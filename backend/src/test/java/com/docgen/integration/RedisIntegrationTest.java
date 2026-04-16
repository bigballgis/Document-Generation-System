package com.docgen.integration;

import com.docgen.entity.RateLimitConfig;
import com.docgen.entity.Tenant;
import com.docgen.exception.RateLimitExceededException;
import com.docgen.repository.RateLimitConfigRepository;
import com.docgen.repository.TenantRepository;
import com.docgen.service.RateLimitService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Redis operations: rate limit counters.
 */
class RedisIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private RateLimitConfigRepository rateLimitConfigRepository;

    private Long tenantId;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setName("redis-test-tenant-" + System.nanoTime());
        tenant.setStatus("ACTIVE");
        tenant = tenantRepository.save(tenant);
        tenantId = tenant.getId();
    }

    @AfterEach
    void tearDown() {
        var keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        rateLimitConfigRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    // ── Rate Limit Counter Tests ──

    @Test
    void shouldAllowRequestsWithinLimit() {
        rateLimitService.checkRateLimit(999L, 10, 100, 1000);
        rateLimitService.checkRateLimit(999L, 10, 100, 1000);
        rateLimitService.checkRateLimit(999L, 10, 100, 1000);
        // No exception means success
    }

    @Test
    void shouldRejectRequestsExceedingPerSecondLimit() {
        Long apiKeyId = 1000L + System.nanoTime() % 10000;

        rateLimitService.checkRateLimit(apiKeyId, 2, 1000, 10000);
        rateLimitService.checkRateLimit(apiKeyId, 2, 1000, 10000);

        assertThatThrownBy(() -> rateLimitService.checkRateLimit(apiKeyId, 2, 1000, 10000))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("per second");
    }

    @Test
    void shouldTrackMonthlyQuota() {
        RateLimitConfig config = new RateLimitConfig();
        config.setTenantId(tenantId);
        config.setMonthlyQuota(3);
        config.setCurrentMonthUsage(0);
        config.setResetAt(YearMonth.now(ZoneOffset.UTC).plusMonths(1)
                .atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        rateLimitConfigRepository.save(config);

        rateLimitService.checkMonthlyQuota(tenantId);
        rateLimitService.checkMonthlyQuota(tenantId);
        rateLimitService.checkMonthlyQuota(tenantId);

        assertThatThrownBy(() -> rateLimitService.checkMonthlyQuota(tenantId))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("quota");
    }

    @Test
    void shouldStoreAndRetrieveRedisValues() {
        redisTemplate.opsForValue().set("test:key", "test-value", 60, TimeUnit.SECONDS);
        String value = redisTemplate.opsForValue().get("test:key");
        assertThat(value).isEqualTo("test-value");
    }
}
