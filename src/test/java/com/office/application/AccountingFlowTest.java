package com.office.application;

import com.office.application.db.DatabaseManager;
import com.office.application.model.*;
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

/** End-to-end test of the backend on a throw-away database. */
class AccountingFlowTest {

    @TempDir Path dir;
    AppContext ctx;
    Account cash, rent;
    final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        DatabaseManager db = new DatabaseManager(dir.resolve("test.db"));
        db.migrate();
        ctx = new AppContext(db);
        cash = ctx.accounts.all().stream().filter(a -> a.code().equals("1000")).findFirst().orElseThrow();
        rent = ctx.accounts.all().stream().filter(a -> a.code().equals("5100")).findFirst().orElseThrow();
    }

    private static BigDecimal m(String s) { return new BigDecimal(s).setScale(2); }

    @Test
    void fullBusinessCycle() {
        Party abc = ctx.parties.createParty("ABC Traders", PartyType.VENDOR, "0300", "Lahore");
        Party xyz = ctx.parties.createParty("XYZ Ltd", PartyType.CUSTOMER, null, "Karachi");

        ctx.transactions.record(new Request(TransactionType.OWNER_INVESTMENT, today, null, null, cash, m("500000"), null, null));
        ctx.transactions.record(new Request(TransactionType.PURCHASE, today, abc, null, cash, m("200000"), m("100000"), "inv 1"));
        ctx.transactions.record(new Request(TransactionType.SALE, today, xyz, null, cash, m("80000"), null, null));
        ctx.transactions.record(new Request(TransactionType.PAYMENT_RECEIVED, today, xyz, null, cash, m("50000"), null, null));
        ctx.transactions.record(new Request(TransactionType.EXPENSE, today, null, rent, cash, m("30000"), null, null));

        // cash: 500k - 100k + 50k - 30k
        assertEquals(m("420000"), ctx.reports.balance(cash, today));

        List<PartyBalance> pbs = ctx.reports.partyBalances(today);
        assertEquals(m("-100000"), pbs.stream().filter(p -> p.party().equals(abc)).findFirst().orElseThrow().net());
        assertEquals(m("30000"), pbs.stream().filter(p -> p.party().equals(xyz)).findFirst().orElseThrow().net());

        // profit = sales 80k - rent 30k
        assertEquals(m("50000"), ctx.reports.netProfit(today.withDayOfMonth(1), today));

        List<TrialBalanceRow> tb = ctx.reports.trialBalance(today);
        BigDecimal d = tb.stream().map(TrialBalanceRow::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal c = tb.stream().map(TrialBalanceRow::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, d.compareTo(c));

        List<StatementLine> bs = ctx.reports.balanceSheet(today);
        assertTrue(bs.stream().noneMatch(l -> l.label().startsWith("WARNING")), "balance sheet must balance");

        // ledger of ABC: opening 0, bill 200k, payment 100k, closing 100k owed
        var ledger = ctx.reports.ledger(ctx.accounts.all().stream().filter(a -> a.id().equals(abc.accountId())).findFirst().orElseThrow(),
                today.withDayOfMonth(1), today);
        assertEquals(m("100000"), ledger.get(ledger.size() - 1).balance());
        assertEquals("Purchase from ABC Traders - inv 1", ledger.get(1).description());
        assertEquals("Payment to ABC Traders (with purchase) - inv 1", ledger.get(2).description());

        // delete the expense -> cash goes back up
        var expense = ctx.journal.entries(today, today).stream().filter(e -> e.description().startsWith("Rent")).findFirst().orElseThrow();
        ctx.journal.delete(expense.id());
        assertEquals(m("450000"), ctx.reports.balance(cash, today));
    }

    @Test
    void partyGetsOwnAccountWithNextCode() {
        Party v1 = ctx.parties.createParty("V1", PartyType.VENDOR, null, null);
        Party v2 = ctx.parties.createParty("V2", PartyType.VENDOR, null, null);
        Party c1 = ctx.parties.createParty("C1", PartyType.CUSTOMER, null, null);
        assertEquals("2100", code(v1.accountId()));
        assertEquals("2101", code(v2.accountId()));
        assertEquals("1300", code(c1.accountId()));
        assertThrows(ValidationException.class, () -> ctx.parties.createParty("v1", PartyType.VENDOR, null, null));
    }

    @Test
    void rejectsBadInput() {
        Party abc = ctx.parties.createParty("ABC", PartyType.VENDOR, null, null);
        assertThrows(ValidationException.class, () -> ctx.transactions.record(
                new Request(TransactionType.PURCHASE, today, abc, null, cash, m("100"), m("200"), null)));
        assertThrows(ValidationException.class, () -> ctx.journal.post(JournalEntry.create(today, "bad", List.of(
                JournalLine.debit(cash.id(), m("5000")), JournalLine.credit(rent.id(), m("4000"))))));
        assertThrows(ValidationException.class, () -> ctx.accounts.create("1000", "Dup", AccountType.ASSET));
        assertEquals("5300", ctx.accounts.suggestCode(AccountType.EXPENSE));
    }

    @Test
    void backupCreatesFile() {
        Path target = dir.resolve("backup.db");
        ctx.db.backupTo(target);
        assertTrue(target.toFile().length() > 0);
    }

    private String code(java.util.UUID accountId) {
        return ctx.accounts.all().stream().filter(a -> a.id().equals(accountId)).findFirst().orElseThrow().code();
    }
}
