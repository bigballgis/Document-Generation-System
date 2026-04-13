package com.docgen.service;

import com.docgen.dto.TransformRule;
import com.docgen.dto.TransformRule.RuleType;
import com.docgen.dto.TransformRule.SortDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ResponseTransformerServiceTest {

    private ResponseTransformerService service;

    @BeforeEach
    void setUp() {
        service = new ResponseTransformerService();
    }

    // ── JSONPath ──

    @Test
    void jsonPath_extractsSimpleField() {
        Map<String, Object> response = Map.of("name", "Alice", "age", 30);
        List<TransformRule> rules = List.of(
                new TransformRule("userName", RuleType.JSONPATH, "$.name")
        );
        Map<String, Object> result = service.transform(response, rules);
        assertEquals("Alice", result.get("userName"));
    }

    @Test
    void jsonPath_extractsNestedField() {
        Map<String, Object> response = Map.of(
                "user", Map.of("address", Map.of("city", "Shanghai"))
        );
        List<TransformRule> rules = List.of(
                new TransformRule("city", RuleType.JSONPATH, "$.user.address.city")
        );
        Map<String, Object> result = service.transform(response, rules);
        assertEquals("Shanghai", result.get("city"));
    }

    @Test
    void jsonPath_extractsArrayElement() {
        Map<String, Object> response = Map.of(
                "items", List.of("a", "b", "c")
        );
        List<TransformRule> rules = List.of(
                new TransformRule("second", RuleType.JSONPATH, "$.items[1]")
        );
        Map<String, Object> result = service.transform(response, rules);
        assertEquals("b", result.get("second"));
    }

    @Test
    void jsonPath_invalidExpression_returnsError() {
        Map<String, Object> response = Map.of("name", "Alice");
        List<TransformRule> rules = List.of(
                new TransformRule("missing", RuleType.JSONPATH, "$.nonexistent.deep")
        );
        Map<String, Object> result = service.transform(response, rules);
        Object value = result.get("missing");
        assertInstanceOf(Map.class, value);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) value;
        assertEquals("missing", error.get("_ruleName"));
        assertNotNull(error.get("_error"));
    }

    // ── XPath ──

    @Test
    void xpath_extractsSingleElement() {
        String xml = "<root><name>Bob</name><age>25</age></root>";
        List<TransformRule> rules = List.of(
                new TransformRule("name", RuleType.XPATH, "/root/name")
        );
        Map<String, Object> result = service.transform(xml, rules);
        assertEquals("Bob", result.get("name"));
    }

    @Test
    void xpath_extractsMultipleElements() {
        String xml = "<root><item>A</item><item>B</item><item>C</item></root>";
        List<TransformRule> rules = List.of(
                new TransformRule("items", RuleType.XPATH, "/root/item")
        );
        Map<String, Object> result = service.transform(xml, rules);
        @SuppressWarnings("unchecked")
        List<String> items = (List<String>) result.get("items");
        assertEquals(List.of("A", "B", "C"), items);
    }

    @Test
    void xpath_invalidXml_returnsError() {
        List<TransformRule> rules = List.of(
                new TransformRule("data", RuleType.XPATH, "/root/name")
        );
        Map<String, Object> result = service.transform("not xml", rules);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) result.get("data");
        assertEquals("data", error.get("_ruleName"));
        assertNotNull(error.get("_error"));
    }

    // ── Flatten ──

    @Test
    void flatten_flattensNestedMap() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("name", "Alice");
        response.put("address", Map.of("city", "Beijing", "zip", "100000"));

        List<TransformRule> rules = List.of(
                new TransformRule("flat", RuleType.FLATTEN, null)
        );
        Map<String, Object> result = service.transform(response, rules);
        @SuppressWarnings("unchecked")
        Map<String, Object> flat = (Map<String, Object>) result.get("flat");
        assertEquals("Alice", flat.get("name"));
        assertEquals("Beijing", flat.get("address.city"));
        assertEquals("100000", flat.get("address.zip"));
    }

    @Test
    void flatten_deeplyNested() {
        Map<String, Object> response = Map.of(
                "a", Map.of("b", Map.of("c", "deep"))
        );
        List<TransformRule> rules = List.of(
                new TransformRule("flat", RuleType.FLATTEN, null)
        );
        Map<String, Object> result = service.transform(response, rules);
        @SuppressWarnings("unchecked")
        Map<String, Object> flat = (Map<String, Object>) result.get("flat");
        assertEquals("deep", flat.get("a.b.c"));
    }

    @Test
    void flatten_nonMapInput_returnsError() {
        List<TransformRule> rules = List.of(
                new TransformRule("flat", RuleType.FLATTEN, null)
        );
        Map<String, Object> result = service.transform(List.of(1, 2), rules);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) result.get("flat");
        assertEquals("flat", error.get("_ruleName"));
    }

    // ── Group By ──

    @Test
    void groupBy_groupsByField() {
        List<Map<String, Object>> response = List.of(
                Map.of("dept", "eng", "name", "Alice"),
                Map.of("dept", "eng", "name", "Bob"),
                Map.of("dept", "hr", "name", "Carol")
        );
        List<TransformRule> rules = List.of(
                new TransformRule("grouped", RuleType.GROUP_BY, "dept")
        );
        Map<String, Object> result = service.transform(response, rules);
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> grouped =
                (Map<String, List<Map<String, Object>>>) result.get("grouped");
        assertEquals(2, grouped.get("eng").size());
        assertEquals(1, grouped.get("hr").size());
    }

    @Test
    void groupBy_missingField_groupsAsNull() {
        List<Map<String, Object>> response = List.of(
                Map.of("name", "Alice"),
                Map.of("dept", "eng", "name", "Bob")
        );
        List<TransformRule> rules = List.of(
                new TransformRule("grouped", RuleType.GROUP_BY, "dept")
        );
        Map<String, Object> result = service.transform(response, rules);
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> grouped =
                (Map<String, List<Map<String, Object>>>) result.get("grouped");
        assertTrue(grouped.containsKey("null"));
        assertTrue(grouped.containsKey("eng"));
    }

    @Test
    void groupBy_nonListInput_returnsError() {
        List<TransformRule> rules = List.of(
                new TransformRule("grouped", RuleType.GROUP_BY, "dept")
        );
        Map<String, Object> result = service.transform(Map.of("a", 1), rules);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) result.get("grouped");
        assertEquals("grouped", error.get("_ruleName"));
    }

    // ── Sort ──

    @Test
    void sort_ascendingByDefault() {
        List<Map<String, Object>> response = new ArrayList<>(List.of(
                Map.of("name", "Charlie", "age", 30),
                Map.of("name", "Alice", "age", 25),
                Map.of("name", "Bob", "age", 28)
        ));
        List<TransformRule> rules = List.of(
                new TransformRule("sorted", RuleType.SORT, "name")
        );
        Map<String, Object> result = service.transform(response, rules);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sorted = (List<Map<String, Object>>) result.get("sorted");
        assertEquals("Alice", sorted.get(0).get("name"));
        assertEquals("Bob", sorted.get(1).get("name"));
        assertEquals("Charlie", sorted.get(2).get("name"));
    }

    @Test
    void sort_descending() {
        List<Map<String, Object>> response = new ArrayList<>(List.of(
                Map.of("name", "Alice", "score", 80),
                Map.of("name", "Bob", "score", 95),
                Map.of("name", "Carol", "score", 70)
        ));
        List<TransformRule> rules = List.of(
                new TransformRule("sorted", RuleType.SORT, "score", SortDirection.DESC)
        );
        Map<String, Object> result = service.transform(response, rules);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sorted = (List<Map<String, Object>>) result.get("sorted");
        assertEquals(95, sorted.get(0).get("score"));
        assertEquals(80, sorted.get(1).get("score"));
        assertEquals(70, sorted.get(2).get("score"));
    }

    @Test
    void sort_nonListInput_returnsError() {
        List<TransformRule> rules = List.of(
                new TransformRule("sorted", RuleType.SORT, "name", SortDirection.ASC)
        );
        Map<String, Object> result = service.transform("not a list", rules);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) result.get("sorted");
        assertEquals("sorted", error.get("_ruleName"));
    }

    // ── General ──

    @Test
    void transform_nullRules_returnsEmptyMap() {
        Map<String, Object> result = service.transform(Map.of("a", 1), null);
        assertTrue(result.isEmpty());
    }

    @Test
    void transform_emptyRules_returnsEmptyMap() {
        Map<String, Object> result = service.transform(Map.of("a", 1), List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void transform_multipleRules_appliedInOrder() {
        Map<String, Object> response = Map.of("name", "Alice", "age", 30);
        List<TransformRule> rules = List.of(
                new TransformRule("extractName", RuleType.JSONPATH, "$.name"),
                new TransformRule("extractAge", RuleType.JSONPATH, "$.age")
        );
        Map<String, Object> result = service.transform(response, rules);
        assertEquals("Alice", result.get("extractName"));
        assertEquals(30, result.get("extractAge"));
    }

    @Test
    void transform_failedRuleContainsRuleNameAndError() {
        Map<String, Object> response = Map.of("name", "Alice");
        List<TransformRule> rules = List.of(
                new TransformRule("badRule", RuleType.JSONPATH, "$.missing.path")
        );
        Map<String, Object> result = service.transform(response, rules);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) result.get("badRule");
        assertEquals("badRule", error.get("_ruleName"));
        assertNotNull(error.get("_error"));
        assertFalse(((String) error.get("_error")).isEmpty());
    }
}
