package com.docgen.property;

import com.docgen.exception.BusinessException;
import com.docgen.repository.ParameterRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.service.AggregationResolver;
import com.docgen.service.AuditLogService;
import com.docgen.service.ExpressionEngine;
import com.docgen.service.ParameterService;
import com.docgen.service.TemplateScanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Property 11: parameter name format validation
 *
 * For any string, the parameter name validation SHALL accept the string if and only if
 * it matches the regex {@code ^[a-zA-Z_][a-zA-Z0-9_-]*$}.
 *
 * <b>Validates: Requirements 10.6</b>
 */
class ParameterNameValidationPropertyTest {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_-]*$");

    private final ParameterService service = new ParameterService(
            mock(ParameterRepository.class),
            mock(TemplateRepository.class),
            mock(TemplateScanService.class),
            mock(ExpressionEngine.class),
            new ObjectMapper(),
            mock(AuditLogService.class),
            mock(AggregationResolver.class)
    );

    private final Method validateNameMethod;

    ParameterNameValidationPropertyTest() throws NoSuchMethodException {
        validateNameMethod = ParameterService.class.getDeclaredMethod("validateName", String.class);
        validateNameMethod.setAccessible(true);
    }

    /**
     * Invoke the package-private validateName via reflection.
     * Unwraps InvocationTargetException to rethrow the original BusinessException.
     */
    private void invokeValidateName(String name) throws BusinessException {
        try {
            validateNameMethod.invoke(service, name);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof BusinessException be) {
                throw be;
            }
            throw new RuntimeException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Property 11: For any valid parameter name (matching the regex),
     * validateName() should NOT throw an exception.
     *
     * <b>Validates: Requirements 10.6</b>
     */
    @Property(tries = 200)
    @Label("Feature: parameter-settings-ux, Property 11: Parameter name format validation — valid names accepted")
    void validNamesAreAccepted(@ForAll("validParameterNames") String name) {
        assertTrue(NAME_PATTERN.matcher(name).matches(), "Generated name should match pattern: " + name);
        assertDoesNotThrow(() -> invokeValidateName(name),
                "validateName should accept valid name: " + name);
    }

    /**
     * Property 11: For any invalid parameter name (not matching the regex),
     * validateName() should throw a BusinessException.
     *
     * <b>Validates: Requirements 10.6</b>
     */
    @Property(tries = 200)
    @Label("Feature: parameter-settings-ux, Property 11: Parameter name format validation — invalid names rejected")
    void invalidNamesAreRejected(@ForAll("invalidParameterNames") String name) {
        assertFalse(NAME_PATTERN.matcher(name).matches(), "Generated name should NOT match pattern: " + name);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> invokeValidateName(name),
                "validateName should reject invalid name: " + name);
        assertEquals("PARAMETER_INVALID_NAME", ex.getErrorCode());
    }

    /**
     * Property 11: For any arbitrary string, validateName() accepts it iff it matches the regex.
     *
     * <b>Validates: Requirements 10.6</b>
     */
    @Property(tries = 300)
    @Label("Feature: parameter-settings-ux, Property 11: Parameter name format validation — consistency with regex")
    void validateNameConsistentWithRegex(@ForAll("arbitraryStrings") String input) {
        boolean matchesRegex = NAME_PATTERN.matcher(input).matches();

        if (matchesRegex) {
            assertDoesNotThrow(() -> invokeValidateName(input),
                    "validateName should accept regex-matching input: " + input);
        } else {
            assertThrows(BusinessException.class, () -> invokeValidateName(input),
                    "validateName should reject non-matching input: " + input);
        }
    }

    /**
     * Property 11: null input should always be rejected.
     *
     * <b>Validates: Requirements 10.6</b>
     */
    @Property(tries = 1)
    @Label("Feature: parameter-settings-ux, Property 11: Parameter name format validation — null rejected")
    void nullNameIsRejected() {
        assertThrows(BusinessException.class, () -> invokeValidateName(null));
    }


    @Provide
    Arbitrary<String> validParameterNames() {
        // First char: letter or underscore
        Arbitrary<Character> firstChar = Arbitraries.of(
                Arbitraries.chars().alpha(),
                Arbitraries.just('_')
        ).flatMap(c -> c);

        // Rest chars: letter, digit, underscore, hyphen
        Arbitrary<String> restChars = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_', '-')
                .ofMinLength(0)
                .ofMaxLength(20);

        return Combinators.combine(firstChar, restChars)
                .as((first, rest) -> first + rest);
    }

    @Provide
    Arbitrary<String> invalidParameterNames() {
        return Arbitraries.oneOf(
                // Starts with digit
                Arbitraries.strings()
                        .withCharRange('0', '9')
                        .ofLength(1)
                        .flatMap(digit -> Arbitraries.strings()
                                .withCharRange('a', 'z')
                                .ofMinLength(0).ofMaxLength(5)
                                .map(rest -> digit + rest)),
                // Starts with hyphen
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .ofMinLength(0).ofMaxLength(5)
                        .map(rest -> "-" + rest),
                // Contains spaces
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .ofMinLength(1).ofMaxLength(3)
                        .map(s -> s + " " + s),
                // Contains special characters
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .ofMinLength(1).ofMaxLength(3)
                        .flatMap(prefix -> Arbitraries.of('.', '@', '#', '$', '!', '+', '=')
                                .map(special -> prefix + special + "x")),
                // Empty string
                Arbitraries.just("")
        );
    }

    @Provide
    Arbitrary<String> arbitraryStrings() {
        return Arbitraries.oneOf(
                // Valid names (should pass)
                validParameterNames(),
                // Random ASCII strings (mix of valid and invalid)
                Arbitraries.strings()
                        .ascii()
                        .ofMinLength(0)
                        .ofMaxLength(15),
                // Unicode strings (should fail)
                Arbitraries.strings()
                        .ofMinLength(1)
                        .ofMaxLength(10)
        ).filter(s -> s != null);
    }
}

