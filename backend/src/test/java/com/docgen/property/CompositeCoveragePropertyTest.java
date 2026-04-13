package com.docgen.property;

import com.docgen.dto.CompositeCoverageReport;
import com.docgen.dto.CompositeCoverageReport.SegmentCoverageEntry;
import com.docgen.dto.SegmentDTO;
import com.docgen.dto.SegmentVariableDTO;
import com.docgen.service.CompositeCoverageService;
import com.docgen.service.DependencyGraphService;
import com.docgen.service.SegmentVariableService;
import net.jqwik.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for CompositeCoverageService — Property 10: Coverage Calculation Correctness.
 *
 * <p><b>Validates: Requirements 6.3</b></p>
 *
 * <p>Verifies that the aggregate coverage of a Composite_Template equals
 * Σ(boundVars) / Σ(totalVars) × 100% across all its Segments.</p>
 */
@Tag("Feature: template-segmentation, Property 10: compositeCoverageCalculation")
class CompositeCoveragePropertyTest {

    /**
     * Property 10: compositeCoverageCalculation
     *
     * For any composite template with multiple segments, each having a random number
     * of total variables and bound variables, the overall coverage must equal
     * Σ(boundVars) / Σ(totalVars) × 100%.
     */
    @Property(tries = 100)
    void compositeCoverageCalculation(
            @ForAll("segmentVariableConfigs") List<SegmentVarConfig> configs
    ) {
        // Arrange: mock dependencies
        SegmentVariableService segmentVariableService = mock(SegmentVariableService.class);
        DependencyGraphService dependencyGraphService = mock(DependencyGraphService.class);
        CompositeCoverageService service = new CompositeCoverageService(
                segmentVariableService, dependencyGraphService);

        Long compositeTemplateId = 1L;

        // Build segment DTOs and mock variable responses
        List<SegmentDTO> segmentDTOs = new ArrayList<>();
        int expectedTotalVars = 0;
        int expectedBoundVars = 0;

        for (int i = 0; i < configs.size(); i++) {
            SegmentVarConfig cfg = configs.get(i);
            Long segmentId = (long) (i + 1);

            SegmentDTO dto = new SegmentDTO();
            dto.setId(segmentId);
            dto.setName("Segment-" + segmentId);
            segmentDTOs.add(dto);

            // Build variable list: first 'boundCount' are bound (required=true), rest are unbound
            List<SegmentVariableDTO> variables = new ArrayList<>();
            for (int j = 0; j < cfg.totalVars; j++) {
                boolean isBound = j < cfg.boundVars;
                variables.add(new SegmentVariableDTO("var_" + j, "STRING", isBound, null));
            }

            when(segmentVariableService.scanVariables(segmentId)).thenReturn(variables);

            expectedTotalVars += cfg.totalVars;
            expectedBoundVars += cfg.boundVars;
        }

        when(dependencyGraphService.getSegmentsForTemplate(compositeTemplateId)).thenReturn(segmentDTOs);

        // Act
        CompositeCoverageReport report = service.checkCoverage(compositeTemplateId);

        // Assert: overall coverage = Σ(boundVars) / Σ(totalVars) × 100%
        double expectedOverall = expectedTotalVars == 0
                ? 100.0
                : Math.round((expectedBoundVars * 100.0 / expectedTotalVars) * 100.0) / 100.0;

        assertEquals(expectedOverall, report.getOverallCoveragePercent(), 0.01,
                "Overall coverage must equal Σ(boundVars)/Σ(totalVars)×100%");

        // Assert: per-segment coverage
        assertEquals(configs.size(), report.getSegmentCoverages().size(),
                "Report must contain one entry per segment");

        for (int i = 0; i < configs.size(); i++) {
            SegmentVarConfig cfg = configs.get(i);
            SegmentCoverageEntry entry = report.getSegmentCoverages().get(i);

            assertEquals(cfg.totalVars, entry.getTotalVariables());
            assertEquals(cfg.boundVars, entry.getBoundVariables());

            double expectedSegCoverage = cfg.totalVars == 0
                    ? 100.0
                    : Math.round((cfg.boundVars * 100.0 / cfg.totalVars) * 100.0) / 100.0;
            assertEquals(expectedSegCoverage, entry.getCoveragePercent(), 0.01,
                    "Per-segment coverage must be correct for segment " + (i + 1));
        }
    }

