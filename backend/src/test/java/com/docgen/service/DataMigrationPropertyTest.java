package com.docgen.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for data migration correctness (Property 32).
 * <p>
 * Verifies the mapping rules from the V37 Flyway migration:
 * <ul>
 *   <li>TemplateVariable with binding_source != 'EXPRESSION' → REQUEST parameter</li>
 *   <li>TemplateVariable with binding_source = 'EXPRESSION' + matching Expression → DERIVED parameter</li>
 *   <li>Standalone Expression (not bound to any TemplateVariable) → DERIVED parameter</li>
 * </ul>
 * <p>
 * This test validates the migration logic in pure Java (no database required).
 *
 * <p><b>Validates: Requirements 9.2, 9.3, 9.9</b></p>
 */
@Tag("feature-template-parameter-redesign")
class DataMigrationPropertyTest {


    record TemplateVariable(Long templateId, String name, String variableType,
                            String defaultValue, String description,
                            String bindingSource, String bindingField) {}

    record Expression(Long templateId, String name, String expressionText,
                      String expressionType, int executionOrder, String description) {}


    record MigratedParameter(Long templateId, String name, String parameterType,
                             String dataType, String defaultValue, String description,
                             int sortOrder, String expressionText, String expressionType) {}

    /**
     * Simulate the V37 migration logic in Java.
     */
    static List<MigratedParameter> simulateMigration(List<TemplateVariable> variables,
                                                      List<Expression> expressions) {
        List<MigratedParameter> result = new ArrayList<>();

        // Step 2: TemplateVariable with binding_source != 'EXPRESSION' → REQUEST
        for (TemplateVariable tv : variables) {
            if (tv.bindingSource == null || !"EXPRESSION".equals(tv.bindingSource)) {
                String dataType = mapVariableType(tv.variableType);
                result.add(new MigratedParameter(
                        tv.templateId, tv.name, "REQUEST", dataType,
                        tv.defaultValue, tv.description, 0, null, null));
            }
        }

        // Step 3: TemplateVariable with binding_source = 'EXPRESSION' + matching Expression → DERIVED
        for (TemplateVariable tv : variables) {
            if ("EXPRESSION".equals(tv.bindingSource)) {
                Expression matchingExpr = expressions.stream()
                        .filter(e -> e.templateId.equals(tv.templateId)
                                && e.name.equals(tv.bindingField))
                        .findFirst().orElse(null);
                if (matchingExpr != null) {
                    result.add(new MigratedParameter(
                            tv.templateId, tv.name, "DERIVED", "STRING",
                            tv.defaultValue, tv.description, matchingExpr.executionOrder,
                            matchingExpr.expressionText, matchingExpr.expressionType));
                }
            }
        }

        // Step 4: Standalone Expression (not bound to any TemplateVariable) → DERIVED
        Set<String> boundExprNames = new HashSet<>();
        for (TemplateVariable tv : variables) {
            if ("EXPRESSION".equals(tv.bindingSource) && tv.bindingField != null) {
                boundExprNames.add(tv.templateId + ":" + tv.bindingField);
            }
        }
        for (Expression e : expressions) {
            String key = e.templateId + ":" + e.name;
            if (!boundExprNames.contains(key)) {
                result.add(new MigratedParameter(
                        e.templateId, e.name, "DERIVED", "STRING",
                        null, e.description, e.executionOrder,
                        e.expressionText, e.expressionType));
            }
        }

        return result;
    }

