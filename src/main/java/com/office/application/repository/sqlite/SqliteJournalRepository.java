package com.office.application.repository.sqlite;

import com.office.application.db.DatabaseManager;
import com.office.application.model.AccountTotals;
import com.office.application.model.JournalEntry;
import com.office.application.model.JournalLine;
import com.office.application.model.LedgerLine;
import com.office.application.repository.JournalRepository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

public class SqliteJournalRepository implements JournalRepository {

    private static final LocalDate BEGINNING = LocalDate.of(1900, 1, 1);

    private final Jdbc jdbc;

    public SqliteJournalRepository(DatabaseManager db) {
        this.jdbc = new Jdbc(db);
    }

    @Override
    public void save(JournalEntry entry) {
        saveAll(List.of(entry));
    }

    @Override
    public void saveAll(List<JournalEntry> entries) {
        jdbc.inTransaction(conn -> {
            try (PreparedStatement es = conn.prepareStatement(
                         "INSERT INTO journal_entries (id, entry_date, description, product, linked_entry_id) VALUES (?, ?, ?, ?, ?)");
                 PreparedStatement ls = conn.prepareStatement(
                         "INSERT INTO journal_lines (id, entry_id, account_id, debit, credit) VALUES (?, ?, ?, ?, ?)")) {
                for (JournalEntry entry : entries) {
                    es.setString(1, entry.id().toString());
                    es.setString(2, entry.entryDate().toString());
                    es.setString(3, entry.description());
                    es.setString(4, entry.product());
                    // entries saved together (a sale + the payment taken with it) are linked to the first one
                    JournalEntry first = entries.get(0);
                    es.setString(5, entry == first ? null : first.id().toString());
                    es.executeUpdate();
                    for (JournalLine line : entry.lines()) {
                        ls.setString(1, line.id().toString());
                        ls.setString(2, entry.id().toString());
                        ls.setString(3, line.accountId().toString());
                        ls.setBigDecimal(4, line.debit());
                        ls.setBigDecimal(5, line.credit());
                        ls.addBatch();
                    }
                }
                ls.executeBatch();
            }
        });
    }

    /** Deletes the entry and any entry saved together with it (e.g. the payment taken with a sale). */
    @Override
    public void delete(UUID entryId) {
        jdbc.inTransaction(conn -> {
            String ids = "SELECT id FROM journal_entries WHERE id = ? OR linked_entry_id = ?";
            try (PreparedStatement ps = Jdbc.prepare(conn,
                    "DELETE FROM journal_lines WHERE entry_id IN (" + ids + ")", entryId, entryId)) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = Jdbc.prepare(conn,
                    "DELETE FROM journal_entries WHERE id = ? OR linked_entry_id = ?", entryId, entryId)) {
                ps.executeUpdate();
            }
        });
    }

    @Override
    public List<JournalEntry> findEntries(LocalDate from, LocalDate to) {
        record Row(UUID entryId, LocalDate date, String desc, String product, JournalLine line) { }

        List<Row> rows = jdbc.query("""
                SELECT e.id AS eid, e.entry_date, e.description, e.product, l.id AS lid, l.account_id, l.debit, l.credit
                FROM journal_entries e JOIN journal_lines l ON l.entry_id = e.id
                WHERE e.entry_date BETWEEN ? AND ?
                ORDER BY e.entry_date, e.rowid, l.rowid
                """,
                rs -> new Row(UUID.fromString(rs.getString("eid")),
                        LocalDate.parse(rs.getString("entry_date")),
                        rs.getString("description"),
                        rs.getString("product"),
                        new JournalLine(UUID.fromString(rs.getString("lid")),
                                UUID.fromString(rs.getString("account_id")),
                                money(rs, "debit"), money(rs, "credit"))),
                orBeginning(from), to);

        // group the flat rows back into entries, keeping order
        Map<UUID, List<Row>> grouped = new LinkedHashMap<>();
        for (Row r : rows) grouped.computeIfAbsent(r.entryId(), k -> new ArrayList<>()).add(r);

        List<JournalEntry> out = new ArrayList<>();
        grouped.forEach((id, list) -> out.add(new JournalEntry(id, list.get(0).date(), list.get(0).desc(),
                list.stream().map(Row::line).toList(), list.get(0).product())));
        return out;
    }

    @Override
    public List<LedgerLine> findLedgerLines(UUID accountId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT e.id, e.entry_date, e.description, l.debit, l.credit
                FROM journal_lines l JOIN journal_entries e ON e.id = l.entry_id
                WHERE l.account_id = ? AND e.entry_date BETWEEN ? AND ?
                ORDER BY e.entry_date, e.rowid, l.rowid
                """,
                rs -> new LedgerLine(UUID.fromString(rs.getString("id")),
                        LocalDate.parse(rs.getString("entry_date")),
                        rs.getString("description"),
                        money(rs, "debit"), money(rs, "credit")),
                accountId, orBeginning(from), to);
    }

    @Override
    public Map<UUID, AccountTotals> totalsByAccount(LocalDate from, LocalDate to) {
        Map<UUID, AccountTotals> out = new HashMap<>();
        jdbc.query("""
                SELECT l.account_id, SUM(l.debit) AS d, SUM(l.credit) AS c
                FROM journal_lines l JOIN journal_entries e ON e.id = l.entry_id
                WHERE e.entry_date BETWEEN ? AND ?
                GROUP BY l.account_id
                """,
                rs -> out.put(UUID.fromString(rs.getString("account_id")),
                        new AccountTotals(money(rs, "d"), money(rs, "c"))),
                orBeginning(from), to);
        return out;
    }

    private static LocalDate orBeginning(LocalDate from) {
        return from == null ? BEGINNING : from;
    }

    /** SQLite stores 200000.00 as 200000, so always bring amounts back to 2 decimals. */
    private static BigDecimal money(ResultSet rs, String col) throws SQLException {
        BigDecimal v = rs.getBigDecimal(col);
        return (v == null ? BigDecimal.ZERO : v).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
