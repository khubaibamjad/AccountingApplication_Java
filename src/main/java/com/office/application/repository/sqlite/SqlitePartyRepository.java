package com.office.application.repository.sqlite;

import com.office.application.db.DatabaseManager;
import com.office.application.model.Account;
import com.office.application.model.Party;
import com.office.application.model.PartyType;
import com.office.application.repository.PartyRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SqlitePartyRepository implements PartyRepository {

    private static final String SELECT = "SELECT id, name, type, phone, region, account_id FROM parties ";
    private static final String UPSERT = """
            INSERT INTO parties (id, name, type, phone, region, account_id) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET name = excluded.name, type = excluded.type,
                phone = excluded.phone, region = excluded.region, account_id = excluded.account_id
            """;

    private final Jdbc jdbc;

    public SqlitePartyRepository(DatabaseManager db) {
        this.jdbc = new Jdbc(db);
    }

    @Override
    public List<Party> findAll() {
        return jdbc.query(SELECT + "ORDER BY name", this::mapRow);
    }

    @Override
    public Optional<Party> findById(UUID id) {
        return jdbc.queryOne(SELECT + "WHERE id = ?", this::mapRow, id);
    }

    @Override
    public List<Party> findByType(PartyType type) {
        return jdbc.query(SELECT + "WHERE type = ? ORDER BY name", this::mapRow, type);
    }

    @Override
    public void save(Party p) {
        jdbc.update(UPSERT, params(p));
    }

    @Override
    public void saveWithAccount(Party p, Account a) {
        jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = Jdbc.prepare(conn,
                    "INSERT INTO accounts (id, code, name, type) VALUES (?, ?, ?, ?)",
                    a.id(), a.code(), a.name(), a.type())) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = Jdbc.prepare(conn, UPSERT, params(p))) {
                ps.executeUpdate();
            }
        });
    }

    private static Object[] params(Party p) {
        return new Object[]{p.id(), p.name(), p.type(), p.phone(), p.region(), p.accountId()};
    }

    private Party mapRow(ResultSet rs) throws SQLException {
        return new Party(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                PartyType.valueOf(rs.getString("type")),
                rs.getString("phone"),
                rs.getString("region"),
                UUID.fromString(rs.getString("account_id")));
    }
}
