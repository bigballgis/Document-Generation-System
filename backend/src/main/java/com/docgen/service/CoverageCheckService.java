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

    public CoverageCheckService(TemplateRepository templateRepository,
                                TemplateScanService templateScanService,
                                ParameterRepository parameterRepository,
                                TestCaseRepository testCaseRepository,
                                ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.templateScanService = templateScanService;
        this.parameterRepository = parameterRepository;
        this.testCaseRepository = testCaseRepository;
        this.objectMapper = objectMapper;
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
        collectConditionsAndLoops(placeholders, conditions, loops);

        // Get parameter definitions and test cases
        List<ParameterDefinition> params = parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
        List<TestCase> testCases = testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(templateId);

        // Parse test case data
        List<Map<String, Object>> testDataList = parseTestCaseData(testCases, warnings);

        // Compute three dimensions
        double branchCov = computeBranchCoverage(conditions, testDataList);
        double loopCov = computeLoopCoverage(loops, testDataList);
        double paramCov = computeParameterCoverage(params, testDataList);
        double overall = computeOverallCoverage(branchCov, loopCov, paramCov,
                conditions.size(), loops.size(), params.size());

        // Find uncovered items
        List<UncoveredItem> uncoveredItems = findUncoveredItems(conditions, loops, params, testDataList);

        // Build report
        CoverageReport report = new CoverageReport();
        report.setTemplateId(templateId);
        report.setTemplateName(template.getName());
        report.setBranchCoverage(round(branchCov));
        report.setLoopCoverage(round(loopCov));
        report.setParameterCoverage(round(paramCov));
        report.setOverallCoverage(round(overall));
        report.setTotalBranches(conditions.size() * 2);
        report.setCoveredBranches(countCoveredBranchScenarios(conditions, testDataList));
        report.setTotalLoopScenarios(loops.size() * 2);
        report.setCoveredLoopScenarios(countCoveredLoopScenarios(loops, testDataList));
        report.setTotalParameters(params.size());
        report.setCoveredParameters(countCoveredParameters(params, testDataList));
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
     * Branch coverage: (branches triggered in both true and false paths) / (total branches × 2) × 100%.
     * Each condition has 2 scenarios: true path and false path.
     */
    public double computeBranchCoverage(List<PlaceholderInfo> conditions, List<Map<String, Object>> testDataList) {
        if (conditions.isEmpty()) {
            return 0.0;
        }
        int totalScenarios = conditions.size() * 2;
        int covered = countCoveredBranchScenarios(conditions, testDataList);
        return (covered * 100.0) / totalScenarios;
    }

    /**
     * Loop coverage: (loops exercised with both empty and non-empty arrays) / (total loops × 2) × 100%.
     * Each loop has 2 scenarios: empty array and non-empty array.
     */
    public double computeLoopCoverage(List<PlaceholderInfo> loops, List<Map<String, Object>> testDataList) {
        if (loops.isEmpty()) {
            return 0.0;
        }
        int totalScenarios = loops.size() * 2;
        int covered = countCoveredLoopScenarios(loops, testDataList);
        return (covered * 100.0) / totalScenarios;
    }

    /**
     * Parameter coverage: (parameters receiving non-null value in at least one test case) / total parameters × 100%.
     */
    public double computeParameterCoverage(List<ParameterDefinition> params, List<Map<String, Object>> testDataList) {
        if (params.isEmpty()) {
            return 0.0;
        }
        int covered = countCoveredParameters(params, testDataList);
        return (covered * 100.0) / params.size();
    }

    /**
     * Overall coverage: equal-weighted average of applicable dimensions.
     * Dimensions with zero items are excluded from the average.
     */
    double computeOverallCoverage(double branchCov, double loopCov, double paramCov,
                                   int conditionCount, int loopCount, int paramCount) {
        double sum = 0.0;
        int dimensions = 0;

        if (conditionCount > 0) {
            sum += branchCov;
            dimensions++;
        }
        if (loopCount > 0) {
            sum += loopCov;
            dimensions++;
        }
        if (paramCount > 0) {
            sum += paramCov;
            dimensions++;
        }

        return dimensions == 0 ? 100.0 : sum / dimensions;
    }

    // ── Private helpers ──

    private void collectConditionsAndLoops(List<PlaceholderInfo> placeholders,
                                           List<PlaceholderInfo> conditions,
                                           List<PlaceholderInfo> loops) {
        for (PlaceholderInfo ph : placeholders) {
            if ("CONDITION".equals(ph.type())) {
                conditions.add(ph);
            } else if ("LOOP".equals(ph.type())) {
                loops.add(ph);
            }
            // Recurse into children
            if (ph.children() != null && !ph.children().isEmpty()) {
                collectConditionsAndLoops(ph.children(), conditions, loops);
            }
        }
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

    private int countCoveredBranchScenarios(List<PlaceholderInfo> conditions, List<Map<String, Object>> testDataList) {
        int covered = 0;
        for (PlaceholderInfo cond : conditions) {
            boolean seenTrue = false;
            boolean seenFalse = false;
            for (Map<String, Object> data : testDataList) {
                Object val = resolveValue(data, cond.name());
                if (isTruthy(val)) {
                    seenTrue = true;
                } else {
                    seenFalse = true;
                }
                if (seenTrue && seenFalse) break;
            }
            if (seenTrue) covered++;
            if (seenFalse) covered++;
        }
        return covered;
    }

    private int countCoveredLoopScenarios(List<PlaceholderInfo> loops, List<Map<String, Object>> testDataList) {
        int covered = 0;
        for (PlaceholderInfo loop : loops) {
            boolean seenEmpty = false;
            boolean seenNonEmpty = false;
            for (Map<String, Object> data : testDataList) {
                Object val = resolveValue(data, loop.name());
                if (val instanceof List<?> list) {
                    if (list.isEmpty()) {
                        seenEmpty = true;
                    } else {
                        seenNonEmpty = true;
                    }
                } else if (val == null) {
                    seenEmpty = true;
                }
                if (seenEmpty && seenNonEmpty) break;
            }
            if (seenEmpty) covered++;
            if (seenNonEmpty) covered++;
        }
        return covered;
    }

    private int countCoveredParameters(List<ParameterDefinition> params, List<Map<String, Object>> testDataList) {
        int covered = 0;
        for (ParameterDefinition param : params) {
            if ("DERIVED".equals(param.getParameterType())) {
                covered++;
                continue;
            }
            boolean hasNonNull = false;
            for (Map<String, Object> data : testDataList) {
                Object val = resolveValue(data, param.getName());
                if (val != null) {
                    hasNonNull = true;
                    break;
                }
            }
            if (hasNonNull) covered++;
        }
        return covered;
    }

    private List<UncoveredItem> findUncoveredItems(List<PlaceholderInfo> conditions,
                                                    List<PlaceholderInfo> loops,
                                                    List<ParameterDefinition> params,
                                                    List<Map<String, Object>> testDataList) {
        List<UncoveredItem> items = new ArrayList<>();

        for (PlaceholderInfo cond : conditions) {
            boolean seenTrue = false;
            boolean seenFalse = false;
            for (Map<String, Object> data : testDataList) {
                Object val = resolveValue(data, cond.name());
                if (isTruthy(val)) seenTrue = true;
                else seenFalse = true;
            }
            if (!seenTrue) items.add(new UncoveredItem("BRANCH", cond.name(), "true"));
            if (!seenFalse) items.add(new UncoveredItem("BRANCH", cond.name(), "false"));
        }

        for (PlaceholderInfo loop : loops) {
            boolean seenEmpty = false;
            boolean seenNonEmpty = false;
            for (Map<String, Object> data : testDataList) {
                Object val = resolveValue(data, loop.name());
                if (val instanceof List<?> list) {
                    if (list.isEmpty()) seenEmpty = true;
                    else seenNonEmpty = true;
                } else if (val == null) {
                    seenEmpty = true;
                }
            }
            if (!seenEmpty) items.add(new UncoveredItem("LOOP", loop.name(), "empty"));
            if (!seenNonEmpty) items.add(new UncoveredItem("LOOP", loop.name(), "non-empty"));
        }

        for (ParameterDefinition param : params) {
            if ("DERIVED".equals(param.getParameterType())) continue;
            boolean hasNonNull = false;
            for (Map<String, Object> data : testDataList) {
                Object val = resolveValue(data, param.getName());
                if (val != null) { hasNonNull = true; break; }
            }
            if (!hasNonNull) items.add(new UncoveredItem("PARAMETER", param.getName(), "null"));
        }

        return items;
    }

    private Object resolveValue(Map<String, Object> data, String name) {
        if (data == null) return null;
        // Try direct lookup first
        if (data.containsKey(name)) return data.get(name);
        // Try dot-notation path
        String[] segments = name.split("\\.");
        Object current = data;
        for (String seg : segments) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(seg);
                if (current == null) return null;
            } else {
                return null;
            }
        }
        return current;
    }

    private boolean isTruthy(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.doubleValue() != 0;
        if (val instanceof String s) return !s.isEmpty();
        if (val instanceof Collection<?> c) return !c.isEmpty();
        return true;
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
