package com.docgen.service;

import com.docgen.entity.ExpressionType;
import com.docgen.entity.ParameterDefinition;
import com.docgen.exception.BusinessException;
import com.docgen.repository.ParameterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * Unit tests for the 5-step pipeline in {@link ParameterValidationService}.
 *
 * <p><b>Validates: Requirements 7.1, 7.2, 7.3</b></p>
 */
@ExtendWith(MockitoExtension.class)
class ParameterValidationServicePipelineTest {

    private static final Long TEMPLATE_ID = 1L;

    @Mock
    private ParameterRepository parameterRepository;

    @Mock
    private ExpressionEngine expressionEngine;

    private AggregationResolver aggregationResolver;
    private ParameterValidationService service;

    @BeforeEach
    void setUp() {
        aggregationResolver = new AggregationResolver(parameterRepository);
        service = new ParameterValidationService(
                parameterRepository, expressionEngine, new ObjectMapper(), aggregationResolver);
    }

    private ParameterDefinition makeParam(Long id, Long parentId, String name,
                                           String paramType, String dataType,
                                           boolean required, int sortOrder) {
        ParameterDefinition p = new ParameterDefinition();
        p.setId(id);
        p.setTemplateId(TEMPLATE_ID);
        p.setParentId(parentId);
        p.setName(name);
        p.setParameterType(paramType);
        p.setDataType(dataType);
        p.setRequired(required);
        p.setSortOrder(sortOrder);
        return p;
    }

