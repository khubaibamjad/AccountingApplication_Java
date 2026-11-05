package com.office.application;

import com.office.application.db.DatabaseManager;
import com.office.application.model.*;
import com.office.application.report.LedgerRow;
import com.office.application.report.PartyBalance;
import com.office.application.report.StatementLine;
import com.office.application.report.TrialBalanceRow;
import com.office.application.service.TransactionService.Request;
import com.office.application.service.ValidationException;
import com.office.application.ui.AppContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end tests of the business flows on a throw-away database. */
class AccountingFlowTest {

    @TempDir Path dir;
    AppContext ctx;
    DatabaseManager db;
    Account cash, bank, rent, vehicles;
    final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        db = new DatabaseManager(dir.resolve("test.db"));
        db.migrate();
        ctx = new AppContext(db);
        cash = acc("1000"); bank = acc("1010"); rent = acc("5100"); vehicles = acc("1250");
    }

    // --- helpers -------------------------------------------------------------
    private Account acc(String code) {
        return ctx.accounts.all().stream().filter(a -> a.code().equals(code)).findFirst().orElseThrow();
    }
    private Account accOf(Party p) {
        return ctx.accounts.all().stream().filter(a -> a.id().equals(p.accountId())).findFirst().orElseThrow();
    }
    private static BigDecimal m(String s) { return new BigDecimal(s).setScale(2); }
    private Request req(TransactionType t, Party p, Account cat, Account pay, String amt, String paid, String cost, String product) {
        return new Request(t, today, p, cat, pay, amt == null ? null : new BigDecimal(amt),
                paid == null ? null : new BigDecimal(paid), cost == null ? null : new BigDecimal(cost), product, null);
    }
    private BigDecimal net(Party p) {
        return ctx.reports.partyBalances(today).stream().filter(b -> b.party().id().equals(p.id())).findFirst().orElseThrow().net();
    }
    private void assertBooksBalance() {
        List<TrialBalanceRow> tb = ctx.reports.trialBalance(today);
        BigDecimal d = tb.stream().map(TrialBalanceRow::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal c = tb.stream().map(TrialBalanceRow::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, d.compareTo(c), "trial balance");
        assertTrue(ctx.reports.balanceSheet(today).stream().noneMatch(l -> l.label().startsWith("WARNING")), "balance sheet");
    }

    // --- flows ---------------------------------------------------------------
    @Test
    void fullBusinessCycle() {
        Party abc = ctx.parties.createParty("ABC Traders", PartyType.VENDOR, "0300-1234567", "Lahore");
        Party xyz = ctx.parties.createParty("XYZ Ltd", PartyType.CUSTOMER, null, "Karachi");

        ctx.transactions.record(req(TransactionType.OWNER_INVESTMENT, null, null, cash, "500000", null, null, null));
        ctx.transactions.record(req(TransactionType.PURCHASE, abc, null, cash, "200000", "100000", null, "Cement"));
        ctx.transactions.record(req(TransactionType.SALE, xyz, null, cash, "80000", null, "60000", "Cement 30 bags"));
        ctx.transactions.record(req(TransactionType.PAYMENT_RECEIVED, xyz, null, cash, "50000", null, null, null));
        ctx.transactions.record(req(TransactionType.EXPENSE, null, rent, cash, "10000", null, null, null));
        ctx.transactions.record(req(TransactionType.ASSET_PURCHASE, null, vehicles, bank.equals(cash) ? cash : cash, "20000", null, null, null));

        assertEquals(m("420000"), ctx.reports.balance(cash, today));             // 500-100+50-10-20
        assertEquals(m("-100000"), net(abc));                                    // we owe ABC
        assertEquals(m("30000"), net(xyz));                                      // XYZ owes us
        assertEquals(m("140000"), ctx.reports.balanceOf(SystemAccounts.INVENTORY, today)); // 200 bought - 60 sold
        assertEquals(m("10000"), ctx.reports.netProfit(today.withDayOfMonth(1), today));   // 80 - 60 - 10 (vehicle not expense)
        assertBooksBalance();

        List<LedgerRow> ledger = ctx.reports.ledger(accOf(abc), today.withDayOfMonth(1), today);
        assertEquals("Purchase from ABC Traders - Cement", ledger.get(1).description());
        assertEquals("Payment to ABC Traders (with purchase) - Cement", ledger.get(2).description());
        assertEquals(m("100000"), ledger.get(ledger.size() - 1).balance());
    }

    @Test
    void deletingSaleAlsoDeletesPaymentTakenWithIt() {
        Party s = ctx.parties.createParty("Shahzad", PartyType.CUSTOMER, null, null);
        ctx.transactions.record(req(TransactionType.SALE, s, null, cash, "100000", "100000", null, "Steel"));
        assertEquals(m("100000"), ctx.reports.balance(cash, today));
        assertEquals(2, ctx.journal.entries(today, today).size());

        JournalEntry sale = ctx.journal.entries(today, today).stream().filter(e -> e.description().startsWith("Sale")).findFirst().orElseThrow();
        ctx.journal.delete(sale.id());
        assertEquals(0, ctx.journal.entries(today, today).size());
        assertEquals(m("0"), ctx.reports.balance(cash, today));
        assertEquals(m("0"), net(s));
        assertBooksBalance();
    }

    @Test
    void deletingOnlyThePaymentKeepsTheSale() {
        Party s = ctx.parties.createParty("Shahzad", PartyType.CUSTOMER, null, null);
        ctx.transactions.record(req(TransactionType.SALE, s, null, cash, "100000", "40000", null, "Steel"));
        JournalEntry pay = ctx.journal.entries(today, today).stream().filter(e -> e.description().startsWith("Payment")).findFirst().orElseThrow();
        ctx.journal.delete(pay.id());
        assertEquals(1, ctx.journal.entries(today, today).size());
        assertEquals(m("100000"), net(s));
    }

    @Test
    void partyAccountsAndNames() {
        Party v1 = ctx.parties.createParty("V1", PartyType.VENDOR, null, null);
        Party v2 = ctx.parties.createParty("V2", PartyType.VENDOR, null, null);
        Party c1 = ctx.parties.createParty("C1", PartyType.CUSTOMER, null, null);
        Party both = ctx.parties.createParty("Both Co", PartyType.BOTH, null, null);
        assertEquals("2100", accOf(v1).code());
        assertEquals("2101", accOf(v2).code());
        assertEquals("1300", accOf(c1).code());
        assertEquals("2102", accOf(both).code());

        assertThrows(ValidationException.class, () -> ctx.parties.createParty("v1", PartyType.VENDOR, null, null));     // duplicate
        assertThrows(ValidationException.class, () -> ctx.parties.createParty("Cash", PartyType.VENDOR, null, null));   // account name
        assertThrows(ValidationException.class, () -> ctx.parties.createParty("  ", PartyType.VENDOR, null, null));
        assertThrows(ValidationException.class, () -> ctx.parties.createParty("X", PartyType.VENDOR, "call me", null)); // bad phone

        // rename keeps account in sync; renaming to another party's name is blocked
        ctx.parties.update(new Party(v1.id(), "V1 Renamed", v1.type(), "0300 111", null, v1.accountId()));
        assertEquals("V1 Renamed", accOf(v1).name());
        assertThrows(ValidationException.class, () -> ctx.parties.update(new Party(v1.id(), "V2", v1.type(), null, null, v1.accountId())));
        // renaming a party's account from the Accounts screen is blocked
        assertThrows(ValidationException.class, () -> ctx.accounts.rename(accOf(v2), "Other"));

        // BOTH party: sell and buy, net shows the difference
        ctx.transactions.record(req(TransactionType.SALE, both, null, cash, "50000", null, null, "A"));
        ctx.transactions.record(req(TransactionType.PURCHASE, both, null, cash, "20000", null, null, "B"));
        assertEquals(m("30000"), net(both));
        assertBooksBalance();
    }

    @Test
    void accountRules() {
        assertEquals("5910", ctx.accounts.suggestCode(AccountType.EXPENSE));
        Account internet = ctx.accounts.create("5910", "Courier", AccountType.EXPENSE);
        assertEquals("5920", ctx.accounts.suggestCode(AccountType.EXPENSE));
        assertThrows(ValidationException.class, () -> ctx.accounts.create("5910", "Dup code", AccountType.EXPENSE));
        assertThrows(ValidationException.class, () -> ctx.accounts.create("5930", "courier", AccountType.EXPENSE)); // dup name
        assertThrows(ValidationException.class, () -> ctx.accounts.create("1500", "Wrong range", AccountType.EXPENSE));
        assertThrows(ValidationException.class, () -> ctx.accounts.create("12a4", "Bad", AccountType.ASSET));
        ctx.accounts.rename(internet, "Courier Charges");
        assertEquals("Courier Charges", acc("5910").name());
    }

    @Test
    void rejectsBadTransactions() {
        Party abc = ctx.parties.createParty("ABC", PartyType.VENDOR, null, null);
        Party xyz = ctx.parties.createParty("XYZ", PartyType.CUSTOMER, null, null);
        assertThrows(ValidationException.class, () -> ctx.transactions.record(req(TransactionType.PURCHASE, abc, null, cash, "100", "200", null, "X")));   // paid > total
        assertThrows(ValidationException.class, () -> ctx.transactions.record(req(TransactionType.SALE, xyz, null, cash, "100", null, null, "  ")));       // no product
        assertThrows(ValidationException.class, () -> ctx.transactions.record(req(TransactionType.SALE, null, null, cash, "100", null, null, "X")));       // no party
        assertThrows(ValidationException.class, () -> ctx.transactions.record(req(TransactionType.EXPENSE, null, null, cash, "100", null, null, null)));   // no category
        assertThrows(ValidationException.class, () -> ctx.transactions.record(req(TransactionType.EXPENSE, null, rent, null, "100", null, null, null)));   // no cash/bank
        assertThrows(ValidationException.class, () -> ctx.transactions.record(req(TransactionType.EXPENSE, null, rent, cash, "0", null, null, null)));
        assertThrows(ValidationException.class, () -> ctx.transactions.record(req(TransactionType.EXPENSE, null, rent, cash, "-5", null, null, null)));
        assertThrows(ValidationException.class, () -> ctx.transactions.record(req(TransactionType.EXPENSE, null, rent, cash, "10.555", null, null, null)));
        assertThrows(ValidationException.class, () -> ctx.transactions.record(req(TransactionType.EXPENSE, null, rent, cash, "99999999999999", null, null, null)));
        assertThrows(ValidationException.class, () -> ctx.transactions.record(req(TransactionType.SALE, xyz, null, cash, "100", null, "-1", "X")));        // negative cost
        assertThrows(ValidationException.class, () -> ctx.transactions.record(new Request(TransactionType.EXPENSE, LocalDate.of(1990, 1, 1),
                null, rent, cash, new BigDecimal("5"), null, null, null, null)));                                                                                // silly date
        assertThrows(ValidationException.class, () -> ctx.journal.post(JournalEntry.create(today, "bad", List.of(
                JournalLine.debit(cash.id(), m("5000")), JournalLine.credit(rent.id(), m("4000"))))));
        assertTrue(ctx.journal.entries(today, today).isEmpty(), "nothing saved");

        // purchase with nothing paid now does not need cash/bank
        ctx.transactions.record(req(TransactionType.PURCHASE, abc, null, null, "1000", null, null, "Bricks"));
        assertEquals(m("-1000"), net(abc));
    }

    @Test
    void warningsForLikelyMistakes() {
        Party abc = ctx.parties.createParty("ABC", PartyType.VENDOR, null, null);
        Party xyz = ctx.parties.createParty("XYZ", PartyType.CUSTOMER, null, null);
        // cash would go negative
        assertFalse(ctx.transactions.warnings(req(TransactionType.EXPENSE, null, rent, cash, "100", null, null, null)).isEmpty());
        ctx.transactions.record(req(TransactionType.OWNER_INVESTMENT, null, null, cash, "1000", null, null, null));
        assertTrue(ctx.transactions.warnings(req(TransactionType.EXPENSE, null, rent, cash, "100", null, null, null)).isEmpty());
        // paying a vendor we owe nothing / overpaying
        assertFalse(ctx.transactions.warnings(req(TransactionType.PAYMENT_MADE, abc, null, cash, "100", null, null, null)).isEmpty());
        ctx.transactions.record(req(TransactionType.PURCHASE, abc, null, cash, "500", null, null, "P"));
        assertTrue(ctx.transactions.warnings(req(TransactionType.PAYMENT_MADE, abc, null, cash, "500", null, null, null)).isEmpty());
        assertFalse(ctx.transactions.warnings(req(TransactionType.PAYMENT_MADE, abc, null, cash, "600", null, null, null)).isEmpty());
        // receiving more than customer owes
        assertFalse(ctx.transactions.warnings(req(TransactionType.PAYMENT_RECEIVED, xyz, null, cash, "10", null, null, null)).isEmpty());
        // selling below cost / more stock than we have
        assertFalse(ctx.transactions.warnings(req(TransactionType.SALE, xyz, null, cash, "100", null, "200", "P")).isEmpty());
        assertFalse(ctx.transactions.warnings(req(TransactionType.SALE, xyz, null, cash, "9000", null, "800", "P")).isEmpty());
    }

    @Test
    void reportsByPeriod() {
        Party xyz = ctx.parties.createParty("XYZ", PartyType.CUSTOMER, null, null);
        LocalDate lastMonth = today.minusMonths(1).withDayOfMonth(10);
        ctx.transactions.record(new Request(TransactionType.SALE, lastMonth, xyz, null, cash, new BigDecimal("1000"), null, null, "Old", null));
        ctx.transactions.record(req(TransactionType.SALE, xyz, null, cash, "500", null, null, "New"));

        LocalDate thisMonthStart = today.withDayOfMonth(1);
        assertEquals(m("500"), ctx.reports.netProfit(thisMonthStart, today));
        assertEquals(m("1000"), ctx.reports.netProfit(lastMonth.withDayOfMonth(1), thisMonthStart.minusDays(1)));
        List<LedgerRow> rows = ctx.reports.ledger(accOf(xyz), thisMonthStart, today);
        assertEquals(m("1000"), rows.get(0).balance());              // opening balance carries last month
        assertEquals(m("1500"), rows.get(rows.size() - 1).balance());
        assertEquals(m("1000"), ctx.reports.partyNet(xyz, thisMonthStart.minusDays(1)));
    }

    @Test
    void backup() {
        Path target = dir.resolve("backup.db");
        ctx.db.backupTo(target);
        assertTrue(target.toFile().length() > 0);
        ctx.db.backupTo(target);                                       // overwrite works
        assertThrows(IllegalArgumentException.class, () -> ctx.db.backupTo(dir.resolve("test.db")));   // never over the live DB
    }

    @Test
    void reopeningDatabaseKeepsData() {
        Party p = ctx.parties.createParty("Keep", PartyType.CUSTOMER, null, null);
        ctx.transactions.record(req(TransactionType.SALE, p, null, cash, "100", null, null, "X"));
        DatabaseManager again = new DatabaseManager(dir.resolve("test.db"));
        again.migrate();                                               // second start: no migration errors
        AppContext ctx2 = new AppContext(again);
        assertEquals(1, ctx2.parties.all().size());
        assertEquals(1, ctx2.journal.entries(today, today).size());
    }

    @Test
    void cachedBalancesNeverStale() {
        Party p = ctx.parties.createParty("Cache Co", PartyType.CUSTOMER, null, null);
        ctx.transactions.record(req(TransactionType.SALE, p, null, cash, "100", null, null, "X"));
        assertEquals(m("100"), net(p));
        // a second copy of the app writing to the same file
        AppContext other = new AppContext(new DatabaseManager(dir.resolve("test.db")));
        other.transactions.record(req(TransactionType.PAYMENT_RECEIVED, p, null, cash, "40", null, null, null));
        assertEquals(m("60"), net(p));
        // delete + re-add the same number of lines must not reuse a stale result
        JournalEntry last = ctx.journal.entries(today, today).stream().filter(e -> e.description().startsWith("Payment")).findFirst().orElseThrow();
        ctx.journal.delete(last.id());
        assertEquals(m("100"), net(p));
        ctx.transactions.record(req(TransactionType.PAYMENT_RECEIVED, p, null, cash, "70", null, null, null));
        assertEquals(m("30"), net(p));
    }

    @Test
    void dailyBackupOncePerDayKeeps30() throws Exception {
        Path dirB = dir.resolve("backups");
        java.nio.file.Files.createDirectories(dirB);
        for (int i = 1; i <= 35; i++)
            java.nio.file.Files.writeString(dirB.resolve("AccountingDB-" + today.minusDays(i) + ".db"), "old");
        assertNotNull(ctx.db.dailyBackup());
        assertNull(ctx.db.dailyBackup());                                  // second call same day: nothing
        try (var files = java.nio.file.Files.list(dirB)) { assertEquals(30, files.count()); }
        assertTrue(java.nio.file.Files.exists(dirB.resolve("AccountingDB-" + today + ".db")));
    }
}
