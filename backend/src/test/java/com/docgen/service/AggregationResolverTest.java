package com.docgen.service;

import com.docgen.entity.ParameterDefinition;
import com.docgen.repository.ParameterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link AggregationResolver}.
 * Tests edge cases: empty array, single element, all-null NUMBER fields, mixed types, nested arrays.
 */
class AggregationResolverTest {

    private static final Long TEMPLATE_ID = 1L;
    private AggregationResolver resolver;

    @BeforeEach
    void setUp() {
        ParameterRepository repo = mock(ParameterRepository.class);
        resolver = new AggregationResolver(repo);
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

    @Test
    @DisplayName("Empty array: $count=0, $sum=0, $avg=0, $min=null, $max=null, $join='', $first=null, $last=null")
    void emptyArray_safeDefaults() {
        ParameterDefinition arrayParam = makeParam(1L, null, "items", "REQUEST", "ARRAY", 0);
        ParameterDefinition priceChild = makeParam(2L, 1L, "price", "REQUEST", "NUMBER", 0);
        ParameterDefinition nameChild = makeParam(3L, 1L, "name", "REQUEST", "STRING", 1);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("items", new ArrayList<>());

        resolver.computeAggregations(context,
                List.of(arrayParam),
                Map.of(1L, List.of(priceChild, nameChild)));

        assertEquals(0, context.get("items.$count"));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) context.get("items.$sum_price")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) context.get("items.$avg_price")));
        assertNull(context.get("items.$min_price"));
        assertNull(context.get("items.$max_price"));
        assertEquals("", context.get("items.$join_name"));
        assertNull(context.get("items.$first"));
        assertNull(context.get("items.$last"));
    }

    @Test
    @DisplayName("Single element array: all aggregations reflect that single element")
    void singleElement() {
        ParameterDefinition arrayParam = makeParam(1L, null, "items", "REQUEST", "ARRAY", 0);
        ParameterDefinition priceChild = makeParam(2L, 1L, "price", "REQUEST", "NUMBER", 0);
        ParameterDefinition nameChild = makeParam(3L, 1L, "name", "REQUEST", "STRING", 1);

        Map<String, Object> element = new LinkedHashMap<>();
        element.put("price", 100);
        element.put("name", "Widget");

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("items", List.of(element));

        resolver.computeAggregations(context,
                List.of(arrayParam),
                Map.of(1L, List.of(priceChild, nameChild)));

        assertEquals(1, context.get("items.$count"));
        assertEquals(0, new BigDecimal("100").compareTo((BigDecimal) context.get("items.$sum_price")));
        assertEquals(0, new BigDecimal("100.00").compareTo((BigDecimal) context.get("items.$avg_price")));
        assertEquals(0, new BigDecimal("100").compareTo((BigDecimal) context.get("items.$min_price")));
        assertEquals(0, new BigDecimal("100").compareTo((BigDecimal) context.get("items.$max_price")));
        assertEquals("Widget", context.get("items.$join_name"));
        assertSame(element, context.get("items.$first"));
        assertSame(element, context.get("items.$last"));
    }

    @Test
    @DisplayName("All-null NUMBER fields: $sum=0, $avg=0, $min=null, $max=null")
    void allNullNumberFields() {
        ParameterDefinition arrayParam = makeParam(1L, null, "items", "REQUEST", "ARRAY", 0);
        ParameterDefinition priceChild = makeParam(2L, 1L, "price", "REQUEST", "NUMBER", 0);

        Map<String, Object> e1 = new LinkedHashMap<>();
        e1.put("price", null);
        Map<String, Object> e2 = new LinkedHashMap<>();
        e2.put("price", null);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("items", List.of(e1, e2));

        resolver.computeAggregations(context,
                List.of(arrayParam),
                Map.of(1L, List.of(priceChild)));

        assertEquals(2, context.get("items.$count"));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) context.get("items.$sum_price")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) context.get("items.$avg_price")));
        assertNull(context.get("items.$min_price"));
        assertNull(context.get("items.$max_price"));
    }

    @Test
    @DisplayName("Mixed null and non-null NUMBER values: aggregations exclude nulls")
    void mixedNullAndNonNull() {
        ParameterDefinition arrayParam = makeParam(1L, null, "items", "REQUEST", "ARRAY", 0);
        ParameterDefinition priceChild = makeParam(2L, 1L, "price", "REQUEST", "NUMBER", 0);

        Map<String, Object> e1 = new LinkedHashMap<>();
        e1.put("price", 100);
        Map<String, Object> e2 = new LinkedHashMap<>();
        e2.put("price", null);
        Map<String, Object> e3 = new LinkedHashMap<>();
        e3.put("price", 200);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("items", List.of(e1, e2, e3));

        resolver.computeAggregations(context,
                List.of(arrayParam),
                Map.of(1L, List.of(priceChild)));

        assertEquals(3, context.get("items.$count"));
        assertEquals(0, new BigDecimal("300").compareTo((BigDecimal) context.get("items.$sum_price")));
        // avg = 300 / 2 = 150.00
        assertEquals(0, new BigDecimal("150.00").compareTo((BigDecimal) context.get("items.$avg_price")));
        assertEquals(0, new BigDecimal("100").compareTo((BigDecimal) context.get("items.$min_price")));
        assertEquals(0, new BigDecimal("200").compareTo((BigDecimal) context.get("items.$max_price")));
    }

    @Test
    @DisplayName("$avg rounds to 2 decimal places with HALF_UP")
    void avgRounding() {
        ParameterDefinition arrayParam = makeParam(1L, null, "items", "REQUEST", "ARRAY", 0);
        ParameterDefinition priceChild = makeParam(2L, 1L, "price", "REQUEST", "NUMBER", 0);

        // 10 + 20 + 30 = 60, avg = 60/3 = 20.00
        // 10 + 20 + 33 = 63, avg = 63/3 = 21.00
        // 10 + 20 + 31 = 61, avg = 61/3 = 20.33 (rounds from 20.333...)
        Map<String, Object> e1 = new LinkedHashMap<>();
        e1.put("price", 10);
        Map<String, Object> e2 = new LinkedHashMap<>();
        e2.put("price", 20);
        Map<String, Object> e3 = new LinkedHashMap<>();
        e3.put("price", 31);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("items", List.of(e1, e2, e3));

        resolver.computeAggregations(context,
                List.of(arrayParam),
                Map.of(1L, List.of(priceChild)));

        BigDecimal avg = (BigDecimal) context.get("items.$avg_price");
        assertEquals(0, new BigDecimal("20.33").compareTo(avg),
                "avg should be 20.33 (61/3 rounded HALF_UP to 2dp)");
    }

    @Test
    @DisplayName("Nested ARRAY: inner aggregations computed before outer")
    void nestedArrayAggregations() {
        // orders (ARRAY) -> items (ARRAY) -> price (NUMBER)
        ParameterDefinition ordersParam = makeParam(1L, null, "orders", "REQUEST", "ARRAY", 0);
        ParameterDefinition itemsChild = makeParam(2L, 1L, "items", "REQUEST", "ARRAY", 0);
        ParameterDefinition priceGrandChild = makeParam(3L, 2L, "price", "REQUEST", "NUMBER", 0);

        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("price", 100);
        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("price", 200);

        Map<String, Object> order1 = new LinkedHashMap<>();
        order1.put("items", new ArrayList<>(List.of(item1, item2)));

        Map<String, Object> item3 = new LinkedHashMap<>();
        item3.put("price", 50);

        Map<String, Object> order2 = new LinkedHashMap<>();
        order2.put("items", new ArrayList<>(List.of(item3)));

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("orders", new ArrayList<>(List.of(order1, order2)));

        resolver.computeAggregations(context,
                List.of(ordersParam),
                Map.of(1L, List.of(itemsChild), 2L, List.of(priceGrandChild)));

        // Outer: orders.$count = 2
        assertEquals(2, context.get("orders.$count"));

        // Inner: order1.items.$count = 2, order1.items.$sum_price = 300
        assertEquals(2, order1.get("items.$count"));
        assertEquals(0, new BigDecimal("300").compareTo((BigDecimal) order1.get("items.$sum_price")));

        // Inner: order2.items.$count = 1, order2.items.$sum_price = 50
        assertEquals(1, order2.get("items.$count"));
        assertEquals(0, new BigDecimal("50").compareTo((BigDecimal) order2.get("items.$sum_price")));
    }

    @Test
    @DisplayName("DERIVED children included in aggregation computation")
    void derivedChildrenIncluded() {
        ParameterDefinition arrayParam = makeParam(1L, null, "items", "REQUEST", "ARRAY", 0);
        ParameterDefinition priceChild = makeParam(2L, 1L, "price", "REQUEST", "NUMBER", 0);
        ParameterDefinition subtotalChild = makeParam(3L, 1L, "subtotal", "DERIVED", "NUMBER", 1);

        Map<String, Object> e1 = new LinkedHashMap<>();
        e1.put("price", 100);
        e1.put("subtotal", 200); // Already computed in Step 2
        Map<String, Object> e2 = new LinkedHashMap<>();
        e2.put("price", 50);
        e2.put("subtotal", 150);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("items", List.of(e1, e2));

        resolver.computeAggregations(context,
                List.of(arrayParam),
                Map.of(1L, List.of(priceChild, subtotalChild)));

        // subtotal is DERIVED but NUMBER, so $sum_subtotal should be computed
        assertEquals(0, new BigDecimal("350").compareTo((BigDecimal) context.get("items.$sum_subtotal")));
        assertEquals(0, new BigDecimal("175.00").compareTo((BigDecimal) context.get("items.$avg_subtotal")));
    }

    @Test
    @DisplayName("No ARRAY parameters: computeAggregations is a no-op")
    void noArrayParams_noOp() {
        ParameterDefinition stringParam = makeParam(1L, null, "title", "REQUEST", "STRING", 0);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("title", "Hello");

        resolver.computeAggregations(context, List.of(stringParam), Map.of());

        // Context should only contain the original entry
        assertEquals(1, context.size());
        assertEquals("Hello", context.get("title"));
    }

    @Test
    @DisplayName("$join with mixed null and non-null STRING values")
    void joinWithNulls() {
        ParameterDefinition arrayParam = makeParam(1L, null, "items", "REQUEST", "ARRAY", 0);
        ParameterDefinition nameChild = makeParam(2L, 1L, "name", "REQUEST", "STRING", 0);

        Map<String, Object> e1 = new LinkedHashMap<>();
        e1.put("name", "Alice");
        Map<String, Object> e2 = new LinkedHashMap<>();
        e2.put("name", null);
        Map<String, Object> e3 = new LinkedHashMap<>();
        e3.put("name", "Bob");

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("items", List.of(e1, e2, e3));

        resolver.computeAggregations(context,
                List.of(arrayParam),
                Map.of(1L, List.of(nameChild)));

        assertEquals("Alice, Bob", context.get("items.$join_name"));
    }
}
