package com.office.application;

import com.office.application.config.AppConfig;
import com.office.application.db.DatabaseManager;
import com.office.application.model.Account;
import com.office.application.model.AccountType;
import com.office.application.repository.AccountRepository;
import com.office.application.repository.sqlite.sqliteRepository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) throws SQLException {
//        var config = AppConfig.load();
//        System.out.println("config is null? " + (config == null));
//        System.out.println(config);


        DatabaseManager db = new DatabaseManager();
        System.out.println(db.getDbUrl());

        db.migrate();
        System.out.println("DB migration done");

        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM accounts")) {
            rs.next();
            System.out.println("Accounts in DB: " + rs.getInt(1));
        }

//        Account b = Account.create("5300", "Electricity Expense", AccountType.EXPENSE);
//        System.out.println(b);

        AccountRepository accounts = new sqliteRepository(db);

//        for (Account a : accounts.findAll()) {
//            System.out.println(a.code() + "  " + a.name() + "  (" + a.type() + ")");


        if (accounts.findByCode("5300").isEmpty()) {
            accounts.save(Account.create("5300", "updated", AccountType.EXPENSE));
            System.out.println("Saved 5300");
        }
        Account existing = accounts.findByCode("5300").orElseThrow();

        Account updated = new Account(
                existing.id(),       // same id, so it UPDATEs instead of INSERTing
                existing.code(),
                "updated",           // new name
                AccountType.LIABILITY
        );
        accounts.save(updated);
        System.out.println("Updated 5300");
        accounts.save(updated);
        System.out.println("Updated 5300");

        System.out.println(accounts.findByCode("5300").orElseThrow());
        System.out.println(accounts.findByCode("9999").isPresent() ? "found" : "9999 not found");

        for (Account a : accounts.findAll()) {
            System.out.println(a.code() + "  " + a.name() + "  (" + a.type() + ")");
        }

    }
}
