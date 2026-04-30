package com.docgen.service;

import com.docgen.dto.CoverageReport;
import com.docgen.dto.PlaceholderInfo;
import com.docgen.entity.ParameterDefinition;
import com.docgen.entity.Template;
import com.docgen.entity.TestCase;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TestCaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for {@link CoverageCheckService}.
 *
 * <p><b>Validates: Requirements 8.2, 8.3, 8.4, 8.5, 8.7, 8.9</b></p>
 */
@Tag("feature-template-parameter-redesign")
class CoverageCheckServicePropertyTest {

    private static final Long TEMPLATE_ID = 1L;
    private static final ObjectMapper MAPPER = new ObjectMapper();


    private CoverageCheckService createService(List<PlaceholderInfo> placeholders,
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
                new TemplateCoverageAnalyzer());
    }

    private PlaceholderInfo condition(String name) {
        return new PlaceholderInfo(name, name, "CONDITION", List.of(name), List.of());
    }

    private PlaceholderInfo loop(String name) {
        return new PlaceholderInfo(name, name, "LOOP", List.of(name), List.of());
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
        try {
            tc.setTestDataJson(MAPPER.writeValueAsString(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return tc;
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 27: Branch coverage computation
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 27: branchCoverage = (covered branch scenarios) / (total conditions × 2) × 100%.
     * Each condition has true/false paths. Test cases that provide truthy/falsy values cover them.
     *
     * <p><b>Validates: Requirements 8.2</b></p>
     */
    @Property(tries = 100)
    @Tag("property-27-branch-coverage-computation")
    void branchCoverage_formulaIsCorrect(
            @ForAll @IntRange(min = 1, max = 5) int numConditions,
            @ForAll @IntRange(min = 0, max = 2) int coverageLevel // 0=none, 1=true-only, 2=both
    ) {
        List<PlaceholderInfo> conditions = IntStream.range(0, numConditions)
                .mapToObj(i -> condition("cond_" + i))
                .toList();

        List<TestCase> testCases = new ArrayList<>();
        if (coverageLevel >= 1) {
            // Add test case with all conditions truthy
            Map<String, Object> trueData = new LinkedHashMap<>();
            for (int i = 0; i < numConditions; i++) {
                trueData.put("cond_" + i, true);
            }
            testCases.add(makeTestCase("true_case", trueData));
        }
        if (coverageLevel >= 2) {
            // Add test case with all conditions falsy
            Map<String, Object> falseData = new LinkedHashMap<>();
            for (int i = 0; i < numConditions; i++) {
                falseData.put("cond_" + i, false);
            }
            testCases.add(makeTestCase("false_case", falseData));
        }

        CoverageCheckService svc = createService(conditions, List.of(), testCases);
        CoverageReport report = svc.checkCoverage(TEMPLATE_ID);

        int totalScenarios = numConditions * 2;
        int expectedCovered = coverageLevel * numConditions; // 0, N, or 2N
        double expectedCoverage = (expectedCovered * 100.0) / totalScenarios;

        assertEquals(totalScenarios, report.getTotalBranches());
        assertEquals(expectedCovered, report.getCoveredBranches());
        assertEquals(Math.round(expectedCoverage * 100.0) / 100.0, report.getBranchCoverage(), 0.01);
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 28: Loop coverage computation
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 28: loopCoverage = (covered loop scenarios) / (total loops × 2) × 100%.
     * Each loop has empty/non-empty scenarios.
     *
     * <p><b>Validates: Requirements 8.3</b></p>
     */
    @Property(tries = 100)
    @Tag("property-28-loop-coverage-computation")
    void loopCoverage_formulaIsCorrect(
            @ForAll @IntRange(min = 1, max = 5) int numLoops,
            @ForAll @IntRange(min = 0, max = 2) int coverageLevel // 0=none, 1=non-empty-only, 2=both
    ) {
        List<PlaceholderInfo> loops = IntStream.range(0, numLoops)
                .mapToObj(i -> loop("loop_" + i))
                .toList();

        List<TestCase> testCases = new ArrayList<>();
        if (coverageLevel >= 1) {
            Map<String, Object> nonEmptyData = new LinkedHashMap<>();
            for (int i = 0; i < numLoops; i++) {
                nonEmptyData.put("loop_" + i, List.of("item"));
            }
            testCases.add(makeTestCase("non_empty", nonEmptyData));
        }
        if (coverageLevel >= 2) {
            Map<String, Object> emptyData = new LinkedHashMap<>();
            for (int i = 0; i < numLoops; i++) {
                emptyData.put("loop_" + i, List.of());
            }
            testCases.add(makeTestCase("empty", emptyData));
        }

        CoverageCheckService svc = createService(loops, List.of(), testCases);
        CoverageReport report = svc.checkCoverage(TEMPLATE_ID);

        int totalScenarios = numLoops * 2;
        int expectedCovered = coverageLevel * numLoops;
        double expectedCoverage = (expectedCovered * 100.0) / totalScenarios;

        assertEquals(totalScenarios, report.getTotalLoopScenarios());
        assertEquals(expectedCovered, report.getCoveredLoopScenarios());
        assertEquals(Math.round(expectedCoverage * 100.0) / 100.0, report.getLoopCoverage(), 0.01);
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 29: Parameter coverage computation
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 29: parameterCoverage = (params with non-null value in at least one test case) / total params × 100%.
     *
     * <p><b>Validates: Requirements 8.4</b></p>
     */
    @Property(tries = 100)
    @Tag("property-29-parameter-coverage-computation")
    void parameterCoverage_formulaIsCorrect(
            @ForAll @IntRange(min = 1, max = 6) int totalParams,
            @ForAll @IntRange(min = 0, max = 6) int coveredCount
    ) {
        int actualCovered = Math.min(coveredCount, totalParams);
        List<ParameterDefinition> params = IntStream.range(0, totalParams)
                .mapToObj(i -> makeParam((long) (i + 1), "param_" + i))
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < actualCovered; i++) {
            data.put("param_" + i, "value_" + i);
        }
        List<TestCase> testCases = data.isEmpty() ? List.of() : List.of(makeTestCase("tc1", data));

        CoverageCheckService svc = createService(List.of(), params, testCases);
        CoverageReport report = svc.checkCoverage(TEMPLATE_ID);

        double expectedCoverage = (actualCovered * 100.0) / totalParams;
        assertEquals(totalParams, report.getTotalParameters());
        assertEquals(actualCovered, report.getCoveredParameters());
        assertEquals(Math.round(expectedCoverage * 100.0) / 100.0, report.getParameterCoverage(), 0.01);
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 30: Overall coverage weighted average
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 30: overallCoverage = equal-weighted average of applicable dimensions.
     * Dimensions with zero items are excluded.
     *
     * <p><b>Validates: Requirements 8.5, 8.7</b></p>
     */
    @Property(tries = 100)
    @Tag("property-30-overall-coverage-weighted-average")
    void overallCoverage_weightedAverage_excludesZeroItemDimensions(
            @ForAll @IntRange(min = 0, max = 3) int numConditions,
            @ForAll @IntRange(min = 0, max = 3) int numLoops,
            @ForAll @IntRange(min = 0, max = 3) int numParams
    ) {
        List<PlaceholderInfo> conditions = IntStream.range(0, numConditions)
                .mapToObj(i -> condition("cond_" + i)).toList();
        List<PlaceholderInfo> loops = IntStream.range(0, numLoops)
                .mapToObj(i -> loop("loop_" + i)).toList();
        List<PlaceholderInfo> placeholders = new ArrayList<>();
        placeholders.addAll(conditions);
        placeholders.addAll(loops);

        List<ParameterDefinition> params = IntStream.range(0, numParams)
                .mapToObj(i -> makeParam((long) (i + 1), "p_" + i)).toList();

        // Provide full coverage for all dimensions
        Map<String, Object> trueData = new LinkedHashMap<>();
        Map<String, Object> falseData = new LinkedHashMap<>();
        for (int i = 0; i < numConditions; i++) {
            trueData.put("cond_" + i, true);
            falseData.put("cond_" + i, false);
        }
        for (int i = 0; i < numLoops; i++) {
            trueData.put("loop_" + i, List.of("item"));
            falseData.put("loop_" + i, List.of());
        }
        for (int i = 0; i < numParams; i++) {
            trueData.put("p_" + i, "val");
        }

        List<TestCase> testCases = new ArrayList<>();
        if (!trueData.isEmpty()) testCases.add(makeTestCase("true_case", trueData));
        if (!falseData.isEmpty()) testCases.add(makeTestCase("false_case", falseData));

        CoverageCheckService svc = createService(placeholders, params, testCases);
        CoverageReport report = svc.checkCoverage(TEMPLATE_ID);

        // When all dimensions have full coverage, overall should be 100%
        // When all dimensions are zero, overall should be 100% (no applicable dimensions)
        int dimensions = 0;
        if (numConditions > 0) dimensions++;
        if (numLoops > 0) dimensions++;
        if (numParams > 0) dimensions++;

        if (dimensions == 0) {
            assertEquals(100.0, report.getOverallCoverage(), 0.01,
                    "No applicable dimensions → 100% overall");
        } else {
            // All dimensions have full coverage → 100%
            assertEquals(100.0, report.getOverallCoverage(), 0.01,
                    "Full coverage in all dimensions → 100% overall");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 31: Coverage threshold
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 31: belowThreshold = true iff overallCoverage < threshold.
     *
     * <p><b>Validates: Requirements 8.9</b></p>
     */
    @Property(tries = 100)
    @Tag("property-31-coverage-threshold")
    void belowThreshold_correctLogic(
            @ForAll @IntRange(min = 0, max = 5) int numParams,
            @ForAll @IntRange(min = 0, max = 5) int coveredCount,
            @ForAll @IntRange(min = 0, max = 100) int thresholdInt
    ) {
        int actualCovered = Math.min(coveredCount, numParams);
        double threshold = thresholdInt;

        List<ParameterDefinition> params = IntStream.range(0, numParams)
                .mapToObj(i -> makeParam((long) (i + 1), "p_" + i)).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < actualCovered; i++) {
            data.put("p_" + i, "val");
        }
        List<TestCase> testCases = data.isEmpty() ? List.of() : List.of(makeTestCase("tc", data));

        CoverageCheckService svc = createService(List.of(), params, testCases);
        CoverageReport report = svc.checkCoverage(TEMPLATE_ID, threshold);

        // overallCoverage is parameterCoverage (only dimension) or 100% if no params
        double expectedOverall;
        if (numParams == 0) {
            expectedOverall = 100.0;
        } else {
            expectedOverall = Math.round((actualCovered * 100.0 / numParams) * 100.0) / 100.0;
        }

        assertEquals(expectedOverall < threshold, report.isBelowThreshold(),
                "belowThreshold should be true iff overall(" + expectedOverall + ") < threshold(" + threshold + ")");
        assertEquals(threshold, report.getThreshold());
    }
}
