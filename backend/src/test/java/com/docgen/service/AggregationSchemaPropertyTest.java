package com.docgen.service;

import com.docgen.dto.AggregationPropertyDTO;
import com.docgen.dto.AggregationSchemaDTO;
import com.docgen.entity.ParameterDefinition;
import com.docgen.repository.ParameterRepository;
import net.jqwik.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for aggregation schema completeness.
 *
 * <p><b>Validates: Requirements 2.2, 2.3, 2.5, 2.6, 5.5</b></p>
 */
// Feature: array-aggregation-and-row-derived, Property 3: aggregation schema completeness
class AggregationSchemaPropertyTest {

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

    // ═══════════════════════════════════════════════════════════════
    // Property 3: aggregation schema completeness
    // ═══════════════════════════════════════════════════════════════

    /**
     * Property 3: For any ARRAY parameter definition with child parameters,
     * the aggregation schema SHALL include $count, $first, $last unconditionally;
     * $sum/$avg/$min/$max for each NUMBER child (including DERIVED NUMBER);
     * $join for each STRING child (including DERIVED STRING);
     * and placeholder paths use the full parameter path prefix.
     *
     * <p><b>Validates: Requirements 2.2, 2.3, 2.5, 2.6, 5.5</b></p>
     */
    @Property(tries = 200)
    @Label("Property 3: aggregation schema completeness")
    void aggregationSchemaCompleteness(
            @ForAll("randomParameterTree") ParamTreeTestData testData
    ) {
        // Setup mock repository
        ParameterRepository repo = mock(ParameterRepository.class);
        when(repo.findByTemplateIdOrderBySortOrderAsc(TEMPLATE_ID)).thenReturn(testData.allParams);

        AggregationResolver resolver = new AggregationResolver(repo);
        List<AggregationSchemaDTO> schemas = resolver.getAggregationSchema(TEMPLATE_ID);

        // Collect all ARRAY params from the tree
        List<ParameterDefinition> arrayParams = testData.allParams.stream()
                .filter(p -> "ARRAY".equals(p.getDataType()))
                .toList();

        // Schema count should match ARRAY param count
        assertEquals(arrayParams.size(), schemas.size(),
                "Schema count should match number of ARRAY parameters");

        // Build children map for verification
        Map<Long, List<ParameterDefinition>> childrenMap = new LinkedHashMap<>();
        Map<Long, ParameterDefinition> paramMap = new LinkedHashMap<>();
        for (ParameterDefinition p : testData.allParams) {
            paramMap.put(p.getId(), p);
            if (p.getParentId() != null) {
                childrenMap.computeIfAbsent(p.getParentId(), k -> new ArrayList<>()).add(p);
            }
        }

        for (int i = 0; i < arrayParams.size(); i++) {
            ParameterDefinition arrayParam = arrayParams.get(i);
            AggregationSchemaDTO schema = schemas.get(i);

            // Verify array name
            assertEquals(arrayParam.getName(), schema.arrayName(),
                    "Schema arrayName should match parameter name");

            // Verify array path
            String expectedPath = computePath(arrayParam, paramMap);
            assertEquals(expectedPath, schema.arrayPath(),
                    "Schema arrayPath should use full parameter path");

            List<AggregationPropertyDTO> props = schema.properties();
            Set<String> propNames = props.stream()
                    .map(AggregationPropertyDTO::name)
                    .collect(Collectors.toSet());

            // Unconditional properties
            assertTrue(propNames.contains("$count"), "Schema must include $count");
            assertTrue(propNames.contains("$first"), "Schema must include $first");
            assertTrue(propNames.contains("$last"), "Schema must include $last");

            // Verify $count, $first, $last placeholder paths
            assertPlaceholderPath(props, "$count", expectedPath + ".$count");
            assertPlaceholderPath(props, "$first", expectedPath + ".$first");
            assertPlaceholderPath(props, "$last", expectedPath + ".$last");

            // Verify result data types for unconditional properties
            assertResultDataType(props, "$count", "NUMBER");
            assertResultDataType(props, "$first", "OBJECT");
            assertResultDataType(props, "$last", "OBJECT");

            // Per-child verification
            List<ParameterDefinition> children = childrenMap.getOrDefault(
                    arrayParam.getId(), Collections.emptyList());

            for (ParameterDefinition child : children) {
                String dt = child.getDataType();
                String fn = child.getName();

                if ("ARRAY".equals(dt) || "OBJECT".equals(dt)) {
                    // Structural children should NOT produce field-level aggregations
                    assertFalse(propNames.contains("$sum_" + fn),
                            "ARRAY/OBJECT child should not produce $sum_");
                    continue;
                }

                if ("NUMBER".equals(dt)) {
                    assertTrue(propNames.contains("$sum_" + fn),
                            "NUMBER child '" + fn + "' should produce $sum_" + fn);
                    assertTrue(propNames.contains("$avg_" + fn),
                            "NUMBER child '" + fn + "' should produce $avg_" + fn);
                    assertTrue(propNames.contains("$min_" + fn),
                            "NUMBER child '" + fn + "' should produce $min_" + fn);
                    assertTrue(propNames.contains("$max_" + fn),
                            "NUMBER child '" + fn + "' should produce $max_" + fn);

                    // Verify placeholder paths
                    assertPlaceholderPath(props, "$sum_" + fn, expectedPath + ".$sum_" + fn);
                    assertPlaceholderPath(props, "$avg_" + fn, expectedPath + ".$avg_" + fn);
                    assertPlaceholderPath(props, "$min_" + fn, expectedPath + ".$min_" + fn);
                    assertPlaceholderPath(props, "$max_" + fn, expectedPath + ".$max_" + fn);

                    // Verify result data types
                    assertResultDataType(props, "$sum_" + fn, "NUMBER");
                    assertResultDataType(props, "$avg_" + fn, "NUMBER");
                    assertResultDataType(props, "$min_" + fn, "NUMBER");
                    assertResultDataType(props, "$max_" + fn, "NUMBER");
                } else if ("STRING".equals(dt)) {
                    assertTrue(propNames.contains("$join_" + fn),
                            "STRING child '" + fn + "' should produce $join_" + fn);
                    assertPlaceholderPath(props, "$join_" + fn, expectedPath + ".$join_" + fn);
                    assertResultDataType(props, "$join_" + fn, "STRING");
                } else {
                    // BOOLEAN, DATE — no field-level aggregations
                    assertFalse(propNames.contains("$sum_" + fn),
                            "Non-NUMBER/STRING child should not produce $sum_");
                    assertFalse(propNames.contains("$join_" + fn),
                            "Non-NUMBER/STRING child should not produce $join_");
                }
            }

            // Verify no extra unexpected properties beyond what children dictate
            int expectedCount = 3; // $count, $first, $last
            for (ParameterDefinition child : children) {
                String dt = child.getDataType();
                if ("NUMBER".equals(dt)) {
                    expectedCount += 4;
                } else if ("STRING".equals(dt)) {
                    // STRING children expose multiple join delimiter variants (see AggregationResolver).
                    expectedCount += 6;
                }
            }
            assertEquals(expectedCount, props.size(),
                    "Property count should match expected based on child types");
        }
    }