    private static String mapVariableType(String variableType) {
        if (variableType == null) return "STRING";
        return switch (variableType) {
            case "STRING" -> "STRING";
            case "NUMBER" -> "NUMBER";
            case "DATE" -> "DATE";
            case "BOOLEAN" -> "BOOLEAN";
            default -> "STRING";
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 32: Data migration correctness
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 32: For any set of TemplateVariables and Expressions:
     * - Unbound variables → REQUEST parameters
     * - Expression-bound variables → DERIVED parameters with expression metadata
     * - Standalone expressions → DERIVED parameters preserving expression_text, expression_type, execution_order
     *
     * <p><b>Validates: Requirements 9.2, 9.3, 9.9</b></p>
     */
    @Property(tries = 100)
    @Tag("property-32-data-migration-correctness")
    void migrationMapsCorrectly(
            @ForAll @IntRange(min = 0, max = 5) int numUnbound,
            @ForAll @IntRange(min = 0, max = 3) int numBound,
            @ForAll @IntRange(min = 0, max = 3) int numStandalone
    ) {
        Long templateId = 1L;
        List<TemplateVariable> variables = new ArrayList<>();
        List<Expression> expressions = new ArrayList<>();

        // Create unbound variables (binding_source = null)
        for (int i = 0; i < numUnbound; i++) {
            variables.add(new TemplateVariable(templateId, "unbound_" + i, "STRING",
                    "default_" + i, "desc_" + i, null, null));
        }

        // Create bound variables + matching expressions
        for (int i = 0; i < numBound; i++) {
            String exprName = "expr_bound_" + i;
            variables.add(new TemplateVariable(templateId, "bound_" + i, "NUMBER",
                    null, "bound_desc_" + i, "EXPRESSION", exprName));
            expressions.add(new Expression(templateId, exprName, "return ctx.x + " + i,
                    "JAVASCRIPT", i + 1, "expr_desc_" + i));
        }

        // Create standalone expressions (not bound to any variable)
        for (int i = 0; i < numStandalone; i++) {
            expressions.add(new Expression(templateId, "standalone_" + i, "return " + i,
                    "EXCEL_FORMULA", 100 + i, "standalone_desc_" + i));
        }

        List<MigratedParameter> migrated = simulateMigration(variables, expressions);

        // Verify counts
        int expectedTotal = numUnbound + numBound + numStandalone;
        assertEquals(expectedTotal, migrated.size(),
                "Total migrated params should be unbound + bound + standalone");

        // Verify unbound → REQUEST
        long requestCount = migrated.stream()
                .filter(p -> "REQUEST".equals(p.parameterType)).count();
        assertEquals(numUnbound, requestCount,
                "Unbound variables should become REQUEST parameters");

        // Verify all REQUEST params have null expression
        migrated.stream()
                .filter(p -> "REQUEST".equals(p.parameterType))
                .forEach(p -> {
                    assertNull(p.expressionText, "REQUEST param should have null expressionText");
                    assertNull(p.expressionType, "REQUEST param should have null expressionType");
                });

        // Verify bound → DERIVED with expression metadata
        for (int i = 0; i < numBound; i++) {
            String name = "bound_" + i;
            MigratedParameter mp = migrated.stream()
                    .filter(p -> name.equals(p.name)).findFirst().orElseThrow();
            assertEquals("DERIVED", mp.parameterType,
                    "Expression-bound variable should become DERIVED");
            assertNotNull(mp.expressionText, "DERIVED param should have expressionText");
            assertEquals("JAVASCRIPT", mp.expressionType);
            assertEquals(i + 1, mp.sortOrder,
                    "sort_order should equal expression's execution_order");
        }

        // Verify standalone → DERIVED preserving metadata
        for (int i = 0; i < numStandalone; i++) {
            String name = "standalone_" + i;
            MigratedParameter mp = migrated.stream()
                    .filter(p -> name.equals(p.name)).findFirst().orElseThrow();
            assertEquals("DERIVED", mp.parameterType,
                    "Standalone expression should become DERIVED");
            assertEquals("return " + i, mp.expressionText,
                    "expression_text should be preserved");
            assertEquals("EXCEL_FORMULA", mp.expressionType,
                    "expression_type should be preserved");
            assertEquals(100 + i, mp.sortOrder,
                    "sort_order should equal execution_order");
        }
    }

    /**
     * Property 32 (edge case): When a bound variable references a non-existent expression,
     * it should NOT be migrated as DERIVED (the JOIN would fail in SQL).
     */
    @Property(tries = 50)
    @Tag("property-32-data-migration-correctness")
    void boundVariable_withNoMatchingExpression_notMigratedAsDerived(
            @ForAll @IntRange(min = 1, max = 3) int numOrphans
    ) {
        Long templateId = 1L;
        List<TemplateVariable> variables = new ArrayList<>();

        for (int i = 0; i < numOrphans; i++) {
            variables.add(new TemplateVariable(templateId, "orphan_" + i, "STRING",
                    null, null, "EXPRESSION", "nonexistent_expr_" + i));
        }

        List<MigratedParameter> migrated = simulateMigration(variables, List.of());

        // Orphan bound variables with no matching expression should not appear
        assertTrue(migrated.isEmpty(),
                "Bound variables with no matching expression should not be migrated");
    }
}

