package com.docgen.property;

import com.docgen.service.EncryptionServiceImpl;
import net.jqwik.api.*;

import javax.crypto.KeyGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for sensitive information masking format correctness.
 *
 * <p><b>Validates: Requirements 2.11, 50.3</b></p>
 */
@Tag("Feature: low-code-document-generation-system, Property 4: 敏感信息脱敏格式正确性")
class MaskingFormatPropertyTest {

    private final EncryptionServiceImpl encryptionService;

    MaskingFormatPropertyTest() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        String base64Key = Base64.getEncoder().encodeToString(keyGen.generateKey().getEncoded());
        this.encryptionService = new EncryptionServiceImpl(base64Key);
    }

    /**
     * Property 4: For strings with length >= 8, masked output should show
     * first 4 chars + stars + last 4 chars, with total length equal to original.
     */
    @Property(tries = 200)
    void longStringsMaskedWithPrefixAndSuffix(
            @ForAll("stringsLengthAtLeast8") String input
    ) {
        String masked = encryptionService.mask(input);

        String expectedPrefix = input.substring(0, 4);
        String expectedSuffix = input.substring(input.length() - 4);
        int expectedStarCount = input.length() - 8;

        assertEquals(input.length(), masked.length(),
                "Masked output length must equal original length");
        assertTrue(masked.startsWith(expectedPrefix),
                "Masked output must start with first 4 chars of original");
        assertTrue(masked.endsWith(expectedSuffix),
                "Masked output must end with last 4 chars of original");

        String middle = masked.substring(4, masked.length() - 4);
        assertEquals(expectedStarCount, middle.length(),
                "Middle section length must be original length - 8");
        assertTrue(middle.chars().allMatch(c -> c == '*'),
                "Middle section must be all stars");
    }

    /**
     * Property 4: For strings with length < 8 and > 0, masked output should
     * be all stars with the same length as the original.
     */
    @Property(tries = 200)
    void shortStringsMaskedAsAllStars(
            @ForAll("stringsLength1to7") String input
    ) {
        String masked = encryptionService.mask(input);

        assertEquals(input.length(), masked.length(),
                "Masked output length must equal original length");
        assertTrue(masked.chars().allMatch(c -> c == '*'),
                "All characters must be stars for short strings");
    }

    /**
     * Property 4: Null input returns empty string.
     */
    @Example
    void nullInputReturnsEmpty() {
        assertEquals("", encryptionService.mask(null));
    }

    /**
     * Property 4: Empty string input returns empty string.
     */
    @Example
    void emptyInputReturnsEmpty() {
        assertEquals("", encryptionService.mask(""));
    }

    // ── Generators ──

    @Provide
    Arbitrary<String> stringsLengthAtLeast8() {
        return Arbitraries.oneOf(
                // Exactly 8 characters (boundary)
                Arbitraries.strings().ascii().ofLength(8),
                // 9-50 characters
                Arbitraries.strings()
                        .withCharRange('!', '~')
                        .ofMinLength(9).ofMaxLength(50),
                // Longer strings
                Arbitraries.strings().ascii().ofMinLength(51).ofMaxLength(200),
                // Unicode strings
                Arbitraries.strings()
                        .withCharRange('\u4e00', '\u9fff')
                        .ofMinLength(8).ofMaxLength(30)
        );
    }

    @Provide
    Arbitrary<String> stringsLength1to7() {
        return Arbitraries.oneOf(
                // Single character
                Arbitraries.strings().ascii().ofLength(1),
                // 2-7 characters
                Arbitraries.strings()
                        .withCharRange('!', '~')
                        .ofMinLength(2).ofMaxLength(7),
                // Unicode short strings
                Arbitraries.strings()
                        .withCharRange('\u4e00', '\u9fff')
                        .ofMinLength(1).ofMaxLength(7)
        );
    }
}
