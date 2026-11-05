package com.office.application.service;

import com.office.application.model.JournalEntry;
import com.office.application.model.JournalLine;
import com.office.application.repository.AccountRepository;
import com.office.application.repository.JournalRepository;
import com.office.application.security.AuditLog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class JournalService {

    private final JournalRepository journals;
    private final AccountRepository accounts;
    private final AuditLog audit;

    public JournalService(JournalRepository journals, AccountRepository accounts, AuditLog audit) {
        this.journals = journals;
        this.accounts = accounts;
        this.audit = audit;
    }

    public void post(JournalEntry entry) {
        validate(entry);
        journals.save(entry);
        audit.record("ENTRY_CREATED", describe(entry));
    }

    /** Validates every entry, then saves them together (all or none). */
    public void postAll(List<JournalEntry> entries) {
        entries.forEach(this::validate);
        journals.saveAll(entries);
        entries.forEach(e -> audit.record("ENTRY_CREATED", describe(e)));
    }

    public void delete(UUID entryId) {
        delete(entryId, null);
    }

    /** label: what the user saw (for the audit log). */
    public void delete(UUID entryId, String label) {
        journals.delete(entryId);
        audit.record("ENTRY_DELETED", (label == null ? "" : label + " ") + "[" + entryId + "]");
    }

    private static String describe(JournalEntry e) {
        String qty = e.quantityKg() == null ? "" : " | " + e.product() + " " + Money.qty(e.quantityKg()) + " kg @ " + Money.format(e.pricePerKg());
        return e.entryDate() + " " + e.description() + qty + " = " + Money.format(e.amount()) + " [" + e.id() + "]";
    }

    public List<JournalEntry> entries(LocalDate from, LocalDate to) {
        return journals.findEntries(from, to);
    }

    private void validate(JournalEntry entry) {
        if (entry.lines().size() < 2) throw new ValidationException("An entry needs at least 2 lines");

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (JournalLine line : entry.lines()) {
            if (line.debit().signum() < 0 || line.credit().signum() < 0)
                throw new ValidationException("Amounts cannot be negative");
            boolean hasDebit = line.debit().signum() > 0;
            boolean hasCredit = line.credit().signum() > 0;
            if (hasDebit == hasCredit)
                throw new ValidationException("Each line must have either a debit or a credit, not both or neither");
            if (accounts.findById(line.accountId()).isEmpty())
                throw new ValidationException("Account not found: " + line.accountId());

            totalDebit = totalDebit.add(line.debit());
            totalCredit = totalCredit.add(line.credit());
        }

        if (totalDebit.compareTo(totalCredit) != 0)
            throw new ValidationException("Debits (" + totalDebit + ") must equal credits (" + totalCredit + ")");
    }
}
