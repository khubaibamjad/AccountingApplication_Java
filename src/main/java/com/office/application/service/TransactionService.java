package com.office.application.service;

import com.office.application.model.*;
import com.office.application.report.ReportService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns simple business transactions ("bought from ABC for 200,000, paid 100,000")
 * into balanced journal entries, so users never deal with debits and credits.
 */
public class TransactionService {

    /** Largest single amount accepted (fits DECIMAL(15,2)). */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");
    private static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);

    /**
     * What the New Transaction form collects. Unused fields may be null.
     * costOfGoods (sales only, optional): what the sold goods cost you; moves that value
     * from Inventory to Cost of Goods Sold so profit is correct.
     * theyOweUs (PARTY_OPENING only): true = the party owed you before you started using the app.
     * quantityKg / pricePerKg (purchases and sales): the amount is calculated from them.
     * note: the user's free-text description.
     */
    public record Request(TransactionType type, LocalDate date, Party party, Account category,
                          Account paymentAccount, BigDecimal amount, BigDecimal paidNow,
                          BigDecimal costOfGoods, String product, String note, boolean theyOweUs,
                          BigDecimal quantityKg, BigDecimal pricePerKg) {

        public Request(TransactionType type, LocalDate date, Party party, Account category, Account paymentAccount,
                       BigDecimal amount, BigDecimal paidNow, BigDecimal costOfGoods, String product, String note,
                       boolean theyOweUs) {
            this(type, date, party, category, paymentAccount, amount, paidNow, costOfGoods, product, note, theyOweUs, null, null);
        }

        /** Same as above for every type except PARTY_OPENING (theyOweUs = false). */
        public Request(TransactionType type, LocalDate date, Party party, Account category, Account paymentAccount,
                       BigDecimal amount, BigDecimal paidNow, BigDecimal costOfGoods, String product, String note) {
            this(type, date, party, category, paymentAccount, amount, paidNow, costOfGoods, product, note, false, null, null);
        }

        /**
         * Purchases/sales: amount = quantity (kg) x price per kg, rounded to 2 decimals.
         * If quantity and price are not given, the typed amount is used (older callers, tests).
         */
        public BigDecimal effectiveAmount() {
            if (type != null && type.needsProduct() && quantityKg != null && pricePerKg != null)
                return quantityKg.multiply(pricePerKg).setScale(2, java.math.RoundingMode.HALF_UP);
            return amount;
        }
    }

    /** quantity x price, for the live total on the form. null if either is missing. */
    public static BigDecimal total(BigDecimal quantityKg, BigDecimal pricePerKg) {
        if (quantityKg == null || pricePerKg == null) return null;
        return quantityKg.multiply(pricePerKg).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private final JournalService journal;
    private final ReportService reports;

    public TransactionService(JournalService journal, ReportService reports) {
        this.journal = journal;
        this.reports = reports;
    }

    public JournalEntry record(Request r) {
        validate(r);
        BigDecimal amount = r.effectiveAmount();
        BigDecimal paid = orZero(r.paidNow());
        BigDecimal cost = orZero(r.costOfGoods());
        String product = r.type().needsProduct() ? r.product().trim() : null;
        BigDecimal qty = r.type().needsProduct() ? r.quantityKg() : null;
        BigDecimal price = r.type().needsProduct() ? r.pricePerKg() : null;
        List<JournalLine> lines = new ArrayList<>();
        String desc;
        JournalEntry extra = null;   // separate payment entry when a purchase/sale is (partly) paid now

        switch (r.type()) {
            case PURCHASE -> {
                desc = "Purchase from " + r.party().name();
                lines.add(JournalLine.debit(SystemAccounts.INVENTORY, amount));
                lines.add(JournalLine.credit(r.party().accountId(), amount));
                if (paid.signum() > 0)
                    extra = JournalEntry.create(r.date(), withNote("Payment to " + r.party().name() + " (with purchase)", r.note()),
                            List.of(JournalLine.debit(r.party().accountId(), paid),
                                    JournalLine.credit(r.paymentAccount().id(), paid)), product);   // payment row: product as reference only, no qty/price
            }
            case SALE -> {
                desc = "Sale to " + r.party().name();
                lines.add(JournalLine.debit(r.party().accountId(), amount));
                lines.add(JournalLine.credit(SystemAccounts.SALES, amount));
                if (cost.signum() > 0) {                       // stock leaves inventory at cost
                    lines.add(JournalLine.debit(SystemAccounts.COST_OF_GOODS_SOLD, cost));
                    lines.add(JournalLine.credit(SystemAccounts.INVENTORY, cost));
                }
                if (paid.signum() > 0)
                    extra = JournalEntry.create(r.date(), withNote("Payment from " + r.party().name() + " (with sale)", r.note()),
                            List.of(JournalLine.debit(r.paymentAccount().id(), paid),
                                    JournalLine.credit(r.party().accountId(), paid)), product);   // payment row: product as reference only, no qty/price
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
            case ASSET_PURCHASE -> {
                desc = "Bought " + r.category().name();
                lines.add(JournalLine.debit(r.category().id(), amount));
                lines.add(JournalLine.credit(r.paymentAccount().id(), amount));
            }
            case OWNER_INVESTMENT -> {
                desc = "Owner investment";
                lines.add(JournalLine.debit(r.paymentAccount().id(), amount));
                lines.add(JournalLine.credit(SystemAccounts.OWNER_CAPITAL, amount));
            }
            case PARTY_OPENING -> {
                // dues from before the app: offset against owner's capital so profit is not affected
                desc = "Opening balance - " + r.party().name() + (r.theyOweUs() ? " owes us" : " is owed by us");
                if (r.theyOweUs()) {
                    lines.add(JournalLine.debit(r.party().accountId(), amount));
                    lines.add(JournalLine.credit(SystemAccounts.OWNER_CAPITAL, amount));
                } else {
                    lines.add(JournalLine.debit(SystemAccounts.OWNER_CAPITAL, amount));
                    lines.add(JournalLine.credit(r.party().accountId(), amount));
                }
            }
            default -> throw new ValidationException("Unsupported type " + r.type());
        }

        JournalEntry entry = JournalEntry.create(r.date(), withNote(desc, r.note()), lines, product, qty, price);
        journal.postAll(extra == null ? List.of(entry) : List.of(entry, extra));
        return entry;
    }

    /**
     * Things that are allowed but probably a mistake (cash going negative, paying more than owed...).
     * The screen asks the user to confirm each one. Call after validation passes.
     */
    public List<String> warnings(Request r) {
        validate(r);
        List<String> out = new ArrayList<>();
        BigDecimal amount = r.effectiveAmount();
        BigDecimal paid = orZero(r.paidNow());
        LocalDate today = LocalDate.now();

        // money leaving cash/bank
        BigDecimal outflow = switch (r.type()) {
            case PURCHASE -> paid;
            case PAYMENT_MADE, EXPENSE, ASSET_PURCHASE -> amount;
            default -> BigDecimal.ZERO;
        };
        if (outflow.signum() > 0 && r.paymentAccount() != null) {
            BigDecimal bal = reports.balance(r.paymentAccount(), today);
            if (bal.subtract(outflow).signum() < 0)
                out.add(r.paymentAccount().name() + " has only " + money(bal) + ". After this it will be "
                        + money(bal.subtract(outflow)) + ".");
        }

        // paying / receiving more than the open balance
        if (r.party() != null && (r.type() == TransactionType.PAYMENT_MADE || r.type() == TransactionType.PAYMENT_RECEIVED)) {
            BigDecimal net = reports.partyNet(r.party(), today);          // >0 they owe you, <0 you owe them
            BigDecimal open = r.type() == TransactionType.PAYMENT_MADE ? net.negate() : net;
            if (amount.compareTo(open.max(BigDecimal.ZERO)) > 0)
                out.add((r.type() == TransactionType.PAYMENT_MADE ? "You owe " : r.party().name() + " owes you ")
                        + (r.type() == TransactionType.PAYMENT_MADE ? r.party().name() + " " : "")
                        + "only " + money(open.max(BigDecimal.ZERO)) + ". The extra "
                        + money(amount.subtract(open.max(BigDecimal.ZERO))) + " will be recorded as an advance.");
        }

        // selling more stock (at cost) than inventory holds
        BigDecimal cost = orZero(r.costOfGoods());
        if (r.type() == TransactionType.SALE && cost.signum() > 0) {
            BigDecimal stock = reports.balanceOf(SystemAccounts.INVENTORY, today);
            if (stock.subtract(cost).signum() < 0)
                out.add("Inventory is worth only " + money(stock) + " but this sale removes " + money(cost) + ".");
        }
        if (r.type() == TransactionType.SALE && cost.compareTo(amount) > 0)
            out.add("Cost of goods (" + money(cost) + ") is higher than the sale price (" + money(amount) + "): this sale makes a loss.");

        if (r.date().isAfter(today))
            out.add("The date " + r.date() + " is in the future.");
        return out;
    }

    private void validate(Request r) {
        if (r.type() == null) throw new ValidationException("Choose a transaction type");
        if (r.date() == null) throw new ValidationException("Choose a valid date");
        if (r.date().isBefore(EARLIEST) || r.date().isAfter(LocalDate.now().plusYears(1)))
            throw new ValidationException("The date " + r.date() + " looks wrong. Please check it.");
        if (r.type().needsProduct() && (r.quantityKg() != null || r.pricePerKg() != null)) {
            checkQuantity(r.quantityKg());
            checkAmount(r.pricePerKg(), "Price per kg", true);
        }
        checkAmount(r.effectiveAmount(), r.type().needsProduct() ? "Total amount" : "Amount", true);
        checkAmount(r.paidNow(), r.type() == TransactionType.SALE ? "Received now" : "Paid now", false);
        checkAmount(r.costOfGoods(), "Cost of goods", false);

        if (r.type().needsParty() && r.party() == null)
            throw new ValidationException("Choose a " + (r.type() == TransactionType.PARTY_OPENING ? "party"
                    : r.type() == TransactionType.PURCHASE || r.type() == TransactionType.PAYMENT_MADE ? "vendor" : "customer"));
        if (r.type() == TransactionType.EXPENSE && r.category() == null) throw new ValidationException("Choose an expense category");
        if (r.type() == TransactionType.ASSET_PURCHASE && r.category() == null) throw new ValidationException("Choose which asset you bought");
        if (r.type().needsProduct() && (r.product() == null || r.product().isBlank()))
            throw new ValidationException("Product name is required for a " + (r.type() == TransactionType.SALE ? "sale" : "purchase"));
        if (r.product() != null && r.product().trim().length() > 150) throw new ValidationException("Product name is too long (max 150 characters)");
        if (r.note() != null && r.note().trim().length() > 200) throw new ValidationException("Description is too long (max 200 characters)");

        BigDecimal paid = orZero(r.paidNow());
        if (paid.compareTo(r.effectiveAmount()) > 0)
            throw new ValidationException((r.type() == TransactionType.SALE ? "Received now" : "Paid now") + " cannot be more than the total");

        boolean needsPaymentAccount = r.type() != TransactionType.PARTY_OPENING && (!r.type().canPayPartNow() || paid.signum() > 0);
        if (needsPaymentAccount && r.paymentAccount() == null) throw new ValidationException("Choose Cash or Bank");
    }

    private static void checkAmount(BigDecimal v, String field, boolean required) {
        if (v == null) {
            if (required) throw new ValidationException(field + " is required");
            return;
        }
        if (required ? v.signum() <= 0 : v.signum() < 0)
            throw new ValidationException(field + (required ? " must be greater than 0" : " cannot be negative"));
        if (v.stripTrailingZeros().scale() > 2) throw new ValidationException(field + " can have at most 2 decimal places");
        if (v.compareTo(MAX_AMOUNT) > 0) throw new ValidationException(field + " is too large");
    }

    private static void checkQuantity(BigDecimal q) {
        if (q == null) throw new ValidationException("Quantity (kg) is required");
        if (q.signum() <= 0) throw new ValidationException("Quantity (kg) must be greater than 0");
        if (q.stripTrailingZeros().scale() > 3) throw new ValidationException("Quantity can have at most 3 decimal places (grams)");
        if (q.compareTo(new BigDecimal("999999999999")) > 0) throw new ValidationException("Quantity is too large");
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String money(BigDecimal v) {
        return Money.format(v);
    }

    private static String withNote(String desc, String note) {
        return (note == null || note.isBlank()) ? desc : desc + " - " + note.trim();
    }
}
