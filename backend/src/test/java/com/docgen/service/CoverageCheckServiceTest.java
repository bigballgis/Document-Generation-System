package com.docgen.service;

import com.docgen.dto.CoverageReport;
import com.docgen.dto.PlaceholderInfo;
import com.docgen.entity.ParameterDefinition;
import com.docgen.entity.Template;
import com.docgen.entity.TestCase;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TestCaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the refactored {@link CoverageCheckService}.
 * Tests three-dimensional coverage: Branch / Loop / Parameter.
 *
 * <p><b>Validates: Requirements 8.7, 8.8, 8.9</b></p>
 */
@ExtendWith(MockitoExtension.class)
class CoverageCheckServiceTest {

    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private TemplateScanService templateScanService;
    @Mock
    private ParameterRepository parameterRepository;
    @Mock
    private TestCaseRepository testCaseRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CoverageCheckService service;
    private Template template;

    @BeforeEach
    void setUp() {
        service = new CoverageCheckService(
                templateRepository, templateScanService,
                parameterRepository, testCaseRepository, objectMapper);

        template = new Template();
        template.setId(1L);
        template.setTenantId(10L);
        template.setName("Test Template");
        template.setTemplateFilePath("templates/10/test.docx");
    }

    @Test
    void checkCoverage_zeroBranchesAndLoops_overallFromParametersOnly() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateScanService.scanPlaceholders(anyString())).thenReturn(List.of());

        ParameterDefinition p1 = makeParam(1L, "name");
        ParameterDefinition p2 = makeParam(2L, "email");
        when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(1L)).thenReturn(List.of(p1, p2));

        TestCase tc = makeTestCase("tc1", Map.of("name", "Alice", "email", "a@b.com"));
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(tc));

        CoverageReport report = service.checkCoverage(1L);

        assertEquals(100.0, report.getParameterCoverage());
        assertEquals(0, report.getTotalBranches());
        assertEquals(0, report.getTotalLoopScenarios());
        assertEquals(100.0, report.getOverallCoverage());
        assertFalse(report.isBelowThreshold());
    }

    @Test
    void checkCoverage_noParametersNoBranchesNoLoops_returns100() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateScanService.scanPlaceholders(anyString())).thenReturn(List.of());
        when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(1L)).thenReturn(List.of());
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        CoverageReport report = service.checkCoverage(1L);

        assertEquals(100.0, report.getOverallCoverage());
        assertFalse(report.isBelowThreshold());
    }

    @Test
    void checkCoverage_templateNotFound_throws() {
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.checkCoverage(999L));
    }

    @Test
    void checkCoverage_thresholdGating_belowThreshold() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateScanService.scanPlaceholders(anyString())).thenReturn(List.of());

        ParameterDefinition p1 = makeParam(1L, "name");
        ParameterDefinition p2 = makeParam(2L, "email");
        when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(1L)).thenReturn(List.of(p1, p2));

        // Only cover 1 of 2 params → 50% parameter coverage
        TestCase tc = makeTestCase("tc1", Map.of("name", "Alice"));
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(tc));

        CoverageReport report = service.checkCoverage(1L, 80.0);

        assertEquals(50.0, report.getParameterCoverage());
        assertTrue(report.isBelowThreshold());
        assertEquals(80.0, report.getThreshold());
    }

    @Test
    void checkCoverage_scanFailure_warnsAndContinues() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateScanService.scanPlaceholders(anyString()))
                .thenThrow(new RuntimeException("MinIO down"));
        when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(1L)).thenReturn(List.of());
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        CoverageReport report = service.checkCoverage(1L);

        assertNotNull(report.getWarnings());
        assertFalse(report.getWarnings().isEmpty());
        assertTrue(report.getWarnings().get(0).contains("MinIO down"));
    }

    @Test
    void checkCoverage_expressionDependencyWarnings_invalidTestDataJson() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateScanService.scanPlaceholders(anyString())).thenReturn(List.of());
        when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(1L)).thenReturn(List.of());

        TestCase tc = new TestCase();
        tc.setId(1L);
        tc.setTemplateId(1L);
        tc.setName("bad_tc");
        tc.setTestDataJson("not valid json");
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(tc));

        CoverageReport report = service.checkCoverage(1L);

        assertNotNull(report.getWarnings());
        assertTrue(report.getWarnings().stream().anyMatch(w -> w.contains("bad_tc")));
    }

    @Test
    void isBelowThreshold_delegatesToCheckCoverage() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateScanService.scanPlaceholders(anyString())).thenReturn(List.of());
        when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(1L)).thenReturn(List.of());
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        assertFalse(service.isBelowThreshold(1L, 100.0));
    }

    // ── Helpers ──

    private ParameterDefinition makeParam(Long id, String name) {
        ParameterDefinition p = new ParameterDefinition();
        p.setId(id);
        p.setTemplateId(1L);
        p.setName(name);
        p.setParameterType("REQUEST");
        p.setDataType("STRING");
        p.setSortOrder(id.intValue());
        return p;
    }

    private TestCase makeTestCase(String name, Map<String, Object> data) {
        TestCase tc = new TestCase();
        tc.setId((long) name.hashCode());
        tc.setTemplateId(1L);
        tc.setName(name);
        try {
            tc.setTestDataJson(objectMapper.writeValueAsString(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return tc;
    }
}
