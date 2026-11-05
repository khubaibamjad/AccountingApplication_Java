package com.office.application.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A ledger line with its running balance. date == null for the opening/closing rows. */
public record LedgerRow(UUID entryId, LocalDate date, String description, String product,
                        BigDecimal quantityKg, BigDecimal pricePerKg,
                        BigDecimal debit, BigDecimal credit, BigDecimal balance) { }
