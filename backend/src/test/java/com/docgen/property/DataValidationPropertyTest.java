package com.docgen.property;

import com.docgen.dto.DataValidationResult;
import com.docgen.dto.ValidationRule;
import com.docgen.dto.ValidationRule.DataType;
import com.docgen.dto.ValidationRule.RuleType;
import com.docgen.service.DataValidationService;
import net.jqwik.api.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for data validation rule correctness.
 *
 * <p>Generates random data and validation rule combinations, verifying the engine
 * correctly accepts valid data and rejects invalid data with appropriate error details.</p>
 *
 * <p><b>Validates: Requirements 13.2, 13.3, 13.4, 13.5, 13.6, 13.7</b></p>
 */
@Tag("feature-low-code-document-generation-system-property-8")
class DataValidationPropertyTest {

    private final DataValidationService service = new DataValidationService();
    private static final String FIELD = "testField";


    /**
     * Property 8 (REQUIRED): Non-null, non-empty values should pass REQUIRED validation.
     */
    @Property(tries = 100)
    void requiredRule_presentValuesShouldPass(
            @ForAll("nonEmptyStrings") String value
    ) {
        ValidationRule rule = new ValidationRule(FIELD, RuleType.REQUIRED, null);
        Map<String, Object> data = Map.of(FIELD, value);

        DataValidationResult result = service.validate(data, List.of(rule));

        assertTrue(result.isValid(),
                "Non-empty value '" + value + "' should pass REQUIRED validation");
    }

    /**
     * Property 8 (REQUIRED): Null or empty values should fail REQUIRED validation.
     */
    @Property(tries = 50)
    void requiredRule_absentValuesShouldFail(
            @ForAll("absentValues") Map<String, Object> data
    ) {
        ValidationRule rule = new ValidationRule(FIELD, RuleType.REQUIRED, null);

        DataValidationResult result = service.validate(data, List.of(rule));

        assertFalse(result.isValid(), "Absent/empty value should fail REQUIRED validation");
        assertFalse(result.getErrors().isEmpty(), "Should contain at least one error");
        assertEquals(RuleType.REQUIRED, result.getErrors().get(0).getRuleType());
    }


    /**
     * Property 8 (TYPE): Values matching the expected type should pass TYPE validation.
     */
    @Property(tries = 100)
    void typeRule_matchingTypesShouldPass(
            @ForAll("matchingTypeAndValue") TypeAndValue tv
    ) {
        ValidationRule rule = new ValidationRule(FIELD, RuleType.TYPE,
                Map.of("type", tv.dataType().name()));
        Map<String, Object> data = Map.of(FIELD, tv.value());

        DataValidationResult result = service.validate(data, List.of(rule));

        assertTrue(result.isValid(),
                "Value '" + tv.value() + "' should pass TYPE " + tv.dataType() + " validation");
    }

    /**
     * Property 8 (TYPE): Values not matching the expected type should fail TYPE validation.
     */
    @Property(tries = 100)
    void typeRule_mismatchedTypesShouldFail(
            @ForAll("mismatchedTypeAndValue") TypeAndValue tv
    ) {
        ValidationRule rule = new ValidationRule(FIELD, RuleType.TYPE,
                Map.of("type", tv.dataType().name()));
        Map<String, Object> data = Map.of(FIELD, tv.value());

        DataValidationResult result = service.validate(data, List.of(rule));

        assertFalse(result.isValid(),
                "Value '" + tv.value() + "' should fail TYPE " + tv.dataType() + " validation");
        assertEquals(RuleType.TYPE, result.getErrors().get(0).getRuleType());
    }


    /**
     * Property 8 (RANGE): Numeric values within [min, max] should pass RANGE validation.
     */
    @Property(tries = 100)
    void rangeRule_inRangeValuesShouldPass(
            @ForAll("rangeWithInValue") RangeTestCase tc
    ) {
        Map<String, Object> params = new HashMap<>();
        params.put("min", tc.min());
        params.put("max", tc.max());
        ValidationRule rule = new ValidationRule(FIELD, RuleType.RANGE, params);
        Map<String, Object> data = Map.of(FIELD, tc.value());

        DataValidationResult result = service.validate(data, List.of(rule));

        assertTrue(result.isValid(),
                "Value " + tc.value() + " in range [" + tc.min() + ", " + tc.max() + "] should pass");
    }

