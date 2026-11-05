package com.office.application.report;

import com.office.application.model.Account;

import java.math.BigDecimal;

/** Net balance of one account placed in the debit or the credit column. */
public record TrialBalanceRow(Account account, BigDecimal debit, BigDecimal credit) { }
