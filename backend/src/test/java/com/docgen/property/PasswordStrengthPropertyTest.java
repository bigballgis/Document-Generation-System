package com.docgen.property;

import com.docgen.exception.BusinessException;
import com.docgen.service.UserService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for password strength validation.
 *
 * <p><b>Validates: Requirements 48.2</b></p>
 */
@Tag("Feature: low-code-document-generation-system, Property 14: 密码强度验证正确性")
class PasswordStrengthPropertyTest {

    private final UserService userService = new UserService(null, null, null, null, null);

    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*()-_=+[]{}|;:',.<>?/`~";

    /**
     * Property 1: Valid passwords (containing all required character types, length ≥8)
     * should be accepted without throwing any exception.
     */
    @Property(tries = 100)
    void validPasswordsShouldBeAccepted(
            @ForAll("validPasswords") String password
    ) {
        assertDoesNotThrow(() -> userService.validatePasswordStrength(password),
                "Password '" + password + "' should be accepted but was rejected");
    }

    /**
     * Property 2: Passwords shorter than 8 characters should be rejected.
     */
    @Property(tries = 100)
    void passwordsShorterThan8CharsShouldBeRejected(
            @ForAll("shortPasswords") String password
    ) {
        Assume.that(password.length() < 8);
        assertThrows(BusinessException.class,
                () -> userService.validatePasswordStrength(password),
                "Password of length " + password.length() + " should be rejected");
    }

    /**
     * Property 3: Passwords missing uppercase letters should be rejected.
     */
    @Property(tries = 100)
    void passwordsMissingUppercaseShouldBeRejected(
            @ForAll("passwordsWithoutUppercase") String password
    ) {
        assertThrows(BusinessException.class,
                () -> userService.validatePasswordStrength(password),
                "Password without uppercase '" + password + "' should be rejected");
    }

    /**
     * Property 4: Passwords missing lowercase letters should be rejected.
     */
    @Property(tries = 100)
    void passwordsMissingLowercaseShouldBeRejected(
            @ForAll("passwordsWithoutLowercase") String password
    ) {
        assertThrows(BusinessException.class,
                () -> userService.validatePasswordStrength(password),
                "Password without lowercase '" + password + "' should be rejected");
    }

    /**
     * Property 5: Passwords missing digits should be rejected.
     */
    @Property(tries = 100)
    void passwordsMissingDigitsShouldBeRejected(
            @ForAll("passwordsWithoutDigits") String password
    ) {
        assertThrows(BusinessException.class,
                () -> userService.validatePasswordStrength(password),
                "Password without digits '" + password + "' should be rejected");
    }

    /**
     * Property 6: Passwords missing special characters should be rejected.
     */
    @Property(tries = 100)
    void passwordsMissingSpecialCharsShouldBeRejected(
            @ForAll("passwordsWithoutSpecialChars") String password
    ) {
        assertThrows(BusinessException.class,
                () -> userService.validatePasswordStrength(password),
                "Password without special chars '" + password + "' should be rejected");
    }


