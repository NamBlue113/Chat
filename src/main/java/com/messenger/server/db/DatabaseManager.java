package com.messenger.server.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private static HikariDataSource dataSource;
    private static boolean initialized = false;

    // Use 127.0.0.1 to avoid localhost→IPv6 resolution issues on Windows
    private static String host = "127.0.0.1";
    private static int port = 3306;
    private static String database = "gui_chat";
    private static String username = "root";
    private static String password = "";

    private DatabaseManager() {
        throw new UnsupportedOperationException("DatabaseManager is a utility class and cannot be instantiated");
    }

    public static void configure(String host, int port, String database, String username, String password) {
        DatabaseManager.host = host;
        DatabaseManager.port = port;
        DatabaseManager.database = database;
        DatabaseManager.username = username;
        DatabaseManager.password = password;
        logger.info("Database configuration set: {}:{}/{}", host, port, database);
    }

    public static void initialize() {
        if (initialized) {
            logger.warn("DatabaseManager is already initialized");
            return;
        }

        // Bootstrap: ensure database exists before connecting to it
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String bootstrapUrl = String.format(
                    "jdbc:mysql://%s:%d?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    host, port);
            try (Connection c = DriverManager.getConnection(bootstrapUrl, username, password);
                 Statement stmt = c.createStatement()) {
                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + database + "`"
                        + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                logger.info("Database '{}' ensured to exist", database);
            }
        } catch (Exception e) {
            logger.error("Failed to create database '{}': {}", database, e.getMessage());
            return;
        }

        HikariConfig config = new HikariConfig();
        String jdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8",
                host, port, database);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(3);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(10000);
        config.setMaxLifetime(600000);
        config.setLeakDetectionThreshold(60000);
        // Validate connections before handing them out
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(3000);
        // Keep idle connections alive
        config.setKeepaliveTime(60000);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        dataSource = new HikariDataSource(config);
        initialized = true;
        logger.info("Database connection pool initialized: {}", jdbcUrl);

        // Immediately verify the pool works
        if (!testConnection()) {
            logger.error("Pool created but connection test FAILED — check MySQL is running at {}:{}", host, port);
            shutdown();
            return;
        }
        logger.info("Database connection test passed");
    }

    public static Connection getConnection() throws SQLException {
        if (!initialized || dataSource == null) {
            throw new IllegalStateException("DatabaseManager has not been initialized. Call initialize() first.");
        }
        return dataSource.getConnection();
    }

    public static void initializeSchema() {
        try (Connection conn = getConnection();
             InputStream is = DatabaseManager.class.getClassLoader().getResourceAsStream("sql/schema.sql")) {
            if (is == null) {
                logger.error("schema.sql not found in classpath resources");
                return;
            }
            String schema = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String[] statements = schema.split(";");
            try (Statement stmt = conn.createStatement()) {
                for (String sql : statements) {
                    String trimmed = sql.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("/*")) {
                        continue;
                    }
                    String upper = trimmed.toUpperCase();
                    if (upper.startsWith("DROP DATABASE") || upper.startsWith("CREATE DATABASE") || upper.startsWith("USE ")) {
                        logger.debug("Skipping database-level statement: {}", trimmed.substring(0, Math.min(60, trimmed.length())));
                        continue;
                    }
                    try {
                        stmt.execute(trimmed);
                    } catch (SQLException e) {
                        logger.warn("SQL warning during schema init: {} — Statement: {}",
                                e.getMessage(), trimmed.substring(0, Math.min(80, trimmed.length())));
                    }
                }
            }
            logger.info("Database schema initialized successfully");

            // === Migrations ===
            try (Statement migrationStmt = conn.createStatement()) {
                migrationStmt.execute("ALTER TABLE conversations ADD COLUMN IF NOT EXISTS description VARCHAR(500) AFTER avatar_url");
            } catch (SQLException e) {
                // IF NOT EXISTS not supported in older MySQL; try catch column-exists error
                if (e.getMessage() != null && !e.getMessage().contains("Duplicate column")) {
                    logger.warn("Migration 'add description': {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to initialize database schema", e);
        }
    }

    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(5);
        } catch (Exception e) {
            logger.error("Database connection test failed: {}", e.getMessage());
            return false;
        }
    }

    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            initialized = false;
            logger.info("Database connection pool shut down");
        }
    }

    public static boolean isInitialized() {
        return initialized && dataSource != null && !dataSource.isClosed();
    }
}