    private void assertPlaceholderPath(List<AggregationPropertyDTO> props, String name, String expectedPath) {
        props.stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .ifPresent(p -> assertEquals(expectedPath, p.placeholderPath(),
                        "Placeholder path for " + name + " should be " + expectedPath));
    }

    private void assertResultDataType(List<AggregationPropertyDTO> props, String name, String expectedType) {
        props.stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .ifPresent(p -> assertEquals(expectedType, p.resultDataType(),
                        "Result data type for " + name + " should be " + expectedType));
    }

    private String computePath(ParameterDefinition param, Map<Long, ParameterDefinition> paramMap) {
        LinkedList<String> segments = new LinkedList<>();
        ParameterDefinition current = param;
        while (current != null) {
            segments.addFirst(current.getName());
            current = current.getParentId() != null ? paramMap.get(current.getParentId()) : null;
        }
        return String.join(".", segments);
    }

    // ═══════════════════════════════════════════════════════════════
    // Records & Generators
    // ═══════════════════════════════════════════════════════════════

    record ParamTreeTestData(List<ParameterDefinition> allParams) {}

    @Provide
    Arbitrary<ParamTreeTestData> randomParameterTree() {
        // Generate a random parameter tree with 1-3 ARRAY params, each with 0-6 children
        // Children can be NUMBER, STRING, BOOLEAN, DATE, DERIVED NUMBER, DERIVED STRING, nested ARRAY
        return Arbitraries.integers().between(1, 3).flatMap(arrayCount -> {
            List<Arbitrary<List<ParameterDefinition>>> paramArbitraries = new ArrayList<>();

            for (int a = 0; a < arrayCount; a++) {
                final int arrayIndex = a;
                Arbitrary<List<ParameterDefinition>> singleArray = generateArrayWithChildren(arrayIndex);
                paramArbitraries.add(singleArray);
            }

            return Combinators.combine(paramArbitraries).as(lists -> {
                List<ParameterDefinition> all = new ArrayList<>();
                for (List<ParameterDefinition> list : lists) {
                    all.addAll(list);
                }
                return new ParamTreeTestData(all);
            });
        });
    }

    private Arbitrary<List<ParameterDefinition>> generateArrayWithChildren(int arrayIndex) {
        long baseId = (arrayIndex + 1) * 100L;

        Arbitrary<String> arrayNameArb = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8)
                .map(s -> s.toLowerCase() + "_" + arrayIndex);

        // Child types: NUMBER, STRING, BOOLEAN, DATE, DERIVED+NUMBER, DERIVED+STRING, ARRAY (nested)
        Arbitrary<String[]> childTypeArb = Arbitraries.of(
                new String[]{"REQUEST", "NUMBER"},
                new String[]{"REQUEST", "STRING"},
                new String[]{"REQUEST", "BOOLEAN"},
                new String[]{"REQUEST", "DATE"},
                new String[]{"DERIVED", "NUMBER"},
                new String[]{"DERIVED", "STRING"}
        );

        return Combinators.combine(
                arrayNameArb,
                Arbitraries.integers().between(0, 6),
                childTypeArb.list().ofMinSize(0).ofMaxSize(6)
        ).as((arrayName, childCount, childTypes) -> {
            List<ParameterDefinition> params = new ArrayList<>();

            // Create ARRAY parameter
            ParameterDefinition arrayParam = makeParam(baseId, null, arrayName, "REQUEST", "ARRAY", arrayIndex);
            params.add(arrayParam);

            // Create children (up to childCount, limited by childTypes size)
            int actualChildren = Math.min(childCount, childTypes.size());
            Set<String> usedNames = new HashSet<>();
            for (int c = 0; c < actualChildren; c++) {
                String[] typeInfo = childTypes.get(c);
                String childName = "field_" + arrayIndex + "_" + c;
                if (usedNames.contains(childName)) continue;
                usedNames.add(childName);

                ParameterDefinition child = makeParam(
                        baseId + c + 1, baseId, childName,
                        typeInfo[0], typeInfo[1], c);
                params.add(child);
            }

            return params;
        });
    }
}

