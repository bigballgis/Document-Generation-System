package com.docgen.service;

import com.docgen.entity.ExpressionType;
import com.docgen.entity.ParameterDefinition;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.ParameterRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Validates request parameters against Parameter_Table definitions during document generation.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Validate all REQUEST params against Parameter_Table (type, required, validation_rules)</li>
 *   <li>Apply default values for missing optional params</li>
 *   <li>Recursively validate OBJECT and ARRAY nested structures</li>
 *   <li>Evaluate DERIVED params in sort_order with context accumulation</li>
 *   <li>Collect all errors (not fail-fast) with full parameter paths</li>
 * </ul>
 */
@Service
public class ParameterValidationService {

    private static final Logger log = LoggerFactory.getLogger(ParameterValidationService.class);

    private final ParameterRepository parameterRepository;
    private final ExpressionEngine expressionEngine;
    private final ObjectMapper objectMapper;
    private final AggregationResolver aggregationResolver;

    public ParameterValidationService(ParameterRepository parameterRepository,
                                      ExpressionEngine expressionEngine,
                                      ObjectMapper objectMapper,
                                      AggregationResolver aggregationResolver) {
        this.parameterRepository = parameterRepository;
        this.expressionEngine = expressionEngine;
        this.objectMapper = objectMapper;
        this.aggregationResolver = aggregationResolver;
    }

    /**
     * Validate all REQUEST parameters against the Parameter_Table, apply defaults,
     * evaluate DERIVED parameters, and return the complete data context.
     * <p>
     * If the template has zero Parameter_Definitions, raw input params are returned directly.
     *
     * @param templateId  the template ID
     * @param inputParams the caller-provided parameters
     * @return complete data context (REQUEST values + DERIVED computed values)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> validateAndBuildContext(Long templateId, Map<String, Object> inputParams) {
        List<ParameterDefinition> allParams = parameterRepository.findByTemplateIdOrderBySortOrderAsc(templateId);

        // Requirement 6.7: skip validation when template has zero Parameter_Definitions
        if (allParams.isEmpty()) {
            log.debug("Template {} has no parameter definitions, passing raw params", templateId);
            return inputParams != null ? new LinkedHashMap<>(inputParams) : new LinkedHashMap<>();
        }

        Map<String, Object> safeInput = inputParams != null ? inputParams : Collections.emptyMap();

        // Separate root-level params and build parent-children map
        List<ParameterDefinition> rootParams = new ArrayList<>();
        Map<Long, List<ParameterDefinition>> childrenByParentId = new LinkedHashMap<>();
        for (ParameterDefinition param : allParams) {
            if (param.getParentId() == null) {
                rootParams.add(param);
            } else {
                childrenByParentId.computeIfAbsent(param.getParentId(), k -> new ArrayList<>()).add(param);
            }
        }

        // Collect all validation errors
        List<ValidationError> errors = new ArrayList<>();

        // Build context from REQUEST parameters
        Map<String, Object> context = new LinkedHashMap<>();
        for (ParameterDefinition param : rootParams) {
            if ("REQUEST".equals(param.getParameterType())) {
                Object value = safeInput.get(param.getName());
                processRequestParameter(param, value, param.getName(), context, errors, childrenByParentId);
            }
        }

        // Throw collected validation errors before evaluating DERIVED params
        if (!errors.isEmpty()) {
            String message = buildErrorMessage(errors);
            throw new BusinessException(ErrorCode.PARAMETER_VALIDATION_FAILED, message, HttpStatus.BAD_REQUEST);
        }

        // Step 2: Evaluate nested-level DERIVED parameters (Row_Level_Derived + Nested_Derived)
        evaluateNestedDerivedParameters(context, rootParams, childrenByParentId);

        // Step 3: Compute aggregation properties for each ARRAY
        aggregationResolver.computeAggregations(context, rootParams, childrenByParentId);

        // Step 4: Evaluate root-level DERIVED parameters
        evaluateDerivedParameters(templateId, context, rootParams, childrenByParentId);

        // Step 5: Return complete context
        return context;
    }


    /**
     * Process a single REQUEST parameter: check required/default, validate type, apply rules,
     * and recurse into nested structures.
     */
    private void processRequestParameter(ParameterDefinition param, Object value, String path,
                                          Map<String, Object> context,
                                          List<ValidationError> errors,
                                          Map<Long, List<ParameterDefinition>> childrenByParentId) {
        // Handle missing value
        if (value == null) {
            if (param.isRequired()) {
                if (param.getDefaultValue() != null) {
                    // Requirement 6.4: use default_value
                    Object converted = convertDefaultValue(param.getDefaultValue(), param.getDataType());
                    context.put(param.getName(), converted);
                    return;
                } else {
                    // Requirement 6.2: missing required
                    errors.add(new ValidationError(path,
                            "Required parameter missing: " + path, "required"));
                    return;
                }
            }
            // Not required and no value — apply default if available, otherwise skip
            if (param.getDefaultValue() != null) {
                Object converted = convertDefaultValue(param.getDefaultValue(), param.getDataType());
                context.put(param.getName(), converted);
            }
            // Apply not_null validation rule if defined
            Map<String, Object> rules = parseValidationRules(param.getValidationRules());
            if (rules != null) {
                applyValidationRules(param, null, path, errors);
            }
            return;
        }

        // Validate type
        validateParameterValue(param, value, path, errors);

        // Apply validation rules
        applyValidationRules(param, value, path, errors);

        // Handle nested structures
        String dataType = param.getDataType();
        List<ParameterDefinition> children = childrenByParentId.getOrDefault(
                param.getId(), Collections.emptyList());

        if ("OBJECT".equals(dataType) && value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> objectValue = (Map<String, Object>) value;
            Map<String, Object> validatedObject = new LinkedHashMap<>();
            validateNestedObject(children, objectValue, path, validatedObject, errors, childrenByParentId);
            context.put(param.getName(), validatedObject);
        } else if ("ARRAY".equals(dataType) && value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> arrayValue = (List<Object>) value;
            List<Object> validatedArray = new ArrayList<>();
            validateNestedArray(children, arrayValue, path, validatedArray, errors, childrenByParentId);
            context.put(param.getName(), validatedArray);
        } else {
            context.put(param.getName(), value);
        }
    }

