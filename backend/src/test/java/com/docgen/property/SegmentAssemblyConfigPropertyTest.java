package com.docgen.property;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.service.AssemblyConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for AssemblyConfig serialization round-trip consistency.
 *
 * <p><b>Validates: Requirements 1.7, 3.1, 8.2</b></p>
 *
 * <p>Property 1: For any valid AssemblyConfigDTO containing inline segment entries
 * (with arbitrary filePath, name, segmentType, position, enabled, pageBreakBefore,
 * conditionExpression, dataScope), serializing to JSON then deserializing back
 * SHALL produce an equivalent object with all fields preserved.</p>
 */
@Tag("Feature: remove-segment-library, Property 1: AssemblyConfig serialization round-trip consistency")
class SegmentAssemblyConfigPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Property 1: AssemblyConfig serialization round-trip consistency.
     *
     * For any valid AssemblyConfigDTO with inline segment entries,
     * serialize → deserialize must produce an equivalent object.
     */
    @Property(tries = 100)
    void serializationRoundTripPreservesAllFields(
            @ForAll("validAssemblyConfigs") AssemblyConfigDTO config
    ) {
        AssemblyConfigService service = new AssemblyConfigService(objectMapper);

        String json = service.serialize(config);
        assertNotNull(json, "Serialized JSON must not be null");

        AssemblyConfigDTO restored = service.deserialize(json);
        assertNotNull(restored, "Deserialized config must not be null");
        assertNotNull(restored.getSegments(), "Deserialized segments must not be null");
        assertEquals(config.getSegments().size(), restored.getSegments().size(),
                "Segment count must be preserved");

        for (int i = 0; i < config.getSegments().size(); i++) {
            AssemblySegmentEntry original = config.getSegments().get(i);
            AssemblySegmentEntry round = restored.getSegments().get(i);

            assertEquals(original.getFilePath(), round.getFilePath(),
                    "filePath must be preserved at index " + i);
            assertEquals(original.getName(), round.getName(),
                    "name must be preserved at index " + i);
            assertEquals(original.getSegmentType(), round.getSegmentType(),
                    "segmentType must be preserved at index " + i);
            assertEquals(original.getPosition(), round.getPosition(),
                    "position must be preserved at index " + i);
            assertEquals(original.isEnabled(), round.isEnabled(),
                    "enabled must be preserved at index " + i);
            assertEquals(original.isPageBreakBefore(), round.isPageBreakBefore(),
                    "pageBreakBefore must be preserved at index " + i);
            assertEquals(original.getConditionExpression(), round.getConditionExpression(),
                    "conditionExpression must be preserved at index " + i);
            assertEquals(original.getDataScope(), round.getDataScope(),
                    "dataScope must be preserved at index " + i);
        }
    }

    // ── Generators ──

    private static final String[] SEGMENT_TYPES = {
            "COVER", "TOC", "CHAPTER", "TABLE", "SIGNATURE", "LEGAL", "APPENDIX"
    };

    @Provide
    Arbitrary<AssemblyConfigDTO> validAssemblyConfigs() {
        return Arbitraries.integers().between(1, 10).flatMap(size ->
                segmentEntryArbitrary().list().ofSize(size).map(entries -> {
                    // Assign consecutive positions
                    for (int i = 0; i < entries.size(); i++) {
                        entries.get(i).setPosition(i);
                    }
                    AssemblyConfigDTO dto = new AssemblyConfigDTO();
                    dto.setSegments(entries);
                    return dto;
                })
        );
    }

    private Arbitrary<AssemblySegmentEntry> segmentEntryArbitrary() {
        Arbitrary<String> filePathArb = Arbitraries.strings()
                .alpha().ofMinLength(3).ofMaxLength(20)
                .map(s -> "segments/1/" + s + ".docx");

        Arbitrary<String> nameArb = Arbitraries.strings()
                .alpha().ofMinLength(1).ofMaxLength(30);

        Arbitrary<String> segmentTypeArb = Arbitraries.of(SEGMENT_TYPES)
                .injectNull(0.1);

        Arbitrary<Boolean> enabledArb = Arbitraries.of(true, false);
        Arbitrary<Boolean> pageBreakArb = Arbitraries.of(true, false);

        Arbitrary<String> conditionArb = Arbitraries.of(
                        "data.show === true",
                        "data.count > 0",
                        "data.includeAppendix === true"
                )
                .injectNull(0.5);

        Arbitrary<Map<String, String>> dataScopeArb = dataScopeArbitrary()
                .injectNull(0.5);

        return Combinators.combine(filePathArb, nameArb, segmentTypeArb,
                        enabledArb, pageBreakArb, conditionArb, dataScopeArb)
                .as((filePath, name, segmentType, enabled, pageBreak, condition, dataScope) -> {
                    AssemblySegmentEntry entry = new AssemblySegmentEntry();
                    entry.setFilePath(filePath);
                    entry.setName(name);
                    entry.setSegmentType(segmentType);
                    entry.setEnabled(enabled);
                    entry.setPageBreakBefore(pageBreak);
                    entry.setConditionExpression(condition);
                    entry.setDataScope(dataScope);
                    return entry;
                });
    }

    private Arbitrary<Map<String, String>> dataScopeArbitrary() {
        Arbitrary<String> keyArb = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(15);
        Arbitrary<String> valueArb = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                .map(s -> "global." + s);

        return Arbitraries.maps(keyArb, valueArb).ofMinSize(1).ofMaxSize(5);
    }
}
