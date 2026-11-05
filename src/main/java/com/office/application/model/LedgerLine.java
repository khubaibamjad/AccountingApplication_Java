package com.office.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One posting on an account, with the entry's date, description and product details. */
public record LedgerLine(UUID entryId, LocalDate date, String description, String product,
                         BigDecimal quantityKg, BigDecimal pricePerKg, BigDecimal debit, BigDecimal credit) { }
