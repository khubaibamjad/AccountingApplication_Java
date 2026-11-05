package com.office.application.model;

import java.util.UUID;

/**
 * A customer/vendor. registered = tax-registered business with an NTN (required);
 * unregistered parties have no NTN.
 */
public record Party(UUID id, String name, PartyType type, String phone, String region, UUID accountId,
                    boolean registered, String ntn) {

    public Party {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (type == null) throw new IllegalArgumentException("type is required");
        if (accountId == null) throw new IllegalArgumentException("accountId is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        name = name.trim();
        phone = blankToNull(phone);
        region = blankToNull(region);
        ntn = registered ? blankToNull(ntn) : null;
    }

    /** Unregistered party (no NTN). */
    public Party(UUID id, String name, PartyType type, String phone, String region, UUID accountId) {
        this(id, name, type, phone, region, accountId, false, null);
    }

    public static Party create(String name, PartyType type, String phone, String region, UUID accountId) {
        return new Party(UUID.randomUUID(), name, type, phone, region, accountId, false, null);
    }

    public static Party create(String name, PartyType type, String phone, String region, UUID accountId,
                               boolean registered, String ntn) {
        return new Party(UUID.randomUUID(), name, type, phone, region, accountId, registered, ntn);
    }

    public String status() {
        return registered ? "Registered" : "Unregistered";
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** Shown in drop-downs: "ABC Traders (Reg.)". */
    @Override
    public String toString() {
        return registered ? name + "  (Reg.)" : name;
    }
}
