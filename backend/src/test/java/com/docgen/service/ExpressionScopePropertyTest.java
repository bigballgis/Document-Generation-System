package com.docgen.service;

import com.docgen.entity.ParameterDefinition;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.Tuple.Tuple2;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for expression scope validation in {@link ParameterService}.
 *
 * <p><b>Validates: Requirements 3.3, 3.4</b></p>
 */
// Feature: array-aggregation-and-row-derived, Property 4: expression scope validation
class ExpressionScopePropertyTest {

    private static final Long TEMPLATE_ID = 1L;
    private static final Long PARENT_ID = 10L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ParameterService createService(List<ParameterDefinition> siblings) {
        ParameterRepository repo = mock(ParameterRepository.class);
        when(repo.findByParentIdOrderBySortOrderAsc(PARENT_ID)).thenReturn(siblings);
        return new ParameterService(repo, mock(TemplateRepository.class),
                mock(TemplateScanService.class), mock(ExpressionEngine.class),
                OBJECT_MAPPER, mock(AuditLogService.class), mock(AggregationResolver.class));
    }

    private ParameterDefinition makeSibling(Long id, String name, String paramType) {
        ParameterDefinition p = new ParameterDefinition();
        p.setId(id);
        p.setTemplateId(TEMPLATE_ID);
        p.setParentId(PARENT_ID);
        p.setName(name);
        p.setParameterType(paramType);
        p.setDataType("NUMBER");
        p.setSortOrder(id.intValue());
        return p;
    }

    // ═══════════════════════════════════════════════════════════════
    // Property 4: expression scope validation
    // ═══════════════════════════════════════════════════════════════

    /**
     * If expression references ONLY sibling names, validation should accept.
     */
    @Property(tries = 200)
    @Label("Property 4a: expression referencing only siblings is accepted")
    void expressionReferencingOnlySiblingsIsAccepted(
            @ForAll("siblingOnlyExpressions") ScopeTestData testData
    ) {
        List<ParameterDefinition> siblings = new ArrayList<>();
        long id = 100L;
        for (String name : testData.siblingNames) {
            siblings.add(makeSibling(id++, name, "REQUEST"));
        }

        ParameterService service = createService(siblings);

        assertDoesNotThrow(() ->
                service.validateExpressionScope(TEMPLATE_ID, PARENT_ID,
                        testData.expressionText, testData.selfName));
    }

    /**
     * If expression references any non-sibling name, validation should reject
     * with PARAMETER_EXPRESSION_INVALID_SCOPE.
     */
    @Property(tries = 200)
    @Label("Property 4b: expression referencing non-sibling is rejected")
    void expressionReferencingNonSiblingIsRejected(
            @ForAll("nonSiblingExpressions") ScopeTestData testData
    ) {
        List<ParameterDefinition> siblings = new ArrayList<>();
        long id = 100L;
        for (String name : testData.siblingNames) {
            siblings.add(makeSibling(id++, name, "REQUEST"));
        }

        ParameterService service = createService(siblings);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.validateExpressionScope(TEMPLATE_ID, PARENT_ID,
                        testData.expressionText, testData.selfName));
        assertEquals(ErrorCode.PARAMETER_EXPRESSION_INVALID_SCOPE, ex.getErrorCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // Records & Generators
    // ═══════════════════════════════════════════════════════════════

    record ScopeTestData(Set<String> siblingNames, String selfName, String expressionText) {}

    @Provide
    Arbitrary<ScopeTestData> siblingOnlyExpressions() {
        return paramNameSet(2, 6).flatMap(siblings -> {
            String selfName = "selfParam";
            List<String> siblingList = new ArrayList<>(siblings);
            // Build expression using only sibling names
            return Arbitraries.integers().between(1, Math.min(3, siblingList.size()))
                    .flatMap(count -> {
                        return Arbitraries.shuffle(siblingList)
                                .map(shuffled -> {
                                    List<String> picked = shuffled.subList(0, Math.min(count, shuffled.size()));
                                    String expr = String.join(" + ", picked);
                                    return new ScopeTestData(siblings, selfName, expr);
                                });
                    });
        });
    }

    @Provide
    Arbitrary<ScopeTestData> nonSiblingExpressions() {
        return paramNameSet(1, 4).flatMap(siblings -> {
            String selfName = "selfParam";
            // Generate a non-sibling name that is NOT a JS keyword and NOT in siblings
            return nonSiblingName(siblings, selfName).map(nonSibling -> {
                // Build expression that includes the non-sibling reference
                List<String> siblingList = new ArrayList<>(siblings);
                String expr;
                if (siblingList.isEmpty()) {
                    expr = nonSibling + " * 2";
                } else {
                    expr = siblingList.get(0) + " + " + nonSibling;
                }
                return new ScopeTestData(siblings, selfName, expr);
            });
        });
    }

    private Arbitrary<Set<String>> paramNameSet(int minSize, int maxSize) {
        return validParamName().set().ofMinSize(minSize).ofMaxSize(maxSize)
                .filter(s -> !s.contains("selfParam"));
    }

    private Arbitrary<String> validParamName() {
        Arbitrary<Character> firstChar = Arbitraries.oneOf(
                Arbitraries.chars().range('a', 'z'),
                Arbitraries.chars().range('A', 'Z')
        );
        Arbitrary<String> rest = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyz0123456789")
                .ofMinLength(2).ofMaxLength(8);
        return Combinators.combine(firstChar, rest).as((f, r) -> f + r)
                .filter(n -> !isJsKeyword(n));
    }

    private Arbitrary<String> nonSiblingName(Set<String> siblings, String selfName) {
        // Generate a name guaranteed not in siblings and not a JS keyword
        return validParamName()
                .filter(n -> !siblings.contains(n) && !n.equals(selfName));
    }

    private static final Set<String> JS_KEYWORDS = Set.of(
            "var", "let", "const", "function", "return", "if", "else",
            "true", "false", "null", "undefined",
            "Math", "Number", "String", "parseInt", "parseFloat",
            "isNaN", "NaN", "Infinity"
    );

    private boolean isJsKeyword(String name) {
        return JS_KEYWORDS.contains(name);
    }
}
