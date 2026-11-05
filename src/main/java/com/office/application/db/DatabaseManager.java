package com.office.application.db;

import org.flywaydb.core.Flyway;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public class DatabaseManager {

    // keep a strong reference, or the level setting can be garbage-collected
    private static final java.util.logging.Logger FLYWAY_LOGGER =
            java.util.logging.Logger.getLogger("org.flywaydb");

    static {
        FLYWAY_LOGGER.setLevel(java.util.logging.Level.WARNING);
    }

    private final Path dbFile;
    private final String dbUrl;

    public DatabaseManager() {
        this(Path.of(System.getProperty("user.home"), "AccountingApp", "AccountingDB"));
    }

    public DatabaseManager(Path dbFile) {
        try {
            Files.createDirectories(dbFile.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create the database folder", e);
        }
        this.dbFile = dbFile.toAbsolutePath();
        this.dbUrl = "jdbc:sqlite:" + this.dbFile;
    }

    public void migrate() {
        Flyway.configure()
                .dataSource(dbUrl, null, null)
                .load()
                .migrate();
    }

    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(dbUrl);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    /** Writes a consistent copy of the whole database to the target file. */
    public void backupTo(Path target) {
        try {
            Files.deleteIfExists(target);
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement("VACUUM INTO ?")) {
                ps.setString(1, target.toAbsolutePath().toString());
                ps.execute();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (SQLException e) {
            throw new RuntimeException("Backup failed: " + e.getMessage(), e);
        }
    }

    public Path getDbFile() {
        return dbFile;
    }

    public String getDbUrl() {
        return dbUrl;
    }
}
