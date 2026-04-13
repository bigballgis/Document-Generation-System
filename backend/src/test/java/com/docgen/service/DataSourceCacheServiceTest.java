package com.docgen.service;

import com.docgen.entity.DataSource;
import com.docgen.exception.ResourceNotFoundException;
import com.docgen.repository.DataSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSourceCacheServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private DataSourceRepository dataSourceRepository;

    private DataSourceCacheService service;

    @BeforeEach
    void setUp() {
        service = new DataSourceCacheService(redisTemplate, dataSourceRepository);
    }

    // ── buildCacheKey ──

    @Test
    void buildCacheKey_producesExpectedFormat() {
        String key = service.buildCacheKey(42L, Map.of("userId", "123"));
        assertTrue(key.startsWith("datasource:42:"));
        // MD5 hash is 32 hex chars
        String hash = key.substring("datasource:42:".length());
        assertEquals(32, hash.length());
    }

    @Test
    void buildCacheKey_sameParamsProduceSameKey() {
        Map<String, Object> params = Map.of("a", "1", "b", "2");
        String key1 = service.buildCacheKey(1L, params);
        String key2 = service.buildCacheKey(1L, params);
        assertEquals(key1, key2);
    }

    @Test
    void buildCacheKey_differentParamsProduceDifferentKeys() {
        String key1 = service.buildCacheKey(1L, Map.of("a", "1"));
        String key2 = service.buildCacheKey(1L, Map.of("a", "2"));
        assertNotEquals(key1, key2);
    }

    @Test
    void buildCacheKey_differentDataSourceIdsProduceDifferentKeys() {
        Map<String, Object> params = Map.of("x", "y");
        String key1 = service.buildCacheKey(1L, params);
        String key2 = service.buildCacheKey(2L, params);
        assertNotEquals(key1, key2);
    }

    @Test
    void buildCacheKey_nullParamsHandled() {
        String key = service.buildCacheKey(1L, null);
        assertTrue(key.startsWith("datasource:1:"));
    }

    // ── getCachedOrFetch ──

    @Test
    void getCachedOrFetch_dataSourceNotFound_throws() {
        when(dataSourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getCachedOrFetch(99L, Map.of(), () -> "data"));
    }

    @Test
    void getCachedOrFetch_cacheDisabled_callsFetchDirectly() {
        DataSource ds = createDataSource(1L, false, 300);
        when(dataSourceRepository.findById(1L)).thenReturn(Optional.of(ds));

        String result = service.getCachedOrFetch(1L, Map.of(), () -> "fresh-data");

        assertEquals("fresh-data", result);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void getCachedOrFetch_cacheHit_returnsCachedValue() {
        DataSource ds = createDataSource(1L, true, 600);
        when(dataSourceRepository.findById(1L)).thenReturn(Optional.of(ds));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("cached-data");

        String result = service.getCachedOrFetch(1L, Map.of("key", "val"), () -> {
            fail("fetchFunction should not be called on cache hit");
            return null;
        });

        assertEquals("cached-data", result);
    }

    @Test
    void getCachedOrFetch_cacheMiss_fetchesAndCaches() {
        DataSource ds = createDataSource(1L, true, 120);
        when(dataSourceRepository.findById(1L)).thenReturn(Optional.of(ds));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        String result = service.getCachedOrFetch(1L, Map.of("p", "v"), () -> "fetched-data");

        assertEquals("fetched-data", result);
        verify(valueOperations).set(anyString(), eq("fetched-data"), eq(120L), eq(TimeUnit.SECONDS));
    }

    @Test
    void getCachedOrFetch_nullTtl_usesDefault300() {
        DataSource ds = createDataSource(1L, true, null);
        when(dataSourceRepository.findById(1L)).thenReturn(Optional.of(ds));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        service.getCachedOrFetch(1L, Map.of(), () -> "data");

        verify(valueOperations).set(anyString(), eq("data"), eq(300L), eq(TimeUnit.SECONDS));
    }

    // ── clearCache ──

    @Test
    void clearCache_deletesMatchingKeys() {
        Set<String> keys = Set.of("datasource:5:abc", "datasource:5:def");
        when(redisTemplate.keys("datasource:5:*")).thenReturn(keys);

        service.clearCache(5L);

        verify(redisTemplate).delete(keys);
    }

    @Test
    void clearCache_noKeys_doesNotCallDelete() {
        when(redisTemplate.keys("datasource:7:*")).thenReturn(Set.of());

        service.clearCache(7L);

        verify(redisTemplate, never()).delete(anyCollection());
    }

    @Test
    void clearCache_nullKeys_doesNotCallDelete() {
        when(redisTemplate.keys("datasource:8:*")).thenReturn(null);

        service.clearCache(8L);

        verify(redisTemplate, never()).delete(anyCollection());
    }

    // ── helpers ──

    private DataSource createDataSource(Long id, boolean cacheEnabled, Integer cacheTtl) {
        DataSource ds = new DataSource();
        ds.setId(id);
        ds.setCacheEnabled(cacheEnabled);
        ds.setCacheTtl(cacheTtl);
        ds.setName("test-ds");
        ds.setType("HTTP_API");
        ds.setConfigJson("{}");
        ds.setTemplateId(1L);
        return ds;
    }
}