    /**
     * Property 10: empty composite template (no segments) should report 100% coverage.
     */
    @Property(tries = 20)
    void emptyCompositeTemplateShouldReport100PercentCoverage(
            @ForAll("compositeTemplateIds") Long compositeTemplateId
    ) {
        SegmentVariableService segmentVariableService = mock(SegmentVariableService.class);
        DependencyGraphService dependencyGraphService = mock(DependencyGraphService.class);
        CompositeCoverageService service = new CompositeCoverageService(
                segmentVariableService, dependencyGraphService);

        when(dependencyGraphService.getSegmentsForTemplate(compositeTemplateId))
                .thenReturn(List.of());

        CompositeCoverageReport report = service.checkCoverage(compositeTemplateId);

        assertEquals(100.0, report.getOverallCoveragePercent(), 0.01,
                "Empty composite template should have 100% coverage");
        assertTrue(report.getSegmentCoverages().isEmpty());
    }

    /**
     * Property 10: isBelowThreshold should be consistent with checkCoverage result.
     */
    @Property(tries = 100)
    void isBelowThresholdConsistentWithCoverage(
            @ForAll("segmentVariableConfigs") List<SegmentVarConfig> configs,
            @ForAll("thresholds") double threshold
    ) {
        SegmentVariableService segmentVariableService = mock(SegmentVariableService.class);
        DependencyGraphService dependencyGraphService = mock(DependencyGraphService.class);
        CompositeCoverageService service = new CompositeCoverageService(
                segmentVariableService, dependencyGraphService);

        Long compositeTemplateId = 1L;

        List<SegmentDTO> segmentDTOs = new ArrayList<>();
        for (int i = 0; i < configs.size(); i++) {
            SegmentVarConfig cfg = configs.get(i);
            Long segmentId = (long) (i + 1);

            SegmentDTO dto = new SegmentDTO();
            dto.setId(segmentId);
            dto.setName("Segment-" + segmentId);
            segmentDTOs.add(dto);

            List<SegmentVariableDTO> variables = new ArrayList<>();
            for (int j = 0; j < cfg.totalVars; j++) {
                boolean isBound = j < cfg.boundVars;
                variables.add(new SegmentVariableDTO("var_" + j, "STRING", isBound, null));
            }
            when(segmentVariableService.scanVariables(segmentId)).thenReturn(variables);
        }

        when(dependencyGraphService.getSegmentsForTemplate(compositeTemplateId)).thenReturn(segmentDTOs);

        boolean belowThreshold = service.isBelowThreshold(compositeTemplateId, threshold);
        CompositeCoverageReport report = service.checkCoverage(compositeTemplateId);

        assertEquals(report.getOverallCoveragePercent() < threshold, belowThreshold,
                "isBelowThreshold must be consistent with checkCoverage result");
    }

    // ── Helper types ──

    static class SegmentVarConfig {
        final int totalVars;
        final int boundVars;

        SegmentVarConfig(int totalVars, int boundVars) {
            this.totalVars = totalVars;
            this.boundVars = boundVars;
        }

        @Override
        public String toString() {
            return "SegmentVarConfig{total=" + totalVars + ", bound=" + boundVars + "}";
        }
    }

    // ── Generators ──

    /**
     * Generates a list of 1-8 segment variable configurations.
     * Each config has 0-20 total variables and 0-totalVars bound variables.
     */
    @Provide
    Arbitrary<List<SegmentVarConfig>> segmentVariableConfigs() {
        Arbitrary<SegmentVarConfig> singleConfig = Arbitraries.integers().between(0, 20)
                .flatMap(totalVars ->
                        Arbitraries.integers().between(0, totalVars)
                                .map(boundVars -> new SegmentVarConfig(totalVars, boundVars))
                );

        return singleConfig.list().ofMinSize(1).ofMaxSize(8);
    }

    @Provide
    Arbitrary<Long> compositeTemplateIds() {
        return Arbitraries.longs().between(1L, 1000L);
    }

    @Provide
    Arbitrary<Double> thresholds() {
        return Arbitraries.doubles().between(0.0, 100.0);
    }
}
