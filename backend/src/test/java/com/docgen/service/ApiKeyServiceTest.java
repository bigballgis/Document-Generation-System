package com.docgen.service;

import com.docgen.dto.ApiKeyDTO;
import com.docgen.dto.ApiKeyInfo;
import com.docgen.dto.CreateApiKeyRequest;
import com.docgen.entity.ApiKey;
import com.docgen.entity.User;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.ApiKeyRepository;
import com.docgen.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private UserRepository userRepository;

    private ApiKeyService service;

    @BeforeEach
    void setUp() {
        service = new ApiKeyService(apiKeyRepository, userRepository);
    }

    // ── createApiKey ──

    @Test
    void createApiKey_success() {
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> {
            ApiKey k = inv.getArgument(0);
            k.setId(1L);
            k.setCreatedAt(Instant.now());
            return k;
        });

        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName("My Key");
        request.setRateLimitPerSecond(20);
        request.setExpiresAt(Instant.now().plusSeconds(86400));

        ApiKeyDTO result = service.createApiKey(10L, 42L, request);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("My Key", result.getName());
        assertTrue(result.isEnabled());
        assertNotNull(result.getRawKey());
        assertTrue(result.getRawKey().startsWith("dg_"));
        assertEquals(20, result.getRateLimitPerSecond());

        // Verify entity was saved with correct fields
        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey saved = captor.getValue();
        assertEquals(10L, saved.getTenantId());
        assertEquals(42L, saved.getUserId());
        assertNotNull(saved.getKeyHash());
        assertFalse(saved.getKeyHash().isEmpty());
    }

    @Test
    void createApiKey_defaultRateLimits() {
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> {
            ApiKey k = inv.getArgument(0);
            k.setId(2L);
            k.setCreatedAt(Instant.now());
            return k;
        });

        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName("Default Key");

        ApiKeyDTO result = service.createApiKey(10L, 42L, request);

        assertEquals(10, result.getRateLimitPerSecond());
        assertEquals(100, result.getRateLimitPerMinute());
        assertEquals(1000, result.getRateLimitPerHour());
        assertNull(result.getExpiresAt());
    }

    // ── listApiKeys ──

    @Test
    void listApiKeys_success() {
        ApiKey k1 = createSampleApiKey(1L, 10L);
        ApiKey k2 = createSampleApiKey(2L, 10L);
        k2.setName("Second Key");
        when(apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(k1, k2));

        List<ApiKeyDTO> result = service.listApiKeys(10L);

        assertEquals(2, result.size());
        assertEquals("Test Key", result.get(0).getName());
        assertEquals("Second Key", result.get(1).getName());
        // Raw key should NOT be populated on list
        assertNull(result.get(0).getRawKey());
    }

    // ── enableApiKey ──

    @Test
    void enableApiKey_success() {
        ApiKey key = createSampleApiKey(1L, 10L);
        key.setEnabled(false);
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyDTO result = service.enableApiKey(1L, 10L);

        assertTrue(result.isEnabled());
    }

    @Test
    void enableApiKey_notFound_throws() {
        when(apiKeyRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.enableApiKey(999L, 10L));
    }

    @Test
    void enableApiKey_wrongTenant_throws() {
        ApiKey key = createSampleApiKey(1L, 10L);
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(key));

        assertThrows(ResourceNotFoundException.class, () -> service.enableApiKey(1L, 99L));
    }

    // ── disableApiKey ──

    @Test
    void disableApiKey_success() {
        ApiKey key = createSampleApiKey(1L, 10L);
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyDTO result = service.disableApiKey(1L, 10L);

        assertFalse(result.isEnabled());
    }

    // ── deleteApiKey ──

    @Test
    void deleteApiKey_success() {
        ApiKey key = createSampleApiKey(1L, 10L);
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(key));

        service.deleteApiKey(1L, 10L);

        verify(apiKeyRepository).delete(key);
    }

    @Test
    void deleteApiKey_notFound_throws() {
        when(apiKeyRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.deleteApiKey(999L, 10L));
    }

    @Test
    void deleteApiKey_wrongTenant_throws() {
        ApiKey key = createSampleApiKey(1L, 10L);
        when(apiKeyRepository.findById(1L)).thenReturn(Optional.of(key));

        assertThrows(ResourceNotFoundException.class, () -> service.deleteApiKey(1L, 99L));
    }

    // ── validateApiKey ──

    @Test
    void validateApiKey_validKey_returnsInfo() {
        ApiKey key = createSampleApiKey(1L, 10L);
        key.setUserId(42L);
        key.setExpiresAt(Instant.now().plusSeconds(3600));
        when(apiKeyRepository.findByKeyHash("abc123")).thenReturn(Optional.of(key));

        User user = new User();
        user.setId(42L);
        user.setRole("ADMIN");
        user.setTeamId(5L);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        Optional<ApiKeyInfo> result = service.validateApiKey("abc123");

        assertTrue(result.isPresent());
        ApiKeyInfo info = result.get();
        assertEquals(1L, info.id());
        assertEquals(10L, info.tenantId());
        assertEquals(42L, info.userId());
        assertEquals("ADMIN", info.role());
        assertEquals(5L, info.teamId());
        assertEquals(10, info.rateLimitPerSecond());
    }

    @Test
    void validateApiKey_disabledKey_returnsEmpty() {
        ApiKey key = createSampleApiKey(1L, 10L);
        key.setEnabled(false);
        when(apiKeyRepository.findByKeyHash("abc123")).thenReturn(Optional.of(key));

        Optional<ApiKeyInfo> result = service.validateApiKey("abc123");

        assertTrue(result.isEmpty());
    }

    @Test
    void validateApiKey_expiredKey_returnsEmpty() {
        ApiKey key = createSampleApiKey(1L, 10L);
        key.setExpiresAt(Instant.now().minusSeconds(3600));
        when(apiKeyRepository.findByKeyHash("abc123")).thenReturn(Optional.of(key));

        Optional<ApiKeyInfo> result = service.validateApiKey("abc123");

        assertTrue(result.isEmpty());
    }

    @Test
    void validateApiKey_noExpiry_returnsInfo() {
        ApiKey key = createSampleApiKey(1L, 10L);
        key.setUserId(42L);
        key.setExpiresAt(null); // no expiry
        when(apiKeyRepository.findByKeyHash("abc123")).thenReturn(Optional.of(key));

        User user = new User();
        user.setId(42L);
        user.setRole("USER");
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        Optional<ApiKeyInfo> result = service.validateApiKey("abc123");

        assertTrue(result.isPresent());
    }

    @Test
    void validateApiKey_unknownHash_returnsEmpty() {
        when(apiKeyRepository.findByKeyHash("unknown")).thenReturn(Optional.empty());

        Optional<ApiKeyInfo> result = service.validateApiKey("unknown");

        assertTrue(result.isEmpty());
    }

    // ── generateRawKey ──

    @Test
    void generateRawKey_startsWithPrefix() {
        String key = service.generateRawKey();
        assertTrue(key.startsWith("dg_"));
        assertTrue(key.length() > 10);
    }

    // ── Helpers ──

    private ApiKey createSampleApiKey(Long id, Long tenantId) {
        ApiKey key = new ApiKey();
        key.setId(id);
        key.setTenantId(tenantId);
        key.setUserId(42L);
        key.setKeyHash("hash_" + id);
        key.setName("Test Key");
        key.setRateLimitPerSecond(10);
        key.setRateLimitPerMinute(100);
        key.setRateLimitPerHour(1000);
        key.setEnabled(true);
        key.setCreatedAt(Instant.now());
        return key;
    }
}
