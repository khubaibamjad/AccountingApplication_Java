package com.office.application.model;

import java.util.UUID;

public record Party(UUID id, String name, PartyType type,
                    String phone, String region, UUID accountId) {

    public Party {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (type == null) throw new IllegalArgumentException("type is required");
        if (accountId == null) throw new IllegalArgumentException("accountId is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        name = name.trim();
        phone = blankToNull(phone);
        region = blankToNull(region);
    }

    public static Party create(String name, PartyType type, String phone, String region, UUID accountId) {
        return new Party(UUID.randomUUID(), name, type, phone, region, accountId);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    @Override
    public String toString() {
        return name;
    }
}