    /**
     * Validate a single parameter value against its expected data_type.
     * Requirement 6.3: type mismatch detection.
     */
    void validateParameterValue(ParameterDefinition param, Object value, String path,
                                List<ValidationError> errors) {
        if (value == null) {
            return; // null handling is done in processRequestParameter
        }

        String dataType = param.getDataType();
        boolean typeMatch = switch (dataType) {
            case "STRING" -> value instanceof String;
            case "NUMBER" -> value instanceof Number;
            case "BOOLEAN" -> value instanceof Boolean;
            case "DATE" -> value instanceof String; // dates are passed as strings
            case "OBJECT" -> value instanceof Map;
            case "ARRAY" -> value instanceof List;
            default -> true;
        };

        if (!typeMatch) {
            errors.add(new ValidationError(path,
                    "Parameter type mismatch: " + path + ", expected " + dataType + ", actual " + value.getClass().getSimpleName(),
                    "type_mismatch"));
        }
    }

    /**
     * Apply validation_rules to a parameter value.
     * Requirements 6.10-6.18: validate not_null, not_blank, min_length, max_length,
     * min, max, pattern, enum_values, min_items, max_items; use custom_message when defined.
     */
    void applyValidationRules(ParameterDefinition param, Object value, String path,
                              List<ValidationError> errors) {
        Map<String, Object> rules = parseValidationRules(param.getValidationRules());
        if (rules == null || rules.isEmpty()) {
            return;
        }

        String customMessage = rules.containsKey("custom_message")
                ? String.valueOf(rules.get("custom_message")) : null;

        // not_null
        if (Boolean.TRUE.equals(rules.get("not_null")) && value == null) {
            errors.add(new ValidationError(path,
                    customMessage != null ? customMessage : "Parameter cannot be null: " + path,
                    "not_null"));
            return; // no further checks if null
        }

        if (value == null) {
            return;
        }

        // not_blank (STRING only)
        if (Boolean.TRUE.equals(rules.get("not_blank")) && value instanceof String s) {
            if (s.isBlank()) {
                errors.add(new ValidationError(path,
                        customMessage != null ? customMessage : "Parameter cannot be blank: " + path,
                        "not_blank"));
            }
        }

        // min_length / max_length (STRING)
        if (value instanceof String s) {
            if (rules.containsKey("min_length")) {
                int minLength = toInt(rules.get("min_length"));
                if (s.length() < minLength) {
                    errors.add(new ValidationError(path,
                            customMessage != null ? customMessage
                                    : "String length cannot be less than " + minLength + ", actual length " + s.length() + ": " + path,
                            "min_length"));
                }
            }
            if (rules.containsKey("max_length")) {
                int maxLength = toInt(rules.get("max_length"));
                if (s.length() > maxLength) {
                    errors.add(new ValidationError(path,
                            customMessage != null ? customMessage
                                    : "String length cannot be greater than " + maxLength + ", actual length " + s.length() + ": " + path,
                            "max_length"));
                }
            }
        }

        // min / max (NUMBER)
        if (value instanceof Number num) {
            BigDecimal actual = new BigDecimal(num.toString());
            if (rules.containsKey("min")) {
                BigDecimal min = toBigDecimal(rules.get("min"));
                if (actual.compareTo(min) < 0) {
                    errors.add(new ValidationError(path,
                            customMessage != null ? customMessage
                                    : "Number cannot be less than " + min + ", actual value " + actual + ": " + path,
                            "min"));
                }
            }
            if (rules.containsKey("max")) {
                BigDecimal max = toBigDecimal(rules.get("max"));
                if (actual.compareTo(max) > 0) {
                    errors.add(new ValidationError(path,
                            customMessage != null ? customMessage
                                    : "Number cannot be greater than " + max + ", actual value " + actual + ": " + path,
                            "max"));
                }
            }
        }

        // pattern (STRING)
        if (rules.containsKey("pattern") && value instanceof String s) {
            String pattern = String.valueOf(rules.get("pattern"));
            if (!s.matches(pattern)) {
                errors.add(new ValidationError(path,
                        customMessage != null ? customMessage
                                : "Parameter does not match pattern " + pattern + ": " + path,
                        "pattern"));
            }
        }

        // enum_values
        if (rules.containsKey("enum_values")) {
            @SuppressWarnings("unchecked")
            List<String> enumValues = (List<String>) rules.get("enum_values");
            String strValue = String.valueOf(value);
            if (!enumValues.contains(strValue)) {
                errors.add(new ValidationError(path,
                        customMessage != null ? customMessage
                                : "Parameter value is not in allowed list " + enumValues + ": " + path,
                        "enum_values"));
            }
        }

        // min_items / max_items (ARRAY)
        if (value instanceof List<?> list) {
            if (rules.containsKey("min_items")) {
                int minItems = toInt(rules.get("min_items"));
                if (list.size() < minItems) {
                    errors.add(new ValidationError(path,
                            customMessage != null ? customMessage
                                    : "Array item count cannot be less than " + minItems + ", actual count " + list.size() + ": " + path,
                            "min_items"));
                }
            }
            if (rules.containsKey("max_items")) {
                int maxItems = toInt(rules.get("max_items"));
                if (list.size() > maxItems) {
                    errors.add(new ValidationError(path,
                            customMessage != null ? customMessage
                                    : "Array item count cannot be greater than " + maxItems + ", actual count " + list.size() + ": " + path,
                            "max_items"));
                }
            }
        }
    }


