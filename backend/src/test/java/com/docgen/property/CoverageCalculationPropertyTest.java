package com.docgen.property;

import com.docgen.dto.CoverageReport;
import com.docgen.entity.DataSource;
import com.docgen.entity.Expression;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateVariable;
import com.docgen.repository.DataSourceRepository;
import com.docgen.repository.ExpressionRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateVariableRepository;
import com.docgen.service.CoverageCheckService;
import com.docgen.service.TemplateVariableService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 13: 模板覆盖率计算正确性
 *
 * For any set of template variables (bound and unbound), the coverage percentage
 * should equal (boundTags / totalTags) × 100%, rounded to 2 decimal places,
 * and unbound tag names should be correctly identified.
 *
 * <p><b>Validates: Requirements 47.3, 47.4, 47.6</b></p>
 */
@Tag("Feature: low-code-document-generation-system, Property 13: 模板覆盖率计算正确性")
class CoverageCalculationPropertyTest {

    private static final Long TEMPLATE_ID = 1L;

    /**
     * Property 1: Coverage percentage = (boundTags / totalTags) × 100%, rounded to 2 decimal places.
     * Also verifies totalTags = boundTags + unboundTags.
     *
     * <b>Validates: Requirements 47.3, 47.4</b>
     */
    @Property(tries = 200)
    @Label("Coverage percentage equals boundTags/totalTags * 100 rounded to 2 decimals")
    void coveragePercentageIsCorrect(
            @ForAll @IntRange(min = 0, max = 50) int boundCount,
            @ForAll @IntRange(min = 0, max = 50) int unboundCount) {

        int totalTags = boundCount + unboundCount;
        List<TemplateVariable> variables = buildVariableList(boundCount, unboundCount);
        CoverageCheckService service = buildServiceWithVariables(variables);

        CoverageReport report = service.checkCoverage(TEMPLATE_ID);

        // Verify totalTags = boundTags + unboundTags
        assertEquals(totalTags, report.getTotalTags(),
                "totalTags should equal boundCount + unboundCount");
        assertEquals(boundCount, report.getBoundTags(),
                "boundTags should equal the number of bound variables");
        assertEquals(unboundCount, report.getUnboundTags(),
                "unboundTags should equal the number of unbound variables");
        assertEquals(totalTags, report.getBoundTags() + report.getUnboundTags(),
                "totalTags must equal boundTags + unboundTags");

        // Verify coverage percentage calculation
        double expectedCoverage = totalTags == 0 ? 100.0 : (boundCount * 100.0) / totalTags;
        expectedCoverage = Math.round(expectedCoverage * 100.0) / 100.0;
        assertEquals(expectedCoverage, report.getCoveragePercentage(), 0.001,
                "Coverage should be (bound/total)*100 rounded to 2 decimals");
    }

    /**
     * Property 2: Unbound tag names list size equals unboundTags count,
     * and contains exactly the names of unbound variables.
     *
     * <b>Validates: Requirements 47.4, 47.6</b>
     */
    @Property(tries = 200)
    @Label("Unbound tag names list matches unbound variable count and names")
    void unboundTagNamesAreCorrect(
            @ForAll @IntRange(min = 0, max = 30) int boundCount,
            @ForAll @IntRange(min = 0, max = 30) int unboundCount) {

        List<TemplateVariable> variables = buildVariableList(boundCount, unboundCount);
        CoverageCheckService service = buildServiceWithVariables(variables);

        CoverageReport report = service.checkCoverage(TEMPLATE_ID);

        assertEquals(unboundCount, report.getUnboundTagNames().size(),
                "unboundTagNames size should equal unboundTags count");

        // Verify all unbound variable names are present
        Set<String> expectedUnbound = new HashSet<>();
        for (TemplateVariable v : variables) {
            if (!v.isBound()) {
                expectedUnbound.add(v.getName());
            }
        }
        assertEquals(expectedUnbound, new HashSet<>(report.getUnboundTagNames()),
                "unboundTagNames should contain exactly the names of unbound variables");
    }

