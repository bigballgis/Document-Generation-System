package com.docgen.service;

import com.docgen.dto.RateLimitStatusDTO;
import com.docgen.dto.UsageStatsDTO;
import com.docgen.entity.RateLimitConfig;
import com.docgen.exception.ErrorCode;
import com.docgen.exception.RateLimitExceededException;
import com.docgen.repository.RateLimitConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

/**
 * Rate limiting service using Redis sliding window algorithm.
 * <p>
 * Supports per-second, per-minute, per-hour frequency limits (based on API Key config)
 * and tenant monthly API call quotas.
 * <p>
 * Redis key patterns:
 * <ul>
 *   <li>{@code ratelimit:{apiKeyId}:s} — per-second sorted set</li>
 *   <li>{@code ratelimit:{apiKeyId}:m} — per-minute sorted set</li>
 *   <li>{@code ratelimit:{apiKeyId}:h} — per-hour sorted set</li>
 *   <li>{@code quota:{tenantId}:{yearMonth}} — monthly quota counter</li>
 * </ul>
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:";
    private static final String QUOTA_KEY_PREFIX = "quota:";

    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitConfigRepository rateLimitConfigRepository;

    public RateLimitService(RedisTemplate<String, String> redisTemplate,
                            RateLimitConfigRepository rateLimitConfigRepository) {
        this.redisTemplate = redisTemplate;
        this.rateLimitConfigRepository = rateLimitConfigRepository;
    }

    /**
     * Check whether the given API key is allowed to make a request.
     * Uses a Redis sorted-set sliding window for per-second/minute/hour limits.
     *
     * @param apiKeyId          the API key ID
     * @param limitPerSecond    max requests per second
     * @param limitPerMinute    max requests per minute
     * @param limitPerHour      max requests per hour
     * @throws RateLimitExceededException if any limit is exceeded
     */
    public void checkRateLimit(Long apiKeyId, int limitPerSecond, int limitPerMinute, int limitPerHour) {
        long now = System.currentTimeMillis();

        // Check per-second limit (window = 1000ms)
        long countSecond = slidingWindowCount(apiKeyId, "s", now, 1000L);
        if (countSecond >= limitPerSecond) {
            throw new RateLimitExceededException(
                    ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Rate limit exceeded: " + limitPerSecond + " requests per second",
                    1L);
        }

        // Check per-minute limit (window = 60000ms)
        long countMinute = slidingWindowCount(apiKeyId, "m", now, 60_000L);
        if (countMinute >= limitPerMinute) {
            long retryAfter = Math.max(1, (60_000L - (now - oldestTimestamp(apiKeyId, "m", now, 60_000L))) / 1000);
            throw new RateLimitExceededException(
                    ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Rate limit exceeded: " + limitPerMinute + " requests per minute",
                    retryAfter);
        }

        // Check per-hour limit (window = 3600000ms)
        long countHour = slidingWindowCount(apiKeyId, "h", now, 3_600_000L);
        if (countHour >= limitPerHour) {
            long retryAfter = Math.max(1, (3_600_000L - (now - oldestTimestamp(apiKeyId, "h", now, 3_600_000L))) / 1000);
            throw new RateLimitExceededException(
                    ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Rate limit exceeded: " + limitPerHour + " requests per hour",
                    retryAfter);
        }

        // All checks passed — record this request in all windows
        String member = now + ":" + Thread.currentThread().getId();
        recordRequest(apiKeyId, "s", now, member, 2_000L);
        recordRequest(apiKeyId, "m", now, member, 120_000L);
        recordRequest(apiKeyId, "h", now, member, 7_200_000L);
    }

    /**
     * Check whether the tenant's monthly quota allows another request.
     *
     * @param tenantId the tenant ID
     * @throws RateLimitExceededException if the monthly quota is exhausted
     */
    public void checkMonthlyQuota(Long tenantId) {
        Optional<RateLimitConfig> configOpt = rateLimitConfigRepository.findByTenantId(tenantId);
        if (configOpt.isEmpty()) {
            // No quota config means unlimited
            return;
        }

        RateLimitConfig config = configOpt.get();

        // Reset usage if past the reset date
        if (Instant.now().isAfter(config.getResetAt())) {
            resetMonthlyUsage(config);
        }

        String quotaKey = buildQuotaKey(tenantId);
        String currentStr = redisTemplate.opsForValue().get(quotaKey);
        long current = currentStr != null ? Long.parseLong(currentStr) : config.getCurrentMonthUsage();

        if (current >= config.getMonthlyQuota()) {
            long retryAfter = Duration.between(Instant.now(), config.getResetAt()).getSeconds();
            throw new RateLimitExceededException(
                    ErrorCode.RATE_LIMIT_QUOTA_EXHAUSTED,
                    "Monthly API quota exhausted. Quota: " + config.getMonthlyQuota(),
                    Math.max(1, retryAfter));
        }

        // Increment usage in Redis
        redisTemplate.opsForValue().increment(quotaKey);

        // Set TTL to expire after the reset date
        Duration ttl = Duration.between(Instant.now(), config.getResetAt().plus(Duration.ofDays(1)));
        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.expire(quotaKey, ttl);
        }
    }

    /**
     * Get the current rate limit status for an API key.
     */
    public RateLimitStatusDTO getRateLimitStatus(Long apiKeyId, String apiKeyName,
                                                  int limitPerSecond, int limitPerMinute, int limitPerHour) {
        long now = System.currentTimeMillis();

        long usedSecond = slidingWindowCount(apiKeyId, "s", now, 1000L);
        long usedMinute = slidingWindowCount(apiKeyId, "m", now, 60_000L);
        long usedHour = slidingWindowCount(apiKeyId, "h", now, 3_600_000L);

        RateLimitStatusDTO dto = new RateLimitStatusDTO();
        dto.setApiKeyId(apiKeyId);
        dto.setApiKeyName(apiKeyName);
        dto.setLimitPerSecond(limitPerSecond);
        dto.setLimitPerMinute(limitPerMinute);
        dto.setLimitPerHour(limitPerHour);
        dto.setRemainingPerSecond(Math.max(0, limitPerSecond - usedSecond));
        dto.setRemainingPerMinute(Math.max(0, limitPerMinute - usedMinute));
        dto.setRemainingPerHour(Math.max(0, limitPerHour - usedHour));
        return dto;
    }

    /**
     * Get usage statistics for a tenant.
     */
    @Transactional(readOnly = true)
    public UsageStatsDTO getUsageStats(Long tenantId) {
        UsageStatsDTO dto = new UsageStatsDTO();
        dto.setTenantId(tenantId);

        Optional<RateLimitConfig> configOpt = rateLimitConfigRepository.findByTenantId(tenantId);
        if (configOpt.isPresent()) {
            RateLimitConfig config = configOpt.get();
            dto.setMonthlyQuota(config.getMonthlyQuota());
            dto.setResetAt(config.getResetAt().toString());

            // Get current usage from Redis (fallback to DB)
            String quotaKey = buildQuotaKey(tenantId);
            String currentStr = redisTemplate.opsForValue().get(quotaKey);
            long current = currentStr != null ? Long.parseLong(currentStr) : config.getCurrentMonthUsage();
            dto.setCurrentMonthUsage(current);
            dto.setRemainingQuota(Math.max(0, config.getMonthlyQuota() - current));
        } else {
            // No config — report unlimited
            dto.setMonthlyQuota(-1);
            dto.setCurrentMonthUsage(0);
            dto.setRemainingQuota(-1);
        }

        return dto;
    }

    /**
     * Get the minimum remaining count across all windows for the X-RateLimit-Remaining header.
     */
    public long getMinRemaining(Long apiKeyId, int limitPerSecond, int limitPerMinute, int limitPerHour) {
        long now = System.currentTimeMillis();
        long remS = Math.max(0, limitPerSecond - slidingWindowCount(apiKeyId, "s", now, 1000L));
        long remM = Math.max(0, limitPerMinute - slidingWindowCount(apiKeyId, "m", now, 60_000L));
        long remH = Math.max(0, limitPerHour - slidingWindowCount(apiKeyId, "h", now, 3_600_000L));
        return Math.min(remS, Math.min(remM, remH));
    }

    // ── Private helpers ──

    private long slidingWindowCount(Long apiKeyId, String suffix, long now, long windowMs) {
        String key = buildRateLimitKey(apiKeyId, suffix);
        // Remove expired entries
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, now - windowMs);
        // Count entries in the window
        Long count = redisTemplate.opsForZSet().zCard(key);
        return count != null ? count : 0L;
    }

    private long oldestTimestamp(Long apiKeyId, String suffix, long now, long windowMs) {
        String key = buildRateLimitKey(apiKeyId, suffix);
        Set<String> oldest = redisTemplate.opsForZSet().rangeByScore(key, now - windowMs, now, 0, 1);
        if (oldest != null && !oldest.isEmpty()) {
            String member = oldest.iterator().next();
            String tsStr = member.contains(":") ? member.substring(0, member.indexOf(':')) : member;
            try {
                return Long.parseLong(tsStr);
            } catch (NumberFormatException e) {
                return now - windowMs;
            }
        }
        return now - windowMs;
    }

    private void recordRequest(Long apiKeyId, String suffix, long now, String member, long ttlMs) {
        String key = buildRateLimitKey(apiKeyId, suffix);
        redisTemplate.opsForZSet().add(key, member, now);
        redisTemplate.expire(key, Duration.ofMillis(ttlMs));
    }

    private void resetMonthlyUsage(RateLimitConfig config) {
        config.setCurrentMonthUsage(0);
        YearMonth nextMonth = YearMonth.now(ZoneOffset.UTC).plusMonths(1);
        config.setResetAt(nextMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        rateLimitConfigRepository.save(config);

        // Clear Redis counter
        String quotaKey = buildQuotaKey(config.getTenantId());
        redisTemplate.delete(quotaKey);
    }

    private String buildRateLimitKey(Long apiKeyId, String suffix) {
        return RATE_LIMIT_KEY_PREFIX + apiKeyId + ":" + suffix;
    }

    private String buildQuotaKey(Long tenantId) {
        YearMonth ym = YearMonth.now(ZoneOffset.UTC);
        return QUOTA_KEY_PREFIX + tenantId + ":" + ym;
    }
}
