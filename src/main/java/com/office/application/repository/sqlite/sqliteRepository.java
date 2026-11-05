package com.office.application.repository.sqlite;

import com.office.application.db.DatabaseManager;
import com.office.application.model.Account;
import com.office.application.model.AccountType;
import com.office.application.repository.AccountRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class sqliteRepository implements AccountRepository {
    private final DatabaseManager db;

    public sqliteRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public List<Account> findAll() {
        String sql = "Select id, code, name, type FROM accounts ORDER BY code";
        List<Account> result = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load accounts", e);
        }
        return result;
    }

    @Override
    public Optional<Account> findByCode(String code) {
        String sql = "SELECT id, code, name, type from accounts where code = ?";

        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1,code);
            try(ResultSet rs = ps.executeQuery())
            {
                if(rs.next()){
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find account"+code, e);
        }
    }

    @Override
    public void save(Account account) {

        String sql = """
            INSERT INTO accounts (id, code, name, type)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                code = excluded.code,
                name = excluded.name,
                type = excluded.type
            """;

        try(Connection con = db.getConnection();
            PreparedStatement ps = con.prepareStatement(sql))
        {
            ps.setString(1,account.id().toString());
            ps.setString(2,account.code());
            ps.setString(3,account.name());
            ps.setString(4,account.type().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save account"+account.code(),e);
        }

    }

    @Override
    public Optional<Account> findById(UUID id) {
        String sql = "SELECT id, code, name, type FROM accounts WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id.toString());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find account " + id, e);
        }
    }

    public Account mapRow(ResultSet rs) throws SQLException {
        return new Account(UUID.fromString(rs.getString("id")),
                rs.getString("code"),
                rs.getString("name"),
                AccountType.valueOf(rs.getString("type")));

    }
}
