package com.docgen.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Data format conversion service.
 * <p>
 * Supports date format conversion, number formatting (decimal places, thousands separator),
 * data type conversion (String/Number/Boolean), and custom field mapping rules.
 * <p>
 * On conversion failure, logs a warning and returns the original value.
 * <p>
 * Validates: Requirements 7.1-7.6
 */
@Service
public class DataFormatService {

    private static final Logger log = LoggerFactory.getLogger(DataFormatService.class);

    /**
     * Supported target types for {@link #convertType}.
     */
    public enum TargetType {
        STRING, NUMBER, BOOLEAN
    }

    // ── Date format conversion (Req 7.2) ──

    /**
     * Convert a date value from one format to another.
     *
     * @param value        the date string to convert
     * @param sourceFormat the source date pattern (e.g. "yyyy-MM-dd")
     * @param targetFormat the target date pattern (e.g. "dd/MM/yyyy")
     * @return the formatted date string, or the original value on failure
     */
    public String convertDate(String value, String sourceFormat, String targetFormat) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        try {
            DateTimeFormatter srcFmt = DateTimeFormatter.ofPattern(sourceFormat);
            DateTimeFormatter tgtFmt = DateTimeFormatter.ofPattern(targetFormat);

            // Try LocalDateTime first, fall back to LocalDate
            try {
                LocalDateTime dateTime = LocalDateTime.parse(value, srcFmt);
                return dateTime.format(tgtFmt);
            } catch (DateTimeParseException e) {
                LocalDate date = LocalDate.parse(value, srcFmt);
                return date.format(tgtFmt);
            }
        } catch (Exception e) {
            log.warn("Date conversion failed for value '{}' (source='{}', target='{}'): {}",
                    value, sourceFormat, targetFormat, e.getMessage());
            return value;
        }
    }

    // ── Number format conversion (Req 7.3) ──

    /**
     * Format a numeric value with the given decimal places and optional thousands separator.
     *
     * @param value                 the value to format (Number or numeric String)
     * @param decimalPlaces         number of decimal places (null = no rounding)
     * @param useThousandsSeparator whether to include thousands separator
     * @return the formatted number string, or the original value's string on failure
     */
    public String convertNumber(Object value, Integer decimalPlaces, boolean useThousandsSeparator) {
        if (value == null) {
            return null;
        }
        try {
            double numericValue = toDouble(value);

            StringBuilder pattern = new StringBuilder();
            if (useThousandsSeparator) {
                pattern.append("#,##0");
            } else {
                pattern.append("0");
            }

            if (decimalPlaces != null && decimalPlaces > 0) {
                pattern.append(".");
                pattern.append("0".repeat(decimalPlaces));
            }

            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
            DecimalFormat df = new DecimalFormat(pattern.toString(), symbols);
            return df.format(numericValue);
        } catch (Exception e) {
            log.warn("Number conversion failed for value '{}': {}", value, e.getMessage());
            return String.valueOf(value);
        }
    }

    // ── Data type conversion (Req 7.4) ──

    /**
     * Convert a value to the specified target type.
     *
     * @param value      the value to convert
     * @param targetType the desired type (STRING, NUMBER, BOOLEAN)
     * @return the converted value, or the original value on failure
     */
    public Object convertType(Object value, TargetType targetType) {
        if (value == null || targetType == null) {
            return value;
        }
        try {
            return switch (targetType) {
                case STRING -> convertToString(value);
                case NUMBER -> convertToNumber(value);
                case BOOLEAN -> convertToBoolean(value);
            };
        } catch (Exception e) {
            log.warn("Type conversion failed for value '{}' to {}: {}", value, targetType, e.getMessage());
            return value;
        }
    }

    // ── Custom field mapping (Req 7.5) ──

    /**
     * Apply field mapping rules to a data map.
     * <p>
     * Each rule is a map with:
     * <ul>
     *   <li>{@code sourceField} – the original field name</li>
     *   <li>{@code targetField} – the new field name (field rename)</li>
     *   <li>{@code valueMapping} – optional Map&lt;String,Object&gt; for value mapping</li>
     * </ul>
     *
     * @param data         the original data map
     * @param mappingRules the list of mapping rules
     * @return a new map with mappings applied
     */
    public Map<String, Object> applyFieldMapping(Map<String, Object> data,
                                                  List<Map<String, Object>> mappingRules) {
        if (data == null) {
            return Map.of();
        }
        if (mappingRules == null || mappingRules.isEmpty()) {
            return new HashMap<>(data);
        }

        Map<String, Object> result = new HashMap<>(data);

        for (Map<String, Object> rule : mappingRules) {
            try {
                applyOneMapping(result, rule);
            } catch (Exception e) {
                log.warn("Field mapping failed for rule {}: {}", rule, e.getMessage());
                // On failure, keep original data unchanged for this rule
            }
        }

        return result;
    }

    // ── Internal helpers ──

    private void applyOneMapping(Map<String, Object> result, Map<String, Object> rule) {
        String sourceField = (String) rule.get("sourceField");
        String targetField = (String) rule.get("targetField");
        @SuppressWarnings("unchecked")
        Map<String, Object> valueMapping = (Map<String, Object>) rule.get("valueMapping");

        if (sourceField == null) {
            return;
        }

        Object value = result.get(sourceField);

        // Apply value mapping if present
        if (valueMapping != null && value != null) {
            String key = String.valueOf(value);
            if (valueMapping.containsKey(key)) {
                value = valueMapping.get(key);
            }
        }

        // Apply field rename
        if (targetField != null && !targetField.equals(sourceField)) {
            result.remove(sourceField);
            result.put(targetField, value);
        } else if (valueMapping != null) {
            // Value mapping only (no rename)
            result.put(sourceField, value);
        }
    }

    private String convertToString(Object value) {
        return String.valueOf(value);
    }

    private Number convertToNumber(Object value) {
        if (value instanceof Number n) {
            return n;
        }
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        String s = String.valueOf(value).trim();
        if (s.contains(".")) {
            return Double.parseDouble(s);
        }
        return Long.parseLong(s);
    }

    private Boolean convertToBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.doubleValue() != 0;
        }
        String s = String.valueOf(value).trim().toLowerCase();
        return switch (s) {
            case "true", "yes", "1" -> true;
            case "false", "no", "0" -> false;
            default -> throw new IllegalArgumentException("Cannot convert '" + value + "' to Boolean");
        };
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value).trim());
    }
}
