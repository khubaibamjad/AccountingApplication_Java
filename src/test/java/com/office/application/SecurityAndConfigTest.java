package com.office.application;

import com.office.application.config.AppConfig;
import com.office.application.config.ConfigException;
import com.office.application.db.DatabaseManager;
import com.office.application.model.PartyType;
import com.office.application.security.AuditLog;
import com.office.application.security.PasswordHasher;
import com.office.application.security.Session;
import com.office.application.service.ValidationException;
import com.office.application.ui.AppContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SecurityAndConfigTest {

    @TempDir Path dir;
    AppContext ctx;
    DatabaseManager db;

    @BeforeEach
    void setUp() {
        Session.end();
        db = new DatabaseManager(dir.resolve("AccountingDB"));
        db.migrate();
        ctx = new AppContext(db);
    }

    @AfterEach
    void tearDown() {
        Session.end();
    }

    private static char[] c(String s) { return s.toCharArray(); }

    @Test
    void configDefaultsFileAndEnvOverrides() throws Exception {
        Path data = dir.resolve("data");
        AppConfig cfg = AppConfig.load(Map.of(AppConfig.ENV_DATA_DIR, data.toString()));
        assertEquals(data.toAbsolutePath(), cfg.dataDir());
        assertTrue(Files.exists(cfg.configFile()), "settings file created on first run");
        assertEquals(30, cfg.backupKeepDays());
        assertEquals(15, cfg.autoLockMinutes());
        assertEquals(data.toAbsolutePath().resolve("AccountingDB"), cfg.dbFile());

        Files.writeString(cfg.configFile(), "company.name=Texaxis\nbackup.keep.days=10\n");
        assertEquals("Texaxis", AppConfig.load(Map.of(AppConfig.ENV_DATA_DIR, data.toString())).companyName());
        AppConfig env = AppConfig.load(Map.of(AppConfig.ENV_DATA_DIR, data.toString(), AppConfig.ENV_COMPANY, "From Env",
                AppConfig.ENV_AUTOLOCK, "0"));
        assertEquals("From Env", env.companyName());
        assertEquals(10, env.backupKeepDays());
        assertEquals(0, env.autoLockMinutes());

        assertThrows(ConfigException.class, () -> AppConfig.load(Map.of(AppConfig.ENV_DATA_DIR, data.toString(), AppConfig.ENV_BACKUP_DAYS, "abc")));
        assertThrows(ConfigException.class, () -> AppConfig.load(Map.of(AppConfig.ENV_DATA_DIR, data.toString(), AppConfig.ENV_BACKUP_DAYS, "0")));
    }

    @Test
    void hashingIsSaltedAndVerifies() {
        String h1 = PasswordHasher.hash(c("Secret123")), h2 = PasswordHasher.hash(c("Secret123"));
        assertNotEquals(h1, h2, "salted");
        assertFalse(h1.contains("Secret123"));
        assertTrue(PasswordHasher.verify(c("Secret123"), h1));
        assertFalse(PasswordHasher.verify(c("secret123"), h1));
        assertFalse(PasswordHasher.verify(c("x"), "garbage"));
    }

    @Test
    void firstRunSetupLoginAndRules() throws Exception {
        assertFalse(ctx.auth.hasUsers());
        assertThrows(ValidationException.class, () -> ctx.auth.createAdmin("ad", c("Secret123"), c("Secret123")));          // short name
        assertThrows(ValidationException.class, () -> ctx.auth.createAdmin("admin", c("short1"), c("short1")));            // short pw
        assertThrows(ValidationException.class, () -> ctx.auth.createAdmin("admin", c("onlyletters"), c("onlyletters")));  // no digit
        assertThrows(ValidationException.class, () -> ctx.auth.createAdmin("admin", c("Secret123"), c("Secret124")));      // mismatch
        ctx.auth.createAdmin("admin", c("Secret123"), c("Secret123"));
        assertTrue(ctx.auth.hasUsers());

        // a second admin cannot be created without being logged in
        assertThrows(ValidationException.class, () -> ctx.auth.createAdmin("intruder", c("Secret123"), c("Secret123")));

        // stored value is a hash, never the password
        try (Connection con = db.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT password_hash FROM users")) {
            rs.next();
            assertTrue(rs.getString(1).startsWith("pbkdf2$"));
            assertFalse(rs.getString(1).contains("Secret123"));
        }

        ValidationException unknown = assertThrows(ValidationException.class, () -> ctx.auth.login("nobody", c("Secret123")));
        assertEquals("Wrong username or password", unknown.getMessage());     // no hint that the user doesn't exist
        assertThrows(ValidationException.class, () -> ctx.auth.login("admin", c("wrong")));
        ctx.auth.login("ADMIN", c("Secret123"));                              // username is case-insensitive
        assertEquals("admin", Session.username());

        ctx.auth.createAdmin("second", c("Another99"), c("Another99"));       // allowed when logged in
        assertEquals(List.of("admin", "second"), ctx.auth.usernames());
    }

    @Test
    void builtInAdminWithForcedPasswordChange() {
        ctx.auth.ensureDefaultAdmin();
        ctx.auth.ensureDefaultAdmin();                                       // idempotent
        assertEquals(List.of("admin"), ctx.auth.usernames());
        ctx.auth.login(c(com.office.application.security.AuthService.DEFAULT_PASSWORD));
        assertTrue(ctx.auth.mustChangePassword());
        ctx.auth.changePassword("admin", c("Admin@123"), c("MyShop2026"), c("MyShop2026"));
        assertFalse(ctx.auth.mustChangePassword());
        ctx.auth.logout();
        assertThrows(ValidationException.class, () -> ctx.auth.login(c("Admin@123")));   // default no longer works
        ctx.auth.login(c("MyShop2026"));
        assertFalse(ctx.auth.mustChangePassword());
    }

    @Test
    void lockoutAfterFiveFailures() {
        ctx.auth.createAdmin("admin", c("Secret123"), c("Secret123"));
        for (int i = 0; i < 4; i++) assertThrows(ValidationException.class, () -> ctx.auth.login("admin", c("bad")));
        ValidationException locked = assertThrows(ValidationException.class, () -> ctx.auth.login("admin", c("bad")));
        assertTrue(locked.getMessage().contains("locked"));
        // even the right password is refused while locked, and a restart does not reset it
        AppContext restarted = new AppContext(new DatabaseManager(dir.resolve("AccountingDB")));
        ValidationException still = assertThrows(ValidationException.class, () -> restarted.auth.login("admin", c("Secret123")));
        assertTrue(still.getMessage().contains("Try again"));
    }

    @Test
    void changePasswordAndAuditTrail() {
        ctx.auth.createAdmin("admin", c("Secret123"), c("Secret123"));
        ctx.auth.login("admin", c("Secret123"));
        assertThrows(ValidationException.class, () -> ctx.auth.changePassword("admin", c("wrong999"), c("NewPass123"), c("NewPass123")));
        assertThrows(ValidationException.class, () -> ctx.auth.changePassword("admin", c("Secret123"), c("Secret123"), c("Secret123")));
        ctx.auth.changePassword("admin", c("Secret123"), c("NewPass123"), c("NewPass123"));
        ctx.auth.logout();
        assertThrows(ValidationException.class, () -> ctx.auth.login("admin", c("Secret123")));
        ctx.auth.login("admin", c("NewPass123"));

        ctx.parties.createParty("Audit Co", PartyType.CUSTOMER, null, null);
        List<AuditLog.Entry> log = ctx.audit.latest(50);
        assertEquals("PARTY_CREATED", log.get(0).action());
        assertEquals("admin", log.get(0).username());
        assertTrue(log.stream().anyMatch(e -> e.action().equals("PASSWORD_CHANGED")));
        assertTrue(log.stream().anyMatch(e -> e.action().equals("LOGIN_FAILED")));
    }

    @Test
    void databaseFilesAreOwnerOnlyOnLinux() throws Exception {
        if (!dir.getFileSystem().supportedFileAttributeViews().contains("posix")) return;
        assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(
                Files.getPosixFilePermissions(dir.resolve("AccountingDB"))));
        assertEquals("rwx------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)));
        assertEquals("ok", db.integrityCheck());
    }
}
