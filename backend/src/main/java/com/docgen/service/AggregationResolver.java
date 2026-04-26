package com.docgen.service;

import com.docgen.dto.AggregationPropertyDTO;
import com.docgen.dto.AggregationSchemaDTO;
import com.docgen.entity.ParameterDefinition;
import com.docgen.repository.ParameterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Computes aggregation properties ($count, $sum_*, $avg_*, $min_*, $max_*, $join_*, $first, $last)
 * for ARRAY parameters and injects them into the data context as flat keys.
 * <p>
 * Recursively processes nested ARRAYs (inner before outer).
 */
@Service
public class AggregationResolver {

    private static final Logger log = LoggerFactory.getLogger(AggregationResolver.class);

    private final ParameterRepository parameterRepository;

    public AggregationResolver(ParameterRepository parameterRepository) {
        this.parameterRepository = parameterRepository;
    }

    /**
     * Compute and inject aggregation properties for all ARRAY parameters in the context.
     * Recursively processes nested ARRAYs (inner arrays before outer).
     *
     * @param context     current data context (already validated + nested DERIVED evaluated)
     * @param rootParams  root-level parameter definitions
     * @param childrenMap parentId → child parameter definitions
     */
    public void computeAggregations(Map<String, Object> context,
                                     List<ParameterDefinition> rootParams,
                                     Map<Long, List<ParameterDefinition>> childrenMap) {
        for (ParameterDefinition param : rootParams) {
            if ("ARRAY".equals(param.getDataType())) {
                Object value = context.get(param.getName());
                if (value instanceof List<?> arrayData) {
                    List<ParameterDefinition> children = childrenMap.getOrDefault(
                            param.getId(), Collections.emptyList());
                    @SuppressWarnings("unchecked")
                    List<Object> typedArray = (List<Object>) arrayData;
                    computeForArray(param.getName(), typedArray, children, context, childrenMap);
                }
            }
        }
    }

    /**
     * Compute aggregation properties for a single ARRAY parameter.
     * Processes nested ARRAYs within each element first (inner before outer).
     */
    void computeForArray(String arrayName,
                         List<Object> arrayData,
                         List<ParameterDefinition> children,
                         Map<String, Object> parentContext,
                         Map<Long, List<ParameterDefinition>> childrenMap) {
        // Step 1: Recursively process nested ARRAYs within each element (inner before outer)
        for (Object element : arrayData) {
            if (element instanceof Map<?, ?> elementMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedElement = (Map<String, Object>) elementMap;
                for (ParameterDefinition child : children) {
                    if ("ARRAY".equals(child.getDataType())) {
                        Object nestedValue = typedElement.get(child.getName());
                        if (nestedValue instanceof List<?> nestedArray) {
                            List<ParameterDefinition> grandChildren = childrenMap.getOrDefault(
                                    child.getId(), Collections.emptyList());
                            @SuppressWarnings("unchecked")
                            List<Object> typedNested = (List<Object>) nestedArray;
                            computeForArray(child.getName(), typedNested, grandChildren, typedElement, childrenMap);
                        }
                    }
                }
            }
        }

        // Step 2: Compute aggregations for this ARRAY
        int count = arrayData.size();
        parentContext.put(arrayName + ".$count", count);

        // $first and $last
        parentContext.put(arrayName + ".$first", count > 0 ? arrayData.get(0) : null);
        parentContext.put(arrayName + ".$last", count > 0 ? arrayData.get(count - 1) : null);

        // Per-child aggregations
        for (ParameterDefinition child : children) {
            if ("ARRAY".equals(child.getDataType()) || "OBJECT".equals(child.getDataType())) {
                continue; // Skip structural children for field-level aggregations
            }
            String fieldName = child.getName();
            String dataType = child.getDataType();

            List<Object> fieldValues = extractFieldValues(arrayData, fieldName);

            if ("NUMBER".equals(dataType)) {
                computeNumericAggregations(arrayName, fieldName, fieldValues, parentContext);
            } else if ("STRING".equals(dataType)) {
                computeStringJoin(arrayName, fieldName, fieldValues, parentContext);
            }
        }

        log.debug("Computed aggregations for ARRAY '{}': $count={}", arrayName, count);
    }

