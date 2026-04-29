package com.docgen.service;

import com.docgen.dto.DataValidationError;
import com.docgen.dto.DataValidationResult;
import com.docgen.dto.ValidationRule;
import com.docgen.dto.ValidationRule.DataType;
import com.docgen.dto.ValidationRule.RuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DataValidationServiceTest {

    private DataValidationService service;

    @BeforeEach
    void setUp() {
        service = new DataValidationService();
    }


    @Test
    void validate_nullRules_returnsSuccess() {
        var result = service.validate(Map.of("a", 1), null);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void validate_emptyRules_returnsSuccess() {
        var result = service.validate(Map.of("a", 1), List.of());
        assertTrue(result.isValid());
    }

    @Test
    void validate_nullData_treatedAsEmpty() {
        var rule = new ValidationRule("name", RuleType.REQUIRED, null);
        var result = service.validate(null, List.of(rule));
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
    }


    @Test
    void required_missingField_fails() {
        var rule = new ValidationRule("name", RuleType.REQUIRED, null);
        var result = service.validate(Map.of(), List.of(rule));
        assertFalse(result.isValid());
        assertEquals(RuleType.REQUIRED, result.getErrors().get(0).getRuleType());
        assertEquals("name", result.getErrors().get(0).getFieldName());
    }

    @Test
    void required_emptyString_fails() {
        var rule = new ValidationRule("name", RuleType.REQUIRED, null);
        var result = service.validate(Map.of("name", ""), List.of(rule));
        assertFalse(result.isValid());
    }

    @Test
    void required_presentField_passes() {
        var rule = new ValidationRule("name", RuleType.REQUIRED, null);
        var result = service.validate(Map.of("name", "Alice"), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void required_numericZero_passes() {
        var rule = new ValidationRule("count", RuleType.REQUIRED, null);
        var result = service.validate(Map.of("count", 0), List.of(rule));
        assertTrue(result.isValid());
    }


    @Test
    void type_stringExpected_stringValue_passes() {
        var rule = new ValidationRule("name", RuleType.TYPE, Map.of("type", "STRING"));
        var result = service.validate(Map.of("name", "Alice"), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void type_numberExpected_intValue_passes() {
        var rule = new ValidationRule("age", RuleType.TYPE, Map.of("type", "NUMBER"));
        var result = service.validate(Map.of("age", 25), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void type_numberExpected_doubleValue_passes() {
        var rule = new ValidationRule("price", RuleType.TYPE, Map.of("type", "NUMBER"));
        var result = service.validate(Map.of("price", 19.99), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void type_numberExpected_numericString_passes() {
        var rule = new ValidationRule("age", RuleType.TYPE, Map.of("type", "NUMBER"));
        var result = service.validate(Map.of("age", "25"), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void type_numberExpected_nonNumericString_fails() {
        var rule = new ValidationRule("age", RuleType.TYPE, Map.of("type", "NUMBER"));
        var result = service.validate(Map.of("age", "abc"), List.of(rule));
        assertFalse(result.isValid());
        assertEquals(RuleType.TYPE, result.getErrors().get(0).getRuleType());
    }

    @Test
    void type_booleanExpected_booleanValue_passes() {
        var rule = new ValidationRule("active", RuleType.TYPE, Map.of("type", "BOOLEAN"));
        var result = service.validate(Map.of("active", true), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void type_booleanExpected_stringTrue_passes() {
        var rule = new ValidationRule("active", RuleType.TYPE, Map.of("type", "BOOLEAN"));
        var result = service.validate(Map.of("active", "true"), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void type_booleanExpected_nonBooleanString_fails() {
        var rule = new ValidationRule("active", RuleType.TYPE, Map.of("type", "BOOLEAN"));
        var result = service.validate(Map.of("active", "yes"), List.of(rule));
        assertFalse(result.isValid());
    }

    @Test
    void type_dateExpected_localDate_passes() {
        var rule = new ValidationRule("dob", RuleType.TYPE, Map.of("type", "DATE"));
        var result = service.validate(Map.of("dob", LocalDate.of(2024, 1, 1)), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void type_dateExpected_isoDateString_passes() {
        var rule = new ValidationRule("dob", RuleType.TYPE, Map.of("type", "DATE"));
        var result = service.validate(Map.of("dob", "2024-01-15"), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void type_dateExpected_isoDateTimeString_passes() {
        var rule = new ValidationRule("ts", RuleType.TYPE, Map.of("type", "DATE"));
        var result = service.validate(Map.of("ts", "2024-01-15T10:30:00"), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void type_dateExpected_invalidString_fails() {
        var rule = new ValidationRule("dob", RuleType.TYPE, Map.of("type", "DATE"));
        var result = service.validate(Map.of("dob", "not-a-date"), List.of(rule));
        assertFalse(result.isValid());
    }

    @Test
    void type_nullValue_skipped() {
        var rule = new ValidationRule("name", RuleType.TYPE, Map.of("type", "STRING"));
        var data = new java.util.HashMap<String, Object>();
        data.put("name", null);
        var result = service.validate(data, List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void type_invalidTypeParam_fails() {
        var rule = new ValidationRule("x", RuleType.TYPE, Map.of("type", "UNKNOWN"));
        var result = service.validate(Map.of("x", "val"), List.of(rule));
        assertFalse(result.isValid());
    }

    @Test
    void type_enumDataType_passes() {
        var rule = new ValidationRule("name", RuleType.TYPE, Map.of("type", DataType.STRING));
        var result = service.validate(Map.of("name", "Alice"), List.of(rule));
        assertTrue(result.isValid());
    }


    @Test
    void range_withinBounds_passes() {
        var rule = new ValidationRule("age", RuleType.RANGE, Map.of("min", 0, "max", 150));
        var result = service.validate(Map.of("age", 25), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void range_belowMin_fails() {
        var rule = new ValidationRule("age", RuleType.RANGE, Map.of("min", 0, "max", 150));
        var result = service.validate(Map.of("age", -1), List.of(rule));
        assertFalse(result.isValid());
        assertEquals(RuleType.RANGE, result.getErrors().get(0).getRuleType());
    }

    @Test
    void range_aboveMax_fails() {
        var rule = new ValidationRule("age", RuleType.RANGE, Map.of("min", 0, "max", 150));
        var result = service.validate(Map.of("age", 200), List.of(rule));
        assertFalse(result.isValid());
    }

    @Test
    void range_exactMin_passes() {
        var rule = new ValidationRule("val", RuleType.RANGE, Map.of("min", 10, "max", 20));
        var result = service.validate(Map.of("val", 10), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void range_exactMax_passes() {
        var rule = new ValidationRule("val", RuleType.RANGE, Map.of("min", 10, "max", 20));
        var result = service.validate(Map.of("val", 20), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void range_onlyMin_passes() {
        var rule = new ValidationRule("val", RuleType.RANGE, Map.of("min", 5));
        var result = service.validate(Map.of("val", 100), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void range_onlyMax_passes() {
        var rule = new ValidationRule("val", RuleType.RANGE, Map.of("max", 100));
        var result = service.validate(Map.of("val", 50), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void range_nonNumericValue_fails() {
        var rule = new ValidationRule("val", RuleType.RANGE, Map.of("min", 0, "max", 100));
        var result = service.validate(Map.of("val", "abc"), List.of(rule));
        assertFalse(result.isValid());
    }

    @Test
    void range_nullValue_skipped() {
        var rule = new ValidationRule("val", RuleType.RANGE, Map.of("min", 0));
        var data = new java.util.HashMap<String, Object>();
        data.put("val", null);
        var result = service.validate(data, List.of(rule));
        assertTrue(result.isValid());
    }


    @Test
    void length_withinBounds_passes() {
        var rule = new ValidationRule("name", RuleType.LENGTH, Map.of("min", 2, "max", 50));
        var result = service.validate(Map.of("name", "Alice"), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void length_tooShort_fails() {
        var rule = new ValidationRule("name", RuleType.LENGTH, Map.of("min", 3, "max", 50));
        var result = service.validate(Map.of("name", "AB"), List.of(rule));
        assertFalse(result.isValid());
        assertEquals(RuleType.LENGTH, result.getErrors().get(0).getRuleType());
    }

    @Test
    void length_tooLong_fails() {
        var rule = new ValidationRule("code", RuleType.LENGTH, Map.of("min", 1, "max", 5));
        var result = service.validate(Map.of("code", "ABCDEF"), List.of(rule));
        assertFalse(result.isValid());
    }

    @Test
    void length_exactMin_passes() {
        var rule = new ValidationRule("code", RuleType.LENGTH, Map.of("min", 3, "max", 10));
        var result = service.validate(Map.of("code", "ABC"), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void length_exactMax_passes() {
        var rule = new ValidationRule("code", RuleType.LENGTH, Map.of("min", 1, "max", 3));
        var result = service.validate(Map.of("code", "ABC"), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void length_nullValue_skipped() {
        var rule = new ValidationRule("name", RuleType.LENGTH, Map.of("min", 1));
        var data = new java.util.HashMap<String, Object>();
        data.put("name", null);
        var result = service.validate(data, List.of(rule));
        assertTrue(result.isValid());
    }


    @Test
    void regex_matchingPattern_passes() {
        var rule = new ValidationRule("email", RuleType.REGEX, Map.of("pattern", "^[\\w.]+@[\\w.]+$"));
        var result = service.validate(Map.of("email", "user@example.com"), List.of(rule));
        assertTrue(result.isValid());
    }

    @Test
    void regex_nonMatchingPattern_fails() {
        var rule = new ValidationRule("email", RuleType.REGEX, Map.of("pattern", "^[\\w.]+@[\\w.]+$"));
        var result = service.validate(Map.of("email", "not-an-email"), List.of(rule));
        assertFalse(result.isValid());
        assertEquals(RuleType.REGEX, result.getErrors().get(0).getRuleType());
    }

    @Test
    void regex_missingPattern_fails() {
        var rule = new ValidationRule("code", RuleType.REGEX, Map.of());
        var result = service.validate(Map.of("code", "ABC"), List.of(rule));
        assertFalse(result.isValid());
    }

    @Test
    void regex_invalidPattern_fails() {
        var rule = new ValidationRule("code", RuleType.REGEX, Map.of("pattern", "[invalid"));
        var result = service.validate(Map.of("code", "ABC"), List.of(rule));
        assertFalse(result.isValid());
    }

    @Test
    void regex_nullValue_skipped() {
        var rule = new ValidationRule("code", RuleType.REGEX, Map.of("pattern", ".*"));
        var data = new java.util.HashMap<String, Object>();
        data.put("code", null);
        var result = service.validate(data, List.of(rule));
        assertTrue(result.isValid());
    }


    @Test
    void multipleRules_allPass() {
        var rules = List.of(
                new ValidationRule("name", RuleType.REQUIRED, null),
                new ValidationRule("name", RuleType.TYPE, Map.of("type", "STRING")),
                new ValidationRule("name", RuleType.LENGTH, Map.of("min", 1, "max", 100)),
                new ValidationRule("age", RuleType.REQUIRED, null),
                new ValidationRule("age", RuleType.TYPE, Map.of("type", "NUMBER")),
                new ValidationRule("age", RuleType.RANGE, Map.of("min", 0, "max", 150))
        );
        var data = Map.<String, Object>of("name", "Alice", "age", 30);
        var result = service.validate(data, rules);
        assertTrue(result.isValid());
    }

    @Test
    void multipleRules_multipleFailures_reportsAll() {
        var rules = List.of(
                new ValidationRule("name", RuleType.REQUIRED, null),
                new ValidationRule("age", RuleType.RANGE, Map.of("min", 0, "max", 150))
        );
        var data = Map.<String, Object>of("age", 200);
        var result = service.validate(data, rules);
        assertFalse(result.isValid());
        assertEquals(2, result.getErrors().size());
    }


    @Test
    void errorContainsFieldNameAndRuleType() {
        var rule = new ValidationRule("email", RuleType.REQUIRED, null);
        var result = service.validate(Map.of(), List.of(rule));
        assertFalse(result.isValid());
        DataValidationError error = result.getErrors().get(0);
        assertEquals("email", error.getFieldName());
        assertEquals(RuleType.REQUIRED, error.getRuleType());
        assertNotNull(error.getMessage());
        assertFalse(error.getMessage().isEmpty());
    }
}

