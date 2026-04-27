package com.docgen.service;

import com.docgen.dto.PlaceholderInfo;
import com.docgen.dto.UncoveredItem;
import com.docgen.dto.readiness.MissingBusinessSituationDTO;
import com.docgen.dto.readiness.ScenarioReadinessCaseDTO;
import com.docgen.dto.readiness.ScenarioReadinessReportDTO;
import com.docgen.dto.readiness.ScenarioReadinessSummaryDTO;
import com.docgen.dto.readiness.ScenarioSuggestionDTO;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds business-facing scenario readiness for the validation workspace.
 * Single-file ({@code SINGLE}) templates use {@link TemplateCoverageAnalyzer};
 * composite templates return aggregate variable readiness with documented limitations.
 */
@Service
public class ScenarioReadinessService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioReadinessService.class);
    private static final int MAX_SUGGESTIONS = 12;

    private final TemplateRepository templateRepository;
    private final TemplateScanService templateScanService;
    private final ParameterRepository parameterRepository;
    private final TestCaseRepository testCaseRepository;
    private final ObjectMapper objectMapper;
    private final TemplateCoverageAnalyzer coverageAnalyzer;
    private final CompositeCoverageService compositeCoverageService;

    public ScenarioReadinessService(TemplateRepository templateRepository,
                                    TemplateScanService templateScanService,
                                    ParameterRepository parameterRepository,
                                    TestCaseRepository testCaseRepository,
                                    ObjectMapper objectMapper,
                                    TemplateCoverageAnalyzer coverageAnalyzer,
                                    CompositeCoverageService compositeCoverageService) {
        this.templateRepository = templateRepository;
        this.templateScanService = templateScanService;
        this.parameterRepository = parameterRepository;
        this.testCaseRepository = testCaseRepository;
        this.objectMapper = objectMapper;
        this.coverageAnalyzer = coverageAnalyzer;
        this.compositeCoverageService = compositeCoverageService;
    }

    @Transactional(readOnly = true)
    public ScenarioReadinessReportDTO getScenarioReadiness(Long templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "Template not found: " + templateId));
        if ("COMPOSITE".equals(template.getTemplateType())) {
            return buildCompositeReport(templateId);
        }
        return buildSingleFileReport(templateId, template);
    }

    private ScenarioReadinessReportDTO buildCompositeReport(Long templateId) {
        ScenarioReadinessReportDTO dto = new ScenarioReadinessReportDTO();
        dto.setTemplateId(templateId);
        dto.setCheckedAt(Instant.now());
        dto.setWarnings(new ArrayList<>(List.of(
                "COMPOSITE_READINESS_USES_VARIABLE_AGGREGATE_ONLY")));

        var composite = compositeCoverageService.checkCoverage(templateId);
        double variableReadiness = round(composite.getOverallCoveragePercent());

        ScenarioReadinessSummaryDTO summary = new ScenarioReadinessSummaryDTO();
        summary.setRequiredInformationReadiness(variableReadiness);
        summary.setConditionalClauseReadiness(100.0);
        summary.setRepeatingDetailReadiness(100.0);
        summary.setOverallReadiness(variableReadiness);
        summary.setTotalBranches(0);
        summary.setCoveredBranches(0);
        summary.setTotalLoopScenarios(0);
        summary.setCoveredLoopScenarios(0);
        summary.setTotalParameters(0);
        summary.setCoveredParameters(0);
        summary.setMissingSituations(List.of());
        dto.setReadiness(summary);
        dto.setScenarios(List.of());
        dto.setSuggestions(List.of());
        log.debug("Scenario readiness (composite) template {}: variableAggregate={}%", templateId, variableReadiness);
        return dto;
    }

    private ScenarioReadinessReportDTO buildSingleFileReport(Long templateId, Template template) {
        List<String> warnings = new ArrayList<>();

        List<PlaceholderInfo> placeholders = loadPlaceholders(templateId, template, warnings);
        List<PlaceholderInfo> conditions = new ArrayList<>();
        List<PlaceholderInfo> loops = new ArrayList<>();
        coverageAnalyzer.collectConditionsAndLoops(placeholders, conditions, loops);

        List<ParameterDefinition> params = parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
        List<TestCase> testCases = testCaseRepository.findByTemplateIdOrderByCreatedAtDesc(templateId);
        List<Map<String, Object>> allData = parseTestCaseData(testCases, warnings);

        TemplateCoverageAnalyzer.CoverageAnalysisResult aggregate =
                coverageAnalyzer.analyze(conditions, loops, params, allData);

        ScenarioReadinessSummaryDTO summary = mapSummary(aggregate);
        summary.setMissingSituations(mapMissing(aggregate.getUncoveredItems()));

        List<ScenarioReadinessCaseDTO> cases = new ArrayList<>();
        for (TestCase tc : testCases) {
            Optional<Map<String, Object>> one = parseOneTestCase(tc, warnings);
            if (one.isEmpty()) {
                continue;
            }
            TemplateCoverageAnalyzer.CoverageAnalysisResult per =
                    coverageAnalyzer.analyze(conditions, loops, params, List.of(one.get()));
            ScenarioReadinessCaseDTO c = new ScenarioReadinessCaseDTO();
            c.setTestCaseId(tc.getId());
            c.setScenarioName(tc.getName());
            c.setOverallReadiness(round(per.getOverallCoverage()));
            c.setConditionalClauseReadiness(round(per.getBranchCoverage()));
            c.setRepeatingDetailReadiness(round(per.getLoopCoverage()));
            c.setRequiredInformationReadiness(round(per.getParameterCoverage()));
            c.setMissingSituations(mapMissing(per.getUncoveredItems()));
            cases.add(c);
        }

        ScenarioReadinessReportDTO dto = new ScenarioReadinessReportDTO();
        dto.setTemplateId(templateId);
        dto.setReadiness(summary);
        dto.setScenarios(cases);
        dto.setSuggestions(buildSuggestions(aggregate.getUncoveredItems()));
        dto.setCheckedAt(Instant.now());
        dto.setWarnings(warnings);
        return dto;
    }

    private List<PlaceholderInfo> loadPlaceholders(Long templateId, Template template, List<String> warnings) {
        String filePath = template.getTemplateFilePath();
        if (filePath == null || filePath.isBlank()) {
            warnings.add("TEMPLATE_FILE_MISSING_PLACEHOLDER_SCAN_SKIPPED");
            return List.of();
        }
        try {
            return templateScanService.scanPlaceholders(filePath);
        } catch (Exception e) {
            log.warn("Scenario readiness: placeholder scan failed for template {}: {}", templateId, e.getMessage());
            warnings.add("PLACEHOLDER_SCAN_FAILED:" + e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> parseTestCaseData(List<TestCase> testCases, List<String> warnings) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (TestCase tc : testCases) {
            parseOneTestCase(tc, warnings).ifPresent(result::add);
        }
        return result;
    }

    private Optional<Map<String, Object>> parseOneTestCase(TestCase tc, List<String> warnings) {
        try {
            if (tc.getTestDataJson() != null && !tc.getTestDataJson().isBlank()) {
                Map<String, Object> data = objectMapper.readValue(
                        tc.getTestDataJson(), new TypeReference<>() {});
                return Optional.of(data);
            }
        } catch (Exception e) {
            warnings.add("TEST_CASE_JSON_INVALID:" + tc.getId() + ":" + tc.getName());
        }
        return Optional.empty();
    }

    private ScenarioReadinessSummaryDTO mapSummary(TemplateCoverageAnalyzer.CoverageAnalysisResult a) {
        ScenarioReadinessSummaryDTO s = new ScenarioReadinessSummaryDTO();
        s.setOverallReadiness(round(a.getOverallCoverage()));
        s.setConditionalClauseReadiness(round(a.getBranchCoverage()));
        s.setRepeatingDetailReadiness(round(a.getLoopCoverage()));
        s.setRequiredInformationReadiness(round(a.getParameterCoverage()));
        s.setTotalBranches(a.getTotalBranches());
        s.setCoveredBranches(a.getCoveredBranches());
        s.setTotalLoopScenarios(a.getTotalLoopScenarios());
        s.setCoveredLoopScenarios(a.getCoveredLoopScenarios());
        s.setTotalParameters(a.getTotalParameters());
        s.setCoveredParameters(a.getCoveredParameters());
        return s;
    }

    private List<MissingBusinessSituationDTO> mapMissing(List<UncoveredItem> items) {
        List<MissingBusinessSituationDTO> out = new ArrayList<>();
        for (UncoveredItem u : items) {
            out.add(new MissingBusinessSituationDTO(u.type(), u.name(), u.missingPath()));
        }
        return out;
    }

    private List<ScenarioSuggestionDTO> buildSuggestions(List<UncoveredItem> uncovered) {
        List<ScenarioSuggestionDTO> suggestions = new ArrayList<>();
        int n = 0;
        for (UncoveredItem u : uncovered) {
            if (n >= MAX_SUGGESTIONS) {
                break;
            }
            ScenarioSuggestionDTO s = new ScenarioSuggestionDTO();
            s.setType("CREATE_SCENARIO");
            s.setSourceMissingSituation(new MissingBusinessSituationDTO(u.type(), u.name(), u.missingPath()));
            switch (u.type()) {
                case "BRANCH" -> {
                    if ("true".equals(u.missingPath())) {
                        s.setCode("BRANCH_MISSING_TRUE");
                        s.setTitle("Exercise the true path");
                        s.setReason("No scenario drives condition \"" + u.name() + "\" to a true outcome.");
                    } else {
                        s.setCode("BRANCH_MISSING_FALSE");
                        s.setTitle("Exercise the false path");
                        s.setReason("No scenario drives condition \"" + u.name() + "\" to a false outcome.");
                    }
                }
                case "LOOP" -> {
                    if ("empty".equals(u.missingPath())) {
                        s.setCode("LOOP_MISSING_EMPTY");
                        s.setTitle("Try an empty detail list");
                        s.setReason("Loop \"" + u.name() + "\" has not been covered with an empty collection.");
                    } else {
                        s.setCode("LOOP_MISSING_NONEMPTY");
                        s.setTitle("Try a non-empty detail list");
                        s.setReason("Loop \"" + u.name() + "\" has not been covered with at least one row.");
                    }
                }
                case "PARAMETER" -> {
                    s.setCode("PARAMETER_MISSING_VALUE");
                    s.setTitle("Supply required information");
                    s.setReason("Parameter \"" + u.name() + "\" is never set across scenarios.");
                }
                default -> {
                    s.setCode("UNKNOWN");
                    s.setTitle("Add a scenario");
                    s.setReason("Coverage gap for " + u.type() + " \"" + u.name() + "\".");
                }
            }
            suggestions.add(s);
            n++;
        }
        return suggestions;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
