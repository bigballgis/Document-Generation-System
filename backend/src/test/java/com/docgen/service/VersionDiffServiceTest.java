package com.docgen.service;

import com.docgen.dto.VersionDiffResult;
import com.docgen.dto.VersionDiffResult.DiffEntry;
import com.docgen.dto.VersionDiffResult.DiffEntry.ChangeType;
import com.docgen.entity.TemplateVersion;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TemplateVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VersionDiffServiceTest {

    @Mock
    private TemplateVersionRepository versionRepository;

    private VersionDiffService service;

    @BeforeEach
    void setUp() {
        service = new VersionDiffService(versionRepository);
    }

    // ── compareVersions ──

    @Test
    void compareVersions_detectsModifiedFields() {
        TemplateVersion v1 = createVersion(1L, 100L, 1,
                "{\"name\":\"Old Name\",\"description\":\"Old Desc\",\"outputFormat\":\"WORD\","
                        + "\"storageStrategy\":\"PERSISTENT\",\"async\":false,\"teamId\":1,"
                        + "\"categoryId\":2,\"reviewRequired\":false,\"status\":\"DRAFT\"}",
                "templates/10/old.docx");
        TemplateVersion v2 = createVersion(2L, 100L, 2,
                "{\"name\":\"New Name\",\"description\":\"New Desc\",\"outputFormat\":\"PDF\","
                        + "\"storageStrategy\":\"PERSISTENT\",\"async\":true,\"teamId\":1,"
                        + "\"categoryId\":3,\"reviewRequired\":true,\"status\":\"ACTIVE\"}",
                "templates/10/new.docx");

        when(versionRepository.findByTemplateIdAndVersionNumber(100L, 1)).thenReturn(Optional.of(v1));
        when(versionRepository.findByTemplateIdAndVersionNumber(100L, 2)).thenReturn(Optional.of(v2));

        VersionDiffResult result = service.compareVersions(100L, 1, 2);

        assertEquals(100L, result.getTemplateId());
        assertEquals(1, result.getVersionA());
        assertEquals(2, result.getVersionB());

        // Text diffs: name, description, outputFormat, status changed
        assertTrue(result.getTextDiffs().size() >= 3);
        assertTrue(result.getTextDiffs().stream()
                .anyMatch(d -> "name".equals(d.getField()) && d.getChangeType() == ChangeType.MODIFIED));
        assertTrue(result.getTextDiffs().stream()
                .anyMatch(d -> "description".equals(d.getField()) && d.getChangeType() == ChangeType.MODIFIED));

        // Variable diffs: async and reviewRequired changed
        assertTrue(result.getVariableDiffs().size() >= 1);

        // Expression diffs: templateFilePath changed
        assertEquals(1, result.getExpressionDiffs().size());
        assertEquals("templateFilePath", result.getExpressionDiffs().get(0).getField());

        // Summary
        assertNotNull(result.getChangeSummary());
        assertTrue(result.getChangeSummary().getTotalChanges() > 0);
        assertTrue(result.getChangeSummary().getModifications() > 0);
        assertEquals(0, result.getChangeSummary().getAdditions());
        assertEquals(0, result.getChangeSummary().getDeletions());
    }

    @Test
    void compareVersions_identicalVersions_noDiffs() {
        String config = "{\"name\":\"Test\",\"description\":\"Desc\",\"outputFormat\":\"WORD\","
                + "\"storageStrategy\":\"TEMP\",\"async\":false,\"teamId\":null,"
                + "\"categoryId\":null,\"reviewRequired\":false,\"status\":\"DRAFT\"}";
        TemplateVersion v1 = createVersion(1L, 100L, 1, config, "templates/10/test.docx");
        TemplateVersion v2 = createVersion(2L, 100L, 2, config, "templates/10/test.docx");

        when(versionRepository.findByTemplateIdAndVersionNumber(100L, 1)).thenReturn(Optional.of(v1));
        when(versionRepository.findByTemplateIdAndVersionNumber(100L, 2)).thenReturn(Optional.of(v2));

        VersionDiffResult result = service.compareVersions(100L, 1, 2);

        assertTrue(result.getTextDiffs().isEmpty());
        assertTrue(result.getVariableDiffs().isEmpty());
        assertTrue(result.getDataSourceDiffs().isEmpty());
        assertTrue(result.getExpressionDiffs().isEmpty());
        assertEquals(0, result.getChangeSummary().getTotalChanges());
    }

    @Test
    void compareVersions_versionANotFound_throwsException() {
        when(versionRepository.findByTemplateIdAndVersionNumber(100L, 1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.compareVersions(100L, 1, 2));
    }

    @Test
    void compareVersions_versionBNotFound_throwsException() {
        TemplateVersion v1 = createVersion(1L, 100L, 1, "{}", "path.docx");
        when(versionRepository.findByTemplateIdAndVersionNumber(100L, 1)).thenReturn(Optional.of(v1));
        when(versionRepository.findByTemplateIdAndVersionNumber(100L, 2)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.compareVersions(100L, 1, 2));
    }

    // ── parseConfigJson ──

    @Test
    void parseConfigJson_parsesValidJson() {
        String json = "{\"name\":\"Test\",\"description\":\"A desc\",\"async\":true,\"teamId\":null}";
        Map<String, String> map = service.parseConfigJson(json);

        assertEquals("Test", map.get("name"));
        assertEquals("A desc", map.get("description"));
        assertEquals("true", map.get("async"));
        assertEquals("null", map.get("teamId"));
    }

    @Test
    void parseConfigJson_emptyJson_returnsEmptyMap() {
        assertTrue(service.parseConfigJson("{}").isEmpty());
        assertTrue(service.parseConfigJson("").isEmpty());
        assertTrue(service.parseConfigJson(null).isEmpty());
    }

    @Test
    void parseConfigJson_handlesEscapedQuotes() {
        String json = "{\"name\":\"Test \\\"quoted\\\"\"}";
        Map<String, String> map = service.parseConfigJson(json);
        assertEquals("Test \"quoted\"", map.get("name"));
    }

    // ── diffConfigFields ──

    @Test
    void diffConfigFields_detectsAddedField() {
        Map<String, String> a = Map.of("name", "Test");
        Map<String, String> b = Map.of("name", "Test", "description", "New");

        var diffs = service.diffConfigFields(a, b, Set.of("name", "description"));
        assertEquals(1, diffs.size());
        assertEquals(ChangeType.ADDED, diffs.get(0).getChangeType());
        assertEquals("description", diffs.get(0).getField());
    }

    @Test
    void diffConfigFields_detectsRemovedField() {
        Map<String, String> a = Map.of("name", "Test", "description", "Old");
        Map<String, String> b = Map.of("name", "Test");

        var diffs = service.diffConfigFields(a, b, Set.of("name", "description"));
        assertEquals(1, diffs.size());
        assertEquals(ChangeType.REMOVED, diffs.get(0).getChangeType());
        assertEquals("description", diffs.get(0).getField());
    }

    @Test
    void diffConfigFields_noChanges_returnsEmpty() {
        Map<String, String> a = Map.of("name", "Test");
        Map<String, String> b = Map.of("name", "Test");

        var diffs = service.diffConfigFields(a, b, Set.of("name"));
        assertTrue(diffs.isEmpty());
    }

    // ── changeSummary counts ──

    @Test
    void compareVersions_summaryCountsCorrect() {
        TemplateVersion v1 = createVersion(1L, 100L, 1,
                "{\"name\":\"A\",\"description\":\"D\",\"outputFormat\":\"WORD\","
                        + "\"storageStrategy\":\"TEMP\",\"async\":false,\"teamId\":null,"
                        + "\"categoryId\":null,\"reviewRequired\":false,\"status\":\"DRAFT\"}",
                "path.docx");
        TemplateVersion v2 = createVersion(2L, 100L, 2,
                "{\"name\":\"B\",\"description\":\"D\",\"outputFormat\":\"WORD\","
                        + "\"storageStrategy\":\"TEMP\",\"async\":false,\"teamId\":null,"
                        + "\"categoryId\":null,\"reviewRequired\":false,\"status\":\"DRAFT\"}",
                "path.docx");

        when(versionRepository.findByTemplateIdAndVersionNumber(100L, 1)).thenReturn(Optional.of(v1));
        when(versionRepository.findByTemplateIdAndVersionNumber(100L, 2)).thenReturn(Optional.of(v2));

        VersionDiffResult result = service.compareVersions(100L, 1, 2);

        assertEquals(1, result.getChangeSummary().getTotalChanges());
        assertEquals(1, result.getChangeSummary().getModifications());
        assertEquals(0, result.getChangeSummary().getAdditions());
        assertEquals(0, result.getChangeSummary().getDeletions());
    }

    // ── Helper ──

    private TemplateVersion createVersion(Long id, Long templateId, int versionNumber,
                                          String configJson, String filePath) {
        TemplateVersion v = new TemplateVersion();
        v.setId(id);
        v.setTemplateId(templateId);
        v.setVersionNumber(versionNumber);
        v.setConfigJson(configJson);
        v.setTemplateFilePath(filePath);
        v.setCreatedBy(1L);
        v.setCreatedAt(Instant.now());
        return v;
    }
}
