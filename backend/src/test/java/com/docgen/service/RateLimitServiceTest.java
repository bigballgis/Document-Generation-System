package com.docgen.service;

import com.docgen.dto.RateLimitStatusDTO;
import com.docgen.dto.UsageStatsDTO;
import com.docgen.entity.RateLimitConfig;
import com.docgen.exception.RateLimitExceededException;
import com.docgen.repository.RateLimitConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RateLimitConfigRepository rateLimitConfigRepository;

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new RateLimitService(redisTemplate, rateLimitConfigRepository);
    }


    @Test
    void checkRateLimit_withinLimits_succeeds() {
        // All windows return 0 count
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.zCard(anyString())).thenReturn(0L);
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        assertDoesNotThrow(() -> service.checkRateLimit(1L, 10, 100, 1000));

        // Verify requests were recorded in all 3 windows
        verify(zSetOperations, times(3)).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void checkRateLimit_exceedsPerSecond_throws429() {
        // Per-second window is full
        when(zSetOperations.removeRangeByScore(contains(":s"), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.zCard(contains(":s"))).thenReturn(10L);

        RateLimitExceededException ex = assertThrows(
                RateLimitExceededException.class,
                () -> service.checkRateLimit(1L, 10, 100, 1000));

        assertTrue(ex.getMessage().contains("per second"));
        assertEquals(1L, ex.getRetryAfterSeconds());
    }

    @Test
    void checkRateLimit_exceedsPerMinute_throws429() {
        // Per-second OK, per-minute full
        when(zSetOperations.removeRangeByScore(contains(":s"), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.zCard(contains(":s"))).thenReturn(5L);

        when(zSetOperations.removeRangeByScore(contains(":m"), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.zCard(contains(":m"))).thenReturn(100L);
        when(zSetOperations.rangeByScore(contains(":m"), anyDouble(), anyDouble(), eq(0L), eq(1L)))
                .thenReturn(Set.of((System.currentTimeMillis() - 30000) + ":1"));

        RateLimitExceededException ex = assertThrows(
                RateLimitExceededException.class,
                () -> service.checkRateLimit(1L, 10, 100, 1000));

        assertTrue(ex.getMessage().contains("per minute"));
        assertTrue(ex.getRetryAfterSeconds() > 0);
    }

    @Test
    void checkRateLimit_exceedsPerHour_throws429() {
        // Per-second and per-minute OK, per-hour full
        when(zSetOperations.removeRangeByScore(contains(":s"), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.zCard(contains(":s"))).thenReturn(5L);

        when(zSetOperations.removeRangeByScore(contains(":m"), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.zCard(contains(":m"))).thenReturn(50L);

        when(zSetOperations.removeRangeByScore(contains(":h"), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.zCard(contains(":h"))).thenReturn(1000L);
        when(zSetOperations.rangeByScore(contains(":h"), anyDouble(), anyDouble(), eq(0L), eq(1L)))
                .thenReturn(Set.of((System.currentTimeMillis() - 1800000) + ":1"));

        RateLimitExceededException ex = assertThrows(
                RateLimitExceededException.class,
                () -> service.checkRateLimit(1L, 10, 100, 1000));

        assertTrue(ex.getMessage().contains("per hour"));
        assertTrue(ex.getRetryAfterSeconds() > 0);
    }


    @Test
    void checkMonthlyQuota_noConfig_passes() {
        when(rateLimitConfigRepository.findByTenantId(1L)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.checkMonthlyQuota(1L));
    }

    @Test
    void checkMonthlyQuota_withinQuota_passes() {
        RateLimitConfig config = createConfig(1L, 100000L, 500L);
        when(rateLimitConfigRepository.findByTenantId(1L)).thenReturn(Optional.of(config));
        when(valueOperations.get(anyString())).thenReturn("500");
        when(valueOperations.increment(anyString())).thenReturn(501L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        assertDoesNotThrow(() -> service.checkMonthlyQuota(1L));
    }

    @Test
    void checkMonthlyQuota_exhausted_throws429() {
        RateLimitConfig config = createConfig(1L, 1000L, 1000L);
        when(rateLimitConfigRepository.findByTenantId(1L)).thenReturn(Optional.of(config));
        when(valueOperations.get(anyString())).thenReturn("1000");

        RateLimitExceededException ex = assertThrows(
                RateLimitExceededException.class,
                () -> service.checkMonthlyQuota(1L));

        assertTrue(ex.getMessage().contains("quota"));
    }

    @Test
    void checkMonthlyQuota_nullRedisValue_fallsBackToDb() {
        RateLimitConfig config = createConfig(1L, 100000L, 50L);
        when(rateLimitConfigRepository.findByTenantId(1L)).thenReturn(Optional.of(config));
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.increment(anyString())).thenReturn(51L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        assertDoesNotThrow(() -> service.checkMonthlyQuota(1L));
    }


    @Test
    void getRateLimitStatus_returnsCorrectRemaining() {
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.zCard(contains(":s"))).thenReturn(3L);
        when(zSetOperations.zCard(contains(":m"))).thenReturn(20L);
        when(zSetOperations.zCard(contains(":h"))).thenReturn(100L);

        RateLimitStatusDTO status = service.getRateLimitStatus(1L, "Test Key", 10, 100, 1000);

        assertEquals(1L, status.getApiKeyId());
        assertEquals("Test Key", status.getApiKeyName());
        assertEquals(7, status.getRemainingPerSecond());
        assertEquals(80, status.getRemainingPerMinute());
        assertEquals(900, status.getRemainingPerHour());
    }


    @Test
    void getUsageStats_withConfig_returnsStats() {
        RateLimitConfig config = createConfig(1L, 100000L, 5000L);
        when(rateLimitConfigRepository.findByTenantId(1L)).thenReturn(Optional.of(config));
        when(valueOperations.get(anyString())).thenReturn("5000");

        UsageStatsDTO stats = service.getUsageStats(1L);

        assertEquals(1L, stats.getTenantId());
        assertEquals(100000L, stats.getMonthlyQuota());
        assertEquals(5000L, stats.getCurrentMonthUsage());
        assertEquals(95000L, stats.getRemainingQuota());
    }

    @Test
    void getUsageStats_noConfig_returnsUnlimited() {
        when(rateLimitConfigRepository.findByTenantId(1L)).thenReturn(Optional.empty());

        UsageStatsDTO stats = service.getUsageStats(1L);

        assertEquals(1L, stats.getTenantId());
        assertEquals(-1L, stats.getMonthlyQuota());
        assertEquals(-1L, stats.getRemainingQuota());
    }


    @Test
    void getMinRemaining_returnsMinimumAcrossWindows() {
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.zCard(contains(":s"))).thenReturn(8L);  // remaining = 2
        when(zSetOperations.zCard(contains(":m"))).thenReturn(50L); // remaining = 50
        when(zSetOperations.zCard(contains(":h"))).thenReturn(100L); // remaining = 900

        long min = service.getMinRemaining(1L, 10, 100, 1000);

        assertEquals(2, min);
    }


    private RateLimitConfig createConfig(Long tenantId, long quota, long usage) {
        RateLimitConfig config = new RateLimitConfig();
        config.setId(1L);
        config.setTenantId(tenantId);
        config.setMonthlyQuota(quota);
        config.setCurrentMonthUsage(usage);
        YearMonth nextMonth = YearMonth.now(ZoneOffset.UTC).plusMonths(1);
        config.setResetAt(nextMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        return config;
    }
}