    /**
     * Property 3: When all variables are bound, coverage = 100%.
     *
     * <b>Validates: Requirements 47.3</b>
     */
    @Property(tries = 100)
    @Label("All variables bound implies 100% coverage")
    void allBoundMeansFullCoverage(
            @ForAll @IntRange(min = 1, max = 50) int totalCount) {

        List<TemplateVariable> variables = buildVariableList(totalCount, 0);
        CoverageCheckService service = buildServiceWithVariables(variables);

        CoverageReport report = service.checkCoverage(TEMPLATE_ID);

        assertEquals(100.0, report.getCoveragePercentage(), 0.001,
                "Coverage should be 100% when all variables are bound");
        assertTrue(report.getUnboundTagNames().isEmpty(),
                "No unbound tag names when all are bound");
    }

    /**
     * Property 4: When no variables exist, coverage = 100%.
     *
     * <b>Validates: Requirements 47.3</b>
     */
    @Property(tries = 10)
    @Label("Empty template has 100% coverage")
    void emptyTemplateMeansFullCoverage() {
        List<TemplateVariable> variables = Collections.emptyList();
        CoverageCheckService service = buildServiceWithVariables(variables);

        CoverageReport report = service.checkCoverage(TEMPLATE_ID);

        assertEquals(100.0, report.getCoveragePercentage(), 0.001,
                "Coverage should be 100% when no variables exist");
        assertEquals(0, report.getTotalTags());
        assertEquals(0, report.getBoundTags());
        assertEquals(0, report.getUnboundTags());
        assertTrue(report.getUnboundTagNames().isEmpty());
    }

    /**
     * Property 5: Coverage is always between 0 and 100 inclusive.
     *
     * <b>Validates: Requirements 47.3</b>
     */
    @Property(tries = 200)
    @Label("Coverage percentage is always in [0, 100]")
    void coverageIsAlwaysInRange(
            @ForAll @IntRange(min = 0, max = 50) int boundCount,
            @ForAll @IntRange(min = 0, max = 50) int unboundCount) {

        List<TemplateVariable> variables = buildVariableList(boundCount, unboundCount);
        CoverageCheckService service = buildServiceWithVariables(variables);

        CoverageReport report = service.checkCoverage(TEMPLATE_ID);

        assertTrue(report.getCoveragePercentage() >= 0.0,
                "Coverage should be >= 0");
        assertTrue(report.getCoveragePercentage() <= 100.0,
                "Coverage should be <= 100");
    }

    // ── Helper methods ──

    private List<TemplateVariable> buildVariableList(int boundCount, int unboundCount) {
        List<TemplateVariable> variables = new ArrayList<>();
        for (int i = 0; i < boundCount; i++) {
            TemplateVariable v = new TemplateVariable();
            v.setId((long) (i + 1));
            v.setTemplateId(TEMPLATE_ID);
            v.setName("bound_var_" + i);
            v.setBound(true);
            v.setBindingSource("datasource");
            v.setBindingField("ds.field_" + i);
            variables.add(v);
        }
        for (int i = 0; i < unboundCount; i++) {
            TemplateVariable v = new TemplateVariable();
            v.setId((long) (boundCount + i + 1));
            v.setTemplateId(TEMPLATE_ID);
            v.setName("unbound_var_" + i);
            v.setBound(false);
            variables.add(v);
        }
        // Sort by name to match repository behavior
        variables.sort(Comparator.comparing(TemplateVariable::getName));
        return variables;
    }

    private CoverageCheckService buildServiceWithVariables(List<TemplateVariable> variables) {
        TemplateVariableRepository variableRepo = mock(TemplateVariableRepository.class);
        TemplateRepository templateRepo = mock(TemplateRepository.class);
        DataSourceRepository dataSourceRepo = mock(DataSourceRepository.class);
        ExpressionRepository expressionRepo = mock(ExpressionRepository.class);
        TemplateVariableService variableService = mock(TemplateVariableService.class);

        Template template = new Template();
        template.setId(TEMPLATE_ID);
        template.setName("Test Template");
        template.setTenantId(1L);

        when(templateRepo.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(variableRepo.findByTemplateIdOrderByNameAsc(TEMPLATE_ID)).thenReturn(variables);
        when(dataSourceRepo.findByTemplateIdOrderByPriorityDesc(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        when(expressionRepo.findByTemplateIdOrderByExecutionOrderAsc(TEMPLATE_ID)).thenReturn(Collections.emptyList());
        doReturn(Collections.emptyList()).when(variableService).scanVariables(anyLong());

        return new CoverageCheckService(variableRepo, templateRepo, dataSourceRepo, expressionRepo, variableService);
    }
}
