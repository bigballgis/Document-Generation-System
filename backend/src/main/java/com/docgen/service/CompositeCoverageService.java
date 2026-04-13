package com.docgen.service;

import com.docgen.dto.CompositeCoverageReport;
import com.docgen.dto.CompositeCoverageReport.SegmentCoverageEntry;
import com.docgen.dto.SegmentDTO;
import com.docgen.dto.SegmentVariableDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for computing aggregate coverage across all Segments of a Composite_Template.
 * Reuses CoverageCheckService threshold logic and SegmentVariableService for variable scanning.
 */
@Service
public class CompositeCoverageService {

    private static final Logger log = LoggerFactory.getLogger(CompositeCoverageService.class);
    private static final double DEFAULT_THRESHOLD = 100.0;

    private final SegmentVariableService segmentVariableService;
    private final DependencyGraphService dependencyGraphService;

    public CompositeCoverageService(SegmentVariableService segmentVariableService,
                                    DependencyGraphService dependencyGraphService) {
        this.segmentVariableService = segmentVariableService;
        this.dependencyGraphService = dependencyGraphService;
    }

    /**
     * Check coverage for a Composite_Template by aggregating variable coverage
     * across all its Segments.
     *
     * @param compositeTemplateId the composite template to check
     * @return coverage report with per-segment and overall coverage
     */
    @Transactional(readOnly = true)
    public CompositeCoverageReport checkCoverage(Long compositeTemplateId) {
        List<SegmentDTO> segments = dependencyGraphService.getSegmentsForTemplate(compositeTemplateId);

        List<SegmentCoverageEntry> segmentCoverages = new ArrayList<>();
        int totalVarsAll = 0;
        int boundVarsAll = 0;

        for (SegmentDTO segment : segments) {
            List<SegmentVariableDTO> variables = segmentVariableService.scanVariables(segment.getId());

            int totalVars = variables.size();
            int boundVars = (int) variables.stream().filter(SegmentVariableDTO::isRequired).count();

            double segCoverage = totalVars == 0 ? 100.0 : (boundVars * 100.0) / totalVars;

            SegmentCoverageEntry entry = new SegmentCoverageEntry();
            entry.setSegmentId(segment.getId());
            entry.setSegmentName(segment.getName());
            entry.setTotalVariables(totalVars);
            entry.setBoundVariables(boundVars);
            entry.setCoveragePercent(Math.round(segCoverage * 100.0) / 100.0);

            segmentCoverages.add(entry);
            totalVarsAll += totalVars;
            boundVarsAll += boundVars;
        }

        double overallCoverage = totalVarsAll == 0 ? 100.0 : (boundVarsAll * 100.0) / totalVarsAll;

        CompositeCoverageReport report = new CompositeCoverageReport();
        report.setOverallCoveragePercent(Math.round(overallCoverage * 100.0) / 100.0);
        report.setSegmentCoverages(segmentCoverages);

        log.info("Composite coverage for template {}: {}/{} bound ({}%), segments={}",
                compositeTemplateId, boundVarsAll, totalVarsAll,
                report.getOverallCoveragePercent(), segments.size());

        return report;
    }

    /**
     * Check if the composite template's overall coverage is below the given threshold.
     * Reuses the same threshold logic as CoverageCheckService.
     *
     * @param compositeTemplateId the composite template to check
     * @param threshold coverage threshold percentage (0-100)
     * @return true if coverage is below threshold
     */
    @Transactional(readOnly = true)
    public boolean isBelowThreshold(Long compositeTemplateId, double threshold) {
        CompositeCoverageReport report = checkCoverage(compositeTemplateId);
        return report.getOverallCoveragePercent() < threshold;
    }
}
