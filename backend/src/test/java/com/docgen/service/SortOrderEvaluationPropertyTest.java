package com.docgen.service;

import com.docgen.entity.ExpressionType;
import com.docgen.entity.ParameterDefinition;
import com.docgen.repository.ParameterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for sort-order evaluation dependency of DERIVED parameters.
 *
 * <p><b>Validates: Requirements 4.4</b></p>
 */
// Feature: array-aggregation-and-row-derived, Property 7: Sort-Order 求值依赖
class SortOrderEvaluationPropertyTest {

    private static final Long TEMPLATE_ID = 1L;

    private ParameterDefinition makeParam(Long id, Long parentId, String name,
                                           String paramType, String dataType, int sortOrder) {
        ParameterDefinition p = new ParameterDefinition();
        p.setId(id);
        p.setTemplateId(TEMPLATE_ID);
        p.setParentId(parentId);
        p.setName(name);
        p.setParameterType(paramType);
        p.setDataType(dataType);
        p.setSortOrder(sortOrder);
        return p;
    }

    /**
     * Property 7: For any set of DERIVED parameters under the same parent, they SHALL be
     * evaluated in sort_order sequence, and each DERIVED parameter's expression context SHALL
     * include the computed values of all previously evaluated DERIVED parameters.
     *
     * Strategy: Create an ARRAY with REQUEST fields (price, quantity) and a chain of DERIVED
     * fields where each depends on the previous one:
     *   subtotal = price * quantity (sort_order 10)
     *   tax = subtotal * taxRate    (sort_order 20, where taxRate is a constant 0.1)
     *   total = subtotal + tax      (sort_order 30)
     *
     * The mock expression engine tracks which context keys are available at each evaluation.
     *
     * <p><b>Validates: Requirements 4.4</b></p>
     */
    @Property(tries = 200)
    @Label("Property 7: Sort-Order 求值依赖")
    void derivedParamsEvaluatedInSortOrder_contextAccumulates(
            @ForAll("chainTestData") ChainTestData testData
    ) {
        // Setup parameter definitions
        ParameterDefinition arrayParam = makeParam(1L, null, "items", "REQUEST", "ARRAY", 0);
        ParameterDefinition priceChild = makeParam(2L, 1L, "price", "REQUEST", "NUMBER", 0);
        ParameterDefinition qtyChild = makeParam(3L, 1L, "quantity", "REQUEST", "NUMBER", 1);

        // DERIVED chain: subtotal → tax → total
        ParameterDefinition subtotalChild = makeParam(10L, 1L, "subtotal", "DERIVED", "NUMBER", 10);
        subtotalChild.setExpressionText("price * quantity");
        subtotalChild.setExpressionType("JAVASCRIPT");

        ParameterDefinition taxChild = makeParam(11L, 1L, "tax", "DERIVED", "NUMBER", 20);
        taxChild.setExpressionText("subtotal * 0.1");
        taxChild.setExpressionType("JAVASCRIPT");

        ParameterDefinition totalChild = makeParam(12L, 1L, "total", "DERIVED", "NUMBER", 30);
        totalChild.setExpressionText("subtotal + tax");
        totalChild.setExpressionType("JAVASCRIPT");

        List<ParameterDefinition> rootParams = List.of(arrayParam);
        Map<Long, List<ParameterDefinition>> childrenMap = new LinkedHashMap<>();
        childrenMap.put(1L, List.of(priceChild, qtyChild, subtotalChild, taxChild, totalChild));

        // Track context keys seen at each DERIVED evaluation
        List<Set<String>> contextKeySets = Collections.synchronizedList(new ArrayList<>());

        ExpressionEngine engine = mock(ExpressionEngine.class);
        when(engine.evaluate(anyString(), eq(ExpressionType.JAVASCRIPT), anyMap()))
                .thenAnswer(inv -> {
                    String expr = inv.getArgument(0);
                    Map<String, Object> ctx = inv.getArgument(2);
                    contextKeySets.add(new LinkedHashSet<>(ctx.keySet()));

                    // Simple mock evaluation
                    if ("price * quantity".equals(expr)) {
                        Number p = (Number) ctx.get("price");
                        Number q = (Number) ctx.get("quantity");
                        return p.doubleValue() * q.doubleValue();
                    } else if ("subtotal * 0.1".equals(expr)) {
                        Number s = (Number) ctx.get("subtotal");
                        return s.doubleValue() * 0.1;
                    } else if ("subtotal + tax".equals(expr)) {
                        Number s = (Number) ctx.get("subtotal");
                        Number t = (Number) ctx.get("tax");
                        return s.doubleValue() + t.doubleValue();
                    }
                    return 0;
                });

        ParameterRepository repo = mock(ParameterRepository.class);
        AggregationResolver aggResolver = mock(AggregationResolver.class);
        ParameterValidationService service = new ParameterValidationService(
                repo, engine, new ObjectMapper(), aggResolver);

        // Build context with array data
        List<Object> arrayData = new ArrayList<>();
        for (int[] row : testData.rows) {
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("price", row[0]);
            rowMap.put("quantity", row[1]);
            arrayData.add(rowMap);
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("items", arrayData);

        // Execute
        service.evaluateNestedDerivedParameters(context, rootParams, childrenMap);

        // For each row, 3 DERIVED params are evaluated in order
        int numRows = testData.rows.length;
        assertEquals(numRows * 3, contextKeySets.size(),
                "Should have 3 evaluations per row");

        for (int r = 0; r < numRows; r++) {
            int base = r * 3;

            // First DERIVED (subtotal): context should have price, quantity (no subtotal yet)
            Set<String> ctx1 = contextKeySets.get(base);
            assertTrue(ctx1.contains("price"), "subtotal eval should see 'price'");
            assertTrue(ctx1.contains("quantity"), "subtotal eval should see 'quantity'");
            assertFalse(ctx1.contains("subtotal"), "subtotal eval should NOT see 'subtotal' yet");

            // Second DERIVED (tax): context should have price, quantity, subtotal
            Set<String> ctx2 = contextKeySets.get(base + 1);
            assertTrue(ctx2.contains("subtotal"), "tax eval should see 'subtotal'");
            assertFalse(ctx2.contains("tax"), "tax eval should NOT see 'tax' yet");

            // Third DERIVED (total): context should have price, quantity, subtotal, tax
            Set<String> ctx3 = contextKeySets.get(base + 2);
            assertTrue(ctx3.contains("subtotal"), "total eval should see 'subtotal'");
            assertTrue(ctx3.contains("tax"), "total eval should see 'tax'");

            // Verify accumulated context grows
            assertTrue(ctx2.size() > ctx1.size(),
                    "Context should grow after each DERIVED evaluation");
            assertTrue(ctx3.size() > ctx2.size(),
                    "Context should grow after each DERIVED evaluation");
        }

        // Verify computed values in the row maps
        for (int r = 0; r < numRows; r++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rowMap = (Map<String, Object>) arrayData.get(r);
            assertNotNull(rowMap.get("subtotal"), "subtotal should be computed");
            assertNotNull(rowMap.get("tax"), "tax should be computed");
            assertNotNull(rowMap.get("total"), "total should be computed");
        }
    }


    record ChainTestData(int[][] rows) {}

    @Provide
    Arbitrary<ChainTestData> chainTestData() {
        Arbitrary<int[]> row = Arbitraries.integers().between(1, 500)
                .flatMap(price -> Arbitraries.integers().between(1, 100)
                        .map(qty -> new int[]{price, qty}));

        return row.list().ofMinSize(1).ofMaxSize(10)
                .map(list -> new ChainTestData(list.toArray(new int[0][])));
    }
}

