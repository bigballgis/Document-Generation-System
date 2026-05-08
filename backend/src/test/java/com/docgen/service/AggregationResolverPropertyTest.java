package com.docgen.service;

import com.docgen.entity.ParameterDefinition;
import com.docgen.repository.ParameterRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Property-based tests for {@link AggregationResolver}.
 *
 * <p><b>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.6, 1.7, 1.9, 1.10</b></p>
 */
// Feature: array-aggregation-and-row-derived, Property 1: aggregation math correctness
// Feature: array-aggregation-and-row-derived, Property 2: nested ARRAY aggregation independence
class AggregationResolverPropertyTest {

    private static final Long TEMPLATE_ID = 1L;

    private AggregationResolver createResolver() {
        ParameterRepository repo = mock(ParameterRepository.class);
        return new AggregationResolver(repo);
    }


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

    // ═══════════════════════════════════════════════════════════════
    // Property 1: aggregation math correctness
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 1: For any ARRAY parameter with random elements (including null NUMBER fields),
     * the AggregationResolver SHALL produce mathematically correct aggregation values.
     *
     * <p><b>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.6, 1.7, 1.10</b></p>
     */
    @Property(tries = 200)
    @Label("Property 1: aggregation math correctness")
    void aggregationMathCorrectness(
            @ForAll("randomArrayData") ArrayTestData testData
    ) {
        AggregationResolver resolver = createResolver();

        // Build parameter definitions
        ParameterDefinition arrayParam = makeParam(1L, null, "items", "REQUEST", "ARRAY", 0);
        ParameterDefinition priceChild = makeParam(2L, 1L, "price", "REQUEST", "NUMBER", 0);
        ParameterDefinition nameChild = makeParam(3L, 1L, "name", "REQUEST", "STRING", 1);

        List<ParameterDefinition> rootParams = List.of(arrayParam);
        Map<Long, List<ParameterDefinition>> childrenMap = Map.of(
                1L, List.of(priceChild, nameChild)
        );

        // Build context
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("items", testData.elements);

        // Execute
        resolver.computeAggregations(context, rootParams, childrenMap);

        // Assert $count
        assertEquals(testData.elements.size(), context.get("items.$count"),
                "$count should equal array length");

        // Assert $first and $last
        if (testData.elements.isEmpty()) {
            assertNull(context.get("items.$first"), "$first should be null for empty array");
            assertNull(context.get("items.$last"), "$last should be null for empty array");
        } else {
            assertEquals(testData.elements.get(0), context.get("items.$first"),
                    "$first should be the first element");
            assertEquals(testData.elements.get(testData.elements.size() - 1), context.get("items.$last"),
                    "$last should be the last element");
        }

        // Compute expected NUMBER aggregations
        List<BigDecimal> nonNullPrices = new ArrayList<>();
        for (Object elem : testData.elements) {
            if (elem instanceof Map<?, ?> map) {
                Object price = map.get("price");
                if (price != null) {
                    nonNullPrices.add(new BigDecimal(price.toString()));
                }
            }
        }

        BigDecimal expectedSum = nonNullPrices.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, expectedSum.compareTo((BigDecimal) context.get("items.$sum_price")),
                "$sum_price should equal sum of non-null prices");

        BigDecimal expectedAvg;
        if (nonNullPrices.isEmpty()) {
            expectedAvg = BigDecimal.ZERO;
        } else {
            expectedAvg = expectedSum.divide(BigDecimal.valueOf(nonNullPrices.size()), 2, RoundingMode.HALF_UP);
        }
        assertEquals(0, expectedAvg.compareTo((BigDecimal) context.get("items.$avg_price")),
                "$avg_price should be correct");

        if (nonNullPrices.isEmpty()) {
            assertNull(context.get("items.$min_price"), "$min_price should be null when all null");
            assertNull(context.get("items.$max_price"), "$max_price should be null when all null");
        } else {
            BigDecimal expectedMin = nonNullPrices.stream().min(BigDecimal::compareTo).orElseThrow();
            BigDecimal expectedMax = nonNullPrices.stream().max(BigDecimal::compareTo).orElseThrow();
            assertEquals(0, expectedMin.compareTo((BigDecimal) context.get("items.$min_price")),
                    "$min_price should be correct");
            assertEquals(0, expectedMax.compareTo((BigDecimal) context.get("items.$max_price")),
                    "$max_price should be correct");
        }

