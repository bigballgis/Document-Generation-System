package com.docgen.property;

import com.docgen.dto.AssemblyConfigDTO;
import com.docgen.dto.CompositeCoverageReport;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateState;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TestCaseRepository;
import com.docgen.service.AssemblyConfigService;
import com.docgen.service.CompositeCoverageService;
import com.docgen.service.CompositeImportExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.docgen.util.TenantContext;
import io.minio.MinioClient;
import net.jqwik.api.*;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for export constraint enforcement.
 *
 * <p>Feature: template-workflow-stages, Property 3: 导出约束后端强制</p>
 * <p><b>Validates: Requirements 12.1</b></p>
 *
 * For any non-ACTIVE TemplateState, exportAsZip must throw BusinessException
 * with error code TEMPLATE_EXPORT_NOT_ACTIVE and HTTP 400.
 * For ACTIVE state, exportAsZip must not throw that exception.
 */
@Tag("Feature: template-workflow-stages, Property 3: 导出约束后端强制")
class ExportConstraintPropertyTest {

    private CompositeImportExportService buildService(TemplateRepository templateRepository,
                                                       AssemblyConfigService assemblyConfigService,
                                                       CompositeCoverageService compositeCoverageService,
                                                       TestCaseRepository testCaseRepository) throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        com.docgen.service.ParameterService parameterService = mock(com.docgen.service.ParameterService.class);
        com.docgen.repository.ParameterRepository parameterRepository = mock(com.docgen.repository.ParameterRepository.class);
        CompositeImportExportService service = new CompositeImportExportService(
                templateRepository, assemblyConfigService,
                minioClient, objectMapper,
                testCaseRepository, compositeCoverageService,
                parameterService, parameterRepository);
        Field bucketField = CompositeImportExportService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(service, "docgen-test");
        return service;
    }

    private Template createTemplate(String status) {
        Template template = new Template();
        template.setId(1L);
        template.setTenantId(1L);
        template.setName("PropertyTestTemplate");
        template.setDescription("Template for property test");
        template.setTemplateFilePath("composite://PropertyTestTemplate");
        template.setTemplateType("COMPOSITE");
        template.setAssemblyConfig("{\"segments\":[]}");
        template.setCreatedBy(1L);
        template.setStatus(status);
        template.setCreatedAt(Instant.now());
        template.setUpdatedAt(Instant.now());
        return template;
    }

    /**
     * Property 3: For any non-ACTIVE template state, exportAsZip throws
     * BusinessException with TEMPLATE_EXPORT_NOT_ACTIVE and HTTP 400.
     */
    @Property(tries = 100)
    void nonActiveState_exportAsZip_throwsBusinessException(
            @ForAll("nonActiveStates") TemplateState state) throws Exception {
        TenantContext.setCurrentTenantId(1L);
        try {
            TemplateRepository templateRepository = mock(TemplateRepository.class);
            Template template = createTemplate(state.name());
            when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

            CompositeImportExportService service = buildService(
                    templateRepository, mock(AssemblyConfigService.class),
                    mock(CompositeCoverageService.class), mock(TestCaseRepository.class));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.exportAsZip(1L));
            assertEquals(ErrorCode.TEMPLATE_EXPORT_NOT_ACTIVE, ex.getErrorCode(),
                    "Error code must be TEMPLATE_EXPORT_NOT_ACTIVE for state " + state);
            assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus(),
                    "HTTP status must be 400 for state " + state);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Property 3: For ACTIVE state, exportAsZip does not throw
     * TEMPLATE_EXPORT_NOT_ACTIVE exception.
     */
    @Property(tries = 100)
    void activeState_exportAsZip_doesNotThrowExportNotActive(
            @ForAll("activeState") TemplateState state) throws Exception {
        TenantContext.setCurrentTenantId(1L);
        try {
            TemplateRepository templateRepository = mock(TemplateRepository.class);
            Template template = createTemplate(state.name());
            when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

            AssemblyConfigService assemblyConfigService = mock(AssemblyConfigService.class);
            AssemblyConfigDTO config = new AssemblyConfigDTO();
            config.setSegments(List.of());
            when(assemblyConfigService.deserialize(any())).thenReturn(config);

            TestCaseRepository testCaseRepository = mock(TestCaseRepository.class);
            when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

            CompositeCoverageService compositeCoverageService = mock(CompositeCoverageService.class);
            CompositeCoverageReport report = new CompositeCoverageReport();
            report.setOverallCoveragePercent(100.0);
            report.setSegmentCoverages(List.of());
            when(compositeCoverageService.checkCoverage(1L)).thenReturn(report);

            CompositeImportExportService service = buildService(
                    templateRepository, assemblyConfigService,
                    compositeCoverageService, testCaseRepository);

            byte[] result = service.exportAsZip(1L);
            assertNotNull(result, "ACTIVE template export should return non-null bytes");
            assertTrue(result.length > 0, "ACTIVE template export should return non-empty bytes");
        } finally {
            TenantContext.clear();
        }
    }

    @Provide
    Arbitrary<TemplateState> nonActiveStates() {
        return Arbitraries.of(
                TemplateState.DRAFT,
                TemplateState.PENDING_REVIEW,
                TemplateState.REVIEWED,
                TemplateState.ARCHIVED
        );
    }

    @Provide
    Arbitrary<TemplateState> activeState() {
        return Arbitraries.of(TemplateState.ACTIVE);
    }
}
