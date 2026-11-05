package com.office.application.repository.sqlite;

import com.office.application.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Tiny helper that removes the repeated try-with-resources boilerplate from the repositories. */
final class Jdbc {

    @FunctionalInterface interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }
    @FunctionalInterface interface Work { void run(Connection conn) throws SQLException; }

    private final DatabaseManager db;

    Jdbc(DatabaseManager db) {
        this.db = db;
    }

    <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = prepare(conn, sql, params);
             ResultSet rs = ps.executeQuery()) {
            List<T> out = new ArrayList<>();
            while (rs.next()) out.add(mapper.map(rs));
            return out;
        } catch (SQLException e) {
            throw new DataAccessException("Query failed: " + sql, e);
        }
    }

    <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = query(sql, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    void update(String sql, Object... params) {
        inTransaction(conn -> {
            try (PreparedStatement ps = prepare(conn, sql, params)) {
                ps.executeUpdate();
            }
        });
    }

    /** Runs the work in one database transaction: commit if it succeeds, rollback if anything fails. */
    void inTransaction(Work work) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                work.run(conn);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database write failed", e);
        }
    }

    static PreparedStatement prepare(Connection conn, String sql, Object... params) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            // UUIDs, dates and enums are stored as text
            ps.setObject(i + 1, (p == null || p instanceof Number) ? p : p.toString());
        }
        return ps;
    }
}
