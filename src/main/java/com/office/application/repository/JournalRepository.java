package com.office.application.repository;

import com.office.application.model.JournalEntry;

public abstract class JournalRepository {
    void save(JournalEntry entry) {

    }

    public abstract void save(JournalEntry entry);
}
