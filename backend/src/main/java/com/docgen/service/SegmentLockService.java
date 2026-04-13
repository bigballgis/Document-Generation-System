package com.docgen.service;

import com.docgen.dto.LockInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing distributed edit locks on Segments using Redis.
 * <p>
 * Key format: {@code segment-lock:{segmentId}}
 * Value format: {@code {userId}:{username}:{lockedAtEpochMilli}}
 * TTL: 30 minutes (auto-expires if not renewed)
 * <p>
 * Validates: Requirements 22.1, 22.2, 22.3, 22.4, 22.5
 */
@Service
public class SegmentLockService {

    private static final Logger log = LoggerFactory.getLogger(SegmentLockService.class);

    private static final String LOCK_KEY_PREFIX = "segment-lock:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final RedisTemplate<String, String> redisTemplate;

    public SegmentLockService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Attempt to acquire an edit lock on a segment.
     * Uses Redis SET NX (set-if-not-exists) for atomic lock acquisition.
     * If the segment is already locked by the same user, the lock TTL is renewed.
     *
     * @param segmentId the segment to lock
     * @param userId    the user requesting the lock
     * @param username  the username for display purposes
     * @return LockInfo if lock acquired successfully, null if locked by another user
     */
    public LockInfo acquireLock(Long segmentId, Long userId, String username) {
        String key = buildKey(segmentId);
        Instant now = Instant.now();
        String value = encodeValue(userId, username, now);

        // Try atomic set-if-not-exists
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, value, LOCK_TTL);

        if (Boolean.TRUE.equals(acquired)) {
            log.info("Lock acquired on segment={} by user={} ({})", segmentId, userId, username);
            return buildLockInfo(segmentId, userId, username, now);
        }

        // Key already exists — check if same user holds the lock (re-entrant)
        String existing = redisTemplate.opsForValue().get(key);
        if (existing != null) {
            LockInfo existingLock = decodeValue(segmentId, existing);
            if (existingLock != null && existingLock.getLockedBy().equals(userId)) {
                // Same user — renew TTL
                redisTemplate.expire(key, LOCK_TTL);
                log.debug("Lock renewed (re-entrant) on segment={} by user={}", segmentId, userId);
                return buildLockInfo(segmentId, userId, username, existingLock.getLockedAt());
            }
        }

        log.debug("Lock acquisition failed on segment={} for user={}, already locked", segmentId, userId);
        return null;
    }

    /**
     * Release the edit lock on a segment. Only the lock holder can release.
     *
     * @param segmentId the segment to unlock
     * @param userId    the user requesting the release
     * @return true if lock was released, false if user is not the lock holder or no lock exists
     */
    public boolean releaseLock(Long segmentId, Long userId) {
        String key = buildKey(segmentId);
        String existing = redisTemplate.opsForValue().get(key);

        if (existing == null) {
            log.debug("No lock to release on segment={}", segmentId);
            return false;
        }

        LockInfo lockInfo = decodeValue(segmentId, existing);
        if (lockInfo == null || !lockInfo.getLockedBy().equals(userId)) {
            log.warn("User={} attempted to release lock on segment={} held by another user", userId, segmentId);
            return false;
        }

        Boolean deleted = redisTemplate.delete(key);
        log.info("Lock released on segment={} by user={}", segmentId, userId);
        return Boolean.TRUE.equals(deleted);
    }

    /**
     * Renew (extend) the TTL of an existing lock. Only the lock holder can renew.
     *
     * @param segmentId the segment whose lock to renew
     * @param userId    the user requesting the renewal
     * @return LockInfo with updated expiry if renewed, null if user is not the lock holder
     */
    public LockInfo renewLock(Long segmentId, Long userId) {
        String key = buildKey(segmentId);
        String existing = redisTemplate.opsForValue().get(key);

        if (existing == null) {
            log.debug("No lock to renew on segment={}", segmentId);
            return null;
        }

        LockInfo lockInfo = decodeValue(segmentId, existing);
        if (lockInfo == null || !lockInfo.getLockedBy().equals(userId)) {
            log.debug("User={} cannot renew lock on segment={}, not the holder", userId, segmentId);
            return null;
        }

        redisTemplate.expire(key, LOCK_TTL);
        log.debug("Lock renewed on segment={} by user={}", segmentId, userId);
        return buildLockInfo(segmentId, lockInfo.getLockedBy(), lockInfo.getLockedByUsername(), lockInfo.getLockedAt());
    }

    /**
     * Get the current lock info for a segment.
     *
     * @param segmentId the segment to query
     * @return LockInfo if locked, null if not locked
     */
    public LockInfo getLockInfo(Long segmentId) {
        String key = buildKey(segmentId);
        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            return null;
        }

        return decodeValue(segmentId, value);
    }

    /**
     * Batch query lock status for multiple segments.
     *
     * @param segmentIds list of segment IDs to query
     * @return map of segmentId to LockInfo (only contains entries for locked segments)
     */
    public Map<Long, LockInfo> getBatchLockInfo(List<Long> segmentIds) {
        Map<Long, LockInfo> result = new HashMap<>();

        if (segmentIds == null || segmentIds.isEmpty()) {
            return result;
        }

        List<String> keys = segmentIds.stream()
                .map(this::buildKey)
                .toList();

        List<String> values = redisTemplate.opsForValue().multiGet(keys);

        if (values == null) {
            return result;
        }

        for (int i = 0; i < segmentIds.size(); i++) {
            String value = values.get(i);
            if (value != null) {
                Long segmentId = segmentIds.get(i);
                LockInfo lockInfo = decodeValue(segmentId, value);
                if (lockInfo != null) {
                    result.put(segmentId, lockInfo);
                }
            }
        }

        return result;
    }

    private String buildKey(Long segmentId) {
        return LOCK_KEY_PREFIX + segmentId;
    }

    /**
     * Encode lock value as {@code userId:username:lockedAtEpochMilli}.
     */
    private String encodeValue(Long userId, String username, Instant lockedAt) {
        return userId + ":" + username + ":" + lockedAt.toEpochMilli();
    }

    /**
     * Decode lock value from {@code userId:username:lockedAtEpochMilli} format.
     */
    private LockInfo decodeValue(Long segmentId, String value) {
        // Format: userId:username:lockedAtEpochMilli
        // Username may contain colons, so split from the ends
        int firstColon = value.indexOf(':');
        int lastColon = value.lastIndexOf(':');

        if (firstColon == -1 || lastColon == -1 || firstColon == lastColon) {
            log.warn("Invalid lock value format for segment={}: {}", segmentId, value);
            return null;
        }

        try {
            Long userId = Long.parseLong(value.substring(0, firstColon));
            String username = value.substring(firstColon + 1, lastColon);
            long epochMilli = Long.parseLong(value.substring(lastColon + 1));
            Instant lockedAt = Instant.ofEpochMilli(epochMilli);

            return buildLockInfo(segmentId, userId, username, lockedAt);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse lock value for segment={}: {}", segmentId, value, e);
            return null;
        }
    }

    private LockInfo buildLockInfo(Long segmentId, Long userId, String username, Instant lockedAt) {
        Instant expiresAt = lockedAt.plus(LOCK_TTL);
        return new LockInfo(segmentId, userId, username, lockedAt, expiresAt);
    }
}
