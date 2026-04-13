package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.entity.Segment;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.SegmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssemblyConfigServiceTest {

    @Mock
    private SegmentRepository segmentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AssemblyConfigService service;

    @BeforeEach
    void setUp() {
        service = new AssemblyConfigService(objectMapper, segmentRepository);
    }

    // ── serialize / deserialize ──

    @Test
    void serialize_roundTrip_preservesData() {
        AssemblyConfigDTO config = buildConfig(
                entry(1L, 0, true, false, null, null, null),
                entry(2L, 1, true, true, 3, "data.includeAppendix === true",
                        Map.of("companyName", "global.company.name"))
        );

        String json = service.serialize(config);
        assertNotNull(json);
        assertTrue(json.contains("\"segmentId\":1"));
        assertTrue(json.contains("\"segmentId\":2"));

        AssemblyConfigDTO restored = service.deserialize(json);
        assertEquals(2, restored.getSegments().size());

        AssemblySegmentEntry first = restored.getSegments().get(0);
        assertEquals(1L, first.getSegmentId());
        assertEquals(0, first.getPosition());
        assertTrue(first.isEnabled());
        assertFalse(first.isPageBreakBefore());
        assertNull(first.getLockedVersion());
        assertNull(first.getConditionExpression());
        assertNull(first.getDataScope());

        AssemblySegmentEntry second = restored.getSegments().get(1);
        assertEquals(2L, second.getSegmentId());
        assertEquals(1, second.getPosition());
        assertTrue(second.isEnabled());
        assertTrue(second.isPageBreakBefore());
        assertEquals(3, second.getLockedVersion());
        assertEquals("data.includeAppendix === true", second.getConditionExpression());
        assertEquals("global.company.name", second.getDataScope().get("companyName"));
    }

    @Test
    void deserialize_invalidJson_throwsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deserialize("not-valid-json"));
        assertEquals(ErrorCode.INTERNAL_ERROR, ex.getErrorCode());
    }

    // ── validate: enabled segment check ──

    @Test
    void validate_noSegments_throwsCompositeTemplateEmpty() {
        AssemblyConfigDTO config = new AssemblyConfigDTO();
        config.setSegments(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validate(config));
        assertEquals(ErrorCode.COMPOSITE_TEMPLATE_EMPTY, ex.getErrorCode());
    }

    @Test
    void validate_emptySegmentList_throwsCompositeTemplateEmpty() {
        AssemblyConfigDTO config = new AssemblyConfigDTO();
        config.setSegments(Collections.emptyList());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validate(config));
        assertEquals(ErrorCode.COMPOSITE_TEMPLATE_EMPTY, ex.getErrorCode());
    }

    @Test
    void validate_allDisabled_throwsCompositeTemplateEmpty() {
        AssemblyConfigDTO config = buildConfig(
                entry(1L, 0, false, false, null, null, null),
                entry(2L, 1, false, false, null, null, null)
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validate(config));
        assertEquals(ErrorCode.COMPOSITE_TEMPLATE_EMPTY, ex.getErrorCode());
    }

    // ── validate: segment existence check ──

    @Test
    void validate_allSegmentsExist_success() {
        AssemblyConfigDTO config = buildConfig(
                entry(1L, 0, true, false, null, null, null),
                entry(2L, 1, true, true, null, null, null)
        );

        when(segmentRepository.findAllById(anyCollection()))
                .thenReturn(List.of(createSegment(1L), createSegment(2L)));

        assertDoesNotThrow(() -> service.validate(config));
    }

    @Test
    void validate_missingSegmentId_throwsSegmentNotFound() {
        AssemblyConfigDTO config = buildConfig(
                entry(1L, 0, true, false, null, null, null),
                entry(999L, 1, true, false, null, null, null)
        );

        when(segmentRepository.findAllById(anyCollection()))
                .thenReturn(List.of(createSegment(1L)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validate(config));
        assertEquals(ErrorCode.SEGMENT_NOT_FOUND, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("999"));
    }

    // ── helpers ──

    private AssemblyConfigDTO buildConfig(AssemblySegmentEntry... entries) {
        AssemblyConfigDTO config = new AssemblyConfigDTO();
        config.setSegments(List.of(entries));
        return config;
    }

    private AssemblySegmentEntry entry(Long segmentId, int position, boolean enabled,
                                       boolean pageBreakBefore, Integer lockedVersion,
                                       String conditionExpression,
                                       Map<String, String> dataScope) {
        AssemblySegmentEntry e = new AssemblySegmentEntry();
        e.setSegmentId(segmentId);
        e.setPosition(position);
        e.setEnabled(enabled);
        e.setPageBreakBefore(pageBreakBefore);
        e.setLockedVersion(lockedVersion);
        e.setConditionExpression(conditionExpression);
        e.setDataScope(dataScope);
        return e;
    }

    private Segment createSegment(Long id) {
        Segment s = new Segment();
        s.setId(id);
        s.setTenantId(1L);
        s.setName("Segment " + id);
        s.setFilePath("segments/1/" + id + "_test.docx");
        s.setCreatedBy(1L);
        return s;
    }
}
