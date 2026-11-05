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

    public DatabaseManager(com.office.application.config.AppConfig config) {
        this(config.dbFile());
    }

    public DatabaseManager(Path dbFile) {
        try {
            Path dir = dbFile.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            ownerOnly(dir, "rwx------");
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
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode = WAL");   // faster writes, readers never blocked (stored in the file)
            st.execute("PRAGMA optimize");
        } catch (SQLException e) {
            throw new RuntimeException("Could not configure the database: " + e.getMessage(), e);
        }
        ownerOnly(dbFile, "rw-------");   // other OS users on this PC cannot read the books
    }

    /** Fast corruption check (seconds even for big files). Returns "ok" when healthy. */
    public String integrityCheck() {
        try (Connection c = getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA quick_check")) {
            return rs.next() ? rs.getString(1) : "no result";
        } catch (SQLException e) {
            return e.getMessage();
        }
    }

    /** On Linux/macOS: restrict a file or folder to the current user. Ignored on Windows (uses user profile ACLs). */
    private static void ownerOnly(Path p, String perms) {
        try {
            if (Files.exists(p) && p.getFileSystem().supportedFileAttributeViews().contains("posix"))
                Files.setPosixFilePermissions(p, java.nio.file.attribute.PosixFilePermissions.fromString(perms));
        } catch (IOException | UnsupportedOperationException ignored) {
            // not fatal: e.g. a network drive
        }
    }

    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(dbUrl);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 5000");   // wait (don't fail) if another copy of the app is writing
            st.execute("PRAGMA synchronous = NORMAL");  // safe with WAL, much faster commits
        }
        return connection;
    }

    /** Writes a consistent copy of the whole database to the target file. */
    public void backupTo(Path target) {
        if (target.toAbsolutePath().normalize().equals(dbFile.normalize()))
            throw new IllegalArgumentException("Choose a different file: that is the live database itself.");
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

    /**
     * Once a day, copies the database to <data dir>/backups and keeps the newest keepDays copies.
     * Returns the backup written, or null if today's backup already exists.
     */
    public Path dailyBackup() {
        return dailyBackup(30);
    }

    public Path dailyBackup(int keepDays) {
        Path dir = dbFile.getParent().resolve("backups");
        Path today = dir.resolve("AccountingDB-" + java.time.LocalDate.now() + ".db");
        try {
            Files.createDirectories(dir);
            if (Files.exists(today)) return null;
            backupTo(today);
            ownerOnly(dir, "rwx------");
            ownerOnly(today, "rw-------");
            try (var files = Files.list(dir)) {
                files.filter(p -> p.getFileName().toString().matches("AccountingDB-\\d{4}-\\d{2}-\\d{2}\\.db"))
                     .sorted(java.util.Comparator.reverseOrder())
                     .skip(keepDays)
                     .forEach(p -> p.toFile().delete());
            }
            return today;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path getDbFile() {
        return dbFile;
    }

    public String getDbUrl() {
        return dbUrl;
    }
}
