package com.docgen.service;

import com.docgen.dto.CoverageReport;
import com.docgen.dto.PlaceholderInfo;
import com.docgen.dto.UncoveredItem;
import com.docgen.entity.ParameterDefinition;
import com.docgen.entity.Template;
import com.docgen.entity.TestCase;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TestCaseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service for computing three-dimensional template coverage:
 * Branch coverage, Loop coverage, and Parameter coverage.
 * <p>
 * Coverage is computed based on test case data against template placeholders and parameter definitions.
 */
@Service
public class CoverageCheckService {

    private static final Logger log = LoggerFactory.getLogger(CoverageCheckService.class);
    private static final double DEFAULT_THRESHOLD = 100.0;

    private final TemplateRepository templateRepository;
    private final TemplateScanService templateScanService;
    private final ParameterRepository parameterRepository;
    private final TestCaseRepository testCaseRepository;
    private final ObjectMapper objectMapper;
    private final TemplateCoverageAnalyzer coverageAnalyzer;

    public CoverageCheckService(TemplateRepository templateRepository,
                                TemplateScanService templateScanService,
                                ParameterRepository parameterRepository,
                                TestCaseRepository testCaseRepository,
                                ObjectMapper objectMapper,
                                TemplateCoverageAnalyzer coverageAnalyzer) {
        this.templateRepository = templateRepository;
        this.templateScanService = templateScanService;
        this.parameterRepository = parameterRepository;
        this.testCaseRepository = testCaseRepository;
        this.objectMapper = objectMapper;
        this.coverageAnalyzer = coverageAnalyzer;
    }

    @Transactional(readOnly = true)
    public CoverageReport checkCoverage(Long templateId) {
        return checkCoverage(templateId, DEFAULT_THRESHOLD);
    }

    @Transactional(readOnly = true)
    public CoverageReport checkCoverage(Long templateId, double threshold) {
        Template template = findTemplateOrThrow(templateId);
        List<String> warnings = new ArrayList<>();

        // Scan template placeholders
        List<PlaceholderInfo> placeholders;
        String filePath = template.getTemplateFilePath();
        if (filePath == null || filePath.isBlank()) {
            placeholders = List.of();
            warnings.add("模板尚未上传文件，跳过占位符扫描");
        } else {
            try {
                placeholders = templateScanService.scanPlaceholders(filePath);
            } catch (Exception e) {
                log.warn("Failed to scan template {} placeholders: {}", templateId, e.getMessage());
                placeholders = List.of();
                warnings.add("模板扫描失败，覆盖率可能不准确: " + e.getMessage());
            }
        }

        // Extract conditions and loops from placeholders
        List<PlaceholderInfo> conditions = new ArrayList<>();
        List<PlaceholderInfo> loops = new ArrayList<>();
        coverageAnalyzer.collectConditionsAndLoops(placeholders, conditions, loops);

        // Get parameter definitions and test cases
        List<ParameterDefinition> params = parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
        List<TestCase> testCases = testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(templateId);

        // Parse test case data
        List<Map<String, Object>> testDataList = parseTestCaseData(testCases, warnings);

        TemplateCoverageAnalyzer.CoverageAnalysisResult analysis =
                coverageAnalyzer.analyze(conditions, loops, params, testDataList);
        double branchCov = analysis.getBranchCoverage();
        double loopCov = analysis.getLoopCoverage();
        double paramCov = analysis.getParameterCoverage();
        double overall = analysis.getOverallCoverage();
        List<UncoveredItem> uncoveredItems = analysis.getUncoveredItems();

        // Build report
        CoverageReport report = new CoverageReport();
        report.setTemplateId(templateId);
        report.setTemplateName(template.getName());
        report.setBranchCoverage(round(branchCov));
        report.setLoopCoverage(round(loopCov));
        report.setParameterCoverage(round(paramCov));
        report.setOverallCoverage(round(overall));
        report.setTotalBranches(analysis.getTotalBranches());
        report.setCoveredBranches(analysis.getCoveredBranches());
        report.setTotalLoopScenarios(analysis.getTotalLoopScenarios());
        report.setCoveredLoopScenarios(analysis.getCoveredLoopScenarios());
        report.setTotalParameters(analysis.getTotalParameters());
        report.setCoveredParameters(analysis.getCoveredParameters());
        report.setUncoveredItems(uncoveredItems);
        report.setBelowThreshold(overall < threshold);
        report.setThreshold(threshold);
        report.setCheckedAt(Instant.now());
        report.setWarnings(warnings);

        log.info("Coverage check for template {}: branch={}%, loop={}%, param={}%, overall={}%, threshold={}%",
                templateId, report.getBranchCoverage(), report.getLoopCoverage(),
                report.getParameterCoverage(), report.getOverallCoverage(), threshold);

        return report;
    }

    /**
     * Branch coverage: delegates to {@link TemplateCoverageAnalyzer} for property tests and tooling.
     */
    public double computeBranchCoverage(List<PlaceholderInfo> conditions, List<Map<String, Object>> testDataList) {
        return coverageAnalyzer.computeBranchCoverage(conditions, testDataList);
    }

    public double computeLoopCoverage(List<PlaceholderInfo> loops, List<Map<String, Object>> testDataList) {
        return coverageAnalyzer.computeLoopCoverage(loops, testDataList);
    }

    public double computeParameterCoverage(List<ParameterDefinition> params, List<Map<String, Object>> testDataList) {
        return coverageAnalyzer.computeParameterCoverage(params, testDataList);
    }

    double computeOverallCoverage(double branchCov, double loopCov, double paramCov,
                                   int conditionCount, int loopCount, int paramCount) {
        return coverageAnalyzer.computeOverallCoverage(branchCov, loopCov, paramCov,
                conditionCount, loopCount, paramCount);
    }


    private List<Map<String, Object>> parseTestCaseData(List<TestCase> testCases, List<String> warnings) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (TestCase tc : testCases) {
            try {
                if (tc.getTestDataJson() != null && !tc.getTestDataJson().isBlank()) {
                    Map<String, Object> data = objectMapper.readValue(
                            tc.getTestDataJson(), new TypeReference<>() {});
                    result.add(data);
                }
            } catch (Exception e) {
                warnings.add("测试用例 '" + tc.getName() + "' 数据解析失败: " + e.getMessage());
            }
        }
        return result;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Template findTemplateOrThrow(Long templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在"));
    }

    /**
     * Check if coverage is below threshold — used during template activation to warn.
     */
    @Transactional(readOnly = true)
    public boolean isBelowThreshold(Long templateId, double threshold) {
        CoverageReport report = checkCoverage(templateId, threshold);
        return report.isBelowThreshold();
    }
}
