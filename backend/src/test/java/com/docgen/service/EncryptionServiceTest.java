package com.docgen.service;

import com.docgen.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionServiceImpl encryptionService;
    private String testKeyBase64;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        testKeyBase64 = generateBase64Key();
        encryptionService = new EncryptionServiceImpl(testKeyBase64);
    }


    @Test
    void encryptDecrypt_roundTrip_returnsOriginal() {
        String original = "my-secret-password";
        String encrypted = encryptionService.encrypt(original);
        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void encryptDecrypt_emptyString_returnsEmpty() {
        String encrypted = encryptionService.encrypt("");
        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals("", decrypted);
    }

    @Test
    void encryptDecrypt_unicodeContent_returnsOriginal() {
        String original = "password🔑パスワード";
        String encrypted = encryptionService.encrypt(original);
        assertEquals(original, encryptionService.decrypt(encrypted));
    }

    @Test
    void encryptDecrypt_specialCharacters_returnsOriginal() {
        String original = "p@$$w0rd!#%^&*()_+-=[]{}|;':\",./<>?";
        String encrypted = encryptionService.encrypt(original);
        assertEquals(original, encryptionService.decrypt(encrypted));
    }

    @Test
    void encrypt_producesUniqueOutputEachTime() {
        String original = "same-input";
        String encrypted1 = encryptionService.encrypt(original);
        String encrypted2 = encryptionService.encrypt(original);
        // Different IVs should produce different ciphertexts
        assertNotEquals(encrypted1, encrypted2);
        // But both should decrypt to the same value
        assertEquals(original, encryptionService.decrypt(encrypted1));
        assertEquals(original, encryptionService.decrypt(encrypted2));
    }

    @Test
    void encrypt_nullInput_throws() {
        assertThrows(BusinessException.class, () -> encryptionService.encrypt(null));
    }

    @Test
    void decrypt_nullInput_throws() {
        assertThrows(BusinessException.class, () -> encryptionService.decrypt(null));
    }

    @Test
    void decrypt_invalidBase64_throws() {
        assertThrows(BusinessException.class, () -> encryptionService.decrypt("not-valid-base64!!!"));
    }

    @Test
    void decrypt_tamperedCiphertext_throws() {
        String encrypted = encryptionService.encrypt("secret");
        // Tamper with the ciphertext
        byte[] bytes = Base64.getDecoder().decode(encrypted);
        bytes[bytes.length - 1] ^= 0xFF;
        String tampered = Base64.getEncoder().encodeToString(bytes);
        assertThrows(BusinessException.class, () -> encryptionService.decrypt(tampered));
    }

    @Test
    void decrypt_wrongKey_throws() throws NoSuchAlgorithmException {
        String encrypted = encryptionService.encrypt("secret");
        // Create a service with a different key
        EncryptionServiceImpl otherService = new EncryptionServiceImpl(generateBase64Key());
        assertThrows(BusinessException.class, () -> otherService.decrypt(encrypted));
    }


    @Test
    void mask_lengthTwelve_showsFirstFourStarsLastFour() {
        // "abcdefghijkl" (12 chars) → "abcd****ijkl"
        assertEquals("abcd****ijkl", encryptionService.mask("abcdefghijkl"));
    }

    @Test
    void mask_lengthTen_showsFirstFourStarsLastFour() {
        // "1234567890" → "1234**7890"
        assertEquals("1234**7890", encryptionService.mask("1234567890"));
    }

    @Test
    void mask_lengthExactlyEight_showsFirstFourAndLastFour() {
        // "12345678" → first4="1234", last4="5678", stars = 8-8 = 0 → "12345678"
        assertEquals("12345678", encryptionService.mask("12345678"));
    }

    @Test
    void mask_lengthLessThanEight_allStars() {
        assertEquals("*******", encryptionService.mask("short12"));
        assertEquals("***", encryptionService.mask("abc"));
        assertEquals("*", encryptionService.mask("x"));
    }

    @Test
    void mask_emptyString_returnsEmpty() {
        assertEquals("", encryptionService.mask(""));
    }

    @Test
    void mask_nullInput_returnsEmpty() {
        assertEquals("", encryptionService.mask(null));
    }


    @Test
    void rotateKey_reEncryptsAllValues() throws NoSuchAlgorithmException {
        String oldKey = testKeyBase64;
        String newKey = generateBase64Key();

        // Encrypt some values with the old key
        List<String> originals = List.of("password1", "api-key-secret", "oauth-token-xyz");
        List<String> encrypted = originals.stream()
                .map(encryptionService::encrypt)
                .toList();

        // Rotate
        List<String> reEncrypted = encryptionService.rotateKey(oldKey, newKey, encrypted);

        assertEquals(encrypted.size(), reEncrypted.size());

        // Verify re-encrypted values can be decrypted with the new key
        EncryptionServiceImpl newService = new EncryptionServiceImpl(newKey);
        for (int i = 0; i < originals.size(); i++) {
            assertEquals(originals.get(i), newService.decrypt(reEncrypted.get(i)));
        }

        // Verify re-encrypted values cannot be decrypted with the old key
        for (String ct : reEncrypted) {
            assertThrows(BusinessException.class, () -> encryptionService.decrypt(ct));
        }
    }

    @Test
    void rotateKey_emptyList_returnsEmptyList() throws NoSuchAlgorithmException {
        String newKey = generateBase64Key();
        List<String> result = encryptionService.rotateKey(testKeyBase64, newKey, List.of());
        assertTrue(result.isEmpty());
    }


    @Test
    void constructor_invalidKeyLength_throws() {
        // 16-byte key (AES-128) should be rejected
        byte[] shortKey = new byte[16];
        String shortKeyBase64 = Base64.getEncoder().encodeToString(shortKey);
        assertThrows(BusinessException.class, () -> new EncryptionServiceImpl(shortKeyBase64));
    }

    @Test
    void constructor_invalidBase64_throws() {
        assertThrows(BusinessException.class, () -> new EncryptionServiceImpl("not-valid-base64!!!"));
    }


    private static String generateBase64Key() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        return Base64.getEncoder().encodeToString(keyGen.generateKey().getEncoded());
    }
}

