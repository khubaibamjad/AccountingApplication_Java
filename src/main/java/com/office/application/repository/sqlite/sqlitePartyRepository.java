package com.office.application.repository.sqlite;

import com.office.application.db.DatabaseManager;
import com.office.application.model.Party;
import com.office.application.model.PartyType;
import com.office.application.repository.PartyRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class sqlitePartyRepository implements PartyRepository {

    private static final String COLUMNS = "id, name, type, phone, region, account_id";

    private final DatabaseManager db;

    public sqlitePartyRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public List<Party> findAll() {
        String sql = "SELECT " + COLUMNS + " FROM parties ORDER BY name";
        List<Party> result = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load parties", e);
        }
        return result;
    }

    @Override
    public Optional<Party> findById(UUID id) {
        String sql = "SELECT " + COLUMNS + " FROM parties WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id.toString());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find party " + id, e);
        }
    }

    @Override
    public List<Party> findByType(PartyType type) {
        String sql = "SELECT " + COLUMNS + " FROM parties WHERE type = ? ORDER BY name";
        List<Party> result = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, type.name());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load parties of type " + type, e);
        }
        return result;
    }

    @Override
    public void save(Party party) {
        String sql = """
                INSERT INTO parties (id, name, type, phone, region, account_id)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name       = excluded.name,
                    type       = excluded.type,
                    phone      = excluded.phone,
                    region     = excluded.region,
                    account_id = excluded.account_id
                """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, party.id().toString());
            ps.setString(2, party.name());
            ps.setString(3, party.type().name());
            ps.setString(4, party.phone());                    // may be null
            ps.setString(5, party.region());                   // may be null
            ps.setString(6, party.accountId().toString());
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save party " + party.name(), e);
        }
    }

    // Order must match the record: id, name, type, phone, region, accountId
    private Party mapRow(ResultSet rs) throws SQLException {
        return new Party(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                PartyType.valueOf(rs.getString("type")),
                rs.getString("phone"),
                rs.getString("region"),
                UUID.fromString(rs.getString("account_id"))
        );
    }
}