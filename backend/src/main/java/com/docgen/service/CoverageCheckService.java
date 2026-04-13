package com.docgen.service;

import com.docgen.dto.CoverageReport;
import com.docgen.entity.DataSource;
import com.docgen.entity.Expression;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateVariable;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.DataSourceRepository;
import com.docgen.repository.ExpressionRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateVariableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for checking template data-binding coverage.
 * <p>
 * Forward coverage: percentage of template tags that are bound to a data source or expression.
 * Reverse coverage: data source fields / expression names that are not used by any template tag.
 */
@Service
public class CoverageCheckService {

    private static final Logger log = LoggerFactory.getLogger(CoverageCheckService.class);
    private static final double DEFAULT_THRESHOLD = 100.0;

    private final TemplateVariableRepository variableRepository;
    private final TemplateRepository templateRepository;
    private final DataSourceRepository dataSourceRepository;
    private final ExpressionRepository expressionRepository;
    private final TemplateVariableService templateVariableService;

    public CoverageCheckService(TemplateVariableRepository variableRepository,
                                TemplateRepository templateRepository,
                                DataSourceRepository dataSourceRepository,
                                ExpressionRepository expressionRepository,
                                TemplateVariableService templateVariableService) {
        this.variableRepository = variableRepository;
        this.templateRepository = templateRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.expressionRepository = expressionRepository;
        this.templateVariableService = templateVariableService;
    }

    /**
     * Perform a full coverage check for the given template.
     * Scans variables first to ensure the variable list is up-to-date,
     * then computes forward and reverse coverage.
     *
     * @param templateId the template to check
     * @return coverage report
     */
    @Transactional
    public CoverageReport checkCoverage(Long templateId) {
        return checkCoverage(templateId, DEFAULT_THRESHOLD);
    }

    /**
     * Perform a full coverage check with a custom threshold.
     *
     * @param templateId the template to check
     * @param threshold  coverage threshold percentage (0-100)
     * @return coverage report
     */
    @Transactional
    public CoverageReport checkCoverage(Long templateId, double threshold) {
        Template template = findTemplateOrThrow(templateId);

        // Ensure variables are up-to-date by scanning the template file
        templateVariableService.scanVariables(templateId);

        List<TemplateVariable> variables = variableRepository.findByTemplateIdOrderByNameAsc(templateId);

        // Forward coverage: bound tags / total tags
        int totalTags = variables.size();
        List<TemplateVariable> boundVars = variables.stream()
                .filter(TemplateVariable::isBound)
                .toList();
        int boundTags = boundVars.size();
        int unboundTags = totalTags - boundTags;

        double coveragePercentage = totalTags == 0 ? 100.0 : (boundTags * 100.0) / totalTags;

        List<String> unboundTagNames = variables.stream()
                .filter(v -> !v.isBound())
                .map(TemplateVariable::getName)
                .toList();

        // Reverse coverage: find unused data source fields and expression names
        List<String> unusedFields = computeUnusedDataSourceFields(templateId, boundVars);

        CoverageReport report = new CoverageReport();
        report.setTemplateId(templateId);
        report.setTemplateName(template.getName());
        report.setCoveragePercentage(Math.round(coveragePercentage * 100.0) / 100.0);
        report.setTotalTags(totalTags);
        report.setBoundTags(boundTags);
        report.setUnboundTags(unboundTags);
        report.setUnboundTagNames(unboundTagNames);
        report.setUnusedDataSourceFields(unusedFields);
        report.setBelowThreshold(coveragePercentage < threshold);
        report.setThreshold(threshold);
        report.setCheckedAt(Instant.now());

        log.info("Coverage check for template {}: {}/{} bound ({}%), threshold={}%, belowThreshold={}",
                templateId, boundTags, totalTags, report.getCoveragePercentage(),
                threshold, report.isBelowThreshold());

        return report;
    }

    /**
     * Check if coverage is below threshold — used during template activation to warn.
     *
     * @param templateId the template to check
     * @param threshold  coverage threshold percentage
     * @return true if coverage is below threshold
     */
    @Transactional(readOnly = true)
    public boolean isBelowThreshold(Long templateId, double threshold) {
        findTemplateOrThrow(templateId);
        List<TemplateVariable> variables = variableRepository.findByTemplateIdOrderByNameAsc(templateId);
        if (variables.isEmpty()) {
            return false;
        }
        long boundCount = variables.stream().filter(TemplateVariable::isBound).count();
        double coverage = (boundCount * 100.0) / variables.size();
        return coverage < threshold;
    }

    /**
     * Compute reverse coverage: data source field names and expression names
     * that are not referenced by any bound template variable.
     */
    List<String> computeUnusedDataSourceFields(Long templateId, List<TemplateVariable> boundVars) {
        // Collect all binding fields that are actually used
        Set<String> usedBindings = boundVars.stream()
                .filter(v -> v.getBindingField() != null)
                .map(TemplateVariable::getBindingField)
                .collect(Collectors.toSet());

        List<String> unused = new ArrayList<>();

        // Check data source names
        List<DataSource> dataSources = dataSourceRepository.findByTemplateIdOrderByPriorityDesc(templateId);
        for (DataSource ds : dataSources) {
            String dsName = ds.getName();
            // A data source is considered "used" if any bound variable references it in its binding field
            boolean referenced = usedBindings.stream()
                    .anyMatch(field -> field.startsWith(dsName + ".") || field.equals(dsName));
            if (!referenced) {
                unused.add("datasource:" + dsName);
            }
        }

        // Check expression names
        List<Expression> expressions = expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(templateId);
        for (Expression expr : expressions) {
            String exprName = expr.getName();
            boolean referenced = usedBindings.stream()
                    .anyMatch(field -> field.startsWith(exprName + ".") || field.equals(exprName));
            if (!referenced) {
                unused.add("expression:" + exprName);
            }
        }

        return unused;
    }

    private Template findTemplateOrThrow(Long templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在"));
    }
}
