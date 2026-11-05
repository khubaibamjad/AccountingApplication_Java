package com.office.application.repository.sqlite;

import com.office.application.db.DatabaseManager;
import com.office.application.model.Account;
import com.office.application.model.AccountType;
import com.office.application.repository.AccountRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SqliteAccountRepository implements AccountRepository {

    private static final String SELECT = "SELECT id, code, name, type FROM accounts ";

    private final Jdbc jdbc;

    public SqliteAccountRepository(DatabaseManager db) {
        this.jdbc = new Jdbc(db);
    }

    @Override
    public List<Account> findAll() {
        return jdbc.query(SELECT + "ORDER BY code", this::mapRow);
    }

    @Override
    public List<Account> findByType(AccountType type) {
        return jdbc.query(SELECT + "WHERE type = ? ORDER BY code", this::mapRow, type);
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return jdbc.queryOne(SELECT + "WHERE id = ?", this::mapRow, id);
    }

    @Override
    public Optional<Account> findByCode(String code) {
        return jdbc.queryOne(SELECT + "WHERE code = ?", this::mapRow, code);
    }

    @Override
    public void save(Account a) {
        jdbc.update("""
                INSERT INTO accounts (id, code, name, type) VALUES (?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET code = excluded.code, name = excluded.name, type = excluded.type
                """, a.id(), a.code(), a.name(), a.type());
    }

    private Account mapRow(ResultSet rs) throws SQLException {
        return new Account(
                UUID.fromString(rs.getString("id")),
                rs.getString("code"),
                rs.getString("name"),
                AccountType.valueOf(rs.getString("type")));
    }
}
