package com.docgen.property;

import com.docgen.dto.TransformRule;
import com.docgen.service.ResponseTransformerService;
import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for response data extraction correctness.
 *
 * <p><b>Validates: Requirements 42.1</b></p>
 */
@Tag("feature-low-code-document-generation-system-property-16")
class ResponseTransformerPropertyTest {

    private final ResponseTransformerService service = new ResponseTransformerService();

    /**
     * Property 16: For any JSON object and a valid JSONPath expression targeting a known
     * top-level field, the extraction result should match the expected value.
     */
    @Property(tries = 200)
    void jsonPathExtractsTopLevelFieldCorrectly(
            @ForAll("topLevelJsonData") JsonWithPath testData
    ) {
        TransformRule rule = new TransformRule("extract", TransformRule.RuleType.JSONPATH, testData.jsonPath);
        Map<String, Object> result = service.transform(testData.json, List.of(rule));

        assertTrue(result.containsKey("extract"), "Result should contain the rule key");
        assertEquals(testData.expectedValue, result.get("extract"),
                "JSONPath '" + testData.jsonPath + "' should extract the expected value");
    }

    /**
     * Property 16: For any JSON object with a nested structure, JSONPath should correctly
     * extract nested field values.
     */
    @Property(tries = 200)
    void jsonPathExtractsNestedFieldCorrectly(
            @ForAll("nestedJsonData") JsonWithPath testData
    ) {
        TransformRule rule = new TransformRule("nested", TransformRule.RuleType.JSONPATH, testData.jsonPath);
        Map<String, Object> result = service.transform(testData.json, List.of(rule));

        assertTrue(result.containsKey("nested"), "Result should contain the rule key");
        assertEquals(testData.expectedValue, result.get("nested"),
                "JSONPath '" + testData.jsonPath + "' should extract the nested value");
    }

    /**
     * Property 16: For any JSON object containing an array, JSONPath should correctly
     * extract array elements by index.
     */
    @Property(tries = 200)
    void jsonPathExtractsArrayElementCorrectly(
            @ForAll("arrayJsonData") JsonWithPath testData
    ) {
        TransformRule rule = new TransformRule("arrElem", TransformRule.RuleType.JSONPATH, testData.jsonPath);
        Map<String, Object> result = service.transform(testData.json, List.of(rule));

        assertTrue(result.containsKey("arrElem"), "Result should contain the rule key");
        assertEquals(testData.expectedValue, result.get("arrElem"),
                "JSONPath '" + testData.jsonPath + "' should extract the array element");
    }

    /**
     * Property 16: Multiple JSONPath rules applied to the same object should each
     * independently extract the correct value.
     */
    @Property(tries = 100)
    void multipleJsonPathRulesExtractIndependently(
            @ForAll("multiFieldJsonData") MultiFieldData testData
    ) {
        List<TransformRule> rules = new ArrayList<>();
        rules.add(new TransformRule("field1", TransformRule.RuleType.JSONPATH, testData.path1));
        rules.add(new TransformRule("field2", TransformRule.RuleType.JSONPATH, testData.path2));

        Map<String, Object> result = service.transform(testData.json, rules);

        assertEquals(testData.expected1, result.get("field1"),
                "First JSONPath should extract correctly");
        assertEquals(testData.expected2, result.get("field2"),
                "Second JSONPath should extract correctly");
    }


    record JsonWithPath(Map<String, Object> json, String jsonPath, Object expectedValue) {}
    record MultiFieldData(Map<String, Object> json, String path1, Object expected1,
                          String path2, Object expected2) {}


    @Provide
    Arbitrary<JsonWithPath> topLevelJsonData() {
        return Arbitraries.of("str", "num", "bool").flatMap(fieldType -> {
            Arbitrary<String> keyArb = Arbitraries.strings()
                    .alpha().ofMinLength(1).ofMaxLength(10);
            Arbitrary<Object> valueArb = primitiveValues(fieldType);

            return Combinators.combine(keyArb, valueArb).as((key, value) -> {
                Map<String, Object> json = new LinkedHashMap<>();
                json.put(key, value);
                json.put("extra", "noise");
                return new JsonWithPath(json, "$." + key, value);
            });
        });
    }

    @Provide
    Arbitrary<JsonWithPath> nestedJsonData() {
        Arbitrary<String> outerKeyArb = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8);
        Arbitrary<String> innerKeyArb = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8);
        Arbitrary<Object> valueArb = Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20).map(s -> (Object) s),
                Arbitraries.integers().between(-1000, 1000).map(i -> (Object) i),
                Arbitraries.of(true, false).map(b -> (Object) b)
        );

        return Combinators.combine(outerKeyArb, innerKeyArb, valueArb).as((outerKey, innerKey, value) -> {
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put(innerKey, value);
            inner.put("padding", 42);

            Map<String, Object> json = new LinkedHashMap<>();
            json.put(outerKey, inner);
            json.put("other", "data");

            return new JsonWithPath(json, "$." + outerKey + "." + innerKey, value);
        });
    }

    @Provide
    Arbitrary<JsonWithPath> arrayJsonData() {
        Arbitrary<String> keyArb = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8);
        Arbitrary<List<Object>> listArb = Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).map(s -> (Object) s),
                Arbitraries.integers().between(-500, 500).map(i -> (Object) i)
        ).list().ofMinSize(1).ofMaxSize(10);

        return Combinators.combine(keyArb, listArb).flatAs((key, list) -> {
            Arbitrary<Integer> indexArb = Arbitraries.integers().between(0, list.size() - 1);
            return indexArb.map(index -> {
                Map<String, Object> json = new LinkedHashMap<>();
                json.put(key, list);
                return new JsonWithPath(json, "$." + key + "[" + index + "]", list.get(index));
            });
        });
    }

    @Provide
    Arbitrary<MultiFieldData> multiFieldJsonData() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(8),
                Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(8),
                Arbitraries.oneOf(
                        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(15).map(s -> (Object) s),
                        Arbitraries.integers().between(-100, 100).map(i -> (Object) i)
                ),
                Arbitraries.oneOf(
                        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(15).map(s -> (Object) s),
                        Arbitraries.integers().between(-100, 100).map(i -> (Object) i)
                )
        ).as((key1, key2, val1, val2) -> {
            // Ensure distinct keys
            String k2 = key1.equals(key2) ? key2 + "x" : key2;
            Map<String, Object> json = new LinkedHashMap<>();
            json.put(key1, val1);
            json.put(k2, val2);
            return new MultiFieldData(json, "$." + key1, val1, "$." + k2, val2);
        });
    }

    private Arbitrary<Object> primitiveValues(String type) {
        return switch (type) {
            case "str" -> Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30).map(s -> (Object) s);
            case "num" -> Arbitraries.integers().between(-10000, 10000).map(i -> (Object) i);
            case "bool" -> Arbitraries.of(true, false).map(b -> (Object) b);
            default -> Arbitraries.just("default");
        };
    }
}

