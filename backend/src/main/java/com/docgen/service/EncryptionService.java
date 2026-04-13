package com.docgen.service;

/**
 * Service for encrypting/decrypting sensitive data using AES-256-GCM.
 * Encryption key is read from the ENCRYPTION_KEY environment variable.
 * Supports key rotation and masking for display purposes.
 */
public interface EncryptionService {

    /**
     * Encrypt a plaintext string using AES-256-GCM.
     * Each call generates a random 12-byte IV prepended to the ciphertext.
     *
     * @param plainText the value to encrypt
     * @return Base64-encoded string containing IV + ciphertext + auth tag
     */
    String encrypt(String plainText);

    /**
     * Decrypt a ciphertext string previously encrypted by {@link #encrypt}.
     *
     * @param cipherText Base64-encoded string containing IV + ciphertext + auth tag
     * @return the original plaintext
     */
    String decrypt(String cipherText);

    /**
     * Rotate the encryption key. Re-encrypts a list of ciphertext values
     * with the new key. The old key must remain available until rotation completes.
     * <p>
     * Full integration with DataSource repository will be connected in task 8.4.
     *
     * @param oldKey       Base64-encoded old encryption key
     * @param newKey       Base64-encoded new encryption key
     * @param cipherTexts  list of ciphertext values encrypted with the old key
     * @return list of ciphertext values re-encrypted with the new key (same order)
     */
    java.util.List<String> rotateKey(String oldKey, String newKey, java.util.List<String> cipherTexts);

    /**
     * Mask a sensitive value for display.
     * <ul>
     *   <li>Length &ge; 8: show first 4 chars + stars + last 4 chars</li>
     *   <li>Length &lt; 8: all stars matching the original length</li>
     * </ul>
     *
     * @param sensitiveValue the value to mask
     * @return masked string
     */
    String mask(String sensitiveValue);
}
