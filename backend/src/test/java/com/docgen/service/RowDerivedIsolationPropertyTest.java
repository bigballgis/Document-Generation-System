package com.docgen.service;

import com.docgen.entity.ExpressionType;
import com.docgen.entity.ParameterDefinition;
import com.docgen.repository.ParameterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for DERIVED scope isolation within ARRAY rows.
 *
 * <p><b>Validates: Requirements 4.2, 4.3</b></p>
 */
// Feature: array-aggregation-and-row-derived, Property 6: DERIVED scope isolation
class RowDerivedIsolationPropertyTest {

    private static final Long TEMPLATE_ID = 1L;

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

    /**
     * Property 6: For any ARRAY with Row_Level_Derived parameters, modifying the field values
     * of row j (j ≠ i) SHALL NOT change the DERIVED computation result of row i.
     *
     * <p><b>Validates: Requirements 4.2, 4.3</b></p>
     */
    @Property(tries = 200)
    @Label("Property 6: DERIVED scope isolation")
    void modifyingRowJ_doesNotAffectRowI(
            @ForAll("arrayWithDerived") ArrayDerivedTestData testData
    ) {
        // We need at least 2 rows to test isolation
        Assume.that(testData.rows.size() >= 2);

        // Setup parameter definitions
        ParameterDefinition arrayParam = makeParam(1L, null, "items", "REQUEST", "ARRAY", 0);
        ParameterDefinition priceChild = makeParam(2L, 1L, "price", "REQUEST", "NUMBER", 0);
        ParameterDefinition qtyChild = makeParam(3L, 1L, "quantity", "REQUEST", "NUMBER", 1);
        ParameterDefinition subtotalChild = makeParam(4L, 1L, "subtotal", "DERIVED", "NUMBER", 2);
        subtotalChild.setExpressionText("price * quantity");
        subtotalChild.setExpressionType("JAVASCRIPT");

        List<ParameterDefinition> rootParams = List.of(arrayParam);
        Map<Long, List<ParameterDefinition>> childrenMap = new LinkedHashMap<>();
        childrenMap.put(1L, List.of(priceChild, qtyChild, subtotalChild));

        // Mock expression engine: evaluate "price * quantity" from row context
        ExpressionEngine engine = mock(ExpressionEngine.class);
        when(engine.evaluate(eq("price * quantity"), eq(ExpressionType.JAVASCRIPT), anyMap()))
                .thenAnswer(inv -> {
                    Map<String, Object> ctx = inv.getArgument(2);
                    Number p = (Number) ctx.get("price");
                    Number q = (Number) ctx.get("quantity");
                    if (p == null || q == null) return 0;
                    return p.intValue() * q.intValue();
                });

        ParameterRepository repo = mock(ParameterRepository.class);
        AggregationResolver aggResolver = mock(AggregationResolver.class);
        ParameterValidationService service = new ParameterValidationService(
                repo, engine, new ObjectMapper(), aggResolver);

        // Run 1: evaluate with original data
        List<Object> originalArray = deepCopyRows(testData.rows);
        Map<String, Object> context1 = new LinkedHashMap<>();
        context1.put("items", originalArray);
        service.evaluateNestedDerivedParameters(context1, rootParams, childrenMap);

        // Capture row 0's subtotal
        @SuppressWarnings("unchecked")
        Map<String, Object> row0Run1 = (Map<String, Object>) originalArray.get(0);
        Object subtotalRow0Run1 = row0Run1.get("subtotal");

        // Run 2: modify row 1 (or last row) and re-evaluate
        List<Object> modifiedArray = deepCopyRows(testData.rows);
        @SuppressWarnings("unchecked")
        Map<String, Object> rowToModify = (Map<String, Object>) modifiedArray.get(modifiedArray.size() - 1);
        rowToModify.put("price", 99999);
        rowToModify.put("quantity", 99999);

        Map<String, Object> context2 = new LinkedHashMap<>();
        context2.put("items", modifiedArray);
        service.evaluateNestedDerivedParameters(context2, rootParams, childrenMap);

        // Verify: row 0's subtotal is unchanged
        @SuppressWarnings("unchecked")
        Map<String, Object> row0Run2 = (Map<String, Object>) modifiedArray.get(0);
        Object subtotalRow0Run2 = row0Run2.get("subtotal");

        assertEquals(subtotalRow0Run1, subtotalRow0Run2,
                "Row 0 DERIVED result should be unchanged when other rows are modified");
    }

    private List<Object> deepCopyRows(List<Map<String, Object>> rows) {
        List<Object> copy = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            copy.add(new LinkedHashMap<>(row));
        }
        return copy;
    }


    record ArrayDerivedTestData(List<Map<String, Object>> rows) {}

    @Provide
    Arbitrary<ArrayDerivedTestData> arrayWithDerived() {
        Arbitrary<Map<String, Object>> row = Arbitraries.integers().between(1, 1000)
                .flatMap(price -> Arbitraries.integers().between(1, 100)
                        .map(qty -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("price", price);
                            m.put("quantity", qty);
                            return m;
                        }));

        return row.list().ofMinSize(2).ofMaxSize(20)
                .map(ArrayDerivedTestData::new);
    }
}

