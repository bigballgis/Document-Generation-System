package com.docgen.service;

import com.docgen.service.DataFormatService.TargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DataFormatServiceTest {

    private DataFormatService service;

    @BeforeEach
    void setUp() {
        service = new DataFormatService();
    }

    // ── convertDate ──

    @Test
    void convertDate_validConversion() {
        String result = service.convertDate("2024-01-15", "yyyy-MM-dd", "dd/MM/yyyy");
        assertEquals("15/01/2024", result);
    }

    @Test
    void convertDate_dateTimeConversion() {
        String result = service.convertDate("2024-01-15 10:30:00", "yyyy-MM-dd HH:mm:ss", "dd-MM-yyyy HH:mm");
        assertEquals("15-01-2024 10:30", result);
    }

    @Test
    void convertDate_nullValue_returnsNull() {
        assertNull(service.convertDate(null, "yyyy-MM-dd", "dd/MM/yyyy"));
    }

    @Test
    void convertDate_emptyValue_returnsEmpty() {
        assertEquals("", service.convertDate("", "yyyy-MM-dd", "dd/MM/yyyy"));
    }

    @Test
    void convertDate_invalidValue_returnsOriginal() {
        String result = service.convertDate("not-a-date", "yyyy-MM-dd", "dd/MM/yyyy");
        assertEquals("not-a-date", result);
    }

    @Test
    void convertDate_invalidSourceFormat_returnsOriginal() {
        String result = service.convertDate("2024-01-15", "dd/MM/yyyy", "yyyy-MM-dd");
        // "2024-01-15" doesn't match "dd/MM/yyyy", so returns original
        assertEquals("2024-01-15", result);
    }

    // ── convertNumber ──

    @Test
    void convertNumber_withDecimalPlaces() {
        String result = service.convertNumber(1234.5678, 2, false);
        assertEquals("1234.57", result);
    }

    @Test
    void convertNumber_withThousandsSeparator() {
        String result = service.convertNumber(1234567.89, 2, true);
        assertEquals("1,234,567.89", result);
    }

    @Test
    void convertNumber_noDecimalPlaces() {
        // DecimalFormat with pattern "0" rounds using HALF_EVEN (banker's rounding)
        String result = service.convertNumber(1234.5, null, false);
        assertEquals("1234", result);
        // 1234.6 rounds up
        assertEquals("1235", service.convertNumber(1234.6, null, false));
    }

    @Test
    void convertNumber_stringInput() {
        String result = service.convertNumber("9876.54", 1, true);
        assertEquals("9,876.5", result);
    }

    @Test
    void convertNumber_nullValue_returnsNull() {
        assertNull(service.convertNumber(null, 2, false));
    }

    @Test
    void convertNumber_invalidValue_returnsOriginal() {
        String result = service.convertNumber("abc", 2, false);
        assertEquals("abc", result);
    }

    @Test
    void convertNumber_integerWithThousands() {
        String result = service.convertNumber(1000000, null, true);
        assertEquals("1,000,000", result);
    }

    @Test
    void convertNumber_zeroDecimalPlaces() {
        String result = service.convertNumber(3.14159, 0, false);
        assertEquals("3", result);
    }

    // ── convertType ──

    @Test
    void convertType_toStringFromNumber() {
        Object result = service.convertType(42, TargetType.STRING);
        assertEquals("42", result);
    }

    @Test
    void convertType_toStringFromBoolean() {
        Object result = service.convertType(true, TargetType.STRING);
        assertEquals("true", result);
    }

    @Test
    void convertType_toNumberFromString() {
        Object result = service.convertType("123", TargetType.NUMBER);
        assertInstanceOf(Number.class, result);
        assertEquals(123L, ((Number) result).longValue());
    }

    @Test
    void convertType_toNumberFromDecimalString() {
        Object result = service.convertType("3.14", TargetType.NUMBER);
        assertInstanceOf(Number.class, result);
        assertEquals(3.14, ((Number) result).doubleValue(), 0.001);
    }

    @Test
    void convertType_toNumberFromBoolean() {
        assertEquals(1, ((Number) service.convertType(true, TargetType.NUMBER)).intValue());
        assertEquals(0, ((Number) service.convertType(false, TargetType.NUMBER)).intValue());
    }

    @Test
    void convertType_toBooleanFromString() {
        assertEquals(true, service.convertType("true", TargetType.BOOLEAN));
        assertEquals(false, service.convertType("false", TargetType.BOOLEAN));
        assertEquals(true, service.convertType("yes", TargetType.BOOLEAN));
        assertEquals(false, service.convertType("no", TargetType.BOOLEAN));
    }

    @Test
    void convertType_toBooleanFromNumber() {
        assertEquals(true, service.convertType(1, TargetType.BOOLEAN));
        assertEquals(false, service.convertType(0, TargetType.BOOLEAN));
        assertEquals(true, service.convertType(-1, TargetType.BOOLEAN));
    }

    @Test
    void convertType_nullValue_returnsNull() {
        assertNull(service.convertType(null, TargetType.STRING));
    }

    @Test
    void convertType_nullTargetType_returnsOriginal() {
        assertEquals("hello", service.convertType("hello", null));
    }

    @Test
    void convertType_invalidConversion_returnsOriginal() {
        // "hello" cannot be converted to NUMBER
        Object result = service.convertType("hello", TargetType.NUMBER);
        assertEquals("hello", result);
    }

    @Test
    void convertType_invalidBooleanString_returnsOriginal() {
        Object result = service.convertType("maybe", TargetType.BOOLEAN);
        assertEquals("maybe", result);
    }

    // ── applyFieldMapping ──

    @Test
    void applyFieldMapping_fieldRename() {
        Map<String, Object> data = new HashMap<>(Map.of("firstName", "Alice"));
        List<Map<String, Object>> rules = List.of(
                Map.of("sourceField", "firstName", "targetField", "name")
        );
        Map<String, Object> result = service.applyFieldMapping(data, rules);
        assertFalse(result.containsKey("firstName"));
        assertEquals("Alice", result.get("name"));
    }

    @Test
    void applyFieldMapping_valueMapping() {
        Map<String, Object> data = new HashMap<>(Map.of("status", "A"));
        List<Map<String, Object>> rules = List.of(
                Map.of("sourceField", "status",
                        "valueMapping", Map.of("A", "Active", "I", "Inactive"))
        );
        Map<String, Object> result = service.applyFieldMapping(data, rules);
        assertEquals("Active", result.get("status"));
    }

    @Test
    void applyFieldMapping_renameAndValueMapping() {
        Map<String, Object> data = new HashMap<>(Map.of("st", "Y"));
        List<Map<String, Object>> rules = List.of(
                Map.of("sourceField", "st",
                        "targetField", "enabled",
                        "valueMapping", Map.of("Y", true, "N", false))
        );
        Map<String, Object> result = service.applyFieldMapping(data, rules);
        assertFalse(result.containsKey("st"));
        assertEquals(true, result.get("enabled"));
    }

    @Test
    void applyFieldMapping_nullData_returnsEmptyMap() {
        Map<String, Object> result = service.applyFieldMapping(null, List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void applyFieldMapping_nullRules_returnsCopy() {
        Map<String, Object> data = Map.of("a", 1);
        Map<String, Object> result = service.applyFieldMapping(data, null);
        assertEquals(1, result.get("a"));
    }

    @Test
    void applyFieldMapping_emptyRules_returnsCopy() {
        Map<String, Object> data = Map.of("a", 1);
        Map<String, Object> result = service.applyFieldMapping(data, List.of());
        assertEquals(1, result.get("a"));
    }

    @Test
    void applyFieldMapping_unmatchedValueMapping_keepsOriginal() {
        Map<String, Object> data = new HashMap<>(Map.of("status", "X"));
        List<Map<String, Object>> rules = List.of(
                Map.of("sourceField", "status",
                        "valueMapping", Map.of("A", "Active"))
        );
        Map<String, Object> result = service.applyFieldMapping(data, rules);
        // "X" not in mapping, so original value kept
        assertEquals("X", result.get("status"));
    }

    @Test
    void applyFieldMapping_missingSourceField_noChange() {
        Map<String, Object> data = new HashMap<>(Map.of("a", 1));
        List<Map<String, Object>> rules = List.of(
                Map.of("sourceField", "nonexistent", "targetField", "b")
        );
        Map<String, Object> result = service.applyFieldMapping(data, rules);
        assertEquals(1, result.get("a"));
        // sourceField "nonexistent" not in data, so targetField gets null
        assertTrue(result.containsKey("b"));
        assertNull(result.get("b"));
        assertFalse(result.containsKey("nonexistent"));
    }

    @Test
    void applyFieldMapping_multipleRules() {
        Map<String, Object> data = new HashMap<>(Map.of("first", "A", "second", "B"));
        List<Map<String, Object>> rules = List.of(
                Map.of("sourceField", "first", "targetField", "one"),
                Map.of("sourceField", "second", "targetField", "two")
        );
        Map<String, Object> result = service.applyFieldMapping(data, rules);
        assertEquals("A", result.get("one"));
        assertEquals("B", result.get("two"));
        assertFalse(result.containsKey("first"));
        assertFalse(result.containsKey("second"));
    }
}
