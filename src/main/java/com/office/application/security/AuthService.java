package com.office.application.security;

import com.office.application.db.DatabaseManager;
import com.office.application.service.ValidationException;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Single built-in admin login. On first start the user "admin" is created with DEFAULT_PASSWORD
 * (stored hashed) and must change it at the first login, so the published default stops working.
 * Lockout after repeated failures. Passwords arrive as char[] and are wiped after use.
 */
public class AuthService {

    public static final String ADMIN = "admin";
    /** Only valid until the first login: the app then forces a new password. */
    public static final String DEFAULT_PASSWORD = "Admin@123";

    public static final int MAX_FAILED = 5;
    public static final Duration LOCKOUT = Duration.ofMinutes(5);
    private static final String DUMMY_HASH = PasswordHasher.hash("not-a-real-password".toCharArray());

    private final DatabaseManager db;
    private final AuditLog audit;

    public AuthService(DatabaseManager db, AuditLog audit) {
        this.db = db;
        this.audit = audit;
    }

    public boolean hasUsers() {
        try (Connection c = db.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Could not read users", e);
        }
    }

    /** Creates the built-in admin with the default password if it doesn't exist yet. Safe to call on every start. */
    public void ensureDefaultAdmin() {
        if (find(ADMIN).isPresent()) return;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR IGNORE INTO users (id, username, password_hash, role, must_change_pw, created_at) VALUES (?, ?, ?, 'ADMIN', 1, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, ADMIN);
            ps.setString(3, PasswordHasher.hash(DEFAULT_PASSWORD.toCharArray()));
            ps.setString(4, Instant.now().toString());
            if (ps.executeUpdate() > 0) audit.record("USER_CREATED", "Built-in admin created with the default password");
        } catch (SQLException e) {
            throw new RuntimeException("Could not create the admin user", e);
        }
    }

    /** Login as the built-in admin. */
    public User login(char[] password) {
        return login(ADMIN, password);
    }

    /** True while the admin still uses the default password (first login). */
    public boolean mustChangePassword() {
        return find(Session.username()).map(User::mustChangePassword).orElse(false);
    }

    /** Creates an admin. Allowed without login only when no user exists yet (first run). */
    public User createAdmin(String username, char[] password, char[] confirm) {
        try {
            if (hasUsers() && !Session.isLoggedIn()) throw new ValidationException("Only a logged-in admin can add users");
            String u = username == null ? "" : username.trim();
            if (!u.matches("[A-Za-z0-9._-]{3,50}"))
                throw new ValidationException("Username: 3-50 characters, letters, digits, . _ - only");
            if (find(u).isPresent()) throw new ValidationException("Username '" + u + "' already exists");
            checkPassword(u, password, confirm);

            User user = new User(UUID.randomUUID(), u, PasswordHasher.hash(password), "ADMIN", 0, null, null, false);
            try (Connection c = db.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO users (id, username, password_hash, role, created_at) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, user.id().toString());
                ps.setString(2, user.username());
                ps.setString(3, user.passwordHash());
                ps.setString(4, user.role());
                ps.setString(5, Instant.now().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Could not create user", e);
            }
            audit.record("USER_CREATED", "Admin '" + u + "' created");
            return user;
        } finally {
            wipe(password, confirm);
        }
    }

    /** Returns the user on success. Same message for unknown user and wrong password (no username probing). */
    public User login(String username, char[] password) {
        try {
            String u = username == null ? "" : username.trim();
            Optional<User> found = find(u);
            if (found.isEmpty()) {
                PasswordHasher.verify(password, DUMMY_HASH);   // same time cost as a real check
                audit.record("LOGIN_FAILED", "Unknown user '" + u + "'");
                throw new ValidationException("Wrong username or password");
            }
            User user = found.get();
            Instant now = Instant.now();
            if (user.lockedUntil() != null && user.lockedUntil().isAfter(now)) {
                long mins = Math.max(1, Duration.between(now, user.lockedUntil()).toMinutes() + 1);
                throw new ValidationException("Too many failed attempts. Try again in " + mins + " minute(s).");
            }
            if (!PasswordHasher.verify(password, user.passwordHash())) {
                int fails = user.failedAttempts() + 1;
                Instant lock = fails >= MAX_FAILED ? now.plus(LOCKOUT) : null;
                update("UPDATE users SET failed_attempts = ?, locked_until = ? WHERE id = ?",
                        fails >= MAX_FAILED ? 0 : fails, lock == null ? null : lock.toString(), user.id().toString());
                audit.record("LOGIN_FAILED", "Wrong password for '" + user.username() + "'" + (lock != null ? " - locked 5 min" : ""));
                throw new ValidationException(lock != null
                        ? "Too many failed attempts. Login is locked for " + LOCKOUT.toMinutes() + " minutes."
                        : "Wrong username or password (" + (MAX_FAILED - fails) + " attempts left)");
            }
            update("UPDATE users SET failed_attempts = 0, locked_until = NULL, last_login_at = ? WHERE id = ?",
                    now.toString(), user.id().toString());
            Session.start(user);
            audit.record("LOGIN", "Logged in");
            return user;
        } finally {
            wipe(password);
        }
    }

    public void logout() {
        if (Session.isLoggedIn()) audit.record("LOGOUT", "Logged out / locked");
        Session.end();
    }

    public void changePassword(String username, char[] current, char[] next, char[] confirm) {
        try {
            User user = find(username).orElseThrow(() -> new ValidationException("User not found"));
            if (!PasswordHasher.verify(current, user.passwordHash())) throw new ValidationException("Current password is wrong");
            checkPassword(user.username(), next, confirm);
            if (PasswordHasher.verify(next, user.passwordHash())) throw new ValidationException("New password must be different");
            update("UPDATE users SET password_hash = ?, must_change_pw = 0 WHERE id = ?", PasswordHasher.hash(next), user.id().toString());
            audit.record("PASSWORD_CHANGED", "Password changed for '" + user.username() + "'");
        } finally {
            wipe(current, next, confirm);
        }
    }

    public List<String> usernames() {
        try (Connection c = db.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT username FROM users ORDER BY username")) {
            List<String> out = new java.util.ArrayList<>();
            while (rs.next()) out.add(rs.getString(1));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("Could not read users", e);
        }
    }

    // --- helpers -------------------------------------------------------------

    private static void checkPassword(String username, char[] password, char[] confirm) {
        if (password == null || password.length < 8) throw new ValidationException("Password must be at least 8 characters");
        if (password.length > 128) throw new ValidationException("Password is too long");
        if (confirm == null || !Arrays.equals(password, confirm)) throw new ValidationException("Passwords do not match");
        String p = new String(password);
        if (p.equalsIgnoreCase(username)) throw new ValidationException("Password cannot be the same as the username");
        if (!p.matches(".*[A-Za-z].*") || !p.matches(".*[0-9].*"))
            throw new ValidationException("Password must contain at least one letter and one number");
    }

    private Optional<User> find(String username) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, username, password_hash, role, failed_attempts, locked_until, last_login_at, must_change_pw FROM users WHERE username = ?")) {
            ps.setString(1, username == null ? "" : username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new User(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), instant(rs.getString(6)), instant(rs.getString(7)), rs.getInt(8) == 1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not read users", e);
        }
    }

    private void update(String sql, Object... params) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not update user", e);
        }
    }

    private static Instant instant(String s) {
        return s == null ? null : Instant.parse(s);
    }

    private static void wipe(char[]... arrays) {
        for (char[] a : arrays) if (a != null) Arrays.fill(a, '\0');
    }
}
