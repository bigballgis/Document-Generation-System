package com.docgen.property;

import com.docgen.dto.CoverageReport;
import com.docgen.dto.PlaceholderInfo;
import com.docgen.entity.ParameterDefinition;
import com.docgen.entity.Template;
import com.docgen.entity.TestCase;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TestCaseRepository;
import com.docgen.service.CoverageCheckService;
import com.docgen.service.TemplateScanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 13: 模板覆盖率计算正确性 (updated for three-dimensional coverage)
 *
 * For any set of parameters and test cases, the parameter coverage percentage
 * should equal (coveredParams / totalParams) × 100%, and overall coverage
 * should be correctly computed.
 *
 * <p><b>Validates: Requirements 8.2, 8.3, 8.4, 8.5</b></p>
 */
@Tag("feature-template-parameter-redesign-property-13")
class CoverageCalculationPropertyTest {

    private static final Long TEMPLATE_ID = 1L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Property 1: Parameter coverage = (coveredParams / totalParams) × 100%.
     */
    @Property(tries = 200)
    @Label("Parameter coverage percentage is correct")
    void parameterCoverageIsCorrect(
            @ForAll @IntRange(min = 0, max = 50) int coveredCount,
            @ForAll @IntRange(min = 0, max = 50) int uncoveredCount) {

        int totalParams = coveredCount + uncoveredCount;
        if (totalParams == 0) {
            // No params → overall 100%
            CoverageCheckService service = buildService(List.of(), List.of(), List.of());
            CoverageReport report = service.checkCoverage(TEMPLATE_ID);
            assertEquals(100.0, report.getOverallCoverage(), 0.001);
            return;
        }

        List<ParameterDefinition> params = new ArrayList<>();
        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < coveredCount; i++) {
            params.add(makeParam((long) (i + 1), "covered_" + i));
            data.put("covered_" + i, "value_" + i);
        }
        for (int i = 0; i < uncoveredCount; i++) {
            params.add(makeParam((long) (coveredCount + i + 1), "uncovered_" + i));
        }

        List<TestCase> testCases = data.isEmpty() ? List.of() : List.of(makeTestCase("tc", data));
        CoverageCheckService service = buildService(List.of(), params, testCases);
        CoverageReport report = service.checkCoverage(TEMPLATE_ID);

        double expectedCoverage = (coveredCount * 100.0) / totalParams;
        expectedCoverage = Math.round(expectedCoverage * 100.0) / 100.0;
        assertEquals(expectedCoverage, report.getParameterCoverage(), 0.01);
        assertEquals(totalParams, report.getTotalParameters());
        assertEquals(coveredCount, report.getCoveredParameters());
    }

    /**
     * Property 2: Coverage is always in [0, 100].
     */
    @Property(tries = 200)
    @Label("Coverage percentages are always in [0, 100]")
    void coverageIsAlwaysInRange(
            @ForAll @IntRange(min = 0, max = 20) int numParams,
            @ForAll @IntRange(min = 0, max = 20) int coveredCount) {

        int actualCovered = Math.min(coveredCount, numParams);
        List<ParameterDefinition> params = IntStream.range(0, numParams)
                .mapToObj(i -> makeParam((long) (i + 1), "p_" + i)).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < actualCovered; i++) data.put("p_" + i, "v");
        List<TestCase> testCases = data.isEmpty() ? List.of() : List.of(makeTestCase("tc", data));

        CoverageCheckService service = buildService(List.of(), params, testCases);
        CoverageReport report = service.checkCoverage(TEMPLATE_ID);

        assertTrue(report.getParameterCoverage() >= 0.0);
        assertTrue(report.getParameterCoverage() <= 100.0);
        assertTrue(report.getOverallCoverage() >= 0.0);
        assertTrue(report.getOverallCoverage() <= 100.0);
    }


    private ParameterDefinition makeParam(Long id, String name) {
        ParameterDefinition p = new ParameterDefinition();
        p.setId(id);
        p.setTemplateId(TEMPLATE_ID);
        p.setName(name);
        p.setParameterType("REQUEST");
        p.setDataType("STRING");
        p.setSortOrder(id.intValue());
        return p;
    }

    private TestCase makeTestCase(String name, Map<String, Object> data) {
        TestCase tc = new TestCase();
        tc.setId((long) name.hashCode());
        tc.setTemplateId(TEMPLATE_ID);
        tc.setName(name);
        try { tc.setTestDataJson(MAPPER.writeValueAsString(data)); }
        catch (Exception e) { throw new RuntimeException(e); }
        return tc;
    }

    private CoverageCheckService buildService(List<PlaceholderInfo> placeholders,
                                               List<ParameterDefinition> params,
                                               List<TestCase> testCases) {
        TemplateRepository templateRepo = mock(TemplateRepository.class);
        TemplateScanService scanService = mock(TemplateScanService.class);
        ParameterRepository paramRepo = mock(ParameterRepository.class);
        TestCaseRepository testCaseRepo = mock(TestCaseRepository.class);

        Template template = new Template();
        template.setId(TEMPLATE_ID);
        template.setName("Test Template");
        template.setTenantId(1L);
        template.setTemplateFilePath("templates/1/test.docx");

        when(templateRepo.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(scanService.scanPlaceholders(anyString())).thenReturn(placeholders);
        when(paramRepo.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID)).thenReturn(params);
        when(testCaseRepo.findByTemplateIdOrderByCreatedAtDesc(TEMPLATE_ID)).thenReturn(testCases);

        return new CoverageCheckService(templateRepo, scanService, paramRepo, testCaseRepo, MAPPER,
                new com.docgen.service.TemplateCoverageAnalyzer());
    }
}
