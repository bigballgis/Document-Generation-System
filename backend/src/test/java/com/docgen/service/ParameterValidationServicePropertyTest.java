package com.docgen.service;

import com.docgen.entity.ExpressionType;
import com.docgen.entity.ParameterDefinition;
import com.docgen.exception.BusinessException;
import com.docgen.repository.ParameterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for {@link ParameterValidationService}.
 *
 * <p><b>Validates: Requirements 5.2, 5.5, 6.2, 6.3, 6.4, 6.5, 6.6, 6.10-6.23</b></p>
 */
@Tag("Feature: template-parameter-redesign")
class ParameterValidationServicePropertyTest {

    private static final Long TEMPLATE_ID = 1L;

    private ParameterValidationService createService(List<ParameterDefinition> params) {
        ParameterRepository repo = mock(ParameterRepository.class);
        ExpressionEngine engine = mock(ExpressionEngine.class);
        ObjectMapper mapper = new ObjectMapper();

        when(repo.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID)).thenReturn(params);

        // Default: expression engine returns a computed string
        when(engine.evaluate(anyString(), any(ExpressionType.class), anyMap()))
                .thenAnswer(inv -> {
                    Map<String, Object> ctx = inv.getArgument(2);
                    return "derived_from_" + ctx.size();
                });