        // Assert $join_name
        List<String> nonNullNames = new ArrayList<>();
        for (Object elem : testData.elements) {
            if (elem instanceof Map<?, ?> map) {
                Object name = map.get("name");
                if (name != null) {
                    nonNullNames.add(String.valueOf(name));
                }
            }
        }
        String expectedJoin = String.join(", ", nonNullNames);
        assertEquals(expectedJoin, context.get("items.$join_name"),
                "$join_name should concatenate non-null names with ', '");
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 2: nested ARRAY aggregation independence
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 2: For nested ARRAY structures, modifying one inner array does not affect
     * sibling inner arrays or the outer array's aggregations.
     *
     * <p><b>Validates: Requirements 1.9</b></p>
     */
    @Property(tries = 200)
    @Label("Property 2: nested ARRAY aggregation independence")
    void nestedArrayAggregationIndependence(
            @ForAll("nestedArrayData") NestedArrayTestData testData
    ) {
        AggregationResolver resolver = createResolver();

        // Outer ARRAY "orders" with inner ARRAY "items" per order
        ParameterDefinition ordersParam = makeParam(1L, null, "orders", "REQUEST", "ARRAY", 0);
        ParameterDefinition itemsChild = makeParam(2L, 1L, "items", "REQUEST", "ARRAY", 0);
        ParameterDefinition priceGrandChild = makeParam(3L, 2L, "price", "REQUEST", "NUMBER", 0);

        List<ParameterDefinition> rootParams = List.of(ordersParam);
        Map<Long, List<ParameterDefinition>> childrenMap = Map.of(
                1L, List.of(itemsChild),
                2L, List.of(priceGrandChild)
        );

        // Run 1: original data
        Map<String, Object> context1 = new LinkedHashMap<>();
        List<Object> orders1 = deepCopyOrders(testData.orders);
        context1.put("orders", orders1);
        resolver.computeAggregations(context1, rootParams, childrenMap);

        // Capture sibling aggregation from order[0] (if exists)
        Map<String, Object> siblingAggs1 = new LinkedHashMap<>();
        if (testData.orders.size() > 1) {
            @SuppressWarnings("unchecked")
            Map<String, Object> order0 = (Map<String, Object>) orders1.get(0);
            for (Map.Entry<String, Object> entry : order0.entrySet()) {
                if (entry.getKey().startsWith("items.$")) {
                    siblingAggs1.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // Run 2: mutate order[last].items by adding an extra element
        Map<String, Object> context2 = new LinkedHashMap<>();
        List<Object> orders2 = deepCopyOrders(testData.orders);
        if (!orders2.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> lastOrder = (Map<String, Object>) orders2.get(orders2.size() - 1);
            @SuppressWarnings("unchecked")
            List<Object> lastItems = (List<Object>) lastOrder.get("items");
            if (lastItems == null) {
                lastItems = new ArrayList<>();
                lastOrder.put("items", lastItems);
            }
            Map<String, Object> extraItem = new LinkedHashMap<>();
            extraItem.put("price", 99999);
            lastItems.add(extraItem);
        }
        context2.put("orders", orders2);
        resolver.computeAggregations(context2, rootParams, childrenMap);

        // Verify: sibling (order[0]) aggregations unchanged
        if (testData.orders.size() > 1) {
            @SuppressWarnings("unchecked")
            Map<String, Object> order0After = (Map<String, Object>) orders2.get(0);
            for (Map.Entry<String, Object> entry : siblingAggs1.entrySet()) {
                Object afterVal = order0After.get(entry.getKey());
                if (entry.getValue() instanceof BigDecimal bd1 && afterVal instanceof BigDecimal bd2) {
                    assertEquals(0, bd1.compareTo(bd2),
                            "Sibling aggregation " + entry.getKey() + " should be unchanged");
                } else {
                    assertEquals(entry.getValue(), afterVal,
                            "Sibling aggregation " + entry.getKey() + " should be unchanged");
                }
            }
        }

        // Verify: outer $count unchanged (same number of orders)
        assertEquals(context1.get("orders.$count"), context2.get("orders.$count"),
                "Outer $count should be unchanged when only inner array is mutated");
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private List<Object> deepCopyOrders(List<Map<String, Object>> orders) {
        List<Object> copy = new ArrayList<>();
        for (Map<String, Object> order : orders) {
            Map<String, Object> orderCopy = new LinkedHashMap<>(order);
            Object items = orderCopy.get("items");
            if (items instanceof List<?> itemList) {
                List<Object> itemsCopy = new ArrayList<>();
                for (Object item : itemList) {
                    if (item instanceof Map<?, ?> itemMap) {
                        itemsCopy.add(new LinkedHashMap<>((Map<String, Object>) itemMap));
                    } else {
                        itemsCopy.add(item);
                    }
                }
                orderCopy.put("items", itemsCopy);
            }
            copy.add(orderCopy);
        }
        return copy;
    }

    // ═══════════════════════════════════════════════════════════════
    // Records & Generators
    // ═══════════════════════════════════════════════════════════════

    record ArrayTestData(List<Object> elements) {}
    record NestedArrayTestData(List<Map<String, Object>> orders) {}

    @Provide
    Arbitrary<ArrayTestData> randomArrayData() {
        Arbitrary<Object> element = Arbitraries.lazy(() -> {
            Arbitrary<Object> price = Arbitraries.oneOf(
                    Arbitraries.integers().between(-10000, 10000).map(i -> (Object) i),
                    Arbitraries.just(null)
            );
            Arbitrary<Object> name = Arbitraries.oneOf(
                    Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).map(s -> (Object) s),
                    Arbitraries.just(null)
            );
            return Combinators.combine(price, name).as((p, n) -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("price", p);
                map.put("name", n);
                return (Object) map;
            });
        });

        return element.list().ofMinSize(0).ofMaxSize(50)
                .map(ArrayTestData::new);
    }

    @Provide
    Arbitrary<NestedArrayTestData> nestedArrayData() {
        Arbitrary<Map<String, Object>> innerItem = Arbitraries.oneOf(
                Arbitraries.integers().between(-1000, 1000).map(i -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("price", (Object) i);
                    return m;
                }),
                Arbitraries.just(null).map(n -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("price", null);
                    return m;
                })
        );

        Arbitrary<Map<String, Object>> order = innerItem.list().ofMinSize(0).ofMaxSize(10)
                .map(items -> {
                    Map<String, Object> o = new LinkedHashMap<>();
                    o.put("items", new ArrayList<>(items));
                    return o;
                });

        return order.list().ofMinSize(1).ofMaxSize(5)
                .map(NestedArrayTestData::new);
    }
}
