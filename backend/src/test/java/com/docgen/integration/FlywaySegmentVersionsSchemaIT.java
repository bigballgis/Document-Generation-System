package com.docgen.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Greenfield Flyway verification for segment version schema evolution (V30 → V36 → V39).
 * <p>
 * V36 drops the segment-library stack (including the legacy {@code segment_versions} keyed by
 * {@code segment_id}). V39 recreates {@code segment_versions} for template-scoped inline versions.
 * </p>
 */
@Testcontainers
class FlywaySegmentVersionsSchemaIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16.5-alpine"))
            .withDatabaseName("docgen_flyway_it")
            .withUsername("test")
            .withPassword("test");

    @Test
    void migrate_emptyDatabase_segmentVersionsReflectsV39InlineModelAndSegmentsRemoved() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection c = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement st = c.createStatement()) {
            assertTableAbsent(st, "segments");
            assertTableAbsent(st, "segment_tag_mappings");
            assertV39SegmentVersionsShape(st);
        }
    }

    private static void assertTableAbsent(Statement st, String tableName) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = '" + tableName + "')")) {
            assertTrue(rs.next());
            assertFalse(rs.getBoolean(1), "Table " + tableName + " must not exist after V36");
        }
    }

    private static void assertV39SegmentVersionsShape(Statement st) throws Exception {
        Set<String> columns = new HashSet<>();
        try (ResultSet rs = st.executeQuery(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'segment_versions'")) {
            while (rs.next()) {
                columns.add(rs.getString(1));
            }
        }
        assertFalse(columns.isEmpty(), "segment_versions must exist after V39");
        assertTrue(columns.contains("template_id"), "V39 model requires template_id");
        assertTrue(columns.contains("segment_name"), "V39 model requires segment_name");
        assertTrue(columns.contains("tenant_id"), "V39 model requires tenant_id");
        assertTrue(columns.contains("config_snapshot"), "V39 model requires config_snapshot");
        assertFalse(columns.contains("segment_id"), "Legacy segment_id column must not exist (V30 table dropped in V36)");
    }
}
