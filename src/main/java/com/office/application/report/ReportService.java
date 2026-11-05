package com.office.application.report;

import com.office.application.model.*;
import com.office.application.repository.AccountRepository;
import com.office.application.repository.JournalRepository;
import com.office.application.repository.PartyRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** All read-only reports. Balances are never stored, always calculated from journal lines. */
public class ReportService {

    private final JournalRepository journals;
    private final AccountRepository accounts;
    private final PartyRepository parties;

    public ReportService(JournalRepository journals, AccountRepository accounts, PartyRepository parties) {
        this.journals = journals;
        this.accounts = accounts;
        this.parties = parties;
    }

    /** Balance of an account up to and including a date, in its normal direction. */
    public BigDecimal balance(Account account, LocalDate upTo) {
        AccountTotals t = journals.totalsByAccount(null, upTo).getOrDefault(account.id(), AccountTotals.ZERO);
        return account.type().balance(t.debit(), t.credit());
    }

    /** Account statement: opening balance, every posting with a running balance, closing balance. */
    public List<LedgerRow> ledger(Account account, LocalDate from, LocalDate to) {
        BigDecimal running = balance(account, from.minusDays(1));
        List<LedgerRow> rows = new ArrayList<>();
        rows.add(new LedgerRow(null, null, "Opening balance", null, null, running));

        for (LedgerLine l : journals.findLedgerLines(account.id(), from, to)) {
            running = running.add(account.type().balance(l.debit(), l.credit()));
            rows.add(new LedgerRow(l.entryId(), l.date(), l.description(), l.debit(), l.credit(), running));
        }
        rows.add(new LedgerRow(null, null, "Closing balance", null, null, running));
        return rows;
    }

    public List<TrialBalanceRow> trialBalance(LocalDate asOf) {
        Map<UUID, AccountTotals> totals = journals.totalsByAccount(null, asOf);
        List<TrialBalanceRow> rows = new ArrayList<>();
        for (Account a : accounts.findAll()) {
            AccountTotals t = totals.get(a.id());
            if (t == null) continue;
            BigDecimal net = t.debit().subtract(t.credit());
            if (net.signum() == 0) continue;
            rows.add(net.signum() > 0
                    ? new TrialBalanceRow(a, net, BigDecimal.ZERO)
                    : new TrialBalanceRow(a, BigDecimal.ZERO, net.negate()));
        }
        return rows;
    }

    /** Income and expense accounts for a period, and the net profit. */
    public List<StatementLine> profitAndLoss(LocalDate from, LocalDate to) {
        Map<UUID, AccountTotals> totals = journals.totalsByAccount(from, to);
        List<StatementLine> out = new ArrayList<>();
        BigDecimal income = section(out, "Income", AccountType.INCOME, totals);
        out.add(StatementLine.blank());
        BigDecimal expenses = section(out, "Expenses", AccountType.EXPENSE, totals);
        out.add(StatementLine.blank());
        out.add(StatementLine.total(income.subtract(expenses).signum() >= 0 ? "NET PROFIT" : "NET LOSS",
                income.subtract(expenses)));
        return out;
    }

    public BigDecimal netProfit(LocalDate from, LocalDate to) {
        Map<UUID, AccountTotals> totals = journals.totalsByAccount(from, to);
        return sumType(AccountType.INCOME, totals).subtract(sumType(AccountType.EXPENSE, totals));
    }

    /** Assets = Liabilities + Equity (profit to date is shown inside equity). */
    public List<StatementLine> balanceSheet(LocalDate asOf) {
        Map<UUID, AccountTotals> totals = journals.totalsByAccount(null, asOf);
        List<StatementLine> out = new ArrayList<>();
        BigDecimal assets = section(out, "Assets", AccountType.ASSET, totals);
        out.add(StatementLine.blank());
        BigDecimal liabilities = section(out, "Liabilities", AccountType.LIABILITY, totals);
        out.add(StatementLine.blank());

        out.add(StatementLine.heading("Equity"));
        BigDecimal equity = BigDecimal.ZERO;
        for (Account a : accounts.findByType(AccountType.EQUITY)) {
            BigDecimal b = balanceFrom(a, totals);
            if (b.signum() != 0) out.add(StatementLine.item(a.name(), b));
            equity = equity.add(b);
        }
        BigDecimal profit = sumType(AccountType.INCOME, totals).subtract(sumType(AccountType.EXPENSE, totals));
        out.add(StatementLine.item("Profit / loss to date", profit));
        equity = equity.add(profit);
        out.add(StatementLine.total("Total Equity", equity));
        out.add(StatementLine.blank());
        out.add(StatementLine.total("Total Liabilities + Equity", liabilities.add(equity)));
        if (assets.compareTo(liabilities.add(equity)) != 0)
            out.add(StatementLine.heading("WARNING: does not balance"));
        return out;
    }

    public List<PartyBalance> partyBalances(LocalDate asOf) {
        Map<UUID, AccountTotals> totals = journals.totalsByAccount(null, asOf);
        return parties.findAll().stream()
                .map(p -> {
                    AccountTotals t = totals.getOrDefault(p.accountId(), AccountTotals.ZERO);
                    return new PartyBalance(p, t.debit().subtract(t.credit()));
                })
                .toList();
    }

    /** Positive total of what customers owe you and what you owe vendors. */
    public BigDecimal[] receivablesAndPayables(LocalDate asOf) {
        BigDecimal receivable = BigDecimal.ZERO, payable = BigDecimal.ZERO;
        for (PartyBalance pb : partyBalances(asOf)) {
            if (pb.net().signum() > 0) receivable = receivable.add(pb.net());
            else payable = payable.add(pb.net().negate());
        }
        return new BigDecimal[]{receivable, payable};
    }

    // --- helpers -------------------------------------------------------------

    private BigDecimal section(List<StatementLine> out, String title, AccountType type, Map<UUID, AccountTotals> totals) {
        out.add(StatementLine.heading(title));
        BigDecimal sum = BigDecimal.ZERO;
        for (Account a : accounts.findByType(type)) {
            BigDecimal b = balanceFrom(a, totals);
            if (b.signum() != 0) out.add(StatementLine.item(a.code() + "  " + a.name(), b));
            sum = sum.add(b);
        }
        out.add(StatementLine.total("Total " + title, sum));
        return sum;
    }

    private BigDecimal sumType(AccountType type, Map<UUID, AccountTotals> totals) {
        return accounts.findByType(type).stream()
                .map(a -> balanceFrom(a, totals))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal balanceFrom(Account a, Map<UUID, AccountTotals> totals) {
        AccountTotals t = totals.getOrDefault(a.id(), AccountTotals.ZERO);
        return a.type().balance(t.debit(), t.credit());
    }
}
