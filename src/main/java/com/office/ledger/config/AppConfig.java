package com.office.ledger.config;

import java.nio.file.Path;
import java.util.Properties;

public record AppConfig(String dbURL, String dbname, String dbUser, String dbPassword, String companyName, Path backupDir)
{

    private static final String DEFAULTS_RESOURCE = "/app.properties";
    private static final Path EXTERNAL_FILE = Path.of("app.properties");

    public static AppConfig load()
    {
        return load(EXTERNAL_FILE);
    }

    static AppConfig load(Path externalFile)
    {
        return null;
    }

    private static String required(Properties p, String key)
    {
        return null;
    }

    @Override
    public String toString()
    {
        return null;
    }
}
