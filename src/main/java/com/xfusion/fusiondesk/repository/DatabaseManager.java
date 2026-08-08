package com.xfusion.fusiondesk.repository;

import com.xfusion.fusiondesk.exception.DatabaseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public class DatabaseManager {
    private final Path dbPath;

    public DatabaseManager(Path dbPath) { this.dbPath = dbPath.toAbsolutePath().normalize(); }
    public static DatabaseManager defaultDatabase() { return new DatabaseManager(Path.of("data", "fusiondesk.db")); }
    public Path dbPath() { return dbPath; }

    public Connection openConnection() throws SQLException {
        ensureParentDirectory();
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA journal_mode = WAL");
        }
        return connection;
    }

    public void initializeSchema() {
        String[] statements = {
            """
            CREATE TABLE IF NOT EXISTS tickets (
              id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, description TEXT NOT NULL,
              submitter TEXT NOT NULL,
              status TEXT NOT NULL CHECK(status IN ('NEW','IN_PROGRESS','RESOLVED','CLOSED')),
              category TEXT CHECK(category IS NULL OR category IN ('ACCOUNT_ACCESS','SOFTWARE_FAILURE','NETWORK','HARDWARE_OFFICE','BUSINESS_SYSTEM','OTHER')),
              priority TEXT NOT NULL CHECK(priority IN ('P0','P1','P2','P3')),
              version INTEGER NOT NULL DEFAULT 0 CHECK(version >= 0), dedup_key TEXT NOT NULL,
              created_at TEXT NOT NULL, updated_at TEXT NOT NULL)
            """,
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_tickets_active_dedup ON tickets(dedup_key) WHERE status <> 'CLOSED'",
            "CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets(status)",
            "CREATE INDEX IF NOT EXISTS idx_tickets_category_priority ON tickets(category, priority)",
            "CREATE INDEX IF NOT EXISTS idx_tickets_submitter ON tickets(submitter)",
            "CREATE INDEX IF NOT EXISTS idx_tickets_created_at ON tickets(created_at)",
            """
            CREATE TABLE IF NOT EXISTS audit_events (
              id INTEGER PRIMARY KEY AUTOINCREMENT, ticket_id INTEGER NOT NULL, event_type TEXT NOT NULL,
              before_data TEXT, after_data TEXT, created_at TEXT NOT NULL,
              FOREIGN KEY(ticket_id) REFERENCES tickets(id))
            """,
            "CREATE INDEX IF NOT EXISTS idx_audit_ticket_time ON audit_events(ticket_id, created_at)",
            """
            CREATE TABLE IF NOT EXISTS ai_suggestions (
              id INTEGER PRIMARY KEY AUTOINCREMENT, ticket_id INTEGER NOT NULL,
              suggested_category TEXT NOT NULL CHECK(suggested_category IN ('ACCOUNT_ACCESS','SOFTWARE_FAILURE','NETWORK','HARDWARE_OFFICE','BUSINESS_SYSTEM','OTHER')),
              suggested_priority TEXT NOT NULL CHECK(suggested_priority IN ('P0','P1','P2','P3')),
              summary TEXT NOT NULL, reason TEXT NOT NULL, raw_response TEXT NOT NULL,
              model TEXT NOT NULL, prompt_version TEXT NOT NULL,
              status TEXT NOT NULL CHECK(status IN ('PENDING','CONFIRMED','MODIFIED','REJECTED')),
              final_category TEXT CHECK(final_category IS NULL OR final_category IN ('ACCOUNT_ACCESS','SOFTWARE_FAILURE','NETWORK','HARDWARE_OFFICE','BUSINESS_SYSTEM','OTHER')),
              final_priority TEXT CHECK(final_priority IS NULL OR final_priority IN ('P0','P1','P2','P3')),
              created_at TEXT NOT NULL, reviewed_at TEXT,
              FOREIGN KEY(ticket_id) REFERENCES tickets(id))
            """,
            "CREATE INDEX IF NOT EXISTS idx_ai_suggestions_ticket ON ai_suggestions(ticket_id, created_at)",
            "CREATE TABLE IF NOT EXISTS app_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)"
        };
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            for (String sql : statements) statement.execute(sql);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to initialize database", e);
        }
    }

    public <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (Exception e) {
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new DatabaseException("Database transaction failed", e);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to open database", e);
        }
    }

    public boolean metadataExists(String key) {
        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement("SELECT 1 FROM app_metadata WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { throw new DatabaseException("Failed to read metadata", e); }
    }

    public void putMetadata(String key, String value) {
        inTransaction(c -> { try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO app_metadata(key,value) VALUES(?,?)")) {
            ps.setString(1, key); ps.setString(2, value); ps.executeUpdate(); return null;
        }});
    }

    private void ensureParentDirectory() {
        Path parent = dbPath.getParent();
        if (parent == null) return;
        try { Files.createDirectories(parent); }
        catch (IOException e) { throw new DatabaseException("Failed to create database directory: " + parent, e); }
    }

    @FunctionalInterface public interface SqlWork<T> { T execute(Connection connection) throws Exception; }
}