    @Test
    @DisplayName("End-to-end pipeline: REQUEST → nested DERIVED → aggregation → root DERIVED referencing aggregation")
    void fullPipeline_requestToDerivedToAggregationToRootDerived() {
        // Setup: items ARRAY with price, quantity (REQUEST) and subtotal (DERIVED)
        // Root DERIVED: grandTotal references items.$sum_subtotal
        ParameterDefinition itemsParam = makeParam(1L, null, "items", "REQUEST", "ARRAY", true, 0);
        ParameterDefinition priceChild = makeParam(2L, 1L, "price", "REQUEST", "NUMBER", true, 0);
        ParameterDefinition qtyChild = makeParam(3L, 1L, "quantity", "REQUEST", "NUMBER", true, 1);
        ParameterDefinition subtotalChild = makeParam(4L, 1L, "subtotal", "DERIVED", "NUMBER", false, 10);
        subtotalChild.setExpressionText("price * quantity");
        subtotalChild.setExpressionType("JAVASCRIPT");

        ParameterDefinition grandTotal = makeParam(5L, null, "grandTotal", "DERIVED", "NUMBER", false, 100);
        grandTotal.setExpressionText("items.$sum_subtotal");
        grandTotal.setExpressionType("JAVASCRIPT");

        List<ParameterDefinition> allParams = List.of(itemsParam, priceChild, qtyChild, subtotalChild, grandTotal);
        when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID)).thenReturn(allParams);

        // Mock expression engine
        when(expressionEngine.evaluate(anyString(), eq(ExpressionType.JAVASCRIPT), anyMap()))
                .thenAnswer(inv -> {
                    String expr = inv.getArgument(0);
                    Map<String, Object> ctx = inv.getArgument(2);

                    if ("price * quantity".equals(expr)) {
                        Number p = (Number) ctx.get("price");
                        Number q = (Number) ctx.get("quantity");
                        return new BigDecimal(p.toString()).multiply(new BigDecimal(q.toString()));
                    } else if ("items.$sum_subtotal".equals(expr)) {
                        // Root DERIVED: should see aggregation value
                        Object sumVal = ctx.get("items.$sum_subtotal");
                        assertNotNull(sumVal, "items.$sum_subtotal should be available in root DERIVED context");
                        return sumVal;
                    }
                    return null;
                });

        // Input data
        Map<String, Object> input = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(Map.of("price", 100, "quantity", 2));
        items.add(Map.of("price", 50, "quantity", 3));
        input.put("items", items);

        // Execute
        Map<String, Object> result = service.validateAndBuildContext(TEMPLATE_ID, input);

        // Verify: items array has subtotal computed in each row
        @SuppressWarnings("unchecked")
        List<Object> resultItems = (List<Object>) result.get("items");
        assertNotNull(resultItems);
        assertEquals(2, resultItems.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> row0 = (Map<String, Object>) resultItems.get(0);
        assertEquals(new BigDecimal("200"), row0.get("subtotal"));

        @SuppressWarnings("unchecked")
        Map<String, Object> row1 = (Map<String, Object>) resultItems.get(1);
        assertEquals(new BigDecimal("150"), row1.get("subtotal"));

        // Verify: aggregation properties injected
        assertTrue(result.containsKey("items.$count"));
        assertEquals(2, result.get("items.$count"));
        assertTrue(result.containsKey("items.$sum_subtotal"));

        // Verify: root DERIVED grandTotal computed
        assertNotNull(result.get("grandTotal"), "grandTotal should be computed");
    }

    @Test
    @DisplayName("Root DERIVED can reference items.$sum_subtotal (aggregation of row-level DERIVED)")
    void rootDerived_canReferenceAggregationOfRowLevelDerived() {
        ParameterDefinition itemsParam = makeParam(1L, null, "items", "REQUEST", "ARRAY", true, 0);
        ParameterDefinition priceChild = makeParam(2L, 1L, "price", "REQUEST", "NUMBER", true, 0);
        ParameterDefinition qtyChild = makeParam(3L, 1L, "quantity", "REQUEST", "NUMBER", true, 1);
        ParameterDefinition subtotalChild = makeParam(4L, 1L, "subtotal", "DERIVED", "NUMBER", false, 10);
        subtotalChild.setExpressionText("price * quantity");
        subtotalChild.setExpressionType("JAVASCRIPT");

        ParameterDefinition taxRate = makeParam(6L, null, "taxRate", "REQUEST", "NUMBER", true, 1);
        ParameterDefinition totalWithTax = makeParam(7L, null, "totalWithTax", "DERIVED", "NUMBER", false, 200);
        totalWithTax.setExpressionText("items.$sum_subtotal * (1 + taxRate)");
        totalWithTax.setExpressionType("JAVASCRIPT");

        List<ParameterDefinition> allParams = List.of(
                itemsParam, priceChild, qtyChild, subtotalChild, taxRate, totalWithTax);
        when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID)).thenReturn(allParams);

        when(expressionEngine.evaluate(anyString(), eq(ExpressionType.JAVASCRIPT), anyMap()))
                .thenAnswer(inv -> {
                    String expr = inv.getArgument(0);
                    Map<String, Object> ctx = inv.getArgument(2);

                    if ("price * quantity".equals(expr)) {
                        Number p = (Number) ctx.get("price");
                        Number q = (Number) ctx.get("quantity");
                        return new BigDecimal(p.toString()).multiply(new BigDecimal(q.toString()));
                    } else if ("items.$sum_subtotal * (1 + taxRate)".equals(expr)) {
                        BigDecimal sum = (BigDecimal) ctx.get("items.$sum_subtotal");
                        Number rate = (Number) ctx.get("taxRate");
                        assertNotNull(sum, "items.$sum_subtotal should be in context");
                        assertNotNull(rate, "taxRate should be in context");
                        BigDecimal multiplier = BigDecimal.ONE.add(new BigDecimal(rate.toString()));
                        return sum.multiply(multiplier);
                    }
                    return null;
                });

        Map<String, Object> input = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(Map.of("price", 100, "quantity", 2));  // subtotal = 200
        items.add(Map.of("price", 50, "quantity", 3));   // subtotal = 150
        input.put("items", items);
        input.put("taxRate", 0.1);

        Map<String, Object> result = service.validateAndBuildContext(TEMPLATE_ID, input);

        // sum_subtotal = 200 + 150 = 350
        // totalWithTax = 350 * 1.1 = 385
        assertNotNull(result.get("totalWithTax"));
        BigDecimal expected = new BigDecimal("350").multiply(new BigDecimal("1.1"));
        assertEquals(0, expected.compareTo((BigDecimal) result.get("totalWithTax")),
                "totalWithTax should be sum_subtotal * (1 + taxRate)");
    }

    @Test
    @DisplayName("Backward compatibility: template with no ARRAY params works unchanged")
    void noArrayParams_backwardCompatible() {
        ParameterDefinition nameParam = makeParam(1L, null, "name", "REQUEST", "STRING", true, 0);
        ParameterDefinition greeting = makeParam(2L, null, "greeting", "DERIVED", "STRING", false, 10);
        greeting.setExpressionText("'Hello ' + name");
        greeting.setExpressionType("JAVASCRIPT");

        when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                .thenReturn(List.of(nameParam, greeting));
        when(expressionEngine.evaluate(eq("'Hello ' + name"), eq(ExpressionType.JAVASCRIPT), anyMap()))
                .thenReturn("Hello Alice");

        Map<String, Object> input = Map.of("name", "Alice");
        Map<String, Object> result = service.validateAndBuildContext(TEMPLATE_ID, input);

        assertEquals("Alice", result.get("name"));
        assertEquals("Hello Alice", result.get("greeting"));
        assertEquals(2, result.size(), "Only name and greeting should be in context");
    }

    @Test
    @DisplayName("Empty ARRAY: nested DERIVED skipped, aggregation computed with defaults")
    void emptyArray_nestedDerivedSkipped_aggregationDefaults() {
        ParameterDefinition itemsParam = makeParam(1L, null, "items", "REQUEST", "ARRAY", false, 0);
        ParameterDefinition priceChild = makeParam(2L, 1L, "price", "REQUEST", "NUMBER", true, 0);
        ParameterDefinition subtotalChild = makeParam(3L, 1L, "subtotal", "DERIVED", "NUMBER", false, 10);
        subtotalChild.setExpressionText("price * 2");
        subtotalChild.setExpressionType("JAVASCRIPT");

        when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                .thenReturn(List.of(itemsParam, priceChild, subtotalChild));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("items", new ArrayList<>());

        Map<String, Object> result = service.validateAndBuildContext(TEMPLATE_ID, input);

        // Empty array: no expression evaluation should happen
        verify(expressionEngine, never()).evaluate(anyString(), any(), anyMap());

        // Aggregation defaults
        assertEquals(0, result.get("items.$count"));
        assertNull(result.get("items.$first"));
        assertNull(result.get("items.$last"));
    }

    @Test
    @DisplayName("Row-level DERIVED evaluation failure includes array path and row index")
    void rowLevelDerivedFailure_includesPathAndIndex() {
        ParameterDefinition itemsParam = makeParam(1L, null, "items", "REQUEST", "ARRAY", true, 0);
        ParameterDefinition priceChild = makeParam(2L, 1L, "price", "REQUEST", "NUMBER", true, 0);
        ParameterDefinition badDerived = makeParam(3L, 1L, "computed", "DERIVED", "NUMBER", false, 10);
        badDerived.setExpressionText("invalid_expr");
        badDerived.setExpressionType("JAVASCRIPT");

        when(parameterRepository.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID))
                .thenReturn(List.of(itemsParam, priceChild, badDerived));
        when(expressionEngine.evaluate(eq("invalid_expr"), eq(ExpressionType.JAVASCRIPT), anyMap()))
                .thenThrow(new RuntimeException("Syntax error"));

        Map<String, Object> input = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(Map.of("price", 100));
        input.put("items", items);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateAndBuildContext(TEMPLATE_ID, input));

        assertEquals("PARAMETER_EXPRESSION_EVALUATION_FAILED", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("items[0].computed"),
                "Error should include array path and row index, got: " + ex.getMessage());
    }
}
