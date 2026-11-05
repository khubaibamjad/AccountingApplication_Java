package com.office.application.repository.sqlite;

import com.office.application.db.DatabaseManager;
import com.office.application.model.JournalEntry;
import com.office.application.model.JournalLine;
import com.office.application.repository.JournalRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class sqliteJournalRepository extends JournalRepository {

    private final DatabaseManager db;

    public sqliteJournalRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void save(JournalEntry entry) {
        String entrySql = "INSERT INTO journal_entries (id, entry_date, description) VALUES (?, ?, ?)";
        String lineSql  = "INSERT INTO journal_lines (id, entry_id, account_id, debit, credit) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);                       // start the transaction

            try (PreparedStatement es = conn.prepareStatement(entrySql);
                 PreparedStatement ls = conn.prepareStatement(lineSql)) {

                // the entry
                es.setString(1, entry.id().toString());
                es.setString(2, entry.entryDate().toString());
                es.setString(3, entry.description());
                es.executeUpdate();

                // its lines
                for (JournalLine line : entry.lines()) {
                    ls.setString(1, line.id().toString());
                    ls.setString(2, entry.id().toString());
                    ls.setString(3, line.accountId().toString());
                    ls.setBigDecimal(4, line.debit());
                    ls.setBigDecimal(5, line.credit());
                    ls.addBatch();
                }
                ls.executeBatch();

                conn.commit();                               // save all
            } catch (SQLException e) {
                conn.rollback();                             // undo all
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save journal entry", e);
        }
    }
}
