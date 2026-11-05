package com.office.application.model;

public enum PartyType {
    VENDOR, CUSTOMER, BOTH;

    /** Friendly name for screens. (Not toString(): the enum name is what the database stores.) */
    public String label() {
        return switch (this) {
            case VENDOR -> "Vendor";
            case CUSTOMER -> "Customer";
            case BOTH -> "Customer & vendor";
        };
    }
}
