package com.docgen.service;

import com.docgen.dto.ApiKeyDTO;
import com.docgen.dto.ApiKeyInfo;
import com.docgen.dto.CreateApiKeyRequest;
import com.docgen.entity.ApiKey;
import com.docgen.entity.User;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.filter.ApiKeyAuthenticationFilter;
import com.docgen.repository.ApiKeyRepository;
import com.docgen.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Service for API Key CRUD operations.
 * Also implements {@link ApiKeyValidationService} so the authentication filter
 * can validate keys against the database.
 */
@Service
public class ApiKeyService implements ApiKeyValidationService {

    private static final int RAW_KEY_BYTE_LENGTH = 32;
    private static final String KEY_PREFIX = "dg_";

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom;

    @Autowired
    public ApiKeyService(ApiKeyRepository apiKeyRepository, UserRepository userRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
        this.secureRandom = new SecureRandom();
    }

    // ── Visible for testing ──
    ApiKeyService(ApiKeyRepository apiKeyRepository, UserRepository userRepository, SecureRandom secureRandom) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
        this.secureRandom = secureRandom;
    }

    /**
     * Create a new API Key. The raw key is returned only once in the response.
     */
    @Transactional
    public ApiKeyDTO createApiKey(Long tenantId, Long userId, CreateApiKeyRequest request) {
        String rawKey = generateRawKey();
        String keyHash = ApiKeyAuthenticationFilter.sha256Hex(rawKey);

        ApiKey entity = new ApiKey();
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setKeyHash(keyHash);
        entity.setName(request.getName());
        if (request.getRateLimitPerSecond() != null) {
            entity.setRateLimitPerSecond(request.getRateLimitPerSecond());
        }
        if (request.getRateLimitPerMinute() != null) {
            entity.setRateLimitPerMinute(request.getRateLimitPerMinute());
        }
        if (request.getRateLimitPerHour() != null) {
            entity.setRateLimitPerHour(request.getRateLimitPerHour());
        }
        entity.setExpiresAt(request.getExpiresAt());

        ApiKey saved = apiKeyRepository.save(entity);

        ApiKeyDTO dto = toDTO(saved);
        dto.setRawKey(rawKey);
        dto.setKeyPrefix(rawKey.substring(0, Math.min(rawKey.length(), 8)) + "...");
        return dto;
    }

    /**
     * List all API Keys for a tenant.
     */
    @Transactional(readOnly = true)
    public List<ApiKeyDTO> listApiKeys(Long tenantId) {
        return apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Enable an API Key.
     */
    @Transactional
    public ApiKeyDTO enableApiKey(Long id, Long tenantId) {
        ApiKey key = findByIdAndTenant(id, tenantId);
        key.setEnabled(true);
        return toDTO(apiKeyRepository.save(key));
    }

    /**
     * Disable an API Key.
     */
    @Transactional
    public ApiKeyDTO disableApiKey(Long id, Long tenantId) {
        ApiKey key = findByIdAndTenant(id, tenantId);
        key.setEnabled(false);
        return toDTO(apiKeyRepository.save(key));
    }

    /**
     * Delete an API Key.
     */
    @Transactional
    public void deleteApiKey(Long id, Long tenantId) {
        ApiKey key = findByIdAndTenant(id, tenantId);
        apiKeyRepository.delete(key);
    }

    // ── ApiKeyValidationService implementation ──

    @Override
    @Transactional(readOnly = true)
    public Optional<ApiKeyInfo> validateApiKey(String keyHash) {
        return apiKeyRepository.findByKeyHash(keyHash)
                .filter(ApiKey::isEnabled)
                .filter(key -> key.getExpiresAt() == null || key.getExpiresAt().isAfter(Instant.now()))
                .map(key -> {
                    // Look up user to get role and teamId
                    User user = userRepository.findById(key.getUserId()).orElse(null);
                    String role = user != null ? user.getRole() : "USER";
                    Long teamId = user != null ? user.getTeamId() : null;

                    return new ApiKeyInfo(
                            key.getId(),
                            key.getTenantId(),
                            key.getUserId(),
                            role,
                            teamId,
                            key.getRateLimitPerSecond(),
                            key.getRateLimitPerMinute(),
                            key.getRateLimitPerHour(),
                            key.isEnabled(),
                            key.getExpiresAt()
                    );
                });
    }

    // ── Private helpers ──

    private ApiKey findByIdAndTenant(Long id, Long tenantId) {
        ApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.API_KEY_NOT_FOUND,
                        "API Key not found: " + id));
        if (!key.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException(
                    ErrorCode.API_KEY_NOT_FOUND,
                    "API Key not found: " + id);
        }
        return key;
    }

    String generateRawKey() {
        byte[] bytes = new byte[RAW_KEY_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ApiKeyDTO toDTO(ApiKey entity) {
        ApiKeyDTO dto = new ApiKeyDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        // Show first 8 chars of the hash as a prefix identifier
        String hash = entity.getKeyHash();
        dto.setKeyPrefix(hash.substring(0, Math.min(hash.length(), 8)) + "...");
        dto.setEnabled(entity.isEnabled());
        dto.setRateLimitPerSecond(entity.getRateLimitPerSecond());
        dto.setRateLimitPerMinute(entity.getRateLimitPerMinute());
        dto.setRateLimitPerHour(entity.getRateLimitPerHour());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setExpiresAt(entity.getExpiresAt());
        return dto;
    }
}
