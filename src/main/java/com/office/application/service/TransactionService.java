package com.office.application.service;

import com.office.application.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns simple business transactions ("bought from ABC for 200,000, paid 100,000")
 * into balanced journal entries, so users never deal with debits and credits.
 */
public class TransactionService {

    /** What the New Transaction form collects. Unused fields may be null. */
    public record Request(TransactionType type, LocalDate date, Party party, Account category,
                          Account paymentAccount, BigDecimal amount, BigDecimal paidNow, String note) { }

    private final JournalService journal;

    public TransactionService(JournalService journal) {
        this.journal = journal;
    }

    public JournalEntry record(Request r) {
        validate(r);
        BigDecimal amount = r.amount();
        BigDecimal paid = r.paidNow() == null ? BigDecimal.ZERO : r.paidNow();
        List<JournalLine> lines = new ArrayList<>();
        String desc;
        JournalEntry extra = null;   // separate payment entry when a purchase/sale is (partly) paid now

        switch (r.type()) {
            case PURCHASE -> {
                desc = "Purchase from " + r.party().name();
                lines.add(JournalLine.debit(SystemAccounts.INVENTORY, amount));
                lines.add(JournalLine.credit(r.party().accountId(), amount));
                if (paid.signum() > 0)                         // part paid on the spot: its own entry
                    extra = JournalEntry.create(r.date(), withNote("Payment to " + r.party().name() + " (with purchase)", r.note()),
                            List.of(JournalLine.debit(r.party().accountId(), paid),
                                    JournalLine.credit(r.paymentAccount().id(), paid)));
            }
            case SALE -> {
                desc = "Sale to " + r.party().name();
                lines.add(JournalLine.debit(r.party().accountId(), amount));
                lines.add(JournalLine.credit(SystemAccounts.SALES, amount));
                if (paid.signum() > 0)                         // part received on the spot: its own entry
                    extra = JournalEntry.create(r.date(), withNote("Payment from " + r.party().name() + " (with sale)", r.note()),
                            List.of(JournalLine.debit(r.paymentAccount().id(), paid),
                                    JournalLine.credit(r.party().accountId(), paid)));
            }
            case PAYMENT_MADE -> {
                desc = "Payment to " + r.party().name();
                lines.add(JournalLine.debit(r.party().accountId(), amount));
                lines.add(JournalLine.credit(r.paymentAccount().id(), amount));
            }
            case PAYMENT_RECEIVED -> {
                desc = "Payment from " + r.party().name();
                lines.add(JournalLine.debit(r.paymentAccount().id(), amount));
                lines.add(JournalLine.credit(r.party().accountId(), amount));
            }
            case EXPENSE -> {
                desc = r.category().name();
                lines.add(JournalLine.debit(r.category().id(), amount));
                lines.add(JournalLine.credit(r.paymentAccount().id(), amount));
            }
            case OWNER_INVESTMENT -> {
                desc = "Owner investment";
                lines.add(JournalLine.debit(r.paymentAccount().id(), amount));
                lines.add(JournalLine.credit(SystemAccounts.OWNER_CAPITAL, amount));
            }
            default -> throw new ValidationException("Unsupported type " + r.type());
        }

        JournalEntry entry = JournalEntry.create(r.date(), withNote(desc, r.note()), lines);
        journal.postAll(extra == null ? List.of(entry) : List.of(entry, extra));
        return entry;
    }

    private static String withNote(String desc, String note) {
        return (note == null || note.isBlank()) ? desc : desc + " - " + note.trim();
    }

    private void validate(Request r) {
        if (r.type() == null) throw new ValidationException("Choose a transaction type");
        if (r.date() == null) throw new ValidationException("Choose a date");
        if (r.amount() == null || r.amount().signum() <= 0) throw new ValidationException("Amount must be greater than 0");
        if (r.type().needsParty() && r.party() == null) throw new ValidationException("Choose a party");
        if (r.type() == TransactionType.EXPENSE && r.category() == null) throw new ValidationException("Choose an expense category");

        BigDecimal paid = r.paidNow() == null ? BigDecimal.ZERO : r.paidNow();
        if (paid.signum() < 0) throw new ValidationException("Paid amount cannot be negative");
        if (paid.compareTo(r.amount()) > 0) throw new ValidationException("Paid now cannot be more than the total");

        boolean needsPaymentAccount = !r.type().canPayPartNow() || paid.signum() > 0;
        if (needsPaymentAccount && r.paymentAccount() == null) throw new ValidationException("Choose Cash or Bank");
    }
}