    @Provide
    Arbitrary<String> validPasswords() {
        // Build a password that always contains at least one of each required type
        Arbitrary<Character> upper = Arbitraries.of(UPPERCASE.chars().mapToObj(c -> (char) c).toArray(Character[]::new));
        Arbitrary<Character> lower = Arbitraries.of(LOWERCASE.chars().mapToObj(c -> (char) c).toArray(Character[]::new));
        Arbitrary<Character> digit = Arbitraries.of(DIGITS.chars().mapToObj(c -> (char) c).toArray(Character[]::new));
        Arbitrary<Character> special = Arbitraries.of(SPECIAL.chars().mapToObj(c -> (char) c).toArray(Character[]::new));

        String allChars = UPPERCASE + LOWERCASE + DIGITS + SPECIAL;
        Arbitrary<Character> anyChar = Arbitraries.of(allChars.chars().mapToObj(c -> (char) c).toArray(Character[]::new));

        // Generate 4 mandatory chars + 4-12 random filler chars, then shuffle
        return Combinators.combine(upper, lower, digit, special,
                        anyChar.list().ofMinSize(4).ofMaxSize(12))
                .as((u, l, d, s, filler) -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append(u).append(l).append(d).append(s);
                    filler.forEach(sb::append);
                    // Shuffle to avoid predictable positions
                    char[] chars = sb.toString().toCharArray();
                    for (int i = chars.length - 1; i > 0; i--) {
                        int j = (int) (Math.random() * (i + 1));
                        char tmp = chars[i];
                        chars[i] = chars[j];
                        chars[j] = tmp;
                    }
                    return new String(chars);
                });
    }

    @Provide
    Arbitrary<String> shortPasswords() {
        // Passwords with length 1-7 containing all char types where possible
        return Arbitraries.integers().between(1, 7).flatMap(len -> {
            String allChars = UPPERCASE + LOWERCASE + DIGITS + SPECIAL;
            Arbitrary<Character> anyChar = Arbitraries.of(
                    allChars.chars().mapToObj(c -> (char) c).toArray(Character[]::new));
            return anyChar.list().ofSize(len).map(chars -> {
                StringBuilder sb = new StringBuilder();
                chars.forEach(sb::append);
                return sb.toString();
            });
        });
    }

    @Provide
    Arbitrary<String> passwordsWithoutUppercase() {
        // ≥8 chars, has lowercase + digit + special, but NO uppercase
        String noUpperChars = LOWERCASE + DIGITS + SPECIAL;
        Arbitrary<Character> lower = Arbitraries.of(LOWERCASE.chars().mapToObj(c -> (char) c).toArray(Character[]::new));
        Arbitrary<Character> digit = Arbitraries.of(DIGITS.chars().mapToObj(c -> (char) c).toArray(Character[]::new));
        Arbitrary<Character> special = Arbitraries.of(SPECIAL.chars().mapToObj(c -> (char) c).toArray(Character[]::new));
        Arbitrary<Character> filler = Arbitraries.of(noUpperChars.chars().mapToObj(c -> (char) c).toArray(Character[]::new));

        return Combinators.combine(lower, digit, special, filler.list().ofMinSize(5).ofMaxSize(13))
                .as((l, d, s, fill) -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append(l).append(d).append(s);
                    fill.forEach(sb::append);
                    return shuffleString(sb.toString());
                });
    }

    @Provide
    Arbitrary<String> passwordsWithoutLowercase() {
        // ≥8 chars, has uppercase + digit + special, but NO lowercase
        String noLowerChars = UPPERCASE + DIGITS + SPECIAL;
        Arbitrary<Character> upper = Arbitraries.of(UPPERCASE.chars().mapToObj(c -> (char) c).toArray(Character[]::new));
        Arbitrary<Character> digit = Arbitraries.of(DIGITS.chars().mapToObj(c -> (char) c).toArray(Character[]::new));
        Arbitrary<Character> special = Arbitraries.of(SPECIAL.chars().mapToObj(c -> (char) c).toArray(Character[]::new));
        Arbitrary<Character> filler = Arbitraries.of(noLowerChars.chars().mapToObj(c -> (char) c).toArray(Character[]::new));

        return Combinators.combine(upper, digit, special, filler.list().ofMinSize(5).ofMaxSize(13))
                .as((u, d, s, fill) -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append(u).append(d).append(s);
                    fill.forEach(sb::append);
                    return shuffleString(sb.toString());
                });
    }

    @Provide
    Arbitrary<String> passwordsWithoutDigits() {
        // ≥8 chars, has uppercase + lowercase + special, but NO digits
        String noDigitChars = UPPERCASE + LOWERCASE + SPECIAL;
        Arbitrary<Character> upper = Arbitraries.of(UPPERCASE.chars().mapToObj(c -> (char) c).toArray(Character[]::new));
        Arbitrary<Character> lower = Arbitraries.of(LOWERCASE.chars().mapToObj(c -> (char) c).toArray(Character[]::new));
        Arbitrary<Character> special = Arbitraries.of(SPECIAL.chars().mapToObj(c -> (char) c).toArray(Character[]::new));
        Arbitrary<Character> filler = Arbitraries.of(noDigitChars.chars().mapToObj(c -> (char) c).toArray(Character[]::new));

        return Combinators.combine(upper, lower, special, filler.list().ofMinSize(5).ofMaxSize(13))
                .as((u, l, s, fill) -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append(u).append(l).append(s);
                    fill.forEach(sb::append);
                    return shuffleString(sb.toString());
                });
    }

    @Provide
    Arbitrary<String> passwordsWithoutSpecialChars() {
        // ≥8 chars, has uppercase + lowercase + digit, but NO special chars
        String noSpecialChars = UPPERCASE + LOWERCASE + DIGITS;
        Arbitrary<Character> upper = Arbitraries.of(UPPERCASE.chars().mapToObj(c -> (char) c).toArray(Character[]::new));
        Arbitrary<Character> lower = Arbitraries.of(LOWERCASE.chars().mapToObj(c -> (char) c).toArray(Character[]::new));
        Arbitrary<Character> digit = Arbitraries.of(DIGITS.chars().mapToObj(c -> (char) c).toArray(Character[]::new));
        Arbitrary<Character> filler = Arbitraries.of(noSpecialChars.chars().mapToObj(c -> (char) c).toArray(Character[]::new));

        return Combinators.combine(upper, lower, digit, filler.list().ofMinSize(5).ofMaxSize(13))
                .as((u, l, d, fill) -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append(u).append(l).append(d);
                    fill.forEach(sb::append);
                    return shuffleString(sb.toString());
                });
    }

    private static String shuffleString(String input) {
        char[] chars = input.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = (int) (Math.random() * (i + 1));
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}

