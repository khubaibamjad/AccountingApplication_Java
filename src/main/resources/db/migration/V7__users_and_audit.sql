-- Login accounts. Passwords are never stored: only a salted PBKDF2 hash.
CREATE TABLE users
(
    id              VARCHAR(36)  PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE COLLATE NOCASE,
    password_hash   VARCHAR(200) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'ADMIN',
    failed_attempts INTEGER      NOT NULL DEFAULT 0,
    must_change_pw  INTEGER      NOT NULL DEFAULT 0,
    locked_until    TEXT,
    created_at      TEXT         NOT NULL,
    last_login_at   TEXT
);

-- Who did what, and when.
CREATE TABLE audit_log
(
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    at       TEXT         NOT NULL,
    username VARCHAR(50)  NOT NULL,
    action   VARCHAR(50)  NOT NULL,
    details  VARCHAR(500)
);
CREATE INDEX idx_audit_at ON audit_log (at);
