package com.office.application.model;

import java.util.UUID;

/** Fixed ids of the accounts seeded in V1__init.sql. */
public final class SystemAccounts {
    private SystemAccounts() { }

    public static final UUID CASH          = UUID.fromString("00000000-0000-0000-0000-000000001000");
    public static final UUID BANK          = UUID.fromString("00000000-0000-0000-0000-000000001010");
    public static final UUID INVENTORY     = UUID.fromString("00000000-0000-0000-0000-000000001200");
    public static final UUID OWNER_CAPITAL = UUID.fromString("00000000-0000-0000-0000-000000003000");
    public static final UUID SALES         = UUID.fromString("00000000-0000-0000-0000-000000004000");
}
