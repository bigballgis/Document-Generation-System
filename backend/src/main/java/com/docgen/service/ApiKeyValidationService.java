package com.docgen.service;

import com.docgen.dto.ApiKeyInfo;

import java.util.Optional;

/**
 * Validates an API key by its SHA-256 hash.
 * <p>
 * The actual repository-backed implementation will be provided in task 16.3.
 * For now only the contract is defined so that
 * {@link com.docgen.filter.ApiKeyAuthenticationFilter} can be compiled and tested.
 */
public interface ApiKeyValidationService {

    /**
     * Look up an API key record by its SHA-256 hash.
     *
     * @param keyHash the hex-encoded SHA-256 hash of the raw API key
     * @return the key metadata if the hash exists, the key is enabled,
     *         and it has not expired; empty otherwise
     */
    Optional<ApiKeyInfo> validateApiKey(String keyHash);
}
