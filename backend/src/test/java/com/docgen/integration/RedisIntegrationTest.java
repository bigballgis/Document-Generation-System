package com.docgen.integration;

import com.docgen.entity.DataSource;
import com.docgen.entity.RateLimitConfig;
import com.docgen.entity.Tenant;
import com.docgen.exception.RateLimitExceededException;
import com.docgen.repository.DataSourceRepository;
import com.docgen.repository.RateLimitConfigRepository;
import com.docgen.repository.TenantRepository;
import com.docgen.service.DataSourceCacheService;
import com.docgen.service.RateLimitService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Redis operations: data caching and rate limit counters.
 * Validates: Requirements 14, 36
 */
class RedisIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DataSourceCacheService cacheService;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private DataSourceRepository dataSourceRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private RateLimitConfigRepository rateLimitConfigRepository;

    private Long tenantId;

    @BeforeEach
    void setUp() {
        // Create a tenant for data source tests
        Tenant tenant = new Tenant();
        tenant.setName("redis-test-tenant-" + System.nanoTime());
        tenant.setStatus("ACTIVE");
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();
    }

    @AfterEach
    void tearDown() {
        // Clean up Redis keys
        var keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        dataSourceRepository.deleteAll();
        rateLimitConfigRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    // ── Data Caching Tests ──

    @Test
    void shouldCacheDataSourceResponse() {
        DataSource ds = createCacheEnabledDataSource(300);

        Map<String, Object> params = Map.of("key", "value");
        String[] callCount = {""};

        // First call — cache miss
        String result1 = cacheService.getCachedOrFetch(ds.getId(), params, () -> {
            callCount[0] = "called";
            return "{\"data\": \"fresh\"}";
        });

        assertThat(result1).isEqualTo("{\"data\": \"fresh\"}");
        assertThat(callCount[0]).isEqualTo("called");

        // Second call — cache hit (supplier should not be called)
        callCount[0] = "";
        String result2 = cacheService.getCachedOrFetch(ds.getId(), params, () -> {
            callCount[0] = "called-again";
            return "{\"data\": \"should-not-be-used\"}";
        });

        assertThat(result2).isEqualTo("{\"data\": \"fresh\"}");
        assertThat(callCount[0]).isEmpty(); // supplier was not called
    }

    @Test
    void shouldBypassCacheWhenDisabled() {
        DataSource ds = createCacheDisabledDataSource();

        Map<String, Object> params = Map.of("key", "value");

        String result1 = cacheService.getCachedOrFetch(ds.getId(), params, () -> "response-1");
        String result2 = cacheService.getCachedOrFetch(ds.getId(), params, () -> "response-2");

        // Both calls should hit the supplier since cache is disabled
        assertThat(result1).isEqualTo("response-1");
        assertThat(result2).isEqualTo("response-2");
    }

    @Test
    void shouldClearCacheForDataSource() {
        DataSource ds = createCacheEnabledDataSource(300);

        Map<String, Object> params = Map.of("key", "value");

        // Populate cache
        cacheService.getCachedOrFetch(ds.getId(), params, () -> "{\"data\": \"cached\"}");

        // Verify cache is populated by fetching again (should return cached value)
        String[] supplierCalled = {""};
        String cached = cacheService.getCachedOrFetch(ds.getId(), params, () -> {
            supplierCalled[0] = "called";
            return "{\"data\": \"fresh\"}";
        });
        assertThat(cached).isEqualTo("{\"data\": \"cached\"}");
        assertThat(supplierCalled[0]).isEmpty();

        // Clear cache
        cacheService.clearCache(ds.getId());

        // After clearing, supplier should be called again
        String afterClear = cacheService.getCachedOrFetch(ds.getId(), params, () -> "{\"data\": \"after-clear\"}");
        assertThat(afterClear).isEqualTo("{\"data\": \"after-clear\"}");
    }

    // ── Rate Limit Counter Tests ──

    @Test
    void shouldAllowRequestsWithinLimit() {
        // Should not throw for requests within limit
        rateLimitService.checkRateLimit(999L, 10, 100, 1000);
        rateLimitService.checkRateLimit(999L, 10, 100, 1000);
        rateLimitService.checkRateLimit(999L, 10, 100, 1000);
        // No exception means success
    }

    @Test
    void shouldRejectRequestsExceedingPerSecondLimit() {
        Long apiKeyId = 1000L + System.nanoTime() % 10000;

        // Fill up the per-second limit (limit = 2)
        rateLimitService.checkRateLimit(apiKeyId, 2, 1000, 10000);
        rateLimitService.checkRateLimit(apiKeyId, 2, 1000, 10000);

        // Third request should be rejected
        assertThatThrownBy(() -> rateLimitService.checkRateLimit(apiKeyId, 2, 1000, 10000))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("per second");
    }

    @Test
    void shouldTrackMonthlyQuota() {
        // Create rate limit config with a small quota
        RateLimitConfig config = new RateLimitConfig();
        config.setTenantId(tenantId);
        config.setMonthlyQuota(3);
        config.setCurrentMonthUsage(0);
        config.setResetAt(YearMonth.now(ZoneOffset.UTC).plusMonths(1)
                .atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        rateLimitConfigRepository.save(config);

        // Use up the quota
        rateLimitService.checkMonthlyQuota(tenantId);
        rateLimitService.checkMonthlyQuota(tenantId);
        rateLimitService.checkMonthlyQuota(tenantId);

        // Fourth call should exceed quota
        assertThatThrownBy(() -> rateLimitService.checkMonthlyQuota(tenantId))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("quota");
    }

    @Test
    void shouldStoreAndRetrieveRedisValues() {
        // Basic Redis connectivity test
        redisTemplate.opsForValue().set("test:key", "test-value", 60, TimeUnit.SECONDS);
        String value = redisTemplate.opsForValue().get("test:key");
        assertThat(value).isEqualTo("test-value");
    }

    // ── Helpers ──

    private DataSource createCacheEnabledDataSource(int ttl) {
        DataSource ds = new DataSource();
        ds.setTemplateId(1L);
        ds.setName("cached-ds-" + System.nanoTime());
        ds.setType("HTTP_API");
        ds.setConfigJson("{\"url\": \"http://example.com/api\"}");
        ds.setCacheEnabled(true);
        ds.setCacheTtl(ttl);
        return dataSourceRepository.save(ds);
    }

    private DataSource createCacheDisabledDataSource() {
        DataSource ds = new DataSource();
        ds.setTemplateId(1L);
        ds.setName("uncached-ds-" + System.nanoTime());
        ds.setType("HTTP_API");
        ds.setConfigJson("{\"url\": \"http://example.com/api\"}");
        ds.setCacheEnabled(false);
        return dataSourceRepository.save(ds);
    }
}
