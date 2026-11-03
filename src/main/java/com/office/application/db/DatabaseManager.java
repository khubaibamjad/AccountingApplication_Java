package com.office.application.db;

import org.flywaydb.core.Flyway;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public class DatabaseManager {

    private final String dbUrl;


    public DatabaseManager()
    {
        this(Path.of(System.getProperty("user.home"), "AccountingApp","AccountingDB"));
    }

    public DatabaseManager(Path dbFile)
    {
        try{
            Files.createDirectories(dbFile.getParent());
        } catch(IOException e){
            throw new RuntimeException("Unable to create the database file",e);
        }
        dbUrl="jdbc:sqlite:"+dbFile.toAbsolutePath();
    }

    public void migrate()
    {
        Flyway.configure()
                .dataSource(dbUrl,null,null)
                .load()
                .migrate();
    }

    public Connection getConnection() throws SQLException
    {
        Connection connection = DriverManager.getConnection(getDbUrl());
        try(Statement st = connection.createStatement())
        {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    public String getDbUrl()
    {
        return dbUrl;
    }
}
