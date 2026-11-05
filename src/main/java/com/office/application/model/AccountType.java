package com.office.application.model;

import java.math.BigDecimal;

public enum AccountType {
    ASSET(1000, 1299), LIABILITY(2000, 2099), EQUITY(3000, 3999), INCOME(4000, 4999), EXPENSE(5000, 5999);

    /** Code range used when suggesting a code for a new (non-party) account. */
    public final int codeStart, codeEnd;

    AccountType(int codeStart, int codeEnd) {
        this.codeStart = codeStart;
        this.codeEnd = codeEnd;
    }

    /** Assets and expenses grow with debits; the others grow with credits. */
    public boolean isDebitNormal() {
        return this == ASSET || this == EXPENSE;
    }

    /** Balance shown as a positive number in the account's "normal" direction. */
    public BigDecimal balance(BigDecimal debit, BigDecimal credit) {
        return isDebitNormal() ? debit.subtract(credit) : credit.subtract(debit);
    }
}
