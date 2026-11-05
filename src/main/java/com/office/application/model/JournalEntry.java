package com.office.application.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record JournalEntry(UUID id, LocalDate entryDate, String description, List<JournalLine> lines) {

    public JournalEntry {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (entryDate == null) throw new IllegalArgumentException("date is required");
        lines = List.copyOf(lines);
    }

    public static JournalEntry create(LocalDate date, String description, List<JournalLine> lines) {
        return new JournalEntry(UUID.randomUUID(), date, description, lines);
    }
}