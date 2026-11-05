package com.office.application.security;

import com.office.application.db.DatabaseManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Append-only record of who did what. Never blocks the business action if logging fails. */
public class AuditLog {

    public record Entry(String at, String username, String action, String details) { }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DatabaseManager db;

    public AuditLog(DatabaseManager db) {
        this.db = db;
    }

    public void record(String action, String details) {
        String d = details == null ? null : (details.length() > 500 ? details.substring(0, 500) : details);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO audit_log (at, username, action, details) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, LocalDateTime.now().format(TS));
            ps.setString(2, Session.username());
            ps.setString(3, action);
            ps.setString(4, d);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Audit log write failed: " + e.getMessage());
        }
    }

    public List<Entry> latest(int limit) {
        List<Entry> out = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT at, username, action, details FROM audit_log ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new Entry(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not read the activity log", e);
        }
        return out;
    }
}
