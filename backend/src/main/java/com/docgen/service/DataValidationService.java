package com.docgen.service;

import com.docgen.dto.DataValidationError;
import com.docgen.dto.DataValidationResult;
import com.docgen.dto.ValidationRule;
import com.docgen.dto.ValidationRule.DataType;
import com.docgen.dto.ValidationRule.RuleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates data maps against a set of {@link ValidationRule}s.
 * <p>
 * Supports: REQUIRED, TYPE, RANGE, LENGTH, REGEX rule types.
 * <p>
 * Validates: Requirements 13.1-13.7
 */
@Service
public class DataValidationService {

    private static final Logger log = LoggerFactory.getLogger(DataValidationService.class);

    /**
     * Validate the given data against the provided rules.
     *
     * @param data  the data map to validate
     * @param rules the list of validation rules
     * @return a {@link DataValidationResult} with errors (empty if all valid)
     */
    public DataValidationResult validate(Map<String, Object> data, List<ValidationRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return DataValidationResult.success();
        }

        List<DataValidationError> errors = new ArrayList<>();
        Map<String, Object> safeData = data != null ? data : Map.of();

        for (ValidationRule rule : rules) {
            DataValidationError error = validateRule(safeData, rule);
            if (error != null) {
                errors.add(error);
            }
        }

        if (errors.isEmpty()) {
            return DataValidationResult.success();
        }
        return DataValidationResult.failure(errors);
    }

    private DataValidationError validateRule(Map<String, Object> data, ValidationRule rule) {
        String field = rule.getFieldName();
        RuleType type = rule.getRuleType();
        Object value = data.get(field);

        return switch (type) {
            case REQUIRED -> validateRequired(field, value);
            case TYPE -> validateType(field, value, rule.getParameters());
            case RANGE -> validateRange(field, value, rule.getParameters());
            case LENGTH -> validateLength(field, value, rule.getParameters());
            case REGEX -> validateRegex(field, value, rule.getParameters());
        };
    }

    // ── REQUIRED ──

    private DataValidationError validateRequired(String field, Object value) {
        if (value == null) {
            return new DataValidationError(field, RuleType.REQUIRED,
                    "Field '" + field + "' is required");
        }
        if (value instanceof String s && s.isEmpty()) {
            return new DataValidationError(field, RuleType.REQUIRED,
                    "Field '" + field + "' is required");
        }
        return null;
    }

    // ── TYPE ──

    private DataValidationError validateType(String field, Object value, Map<String, Object> params) {
        if (value == null) {
            return null; // null values are handled by REQUIRED rule
        }
        DataType expectedType = resolveDataType(params);
        if (expectedType == null) {
            return new DataValidationError(field, RuleType.TYPE,
                    "Invalid type parameter for field '" + field + "'");
        }

        boolean matches = switch (expectedType) {
            case STRING -> value instanceof String;
            case NUMBER -> isNumber(value);
            case BOOLEAN -> isBoolean(value);
            case DATE -> isDate(value);
        };

        if (!matches) {
            return new DataValidationError(field, RuleType.TYPE,
                    "Field '" + field + "' must be of type " + expectedType);
        }
        return null;
    }

    // ── RANGE ──

    private DataValidationError validateRange(String field, Object value, Map<String, Object> params) {
        if (value == null) {
            return null;
        }
        Double numValue = toDouble(value);
        if (numValue == null) {
            return new DataValidationError(field, RuleType.RANGE,
                    "Field '" + field + "' must be numeric for range validation");
        }

        Double min = params != null ? toDouble(params.get("min")) : null;
        Double max = params != null ? toDouble(params.get("max")) : null;

        if (min != null && numValue < min) {
            return new DataValidationError(field, RuleType.RANGE,
                    "Field '" + field + "' must be >= " + min);
        }
        if (max != null && numValue > max) {
            return new DataValidationError(field, RuleType.RANGE,
                    "Field '" + field + "' must be <= " + max);
        }
        return null;
    }

    // ── LENGTH ──

    private DataValidationError validateLength(String field, Object value, Map<String, Object> params) {
        if (value == null) {
            return null;
        }
        String strValue = value.toString();
        int length = strValue.length();

        Integer min = params != null ? toInteger(params.get("min")) : null;
        Integer max = params != null ? toInteger(params.get("max")) : null;

        if (min != null && length < min) {
            return new DataValidationError(field, RuleType.LENGTH,
                    "Field '" + field + "' length must be >= " + min);
        }
        if (max != null && length > max) {
            return new DataValidationError(field, RuleType.LENGTH,
                    "Field '" + field + "' length must be <= " + max);
        }
        return null;
    }

    // ── REGEX ──

    private DataValidationError validateRegex(String field, Object value, Map<String, Object> params) {
        if (value == null) {
            return null;
        }
        String pattern = params != null ? (String) params.get("pattern") : null;
        if (pattern == null) {
            return new DataValidationError(field, RuleType.REGEX,
                    "Missing regex pattern for field '" + field + "'");
        }
        try {
            if (!Pattern.matches(pattern, value.toString())) {
                return new DataValidationError(field, RuleType.REGEX,
                        "Field '" + field + "' does not match pattern '" + pattern + "'");
            }
        } catch (PatternSyntaxException e) {
            log.warn("Invalid regex pattern '{}' for field '{}': {}", pattern, field, e.getMessage());
            return new DataValidationError(field, RuleType.REGEX,
                    "Invalid regex pattern for field '" + field + "'");
        }
        return null;
    }

    // ── Helpers ──

    private DataType resolveDataType(Map<String, Object> params) {
        if (params == null) return null;
        Object typeParam = params.get("type");
        if (typeParam instanceof DataType dt) return dt;
        if (typeParam instanceof String s) {
            try {
                return DataType.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private boolean isNumber(Object value) {
        if (value instanceof Number) return true;
        if (value instanceof String s) {
            try {
                Double.parseDouble(s);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private boolean isBoolean(Object value) {
        if (value instanceof Boolean) return true;
        if (value instanceof String s) {
            return "true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s);
        }
        return false;
    }

    private boolean isDate(Object value) {
        if (value instanceof Date || value instanceof LocalDate || value instanceof LocalDateTime) {
            return true;
        }
        if (value instanceof String s) {
            try {
                LocalDate.parse(s);
                return true;
            } catch (DateTimeParseException e1) {
                try {
                    LocalDateTime.parse(s);
                    return true;
                } catch (DateTimeParseException e2) {
                    return false;
                }
            }
        }
        return false;
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
