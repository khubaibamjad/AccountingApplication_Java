package com.office.application.model;

import java.util.UUID;

public record Account(UUID id, String code, String name, AccountType type)
{
    public Account {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (type == null) throw new IllegalArgumentException("type is required");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
    }

    public static Account create(String code, String name, AccountType type)
    {
        return new Account(UUID.randomUUID(),code,name,type);
    }
}
