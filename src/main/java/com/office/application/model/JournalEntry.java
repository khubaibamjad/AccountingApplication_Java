package com.office.application.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** One transaction. product is filled for purchases and sales, null otherwise. */
public record JournalEntry(UUID id, LocalDate entryDate, String description, List<JournalLine> lines, String product) {

    public JournalEntry {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (entryDate == null) throw new IllegalArgumentException("date is required");
        lines = List.copyOf(lines);
        product = (product == null || product.isBlank()) ? null : product.trim();
    }

    public JournalEntry(UUID id, LocalDate entryDate, String description, List<JournalLine> lines) {
        this(id, entryDate, description, lines, null);
    }

    public static JournalEntry create(LocalDate date, String description, List<JournalLine> lines) {
        return new JournalEntry(UUID.randomUUID(), date, description, lines, null);
    }

    public static JournalEntry create(LocalDate date, String description, List<JournalLine> lines, String product) {
        return new JournalEntry(UUID.randomUUID(), date, description, lines, product);
    }
}
