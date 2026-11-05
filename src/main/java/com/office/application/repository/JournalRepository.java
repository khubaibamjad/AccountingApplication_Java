package com.office.application.repository;

import com.office.application.model.AccountTotals;
import com.office.application.model.JournalEntry;
import com.office.application.model.LedgerLine;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface JournalRepository {
    void save(JournalEntry entry);

    /** Saves several entries in ONE transaction (all or none). */
    void saveAll(List<JournalEntry> entries);
    void delete(UUID entryId);

    /** Entries (with their lines) dated between from and to, inclusive. */
    List<JournalEntry> findEntries(LocalDate from, LocalDate to);

    /** Postings on one account between from and to, oldest first. */
    List<LedgerLine> findLedgerLines(UUID accountId, LocalDate from, LocalDate to);

    /** Debit/credit totals per account. from == null means "since the beginning". */
    Map<UUID, AccountTotals> totalsByAccount(LocalDate from, LocalDate to);
}