    /**
     * Extract field values from array elements (each element is expected to be a Map).
     */
    private List<Object> extractFieldValues(List<Object> arrayData, String fieldName) {
        List<Object> values = new ArrayList<>(arrayData.size());
        for (Object element : arrayData) {
            if (element instanceof Map<?, ?> map) {
                values.add(map.get(fieldName));
            } else {
                values.add(null);
            }
        }
        return values;
    }

    /**
     * Compute $sum_, $avg_, $min_, $max_ for a NUMBER field.
     */
    void computeNumericAggregations(String arrayName, String fieldName,
                                     List<Object> values,
                                     Map<String, Object> parentContext) {
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal min = null;
        BigDecimal max = null;
        int nonNullCount = 0;

        for (Object val : values) {
            if (val == null) {
                continue;
            }
            BigDecimal bd = toBigDecimal(val);
            sum = sum.add(bd);
            nonNullCount++;
            if (min == null || bd.compareTo(min) < 0) {
                min = bd;
            }
            if (max == null || bd.compareTo(max) > 0) {
                max = bd;
            }
        }

        parentContext.put(arrayName + ".$sum_" + fieldName, sum);

        BigDecimal avg;
        if (nonNullCount > 0) {
            avg = sum.divide(BigDecimal.valueOf(nonNullCount), 2, RoundingMode.HALF_UP);
        } else {
            avg = BigDecimal.ZERO;
        }
        parentContext.put(arrayName + ".$avg_" + fieldName, avg);

        parentContext.put(arrayName + ".$min_" + fieldName, min);
        parentContext.put(arrayName + ".$max_" + fieldName, max);
    }

    /**
     * Compute $join_ for a STRING field.
     * Generates default join (", ") and common separator variants.
     */
    void computeStringJoin(String arrayName, String fieldName,
                            List<Object> values,
                            Map<String, Object> parentContext) {
        List<String> nonNullStrings = new ArrayList<>();
        for (Object val : values) {
            if (val != null) {
                nonNullStrings.add(String.valueOf(val));
            }
        }
        // Default separator: ", "
        parentContext.put(arrayName + ".$join_" + fieldName, String.join(", ", nonNullStrings));
        // Common separator variants
        Map<String, String> separators = Map.of(
                ";", "; ",
                "、", "、",
                "|", " | ",
                "/", "/",
                "\\n", "\n"
        );
        for (var entry : separators.entrySet()) {
            String key = arrayName + ".$join(" + entry.getKey() + ")_" + fieldName;
            parentContext.put(key, String.join(entry.getValue(), nonNullStrings));
        }
    }

    /**
     * Get aggregation schema for all ARRAY parameters in a template.
     * Returns the list of available aggregation properties per ARRAY parameter,
     * including placeholder paths using the full parameter path prefix.
     *
     * @param templateId template ID
     * @return aggregation schema list (one entry per ARRAY parameter)
     */
    public List<AggregationSchemaDTO> getAggregationSchema(Long templateId) {
        List<ParameterDefinition> allParams = parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);
        if (allParams.isEmpty()) {
            return Collections.emptyList();
        }

        // Build parent-children map
        Map<Long, List<ParameterDefinition>> childrenMap = new LinkedHashMap<>();
        Map<Long, ParameterDefinition> paramMap = new LinkedHashMap<>();
        for (ParameterDefinition p : allParams) {
            paramMap.put(p.getId(), p);
            if (p.getParentId() != null) {
                childrenMap.computeIfAbsent(p.getParentId(), k -> new ArrayList<>()).add(p);
            }
        }

