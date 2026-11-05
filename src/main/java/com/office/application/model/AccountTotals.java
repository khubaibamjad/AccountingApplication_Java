package com.office.application.model;

import java.math.BigDecimal;

/** Sum of debits and credits on one account for some period. */
public record AccountTotals(BigDecimal debit, BigDecimal credit) {
    public static final AccountTotals ZERO = new AccountTotals(new BigDecimal("0.00"), new BigDecimal("0.00"));

    public AccountTotals plus(AccountTotals o) {
        return new AccountTotals(debit.add(o.debit), credit.add(o.credit));
    }
}
