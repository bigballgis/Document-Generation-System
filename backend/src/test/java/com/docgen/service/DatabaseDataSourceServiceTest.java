package com.docgen.service;

import com.docgen.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseDataSourceServiceTest {

    @Mock
    private EncryptionService encryptionService;

    private DatabaseDataSourceService service;

    @BeforeEach
    void setUp() {
        service = new DatabaseDataSourceService(encryptionService);
    }

    // ── parseConfig ──

    @Test
    void parseConfig_validJson_returnMap() {
        Map<String, Object> result = service.parseConfig(
                "{\"dbType\":\"POSTGRESQL\",\"host\":\"localhost\",\"port\":5432}");
        assertEquals("POSTGRESQL", result.get("dbType"));
        assertEquals("localhost", result.get("host"));
    }

    @Test
    void parseConfig_invalidJson_throws() {
        assertThrows(BusinessException.class, () -> service.parseConfig("not-json"));
    }

    // ── buildJdbcUrl ──

    @Test
    void buildJdbcUrl_postgresql() {
        String url = service.buildJdbcUrl(Map.of(
                "dbType", "POSTGRESQL", "host", "db.example.com", "port", 5432, "database", "mydb"));
        assertEquals("jdbc:postgresql://db.example.com:5432/mydb", url);
    }

    @Test
    void buildJdbcUrl_mysql() {
        String url = service.buildJdbcUrl(Map.of(
                "dbType", "MYSQL", "host", "db.example.com", "port", 3306, "database", "mydb"));
        assertEquals("jdbc:mysql://db.example.com:3306/mydb", url);
    }

    @Test
    void buildJdbcUrl_sqlserver() {
        String url = service.buildJdbcUrl(Map.of(
                "dbType", "SQLSERVER", "host", "db.example.com", "port", 1433, "database", "mydb"));
        assertEquals("jdbc:sqlserver://db.example.com:1433;databaseName=mydb", url);
    }

    @Test
    void buildJdbcUrl_oracle() {
        String url = service.buildJdbcUrl(Map.of(
                "dbType", "ORACLE", "host", "db.example.com", "port", 1521, "database", "mydb"));
        assertEquals("jdbc:oracle:thin:@db.example.com:1521:mydb", url);
    }

    @Test
    void buildJdbcUrl_defaultPorts() {
        assertEquals("jdbc:postgresql://localhost:5432/test",
                service.buildJdbcUrl(Map.of("dbType", "POSTGRESQL", "database", "test")));
        assertEquals("jdbc:mysql://localhost:3306/test",
                service.buildJdbcUrl(Map.of("dbType", "MYSQL", "database", "test")));
        assertEquals("jdbc:sqlserver://localhost:1433;databaseName=test",
                service.buildJdbcUrl(Map.of("dbType", "SQLSERVER", "database", "test")));
        assertEquals("jdbc:oracle:thin:@localhost:1521:test",
                service.buildJdbcUrl(Map.of("dbType", "ORACLE", "database", "test")));
    }

    @Test
    void buildJdbcUrl_unsupportedType_throws() {
        assertThrows(BusinessException.class, () ->
                service.buildJdbcUrl(Map.of("dbType", "SQLITE", "database", "test")));
    }

    @Test
    void buildJdbcUrl_emptyType_throws() {
        assertThrows(BusinessException.class, () ->
                service.buildJdbcUrl(Map.of("database", "test")));
    }

    // ── buildFingerprint ──

    @Test
    void buildFingerprint_includesAllFields() {
        String fp = service.buildFingerprint(Map.of(
                "dbType", "POSTGRESQL", "host", "localhost", "port", 5432,
                "database", "mydb", "username", "user"));
        assertEquals("POSTGRESQL:localhost:5432:mydb:user", fp);
    }

    @Test
    void buildFingerprint_differentConfigs_differentFingerprints() {
        String fp1 = service.buildFingerprint(Map.of("dbType", "POSTGRESQL", "host", "host1", "port", 5432, "database", "db1", "username", "u1"));
        String fp2 = service.buildFingerprint(Map.of("dbType", "MYSQL", "host", "host1", "port", 3306, "database", "db1", "username", "u1"));
        assertNotEquals(fp1, fp2);
    }

    // ── replaceNamedParameters ──

    @Test
    void replaceNamedParameters_noParams() {
        var result = service.replaceNamedParameters("SELECT * FROM users", Collections.emptyMap());
        assertEquals("SELECT * FROM users", result.sql());
        assertTrue(result.values().isEmpty());
    }

    @Test
    void replaceNamedParameters_singleParam() {
        var result = service.replaceNamedParameters(
                "SELECT * FROM users WHERE id = :userId",
                Map.of("userId", 42));
        assertEquals("SELECT * FROM users WHERE id = ?", result.sql());
        assertEquals(List.of(42), result.values());
    }

    @Test
    void replaceNamedParameters_multipleParams() {
        var result = service.replaceNamedParameters(
                "SELECT * FROM users WHERE name = :name AND age > :minAge",
                Map.of("name", "Alice", "minAge", 18));
        assertEquals("SELECT * FROM users WHERE name = ? AND age > ?", result.sql());
        assertEquals(2, result.values().size());
        assertEquals("Alice", result.values().get(0));
        assertEquals(18, result.values().get(1));
    }

    @Test
    void replaceNamedParameters_missingParam_setsNull() {
        var result = service.replaceNamedParameters(
                "SELECT * FROM users WHERE id = :userId",
                Collections.emptyMap());
        assertEquals("SELECT * FROM users WHERE id = ?", result.sql());
        assertEquals(1, result.values().size());
        assertNull(result.values().get(0));
    }

    @Test
    void replaceNamedParameters_skipsStringLiterals() {
        var result = service.replaceNamedParameters(
                "SELECT * FROM users WHERE name = ':notAParam' AND id = :userId",
                Map.of("userId", 1));
        assertEquals("SELECT * FROM users WHERE name = ':notAParam' AND id = ?", result.sql());
        assertEquals(List.of(1), result.values());
    }

    @Test
    void replaceNamedParameters_underscoreInParamName() {
        var result = service.replaceNamedParameters(
                "SELECT * FROM users WHERE user_id = :user_id",
                Map.of("user_id", 99));
        assertEquals("SELECT * FROM users WHERE user_id = ?", result.sql());
        assertEquals(List.of(99), result.values());
    }

    // ── mapResultSet ──

    @Test
    void mapResultSet_emptyResultSet() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        when(rs.getMetaData()).thenReturn(meta);
        when(meta.getColumnCount()).thenReturn(2);
        when(rs.next()).thenReturn(false);

        List<Map<String, Object>> rows = service.mapResultSet(rs);
        assertTrue(rows.isEmpty());
    }

    @Test
    void mapResultSet_multipleRows() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        when(rs.getMetaData()).thenReturn(meta);
        when(meta.getColumnCount()).thenReturn(2);
        when(meta.getColumnLabel(1)).thenReturn("id");
        when(meta.getColumnLabel(2)).thenReturn("name");
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getObject(1)).thenReturn(1, 2);
        when(rs.getObject(2)).thenReturn("Alice", "Bob");

        List<Map<String, Object>> rows = service.mapResultSet(rs);
        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).get("id"));
        assertEquals("Alice", rows.get(0).get("name"));
        assertEquals(2, rows.get(1).get("id"));
        assertEquals("Bob", rows.get(1).get("name"));
    }

    // ── fetchData ──

    @Test
    void fetchData_emptyQuery_throws() {
        String configJson = "{\"dbType\":\"POSTGRESQL\",\"host\":\"localhost\",\"database\":\"test\",\"query\":\"\"}";
        assertThrows(BusinessException.class, () ->
                service.fetchData(configJson, Collections.emptyMap()));
    }

    @Test
    void fetchData_noQuery_throws() {
        String configJson = "{\"dbType\":\"POSTGRESQL\",\"host\":\"localhost\",\"database\":\"test\"}";
        assertThrows(BusinessException.class, () ->
                service.fetchData(configJson, Collections.emptyMap()));
    }

    @Test
    void fetchData_withMockedPool_returnsResults() throws SQLException {
        // Set up a mock DataSource in the pool cache
        javax.sql.DataSource mockDs = mock(javax.sql.DataSource.class);
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockPs = mock(PreparedStatement.class);
        ResultSet mockRs = mock(ResultSet.class);
        ResultSetMetaData mockMeta = mock(ResultSetMetaData.class);

        when(mockDs.getConnection()).thenReturn(mockConn);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockPs);
        when(mockPs.executeQuery()).thenReturn(mockRs);
        when(mockRs.getMetaData()).thenReturn(mockMeta);
        when(mockMeta.getColumnCount()).thenReturn(1);
        when(mockMeta.getColumnLabel(1)).thenReturn("count");
        when(mockRs.next()).thenReturn(true, false);
        when(mockRs.getObject(1)).thenReturn(42);

        // Pre-populate pool cache
        String configJson = "{\"dbType\":\"POSTGRESQL\",\"host\":\"mockhost\",\"port\":5432,\"database\":\"testdb\",\"username\":\"user\",\"query\":\"SELECT count(*) as count FROM users\"}";
        Map<String, Object> config = service.parseConfig(configJson);
        String fingerprint = service.buildFingerprint(config);
        service.poolCache.put(fingerprint, mockDs);

        List<Map<String, Object>> results = service.fetchData(configJson, Collections.emptyMap());
        assertEquals(1, results.size());
        assertEquals(42, results.get(0).get("count"));
    }

    @Test
    void fetchData_withParameters_bindsCorrectly() throws SQLException {
        javax.sql.DataSource mockDs = mock(javax.sql.DataSource.class);
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockPs = mock(PreparedStatement.class);
        ResultSet mockRs = mock(ResultSet.class);
        ResultSetMetaData mockMeta = mock(ResultSetMetaData.class);

        when(mockDs.getConnection()).thenReturn(mockConn);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockPs);
        when(mockPs.executeQuery()).thenReturn(mockRs);
        when(mockRs.getMetaData()).thenReturn(mockMeta);
        when(mockMeta.getColumnCount()).thenReturn(1);
        when(mockMeta.getColumnLabel(1)).thenReturn("name");
        when(mockRs.next()).thenReturn(true, false);
        when(mockRs.getObject(1)).thenReturn("Alice");

        String configJson = "{\"dbType\":\"POSTGRESQL\",\"host\":\"mockhost2\",\"port\":5432,\"database\":\"testdb\",\"username\":\"user\",\"query\":\"SELECT name FROM users WHERE id = :userId\"}";
        Map<String, Object> config = service.parseConfig(configJson);
        service.poolCache.put(service.buildFingerprint(config), mockDs);

        List<Map<String, Object>> results = service.fetchData(configJson, Map.of("userId", 1));
        assertEquals(1, results.size());
        assertEquals("Alice", results.get(0).get("name"));

        // Verify PreparedStatement was called with the parameter
        verify(mockPs).setObject(1, 1);
    }

    @Test
    void fetchData_sqlException_throwsBusinessException() throws SQLException {
        javax.sql.DataSource mockDs = mock(javax.sql.DataSource.class);
        when(mockDs.getConnection()).thenThrow(new SQLException("Connection refused"));

        String configJson = "{\"dbType\":\"POSTGRESQL\",\"host\":\"failhost\",\"port\":5432,\"database\":\"testdb\",\"username\":\"user\",\"query\":\"SELECT 1\"}";
        Map<String, Object> config = service.parseConfig(configJson);
        service.poolCache.put(service.buildFingerprint(config), mockDs);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.fetchData(configJson, Collections.emptyMap()));
        assertTrue(ex.getMessage().contains("SQL查询执行失败"));
    }

    // ── testConnection ──

    @Test
    void testConnection_success() throws SQLException {
        javax.sql.DataSource mockDs = mock(javax.sql.DataSource.class);
        Connection mockConn = mock(Connection.class);
        DatabaseMetaData mockDbMeta = mock(DatabaseMetaData.class);

        when(mockDs.getConnection()).thenReturn(mockConn);
        when(mockConn.isValid(5)).thenReturn(true);
        when(mockConn.getMetaData()).thenReturn(mockDbMeta);
        when(mockDbMeta.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(mockDbMeta.getDatabaseProductVersion()).thenReturn("16.5");

        String configJson = "{\"dbType\":\"POSTGRESQL\",\"host\":\"testhost\",\"port\":5432,\"database\":\"testdb\",\"username\":\"user\"}";
        Map<String, Object> config = service.parseConfig(configJson);
        service.poolCache.put(service.buildFingerprint(config), mockDs);

        Map<String, Object> result = service.testConnection(configJson);
        assertTrue((Boolean) result.get("success"));
        assertTrue(((String) result.get("message")).contains("PostgreSQL"));
    }

    @Test
    void testConnection_invalidConnection() throws SQLException {
        javax.sql.DataSource mockDs = mock(javax.sql.DataSource.class);
        Connection mockConn = mock(Connection.class);

        when(mockDs.getConnection()).thenReturn(mockConn);
        when(mockConn.isValid(5)).thenReturn(false);

        String configJson = "{\"dbType\":\"POSTGRESQL\",\"host\":\"testhost2\",\"port\":5432,\"database\":\"testdb\",\"username\":\"user\"}";
        Map<String, Object> config = service.parseConfig(configJson);
        service.poolCache.put(service.buildFingerprint(config), mockDs);

        Map<String, Object> result = service.testConnection(configJson);
        assertFalse((Boolean) result.get("success"));
    }

    @Test
    void testConnection_sqlException_returnsFalse() throws SQLException {
        javax.sql.DataSource mockDs = mock(javax.sql.DataSource.class);
        when(mockDs.getConnection()).thenThrow(new SQLException("Access denied"));

        String configJson = "{\"dbType\":\"POSTGRESQL\",\"host\":\"testhost3\",\"port\":5432,\"database\":\"testdb\",\"username\":\"user\"}";
        Map<String, Object> config = service.parseConfig(configJson);
        service.poolCache.put(service.buildFingerprint(config), mockDs);

        Map<String, Object> result = service.testConnection(configJson);
        assertFalse((Boolean) result.get("success"));
        assertTrue(((String) result.get("message")).contains("数据库连接失败"));
    }

    @Test
    void testConnection_invalidConfig_returnsFalse() {
        Map<String, Object> result = service.testConnection("not-json");
        assertFalse((Boolean) result.get("success"));
    }
}
