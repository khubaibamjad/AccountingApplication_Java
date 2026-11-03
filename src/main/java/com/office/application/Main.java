package com.office.application;

import com.office.application.config.AppConfig;
import com.office.application.db.DatabaseManager;

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
    }
}