    /**
     * Property 8 (RANGE): Numeric values outside [min, max] should fail RANGE validation.
     */
    @Property(tries = 100)
    void rangeRule_outOfRangeValuesShouldFail(
            @ForAll("rangeWithOutValue") RangeTestCase tc
    ) {
        Map<String, Object> params = new HashMap<>();
        params.put("min", tc.min());
        params.put("max", tc.max());
        ValidationRule rule = new ValidationRule(FIELD, RuleType.RANGE, params);
        Map<String, Object> data = Map.of(FIELD, tc.value());

        DataValidationResult result = service.validate(data, List.of(rule));

        assertFalse(result.isValid(),
                "Value " + tc.value() + " outside range [" + tc.min() + ", " + tc.max() + "] should fail");
        assertEquals(RuleType.RANGE, result.getErrors().get(0).getRuleType());
    }


    /**
     * Property 8 (LENGTH): Strings with length in [min, max] should pass LENGTH validation.
     */
    @Property(tries = 100)
    void lengthRule_validLengthShouldPass(
            @ForAll("lengthWithValidString") LengthTestCase tc
    ) {
        Map<String, Object> params = new HashMap<>();
        params.put("min", tc.minLen());
        params.put("max", tc.maxLen());
        ValidationRule rule = new ValidationRule(FIELD, RuleType.LENGTH, params);
        Map<String, Object> data = Map.of(FIELD, tc.value());

        DataValidationResult result = service.validate(data, List.of(rule));

        assertTrue(result.isValid(),
                "String of length " + tc.value().length() + " in [" + tc.minLen() + ", " + tc.maxLen() + "] should pass");
    }

    /**
     * Property 8 (LENGTH): Strings with length outside [min, max] should fail LENGTH validation.
     */
    @Property(tries = 100)
    void lengthRule_invalidLengthShouldFail(
            @ForAll("lengthWithInvalidString") LengthTestCase tc
    ) {
        Map<String, Object> params = new HashMap<>();
        params.put("min", tc.minLen());
        params.put("max", tc.maxLen());
        ValidationRule rule = new ValidationRule(FIELD, RuleType.LENGTH, params);
        Map<String, Object> data = Map.of(FIELD, tc.value());

        DataValidationResult result = service.validate(data, List.of(rule));

        assertFalse(result.isValid(),
                "String of length " + tc.value().length() + " outside [" + tc.minLen() + ", " + tc.maxLen() + "] should fail");
        assertEquals(RuleType.LENGTH, result.getErrors().get(0).getRuleType());
    }


    /**
     * Property 8 (REGEX): Values matching the regex pattern should pass REGEX validation.
     */
    @Property(tries = 100)
    void regexRule_matchingValuesShouldPass(
            @ForAll("regexMatchingPair") RegexTestCase tc
    ) {
        ValidationRule rule = new ValidationRule(FIELD, RuleType.REGEX,
                Map.of("pattern", tc.pattern()));
        Map<String, Object> data = Map.of(FIELD, tc.value());

        DataValidationResult result = service.validate(data, List.of(rule));

        assertTrue(result.isValid(),
                "Value '" + tc.value() + "' should match pattern '" + tc.pattern() + "'");
    }

    /**
     * Property 8 (REGEX): Values not matching the regex pattern should fail REGEX validation.
     */
    @Property(tries = 100)
    void regexRule_nonMatchingValuesShouldFail(
            @ForAll("regexNonMatchingPair") RegexTestCase tc
    ) {
        ValidationRule rule = new ValidationRule(FIELD, RuleType.REGEX,
                Map.of("pattern", tc.pattern()));
        Map<String, Object> data = Map.of(FIELD, tc.value());

        DataValidationResult result = service.validate(data, List.of(rule));

        assertFalse(result.isValid(),
                "Value '" + tc.value() + "' should NOT match pattern '" + tc.pattern() + "'");
        assertEquals(RuleType.REGEX, result.getErrors().get(0).getRuleType());
    }


    /**
     * Property 8 (Error details): Every validation failure should include the field name
     * and rule type in the error details.
     */
    @Property(tries = 100)
    void validationFailures_shouldContainFieldNameAndRuleType(
            @ForAll("failingRuleAndData") RuleAndData rd
    ) {
        DataValidationResult result = service.validate(rd.data(), List.of(rd.rule()));

        assertFalse(result.isValid(), "Should be a failing validation");
        assertFalse(result.getErrors().isEmpty(), "Should have at least one error");
        assertEquals(FIELD, result.getErrors().get(0).getFieldName(),
                "Error should reference the correct field name");
        assertEquals(rd.rule().getRuleType(), result.getErrors().get(0).getRuleType(),
                "Error should reference the correct rule type");
        assertNotNull(result.getErrors().get(0).getMessage(),
                "Error message should not be null");
        assertFalse(result.getErrors().get(0).getMessage().isBlank(),
                "Error message should not be blank");
    }