    /**
     * Recursively validate OBJECT children against JSON object fields.
     * Requirement 6.20: validate each child parameter defined under the OBJECT.
     */
    void validateNestedObject(List<ParameterDefinition> children,
                              Map<String, Object> objectValue,
                              String parentPath,
                              Map<String, Object> validatedObject,
                              List<ValidationError> errors,
                              Map<Long, List<ParameterDefinition>> childrenByParentId) {
        for (ParameterDefinition child : children) {
            if (!"REQUEST".equals(child.getParameterType())) {
                continue;
            }
            String childPath = parentPath + "." + child.getName();
            Object childValue = objectValue.get(child.getName());

            if (childValue == null) {
                if (child.isRequired()) {
                    if (child.getDefaultValue() != null) {
                        Object converted = convertDefaultValue(child.getDefaultValue(), child.getDataType());
                        validatedObject.put(child.getName(), converted);
                        continue;
                    } else {
                        errors.add(new ValidationError(childPath,
                                "Required parameter missing: " + childPath, "required"));
                        continue;
                    }
                }
                if (child.getDefaultValue() != null) {
                    Object converted = convertDefaultValue(child.getDefaultValue(), child.getDataType());
                    validatedObject.put(child.getName(), converted);
                }
                // Apply not_null rule check
                Map<String, Object> rules = parseValidationRules(child.getValidationRules());
                if (rules != null) {
                    applyValidationRules(child, null, childPath, errors);
                }
                continue;
            }

            // Validate type
            validateParameterValue(child, childValue, childPath, errors);

            // Apply validation rules
            applyValidationRules(child, childValue, childPath, errors);

            // Recurse into nested structures
            List<ParameterDefinition> grandChildren = childrenByParentId.getOrDefault(
                    child.getId(), Collections.emptyList());

            if ("OBJECT".equals(child.getDataType()) && childValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedObj = (Map<String, Object>) childValue;
                Map<String, Object> validatedNested = new LinkedHashMap<>();
                validateNestedObject(grandChildren, nestedObj, childPath, validatedNested, errors, childrenByParentId);
                validatedObject.put(child.getName(), validatedNested);
            } else if ("ARRAY".equals(child.getDataType()) && childValue instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> nestedArr = (List<Object>) childValue;
                List<Object> validatedNested = new ArrayList<>();
                validateNestedArray(grandChildren, nestedArr, childPath, validatedNested, errors, childrenByParentId);
                validatedObject.put(child.getName(), validatedNested);
            } else {
                validatedObject.put(child.getName(), childValue);
            }
        }
    }