        return new ParameterValidationService(repo, engine, mapper);
    }

    // ── Helpers ──

    private ParameterDefinition makeParam(Long id, String name, String paramType, String dataType,
                                           boolean required, String defaultValue, int sortOrder) {
        ParameterDefinition p = new ParameterDefinition();
        p.setId(id);
        p.setTemplateId(TEMPLATE_ID);
        p.setName(name);
        p.setParameterType(paramType);
        p.setDataType(dataType);
        p.setRequired(required);
        p.setDefaultValue(defaultValue);
        p.setSortOrder(sortOrder);
        return p;
    }

    private ParameterDefinition makeDerived(Long id, String name, int sortOrder, String exprText) {
        ParameterDefinition p = makeParam(id, name, "DERIVED", "STRING", false, null, sortOrder);
        p.setExpressionText(exprText);
        p.setExpressionType("JAVASCRIPT");
        return p;
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 16: DERIVED parameter evaluation order
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 16: For N DERIVED parameters evaluated in sort_order, each parameter's
     * expression has access to all REQUEST params and all previously evaluated DERIVED params.
     * The final context contains all REQUEST + DERIVED values.
     *
     * <p><b>Validates: Requirements 5.2, 5.5</b></p>
     */
    @Property(tries = 100)
    @Tag("Property 16: DERIVED parameter evaluation order")
    void derivedParamsEvaluatedInSortOrder_contextAccumulates(
            @ForAll @IntRange(min = 1, max = 5) int numDerived
    ) {
        // One REQUEST param + N DERIVED params
        List<ParameterDefinition> params = new ArrayList<>();
        params.add(makeParam(1L, "base", "REQUEST", "STRING", false, null, 0));

        for (int i = 0; i < numDerived; i++) {
            params.add(makeDerived((long) (10 + i), "derived_" + i, i + 1, "expr_" + i));
        }

        // Track context sizes seen by each evaluate call
        List<Integer> contextSizes = new ArrayList<>();
        ParameterRepository repo = mock(ParameterRepository.class);
        ExpressionEngine engine = mock(ExpressionEngine.class);
        when(repo.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID)).thenReturn(params);
        when(engine.evaluate(anyString(), any(ExpressionType.class), anyMap()))
                .thenAnswer(inv -> {
                    Map<String, Object> ctx = inv.getArgument(2);
                    contextSizes.add(ctx.size());
                    return "result_" + ctx.size();
                });

        ParameterValidationService svc = new ParameterValidationService(repo, engine, new ObjectMapper());
        Map<String, Object> input = Map.of("base", "hello");
        Map<String, Object> result = svc.validateAndBuildContext(TEMPLATE_ID, input);

        // Context sizes should be strictly increasing (each DERIVED sees more context)
        for (int i = 1; i < contextSizes.size(); i++) {
            assertTrue(contextSizes.get(i) > contextSizes.get(i - 1),
                    "Each DERIVED param should see more context than the previous one");
        }

        // Final context should contain base + all derived
        assertEquals(1 + numDerived, result.size(),
                "Final context should have 1 REQUEST + " + numDerived + " DERIVED params");
        assertTrue(result.containsKey("base"));
        for (int i = 0; i < numDerived; i++) {
            assertTrue(result.containsKey("derived_" + i),
                    "Context should contain derived_" + i);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 18: Required parameter handling with defaults
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 18: Missing required param WITH default → default used in context.
     *
     * <p><b>Validates: Requirements 6.2, 6.4</b></p>
     */
    @Property(tries = 100)
    @Tag("Property 18: Required parameter handling with defaults")
    void missingRequiredParam_withDefault_usesDefault(
            @ForAll("validParamNames") String paramName,
            @ForAll("nonEmptyStrings") String defaultVal
    ) {
        ParameterDefinition p = makeParam(1L, paramName, "REQUEST", "STRING", true, defaultVal, 0);
        ParameterValidationService svc = createService(List.of(p));

        Map<String, Object> result = svc.validateAndBuildContext(TEMPLATE_ID, Map.of());

        assertTrue(result.containsKey(paramName), "Context should contain param with default");
        assertEquals(defaultVal, result.get(paramName), "Default value should be used");
    }

    /**
     * Property 18: Missing required param WITHOUT default → validation fails.
     *
     * <p><b>Validates: Requirements 6.2</b></p>
     */
    @Property(tries = 100)
    @Tag("Property 18: Required parameter handling with defaults")
    void missingRequiredParam_withoutDefault_throwsError(
            @ForAll("validParamNames") String paramName
    ) {
        ParameterDefinition p = makeParam(1L, paramName, "REQUEST", "STRING", true, null, 0);
        ParameterValidationService svc = createService(List.of(p));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.validateAndBuildContext(TEMPLATE_ID, Map.of()));

        assertTrue(ex.getMessage().contains(paramName),
                "Error should reference the missing param name");
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 19: Type validation
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 19: Value type mismatch → validation fails with type_mismatch.
     *
     * <p><b>Validates: Requirements 6.3</b></p>
     */
    @Property(tries = 100)
    @Tag("Property 19: Type validation")
    void typeMismatch_throwsValidationError(
            @ForAll("typeMismatchPairs") TypeMismatchCase tc
    ) {
        ParameterDefinition p = makeParam(1L, "field", "REQUEST", tc.dataType, false, null, 0);
        ParameterValidationService svc = createService(List.of(p));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.validateAndBuildContext(TEMPLATE_ID, Map.of("field", tc.value)));

        assertTrue(ex.getMessage().contains("类型不匹配") || ex.getMessage().contains("type_mismatch")
                        || ex.getMessage().contains(tc.dataType),
                "Error should indicate type mismatch");
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 20: Extra parameters are ignored
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 20: Extra params not in Parameter_Table don't appear in context.
     *
     * <p><b>Validates: Requirements 6.5</b></p>
     */
    @Property(tries = 100)
    @Tag("Property 20: Extra parameters are ignored")
    void extraParams_notInContext(
            @ForAll @IntRange(min = 1, max = 5) int numExtra
    ) {
        ParameterDefinition defined = makeParam(1L, "defined", "REQUEST", "STRING", false, null, 0);
        ParameterValidationService svc = createService(List.of(defined));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("defined", "value");
        for (int i = 0; i < numExtra; i++) {
            input.put("extra_" + i, "should_be_ignored");
        }

        Map<String, Object> result = svc.validateAndBuildContext(TEMPLATE_ID, input);

        assertTrue(result.containsKey("defined"), "Defined param should be in context");
        for (int i = 0; i < numExtra; i++) {
            assertFalse(result.containsKey("extra_" + i),
                    "Extra param extra_" + i + " should NOT be in context");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 21: Validation rules enforcement
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 21: Each validation rule type violation → PARAMETER_VALIDATION_FAILED.
     *
     * <p><b>Validates: Requirements 6.10-6.18</b></p>
     */
    @Property(tries = 100)
    @Tag("Property 21: Validation rules enforcement")
    void validationRuleViolation_throwsError(
            @ForAll("ruleViolationCases") RuleViolationCase tc
    ) {
        ParameterDefinition p = makeParam(1L, "field", "REQUEST", tc.dataType, false, null, 0);
        p.setValidationRules(tc.rulesJson);
        ParameterValidationService svc = createService(List.of(p));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.validateAndBuildContext(TEMPLATE_ID, Map.of("field", tc.value)));

        assertEquals("PARAMETER_VALIDATION_FAILED", ex.getErrorCode(),
                "Error code should be PARAMETER_VALIDATION_FAILED");
    }

    /**
     * Property 21: custom_message in validation_rules → used as error detail.
     *
     * <p><b>Validates: Requirements 6.18</b></p>
     */
    @Property(tries = 100)
    @Tag("Property 21: Validation rules enforcement")
    void customMessage_usedInError(
            @ForAll("safeCustomMessages") String customMsg
    ) {
        ParameterDefinition p = makeParam(1L, "field", "REQUEST", "STRING", false, null, 0);
        p.setValidationRules("{\"min_length\": 999, \"custom_message\": \"" + customMsg + "\"}");
        ParameterValidationService svc = createService(List.of(p));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.validateAndBuildContext(TEMPLATE_ID, Map.of("field", "short")));

        assertTrue(ex.getMessage().contains(customMsg),
                "Error should contain custom_message: " + customMsg);
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 22: Multiple validation errors collected
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 22: N parameter violations → error message contains N error entries.
     *
     * <p><b>Validates: Requirements 6.19</b></p>
     */
    @Property(tries = 100)
    @Tag("Property 22: Multiple validation errors collected")
    void multipleErrors_allCollected(
            @ForAll @IntRange(min = 2, max = 6) int numParams
    ) {
        // Create N required params with no defaults — all missing → N errors
        List<ParameterDefinition> params = IntStream.range(0, numParams)
                .mapToObj(i -> makeParam((long) (i + 1), "param_" + i, "REQUEST", "STRING", true, null, i))
                .toList();

        ParameterValidationService svc = createService(params);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.validateAndBuildContext(TEMPLATE_ID, Map.of()));

        // Count error entries by counting param names in the message
        long errorCount = IntStream.range(0, numParams)
                .filter(i -> ex.getMessage().contains("param_" + i))
                .count();

        assertEquals(numParams, errorCount,
                "Should collect exactly " + numParams + " errors, got message: " + ex.getMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 23: Recursive nested validation
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 23: OBJECT nested validation — errors include full dot-notation path.
     *
     * <p><b>Validates: Requirements 6.20, 6.22</b></p>
     */
    @Property(tries = 100)
    @Tag("Property 23: Recursive nested validation")
    void objectNestedValidation_fullPathInErrors(
            @ForAll("validParamNames") String parentName,
            @ForAll("validParamNames") String childName
    ) {
        // Parent OBJECT with a required child that has no default
        ParameterDefinition parent = makeParam(1L, parentName, "REQUEST", "OBJECT", false, null, 0);
        ParameterDefinition child = makeParam(2L, childName, "REQUEST", "STRING", true, null, 1);
        child.setParentId(1L);

        ParameterValidationService svc = createService(List.of(parent, child));

        // Provide parent object but missing child field
        Map<String, Object> input = Map.of(parentName, Map.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.validateAndBuildContext(TEMPLATE_ID, input));

        String expectedPath = parentName + "." + childName;
        assertTrue(ex.getMessage().contains(expectedPath),
                "Error should contain full path '" + expectedPath + "', got: " + ex.getMessage());
    }

    /**
     * Property 23: ARRAY nested validation — errors include path with array index.
     *
     * <p><b>Validates: Requirements 6.21, 6.22, 6.23</b></p>
     */
    @Property(tries = 100)
    @Tag("Property 23: Recursive nested validation")
    void arrayNestedValidation_fullPathWithIndex(
            @ForAll @IntRange(min = 1, max = 4) int numElements
    ) {
        // ARRAY parent with a required STRING child
        ParameterDefinition parent = makeParam(1L, "items", "REQUEST", "ARRAY", false, null, 0);
        ParameterDefinition child = makeParam(2L, "name", "REQUEST", "STRING", true, null, 1);
        child.setParentId(1L);

        ParameterValidationService svc = createService(List.of(parent, child));

        // Provide array with empty objects (missing required "name")
        List<Map<String, Object>> elements = new ArrayList<>();
        for (int i = 0; i < numElements; i++) {
            elements.add(Map.of());
        }
        Map<String, Object> input = Map.of("items", elements);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.validateAndBuildContext(TEMPLATE_ID, input));

        // Each element should produce an error with items[i].name path
        for (int i = 0; i < numElements; i++) {
            String expectedPath = "items[" + i + "].name";
            assertTrue(ex.getMessage().contains(expectedPath),
                    "Error should contain path '" + expectedPath + "', got: " + ex.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Records & Generators
    // ═══════════════════════════════════════════════════════════════

    record TypeMismatchCase(String dataType, Object value) {}
    record RuleViolationCase(String dataType, String rulesJson, Object value) {}

    @Provide
    Arbitrary<String> validParamNames() {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyz")
                .ofMinLength(2).ofMaxLength(10)
                .map(s -> "p" + s);  // ensure starts with letter
    }

    @Provide
    Arbitrary<String> nonEmptyStrings() {
        return Arbitraries.strings().ascii()
                .ofMinLength(1).ofMaxLength(30)
                .filter(s -> !s.contains("\"") && !s.contains("\\") && !s.contains("\n"));
    }

    @Provide
    Arbitrary<String> safeCustomMessages() {
        // Only alphanumeric + spaces — safe for embedding in JSON without escaping
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ")
                .ofMinLength(3).ofMaxLength(30)
                .filter(s -> !s.isBlank());
    }

    @Provide
    Arbitrary<TypeMismatchCase> typeMismatchPairs() {
        return Arbitraries.oneOf(
                // STRING expects String, give Number
                Arbitraries.integers().between(1, 1000)
                        .map(i -> new TypeMismatchCase("STRING", i)),
                // NUMBER expects Number, give String
                Arbitraries.just(new TypeMismatchCase("NUMBER", "not_a_number")),
                // BOOLEAN expects Boolean, give String
                Arbitraries.just(new TypeMismatchCase("BOOLEAN", "not_bool")),
                // OBJECT expects Map, give String
                Arbitraries.just(new TypeMismatchCase("OBJECT", "not_object")),
                // ARRAY expects List, give String
                Arbitraries.just(new TypeMismatchCase("ARRAY", "not_array"))
        );
    }

    @Provide
    Arbitrary<RuleViolationCase> ruleViolationCases() {
        return Arbitraries.oneOf(
                // not_blank: blank string
                Arbitraries.just(new RuleViolationCase("STRING",
                        "{\"not_blank\": true}", "   ")),
                // min_length: too short
                Arbitraries.integers().between(5, 20)
                        .map(min -> new RuleViolationCase("STRING",
                                "{\"min_length\": " + min + "}", "ab")),
                // max_length: too long
                Arbitraries.just(new RuleViolationCase("STRING",
                        "{\"max_length\": 3}", "toolong")),
                // min: below minimum
                Arbitraries.integers().between(10, 100)
                        .map(min -> new RuleViolationCase("NUMBER",
                                "{\"min\": " + min + "}", min - 1)),
                // max: above maximum
                Arbitraries.integers().between(1, 50)
                        .map(max -> new RuleViolationCase("NUMBER",
                                "{\"max\": " + max + "}", max + 1)),
                // pattern: non-matching
                Arbitraries.just(new RuleViolationCase("STRING",
                        "{\"pattern\": \"^[A-Z].*\"}", "lowercase")),
                // enum_values: not in list
                Arbitraries.just(new RuleViolationCase("STRING",
                        "{\"enum_values\": [\"A\", \"B\", \"C\"]}", "D")),
                // min_items: too few
                Arbitraries.just(new RuleViolationCase("ARRAY",
                        "{\"min_items\": 3}", List.of("a"))),
                // max_items: too many
                Arbitraries.just(new RuleViolationCase("ARRAY",
                        "{\"max_items\": 1}", List.of("a", "b", "c")))
        );
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
