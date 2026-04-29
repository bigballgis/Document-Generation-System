package com.docgen.property;

import com.docgen.service.EncryptionServiceImpl;
import net.jqwik.api.*;

import javax.crypto.KeyGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for encryption round-trip consistency.
 *
 * <p><b>Validates: Requirements 2.10, 50.1, 50.2</b></p>
 */
@Tag("Feature: low-code-document-generation-system, Property 3: 敏感信息加密往返一致性")
class EncryptionRoundTripPropertyTest {

    private final EncryptionServiceImpl encryptionService;

    EncryptionRoundTripPropertyTest() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        String base64Key = Base64.getEncoder().encodeToString(keyGen.generateKey().getEncoded());
        this.encryptionService = new EncryptionServiceImpl(base64Key);
    }

    /**
     * Property 3: Encryption round-trip consistency.
     *
     * For any string, encrypting then decrypting should return the original value.
     */
    @Property(tries = 100)
    void encryptThenDecryptShouldReturnOriginal(
            @ForAll("sensitiveStrings") String original
    ) {
        String encrypted = encryptionService.encrypt(original);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(original, decrypted,
                "Decrypted value must equal the original plaintext");
    }

    /**
     * Property 3 (supplementary): Encrypting the same value twice should produce
     * different ciphertexts due to random IV generation.
     */
    @Property(tries = 100)
    void encryptingSameValueTwiceShouldProduceDifferentCiphertexts(
            @ForAll("sensitiveStrings") String original
    ) {
        String encrypted1 = encryptionService.encrypt(original);
        String encrypted2 = encryptionService.encrypt(original);

        assertNotEquals(encrypted1, encrypted2,
                "Two encryptions of the same value should produce different ciphertexts (random IV)");

        // Both should still decrypt to the original
        assertEquals(original, encryptionService.decrypt(encrypted1));
        assertEquals(original, encryptionService.decrypt(encrypted2));
    }


    @Provide
    Arbitrary<String> sensitiveStrings() {
        return Arbitraries.oneOf(
                // Empty string
                Arbitraries.just(""),
                // Short ASCII strings (1-7 chars)
                Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(7),
                // Medium ASCII strings with special characters
                Arbitraries.strings()
                        .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+-=[]{}|;':\",./<>?`~ \t\n\r")
                        .ofMinLength(8).ofMaxLength(100),
                // Long strings
                Arbitraries.strings().ascii().ofMinLength(101).ofMaxLength(500),
                // Unicode: Chinese characters
                Arbitraries.strings()
                        .withCharRange('\u4e00', '\u9fff')
                        .ofMinLength(1).ofMaxLength(50),
                // Unicode: Japanese katakana
                Arbitraries.strings()
                        .withCharRange('\u30a0', '\u30ff')
                        .ofMinLength(1).ofMaxLength(30),
                // Unicode: emoji and mixed content (realistic sensitive values)
                Arbitraries.of(
                        "密码🔑パスワード",
                        "🎉🚀💻🔒",
                        "café résumé naïve",
                        "Ñoño año",
                        "数据源密码: p@$$w0rd!",
                        "Bearer eyJhbGciOiJIUzI1NiJ9.test.sig",
                        "Basic dXNlcjpwYXNz",
                        "\u0000\u0001\u0002\u0003",
                        "key=value&token=abc123"
                ),
                // BMP characters only (avoids unpaired surrogates that break UTF-8 round-trip)
                Arbitraries.strings()
                        .withCharRange('\u0020', '\ud7ff')
                        .ofMinLength(1).ofMaxLength(200)
        );
    }
}

