package com.office.application.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DatabaseManager {

    private final String dbUrl;


    public DatabaseManager()
    {
        this(Path.of(System.getProperty("user.home"), "AccountingApp", "AccountingDB"));
    }

    public DatabaseManager(Path dbFile)
    {
        try{
            Files.createDirectories(dbFile.getParent());
        } catch(IOException e){
            throw new RuntimeException("Unable to create the database file",e);
        }
        dbUrl="jdbc:sqlite:"+dbFile.getFileName();
    }
}
