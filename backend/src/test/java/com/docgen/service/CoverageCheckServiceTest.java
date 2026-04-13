package com.docgen.service;

import com.docgen.dto.CoverageReport;
import com.docgen.entity.DataSource;
import com.docgen.entity.Expression;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateVariable;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.DataSourceRepository;
import com.docgen.repository.ExpressionRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateVariableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoverageCheckServiceTest {

    @Mock
    private TemplateVariableRepository variableRepository;
    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private DataSourceRepository dataSourceRepository;
    @Mock
    private ExpressionRepository expressionRepository;
    @Mock
    private TemplateVariableService templateVariableService;

    private CoverageCheckService service;
    private Template template;

    @BeforeEach
    void setUp() {
        service = new CoverageCheckService(
                variableRepository, templateRepository,
                dataSourceRepository, expressionRepository,
                templateVariableService);

        template = new Template();
        template.setId(1L);
        template.setTenantId(10L);
        template.setName("Test Template");
        template.setTemplateFilePath("templates/10/test.docx");
    }

    // ── checkCoverage ──

    @Test
    void checkCoverage_allBound_returns100Percent() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateVariableService.scanVariables(1L)).thenReturn(Collections.emptyList());

        TemplateVariable v1 = createVariable(10L, "name", true, "userApi", "user.name");
        TemplateVariable v2 = createVariable(11L, "email", true, "userApi", "user.email");
        when(variableRepository.findByTemplateIdOrderByNameAsc(1L)).thenReturn(List.of(v1, v2));
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L)).thenReturn(Collections.emptyList());
        when(expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(1L)).thenReturn(Collections.emptyList());

        CoverageReport report = service.checkCoverage(1L);

        assertEquals(100.0, report.getCoveragePercentage());
        assertEquals(2, report.getTotalTags());
        assertEquals(2, report.getBoundTags());
        assertEquals(0, report.getUnboundTags());
        assertTrue(report.getUnboundTagNames().isEmpty());
        assertFalse(report.isBelowThreshold());
        assertEquals("Test Template", report.getTemplateName());
        assertNotNull(report.getCheckedAt());
    }

    @Test
    void checkCoverage_noneBound_returns0Percent() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateVariableService.scanVariables(1L)).thenReturn(Collections.emptyList());

        TemplateVariable v1 = createVariable(10L, "name", false, null, null);
        TemplateVariable v2 = createVariable(11L, "email", false, null, null);
        when(variableRepository.findByTemplateIdOrderByNameAsc(1L)).thenReturn(List.of(v1, v2));
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L)).thenReturn(Collections.emptyList());
        when(expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(1L)).thenReturn(Collections.emptyList());

        CoverageReport report = service.checkCoverage(1L);

        assertEquals(0.0, report.getCoveragePercentage());
        assertEquals(2, report.getTotalTags());
        assertEquals(0, report.getBoundTags());
        assertEquals(2, report.getUnboundTags());
        assertEquals(List.of("name", "email"), report.getUnboundTagNames());
        assertTrue(report.isBelowThreshold());
    }

    @Test
    void checkCoverage_partiallyBound_calculatesCorrectly() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateVariableService.scanVariables(1L)).thenReturn(Collections.emptyList());

        TemplateVariable v1 = createVariable(10L, "name", true, "userApi", "user.name");
        TemplateVariable v2 = createVariable(11L, "email", false, null, null);
        TemplateVariable v3 = createVariable(12L, "phone", true, "userApi", "user.phone");
        when(variableRepository.findByTemplateIdOrderByNameAsc(1L)).thenReturn(List.of(v1, v2, v3));
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L)).thenReturn(Collections.emptyList());
        when(expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(1L)).thenReturn(Collections.emptyList());

        CoverageReport report = service.checkCoverage(1L);

        assertEquals(66.67, report.getCoveragePercentage());
        assertEquals(3, report.getTotalTags());
        assertEquals(2, report.getBoundTags());
        assertEquals(1, report.getUnboundTags());
        assertEquals(List.of("email"), report.getUnboundTagNames());
        assertTrue(report.isBelowThreshold()); // below default 100%
    }

    @Test
    void checkCoverage_noVariables_returns100Percent() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateVariableService.scanVariables(1L)).thenReturn(Collections.emptyList());
        when(variableRepository.findByTemplateIdOrderByNameAsc(1L)).thenReturn(Collections.emptyList());
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L)).thenReturn(Collections.emptyList());
        when(expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(1L)).thenReturn(Collections.emptyList());

        CoverageReport report = service.checkCoverage(1L);

        assertEquals(100.0, report.getCoveragePercentage());
        assertEquals(0, report.getTotalTags());
        assertFalse(report.isBelowThreshold());
    }

    @Test
    void checkCoverage_customThreshold_belowThreshold() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateVariableService.scanVariables(1L)).thenReturn(Collections.emptyList());

        TemplateVariable v1 = createVariable(10L, "name", true, "userApi", "user.name");
        TemplateVariable v2 = createVariable(11L, "email", true, "userApi", "user.email");
        TemplateVariable v3 = createVariable(12L, "phone", false, null, null);
        when(variableRepository.findByTemplateIdOrderByNameAsc(1L)).thenReturn(List.of(v1, v2, v3));
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L)).thenReturn(Collections.emptyList());
        when(expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(1L)).thenReturn(Collections.emptyList());

        // 66.67% coverage, threshold 50% → not below
        CoverageReport report = service.checkCoverage(1L, 50.0);
        assertFalse(report.isBelowThreshold());
        assertEquals(50.0, report.getThreshold());
    }

    @Test
    void checkCoverage_templateNotFound_throws() {
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.checkCoverage(999L));
    }

    // ── Reverse coverage ──

    @Test
    void checkCoverage_detectsUnusedDataSources() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateVariableService.scanVariables(1L)).thenReturn(Collections.emptyList());

        TemplateVariable v1 = createVariable(10L, "name", true, "DATASOURCE", "userApi.name");
        when(variableRepository.findByTemplateIdOrderByNameAsc(1L)).thenReturn(List.of(v1));

        DataSource ds1 = createDataSource(100L, "userApi");
        DataSource ds2 = createDataSource(101L, "orderApi"); // not used
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L)).thenReturn(List.of(ds1, ds2));
        when(expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(1L)).thenReturn(Collections.emptyList());

        CoverageReport report = service.checkCoverage(1L);

        assertEquals(1, report.getUnusedDataSourceFields().size());
        assertEquals("datasource:orderApi", report.getUnusedDataSourceFields().get(0));
    }

    @Test
    void checkCoverage_detectsUnusedExpressions() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateVariableService.scanVariables(1L)).thenReturn(Collections.emptyList());

        TemplateVariable v1 = createVariable(10L, "total", true, "EXPRESSION", "calcTotal");
        when(variableRepository.findByTemplateIdOrderByNameAsc(1L)).thenReturn(List.of(v1));

        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L)).thenReturn(Collections.emptyList());

        Expression expr1 = createExpression(200L, "calcTotal");
        Expression expr2 = createExpression(201L, "calcTax"); // not used
        when(expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(1L)).thenReturn(List.of(expr1, expr2));

        CoverageReport report = service.checkCoverage(1L);

        assertEquals(1, report.getUnusedDataSourceFields().size());
        assertEquals("expression:calcTax", report.getUnusedDataSourceFields().get(0));
    }

    @Test
    void checkCoverage_allDataSourcesAndExpressionsUsed_noUnused() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(templateVariableService.scanVariables(1L)).thenReturn(Collections.emptyList());

        TemplateVariable v1 = createVariable(10L, "name", true, "DATASOURCE", "userApi.name");
        TemplateVariable v2 = createVariable(11L, "total", true, "EXPRESSION", "calcTotal");
        when(variableRepository.findByTemplateIdOrderByNameAsc(1L)).thenReturn(List.of(v1, v2));

        DataSource ds1 = createDataSource(100L, "userApi");
        when(dataSourceRepository.findByTemplateIdOrderByPriorityDesc(1L)).thenReturn(List.of(ds1));

        Expression expr1 = createExpression(200L, "calcTotal");
        when(expressionRepository.findByTemplateIdOrderByExecutionOrderAsc(1L)).thenReturn(List.of(expr1));

        CoverageReport report = service.checkCoverage(1L);

        assertTrue(report.getUnusedDataSourceFields().isEmpty());
    }

    // ── isBelowThreshold ──

    @Test
    void isBelowThreshold_belowThreshold_returnsTrue() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        TemplateVariable v1 = createVariable(10L, "name", false, null, null);
        when(variableRepository.findByTemplateIdOrderByNameAsc(1L)).thenReturn(List.of(v1));

        assertTrue(service.isBelowThreshold(1L, 100.0));
    }

    @Test
    void isBelowThreshold_atThreshold_returnsFalse() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        TemplateVariable v1 = createVariable(10L, "name", true, "DATASOURCE", "user.name");
        when(variableRepository.findByTemplateIdOrderByNameAsc(1L)).thenReturn(List.of(v1));

        assertFalse(service.isBelowThreshold(1L, 100.0));
    }

    @Test
    void isBelowThreshold_noVariables_returnsFalse() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(variableRepository.findByTemplateIdOrderByNameAsc(1L)).thenReturn(Collections.emptyList());

        assertFalse(service.isBelowThreshold(1L, 100.0));
    }

    // ── Helpers ──

    private TemplateVariable createVariable(Long id, String name, boolean bound,
                                            String bindingSource, String bindingField) {
        TemplateVariable v = new TemplateVariable();
        v.setId(id);
        v.setTemplateId(1L);
        v.setName(name);
        v.setVariableType("STRING");
        v.setBound(bound);
        v.setBindingSource(bindingSource);
        v.setBindingField(bindingField);
        v.setCreatedAt(Instant.now());
        return v;
    }

    private DataSource createDataSource(Long id, String name) {
        DataSource ds = new DataSource();
        ds.setId(id);
        ds.setTemplateId(1L);
        ds.setName(name);
        ds.setType("HTTP_API");
        ds.setConfigJson("{}");
        return ds;
    }

    private Expression createExpression(Long id, String name) {
        Expression expr = new Expression();
        expr.setId(id);
        expr.setTemplateId(1L);
        expr.setName(name);
        expr.setExpressionType("JAVASCRIPT");
        expr.setExpressionText("1+1");
        return expr;
    }
}
