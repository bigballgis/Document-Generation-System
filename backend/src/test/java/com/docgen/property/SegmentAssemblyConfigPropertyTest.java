package com.docgen.property;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.entity.Segment;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.SegmentRepository;
import com.docgen.service.AssemblyConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for AssemblyConfigService — Property 9: Assembly_Config Validation Completeness.
 *
 * <p><b>Validates: Requirements 2.8, 2.9</b></p>
 *
 * <p>Verifies that saving an Assembly_Config is rejected when all segments are disabled
 * (COMPOSITE_TEMPLATE_EMPTY) or when non-existent segment IDs are referenced
 * (SEGMENT_NOT_FOUND), and that valid configs pass validation.</p>
 */
@Tag("Feature: template-segmentation, Property 9: assemblyConfigValidation")
class SegmentAssemblyConfigPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Property Tests ──

    /**
     * Property 9: assemblyConfigValidation — all-disabled configs rejected.
     *
     * For any Assembly_Config where all segments have enabled = false,
     * validation must throw BusinessException with COMPOSITE_TEMPLATE_EMPTY.
     */
    @Property(tries = 100)
    void allDisabledConfigsShouldBeRejectedWithCompositeTemplateEmpty(
            @ForAll("allDisabledConfigs") AssemblyConfigDTO config
    ) {
        SegmentRepository segmentRepository = mock(SegmentRepository.class);
        AssemblyConfigService service = new AssemblyConfigService(objectMapper, segmentRepository);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validate(config),
                "All-disabled config must be rejected");

        assertEquals(ErrorCode.COMPOSITE_TEMPLATE_EMPTY, ex.getErrorCode(),
                "Error code should be COMPOSITE_TEMPLATE_EMPTY");

        // Repository should never be called since the enabled check fails first
        verify(segmentRepository, never()).findAllById(anyCollection());
    }

    /**
     * Property 9: assemblyConfigValidation — non-existent segment IDs rejected.
     *
     * For any Assembly_Config where at least one segment ID does not exist in the database,
     * validation must throw BusinessException with SEGMENT_NOT_FOUND.
     */
    @Property(tries = 100)
    void configsWithNonExistentSegmentIdsShouldBeRejectedWithSegmentNotFound(
            @ForAll("configsWithInvalidIds") ConfigWithExistingIds testData
    ) {
        SegmentRepository segmentRepository = mock(SegmentRepository.class);
        AssemblyConfigService service = new AssemblyConfigService(objectMapper, segmentRepository);

        // Mock: only return segments for the existing IDs
        List<Segment> existingSegments = testData.existingIds.stream()
                .map(id -> {
                    Segment s = new Segment();
                    s.setId(id);
                    s.setTenantId(1L);
                    s.setName("Segment-" + id);
                    s.setFilePath("segments/1/uuid_seg" + id + ".docx");
                    s.setCreatedBy(1L);
                    s.setCreatedAt(Instant.now());
                    s.setUpdatedAt(Instant.now());
                    return s;
                })
                .collect(Collectors.toList());

        when(segmentRepository.findAllById(anyCollection())).thenReturn(existingSegments);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validate(testData.config),
                "Config with non-existent segment IDs must be rejected");

        assertEquals(ErrorCode.SEGMENT_NOT_FOUND, ex.getErrorCode(),
                "Error code should be SEGMENT_NOT_FOUND");
    }

    /**
     * Property 9: assemblyConfigValidation — valid configs pass validation.
     *
     * For any Assembly_Config where at least one segment is enabled and all segment IDs
     * exist in the database, validation must succeed without throwing.
     */
    @Property(tries = 100)
    void validConfigsShouldPassValidation(
            @ForAll("validConfigs") AssemblyConfigDTO config
    ) {
        SegmentRepository segmentRepository = mock(SegmentRepository.class);
        AssemblyConfigService service = new AssemblyConfigService(objectMapper, segmentRepository);

        // All segment IDs in the config exist
        Set<Long> allIds = config.getSegments().stream()
                .map(AssemblySegmentEntry::getSegmentId)
                .collect(Collectors.toSet());

        List<Segment> existingSegments = allIds.stream()
                .map(id -> {
                    Segment s = new Segment();
                    s.setId(id);
                    s.setTenantId(1L);
                    s.setName("Segment-" + id);
                    s.setFilePath("segments/1/uuid_seg" + id + ".docx");
                    s.setCreatedBy(1L);
                    s.setCreatedAt(Instant.now());
                    s.setUpdatedAt(Instant.now());
                    return s;
                })
                .collect(Collectors.toList());

        when(segmentRepository.findAllById(anyCollection())).thenReturn(existingSegments);

        assertDoesNotThrow(() -> service.validate(config),
                "Valid config (at least one enabled, all IDs exist) should pass validation");
    }

    // ── Helper record for test data ──

    static class ConfigWithExistingIds {
        final AssemblyConfigDTO config;
        final Set<Long> existingIds;

        ConfigWithExistingIds(AssemblyConfigDTO config, Set<Long> existingIds) {
            this.config = config;
            this.existingIds = existingIds;
        }
    }

    // ── Generators ──

    /**
     * Generates AssemblyConfigDTO where ALL segments have enabled = false.
     */
    @Provide
    Arbitrary<AssemblyConfigDTO> allDisabledConfigs() {
        return Arbitraries.integers().between(1, 10).flatMap(size -> {
            Arbitrary<List<AssemblySegmentEntry>> entriesArb = Arbitraries.longs()
                    .between(1L, 1000L)
                    .list().ofSize(size)
                    .map(ids -> {
                        List<AssemblySegmentEntry> entries = new ArrayList<>();
                        for (int i = 0; i < ids.size(); i++) {
                            AssemblySegmentEntry entry = new AssemblySegmentEntry();
                            entry.setSegmentId(ids.get(i));
                            entry.setPosition(i);
                            entry.setEnabled(false); // all disabled
                            entry.setPageBreakBefore(i > 0);
                            entries.add(entry);
                        }
                        return entries;
                    });

            return entriesArb.map(entries -> {
                AssemblyConfigDTO dto = new AssemblyConfigDTO();
                dto.setSegments(entries);
                return dto;
            });
        });
    }

    /**
     * Generates configs with at least one enabled segment but containing non-existent IDs.
     * Returns a ConfigWithExistingIds containing the config and the set of IDs that "exist".
     */
    @Provide
    Arbitrary<ConfigWithExistingIds> configsWithInvalidIds() {
        return Arbitraries.integers().between(2, 8).flatMap(size ->
            Arbitraries.longs().between(1L, 500L).set().ofMinSize(size).ofMaxSize(size + 2)
                .flatMap(allIds -> {
                    List<Long> idList = new ArrayList<>(allIds);
                    // Pick a subset as "existing" — at least 1 missing
                    int existCount = Math.max(1, idList.size() - 1);
                    return Arbitraries.integers().between(1, existCount).map(ec -> {
                        int actualExistCount = Math.min(ec, idList.size() - 1);
                        Set<Long> existingIds = new HashSet<>(idList.subList(0, actualExistCount));

                        List<AssemblySegmentEntry> entries = new ArrayList<>();
                        boolean hasEnabled = false;
                        for (int i = 0; i < idList.size(); i++) {
                            AssemblySegmentEntry entry = new AssemblySegmentEntry();
                            entry.setSegmentId(idList.get(i));
                            entry.setPosition(i);
                            // Ensure at least one is enabled so we pass the first check
                            if (i == 0) {
                                entry.setEnabled(true);
                                hasEnabled = true;
                            } else {
                                entry.setEnabled(true);
                            }
                            entry.setPageBreakBefore(i > 0);
                            entries.add(entry);
                        }

                        AssemblyConfigDTO dto = new AssemblyConfigDTO();
                        dto.setSegments(entries);
                        return new ConfigWithExistingIds(dto, existingIds);
                    });
                })
        );
    }

    /**
     * Generates valid AssemblyConfigDTO: at least one enabled segment, all IDs unique and positive.
     */
    @Provide
    Arbitrary<AssemblyConfigDTO> validConfigs() {
        return Arbitraries.integers().between(1, 10).flatMap(size -> {
            Arbitrary<List<Long>> idsArb = Arbitraries.longs().between(1L, 1000L)
                    .set().ofSize(size)
                    .map(ArrayList::new);

            return idsArb.flatMap(ids ->
                Arbitraries.of(true, false).list().ofSize(ids.size()).map(enabledFlags -> {
                    List<AssemblySegmentEntry> entries = new ArrayList<>();
                    boolean hasEnabled = false;
                    for (int i = 0; i < ids.size(); i++) {
                        AssemblySegmentEntry entry = new AssemblySegmentEntry();
                        entry.setSegmentId(ids.get(i));
                        entry.setPosition(i);
                        entry.setEnabled(enabledFlags.get(i));
                        entry.setPageBreakBefore(i > 0);
                        if (enabledFlags.get(i)) hasEnabled = true;
                        entries.add(entry);
                    }
                    // Guarantee at least one enabled
                    if (!hasEnabled) {
                        entries.get(0).setEnabled(true);
                    }

                    AssemblyConfigDTO dto = new AssemblyConfigDTO();
                    dto.setSegments(entries);
                    return dto;
                })
            );
        });
    }
}
