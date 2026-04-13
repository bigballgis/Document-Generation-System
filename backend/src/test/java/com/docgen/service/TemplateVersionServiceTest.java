package com.docgen.service;

import com.docgen.dto.TemplateDTO;
import com.docgen.dto.TemplateVersionDTO;
import com.docgen.dto.UpdateTemplateRequest;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateVersion;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateTagMappingRepository;
import com.docgen.repository.TemplateVersionRepository;
import com.docgen.util.TenantContext;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateVersionServiceTest {

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private TemplateVersionRepository templateVersionRepository;

    @Mock
    private MinioClient minioClient;

    private TemplateService templateService;

    @BeforeEach
    void setUp() throws Exception {
        templateService = new TemplateService(templateRepository, templateVersionRepository, mock(TemplateTagMappingRepository.class), minioClient);
        Field bucketField = TemplateService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(templateService, "docgen-test");
        TenantContext.setCurrentTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── getTemplateVersions tests ──

    @Test
    void getTemplateVersions_returnsVersionsOrderedByVersionNumberDesc() {
        Template template = createTestTemplate();
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        TemplateVersion v2 = createTestVersion(2L, 1L, 2);
        TemplateVersion v1 = createTestVersion(1L, 1L, 1);
        when(templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(1L))
                .thenReturn(List.of(v2, v1));

        List<TemplateVersionDTO> result = templateService.getTemplateVersions(1L);

        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getVersionNumber());
        assertEquals(1, result.get(1).getVersionNumber());
    }

    @Test
    void getTemplateVersions_templateNotFound_throws() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> templateService.getTemplateVersions(99L));
    }

    @Test
    void getTemplateVersions_noVersions_returnsEmptyList() {
        Template template = createTestTemplate();
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateVersionRepository.findByTemplateIdOrderByVersionNumberDesc(1L))
                .thenReturn(List.of());

        List<TemplateVersionDTO> result = templateService.getTemplateVersions(1L);

        assertTrue(result.isEmpty());
    }

    // ── updateTemplate auto-version creation tests ──

    @Test
    void updateTemplate_createsVersionWithIncrementedNumber() {
        Template existing = createTestTemplate();
        when(templateRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateVersionRepository.findMaxVersionNumber(1L)).thenReturn(Optional.of(3));
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTemplateRequest request = new UpdateTemplateRequest();
        request.setName("Updated");

        templateService.updateTemplate(1L, request, null);

        ArgumentCaptor<TemplateVersion> captor = ArgumentCaptor.forClass(TemplateVersion.class);
        verify(templateVersionRepository).save(captor.capture());
        TemplateVersion savedVersion = captor.getValue();

        assertEquals(4, savedVersion.getVersionNumber());
        assertEquals(1L, savedVersion.getTemplateId());
        assertNotNull(savedVersion.getConfigJson());
        assertTrue(savedVersion.getConfigJson().contains("Updated"));
    }

    @Test
    void updateTemplate_firstVersion_startsAtOne() {
        Template existing = createTestTemplate();
        when(templateRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateVersionRepository.findMaxVersionNumber(1L)).thenReturn(Optional.empty());
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTemplateRequest request = new UpdateTemplateRequest();
        request.setName("First Update");

        templateService.updateTemplate(1L, request, null);

        ArgumentCaptor<TemplateVersion> captor = ArgumentCaptor.forClass(TemplateVersion.class);
        verify(templateVersionRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getVersionNumber());
    }

    // ── rollbackToVersion tests ──

    @Test
    void rollbackToVersion_success_createsNewVersion() {
        Template template = createTestTemplate();
        template.setName("Current Name");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        TemplateVersion targetVersion = createTestVersion(5L, 1L, 2);
        targetVersion.setConfigJson("{\"name\":\"Old Name\",\"description\":\"Old Desc\","
                + "\"outputFormat\":\"PDF\",\"storageStrategy\":\"PERSISTENT\","
                + "\"async\":false,\"teamId\":null,\"categoryId\":null,"
                + "\"reviewRequired\":false,\"status\":\"DRAFT\"}");
        targetVersion.setTemplateFilePath("templates/1/old_file.docx");

        when(templateVersionRepository.findByIdAndTemplateId(5L, 1L))
                .thenReturn(Optional.of(targetVersion));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateVersionRepository.findMaxVersionNumber(1L)).thenReturn(Optional.of(4));
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        TemplateDTO result = templateService.rollbackToVersion(1L, 5L);

        assertEquals("Old Name", result.getName());
        assertEquals("templates/1/old_file.docx", result.getTemplateFilePath());

        // Verify a new version was created with incremented number
        ArgumentCaptor<TemplateVersion> captor = ArgumentCaptor.forClass(TemplateVersion.class);
        verify(templateVersionRepository).save(captor.capture());
        assertEquals(5, captor.getValue().getVersionNumber());
    }

    @Test
    void rollbackToVersion_templateNotFound_throws() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> templateService.rollbackToVersion(99L, 1L));
    }

    @Test
    void rollbackToVersion_versionNotFound_throws() {
        Template template = createTestTemplate();
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateVersionRepository.findByIdAndTemplateId(99L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> templateService.rollbackToVersion(1L, 99L));
    }

    // ── Version config_json content tests ──

    @Test
    void updateTemplate_versionConfigJsonContainsAllMetadata() {
        Template existing = createTestTemplate();
        existing.setOutputFormat("PDF");
        existing.setStorageStrategy("PERSISTENT");
        existing.setAsync(true);
        existing.setReviewRequired(true);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateVersionRepository.findMaxVersionNumber(1L)).thenReturn(Optional.empty());
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTemplateRequest request = new UpdateTemplateRequest();
        request.setName("Config Test");

        templateService.updateTemplate(1L, request, null);

        ArgumentCaptor<TemplateVersion> captor = ArgumentCaptor.forClass(TemplateVersion.class);
        verify(templateVersionRepository).save(captor.capture());
        String configJson = captor.getValue().getConfigJson();

        assertTrue(configJson.contains("\"name\":\"Config Test\""));
        assertTrue(configJson.contains("\"outputFormat\":\"PDF\""));
        assertTrue(configJson.contains("\"storageStrategy\":\"PERSISTENT\""));
        assertTrue(configJson.contains("\"async\":true"));
        assertTrue(configJson.contains("\"reviewRequired\":true"));
        assertTrue(configJson.contains("\"status\":\"DRAFT\""));
    }

    // ── Helpers ──

    private Template createTestTemplate() {
        Template template = new Template();
        template.setId(1L);
        template.setTenantId(1L);
        template.setName("Test Template");
        template.setDescription("A test template");
        template.setTemplateFilePath("templates/1/uuid_test.docx");
        template.setOutputFormat("WORD");
        template.setStorageStrategy("TEMP");
        template.setAsync(false);
        template.setTeamId(null);
        template.setCreatedBy(10L);
        template.setCategoryId(null);
        template.setReviewRequired(false);
        template.setStatus("DRAFT");
        template.setCreatedAt(Instant.now());
        template.setUpdatedAt(Instant.now());
        return template;
    }

    private TemplateVersion createTestVersion(Long id, Long templateId, int versionNumber) {
        TemplateVersion version = new TemplateVersion();
        version.setId(id);
        version.setTemplateId(templateId);
        version.setVersionNumber(versionNumber);
        version.setTemplateFilePath("templates/1/v" + versionNumber + "_test.docx");
        version.setConfigJson("{\"name\":\"Version " + versionNumber + "\",\"description\":\"Desc\","
                + "\"outputFormat\":\"WORD\",\"storageStrategy\":\"TEMP\","
                + "\"async\":false,\"teamId\":null,\"categoryId\":null,"
                + "\"reviewRequired\":false,\"status\":\"DRAFT\"}");
        version.setCreatedBy(10L);
        version.setCreatedAt(Instant.now());
        return version;
    }
}
