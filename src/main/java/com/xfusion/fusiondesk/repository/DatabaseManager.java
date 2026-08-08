package com.xfusion.fusiondesk.repository;

import com.xfusion.fusiondesk.exception.DatabaseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.Objects;

public class DatabaseManager {
    private final DatabaseType type;
    private final DatabaseDialect dialect;
    private final Path dbPath;
    private final String jdbcUrl;
    private final String username;
    private final String password;

    /** Explicit paths always select SQLite; this keeps temporary-file tests deterministic. */
    public DatabaseManager(Path dbPath) {
        this.type = DatabaseType.SQLITE;
        this.dialect = new SqliteDialect();
        this.dbPath = Objects.requireNonNull(dbPath).toAbsolutePath().normalize();
        this.jdbcUrl = "jdbc:sqlite:" + this.dbPath;
        this.username = null;
        this.password = null;
    }

    private DatabaseManager(String jdbcUrl, String username, String password) {
        this.type = DatabaseType.MYSQL;
        this.dialect = new MySqlDialect();
        this.dbPath = null;
        this.jdbcUrl = requireConfiguration(jdbcUrl, "MYSQL_URL");
        this.username = requireConfiguration(username, "MYSQL_USER");
        this.password = requireConfiguration(password, "MYSQL_PASSWORD");
    }

    public static DatabaseManager defaultDatabase() {
        DatabaseType configured = DatabaseType.parse(System.getenv("FUSIONDESK_DB_TYPE"));
        if (configured == DatabaseType.MYSQL) {
            return mysql(System.getenv("MYSQL_URL"), System.getenv("MYSQL_USER"), System.getenv("MYSQL_PASSWORD"));
        }
        return new DatabaseManager(Path.of("data", "fusiondesk.db"));
    }

    public static DatabaseManager mysql(String jdbcUrl, String username, String password) {
        return new DatabaseManager(jdbcUrl, username, password);
    }

    public DatabaseType type() { return type; }
    public Path dbPath() { return dbPath; }
    public String jdbcUrl() { return jdbcUrl; }

    public Connection openConnection() throws SQLException {
        if (type == DatabaseType.SQLITE) ensureParentDirectory();
        Connection connection;
        try {
            connection = type == DatabaseType.MYSQL
                    ? DriverManager.getConnection(jdbcUrl, username, password)
                    : DriverManager.getConnection(jdbcUrl);
            dialect.configure(connection);
            return connection;
        } catch (SQLException e) {
            throw new SQLException("Database connection failed for " + safeLocation(), e.getSQLState(), e.getErrorCode(), e);
        }
    }

    public void initializeSchema() {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            for (String sql : dialect.schemaStatements()) statement.execute(sql);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to initialize database at " + safeLocation(), e);
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
            throw new DatabaseException("Database connection failed for " + safeLocation(), e);
        }
    }

    public void bindInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        dialect.bindInstant(statement, index, value);
    }

    public Instant readInstant(ResultSet resultSet, String column) throws SQLException {
        return dialect.readInstant(resultSet, column);
    }

    public boolean isDuplicateKey(Throwable error) { return dialect.isDuplicateKey(error); }

    public boolean metadataExists(String key) {
        String keyColumn = dialect.metadataKeyColumn();
        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM app_metadata WHERE " + keyColumn + "=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { throw new DatabaseException("Failed to read metadata", e); }
    }

    public void putMetadata(String key, String value) {
        String sql = type == DatabaseType.MYSQL
                ? "INSERT INTO app_metadata(meta_key,value) VALUES(?,?) ON DUPLICATE KEY UPDATE value=VALUES(value)"
                : "INSERT OR REPLACE INTO app_metadata(key,value) VALUES(?,?)";
        inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, key); ps.setString(2, value); ps.executeUpdate(); return null;
            }
        });
    }

    private String safeLocation() {
        if (type == DatabaseType.SQLITE) return dbPath.toString();
        try {
            java.net.URI uri = java.net.URI.create(jdbcUrl.substring("jdbc:".length()));
            return "mysql://" + uri.getAuthority() + uri.getPath();
        } catch (RuntimeException ignored) {
            return "configured MySQL database";
        }
    }

    private void ensureParentDirectory() {
        Path parent = dbPath.getParent();
        if (parent == null) return;
        try { Files.createDirectories(parent); }
        catch (IOException e) { throw new DatabaseException("Failed to create database directory: " + parent, e); }
    }

    private static String requireConfiguration(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new DatabaseException("MySQL configuration is missing: " + name + ".", null);
        }
        return value.strip();
    }

    @FunctionalInterface public interface SqlWork<T> { T execute(Connection connection) throws Exception; }
}
