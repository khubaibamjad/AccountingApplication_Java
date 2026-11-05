package com.office.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One posting on an account, with the entry's date and description. */
public record LedgerLine(UUID entryId, LocalDate date, String description, BigDecimal debit, BigDecimal credit) { }
