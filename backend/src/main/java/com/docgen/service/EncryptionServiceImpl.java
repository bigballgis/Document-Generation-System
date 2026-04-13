package com.docgen.service;

import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * AES-256-GCM implementation of {@link EncryptionService}.
 * <p>
 * The encryption key is read from the {@code ENCRYPTION_KEY} environment variable
 * (via {@code encryption.key} property) and must be a Base64-encoded 256-bit (32-byte) key.
 * <p>
 * Encrypted output format: Base64( IV(12 bytes) || ciphertext || GCM auth tag(16 bytes) )
 */
@Service
public class EncryptionServiceImpl implements EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionServiceImpl.class);

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionServiceImpl(@Value("${encryption.key}") String base64Key) {
        this.secretKey = parseKey(base64Key);
    }

    @Override
    public String encrypt(String plainText) {
        if (plainText == null) {
            throw new BusinessException(ErrorCode.ENCRYPTION_FAILED,
                    "待加密内容不能为空", HttpStatus.BAD_REQUEST);
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.ENCRYPTION_FAILED,
                    "加密失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    @Override
    public String decrypt(String cipherText) {
        if (cipherText == null) {
            throw new BusinessException(ErrorCode.DECRYPTION_FAILED,
                    "待解密内容不能为空", HttpStatus.BAD_REQUEST);
        }
        return decryptWithKey(cipherText, secretKey);
    }

    @Override
    public List<String> rotateKey(String oldKeyBase64, String newKeyBase64, List<String> cipherTexts) {
        SecretKeySpec oldKey = parseKey(oldKeyBase64);
        SecretKeySpec newKey = parseKey(newKeyBase64);

        List<String> reEncrypted = new ArrayList<>(cipherTexts.size());
        for (String ct : cipherTexts) {
            String plainText = decryptWithKey(ct, oldKey);
            String newCt = encryptWithKey(plainText, newKey);
            reEncrypted.add(newCt);
        }
        log.info("Key rotation completed: re-encrypted {} values", cipherTexts.size());
        return reEncrypted;
    }

    @Override
    public String mask(String sensitiveValue) {
        if (sensitiveValue == null || sensitiveValue.isEmpty()) {
            return "";
        }
        int len = sensitiveValue.length();
        if (len >= 8) {
            String prefix = sensitiveValue.substring(0, 4);
            String suffix = sensitiveValue.substring(len - 4);
            return prefix + "*".repeat(len - 8) + suffix;
        }
        return "*".repeat(len);
    }

    // ── Private helpers ──

    private SecretKeySpec parseKey(String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            if (keyBytes.length != 32) {
                throw new BusinessException(ErrorCode.ENCRYPTION_KEY_INVALID,
                        "加密密钥必须为256位（32字节）", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.ENCRYPTION_KEY_INVALID,
                    "加密密钥Base64格式无效", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private String decryptWithKey(String cipherText, SecretKeySpec key) {
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            if (combined.length < GCM_IV_LENGTH) {
                throw new BusinessException(ErrorCode.DECRYPTION_FAILED,
                        "密文格式无效", HttpStatus.BAD_REQUEST);
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

            byte[] cipherBytes = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.DECRYPTION_FAILED,
                    "解密失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private String encryptWithKey(String plainText, SecretKeySpec key) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Encryption with provided key failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.ENCRYPTION_FAILED,
                    "加密失败", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }
}
