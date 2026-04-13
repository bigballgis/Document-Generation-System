package com.docgen.service;

import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.docgen.util.ParameterResolver;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for executing SQL queries against configured databases.
 * Supports PostgreSQL, MySQL, SQL Server, and Oracle.
 * Uses HikariCP connection pools cached per unique connection config.
 */
@Service
public class DatabaseDataSourceService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseDataSourceService.class);

    static final int DEFAULT_MAX_POOL_SIZE = 5;
    static final int DEFAULT_CONNECTION_TIMEOUT = 5000;

    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    /**
     * Cache of HikariCP DataSource instances keyed by a connection fingerprint.
     * Visible for testing.
     */
    final ConcurrentHashMap<String, DataSource> poolCache = new ConcurrentHashMap<>();

    public DatabaseDataSourceService(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Execute a SQL query using the provided database configuration and parameters.
     *
     * @param configJson  the data source configJson (with encrypted password)
     * @param parameters  runtime parameters to bind into the SQL query
     * @return query results as a list of row maps
     */
    public List<Map<String, Object>> fetchData(String configJson, Map<String, Object> parameters) {
        Map<String, Object> config = parseConfig(configJson);
        String query = getString(config, "query", "");
        if (query.isBlank()) {
            throw new BusinessException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "SQL查询语句不能为空", HttpStatus.BAD_REQUEST);
        }

        List<Map<String, Object>> paramDefs = ParameterResolver.extractParameterDefs(config);
        Map<String, Object> resolvedParams = ParameterResolver.resolve(paramDefs, parameters);

        DataSource ds = getOrCreatePool(config);
        NamedParameterResult parsed = replaceNamedParameters(query, resolvedParams);

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(parsed.sql())) {

            for (int i = 0; i < parsed.values().size(); i++) {
                ps.setObject(i + 1, parsed.values().get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                return mapResultSet(rs);
            }
        } catch (SQLException e) {
            log.error("SQL query execution failed: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "SQL查询执行失败: " + e.getMessage(), HttpStatus.BAD_GATEWAY, e);
        }
    }

    /**
     * Test the database connection using the provided configuration.
     *
     * @param configJson the data source configJson (with encrypted password)
     * @return a result map with "success" (boolean) and "message" (String)
     */
    public Map<String, Object> testConnection(String configJson) {
        try {
            Map<String, Object> config = parseConfig(configJson);
            DataSource ds = getOrCreatePool(config);

            try (Connection conn = ds.getConnection()) {
                boolean valid = conn.isValid(5);
                if (valid) {
                    DatabaseMetaData meta = conn.getMetaData();
                    return Map.of(
                            "success", true,
                            "message", "连接成功: " + meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion()
                    );
                } else {
                    return Map.of("success", false, "message", "连接验证失败");
                }
            }
        } catch (BusinessException e) {
            return Map.of("success", false, "message", e.getMessage());
        } catch (SQLException e) {
            return Map.of("success", false, "message", "数据库连接失败: " + e.getMessage());
        } catch (Exception e) {
            return Map.of("success", false, "message", "连接测试失败: " + e.getMessage());
        }
    }

    // ── Internal helpers ──

    Map<String, Object> parseConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "数据源配置JSON解析失败: " + e.getMessage(), HttpStatus.BAD_REQUEST, e);
        }
    }

    /**
     * Build a JDBC URL based on the database type and connection parameters.
     */
    String buildJdbcUrl(Map<String, Object> config) {
        String dbType = getString(config, "dbType", "").toUpperCase();
        String host = getString(config, "host", "localhost");
        int port = getInt(config, "port", getDefaultPort(dbType));
        String database = getString(config, "database", "");

        return switch (dbType) {
            case "POSTGRESQL" -> "jdbc:postgresql://" + host + ":" + port + "/" + database;
            case "MYSQL" -> "jdbc:mysql://" + host + ":" + port + "/" + database;
            case "SQLSERVER" -> "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + database;
            case "ORACLE" -> "jdbc:oracle:thin:@" + host + ":" + port + ":" + database;
            default -> throw new BusinessException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "不支持的数据库类型: " + dbType + "，支持: POSTGRESQL, MYSQL, SQLSERVER, ORACLE",
                    HttpStatus.BAD_REQUEST);
        };
    }

    /**
     * Get or create a HikariCP connection pool for the given config.
     * Pools are cached by a fingerprint derived from dbType+host+port+database+username.
     */
    DataSource getOrCreatePool(Map<String, Object> config) {
        String fingerprint = buildFingerprint(config);
        return poolCache.computeIfAbsent(fingerprint, key -> createHikariDataSource(config));
    }

    private DataSource createHikariDataSource(Map<String, Object> config) {
        String jdbcUrl = buildJdbcUrl(config);
        String username = getString(config, "username", "");
        String password = decryptField(config, "password");
        int maxPoolSize = getInt(config, "maxPoolSize", DEFAULT_MAX_POOL_SIZE);
        int connectionTimeout = getInt(config, "connectionTimeout", DEFAULT_CONNECTION_TIMEOUT);

        var hikariConfig = new com.zaxxer.hikari.HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        if (password != null && !password.isEmpty()) {
            hikariConfig.setPassword(password);
        }
        hikariConfig.setMaximumPoolSize(maxPoolSize);
        hikariConfig.setConnectionTimeout(connectionTimeout);
        hikariConfig.setPoolName("docgen-ds-" + buildFingerprint(config).substring(0, 8));

        try {
            return new com.zaxxer.hikari.HikariDataSource(hikariConfig);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATASOURCE_CONNECTION_FAILED,
                    "创建数据库连接池失败: " + e.getMessage(), HttpStatus.BAD_GATEWAY, e);
        }
    }

    String buildFingerprint(Map<String, Object> config) {
        String dbType = getString(config, "dbType", "");
        String host = getString(config, "host", "");
        int port = getInt(config, "port", 0);
        String database = getString(config, "database", "");
        String username = getString(config, "username", "");
        return dbType + ":" + host + ":" + port + ":" + database + ":" + username;
    }

    /**
     * Replace named parameters (:paramName) in SQL with positional placeholders (?).
     * Returns the transformed SQL and the ordered list of parameter values.
     */
    public NamedParameterResult replaceNamedParameters(String sql, Map<String, Object> parameters) {
        StringBuilder result = new StringBuilder();
        List<Object> values = new ArrayList<>();
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == ':' && i + 1 < sql.length() && isParamStart(sql.charAt(i + 1))) {
                // Extract parameter name
                int start = i + 1;
                int end = start;
                while (end < sql.length() && isParamChar(sql.charAt(end))) {
                    end++;
                }
                String paramName = sql.substring(start, end);
                result.append('?');
                values.add(parameters.getOrDefault(paramName, null));
                i = end;
            } else if (c == '\'') {
                // Skip string literals
                result.append(c);
                i++;
                while (i < sql.length() && sql.charAt(i) != '\'') {
                    result.append(sql.charAt(i));
                    i++;
                }
                if (i < sql.length()) {
                    result.append(sql.charAt(i));
                    i++;
                }
            } else {
                result.append(c);
                i++;
            }
        }
        return new NamedParameterResult(result.toString(), values);
    }

    private static boolean isParamStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isParamChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Map a ResultSet to a list of row maps.
     */
    List<Map<String, Object>> mapResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = meta.getColumnLabel(i);
                row.put(columnName, rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    private String decryptField(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null) return null;
        String strValue = String.valueOf(value);
        if (strValue.isEmpty()) return "";
        try {
            return encryptionService.decrypt(strValue);
        } catch (Exception e) {
            log.debug("Could not decrypt field '{}', using raw value", key);
            return strValue;
        }
    }

    private static String getString(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private static int getInt(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { /* fall through */ }
        }
        return defaultValue;
    }

    private static int getDefaultPort(String dbType) {
        return switch (dbType) {
            case "POSTGRESQL" -> 5432;
            case "MYSQL" -> 3306;
            case "SQLSERVER" -> 1433;
            case "ORACLE" -> 1521;
            default -> 0;
        };
    }

    /**
     * Result of named parameter replacement.
     */
    public record NamedParameterResult(String sql, List<Object> values) {}
}
