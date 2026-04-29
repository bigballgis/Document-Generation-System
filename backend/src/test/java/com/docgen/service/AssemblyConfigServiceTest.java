package com.docgen.service;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AssemblyConfigServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AssemblyConfigService service;

    @BeforeEach
    void setUp() {
        service = new AssemblyConfigService(objectMapper);
    }


    @Test
    void serialize_roundTrip_preservesData() {
        AssemblyConfigDTO config = buildConfig(
                entry("segments/1/abc_cover.docx", "Cover", "COVER", 0, true, false, null, null),
                entry("segments/1/def_chapter.docx", "Chapter 1", "CHAPTER", 1, true, true,
                        "data.includeAppendix === true",
                        Map.of("companyName", "global.company.name"))
        );

        String json = service.serialize(config);
        assertNotNull(json);
        assertTrue(json.contains("\"filePath\":\"segments/1/abc_cover.docx\""));
        assertTrue(json.contains("\"filePath\":\"segments/1/def_chapter.docx\""));

        AssemblyConfigDTO restored = service.deserialize(json);
        assertEquals(2, restored.getSegments().size());

        AssemblySegmentEntry first = restored.getSegments().get(0);
        assertEquals("segments/1/abc_cover.docx", first.getFilePath());
        assertEquals("Cover", first.getName());
        assertEquals("COVER", first.getSegmentType());
        assertEquals(0, first.getPosition());
        assertTrue(first.isEnabled());
        assertFalse(first.isPageBreakBefore());
        assertNull(first.getConditionExpression());
        assertNull(first.getDataScope());

        AssemblySegmentEntry second = restored.getSegments().get(1);
        assertEquals("segments/1/def_chapter.docx", second.getFilePath());
        assertEquals("Chapter 1", second.getName());
        assertEquals("CHAPTER", second.getSegmentType());
        assertEquals(1, second.getPosition());
        assertTrue(second.isEnabled());
        assertTrue(second.isPageBreakBefore());
        assertEquals("data.includeAppendix === true", second.getConditionExpression());
        assertEquals("global.company.name", second.getDataScope().get("companyName"));
    }

    @Test
    void deserialize_invalidJson_throwsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deserialize("not-valid-json"));
        assertEquals(ErrorCode.INTERNAL_ERROR, ex.getErrorCode());
    }


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
                entry("segments/1/a.docx", "A", "COVER", 0, false, false, null, null),
                entry("segments/1/b.docx", "B", "CHAPTER", 1, false, false, null, null)
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validate(config));
        assertEquals(ErrorCode.COMPOSITE_TEMPLATE_EMPTY, ex.getErrorCode());
    }


    @Test
    void validate_filePathNull_throwsAssemblyConfigInvalid() {
        AssemblyConfigDTO config = buildConfig(
                entry(null, "Missing Path", "COVER", 0, true, false, null, null)
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validate(config));
        assertEquals(ErrorCode.ASSEMBLY_CONFIG_INVALID, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Missing Path"));
    }

    @Test
    void validate_filePathBlank_throwsAssemblyConfigInvalid() {
        AssemblyConfigDTO config = buildConfig(
                entry("   ", "Blank Path", "CHAPTER", 0, true, false, null, null)
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validate(config));
        assertEquals(ErrorCode.ASSEMBLY_CONFIG_INVALID, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Blank Path"));
    }

    @Test
    void validate_validConfig_success() {
        AssemblyConfigDTO config = buildConfig(
                entry("segments/1/cover.docx", "Cover", "COVER", 0, true, false, null, null),
                entry("segments/1/chapter.docx", "Chapter", "CHAPTER", 1, true, true, null, null)
        );

        assertDoesNotThrow(() -> service.validate(config));
    }

    @Test
    void validate_mixedEnabledDisabled_withValidFilePaths_success() {
        AssemblyConfigDTO config = buildConfig(
                entry("segments/1/cover.docx", "Cover", "COVER", 0, true, false, null, null),
                entry("segments/1/chapter.docx", "Chapter", "CHAPTER", 1, false, false, null, null)
        );

        assertDoesNotThrow(() -> service.validate(config));
    }


    private AssemblyConfigDTO buildConfig(AssemblySegmentEntry... entries) {
        AssemblyConfigDTO config = new AssemblyConfigDTO();
        config.setSegments(List.of(entries));
        return config;
    }

    private AssemblySegmentEntry entry(String filePath, String name, String segmentType,
                                       int position, boolean enabled, boolean pageBreakBefore,
                                       String conditionExpression,
                                       Map<String, String> dataScope) {
        AssemblySegmentEntry e = new AssemblySegmentEntry();
        e.setFilePath(filePath);
        e.setName(name);
        e.setSegmentType(segmentType);
        e.setPosition(position);
        e.setEnabled(enabled);
        e.setPageBreakBefore(pageBreakBefore);
        e.setConditionExpression(conditionExpression);
        e.setDataScope(dataScope);
        return e;
    }
}

