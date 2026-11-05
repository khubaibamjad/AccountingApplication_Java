package com.office.application.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * App settings. Read in this order (later wins):
 *   1. built-in defaults
 *   2. the file  <data dir>/app.properties   (created with defaults on first run)
 *   3. environment variables  OFFICELEDGER_*
 * Nothing secret lives here: passwords are stored hashed inside the database.
 */
public record AppConfig(Path dataDir, String companyName, int backupKeepDays, int autoLockMinutes) {

    public static final String ENV_DATA_DIR = "OFFICELEDGER_DATA_DIR";
    public static final String ENV_COMPANY = "OFFICELEDGER_COMPANY";
    public static final String ENV_BACKUP_DAYS = "OFFICELEDGER_BACKUP_DAYS";
    public static final String ENV_AUTOLOCK = "OFFICELEDGER_AUTOLOCK_MINUTES";

    private static final String TEMPLATE = """
            # Office Ledger settings. Environment variables OFFICELEDGER_* override these.
            # Restart the app after changing this file.

            # Name shown in the window title
            company.name=Office Ledger

            # How many daily backups to keep in the backups folder (1-365)
            backup.keep.days=30

            # Lock the app after this many idle minutes (0 = never)
            autolock.minutes=15
            """;

    public AppConfig {
        if (dataDir == null) throw new ConfigException("Data folder is not set");
        if (companyName == null || companyName.isBlank()) companyName = "Office Ledger";
        if (backupKeepDays < 1 || backupKeepDays > 365) throw new ConfigException("backup.keep.days must be between 1 and 365");
        if (autoLockMinutes < 0 || autoLockMinutes > 1440) throw new ConfigException("autolock.minutes must be between 0 and 1440");
    }

    public Path dbFile() {
        return dataDir.resolve("AccountingDB");
    }

    public Path backupDir() {
        return dataDir.resolve("backups");
    }

    public Path configFile() {
        return dataDir.resolve("app.properties");
    }

    public static AppConfig load() {
        return load(System.getenv());
    }

    /** Testable: pass any map as "environment". */
    public static AppConfig load(Map<String, String> env) {
        String dirText = env.get(ENV_DATA_DIR);
        Path dataDir = (dirText == null || dirText.isBlank())
                ? Path.of(System.getProperty("user.home"), "AccountingApp")
                : Path.of(dirText.trim());
        dataDir = dataDir.toAbsolutePath().normalize();

        Properties p = new Properties();
        Path file = dataDir.resolve("app.properties");
        try {
            Files.createDirectories(dataDir);
            if (Files.notExists(file)) Files.writeString(file, TEMPLATE);
            try (Reader r = Files.newBufferedReader(file)) {
                p.load(r);
            }
        } catch (IOException e) {
            throw new ConfigException("Cannot read settings file " + file + ": " + e.getMessage(), e);
        }

        String company = pick(env, ENV_COMPANY, p, "company.name", "Office Ledger");
        int keep = number(pick(env, ENV_BACKUP_DAYS, p, "backup.keep.days", "30"), "backup.keep.days");
        int lock = number(pick(env, ENV_AUTOLOCK, p, "autolock.minutes", "15"), "autolock.minutes");
        return new AppConfig(dataDir, company.trim(), keep, lock);
    }

    private static String pick(Map<String, String> env, String envKey, Properties p, String key, String def) {
        String v = env.get(envKey);
        if (v != null && !v.isBlank()) return v;
        return p.getProperty(key, def);
    }

    private static int number(String v, String key) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new ConfigException(key + " must be a whole number, got '" + v + "'");
        }
    }
}
