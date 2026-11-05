package com.office.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One transaction. product, quantityKg and pricePerKg are filled for purchases and sales
 * (and the payment taken together with them), null otherwise.
 */
public record JournalEntry(UUID id, LocalDate entryDate, String description, List<JournalLine> lines,
                           String product, BigDecimal quantityKg, BigDecimal pricePerKg) {

    public JournalEntry {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (entryDate == null) throw new IllegalArgumentException("date is required");
        lines = List.copyOf(lines);
        product = (product == null || product.isBlank()) ? null : product.trim();
    }

    public JournalEntry(UUID id, LocalDate entryDate, String description, List<JournalLine> lines, String product) {
        this(id, entryDate, description, lines, product, null, null);
    }

    public JournalEntry(UUID id, LocalDate entryDate, String description, List<JournalLine> lines) {
        this(id, entryDate, description, lines, null, null, null);
    }

    public static JournalEntry create(LocalDate date, String description, List<JournalLine> lines) {
        return new JournalEntry(UUID.randomUUID(), date, description, lines, null, null, null);
    }

    public static JournalEntry create(LocalDate date, String description, List<JournalLine> lines, String product) {
        return new JournalEntry(UUID.randomUUID(), date, description, lines, product, null, null);
    }

    public static JournalEntry create(LocalDate date, String description, List<JournalLine> lines,
                                      String product, BigDecimal quantityKg, BigDecimal pricePerKg) {
        return new JournalEntry(UUID.randomUUID(), date, description, lines, product, quantityKg, pricePerKg);
    }

    /** Largest debit line: the transaction's headline amount. */
    public BigDecimal amount() {
        return lines.stream().map(JournalLine::debit).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }
}