    /**
     * Validate each array element against ARRAY children definitions.
     * Requirement 6.21: validate each element in the request JSON array.
     * Requirement 6.22: include full parameter path with array index (e.g., "items[2].price").
     */
    void validateNestedArray(List<ParameterDefinition> children,
                             List<Object> arrayValue,
                             String parentPath,
                             List<Object> validatedArray,
                             List<ValidationError> errors,
                             Map<Long, List<ParameterDefinition>> childrenByParentId) {
        for (int i = 0; i < arrayValue.size(); i++) {
            Object element = arrayValue.get(i);
            String elementPath = parentPath + "[" + i + "]";

            if (element instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> elementMap = (Map<String, Object>) element;
                Map<String, Object> validatedElement = new LinkedHashMap<>();
                validateNestedObject(children, elementMap, elementPath, validatedElement, errors, childrenByParentId);
                validatedArray.add(validatedElement);
            } else {
                // Scalar array elements — validate against the first child definition if available
                if (!children.isEmpty()) {
                    ParameterDefinition childDef = children.get(0);
                    validateParameterValue(childDef, element, elementPath, errors);
                    applyValidationRules(childDef, element, elementPath, errors);
                }
                validatedArray.add(element);
            }
        }
    }

    /**
     * Recursively evaluate all nested-level DERIVED parameters (Row_Level_Derived within ARRAY rows,
     * Nested_Derived within OBJECT structures). Inner nesting levels are processed before outer levels.
     *
     * @param context            current data context (already validated REQUEST params)
     * @param rootParams         root-level parameter definitions
     * @param childrenByParentId parentId → child parameter definitions
     */
    void evaluateNestedDerivedParameters(Map<String, Object> context,
                                          List<ParameterDefinition> rootParams,
                                          Map<Long, List<ParameterDefinition>> childrenByParentId) {
        for (ParameterDefinition param : rootParams) {
            String dataType = param.getDataType();
            if ("ARRAY".equals(dataType)) {
                Object value = context.get(param.getName());
                if (value instanceof List<?> arrayData) {
                    @SuppressWarnings("unchecked")
                    List<Object> typedArray = (List<Object>) arrayData;
                    evaluateNestedDerivedForArray(param.getName(), typedArray,
                            childrenByParentId.getOrDefault(param.getId(), Collections.emptyList()),
                            childrenByParentId);
                }
            } else if ("OBJECT".equals(dataType)) {
                Object value = context.get(param.getName());
                if (value instanceof Map<?, ?> objectData) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedObject = (Map<String, Object>) objectData;
                    evaluateNestedDerivedForObject(param.getName(), typedObject,
                            childrenByParentId.getOrDefault(param.getId(), Collections.emptyList()),
                            childrenByParentId);
                }
            }
        }
    }

    /**
     * Evaluate DERIVED children within each row of an ARRAY parameter.
     * Recursively processes inner ARRAY/OBJECT children before evaluating DERIVED at current level.
     */
    private void evaluateNestedDerivedForArray(String arrayPath,
                                                List<Object> arrayData,
                                                List<ParameterDefinition> children,
                                                Map<Long, List<ParameterDefinition>> childrenByParentId) {
        if (arrayData.isEmpty()) {
            return; // Skip empty arrays without error
        }

        for (int rowIndex = 0; rowIndex < arrayData.size(); rowIndex++) {
            Object element = arrayData.get(rowIndex);
            if (!(element instanceof Map<?, ?>)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> rowMap = (Map<String, Object>) element;

            // First: recurse into nested ARRAY/OBJECT children (inner before outer)
            for (ParameterDefinition child : children) {
                if ("ARRAY".equals(child.getDataType())) {
                    Object nestedValue = rowMap.get(child.getName());
                    if (nestedValue instanceof List<?> nestedArray) {
                        @SuppressWarnings("unchecked")
                        List<Object> typedNested = (List<Object>) nestedArray;
                        evaluateNestedDerivedForArray(
                                arrayPath + "[" + rowIndex + "]." + child.getName(),
                                typedNested,
                                childrenByParentId.getOrDefault(child.getId(), Collections.emptyList()),
                                childrenByParentId);
                    }
                } else if ("OBJECT".equals(child.getDataType())) {
                    Object nestedValue = rowMap.get(child.getName());
                    if (nestedValue instanceof Map<?, ?> nestedObj) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> typedNested = (Map<String, Object>) nestedObj;
                        evaluateNestedDerivedForObject(
                                arrayPath + "[" + rowIndex + "]." + child.getName(),
                                typedNested,
                                childrenByParentId.getOrDefault(child.getId(), Collections.emptyList()),
                                childrenByParentId);
                    }
                }
            }

            // Then: evaluate DERIVED children in sort_order with row-scoped context
            List<ParameterDefinition> derivedChildren = children.stream()
                    .filter(c -> "DERIVED".equals(c.getParameterType()))
                    .sorted(Comparator.comparingInt(ParameterDefinition::getSortOrder))
                    .toList();

            // Build row-scoped context: only sibling field values from this row
            Map<String, Object> rowContext = new LinkedHashMap<>(rowMap);

            for (ParameterDefinition derived : derivedChildren) {
                try {
                    ExpressionType exprType = ExpressionType.valueOf(derived.getExpressionType());
                    Object result = expressionEngine.evaluate(
                            derived.getExpressionText(), exprType, rowContext);
                    rowMap.put(derived.getName(), result);
                    rowContext.put(derived.getName(), result);
                    log.debug("Evaluated Row_Level_Derived '{}' at {}[{}] = {}",
                            derived.getName(), arrayPath, rowIndex, result);
                } catch (Exception e) {
                    throw new BusinessException(ErrorCode.PARAMETER_EXPRESSION_EVALUATION_FAILED,
                            "Row-level derived parameter evaluation failed: " + arrayPath + "[" + rowIndex + "]." + derived.getName()
                                    + " — " + e.getMessage(),
                            HttpStatus.BAD_REQUEST, e);
                }
            }
        }
    }

    /**
     * Evaluate DERIVED children within an OBJECT parameter.
     * Recursively processes inner ARRAY/OBJECT children before evaluating DERIVED at current level.
     */
    private void evaluateNestedDerivedForObject(String objectPath,
                                                 Map<String, Object> objectData,
                                                 List<ParameterDefinition> children,
                                                 Map<Long, List<ParameterDefinition>> childrenByParentId) {
        // First: recurse into nested ARRAY/OBJECT children (inner before outer)
        for (ParameterDefinition child : children) {
            if ("ARRAY".equals(child.getDataType())) {
                Object nestedValue = objectData.get(child.getName());
                if (nestedValue instanceof List<?> nestedArray) {
                    @SuppressWarnings("unchecked")
                    List<Object> typedNested = (List<Object>) nestedArray;
                    evaluateNestedDerivedForArray(
                            objectPath + "." + child.getName(),
                            typedNested,
                            childrenByParentId.getOrDefault(child.getId(), Collections.emptyList()),
                            childrenByParentId);
                }
            } else if ("OBJECT".equals(child.getDataType())) {
                Object nestedValue = objectData.get(child.getName());
                if (nestedValue instanceof Map<?, ?> nestedObj) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedNested = (Map<String, Object>) nestedObj;
                    evaluateNestedDerivedForObject(
                            objectPath + "." + child.getName(),
                            typedNested,
                            childrenByParentId.getOrDefault(child.getId(), Collections.emptyList()),
                            childrenByParentId);
                }
            }
        }

        // Then: evaluate DERIVED children in sort_order with object-scoped context
        List<ParameterDefinition> derivedChildren = children.stream()
                .filter(c -> "DERIVED".equals(c.getParameterType()))
                .sorted(Comparator.comparingInt(ParameterDefinition::getSortOrder))
                .toList();

        Map<String, Object> scopedContext = new LinkedHashMap<>(objectData);

        for (ParameterDefinition derived : derivedChildren) {
            try {
                ExpressionType exprType = ExpressionType.valueOf(derived.getExpressionType());
                Object result = expressionEngine.evaluate(
                        derived.getExpressionText(), exprType, scopedContext);
                objectData.put(derived.getName(), result);
                scopedContext.put(derived.getName(), result);
                log.debug("Evaluated Nested_Derived '{}' at {} = {}",
                        derived.getName(), objectPath, result);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.PARAMETER_EXPRESSION_EVALUATION_FAILED,
                        "Nested derived parameter evaluation failed: " + objectPath + "." + derived.getName()
                                + " — " + e.getMessage(),
                        HttpStatus.BAD_REQUEST, e);
            }
        }
    }

    /**
     * Evaluate DERIVED parameters in sort_order.
     * Requirement 5.2: each DERIVED param has access to all REQUEST + previously evaluated DERIVED params.
     * Requirement 5.5: support referencing both REQUEST and other DERIVED parameters.
     */
    void evaluateDerivedParameters(Long templateId, Map<String, Object> context,
                                   List<ParameterDefinition> rootParams,
                                   Map<Long, List<ParameterDefinition>> childrenByParentId) {
        // Collect all DERIVED params (root-level only for now) sorted by sort_order
        List<ParameterDefinition> derivedParams = rootParams.stream()
                .filter(p -> "DERIVED".equals(p.getParameterType()))
                .sorted(Comparator.comparingInt(ParameterDefinition::getSortOrder))
                .toList();

        for (ParameterDefinition derived : derivedParams) {
            try {
                ExpressionType exprType = ExpressionType.valueOf(derived.getExpressionType());
                Object result = expressionEngine.evaluate(
                        derived.getExpressionText(), exprType, context);
                context.put(derived.getName(), result);
                log.debug("Evaluated DERIVED param '{}' = {}", derived.getName(), result);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.PARAMETER_EXPRESSION_EVALUATION_FAILED,
                        "Invalid derived parameter expression type: " + derived.getName() + ", type=" + derived.getExpressionType(),
                        HttpStatus.BAD_REQUEST, e);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.PARAMETER_EXPRESSION_EVALUATION_FAILED,
                        "Derived parameter evaluation failed: " + derived.getName() + " — " + e.getMessage(),
                        HttpStatus.BAD_REQUEST, e);
            }
        }
    }



    /**
     * Convert a default_value string to the appropriate Java type based on data_type.
     */
    private Object convertDefaultValue(String defaultValue, String dataType) {
        if (defaultValue == null) {
            return null;
        }
        try {
            return switch (dataType) {
                case "NUMBER" -> new BigDecimal(defaultValue);
                case "BOOLEAN" -> Boolean.parseBoolean(defaultValue);
                case "DATE", "STRING" -> defaultValue;
                case "OBJECT" -> objectMapper.readValue(defaultValue, new TypeReference<Map<String, Object>>() {});
                case "ARRAY" -> objectMapper.readValue(defaultValue, new TypeReference<List<Object>>() {});
                default -> defaultValue;
            };
        } catch (Exception e) {
            log.warn("Failed to convert default value '{}' for type {}: {}", defaultValue, dataType, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Parse validation_rules JSON string into a Map.
     */
    Map<String, Object> parseValidationRules(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse validation_rules: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Build a combined error message from all collected validation errors.
     */
    private String buildErrorMessage(List<ValidationError> errors) {
        return "Parameter validation failed: " + errors.stream()
                .map(ValidationError::message)
                .collect(Collectors.joining("; "));
    }

    private int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
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

    /**
     * Validation error record with path, message, and rule key.
     */
    public record ValidationError(String path, String message, String ruleKey) {}
}
