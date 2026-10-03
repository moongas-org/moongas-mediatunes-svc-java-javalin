/*
 * Copyright 2026
 * moongas.org 
 */
package org.moongas.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DbConnection implements AutoCloseable {
    private final Connection conn;

    public DbConnection(String url) throws SQLException {
        conn = DriverManager.getConnection(url);
        if (conn != null && !conn.isClosed()) {
            System.out.println("Connection to SQLite has been established.");

            // Ping the database to check if it's truly alive (timeout in seconds)
            if (conn.isValid(2)) {
                System.out.println("Success: Connection is valid and responsive.");
            } else {
                System.out.println("Error: Database is not valid");
                throw new IllegalArgumentException("Database is not valid");
            }
        } else {
            System.out.println("Error: Database connection failed");
            throw new IllegalArgumentException("Database connection failed");
        }
    }

    public PreparedStatement prepareStatement(String sqlQuery) throws SQLException {
        return conn.prepareStatement(sqlQuery);
    }

    public Statement createStatement() throws SQLException {
        return conn.createStatement();
    }

    public boolean hasTable(String tableName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            // ignore
        }
    }
}
