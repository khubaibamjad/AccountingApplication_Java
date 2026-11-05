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

    // --- restore -------------------------------------------------------------

    /** What a backup file contains; shown to the user before they confirm the restore. */
    public record RestorePlan(Path source, Path checkedCopy, int parties, int entries, String lastEntryDate, String version) {
        public String summary() {
            return parties + " parties, " + entries + " transactions"
                    + (lastEntryDate == null ? "" : ", latest dated " + lastEntryDate) + " (database version " + version + ")";
        }
    }

    /**
     * Checks a backup WITHOUT touching the live data: works on a temporary copy, verifies it is a healthy
     * Texaxis Accounts database from this or an older app version, and upgrades the copy if it is older.
     */
    public RestorePlan prepareRestore(Path backup) {
        Path src = backup.toAbsolutePath().normalize();
        if (src.equals(dbFile.normalize())) throw new IllegalArgumentException("That is the live database itself. Choose a backup file.");
        if (!Files.isRegularFile(src)) throw new IllegalArgumentException("File not found: " + src);
        try {
            byte[] head = new byte[16];
            try (var in = Files.newInputStream(src)) {
                if (in.read(head) != 16 || !new String(head, java.nio.charset.StandardCharsets.US_ASCII).startsWith("SQLite format 3"))
                    throw new IllegalArgumentException("This is not a Texaxis Accounts backup (not a database file).");
            }
            Path copy = Files.createTempFile(dbFile.getParent(), "restore-check-", ".db");
            Files.copy(src, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try {
                String url = "jdbc:sqlite:" + copy;
                try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
                    try (ResultSet rs = st.executeQuery("PRAGMA quick_check")) {
                        if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1)))
                            throw new IllegalArgumentException("The backup file is damaged and cannot be used.");
                    }
                    try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN "
                            + "('flyway_schema_history','accounts','parties','journal_entries','journal_lines')")) {
                        if (!rs.next() || rs.getInt(1) != 5)
                            throw new IllegalArgumentException("This database is not from Texaxis Accounts.");
                    }
                }
                Flyway flyway = Flyway.configure().dataSource(url, null, null).load();
                boolean newer = java.util.Arrays.stream(flyway.info().all())
                        .anyMatch(m -> m.getState().name().startsWith("FUTURE"));
                if (newer)
                    throw new IllegalArgumentException("This backup was made by a NEWER version of Texaxis Accounts. Update the app first.");
                flyway.migrate();   // older backup: upgrade the copy now, so the live data is never left half-upgraded

                try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT (SELECT COUNT(*) FROM parties), (SELECT COUNT(*) FROM journal_entries), "
                             + "(SELECT MAX(entry_date) FROM journal_entries), "
                             + "(SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history WHERE success = 1)")) {
                    rs.next();
                    return new RestorePlan(src, copy, rs.getInt(1), rs.getInt(2), rs.getString(3), "V" + rs.getInt(4));
                }
            } catch (RuntimeException | SQLException e) {
                Files.deleteIfExists(copy);
                if (e instanceof IllegalArgumentException iae) throw iae;
                throw new IllegalArgumentException("The backup could not be read: " + e.getMessage(), e);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the backup file", e);
        }
    }

    /**
     * Replaces ALL live data with the checked backup. A safety copy of the current data is written first,
     * so a restore can itself be undone. Returns that safety copy. The app should restart afterwards.
     */
    public Path applyRestore(RestorePlan plan) {
        try {
            Path dir = dbFile.getParent().resolve("backups");
            Files.createDirectories(dir);
            Path safety = dir.resolve("AccountingDB-before-restore-"
                    + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")) + ".db");
            backupTo(safety);
            ownerOnly(safety, "rw-------");
            try (Connection c = DriverManager.getConnection(dbUrl); Statement st = c.createStatement()) {
                // SQLite online backup API (sqlite-jdbc command): copies every page of the checked file into the live DB
                st.executeUpdate("restore from '" + plan.checkedCopy().toString().replace("'", "''") + "'");
            }
            return safety;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (SQLException e) {
            throw new RuntimeException("Restore failed, your data was not changed: " + e.getMessage(), e);
        } finally {
            try { Files.deleteIfExists(plan.checkedCopy()); } catch (IOException ignored) { }
        }
    }

    /** User changed their mind: remove the temporary checked copy. */
    public void cancelRestore(RestorePlan plan) {
        try { Files.deleteIfExists(plan.checkedCopy()); } catch (IOException ignored) { }
    }

    public Path getDbFile() {
        return dbFile;
    }

    public String getDbUrl() {
        return dbUrl;
    }
}
