package com.docgen.property;

import com.docgen.dto.LockInfo;
import com.docgen.service.SegmentLockService;
import net.jqwik.api.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for SegmentLockService — Property 8: Edit Lock Mutual Exclusion.
 *
 * <p><b>Validates: Requirements 22.3</b></p>
 *
 * <p>Verifies that for any set of concurrent users attempting to acquire an edit lock
 * on the same Segment, at most one user succeeds. The lock holder can release and renew,
 * but other users cannot.</p>
 */
@Tag("Feature: template-segmentation, Property 8: editLockMutualExclusion")
class SegmentLockPropertyTest {

    /**
     * Property 8: editLockMutualExclusion — exactly one user acquires the lock.
     *
     * For any random set of 2-10 users all trying to acquire a lock on the same segment,
     * exactly one user should succeed and all others should fail (return null).
     * This simulates the Redis SET NX atomic behavior via mocks.
     */
    @Property(tries = 100)
    void exactlyOneUserShouldAcquireLock(
            @ForAll("userSets") List<Long> userIds,
            @ForAll("segmentIds") Long segmentId
    ) {
        Assume.that(userIds.size() >= 2);

        // Setup mocks simulating Redis atomic SET NX behavior
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // Simulate atomic SET NX: only the first call succeeds, rest fail
        AtomicReference<String> storedValue = new AtomicReference<>(null);
        String expectedKey = "segment-lock:" + segmentId;

        when(valueOps.setIfAbsent(eq(expectedKey), anyString(), any(Duration.class)))
                .thenAnswer(inv -> {
                    String value = inv.getArgument(1);
                    // Atomic compare-and-set: only first caller wins
                    return storedValue.compareAndSet(null, value);
                });

        // After first user acquires, GET returns the stored value
        when(valueOps.get(eq(expectedKey))).thenAnswer(inv -> storedValue.get());

        SegmentLockService service = new SegmentLockService(redisTemplate);

        // All users attempt to acquire the lock
        List<LockInfo> results = new ArrayList<>();
        Long winnerId = null;

        for (Long userId : userIds) {
            String username = "user_" + userId;
            LockInfo result = service.acquireLock(segmentId, userId, username);
            results.add(result);
            if (result != null && winnerId == null) {
                winnerId = userId;
            }
        }

        // Exactly one user should have succeeded
        long successCount = results.stream().filter(Objects::nonNull).count();
        assertEquals(1, successCount,
                "Exactly one user should acquire the lock, but " + successCount + " succeeded");

        // The winner's LockInfo should have correct segmentId and userId
        assertNotNull(winnerId, "There must be a lock winner");
        final Long winner = winnerId;
        LockInfo winnerLock = results.stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow();
        assertEquals(segmentId, winnerLock.getSegmentId());
        assertEquals(winner, winnerLock.getLockedBy());
    }

    /**
     * Property 8: editLockMutualExclusion — lock holder can release, others cannot.
     *
     * After one user acquires the lock, only that user should be able to release it.
     * Other users' release attempts should return false.
     */
    @Property(tries = 100)
    void onlyLockHolderCanRelease(
            @ForAll("userSets") List<Long> userIds,
            @ForAll("segmentIds") Long segmentId
    ) {
        Assume.that(userIds.size() >= 2);

        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // First user acquires the lock
        Long holderId = userIds.get(0);
        String holderUsername = "user_" + holderId;
        String expectedKey = "segment-lock:" + segmentId;
        AtomicReference<String> storedValue = new AtomicReference<>(null);

        when(valueOps.setIfAbsent(eq(expectedKey), anyString(), any(Duration.class)))
                .thenAnswer(inv -> {
                    String value = inv.getArgument(1);
                    return storedValue.compareAndSet(null, value);
                });

        when(valueOps.get(eq(expectedKey))).thenAnswer(inv -> storedValue.get());
        when(redisTemplate.delete(eq(expectedKey))).thenAnswer(inv -> {
            storedValue.set(null);
            return true;
        });

        SegmentLockService service = new SegmentLockService(redisTemplate);

        // Holder acquires the lock
        LockInfo acquired = service.acquireLock(segmentId, holderId, holderUsername);
        assertNotNull(acquired, "First user should acquire the lock");

        // Other users should fail to release
        for (int i = 1; i < userIds.size(); i++) {
            Long otherUserId = userIds.get(i);
            if (otherUserId.equals(holderId)) continue; // skip if same as holder
            boolean released = service.releaseLock(segmentId, otherUserId);
            assertFalse(released,
                    "User " + otherUserId + " should NOT be able to release lock held by " + holderId);
        }

        // Lock holder should be able to release
        boolean holderReleased = service.releaseLock(segmentId, holderId);
        assertTrue(holderReleased, "Lock holder should be able to release the lock");
    }

    /**
     * Property 8: editLockMutualExclusion — lock holder can renew, others cannot.
     *
     * After one user acquires the lock, only that user should be able to renew it.
     * Other users' renew attempts should return null.
     */
    @Property(tries = 100)
    void onlyLockHolderCanRenew(
            @ForAll("userSets") List<Long> userIds,
            @ForAll("segmentIds") Long segmentId
    ) {
        Assume.that(userIds.size() >= 2);

        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        Long holderId = userIds.get(0);
        String holderUsername = "user_" + holderId;
        String expectedKey = "segment-lock:" + segmentId;
        AtomicReference<String> storedValue = new AtomicReference<>(null);

        when(valueOps.setIfAbsent(eq(expectedKey), anyString(), any(Duration.class)))
                .thenAnswer(inv -> {
                    String value = inv.getArgument(1);
                    return storedValue.compareAndSet(null, value);
                });

        when(valueOps.get(eq(expectedKey))).thenAnswer(inv -> storedValue.get());
        when(redisTemplate.expire(eq(expectedKey), any(Duration.class))).thenReturn(true);

        SegmentLockService service = new SegmentLockService(redisTemplate);

        // Holder acquires the lock
        LockInfo acquired = service.acquireLock(segmentId, holderId, holderUsername);
        assertNotNull(acquired, "First user should acquire the lock");

        // Other users should fail to renew
        for (int i = 1; i < userIds.size(); i++) {
            Long otherUserId = userIds.get(i);
            if (otherUserId.equals(holderId)) continue;
            LockInfo renewed = service.renewLock(segmentId, otherUserId);
            assertNull(renewed,
                    "User " + otherUserId + " should NOT be able to renew lock held by " + holderId);
        }

        // Lock holder should be able to renew
        LockInfo holderRenewed = service.renewLock(segmentId, holderId);
        assertNotNull(holderRenewed, "Lock holder should be able to renew the lock");
        assertEquals(holderId, holderRenewed.getLockedBy());
    }

    // ── Generators ──

    @Provide
    Arbitrary<List<Long>> userSets() {
        // Generate 2-10 unique user IDs
        return Arbitraries.longs().between(1L, 1000L)
                .set().ofMinSize(2).ofMaxSize(10)
                .map(ArrayList::new);
    }

    @Provide
    Arbitrary<Long> segmentIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }
}