        // Find all ARRAY parameters at any nesting level
        List<AggregationSchemaDTO> result = new ArrayList<>();
        for (ParameterDefinition param : allParams) {
            if ("ARRAY".equals(param.getDataType())) {
                String arrayPath = computeParameterPath(param, paramMap);
                List<AggregationPropertyDTO> properties = buildSchemaProperties(
                        arrayPath, childrenMap.getOrDefault(param.getId(), Collections.emptyList()));
                result.add(new AggregationSchemaDTO(param.getName(), arrayPath, properties));
            }
        }
        return result;
    }

    /**
     * Compute parameter path by traversing the parent chain.
     */
    private String computeParameterPath(ParameterDefinition param, Map<Long, ParameterDefinition> paramMap) {
        LinkedList<String> segments = new LinkedList<>();
        ParameterDefinition current = param;
        while (current != null) {
            segments.addFirst(current.getName());
            current = current.getParentId() != null ? paramMap.get(current.getParentId()) : null;
        }
        return String.join(".", segments);
    }

    /**
     * Build aggregation property DTOs for a single ARRAY parameter.
     */
    private List<AggregationPropertyDTO> buildSchemaProperties(String arrayPath,
                                                                List<ParameterDefinition> children) {
        List<AggregationPropertyDTO> props = new ArrayList<>();

        // Unconditional properties
        props.add(new AggregationPropertyDTO("$count", arrayPath + ".$count", "NUMBER", "数组元素数量"));
        props.add(new AggregationPropertyDTO("$first", arrayPath + ".$first", "OBJECT", "第一个元素"));
        props.add(new AggregationPropertyDTO("$last", arrayPath + ".$last", "OBJECT", "最后一个元素"));

        // Per-child aggregation properties
        for (ParameterDefinition child : children) {
            String dataType = child.getDataType();
            // Skip structural children (ARRAY, OBJECT) — they don't produce field-level aggregations
            if ("ARRAY".equals(dataType) || "OBJECT".equals(dataType)) {
                continue;
            }
            String fieldName = child.getName();

            if ("NUMBER".equals(dataType)) {
                props.add(new AggregationPropertyDTO("$sum_" + fieldName,
                        arrayPath + ".$sum_" + fieldName, "NUMBER", fieldName + " 求和"));
                props.add(new AggregationPropertyDTO("$avg_" + fieldName,
                        arrayPath + ".$avg_" + fieldName, "NUMBER", fieldName + " 平均值"));
                props.add(new AggregationPropertyDTO("$min_" + fieldName,
                        arrayPath + ".$min_" + fieldName, "NUMBER", fieldName + " 最小值"));
                props.add(new AggregationPropertyDTO("$max_" + fieldName,
                        arrayPath + ".$max_" + fieldName, "NUMBER", fieldName + " 最大值"));
            } else if ("STRING".equals(dataType)) {
                props.add(new AggregationPropertyDTO("$join_" + fieldName,
                        arrayPath + ".$join_" + fieldName, "STRING", fieldName + " 拼接 (逗号)"));
                props.add(new AggregationPropertyDTO("$join(;)_" + fieldName,
                        arrayPath + ".$join(;)_" + fieldName, "STRING", fieldName + " 拼接 (分号)"));
                props.add(new AggregationPropertyDTO("$join(、)_" + fieldName,
                        arrayPath + ".$join(、)_" + fieldName, "STRING", fieldName + " 拼接 (顿号)"));
                props.add(new AggregationPropertyDTO("$join(|)_" + fieldName,
                        arrayPath + ".$join(|)_" + fieldName, "STRING", fieldName + " 拼接 (竖线)"));
                props.add(new AggregationPropertyDTO("$join(/)_" + fieldName,
                        arrayPath + ".$join(/)_" + fieldName, "STRING", fieldName + " 拼接 (斜线)"));
                props.add(new AggregationPropertyDTO("$join(\\n)_" + fieldName,
                        arrayPath + ".$join(\\n)_" + fieldName, "STRING", fieldName + " 拼接 (换行)"));
            }
        }

        return props;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(String.valueOf(value));
    }
}