    record TypeAndValue(DataType dataType, Object value) {}
    record RangeTestCase(double min, double max, double value) {}
    record LengthTestCase(int minLen, int maxLen, String value) {}
    record RegexTestCase(String pattern, String value) {}
    record RuleAndData(ValidationRule rule, Map<String, Object> data) {}


    @Provide
    Arbitrary<String> nonEmptyStrings() {
        return Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(100);
    }

    @Provide
    Arbitrary<Map<String, Object>> absentValues() {
        return Arbitraries.oneOf(
                // null value (field missing from map)
                Arbitraries.just(Map.of()),
                // empty string
                Arbitraries.just(Map.of(FIELD, ""))
        );
    }

    @Provide
    Arbitrary<TypeAndValue> matchingTypeAndValue() {
        return Arbitraries.oneOf(
                // STRING type with string values
                Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(50)
                        .map(s -> new TypeAndValue(DataType.STRING, s)),
                // NUMBER type with numeric values
                Arbitraries.doubles().between(-1_000_000, 1_000_000)
                        .map(d -> new TypeAndValue(DataType.NUMBER, d)),
                // NUMBER type with integer values
                Arbitraries.integers().between(-10000, 10000)
                        .map(i -> new TypeAndValue(DataType.NUMBER, i)),
                // BOOLEAN type with boolean values
                Arbitraries.of(true, false)
                        .map(b -> new TypeAndValue(DataType.BOOLEAN, b)),
                // BOOLEAN type with string "true"/"false"
                Arbitraries.of("true", "false", "TRUE", "FALSE")
                        .map(s -> new TypeAndValue(DataType.BOOLEAN, s)),
                // DATE type with date strings
                Arbitraries.integers().between(2000, 2030)
                        .flatMap(y -> Arbitraries.integers().between(1, 12)
                                .flatMap(m -> Arbitraries.integers().between(1, 28)
                                        .map(d -> new TypeAndValue(DataType.DATE,
                                                String.format("%04d-%02d-%02d", y, m, d)))))
        );
    }

    @Provide
    Arbitrary<TypeAndValue> mismatchedTypeAndValue() {
        return Arbitraries.oneOf(
                // STRING type with non-string values
                Arbitraries.integers().between(-1000, 1000)
                        .map(i -> new TypeAndValue(DataType.STRING, i)),
                // NUMBER type with non-numeric strings
                Arbitraries.of("abc", "hello", "not-a-number", "12.34.56")
                        .map(s -> new TypeAndValue(DataType.NUMBER, s)),
                // BOOLEAN type with non-boolean values
                Arbitraries.of("yes", "no", "1", "0", "maybe", 42)
                        .map(v -> new TypeAndValue(DataType.BOOLEAN, v)),
                // DATE type with invalid date strings
                Arbitraries.of("not-a-date", "2024-13-01", "2024-00-15", "abc-def")
                        .map(s -> new TypeAndValue(DataType.DATE, s))
        );
    }

    @Provide
    Arbitrary<RangeTestCase> rangeWithInValue() {
        // Use integers to avoid jqwik decimal scale issues, then convert to doubles
        return Arbitraries.integers().between(-10000, 10000)
                .flatMap(minInt -> Arbitraries.integers().between(minInt + 1, minInt + 10000)
                        .flatMap(maxInt -> Arbitraries.integers().between(minInt, maxInt)
                                .map(valInt -> new RangeTestCase(minInt.doubleValue(), maxInt.doubleValue(), valInt.doubleValue()))));
    }

    @Provide
    Arbitrary<RangeTestCase> rangeWithOutValue() {
        return Arbitraries.integers().between(-5000, 5000)
                .flatMap(minInt -> Arbitraries.integers().between(minInt + 2, minInt + 5000)
                        .flatMap(maxInt -> Arbitraries.oneOf(
                                // Below min
                                Arbitraries.integers().between(minInt - 5000, minInt - 1)
                                        .map(val -> new RangeTestCase(minInt.doubleValue(), maxInt.doubleValue(), val.doubleValue())),
                                // Above max
                                Arbitraries.integers().between(maxInt + 1, maxInt + 5000)
                                        .map(val -> new RangeTestCase(minInt.doubleValue(), maxInt.doubleValue(), val.doubleValue()))
                        )));
    }

