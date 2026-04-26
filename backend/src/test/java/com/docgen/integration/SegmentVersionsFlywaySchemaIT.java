package com.docgen.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Flyway migrations produce a {@code segment_versions} schema compatible with
 * {@link com.docgen.entity.SegmentVersion} (V39 and prior ordering on a fresh database).
 * <p>
 * Requires Docker (same as {@link BaseIntegrationTest}).
 */
class SegmentVersionsFlywaySchemaIT extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void segmentVersionsTableHasExpectedColumnsUniqueConstraintAndIndexes() {
        Set<String> columns = jdbcTemplate.query(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'segment_versions'",
                (rs, rowNum) -> rs.getString("column_name")
        ).stream().collect(Collectors.toSet());

        assertThat(columns).containsExactlyInAnyOrder(
                "id",
                "tenant_id",
                "template_id",
                "segment_name",
                "version_number",
                "file_path",
                "segment_type",
                "config_snapshot",
                "comment",
                "created_by",
                "created_at"
        );

        Integer uniqueCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint c "
                        + "JOIN pg_class t ON c.conrelid = t.oid "
                        + "WHERE t.relname = 'segment_versions' AND c.contype = 'u'",
                Integer.class);
        assertThat(uniqueCount).isEqualTo(1);

        String uniqueName = jdbcTemplate.queryForObject(
                "SELECT c.conname FROM pg_constraint c "
                        + "JOIN pg_class t ON c.conrelid = t.oid "
                        + "WHERE t.relname = 'segment_versions' AND c.contype = 'u' LIMIT 1",
                String.class);
        assertThat(uniqueName).isEqualTo("uq_segment_versions_template_name_version");

        List<String> indexNames = jdbcTemplate.query(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'segment_versions'",
                (rs, rowNum) -> rs.getString("indexname"));

        assertThat(indexNames)
                .contains(
                        "idx_segment_versions_template_id",
                        "idx_segment_versions_tenant_id",
                        "idx_segment_versions_template_name",
                        "segment_versions_pkey");
    }
}
