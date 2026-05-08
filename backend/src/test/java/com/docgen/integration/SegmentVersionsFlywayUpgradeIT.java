package com.docgen.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upgrade-path Flyway check: stop at V35, seed segment-library era rows, then migrate through V36–V39.
 * <p>
 * Validates V36 assembly_config rewrite and segment-library drops without changing migration SQL.
 * </p>
 */
@Testcontainers(disabledWithoutDocker = true)
class SegmentVersionsFlywayUpgradeIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16.5-alpine"))
            .withDatabaseName("docgen_flyway_upgrade")
            .withUsername("test")
            .withPassword("test");

    @BeforeEach
    void resetPublicSchema() throws Exception {
        try (Connection c = connection();
             Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS public CASCADE");
            st.execute("CREATE SCHEMA public");
            st.execute("GRANT ALL ON SCHEMA public TO CURRENT_USER");
            st.execute("GRANT ALL ON SCHEMA public TO PUBLIC");
        }
    }

    @Test
    void v35WithValidSegment_migratesThroughV39_inlinesAssemblyAndDropsLibrary() throws Exception {
        migrateTo("35");
        try (Connection c = connection()) {
            seedTenantTeamUserSegmentAndCompositeTemplate(c, 1L, 1L, false);
        }
        assertSegmentLibraryPresent(connection());

        migrateToLatest();

        try (Connection c = connection()) {
            assertLibraryTablesAbsent(c);
            assertAssemblyInlinedForTemplate(c, 1L, "t1/seg-intro.docx", false);
            assertNewSegmentVersionsTableEmpty(c);
        }
    }

    @Test
    void v35WithMissingSegmentId_migratesThroughV39_marksInvalidInlinePlaceholder() throws Exception {
        migrateTo("35");
        try (Connection c = connection()) {
            seedTenantTeamUserSegmentAndCompositeTemplate(c, 1L, 99L, true);
        }
        migrateToLatest();

        try (Connection c = connection()) {
            assertLibraryTablesAbsent(c);
            assertAssemblyInlinedForTemplate(c, 1L, "", true);
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void migrateTo(String targetVersion) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target(targetVersion)
                .load()
                .migrate();
    }

    private static void migrateToLatest() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    /**
     * @param segmentPk        primary key for {@code segments} row
     * @param assemblySegmentId {@code segmentId} embedded in {@code assembly_config}
     * @param orphanAssembly    when true, {@code assemblySegmentId} must not exist in {@code segments}
     */
    private static void seedTenantTeamUserSegmentAndCompositeTemplate(
            Connection c, long segmentPk, long assemblySegmentId, boolean orphanAssembly) throws Exception {
        try (Statement st = c.createStatement()) {
            st.execute("""
                    INSERT INTO tenants (name, status, max_templates, max_api_calls_monthly, max_storage_bytes)
                    VALUES ('flyway-upgrade-it', 'ACTIVE', 100, 100000, 10737418240)
                    """);
            st.execute("INSERT INTO teams (tenant_id, name) VALUES (1, 'team-upg')");
            st.execute("""
                    INSERT INTO users (tenant_id, username, email, password_hash, role)
                    VALUES (1, 'upg-user', 'upg-user@test.local', '{noop}x', 'TENANT_ADMIN')
                    """);
            if (!orphanAssembly) {
                st.execute("""
                        INSERT INTO segments (id, tenant_id, name, file_path, is_component, segment_type, created_by)
                        VALUES (%d, 1, 'intro', 't1/seg-intro.docx', false, 'BODY', 1)
                        """.formatted(segmentPk));
                st.execute("""
                        INSERT INTO segment_versions (segment_id, version_number, file_path, created_by)
                        VALUES (%d, 1, 't1/seg-intro-v1.docx', 1)
                        """.formatted(segmentPk));
            } else {
                st.execute("""
                        INSERT INTO segments (id, tenant_id, name, file_path, is_component, segment_type, created_by)
                        VALUES (%d, 1, 'only-segment', 't1/only.docx', false, 'BODY', 1)
                        """.formatted(segmentPk));
            }
            String assembly = """
                    {"segments":[{"segmentId":%d,"position":0,"enabled":true,"pageBreakBefore":false}]}
                    """.formatted(assemblySegmentId);
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO templates (tenant_id, name, template_file_path, output_format, storage_strategy,
                        created_by, status, template_type, assembly_config, review_required, allow_history_versions,
                        created_at, updated_at)
                    VALUES (1, 'composite-upg', 't1/main.docx', 'WORD', 'TEMP', 1, 'DRAFT', 'COMPOSITE', ?::jsonb,
                        false, true, now(), now())
                    """)) {
                ps.setString(1, assembly.trim());
                ps.executeUpdate();
            }
            st.execute("SELECT setval(pg_get_serial_sequence('segments', 'id'), COALESCE((SELECT MAX(id) FROM segments), 1))");
            st.execute("SELECT setval(pg_get_serial_sequence('templates', 'id'), COALESCE((SELECT MAX(id) FROM templates), 1))");
        }
    }

    private static void assertSegmentLibraryPresent(Connection c) throws Exception {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'segments'")) {
            assertThat(rs.next() && rs.getInt(1) == 1).isTrue();
        }
    }

    private static void assertLibraryTablesAbsent(Connection c) throws Exception {
        for (String t : new String[] {"segments", "segment_tag_mappings", "segment_reviews", "segment_favorites",
                "segment_test_data"}) {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '"
                                 + t + "'")) {
                assertThat(rs.next() && rs.getInt(1) == 0)
                        .as("table %s should be absent after V36", t)
                        .isTrue();
            }
        }
    }

    private static void assertAssemblyInlinedForTemplate(
            Connection c, long templateId, String expectedFilePath, boolean expectInvalidName) throws Exception {
        String json;
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT assembly_config::text FROM templates WHERE id = " + templateId)) {
            assertThat(rs.next()).isTrue();
            json = rs.getString(1);
        }
        JsonNode root = JSON.readTree(json);
        JsonNode segments = root.get("segments");
        assertThat(segments).isNotNull();
        assertThat(segments.isArray()).isTrue();
        assertThat(segments).hasSize(1);
        JsonNode first = segments.get(0);
        assertThat(first.has("segmentId")).isFalse();
        assertThat(first.get("filePath").asText()).isEqualTo(expectedFilePath);
        if (expectInvalidName) {
            assertThat(first.get("name").asText()).isEqualTo("INVALID_SEGMENT_99");
            assertThat(first.get("enabled").asBoolean()).isFalse();
        } else {
            assertThat(first.get("name").asText()).isEqualTo("intro");
            assertThat(first.get("enabled").asBoolean()).isTrue();
        }
    }

    private static void assertNewSegmentVersionsTableEmpty(Connection c) throws Exception {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM segment_versions")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong(1)).isZero();
        }
    }
}
