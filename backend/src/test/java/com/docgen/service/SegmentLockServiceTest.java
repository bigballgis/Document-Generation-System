package com.docgen.service;

import com.docgen.dto.LockInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SegmentLockServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SegmentLockService service;

    @BeforeEach
    void setUp() {
        service = new SegmentLockService(redisTemplate);
    }

    // ── acquireLock ──

    @Test
    void acquireLock_success_returnsLockInfo() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("segment-lock:1"), anyString(), eq(Duration.ofMinutes(30))))
                .thenReturn(true);

        LockInfo result = service.acquireLock(1L, 100L, "alice");

        assertNotNull(result);
        assertEquals(1L, result.getSegmentId());
        assertEquals(100L, result.getLockedBy());
        assertEquals("alice", result.getLockedByUsername());
        assertNotNull(result.getLockedAt());
        assertNotNull(result.getExpiresAt());
    }

    @Test
    void acquireLock_alreadyLockedByAnotherUser_returnsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("segment-lock:1"), anyString(), eq(Duration.ofMinutes(30))))
                .thenReturn(false);
        when(valueOperations.get("segment-lock:1")).thenReturn("200:bob:1700000000000");

        LockInfo result = service.acquireLock(1L, 100L, "alice");

        assertNull(result);
    }

    @Test
    void acquireLock_reentrant_sameUser_renewsAndReturns() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("segment-lock:1"), anyString(), eq(Duration.ofMinutes(30))))
                .thenReturn(false);
        when(valueOperations.get("segment-lock:1")).thenReturn("100:alice:1700000000000");
        when(redisTemplate.expire("segment-lock:1", Duration.ofMinutes(30))).thenReturn(true);

        LockInfo result = service.acquireLock(1L, 100L, "alice");

        assertNotNull(result);
        assertEquals(100L, result.getLockedBy());
        verify(redisTemplate).expire("segment-lock:1", Duration.ofMinutes(30));
    }

    // ── releaseLock ──

    @Test
    void releaseLock_byHolder_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("segment-lock:1")).thenReturn("100:alice:1700000000000");
        when(redisTemplate.delete("segment-lock:1")).thenReturn(true);

        boolean result = service.releaseLock(1L, 100L);

        assertTrue(result);
        verify(redisTemplate).delete("segment-lock:1");
    }

    @Test
    void releaseLock_byNonHolder_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("segment-lock:1")).thenReturn("200:bob:1700000000000");

        boolean result = service.releaseLock(1L, 100L);

        assertFalse(result);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void releaseLock_noLockExists_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("segment-lock:1")).thenReturn(null);

        boolean result = service.releaseLock(1L, 100L);

        assertFalse(result);
    }

    // ── renewLock ──

    @Test
    void renewLock_byHolder_returnsLockInfo() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("segment-lock:1")).thenReturn("100:alice:1700000000000");
        when(redisTemplate.expire("segment-lock:1", Duration.ofMinutes(30))).thenReturn(true);

        LockInfo result = service.renewLock(1L, 100L);

        assertNotNull(result);
        assertEquals(1L, result.getSegmentId());
        assertEquals(100L, result.getLockedBy());
        assertEquals("alice", result.getLockedByUsername());
        verify(redisTemplate).expire("segment-lock:1", Duration.ofMinutes(30));
    }

    @Test
    void renewLock_byNonHolder_returnsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("segment-lock:1")).thenReturn("200:bob:1700000000000");

        LockInfo result = service.renewLock(1L, 100L);

        assertNull(result);
    }

    @Test
    void renewLock_noLockExists_returnsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("segment-lock:1")).thenReturn(null);

        LockInfo result = service.renewLock(1L, 100L);

        assertNull(result);
    }

    // ── getLockInfo ──

    @Test
    void getLockInfo_locked_returnsLockInfo() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("segment-lock:5")).thenReturn("100:alice:1700000000000");

        LockInfo result = service.getLockInfo(5L);

        assertNotNull(result);
        assertEquals(5L, result.getSegmentId());
        assertEquals(100L, result.getLockedBy());
        assertEquals("alice", result.getLockedByUsername());
    }

    @Test
    void getLockInfo_notLocked_returnsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("segment-lock:5")).thenReturn(null);

        LockInfo result = service.getLockInfo(5L);

        assertNull(result);
    }

    @Test
    void getLockInfo_invalidValue_returnsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("segment-lock:5")).thenReturn("invalid");

        LockInfo result = service.getLockInfo(5L);

        assertNull(result);
    }

    // ── getBatchLockInfo ──

    @Test
    void getBatchLockInfo_mixedResults() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        List<Long> ids = List.of(1L, 2L, 3L);
        List<String> keys = List.of("segment-lock:1", "segment-lock:2", "segment-lock:3");
        when(valueOperations.multiGet(keys))
                .thenReturn(List.of("100:alice:1700000000000", "", "200:bob:1700000000000"));

        // Note: empty string "" will fail to parse and be excluded
        Map<Long, LockInfo> result = service.getBatchLockInfo(ids);

        // segment 1 and 3 should be present, segment 2 (empty string) should be excluded
        assertEquals(2, result.size());
        assertTrue(result.containsKey(1L));
        assertTrue(result.containsKey(3L));
        assertEquals("alice", result.get(1L).getLockedByUsername());
        assertEquals("bob", result.get(3L).getLockedByUsername());
    }

    @Test
    void getBatchLockInfo_emptyList_returnsEmptyMap() {
        Map<Long, LockInfo> result = service.getBatchLockInfo(List.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void getBatchLockInfo_nullList_returnsEmptyMap() {
        Map<Long, LockInfo> result = service.getBatchLockInfo(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void getBatchLockInfo_multiGetReturnsNull_returnsEmptyMap() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList())).thenReturn(null);

        Map<Long, LockInfo> result = service.getBatchLockInfo(List.of(1L, 2L));

        assertTrue(result.isEmpty());
    }
}