    @Provide
    Arbitrary<LengthTestCase> lengthWithValidString() {
        return Arbitraries.integers().between(0, 20)
                .flatMap(minLen -> Arbitraries.integers().between(minLen, minLen + 30)
                        .flatMap(maxLen -> Arbitraries.strings().ascii()
                                .ofMinLength(minLen).ofMaxLength(maxLen)
                                .map(s -> new LengthTestCase(minLen, maxLen, s))));
    }

    @Provide
    Arbitrary<LengthTestCase> lengthWithInvalidString() {
        return Arbitraries.oneOf(
                // Too short
                Arbitraries.integers().between(3, 20)
                        .flatMap(minLen -> Arbitraries.integers().between(minLen, minLen + 20)
                                .flatMap(maxLen -> Arbitraries.strings().ascii()
                                        .ofMinLength(0).ofMaxLength(minLen - 1)
                                        .map(s -> new LengthTestCase(minLen, maxLen, s)))),
                // Too long
                Arbitraries.integers().between(1, 10)
                        .flatMap(minLen -> Arbitraries.integers().between(minLen, minLen + 10)
                                .flatMap(maxLen -> Arbitraries.strings().ascii()
                                        .ofMinLength(maxLen + 1).ofMaxLength(maxLen + 20)
                                        .map(s -> new LengthTestCase(minLen, maxLen, s))))
        );
    }

    @Provide
    Arbitrary<RegexTestCase> regexMatchingPair() {
        return Arbitraries.oneOf(
                // Digits-only pattern
                Arbitraries.strings().withChars("0123456789").ofMinLength(1).ofMaxLength(10)
                        .map(s -> new RegexTestCase("^\\d+$", s)),
                // Alpha-only pattern
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
                        .map(s -> new RegexTestCase("^[a-zA-Z]+$", s)),
                // Email-like pattern
                Arbitraries.strings().withChars("abcdefghijklmnopqrstuvwxyz").ofMinLength(1).ofMaxLength(8)
                        .flatMap(user -> Arbitraries.strings().withChars("abcdefghijklmnopqrstuvwxyz").ofMinLength(2).ofMaxLength(6)
                                .map(domain -> new RegexTestCase("^[a-z]+@[a-z]+\\.com$",
                                        user + "@" + domain + ".com")))
        );
    }

    @Provide
    Arbitrary<RegexTestCase> regexNonMatchingPair() {
        return Arbitraries.oneOf(
                // Digits-only pattern with alpha input
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
                        .map(s -> new RegexTestCase("^\\d+$", s)),
                // Alpha-only pattern with digit input
                Arbitraries.strings().withChars("0123456789").ofMinLength(1).ofMaxLength(10)
                        .map(s -> new RegexTestCase("^[a-zA-Z]+$", s)),
                // Email pattern with invalid input
                Arbitraries.strings().withChars("0123456789").ofMinLength(1).ofMaxLength(5)
                        .map(s -> new RegexTestCase("^[a-z]+@[a-z]+\\.com$", s))
        );
    }

    @Provide
    Arbitrary<RuleAndData> failingRuleAndData() {
        return Arbitraries.oneOf(
                // REQUIRED with missing value
                Arbitraries.just(new RuleAndData(
                        new ValidationRule(FIELD, RuleType.REQUIRED, null),
                        Map.of())),
                // REQUIRED with empty string
                Arbitraries.just(new RuleAndData(
                        new ValidationRule(FIELD, RuleType.REQUIRED, null),
                        Map.of(FIELD, ""))),
                // TYPE mismatch
                mismatchedTypeAndValue().map(tv -> new RuleAndData(
                        new ValidationRule(FIELD, RuleType.TYPE, Map.of("type", tv.dataType().name())),
                        Map.of(FIELD, tv.value()))),
                // RANGE out of bounds
                rangeWithOutValue().map(tc -> {
                    Map<String, Object> params = new HashMap<>();
                    params.put("min", tc.min());
                    params.put("max", tc.max());
                    return new RuleAndData(
                            new ValidationRule(FIELD, RuleType.RANGE, params),
                            Map.of(FIELD, tc.value()));
                }),
                // LENGTH out of bounds
                lengthWithInvalidString().map(tc -> {
                    Map<String, Object> params = new HashMap<>();
                    params.put("min", tc.minLen());
                    params.put("max", tc.maxLen());
                    return new RuleAndData(
                            new ValidationRule(FIELD, RuleType.LENGTH, params),
                            Map.of(FIELD, tc.value()));
                }),
                // REGEX non-matching
                regexNonMatchingPair().map(tc -> new RuleAndData(
                        new ValidationRule(FIELD, RuleType.REGEX, Map.of("pattern", tc.pattern())),
                        Map.of(FIELD, tc.value())))
        );
    }
}

