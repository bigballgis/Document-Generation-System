package com.docgen.property;

import com.docgen.exception.ErrorCode;
import com.docgen.exception.RateLimitExceededException;
import com.docgen.repository.RateLimitConfigRepository;
import com.docgen.service.RateLimitService;
import net.jqwik.api.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for API rate limiting execution correctness.
 *
 * <p>Verifies that when API call count exceeds the configured frequency limit,
 * the service throws {@link RateLimitExceededException} with appropriate error code,
 * and the exception carries Retry-After and remaining count information.</p>
 *
 * <p><b>Validates: Requirements 36.1, 36.3, 36.4</b></p>
 */
@Tag("Feature: low-code-document-generation-system, Property 11: API 限流执行正确性")
class RateLimitPropertyTest {

    /**
     * Creates a RateLimitService with mocked Redis that simulates a sliding window
     * containing {@code existingCount} entries in all windows.
     */
    @SuppressWarnings("unchecked")
    private RateLimitService createServiceWithWindowCount(long existingCount) {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        RateLimitConfigRepository rateLimitConfigRepository = mock(RateLimitConfigRepository.class);

        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        // removeRangeByScore is called to clean expired entries — no-op
        when(zSetOps.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        // zCard returns the simulated count of entries in the window
        when(zSetOps.zCard(anyString())).thenReturn(existingCount);
        // add and expire are no-ops for recording
        when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(redisTemplate.expire(anyString(), any())).thenReturn(true);

        return new RateLimitService(redisTemplate, rateLimitConfigRepository);
    }


    /**
     * Property 11a: When the number of calls in any window equals or exceeds the limit,
     * checkRateLimit MUST throw RateLimitExceededException with RATE_LIMIT_EXCEEDED code.
     */
    @Property(tries = 50)
    void exceedingPerSecondLimitShouldThrow429(
            @ForAll("rateLimitConfigs") RateLimitConfig config
    ) {
        // Simulate existing count equal to the per-second limit (the smallest window)
        long existingCount = config.limitPerSecond();
        RateLimitService service = createServiceWithWindowCount(existingCount);

        RateLimitExceededException thrown = assertThrows(
                RateLimitExceededException.class,
                () -> service.checkRateLimit(
                        config.apiKeyId(),
                        config.limitPerSecond(),
                        config.limitPerMinute(),
                        config.limitPerHour()
                ),
                "Should throw RateLimitExceededException when per-second limit is reached"
        );

        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, thrown.getErrorCode(),
                "Error code must be RATE_LIMIT_EXCEEDED");
        assertTrue(thrown.getRetryAfterSeconds() >= 1,
                "Retry-After must be at least 1 second, got: " + thrown.getRetryAfterSeconds());
        assertNotNull(thrown.getMessage(), "Error message must not be null");
        assertFalse(thrown.getMessage().isBlank(), "Error message must not be blank");
    }

