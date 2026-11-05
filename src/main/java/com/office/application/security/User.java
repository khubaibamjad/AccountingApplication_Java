package com.office.application.security;

import java.time.Instant;
import java.util.UUID;

public record User(UUID id, String username, String passwordHash, String role,
                   int failedAttempts, Instant lockedUntil, Instant lastLoginAt, boolean mustChangePassword) {
    @Override
    public String toString() {
        return username;   // never print the hash
    }
}
