package com.docgen.service;

import com.docgen.dto.CompositeCoverageReport;
import com.docgen.entity.ParameterDefinition;
import com.docgen.entity.Template;
import com.docgen.entity.TestCase;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TestCaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ScenarioReadinessService}.
 */
@ExtendWith(MockitoExtension.class)
class ScenarioReadinessServiceTest {

    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private TemplateScanService templateScanService;
    @Mock
    private ParameterRepository parameterRepository;
    @Mock
    private TestCaseRepository testCaseRepository;
    @Mock
    private CompositeCoverageService compositeCoverageService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TemplateCoverageAnalyzer analyzer = new TemplateCoverageAnalyzer();
    private ScenarioReadinessService service;

    @BeforeEach
    void setUp() {
        service = new ScenarioReadinessService(
                templateRepository,
                templateScanService,
                parameterRepository,
                testCaseRepository,
                objectMapper,
                analyzer,
                compositeCoverageService);
    }

    @Test
    void compositeReport_usesVariableAggregateAndEmptyScenarios() {
        Template t = new Template();
        t.setId(9L);
        t.setTemplateType("COMPOSITE");
        when(templateRepository.findById(9L)).thenReturn(Optional.of(t));

        CompositeCoverageReport comp = new CompositeCoverageReport();
        comp.setOverallCoveragePercent(72.5);
        when(compositeCoverageService.checkCoverage(9L)).thenReturn(comp);

        var dto = service.getScenarioReadiness(9L);

        assertEquals(9L, dto.getTemplateId());
        assertEquals(72.5, dto.getReadiness().getOverallReadiness(), 0.01);
        assertEquals(72.5, dto.getReadiness().getRequiredInformationReadiness(), 0.01);
        assertTrue(dto.getScenarios().isEmpty());
        assertTrue(dto.getWarnings().stream().anyMatch(w -> w.contains("COMPOSITE")));
        verify(compositeCoverageService).checkCoverage(9L);
        verifyNoInteractions(templateScanService);
    }

    @Test
    void singleFile_aggregateMatchesParameterOnlyWhenNoPlaceholders() throws Exception {
        Template t = new Template();
        t.setId(1L);
        t.setTemplateType("SINGLE");
        t.setTemplateFilePath("templates/1/x.docx");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(t));
        when(templateScanService.scanPlaceholders(anyString())).thenReturn(List.of());

        ParameterDefinition p1 = makeParam(1L, "name");
        ParameterDefinition p2 = makeParam(2L, "email");
        when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(1L)).thenReturn(List.of(p1, p2));

        TestCase tc = new TestCase();
        tc.setId(100L);
        tc.setTemplateId(1L);
        tc.setName("full");
        tc.setTestDataJson(objectMapper.writeValueAsString(Map.of("name", "A", "email", "a@b.com")));
        when(testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(tc));

        var dto = service.getScenarioReadiness(1L);

        assertEquals(100.0, dto.getReadiness().getRequiredInformationReadiness(), 0.01);
        assertEquals(100.0, dto.getReadiness().getOverallReadiness(), 0.01);
        assertEquals(1, dto.getScenarios().size());
        assertEquals(100L, dto.getScenarios().get(0).getTestCaseId());
        assertEquals(100.0, dto.getScenarios().get(0).getOverallReadiness(), 0.01);
    }

    private static ParameterDefinition makeParam(Long id, String name) {
        ParameterDefinition p = new ParameterDefinition();
        p.setId(id);
        p.setTemplateId(1L);
        p.setName(name);
        p.setParameterType("REQUEST");
        p.setDataType("STRING");
        p.setSortOrder(id.intValue());
        return p;
    }
}
