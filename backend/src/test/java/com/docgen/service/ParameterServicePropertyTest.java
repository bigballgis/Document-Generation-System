package com.docgen.service;

import com.docgen.dto.CreateParameterRequest;
import com.docgen.dto.ExpressionValidationResult;
import com.docgen.entity.ExpressionType;
import com.docgen.entity.ParameterDefinition;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for ParameterService.
 *
 * <p><b>Validates: Requirements 1.2, 1.3, 1.4, 1.7, 1.8, 1.9, 1.10, 1.15, 1.16, 1.17, 2.5, 2.6, 2.9, 2.12, 5.6</b></p>
 */
@Tag("feature-template-parameter-redesign")
class ParameterServicePropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Validates: Requirements 1.3, 1.4, 2.6

    /**
     * Property 1: DERIVED parameters must have non-empty expressionText.
     * Creating a DERIVED parameter without expression should be rejected.
     */
    @Property(tries = 100)
    @Tag("property-1-parameter-type-determines-expression-presence")
    void derivedParameterRequiresExpression(
            @ForAll("emptyOrNullExpressions") String expressionText
    ) {
        ParameterService service = createServiceWithMocks();

        CreateParameterRequest req = new CreateParameterRequest(
                "validName", "DERIVED", "STRING", false, null, null, 0,
                expressionText, "JAVASCRIPT", null, null
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createParameter(1L, req));
        assertEquals(ErrorCode.PARAMETER_EXPRESSION_REQUIRED, ex.getErrorCode());
    }

    /**
     * Property 1: REQUEST parameters must not have expressionText.
     * Creating a REQUEST parameter with expression should be rejected.
     */
    @Property(tries = 100)
    @Tag("property-1-parameter-type-determines-expression-presence")
    void requestParameterRejectsExpression(
            @ForAll("nonEmptyExpressions") String expressionText
    ) {
        ParameterService service = createServiceWithMocks();

        CreateParameterRequest req = new CreateParameterRequest(
                "validName", "REQUEST", "STRING", false, null, null, 0,
                expressionText, null, null, null
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createParameter(1L, req));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    // Validates: Requirements 1.2, 2.5

    /**
     * Property 2: Creating a parameter with a name that already exists in the same scope
     * (templateId + parentId) should throw PARAMETER_DUPLICATE_NAME.
     */
    @Property(tries = 100)
    @Tag("property-2-duplicate-name-rejection-within-scope")
    void duplicateNameWithinScopeIsRejected(
            @ForAll("validParamNames") String name,
            @ForAll("optionalParentIds") Long parentId
    ) {
        ParameterRepository repo = mock(ParameterRepository.class);
        ExpressionEngine engine = mock(ExpressionEngine.class);
        ParameterService service = new ParameterService(repo, mock(TemplateRepository.class), mock(TemplateScanService.class), engine, objectMapper, mock(AuditLogService.class), mock(AggregationResolver.class));

        Long templateId = 1L;

        // Simulate name already exists
        if (parentId == null) {
            when(repo.existsByTemplateIdAndParentIdIsNullAndName(templateId, name)).thenReturn(true);
        } else {
            when(repo.existsByTemplateIdAndParentIdAndName(templateId, parentId, name)).thenReturn(true);
            // Mock parent exists with valid type
            ParameterDefinition parent = makeParam(parentId, templateId, null, "parent", "OBJECT");
            when(repo.findById(parentId)).thenReturn(Optional.of(parent));
        }

        CreateParameterRequest req = new CreateParameterRequest(
                name, "REQUEST", "STRING", false, null, null, 0,
                null, null, null, parentId
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createParameter(templateId, req));
        assertEquals(ErrorCode.PARAMETER_DUPLICATE_NAME, ex.getErrorCode());
    }

    // Validates: Requirements 1.8, 1.9, 1.10

    /**
     * Property 3: When min > max for any range pair, validation should reject.
     */
    @Property(tries = 100)
    @Tag("property-3-range-constraint-consistency")
    void rangeConstraintRejectsMinGreaterThanMax(
            @ForAll("invalidRangePairs") RangePair pair
    ) {
        ParameterService service = createServiceWithMocks();

        Map<String, Object> rules = new HashMap<>();
        rules.put(pair.minKey, pair.minVal);
        rules.put(pair.maxKey, pair.maxVal);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateValidationRules(pair.dataType, rules));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    /**
     * Property 3: When min <= max for any range pair, validation should accept.
     */
    @Property(tries = 100)
    @Tag("property-3-range-constraint-consistency")
    void rangeConstraintAcceptsMinLessOrEqualMax(
            @ForAll("validRangePairs") RangePair pair
    ) {
        ParameterService service = createServiceWithMocks();

        Map<String, Object> rules = new HashMap<>();
        rules.put(pair.minKey, pair.minVal);
        rules.put(pair.maxKey, pair.maxVal);

        assertDoesNotThrow(() -> service.validateValidationRules(pair.dataType, rules));
    }

    // Validates: Requirements 2.9

    /**
     * Property 4: Incompatible (dataType, ruleType) pairs should be rejected.
     */
    @Property(tries = 100)
    @Tag("property-4-validation-rules-compatibility-with-data_type")
    void incompatibleRuleTypeIsRejected(
            @ForAll("incompatibleDataTypeRulePairs") DataTypeRulePair pair
    ) {
        ParameterService service = createServiceWithMocks();

        Map<String, Object> rules = new HashMap<>();
        rules.put(pair.ruleKey, pair.ruleValue);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateValidationRules(pair.dataType, rules));
        assertEquals(ErrorCode.PARAMETER_VALIDATION_RULE_INCOMPATIBLE, ex.getErrorCode());
    }

    /**
     * Property 4: Compatible (dataType, ruleType) pairs should be accepted.
     */
    @Property(tries = 100)
    @Tag("property-4-validation-rules-compatibility-with-data_type")
    void compatibleRuleTypeIsAccepted(
            @ForAll("compatibleDataTypeRulePairs") DataTypeRulePair pair
    ) {
        ParameterService service = createServiceWithMocks();

        Map<String, Object> rules = new HashMap<>();
        rules.put(pair.ruleKey, pair.ruleValue);

        assertDoesNotThrow(() -> service.validateValidationRules(pair.dataType, rules));
    }

    // Validates: Requirements 1.15

    /**
     * Property 6: For any tree structure, the computed path should be the dot-joined
     * names from root to the parameter.
     */
    @Property(tries = 100)
    @Tag("property-6-parameter-path-computation")
    void parameterPathIsDotJoinedFromRootToLeaf(
            @ForAll("parameterTrees") List<ParameterDefinition> tree
    ) {
        ParameterService service = createServiceWithMocks();

        Map<Long, ParameterDefinition> paramMap = tree.stream()
                .collect(Collectors.toMap(ParameterDefinition::getId, p -> p));

        for (ParameterDefinition param : tree) {
            String path = service.computeParameterPath(param, paramMap);

            // Build expected path by traversing parent chain
            LinkedList<String> segments = new LinkedList<>();
            ParameterDefinition current = param;
            while (current != null) {
                segments.addFirst(current.getName());
                current = current.getParentId() != null ? paramMap.get(current.getParentId()) : null;
            }
            String expected = String.join(".", segments);

            assertEquals(expected, path,
                    "Path for '" + param.getName() + "' should be dot-joined from root");
            // Path must contain the parameter's own name
            assertTrue(path.endsWith(param.getName()));
            // Root parameters have no dots
            if (param.getParentId() == null) {
                assertFalse(path.contains("."));
            } else {
                assertTrue(path.contains("."));
            }
        }
    }

    // Validates: Requirements 1.16

    /**
     * Property 7: Attempting to create a parameter at depth > 5 should be rejected.
     */
    @Property(tries = 50)
    @Tag("property-7-maximum-depth-enforcement")
    void depthExceedingFiveLevelsIsRejected(
            @ForAll("depthsExceedingMax") int targetDepth
    ) {
        ParameterRepository repo = mock(ParameterRepository.class);
        ExpressionEngine engine = mock(ExpressionEngine.class);
        ParameterService service = new ParameterService(repo, mock(TemplateRepository.class), mock(TemplateScanService.class), engine, objectMapper, mock(AuditLogService.class), mock(AggregationResolver.class));

        // Build a chain of parents at depth = targetDepth (already 5+)
        // The new child would be at targetDepth + 1 which exceeds MAX_DEPTH=5
        Long templateId = 1L;
        List<ParameterDefinition> chain = new ArrayList<>();
        for (int i = 0; i < targetDepth; i++) {
            ParameterDefinition p = makeParam((long) (i + 1), templateId,
                    i == 0 ? null : (long) i, "level" + i, "OBJECT");
            chain.add(p);
        }

        // Mock findById for each parent in the chain
        for (ParameterDefinition p : chain) {
            when(repo.findById(p.getId())).thenReturn(Optional.of(p));
        }

        Long parentId = chain.get(chain.size() - 1).getId();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateDepth(parentId));
        assertEquals(ErrorCode.PARAMETER_MAX_DEPTH_EXCEEDED, ex.getErrorCode());
    }

    // Validates: Requirements 2.12

    /**
     * Property 8: Adding children to non-OBJECT/ARRAY parents should be rejected.
     */
    @Property(tries = 100)
    @Tag("property-8-parent-type-constraint")
    void nonContainerParentRejectsChildren(
            @ForAll("leafDataTypes") String parentDataType
    ) {
        ParameterService service = createServiceWithMocks();

        ParameterDefinition parent = makeParam(1L, 1L, null, "parent", parentDataType);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateParentType(parent));
        assertEquals(ErrorCode.PARAMETER_PARENT_TYPE_INVALID, ex.getErrorCode());
    }

    /**
     * Property 8: Adding children to OBJECT or ARRAY parents should be accepted.
     */
    @Property(tries = 100)
    @Tag("property-8-parent-type-constraint")
    void containerParentAcceptsChildren(
            @ForAll("containerDataTypes") String parentDataType
    ) {
        ParameterService service = createServiceWithMocks();

        ParameterDefinition parent = makeParam(1L, 1L, null, "parent", parentDataType);

        assertDoesNotThrow(() -> service.validateParentType(parent));
    }

    // Validates: Requirements 1.17

    /**
     * Property 11: Valid names matching ^[a-zA-Z_][a-zA-Z0-9_-]*$ should be accepted.
     */
    @Property(tries = 100)
    @Tag("property-11-parameter-name-pattern-validation")
    void validNamesAreAccepted(
            @ForAll("validParamNames") String name
    ) {
        ParameterService service = createServiceWithMocks();
        assertDoesNotThrow(() -> service.validateName(name));
    }

    /**
     * Property 11: Invalid names not matching the pattern should be rejected.
     */
    @Property(tries = 100)
    @Tag("property-11-parameter-name-pattern-validation")
    void invalidNamesAreRejected(
            @ForAll("invalidParamNames") String name
    ) {
        ParameterService service = createServiceWithMocks();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateName(name));
        assertEquals(ErrorCode.PARAMETER_INVALID_NAME, ex.getErrorCode());
    }

    // Validates: Requirements 5.6

    /**
     * Property 17: A dependency graph with cycles should be detected.
     */
    @Property(tries = 50)
    @Tag("property-17-circular-dependency-detection")
    void circularDependencyIsDetected(
            @ForAll("circularDependencyGraphs") CircularGraph graph
    ) {
        ParameterRepository repo = mock(ParameterRepository.class);
        ExpressionEngine engine = mock(ExpressionEngine.class);
        ParameterService service = new ParameterService(repo, mock(TemplateRepository.class), mock(TemplateScanService.class), engine, objectMapper, mock(AuditLogService.class), mock(AggregationResolver.class));

        when(repo.findByTemplateIdOrderBySortOrderAsc(1L)).thenReturn(graph.existingParams);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.detectCircularDependency(1L, graph.newExpression, graph.newParamName));
        assertEquals(ErrorCode.PARAMETER_CIRCULAR_DEPENDENCY, ex.getErrorCode());
    }


    record RangePair(String minKey, String maxKey, double minVal, double maxVal, String dataType) {}
    record DataTypeRulePair(String dataType, String ruleKey, Object ruleValue) {}
    record CircularGraph(List<ParameterDefinition> existingParams, String newParamName, String newExpression) {}


    private ParameterService createServiceWithMocks() {
        ParameterRepository repo = mock(ParameterRepository.class);
        TemplateRepository templateRepo = mock(TemplateRepository.class);
        TemplateScanService scanService = mock(TemplateScanService.class);
        ExpressionEngine engine = mock(ExpressionEngine.class);
        return new ParameterService(repo, templateRepo, scanService, engine, objectMapper, mock(AuditLogService.class), mock(AggregationResolver.class));
    }

    private static ParameterDefinition makeParam(Long id, Long templateId, Long parentId,
                                                   String name, String dataType) {
        ParameterDefinition p = new ParameterDefinition();
        p.setId(id);
        p.setTemplateId(templateId);
        p.setParentId(parentId);
        p.setName(name);
        p.setDataType(dataType);
        p.setParameterType("REQUEST");
        return p;
    }

    private static ParameterDefinition makeDerivedParam(Long id, Long templateId, String name,
                                                         String expressionText) {
        ParameterDefinition p = new ParameterDefinition();
        p.setId(id);
        p.setTemplateId(templateId);
        p.setParentId(null);
        p.setName(name);
        p.setDataType("STRING");
        p.setParameterType("DERIVED");
        p.setExpressionText(expressionText);
        p.setExpressionType("JAVASCRIPT");
        return p;
    }


    @Provide
    Arbitrary<String> emptyOrNullExpressions() {
        return Arbitraries.oneOf(
                Arbitraries.just((String) null),
                Arbitraries.just(""),
                Arbitraries.just("   "),
                Arbitraries.just("\t"),
                Arbitraries.just("\n")
        );
    }

    @Provide
    Arbitrary<String> nonEmptyExpressions() {
        return Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50),
                Arbitraries.of("a + b", "price * quantity", "Math.round(x)", "IF(a>0,a,0)")
        );
    }

    @Provide
    Arbitrary<String> validParamNames() {
        // Must match ^[a-zA-Z_][a-zA-Z0-9_-]*$
        Arbitrary<Character> firstChar = Arbitraries.oneOf(
                Arbitraries.chars().range('a', 'z'),
                Arbitraries.chars().range('A', 'Z'),
                Arbitraries.just('_')
        );
        Arbitrary<String> restChars = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-")
                .ofMinLength(0).ofMaxLength(20);

        return Combinators.combine(firstChar, restChars)
                .as((first, rest) -> first + rest);
    }

    @Provide
    Arbitrary<String> invalidParamNames() {
        return Arbitraries.oneOf(
                // Starts with digit
                Arbitraries.strings().withChars("0123456789").ofLength(1)
                        .flatMap(d -> Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
                                .map(rest -> d + rest)),
                // Starts with hyphen
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
                        .map(s -> "-" + s),
                // Contains spaces
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5)
                        .flatMap(a -> Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5)
                                .map(b -> a + " " + b)),
                // Contains special characters
                Arbitraries.of("name@field", "my.param", "test!", "a+b", "x=y", "a/b"),
                // Empty string
                Arbitraries.just(""),
                // Starts with dot
                Arbitraries.just(".name")
        );
    }

    @Provide
    Arbitrary<Long> optionalParentIds() {
        return Arbitraries.oneOf(
                Arbitraries.just((Long) null),
                Arbitraries.longs().between(100L, 999L)
        );
    }

    @Provide
    Arbitrary<RangePair> invalidRangePairs() {
        // min > max for each range type
        return Arbitraries.oneOf(
                // min_length > max_length (STRING)
                Arbitraries.integers().between(1, 100)
                        .flatMap(maxVal -> Arbitraries.integers().between(maxVal + 1, maxVal + 100)
                                .map(minVal -> new RangePair("min_length", "max_length",
                                        minVal, maxVal, "STRING"))),
                // min > max (NUMBER)
                Arbitraries.integers().between(-1000, 1000)
                        .flatMap(maxVal -> Arbitraries.integers().between(maxVal + 1, maxVal + 1000)
                                .map(minVal -> new RangePair("min", "max",
                                        minVal, maxVal, "NUMBER"))),
                // min_items > max_items (ARRAY)
                Arbitraries.integers().between(0, 50)
                        .flatMap(maxVal -> Arbitraries.integers().between(maxVal + 1, maxVal + 50)
                                .map(minVal -> new RangePair("min_items", "max_items",
                                        minVal, maxVal, "ARRAY")))
        );
    }

    @Provide
    Arbitrary<RangePair> validRangePairs() {
        // min <= max for each range type
        return Arbitraries.oneOf(
                // min_length <= max_length (STRING)
                Arbitraries.integers().between(0, 100)
                        .flatMap(minVal -> Arbitraries.integers().between(minVal, minVal + 100)
                                .map(maxVal -> new RangePair("min_length", "max_length",
                                        minVal, maxVal, "STRING"))),
                // min <= max (NUMBER)
                Arbitraries.integers().between(-1000, 1000)
                        .flatMap(minVal -> Arbitraries.integers().between(minVal, minVal + 1000)
                                .map(maxVal -> new RangePair("min", "max",
                                        minVal, maxVal, "NUMBER"))),
                // min_items <= max_items (ARRAY)
                Arbitraries.integers().between(0, 50)
                        .flatMap(minVal -> Arbitraries.integers().between(minVal, minVal + 50)
                                .map(maxVal -> new RangePair("min_items", "max_items",
                                        minVal, maxVal, "ARRAY")))
        );
    }

    @Provide
    Arbitrary<DataTypeRulePair> incompatibleDataTypeRulePairs() {
        // Generate (dataType, ruleKey) pairs that are NOT compatible per the matrix
        return Arbitraries.oneOf(
                // min_length for non-STRING types
                Arbitraries.of("NUMBER", "DATE", "BOOLEAN", "ARRAY", "OBJECT")
                        .map(dt -> new DataTypeRulePair(dt, "min_length", 1)),
                // max_length for non-STRING types
                Arbitraries.of("NUMBER", "DATE", "BOOLEAN", "ARRAY", "OBJECT")
                        .map(dt -> new DataTypeRulePair(dt, "max_length", 100)),
                // pattern for non-STRING types
                Arbitraries.of("NUMBER", "DATE", "BOOLEAN", "ARRAY", "OBJECT")
                        .map(dt -> new DataTypeRulePair(dt, "pattern", "^.*$")),
                // not_blank for non-STRING types
                Arbitraries.of("NUMBER", "DATE", "BOOLEAN", "ARRAY", "OBJECT")
                        .map(dt -> new DataTypeRulePair(dt, "not_blank", true)),
                // min for non-NUMBER types
                Arbitraries.of("STRING", "DATE", "BOOLEAN", "ARRAY", "OBJECT")
                        .map(dt -> new DataTypeRulePair(dt, "min", 0)),
                // max for non-NUMBER types
                Arbitraries.of("STRING", "DATE", "BOOLEAN", "ARRAY", "OBJECT")
                        .map(dt -> new DataTypeRulePair(dt, "max", 100)),
                // min_items for non-ARRAY types
                Arbitraries.of("STRING", "NUMBER", "DATE", "BOOLEAN", "OBJECT")
                        .map(dt -> new DataTypeRulePair(dt, "min_items", 1)),
                // max_items for non-ARRAY types
                Arbitraries.of("STRING", "NUMBER", "DATE", "BOOLEAN", "OBJECT")
                        .map(dt -> new DataTypeRulePair(dt, "max_items", 10))
        );
    }

    @Provide
    Arbitrary<DataTypeRulePair> compatibleDataTypeRulePairs() {
        // Generate (dataType, ruleKey) pairs that ARE compatible per the matrix
        return Arbitraries.oneOf(
                // not_null is compatible with all types
                Arbitraries.of("STRING", "NUMBER", "DATE", "BOOLEAN", "ARRAY", "OBJECT")
                        .map(dt -> new DataTypeRulePair(dt, "not_null", true)),
                // not_blank for STRING
                Arbitraries.just(new DataTypeRulePair("STRING", "not_blank", true)),
                // min_length/max_length for STRING
                Arbitraries.just(new DataTypeRulePair("STRING", "min_length", 1)),
                Arbitraries.just(new DataTypeRulePair("STRING", "max_length", 100)),
                // pattern for STRING
                Arbitraries.just(new DataTypeRulePair("STRING", "pattern", "^[a-z]+$")),
                // min/max for NUMBER
                Arbitraries.just(new DataTypeRulePair("NUMBER", "min", 0)),
                Arbitraries.just(new DataTypeRulePair("NUMBER", "max", 100)),
                // enum_values for STRING and NUMBER
                Arbitraries.of("STRING", "NUMBER")
                        .map(dt -> new DataTypeRulePair(dt, "enum_values", List.of("A", "B"))),
                // min_items/max_items for ARRAY
                Arbitraries.just(new DataTypeRulePair("ARRAY", "min_items", 1)),
                Arbitraries.just(new DataTypeRulePair("ARRAY", "max_items", 50))
        );
    }

    @Provide
    Arbitrary<List<ParameterDefinition>> parameterTrees() {
        // Generate random tree structures with 1-8 nodes
        return Arbitraries.integers().between(1, 8).flatMap(size -> {
            return Arbitraries.just(size).map(s -> {
                List<ParameterDefinition> tree = new ArrayList<>();
                // First node is always root
                tree.add(makeParam(1L, 1L, null, "root", "OBJECT"));

                for (int i = 2; i <= s; i++) {
                    // Pick a random parent from existing nodes
                    long parentId = tree.get(new Random(i).nextInt(tree.size())).getId();
                    String name = "node" + i;
                    String dataType = i < s ? "OBJECT" : "STRING"; // intermediates are OBJECT
                    tree.add(makeParam((long) i, 1L, parentId, name, dataType));
                }
                return tree;
            });
        });
    }

    @Provide
    Arbitrary<Integer> depthsExceedingMax() {
        // Depths of 5 or more (so adding a child would exceed MAX_DEPTH=5)
        return Arbitraries.integers().between(5, 10);
    }

    @Provide
    Arbitrary<String> leafDataTypes() {
        return Arbitraries.of("STRING", "NUMBER", "DATE", "BOOLEAN");
    }

    @Provide
    Arbitrary<String> containerDataTypes() {
        return Arbitraries.of("OBJECT", "ARRAY");
    }

    @Provide
    Arbitrary<CircularGraph> circularDependencyGraphs() {
        // Generate multi-node circular dependencies (2+ nodes)
        return Arbitraries.oneOf(
                // Direct cycle: existing param "paramB" references "paramA",
                // new param "paramA" references "paramB"
                Arbitraries.just(new CircularGraph(
                        List.of(makeDerivedParam(1L, 1L, "paramB", "paramA + 1")),
                        "paramA", "paramB + 1"
                )),
                // 3-node cycle: B->C, C->A, new A->B
                Arbitraries.just(new CircularGraph(
                        List.of(
                                makeDerivedParam(1L, 1L, "paramB", "paramC * 2"),
                                makeDerivedParam(2L, 1L, "paramC", "paramA + 3")
                        ),
                        "paramA", "paramB + 10"
                )),
                // 4-node cycle: B->C, C->D, D->A, new A->B
                Arbitraries.just(new CircularGraph(
                        List.of(
                                makeDerivedParam(1L, 1L, "paramB", "paramC + 1"),
                                makeDerivedParam(2L, 1L, "paramC", "paramD + 1"),
                                makeDerivedParam(3L, 1L, "paramD", "paramA + 1")
                        ),
                        "paramA", "paramB + 1"
                )),
                // 2-node cycle with different expressions: price -> cost, cost -> price
                Arbitraries.just(new CircularGraph(
                        List.of(makeDerivedParam(1L, 1L, "price", "cost * 2")),
                        "cost", "price + 10"
                ))
        );
    }
}

