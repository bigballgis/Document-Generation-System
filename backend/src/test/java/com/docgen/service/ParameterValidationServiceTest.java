package com.docgen.service;

import com.docgen.entity.ExpressionType;
import com.docgen.entity.ParameterDefinition;
import com.docgen.exception.BusinessException;
import com.docgen.repository.ParameterRepository;
import com.docgen.service.AggregationResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ParameterValidationService}.
 *
 * <p><b>Validates: Requirements 6.1, 6.4, 6.7, 6.8</b></p>
 */
@ExtendWith(MockitoExtension.class)
class ParameterValidationServiceTest {

    private static final Long TEMPLATE_ID = 1L;

    @Mock
    private ParameterRepository parameterRepository;

    @Mock
    private ExpressionEngine expressionEngine;

    @Mock
    private AggregationResolver aggregationResolver;

    private ParameterValidationService service;

    @BeforeEach
    void setUp() {
        service = new ParameterValidationService(parameterRepository, expressionEngine, new ObjectMapper(), aggregationResolver);
    }

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

    @Nested
    @DisplayName("Happy path validation (Req 6.1)")
    class HappyPath {

        @Test
        @DisplayName("All valid REQUEST params → context contains all values")
        void allValidParams_returnsContext() {
            ParameterDefinition name = makeParam(1L, "name", "REQUEST", "STRING", true, null, 0);
            ParameterDefinition age = makeParam(2L, "age", "REQUEST", "NUMBER", false, null, 1);

            when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                    .thenReturn(List.of(name, age));

            Map<String, Object> input = Map.of("name", "Alice", "age", 30);
            Map<String, Object> result = service.validateAndBuildContext(TEMPLATE_ID, input);

            assertEquals("Alice", result.get("name"));
            assertEquals(30, result.get("age"));
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("REQUEST + DERIVED params → both in context")
        void requestAndDerived_bothInContext() {
            ParameterDefinition price = makeParam(1L, "price", "REQUEST", "NUMBER", true, null, 0);
            ParameterDefinition qty = makeParam(2L, "qty", "REQUEST", "NUMBER", true, null, 1);
            ParameterDefinition total = makeParam(3L, "total", "DERIVED", "NUMBER", false, null, 2);
            total.setExpressionText("price * qty");
            total.setExpressionType("JAVASCRIPT");

            when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                    .thenReturn(List.of(price, qty, total));
            when(expressionEngine.evaluate(eq("price * qty"), eq(ExpressionType.JAVASCRIPT), anyMap()))
                    .thenReturn(new BigDecimal("150"));

            Map<String, Object> input = Map.of("price", 50, "qty", 3);
            Map<String, Object> result = service.validateAndBuildContext(TEMPLATE_ID, input);

            assertEquals(50, result.get("price"));
            assertEquals(3, result.get("qty"));
            assertEquals(new BigDecimal("150"), result.get("total"));
        }
    }

    @Nested
    @DisplayName("Default value substitution (Req 6.4)")
    class DefaultValues {

        @Test
        @DisplayName("Missing required STRING param with default → default used")
        void missingRequiredString_withDefault() {
            ParameterDefinition p = makeParam(1L, "greeting", "REQUEST", "STRING", true, "Hello", 0);
            when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                    .thenReturn(List.of(p));

            Map<String, Object> result = service.validateAndBuildContext(TEMPLATE_ID, Map.of());

            assertEquals("Hello", result.get("greeting"));
        }

        @Test
        @DisplayName("Missing required NUMBER param with default → converted to BigDecimal")
        void missingRequiredNumber_withDefault() {
            ParameterDefinition p = makeParam(1L, "count", "REQUEST", "NUMBER", true, "42", 0);
            when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                    .thenReturn(List.of(p));

            Map<String, Object> result = service.validateAndBuildContext(TEMPLATE_ID, Map.of());

            assertEquals(new BigDecimal("42"), result.get("count"));
        }

        @Test
        @DisplayName("Missing required BOOLEAN param with default → converted to Boolean")
        void missingRequiredBoolean_withDefault() {
            ParameterDefinition p = makeParam(1L, "active", "REQUEST", "BOOLEAN", true, "true", 0);
            when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                    .thenReturn(List.of(p));

            Map<String, Object> result = service.validateAndBuildContext(TEMPLATE_ID, Map.of());

            assertEquals(true, result.get("active"));
        }

        @Test
        @DisplayName("Missing optional param with default → default used")
        void missingOptional_withDefault() {
            ParameterDefinition p = makeParam(1L, "color", "REQUEST", "STRING", false, "blue", 0);
            when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                    .thenReturn(List.of(p));

            Map<String, Object> result = service.validateAndBuildContext(TEMPLATE_ID, Map.of());

            assertEquals("blue", result.get("color"));
        }
    }

    @Nested
    @DisplayName("Empty parameter table (Req 6.7)")
    class EmptyParameterTable {

        @Test
        @DisplayName("Zero definitions → raw params returned directly")
        void zeroDefinitions_rawParamsReturned() {
            when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                    .thenReturn(List.of());

            Map<String, Object> input = Map.of("anything", "goes", "num", 123);
            Map<String, Object> result = service.validateAndBuildContext(TEMPLATE_ID, input);

            assertEquals("goes", result.get("anything"));
            assertEquals(123, result.get("num"));
        }

        @Test
        @DisplayName("Zero definitions + null input → empty map returned")
        void zeroDefinitions_nullInput_emptyMap() {
            when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                    .thenReturn(List.of());

            Map<String, Object> result = service.validateAndBuildContext(TEMPLATE_ID, null);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Expression evaluation failure (Req 6.8)")
    class ExpressionFailure {

        @Test
        @DisplayName("DERIVED expression throws → BusinessException with PARAMETER_EXPRESSION_EVALUATION_FAILED")
        void derivedExpressionFails_throwsBusinessException() {
            ParameterDefinition req = makeParam(1L, "x", "REQUEST", "NUMBER", false, null, 0);
            ParameterDefinition derived = makeParam(2L, "y", "DERIVED", "NUMBER", false, null, 1);
            derived.setExpressionText("x / 0");
            derived.setExpressionType("JAVASCRIPT");

            when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                    .thenReturn(List.of(req, derived));
            when(expressionEngine.evaluate(eq("x / 0"), eq(ExpressionType.JAVASCRIPT), anyMap()))
                    .thenThrow(new RuntimeException("Division by zero"));

            Map<String, Object> input = Map.of("x", 10);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.validateAndBuildContext(TEMPLATE_ID, input));

            assertEquals("PARAMETER_EXPRESSION_EVALUATION_FAILED", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("y"),
                    "Error should reference the failing param name 'y'");
        }

        @Test
        @DisplayName("Invalid expression type → BusinessException")
        void invalidExpressionType_throwsBusinessException() {
            ParameterDefinition req = makeParam(1L, "x", "REQUEST", "NUMBER", false, null, 0);
            ParameterDefinition derived = makeParam(2L, "z", "DERIVED", "NUMBER", false, null, 1);
            derived.setExpressionText("some expr");
            derived.setExpressionType("INVALID_TYPE");

            when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                    .thenReturn(List.of(req, derived));

            Map<String, Object> input = Map.of("x", 5);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.validateAndBuildContext(TEMPLATE_ID, input));

            assertEquals("PARAMETER_EXPRESSION_EVALUATION_FAILED", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("z"),
                    "Error should reference the failing param name 'z'");
        }
    }
}
