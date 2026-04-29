package com.docgen.service;

import com.docgen.config.CompositeZipImportProperties;
import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.AssemblySegmentEntry;
import com.docgen.dto.CompositeCoverageReport;
import com.docgen.entity.Template;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TestCaseRepository;
import com.docgen.util.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import okhttp3.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the export status constraint in CompositeImportExportService.exportAsZip.
 * Validates: Requirements 12.1
 */
@ExtendWith(MockitoExtension.class)
class CompositeImportExportServiceExportTest {

    @Mock private TemplateRepository templateRepository;
    @Mock private AssemblyConfigService assemblyConfigService;
    @Mock private MinioClient minioClient;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private CompositeCoverageService compositeCoverageService;
    @Mock private ParameterService parameterService;
    @Mock private com.docgen.repository.ParameterRepository parameterRepository;
    @Mock private RenderConfigValidator renderConfigValidator;

    private CompositeImportExportService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        org.mockito.Mockito.lenient().doNothing().when(renderConfigValidator)
                .validateForImport(org.mockito.ArgumentMatchers.any());
        service = new CompositeImportExportService(
                templateRepository, assemblyConfigService,
                minioClient, objectMapper,
                testCaseRepository, compositeCoverageService,
                parameterService, parameterRepository,
                new CompositeZipImportProperties(),
                renderConfigValidator);
        Field bucketField = CompositeImportExportService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(service, "docgen-test");
        TenantContext.setCurrentTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void exportAsZip_pendingReviewStatus_throwsBusinessException() {
        Template template = createTemplate("PENDING_REVIEW");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.exportAsZip(1L));
        assertEquals(ErrorCode.TEMPLATE_EXPORT_NOT_ACTIVE, ex.getErrorCode());
    }

    @Test
    void exportAsZip_reviewedStatus_throwsBusinessException() {
        Template template = createTemplate("REVIEWED");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.exportAsZip(1L));
        assertEquals(ErrorCode.TEMPLATE_EXPORT_NOT_ACTIVE, ex.getErrorCode());
    }

    @Test
    void exportAsZip_archivedStatus_throwsBusinessException() {
        Template template = createTemplate("ARCHIVED");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.exportAsZip(1L));
        assertEquals(ErrorCode.TEMPLATE_EXPORT_NOT_ACTIVE, ex.getErrorCode());
    }

    @Test
    void exportAsZip_activeStatus_doesNotThrowExportNotActive() throws Exception {
        Template template = createTemplate("ACTIVE");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        AssemblyConfigDTO config = new AssemblyConfigDTO();
        config.setSegments(List.of());
        when(assemblyConfigService.deserialize(any())).thenReturn(config);
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        CompositeCoverageReport report = new CompositeCoverageReport();
        report.setOverallCoveragePercent(100.0);
        report.setSegmentCoverages(List.of());
        when(compositeCoverageService.checkCoverage(1L)).thenReturn(report);

        byte[] result = service.exportAsZip(1L);
        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    private Template createTemplate(String status) {
        Template template = new Template();
        template.setId(1L);
        template.setTenantId(1L);
        template.setName("TestTemplate");
        template.setDescription("Test composite template");
        template.setTemplateFilePath("composite://TestTemplate");
        template.setTemplateType("COMPOSITE");
        template.setAssemblyConfig("{\"segments\":[]}");
        template.setCreatedBy(1L);
        template.setStatus(status);
        template.setCreatedAt(Instant.now());
        template.setUpdatedAt(Instant.now());
        return template;
    }
}
