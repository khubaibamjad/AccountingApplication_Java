package com.office.application.model;

import java.util.UUID;

public record Party(UUID id, String name, PartyType type, String phone) {

    public Party {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (type == null) throw new IllegalArgumentException("type is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        // phone is optional, so null is allowed
    }

    public static Party create(String name, PartyType type, String phone) {
        return new Party(UUID.randomUUID(), name, type, phone);
    }
}