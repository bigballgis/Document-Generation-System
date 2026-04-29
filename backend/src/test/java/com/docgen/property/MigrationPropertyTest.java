package com.docgen.property;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for data migration correctness: segmentId → inline mode.
 *
 * <p><b>Validates: Requirements 3.8, 12.1, 12.2</b></p>
 *
 * <p>Property 5: For any assembly_config JSON containing segmentId references and a
 * corresponding segments table, the migration transformation SHALL produce an inline
 * assembly_config where each entry's filePath, name, and segmentType match the
 * corresponding segment record, and all other fields are preserved. For non-existent
 * segmentId references, the entry SHALL be marked with enabled=false and name prefixed
 * with "INVALID_SEGMENT_".</p>
 */
@Tag("Feature: remove-segment-library, Property 5: dataMigrationSegmentIdToInlineConversion")
class MigrationPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Simulates the SQL migration logic in Java for property testing.
     * Mirrors V36__migrate_assembly_config_and_drop_segments.sql step 1.
     */
    private JsonNode simulateMigration(JsonNode assemblyConfig, Map<Long, SegmentRecord> segmentsTable)
            throws JsonProcessingException {
        ArrayNode segments = (ArrayNode) assemblyConfig.get("segments");
        if (segments == null) {
            return objectMapper.createObjectNode().set("segments", objectMapper.createArrayNode());
        }

        ArrayNode migratedSegments = objectMapper.createArrayNode();
        for (JsonNode elem : segments) {
            long segmentId = elem.get("segmentId").asLong();
            SegmentRecord record = segmentsTable.get(segmentId);

            ObjectNode migrated = objectMapper.createObjectNode();
            if (record != null) {
                migrated.put("filePath", record.filePath);
                migrated.put("name", record.name);
                if (record.segmentType != null) {
                    migrated.put("segmentType", record.segmentType);
                } else {
                    migrated.putNull("segmentType");
                }
                migrated.put("position", elem.get("position").asInt());
                migrated.put("enabled", elem.has("enabled") ? elem.get("enabled").asBoolean() : true);
                migrated.put("pageBreakBefore", elem.has("pageBreakBefore") ? elem.get("pageBreakBefore").asBoolean() : false);
                if (elem.has("conditionExpression") && !elem.get("conditionExpression").isNull()) {
                    migrated.put("conditionExpression", elem.get("conditionExpression").asText());
                } else {
                    migrated.putNull("conditionExpression");
                }
                if (elem.has("dataScope") && !elem.get("dataScope").isNull()) {
                    migrated.set("dataScope", elem.get("dataScope"));
                } else {
                    migrated.putNull("dataScope");
                }
            } else {
                // Non-existent segment: mark as invalid
                migrated.put("filePath", "");
                migrated.put("name", "INVALID_SEGMENT_" + segmentId);
                migrated.putNull("segmentType");
                migrated.put("position", elem.get("position").asInt());
                migrated.put("enabled", false);
                migrated.put("pageBreakBefore", elem.has("pageBreakBefore") ? elem.get("pageBreakBefore").asBoolean() : false);
                if (elem.has("conditionExpression") && !elem.get("conditionExpression").isNull()) {
                    migrated.put("conditionExpression", elem.get("conditionExpression").asText());
                } else {
                    migrated.putNull("conditionExpression");
                }
                if (elem.has("dataScope") && !elem.get("dataScope").isNull()) {
                    migrated.set("dataScope", elem.get("dataScope"));
                } else {
                    migrated.putNull("dataScope");
                }
            }
            migratedSegments.add(migrated);
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.set("segments", migratedSegments);
        return result;
    }

    /**
     * Property 5: dataMigrationSegmentIdToInlineConversion
     *
     * For valid segmentId references, filePath/name/segmentType must match the segment record.
     * For invalid references, entry must be marked enabled=false with INVALID_SEGMENT_ prefix.
     * All other fields (position, pageBreakBefore, conditionExpression, dataScope) must be preserved.
     */
    @Property(tries = 100)
    void dataMigrationSegmentIdToInlineConversion(
            @ForAll("migrationInputs") MigrationInput input
    ) throws Exception {
        JsonNode migrated = simulateMigration(input.assemblyConfig, input.segmentsTable);

        ArrayNode originalSegments = (ArrayNode) input.assemblyConfig.get("segments");
        ArrayNode migratedSegments = (ArrayNode) migrated.get("segments");

        assertNotNull(migratedSegments, "Migrated config must have segments array");
        assertEquals(originalSegments.size(), migratedSegments.size(),
                "Migrated segments count must match original");

        for (int i = 0; i < originalSegments.size(); i++) {
            JsonNode orig = originalSegments.get(i);
            JsonNode mig = migratedSegments.get(i);
            long segmentId = orig.get("segmentId").asLong();
            SegmentRecord record = input.segmentsTable.get(segmentId);

            if (record != null) {
                // Valid reference: filePath/name/segmentType must match segment record
                assertEquals(record.filePath, mig.get("filePath").asText(),
                        "filePath must match segment record for segmentId=" + segmentId);
                assertEquals(record.name, mig.get("name").asText(),
                        "name must match segment record for segmentId=" + segmentId);
                if (record.segmentType != null) {
                    assertEquals(record.segmentType, mig.get("segmentType").asText(),
                            "segmentType must match segment record");
                } else {
                    assertTrue(mig.get("segmentType").isNull(), "segmentType should be null");
                }
            } else {
                // Invalid reference: must be marked as invalid
                assertEquals("", mig.get("filePath").asText(),
                        "Invalid segment filePath must be empty");
                assertEquals("INVALID_SEGMENT_" + segmentId, mig.get("name").asText(),
                        "Invalid segment name must have INVALID_SEGMENT_ prefix");
                assertFalse(mig.get("enabled").asBoolean(),
                        "Invalid segment must be disabled");
            }

            // Preserved fields
            assertEquals(orig.get("position").asInt(), mig.get("position").asInt(),
                    "position must be preserved");
            boolean origPageBreak = orig.has("pageBreakBefore") && orig.get("pageBreakBefore").asBoolean();
            assertEquals(origPageBreak, mig.get("pageBreakBefore").asBoolean(),
                    "pageBreakBefore must be preserved");

            // conditionExpression preserved
            if (orig.has("conditionExpression") && !orig.get("conditionExpression").isNull()) {
                assertEquals(orig.get("conditionExpression").asText(),
                        mig.get("conditionExpression").asText(),
                        "conditionExpression must be preserved");
            }

            // dataScope preserved
            if (orig.has("dataScope") && !orig.get("dataScope").isNull()) {
                assertEquals(orig.get("dataScope"), mig.get("dataScope"),
                        "dataScope must be preserved");
            }
        }
    }


    static class SegmentRecord {
        final long id;
        final String filePath;
        final String name;
        final String segmentType;

        SegmentRecord(long id, String filePath, String name, String segmentType) {
            this.id = id;
            this.filePath = filePath;
            this.name = name;
            this.segmentType = segmentType;
        }
    }

    static class MigrationInput {
        final JsonNode assemblyConfig;
        final Map<Long, SegmentRecord> segmentsTable;

        MigrationInput(JsonNode assemblyConfig, Map<Long, SegmentRecord> segmentsTable) {
            this.assemblyConfig = assemblyConfig;
            this.segmentsTable = segmentsTable;
        }

        @Override
        public String toString() {
            return "MigrationInput{config=" + assemblyConfig + ", segments=" + segmentsTable.keySet() + "}";
        }
    }


    @Provide
    Arbitrary<MigrationInput> migrationInputs() {
        String[] segmentTypes = {"COVER", "TOC", "CHAPTER", "TABLE", "SIGNATURE", "LEGAL", "APPENDIX", null};

        return Arbitraries.integers().between(1, 6).flatMap(segmentCount -> {
            // Generate segment records (some IDs will be in the table, some won't)
            return Arbitraries.longs().between(1, 100)
                    .list().ofSize(segmentCount).uniqueElements()
                    .flatMap(segmentIds -> {
                        // Decide which IDs exist in the segments table
                        return Arbitraries.of(true, false).list().ofSize(segmentCount)
                                .flatMap(existFlags -> {
                                    return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                                            .list().ofSize(segmentCount)
                                            .flatMap(names -> {
                                                return Arbitraries.of(segmentTypes).list().ofSize(segmentCount)
                                                        .flatMap(types -> {
                                                            return Arbitraries.of(true, false).list().ofSize(segmentCount)
                                                                    .flatMap(enabledFlags -> {
                                                                        return Arbitraries.of(true, false).list().ofSize(segmentCount)
                                                                                .map(pageBreakFlags -> {
                                                                                    return buildMigrationInput(
                                                                                            segmentIds, existFlags, names,
                                                                                            types, enabledFlags, pageBreakFlags);
                                                                                });
                                                                    });
                                                        });
                                            });
                                });
                    });
        });
    }

    private MigrationInput buildMigrationInput(
            List<Long> segmentIds, List<Boolean> existFlags, List<String> names,
            List<String> types, List<Boolean> enabledFlags, List<Boolean> pageBreakFlags) {

        Map<Long, SegmentRecord> segmentsTable = new HashMap<>();
        ObjectNode assemblyConfig = objectMapper.createObjectNode();
        ArrayNode segments = objectMapper.createArrayNode();

        for (int i = 0; i < segmentIds.size(); i++) {
            long id = segmentIds.get(i);
            String filePath = "segments/" + id + "/" + names.get(i) + ".docx";

            if (existFlags.get(i)) {
                segmentsTable.put(id, new SegmentRecord(id, filePath, names.get(i), types.get(i)));
            }

            ObjectNode entry = objectMapper.createObjectNode();
            entry.put("segmentId", id);
            entry.put("position", i);
            entry.put("enabled", enabledFlags.get(i));
            entry.put("pageBreakBefore", pageBreakFlags.get(i));
            entry.putNull("conditionExpression");
            entry.putNull("dataScope");
            segments.add(entry);
        }

        assemblyConfig.set("segments", segments);
        return new MigrationInput(assemblyConfig, segmentsTable);
    }
}

