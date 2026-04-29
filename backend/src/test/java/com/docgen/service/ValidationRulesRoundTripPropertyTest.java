package com.docgen.service;

import com.docgen.entity.ParameterDefinition;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based test for validation_rules JSON round-trip.
 *
 * <p><b>Validates: Requirements 1.7</b></p>
 */
@Tag("Feature: template-parameter-redesign")
class ValidationRulesRoundTripPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Property 5: For any valid validation_rules Map, serializing to JSON string
     * and deserializing back produces an equivalent Map structure.
     */
    @Property(tries = 200)
    @Tag("Property 5: validation_rules round-trip")
    void validationRulesRoundTripPreservesEquality(
            @ForAll("validValidationRules") Map<String, Object> originalRules
    ) throws Exception {
        // Serialize: Map -> JSON string
        String json = objectMapper.writeValueAsString(originalRules);
        assertNotNull(json);
        assertFalse(json.isBlank());

        // Deserialize: JSON string -> Map
        Map<String, Object> deserialized = objectMapper.readValue(json, new TypeReference<>() {});

        // Round-trip equality
        assertEquals(originalRules.size(), deserialized.size(),
                "Deserialized map should have same number of entries");

        for (Map.Entry<String, Object> entry : originalRules.entrySet()) {
            assertTrue(deserialized.containsKey(entry.getKey()),
                    "Deserialized map should contain key: " + entry.getKey());

            Object originalVal = entry.getValue();
            Object deserializedVal = deserialized.get(entry.getKey());

            // Jackson may deserialize Integer as Integer, but we compare via toString for numeric types
            if (originalVal instanceof Number && deserializedVal instanceof Number) {
                assertEquals(((Number) originalVal).doubleValue(),
                        ((Number) deserializedVal).doubleValue(), 0.0001,
                        "Numeric value mismatch for key: " + entry.getKey());
            } else {
                assertEquals(originalVal, deserializedVal,
                        "Value mismatch for key: " + entry.getKey());
            }
        }
    }

    /**
     * Property 5: Round-trip through ParameterService's serialize/deserialize methods
     * (via ParameterDefinition entity) preserves the validation rules.
     */
    @Property(tries = 200)
    @Tag("Property 5: validation_rules round-trip")
    void validationRulesRoundTripThroughEntity(
            @ForAll("validValidationRules") Map<String, Object> originalRules
    ) throws Exception {
        // Simulate what ParameterService does: serialize to JSON string for entity storage
        String json = objectMapper.writeValueAsString(originalRules);

        // Store in entity
        ParameterDefinition entity = new ParameterDefinition();
        entity.setValidationRules(json);

        // Retrieve and deserialize
        String storedJson = entity.getValidationRules();
        Map<String, Object> deserialized = objectMapper.readValue(storedJson, new TypeReference<>() {});

        // Verify all keys preserved
        assertEquals(originalRules.keySet(), deserialized.keySet(),
                "All rule keys should be preserved through entity storage");

        // Verify values preserved
        for (String key : originalRules.keySet()) {
            Object orig = originalRules.get(key);
            Object deser = deserialized.get(key);
            if (orig instanceof Number && deser instanceof Number) {
                assertEquals(((Number) orig).doubleValue(), ((Number) deser).doubleValue(), 0.0001);
            } else {
                assertEquals(orig, deser, "Value for key '" + key + "' should be preserved");
            }
        }
    }


    @Provide
    Arbitrary<Map<String, Object>> validValidationRules() {
        // Generate random subsets of valid validation rule entries
        return Arbitraries.oneOf(
                // STRING-compatible rules
                stringRules(),
                // NUMBER-compatible rules
                numberRules(),
                // ARRAY-compatible rules
                arrayRules(),
                // Mixed rules (not_null + custom_message, applicable to all types)
                universalRules(),
                // Full STRING rule set
                fullStringRules()
        );
    }

    private Arbitrary<Map<String, Object>> stringRules() {
        return Combinators.combine(
                Arbitraries.of(true, false),                          // not_null
                Arbitraries.of(true, false),                          // not_blank
                Arbitraries.integers().between(0, 50),                // min_length
                Arbitraries.integers().between(50, 200),              // max_length
                Arbitraries.of("^[a-z]+$", "^\\d{3}$", ".*@.*")     // pattern
        ).as((notNull, notBlank, minLen, maxLen, pattern) -> {
            Map<String, Object> rules = new LinkedHashMap<>();
            rules.put("not_null", notNull);
            rules.put("not_blank", notBlank);
            rules.put("min_length", minLen);
            rules.put("max_length", maxLen);
            rules.put("pattern", pattern);
            return rules;
        });
    }

    private Arbitrary<Map<String, Object>> numberRules() {
        return Combinators.combine(
                Arbitraries.of(true, false),                // not_null
                Arbitraries.integers().between(-1000, 0),   // min
                Arbitraries.integers().between(0, 1000)     // max
        ).as((notNull, min, max) -> {
            Map<String, Object> rules = new LinkedHashMap<>();
            rules.put("not_null", notNull);
            rules.put("min", min);
            rules.put("max", max);
            return rules;
        });
    }

    private Arbitrary<Map<String, Object>> arrayRules() {
        return Combinators.combine(
                Arbitraries.of(true, false),              // not_null
                Arbitraries.integers().between(0, 5),     // min_items
                Arbitraries.integers().between(5, 100)    // max_items
        ).as((notNull, minItems, maxItems) -> {
            Map<String, Object> rules = new LinkedHashMap<>();
            rules.put("not_null", notNull);
            rules.put("min_items", minItems);
            rules.put("max_items", maxItems);
            return rules;
        });
    }

    private Arbitrary<Map<String, Object>> universalRules() {
        return Combinators.combine(
                Arbitraries.of(true, false),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
        ).as((notNull, customMsg) -> {
            Map<String, Object> rules = new LinkedHashMap<>();
            rules.put("not_null", notNull);
            rules.put("custom_message", customMsg);
            return rules;
        });
    }

    private Arbitrary<Map<String, Object>> fullStringRules() {
        return Combinators.combine(
                Arbitraries.of(true, false),                          // not_null
                Arbitraries.of(true, false),                          // not_blank
                Arbitraries.integers().between(1, 10),                // min_length
                Arbitraries.integers().between(10, 100),              // max_length
                Arbitraries.of(List.of("A", "B"), List.of("X", "Y", "Z"))  // enum_values
        ).as((notNull, notBlank, minLen, maxLen, enumVals) -> {
            Map<String, Object> rules = new LinkedHashMap<>();
            rules.put("not_null", notNull);
            rules.put("not_blank", notBlank);
            rules.put("min_length", minLen);
            rules.put("max_length", maxLen);
            rules.put("enum_values", enumVals);
            rules.put("custom_message", "自定义错误消息");
            return rules;
        });
    }
}

