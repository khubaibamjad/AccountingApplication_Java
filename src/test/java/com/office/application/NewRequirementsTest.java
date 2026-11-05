package com.office.application;

import com.office.application.db.DatabaseManager;
import com.office.application.model.*;
import com.office.application.report.LedgerRow;
import com.office.application.security.SystemSecurityCheck;
import com.office.application.service.Money;
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

/** Registered/NTN parties, quantity x price per kg, number formatting, hidden codes, PC security parsing. */
class NewRequirementsTest {

    @TempDir Path dir;
    AppContext ctx;
    Account cash;
    final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        DatabaseManager db = new DatabaseManager(dir.resolve("t.db"));
        db.migrate();
        ctx = new AppContext(db);
        cash = ctx.accounts.all().stream().filter(a -> a.code().equals("1000")).findFirst().orElseThrow();
    }

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    private Request sale(Party p, String qty, String price, String paid) {
        return new Request(TransactionType.SALE, today, p, null, cash, null, paid == null ? null : d(paid), null,
                "Cement", "inv 7", false, qty == null ? null : d(qty), price == null ? null : d(price));
    }

    @Test
    void registeredPartiesNeedValidUniqueNtn() {
        Party reg = ctx.parties.createParty("Reg Co", PartyType.CUSTOMER, null, null, true, "1234567-8");
        assertTrue(reg.registered());
        assertEquals("1234567-8", reg.ntn());
        assertEquals("Registered", reg.status());

        assertThrows(ValidationException.class, () -> ctx.parties.createParty("No Ntn", PartyType.VENDOR, null, null, true, " "));
        assertThrows(ValidationException.class, () -> ctx.parties.createParty("Bad Ntn", PartyType.VENDOR, null, null, true, "12AB"));
        assertThrows(ValidationException.class, () -> ctx.parties.createParty("Dup Ntn", PartyType.VENDOR, null, null, true, "12345678"));   // same digits
        ctx.parties.createParty("Cnic Person", PartyType.VENDOR, null, null, true, "35202-1234567-1");
        ctx.parties.createParty("Cnic2", PartyType.VENDOR, null, null, true, "3520212345672");

        Party unreg = ctx.parties.createParty("Cash Buyer", PartyType.CUSTOMER, null, null, false, "9999999");
        assertFalse(unreg.registered());
        assertNull(unreg.ntn(), "unregistered parties never keep an NTN");

        // switch to registered later
        ctx.parties.update(new Party(unreg.id(), unreg.name(), unreg.type(), null, null, unreg.accountId(), true, "7654321"));
        Party now = ctx.parties.all().stream().filter(p -> p.id().equals(unreg.id())).findFirst().orElseThrow();
        assertTrue(now.registered());
        assertEquals("7654321", now.ntn());

        // unregistered parties can still buy on credit
        Party walkIn = ctx.parties.createParty("Walk In", PartyType.CUSTOMER, null, null);
        ctx.transactions.record(sale(walkIn, "10", "100", null));
        assertEquals(0, d("1000.00").compareTo(ctx.reports.partyNet(walkIn, today)));
    }

    @Test
    void amountIsQuantityTimesPricePerKg() {
        Party p = ctx.parties.createParty("Buyer", PartyType.CUSTOMER, null, null);
        JournalEntry e = ctx.transactions.record(sale(p, "1250.5", "85.40", "50000"));
        assertEquals(d("106792.70"), e.amount());          // 1250.5 x 85.40
        assertEquals("Cement", e.product());
        assertEquals(0, d("1250.5").compareTo(e.quantityKg()));
        assertEquals("Sale to Buyer - inv 7", e.description());

        Account acc = ctx.accounts.all().stream().filter(a -> a.id().equals(p.accountId())).findFirst().orElseThrow();
        List<LedgerRow> ledger = ctx.reports.ledger(acc, today.withDayOfMonth(1), today);
        LedgerRow saleRow = ledger.get(1);
        assertEquals("Cement", saleRow.product());
        assertEquals(d("1250.500"), saleRow.quantityKg());
        assertEquals(d("85.40"), saleRow.pricePerKg());
        assertEquals(d("106792.70"), saleRow.debit());
        assertEquals("Payment from Buyer (with sale) - inv 7", ledger.get(2).description());
        assertEquals("Cement", ledger.get(2).product());
        assertEquals(d("56792.70"), ledger.get(ledger.size() - 1).balance());

        JournalEntry reloaded = ctx.journal.entries(today, today).get(0);
        assertEquals(d("85.40"), reloaded.pricePerKg());

        assertThrows(ValidationException.class, () -> ctx.transactions.record(sale(p, "0", "85", null)));
        assertThrows(ValidationException.class, () -> ctx.transactions.record(sale(p, "1.2345", "85", null)));   // > 3 decimals
        assertThrows(ValidationException.class, () -> ctx.transactions.record(sale(p, "10", "-1", null)));
        assertThrows(ValidationException.class, () -> ctx.transactions.record(sale(p, null, "85", null)));
        assertThrows(ValidationException.class, () -> ctx.transactions.record(sale(p, "10", "10", "101")));     // received > total
    }

    @Test
    void numbersAreFormattedWithSeparators() {
        assertEquals("1,000,000.00", Money.format(d("1000000")));
        assertEquals("106,792.70", Money.format(d("106792.7")));
        assertEquals("-60,000.00", Money.format(d("-60000")));
        assertEquals("1,250.5", Money.qty(d("1250.500")));
        assertEquals(0, d("1000000.5").compareTo(Money.parse("1,000,000.50", "x")));
        assertNull(Money.parse("  ", "x"));
        assertThrows(ValidationException.class, () -> Money.parse("12abc", "Amount"));
    }

    @Test
    void accountCodesAreInternalOnly() {
        Account a = ctx.accounts.create("Courier Charges", AccountType.EXPENSE);
        assertEquals("Courier Charges", a.toString(), "drop-downs show names only");
        assertTrue(a.code().matches("5\\d{3}"), "code still assigned internally");
        Account b = ctx.accounts.create("Petty Cash", AccountType.ASSET);
        assertTrue(b.code().compareTo("1000") >= 0 && b.code().compareTo("1299") <= 0);
        assertTrue(ctx.reports.profitAndLoss(today, today).stream().noneMatch(l -> l.label().matches(".*\\d{4}.*")));
    }

    @Test
    void securityCheckParsing() {
        String healthy = String.join("\n",
                "AV=Windows Defender|397568", "DEFENDER_RT=True", "DEFENDER_SIG_AGE=1",
                "FW=Domain|True", "FW=Private|True", "FW=Public|True",
                "BITLOCKER=1", "LAST_UPDATE=" + today.minusDays(10), "PW_REQUIRED=True", "OS=Microsoft Windows 11 Pro (build 22631)");
        var ok = SystemSecurityCheck.parse(healthy, today);
        assertEquals(0, SystemSecurityCheck.warnings(ok), ok.toString());

        String bad = String.join("\n",
                "AV=Windows Defender|393472", "FW=Domain|True", "FW=Public|False",
                "BITLOCKER=2", "LAST_UPDATE=" + today.minusDays(200), "PW_REQUIRED=False");
        var issues = SystemSecurityCheck.parse(bad, today);
        assertEquals(5, SystemSecurityCheck.warnings(issues), issues.toString());   // AV off, firewall, encryption, updates, password

        var empty = SystemSecurityCheck.parse("", today);
        assertEquals(0, SystemSecurityCheck.warnings(empty));                      // unknown, never a false alarm
        assertTrue(empty.stream().allMatch(i -> i.status() == SystemSecurityCheck.Status.UNKNOWN));
    }
}