    /**
     * Property 11b: When the number of calls in the per-minute window equals or exceeds
     * the per-minute limit (but per-second is fine), checkRateLimit MUST throw.
     */
    @Property(tries = 50)
    void exceedingPerMinuteLimitShouldThrow429(
            @ForAll("rateLimitConfigs") RateLimitConfig config
    ) {
        // Per-second window is under limit, but per-minute is at limit
        // We need per-window-specific counts, so use a more targeted mock
        RateLimitService service = createServiceWithPerWindowCounts(
                config.limitPerSecond() - 1,  // under per-second limit
                config.limitPerMinute(),       // at per-minute limit
                config.limitPerHour() - 1      // under per-hour limit
        );

        RateLimitExceededException thrown = assertThrows(
                RateLimitExceededException.class,
                () -> service.checkRateLimit(
                        config.apiKeyId(),
                        config.limitPerSecond(),
                        config.limitPerMinute(),
                        config.limitPerHour()
                ),
                "Should throw RateLimitExceededException when per-minute limit is reached"
        );

        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, thrown.getErrorCode());
        assertTrue(thrown.getRetryAfterSeconds() >= 1,
                "Retry-After must be at least 1 second");
    }

    /**
     * Property 11c: When the number of calls in the per-hour window equals or exceeds
     * the per-hour limit (but per-second and per-minute are fine), checkRateLimit MUST throw.
     */
    @Property(tries = 50)
    void exceedingPerHourLimitShouldThrow429(
            @ForAll("rateLimitConfigs") RateLimitConfig config
    ) {
        RateLimitService service = createServiceWithPerWindowCounts(
                config.limitPerSecond() - 1,  // under per-second limit
                config.limitPerMinute() - 1,  // under per-minute limit
                config.limitPerHour()          // at per-hour limit
        );

        RateLimitExceededException thrown = assertThrows(
                RateLimitExceededException.class,
                () -> service.checkRateLimit(
                        config.apiKeyId(),
                        config.limitPerSecond(),
                        config.limitPerMinute(),
                        config.limitPerHour()
                ),
                "Should throw RateLimitExceededException when per-hour limit is reached"
        );

        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, thrown.getErrorCode());
        assertTrue(thrown.getRetryAfterSeconds() >= 1,
                "Retry-After must be at least 1 second");
    }

    /**
     * Property 11d: When all windows are under their limits, checkRateLimit
     * MUST NOT throw and the request should be recorded.
     */
    @Property(tries = 50)
    void underAllLimitsShouldNotThrow(
            @ForAll("rateLimitConfigs") RateLimitConfig config
    ) {
        RateLimitService service = createServiceWithPerWindowCounts(
                config.limitPerSecond() - 1,  // under per-second limit
                config.limitPerMinute() - 1,  // under per-minute limit
                config.limitPerHour() - 1      // under per-hour limit
        );

        assertDoesNotThrow(
                () -> service.checkRateLimit(
                        config.apiKeyId(),
                        config.limitPerSecond(),
                        config.limitPerMinute(),
                        config.limitPerHour()
                ),
                "Should not throw when all windows are under their limits"
        );
    }

    /**
     * Property 11e: getMinRemaining returns 0 when any limit is exceeded,
     * confirming X-RateLimit-Remaining header correctness.
     */
    @Property(tries = 50)
    void remainingIsZeroWhenLimitExceeded(
            @ForAll("rateLimitConfigs") RateLimitConfig config
    ) {
        // Exceed per-second limit
        RateLimitService service = createServiceWithWindowCount(config.limitPerSecond());

        long remaining = service.getMinRemaining(
                config.apiKeyId(),
                config.limitPerSecond(),
                config.limitPerMinute(),
                config.limitPerHour()
        );

        assertEquals(0, remaining,
                "X-RateLimit-Remaining must be 0 when per-second limit is reached");
    }


    @SuppressWarnings("unchecked")
    private RateLimitService createServiceWithPerWindowCounts(
            long secondCount, long minuteCount, long hourCount
    ) {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        RateLimitConfigRepository rateLimitConfigRepository = mock(RateLimitConfigRepository.class);

        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

        // Return different counts based on the key suffix
        when(zSetOps.zCard(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            if (key.endsWith(":s")) return secondCount;
            if (key.endsWith(":m")) return minuteCount;
            if (key.endsWith(":h")) return hourCount;
            return 0L;
        });

        // For oldestTimestamp — return empty set so it falls back to default
        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(Set.of());

        when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(redisTemplate.expire(anyString(), any())).thenReturn(true);

        return new RateLimitService(redisTemplate, rateLimitConfigRepository);
    }


    @Provide
    Arbitrary<RateLimitConfig> rateLimitConfigs() {
        return Arbitraries.longs().between(1L, 1000L)
                .flatMap(apiKeyId ->
                        Arbitraries.integers().between(1, 100)
                                .flatMap(perSecond ->
                                        Arbitraries.integers().between(perSecond, perSecond * 60)
                                                .flatMap(perMinute ->
                                                        Arbitraries.integers().between(perMinute, perMinute * 60)
                                                                .map(perHour ->
                                                                        new RateLimitConfig(apiKeyId, perSecond, perMinute, perHour)))));
    }

    record RateLimitConfig(long apiKeyId, int limitPerSecond, int limitPerMinute, int limitPerHour) {}
}

