package com.docgen.service;

import com.docgen.entity.ParameterDefinition;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for sibling circular dependency detection in {@link ParameterService}.
 *
 * <p><b>Validates: Requirements 3.5</b></p>
 */
// Feature: array-aggregation-and-row-derived, Property 5: sibling circular dependency detection
class CircularDependencyPropertyTest {

    private static final Long TEMPLATE_ID = 1L;
    private static final Long PARENT_ID = 10L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ParameterService createService(List<ParameterDefinition> siblings) {
        ParameterRepository repo = mock(ParameterRepository.class);
        when(repo.findByParentIdOrderBySortOrderAsc(PARENT_ID)).thenReturn(siblings);
        return new ParameterService(repo, mock(TemplateRepository.class),
                mock(TemplateScanService.class), mock(ExpressionEngine.class),
                OBJECT_MAPPER, mock(AuditLogService.class), mock(AggregationResolver.class));
    }

    private ParameterDefinition makeDerivedSibling(Long id, String name, String expressionText, int sortOrder) {
        ParameterDefinition p = new ParameterDefinition();
        p.setId(id);
        p.setTemplateId(TEMPLATE_ID);
        p.setParentId(PARENT_ID);
        p.setName(name);
        p.setParameterType("DERIVED");
        p.setDataType("NUMBER");
        p.setSortOrder(sortOrder);
        p.setExpressionText(expressionText);
        p.setExpressionType("JAVASCRIPT");
        return p;
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 5: sibling circular dependency detection
    // ═══════════════════════════════════════════════════════════════

    /**
     * When DERIVED parameters under the same parent form a cycle,
     * detectCircularDependency should throw PARAMETER_CIRCULAR_DEPENDENCY.
     */
    @Property(tries = 200)
    @Label("Property 5a: cyclic sibling dependencies are detected")
    void cyclicSiblingDependenciesAreDetected(
            @ForAll("cyclicGraphs") GraphTestData testData
    ) {
        ParameterService service = createService(testData.existingSiblings);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.detectCircularDependency(TEMPLATE_ID, PARENT_ID,
                        testData.newExpression, testData.newParamName));
        assertEquals(ErrorCode.PARAMETER_CIRCULAR_DEPENDENCY, ex.getErrorCode());
    }

    /**
     * When DERIVED parameters under the same parent have no cycle (DAG),
     * detectCircularDependency should not throw.
     */
    @Property(tries = 200)
    @Label("Property 5b: acyclic sibling dependencies are accepted")
    void acyclicSiblingDependenciesAreAccepted(
            @ForAll("acyclicGraphs") GraphTestData testData
    ) {
        ParameterService service = createService(testData.existingSiblings);

        assertDoesNotThrow(() ->
                service.detectCircularDependency(TEMPLATE_ID, PARENT_ID,
                        testData.newExpression, testData.newParamName));
    }

    // ═══════════════════════════════════════════════════════════════
    // Records & Generators
    // ═══════════════════════════════════════════════════════════════

    record GraphTestData(List<ParameterDefinition> existingSiblings,
                         String newParamName, String newExpression) {}

    @Provide
    Arbitrary<GraphTestData> cyclicGraphs() {
        // Generate cycles of various sizes (2-node, 3-node, 4-node)
        return Arbitraries.integers().between(2, 5).flatMap(size -> {
            return Arbitraries.just(size).map(s -> {
                List<String> names = new ArrayList<>();
                for (int i = 0; i < s; i++) {
                    names.add("param" + (char) ('A' + i));
                }

                // Create a cycle: A -> B -> C -> ... -> A
                // The "new" param is the last one, its expression references the first
                // Existing params form the chain: param[0] refs param[1], param[1] refs param[2], etc.
                List<ParameterDefinition> existing = new ArrayList<>();
                for (int i = 0; i < s - 1; i++) {
                    String expr = names.get((i + 1) % s) + " + 1";
                    existing.add(makeDerivedSibling((long) (i + 100), names.get(i), expr, i));
                }

                // New param closes the cycle: last -> first
                String newName = names.get(s - 1);
                String newExpr = names.get(0) + " * 2";

                return new GraphTestData(existing, newName, newExpr);
            });
        });
    }

    @Provide
    Arbitrary<GraphTestData> acyclicGraphs() {
        // Generate DAGs: each param only references params with lower index (topological order)
        return Arbitraries.integers().between(2, 5).flatMap(size -> {
            return Arbitraries.just(size).map(s -> {
                List<String> names = new ArrayList<>();
                for (int i = 0; i < s; i++) {
                    names.add("param" + (char) ('A' + i));
                }

                // Existing params: each references only earlier params (or none)
                // param[0] has no deps, param[1] refs param[0], param[2] refs param[1], etc.
                List<ParameterDefinition> existing = new ArrayList<>();
                for (int i = 0; i < s - 1; i++) {
                    String expr;
                    if (i == 0) {
                        expr = "100"; // no dependency on other params
                    } else {
                        expr = names.get(i - 1) + " + 1"; // depends on previous only
                    }
                    existing.add(makeDerivedSibling((long) (i + 100), names.get(i), expr, i));
                }

                // New param references the previous one (no cycle)
                String newName = names.get(s - 1);
                String newExpr = names.get(s - 2) + " * 2";

                return new GraphTestData(existing, newName, newExpr);
            });
        });
    }
}
