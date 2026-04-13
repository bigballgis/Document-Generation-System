package com.docgen.service;

import com.docgen.dto.CreateTemplateRequest;
import com.docgen.dto.TemplateDTO;
import com.docgen.dto.TemplateQueryRequest;
import com.docgen.dto.UpdateTemplateRequest;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateVersion;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateTagMappingRepository;
import com.docgen.repository.TemplateVersionRepository;
import com.docgen.util.TenantContext;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private TemplateVersionRepository templateVersionRepository;

    @Mock
    private TemplateTagMappingRepository tagMappingRepository;

    @Mock
    private MinioClient minioClient;

    private TemplateService templateService;

    @BeforeEach
    void setUp() throws Exception {
        templateService = new TemplateService(templateRepository, templateVersionRepository, tagMappingRepository, minioClient);
        // Set the @Value-injected bucketName field via reflection for unit tests
        Field bucketField = TemplateService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(templateService, "docgen-test");
        TenantContext.setCurrentTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Create tests ──

    @Test
    void createTemplate_success() throws Exception {
        CreateTemplateRequest request = new CreateTemplateRequest();
        request.setName("Test Template");
        request.setDescription("A test template");
        request.setOutputFormat("WORD");

        MultipartFile file = new MockMultipartFile("file", "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "test content".getBytes());

        when(minioClient.putObject(any())).thenReturn(mock(ObjectWriteResponse.class));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> {
            Template t = inv.getArgument(0);
            t.setId(1L);
            t.setCreatedAt(Instant.now());
            t.setUpdatedAt(Instant.now());
            return t;
        });

        TemplateDTO result = templateService.createTemplate(request, file, 10L);

        assertEquals("Test Template", result.getName());
        assertEquals("A test template", result.getDescription());
        assertEquals("WORD", result.getOutputFormat());
        assertEquals("DRAFT", result.getStatus());
        assertEquals(1L, result.getTenantId());
        assertEquals(10L, result.getCreatedBy());
        assertNotNull(result.getId());
    }

    @Test
    void createTemplate_nullFile_createsEmptyDocx() throws Exception {
        CreateTemplateRequest request = new CreateTemplateRequest();
        request.setName("Test");

        when(minioClient.putObject(any())).thenReturn(mock(ObjectWriteResponse.class));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> {
            Template t = inv.getArgument(0);
            t.setId(1L);
            t.setCreatedAt(Instant.now());
            t.setUpdatedAt(Instant.now());
            return t;
        });

        TemplateDTO result = templateService.createTemplate(request, null, 10L);

        assertNotNull(result);
        assertEquals("Test", result.getName());
        assertEquals("DRAFT", result.getStatus());
    }

    @Test
    void createTemplate_emptyFile_createsEmptyDocx() throws Exception {
        CreateTemplateRequest request = new CreateTemplateRequest();
        request.setName("Test");

        MultipartFile file = new MockMultipartFile("file", "test.docx",
                "application/octet-stream", new byte[0]);

        when(minioClient.putObject(any())).thenReturn(mock(ObjectWriteResponse.class));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> {
            Template t = inv.getArgument(0);
            t.setId(1L);
            t.setCreatedAt(Instant.now());
            t.setUpdatedAt(Instant.now());
            return t;
        });

        TemplateDTO result = templateService.createTemplate(request, file, 10L);

        assertNotNull(result);
        assertEquals("Test", result.getName());
        assertEquals("DRAFT", result.getStatus());
    }

    // ── Get tests ──

    @Test
    void getTemplate_success() {
        Template template = createTestTemplate();
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        TemplateDTO result = templateService.getTemplate(1L);

        assertEquals(1L, result.getId());
        assertEquals("Test Template", result.getName());
    }

    @Test
    void getTemplate_notFound_throws() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> templateService.getTemplate(99L));
    }

    // ── List tests ──

    @Test
    void listTemplates_withKeyword_returnsPaginatedResults() {
        Pageable pageable = PageRequest.of(0, 10);
        Template template = createTestTemplate();
        Page<Template> page = new PageImpl<>(List.of(template), pageable, 1);
        when(templateRepository.searchByKeyword("Test", pageable)).thenReturn(page);

        TemplateQueryRequest query = new TemplateQueryRequest("Test");
        Page<TemplateDTO> result = templateService.listTemplates(query, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Test Template", result.getContent().get(0).getName());
    }

    @Test
    void listTemplates_noKeyword_returnsAll() {
        Pageable pageable = PageRequest.of(0, 10);
        Template template = createTestTemplate();
        Page<Template> page = new PageImpl<>(List.of(template), pageable, 1);
        when(templateRepository.searchByKeyword(null, pageable)).thenReturn(page);

        Page<TemplateDTO> result = templateService.listTemplates(null, pageable);

        assertEquals(1, result.getTotalElements());
    }

    // ── Update tests ──

    @Test
    void updateTemplate_success_noFileChange() {
        Template existing = createTestTemplate();
        when(templateRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));
        when(templateVersionRepository.findMaxVersionNumber(1L)).thenReturn(Optional.empty());
        when(templateVersionRepository.save(any(TemplateVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTemplateRequest request = new UpdateTemplateRequest();
        request.setName("Updated Name");
        request.setDescription("Updated description");

        TemplateDTO result = templateService.updateTemplate(1L, request, null);

        assertEquals("Updated Name", result.getName());
        assertEquals("Updated description", result.getDescription());
        verify(templateVersionRepository).save(any(TemplateVersion.class));
    }

    @Test
    void updateTemplate_notFound_throws() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> templateService.updateTemplate(99L, new UpdateTemplateRequest(), null));
    }

    // ── Delete tests ──

    @Test
    void deleteTemplate_success() {
        Template template = createTestTemplate();
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        templateService.deleteTemplate(1L);

        verify(templateRepository).delete(template);
    }

    @Test
    void deleteTemplate_notFound_throws() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> templateService.deleteTemplate(99L));
    }

    // ── Clone tests ──

    @Test
    void cloneTemplate_success() throws Exception {
        Template source = createTestTemplate();
        when(templateRepository.findById(1L)).thenReturn(Optional.of(source));
        when(minioClient.copyObject(any())).thenReturn(mock(ObjectWriteResponse.class));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> {
            Template t = inv.getArgument(0);
            t.setId(2L);
            t.setCreatedAt(Instant.now());
            t.setUpdatedAt(Instant.now());
            return t;
        });

        TemplateDTO result = templateService.cloneTemplate(1L);

        assertEquals("Test Template - 副本", result.getName());
        assertEquals("DRAFT", result.getStatus());
        assertEquals(source.getDescription(), result.getDescription());
        assertEquals(source.getOutputFormat(), result.getOutputFormat());
        assertEquals(source.getStorageStrategy(), result.getStorageStrategy());
        assertEquals(source.getTenantId(), result.getTenantId());
        assertEquals(source.getCreatedBy(), result.getCreatedBy());
        assertNotEquals(source.getId(), result.getId());
    }

    @Test
    void cloneTemplate_notFound_throws() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> templateService.cloneTemplate(99L));
    }

    // ── Helper ──

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
}
