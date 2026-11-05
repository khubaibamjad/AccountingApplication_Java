package com.office.application.report;

import java.math.BigDecimal;

/** One line of a printed statement (P&L, balance sheet). amount == null means a heading. */
public record StatementLine(String label, BigDecimal amount, boolean bold) {
    public static StatementLine heading(String label) { return new StatementLine(label, null, true); }
    public static StatementLine item(String label, BigDecimal amount) { return new StatementLine("    " + label, amount, false); }
    public static StatementLine total(String label, BigDecimal amount) { return new StatementLine(label, amount, true); }
    public static StatementLine blank() { return new StatementLine("", null, false); }
}
