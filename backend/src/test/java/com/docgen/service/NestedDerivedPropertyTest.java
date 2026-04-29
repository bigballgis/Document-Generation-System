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
 * Property-based tests for recursive nested DERIVED evaluation.
 * Verifies that inner-level DERIVED parameters are evaluated before outer-level.
 *
 * <p><b>Validates: Requirements 4.10</b></p>
 */
// Feature: array-aggregation-and-row-derived, Property 8: 递归嵌套 DERIVED 求值
class NestedDerivedPropertyTest {

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
     * Property 8: For nested ARRAY structures containing Row_Level_Derived parameters at
     * multiple nesting levels, the inner-level DERIVED parameters SHALL be fully evaluated
     * before outer-level DERIVED parameters.
     *
     * Structure:
     *   orders (ARRAY)
     *     ├── orderId (REQUEST, STRING)
     *     ├── items (ARRAY)
     *     │     ├── price (REQUEST, NUMBER)
     *     │     ├── quantity (REQUEST, NUMBER)
     *     │     └── subtotal (DERIVED, NUMBER) = price * quantity  [inner level]
     *     └── orderLabel (DERIVED, STRING) = orderId + "_processed" [outer level]
     *
     * When evaluating outer-level DERIVED (orderLabel), inner-level DERIVED (subtotal)
     * should already be computed in each items row.
     *
     * <p><b>Validates: Requirements 4.10</b></p>
     */
    @Property(tries = 200)
    @Label("Property 8: 递归嵌套 DERIVED 求值")
    void innerLevelDerivedEvaluatedBeforeOuterLevel(
            @ForAll("nestedDerivedData") NestedDerivedTestData testData
    ) {
        // Parameter definitions
        ParameterDefinition ordersParam = makeParam(1L, null, "orders", "REQUEST", "ARRAY", 0);

        // Children of orders
        ParameterDefinition orderIdChild = makeParam(2L, 1L, "orderId", "REQUEST", "STRING", 0);
        ParameterDefinition itemsChild = makeParam(3L, 1L, "items", "REQUEST", "ARRAY", 1);
        ParameterDefinition orderLabelChild = makeParam(4L, 1L, "orderLabel", "DERIVED", "STRING", 10);
        orderLabelChild.setExpressionText("orderId + '_processed'");
        orderLabelChild.setExpressionType("JAVASCRIPT");

        // Children of items (inner ARRAY)
        ParameterDefinition priceGrandChild = makeParam(5L, 3L, "price", "REQUEST", "NUMBER", 0);
        ParameterDefinition qtyGrandChild = makeParam(6L, 3L, "quantity", "REQUEST", "NUMBER", 1);
        ParameterDefinition subtotalGrandChild = makeParam(7L, 3L, "subtotal", "DERIVED", "NUMBER", 10);
        subtotalGrandChild.setExpressionText("price * quantity");
        subtotalGrandChild.setExpressionType("JAVASCRIPT");

        List<ParameterDefinition> rootParams = List.of(ordersParam);
        Map<Long, List<ParameterDefinition>> childrenMap = new LinkedHashMap<>();
        childrenMap.put(1L, List.of(orderIdChild, itemsChild, orderLabelChild));
        childrenMap.put(3L, List.of(priceGrandChild, qtyGrandChild, subtotalGrandChild));

        // Track evaluation order
        List<String> evaluationOrder = Collections.synchronizedList(new ArrayList<>());

        ExpressionEngine engine = mock(ExpressionEngine.class);
        when(engine.evaluate(anyString(), eq(ExpressionType.JAVASCRIPT), anyMap()))
                .thenAnswer(inv -> {
                    String expr = inv.getArgument(0);
                    Map<String, Object> ctx = inv.getArgument(2);

                    if ("price * quantity".equals(expr)) {
                        evaluationOrder.add("inner_subtotal");
                        Number p = (Number) ctx.get("price");
                        Number q = (Number) ctx.get("quantity");
                        return p.intValue() * q.intValue();
                    } else if ("orderId + '_processed'".equals(expr)) {
                        evaluationOrder.add("outer_orderLabel");
                        String id = (String) ctx.get("orderId");
                        return id + "_processed";
                    }
                    return null;
                });

        ParameterRepository repo = mock(ParameterRepository.class);
        AggregationResolver aggResolver = mock(AggregationResolver.class);
        ParameterValidationService service = new ParameterValidationService(
                repo, engine, new ObjectMapper(), aggResolver);

        // Build context
        List<Object> ordersData = new ArrayList<>();
        for (OrderData order : testData.orders) {
            Map<String, Object> orderMap = new LinkedHashMap<>();
            orderMap.put("orderId", order.orderId);
            List<Object> itemsList = new ArrayList<>();
            for (int[] item : order.items) {
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put("price", item[0]);
                itemMap.put("quantity", item[1]);
                itemsList.add(itemMap);
            }
            orderMap.put("items", itemsList);
            ordersData.add(orderMap);
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("orders", ordersData);

        // Execute
        service.evaluateNestedDerivedParameters(context, rootParams, childrenMap);

        // Count total inner and outer evaluations
        int totalInnerItems = testData.orders.stream().mapToInt(o -> o.items.length).sum();
        int totalOrders = testData.orders.size();

        // Verify all inner subtotals are evaluated
        long innerCount = evaluationOrder.stream().filter("inner_subtotal"::equals).count();
        assertEquals(totalInnerItems, innerCount,
                "All inner subtotals should be evaluated");

        // Verify all outer orderLabels are evaluated
        long outerCount = evaluationOrder.stream().filter("outer_orderLabel"::equals).count();
        assertEquals(totalOrders, outerCount,
                "All outer orderLabels should be evaluated");

        // Verify ordering: for each order, all its inner subtotals come before its outer orderLabel
        // Since we process inner before outer per order element, we check that
        // within each order's evaluations, inner comes first
        int innerSeen = 0;
        int outerSeen = 0;
        for (String eval : evaluationOrder) {
            if ("inner_subtotal".equals(eval)) {
                innerSeen++;
            } else if ("outer_orderLabel".equals(eval)) {
                outerSeen++;
                // At this point, all inner items for orders[0..outerSeen-1] should be done
                // The inner items for the current order should have been evaluated
                // We verify by checking that subtotal values exist in the data
            }
        }

        // Verify computed values exist
        for (int o = 0; o < testData.orders.size(); o++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> orderMap = (Map<String, Object>) ordersData.get(o);

            // Outer DERIVED should be computed
            assertNotNull(orderMap.get("orderLabel"),
                    "orderLabel should be computed for order " + o);
            assertEquals(testData.orders.get(o).orderId + "_processed", orderMap.get("orderLabel"));

            // Inner DERIVED should be computed
            @SuppressWarnings("unchecked")
            List<Object> items = (List<Object>) orderMap.get("items");
            for (int i = 0; i < items.size(); i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> itemMap = (Map<String, Object>) items.get(i);
                assertNotNull(itemMap.get("subtotal"),
                        "subtotal should be computed for order " + o + " item " + i);
                int expectedSubtotal = testData.orders.get(o).items[i][0]
                        * testData.orders.get(o).items[i][1];
                assertEquals(expectedSubtotal, itemMap.get("subtotal"),
                        "subtotal should equal price * quantity");
            }
        }

        // Verify inner-before-outer ordering per order:
        // For each order, its inner evaluations should appear before its outer evaluation
        // We track this by verifying that when we see the Nth "outer_orderLabel",
        // we've already seen all inner subtotals for orders 0..N
        int innerExpectedSoFar = 0;
        int innerActualSoFar = 0;
        int outerIdx = 0;
        for (String eval : evaluationOrder) {
            if ("inner_subtotal".equals(eval)) {
                innerActualSoFar++;
            } else if ("outer_orderLabel".equals(eval)) {
                innerExpectedSoFar += testData.orders.get(outerIdx).items.length;
                assertTrue(innerActualSoFar >= innerExpectedSoFar,
                        "Inner DERIVED for order " + outerIdx
                                + " should be evaluated before outer DERIVED");
                outerIdx++;
            }
        }
    }


    record OrderData(String orderId, int[][] items) {}
    record NestedDerivedTestData(List<OrderData> orders) {}

    @Provide
    Arbitrary<NestedDerivedTestData> nestedDerivedData() {
        Arbitrary<int[]> item = Arbitraries.integers().between(1, 500)
                .flatMap(price -> Arbitraries.integers().between(1, 50)
                        .map(qty -> new int[]{price, qty}));

        Arbitrary<OrderData> order = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(8)
                .flatMap(orderId -> item.list().ofMinSize(1).ofMaxSize(5)
                        .map(items -> new OrderData(orderId, items.toArray(new int[0][]))));

        return order.list().ofMinSize(1).ofMaxSize(5)
                .map(NestedDerivedTestData::new);
    }
}

