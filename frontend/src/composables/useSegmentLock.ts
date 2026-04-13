import { ref, onBeforeUnmount } from 'vue'
import {
  acquireSegmentLock,
  releaseSegmentLock,
  renewSegmentLock,
  getSegmentLockInfo,
} from '@/api/segments'
import type { LockInfo } from '@/types/segment'

export interface UseSegmentLockOptions {
  /** Heartbeat interval in ms (default: 5 minutes) */
  heartbeatInterval?: number
}

const DEFAULT_HEARTBEAT_INTERVAL = 5 * 60 * 1000

export function useSegmentLock(options: UseSegmentLockOptions = {}) {
  const { heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL } = options

  const locked = ref(false)
  const lockInfo = ref<LockInfo | null>(null)
  const otherUserLock = ref<LockInfo | null>(null)
  const lockError = ref('')

  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let currentSegmentId: number | null = null

  function startHeartbeat(segmentId: number) {
    stopHeartbeat()
    heartbeatTimer = setInterval(async () => {
      try {
        const info = await renewSegmentLock(segmentId)
        lockInfo.value = info
      } catch {
        locked.value = false
        lockInfo.value = null
        lockError.value = 'lock_expired'
        stopHeartbeat()
      }
    }, heartbeatInterval)
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  async function acquire(segmentId: number): Promise<boolean> {
    lockError.value = ''
    otherUserLock.value = null
    try {
      // Check if someone else holds the lock
      const existing = await getSegmentLockInfo(segmentId)
      if (existing) {
        otherUserLock.value = existing
        // Still try to acquire — server decides
      }
      const info = await acquireSegmentLock(segmentId)
      locked.value = true
      lockInfo.value = info
      currentSegmentId = segmentId
      startHeartbeat(segmentId)
      return true
    } catch {
      locked.value = false
      lockError.value = 'acquire_failed'
      return false
    }
  }

  async function release(): Promise<void> {
    stopHeartbeat()
    if (currentSegmentId != null) {
      try {
        await releaseSegmentLock(currentSegmentId)
      } catch {
        // best-effort release
      }
    }
    locked.value = false
    lockInfo.value = null
    currentSegmentId = null
  }

  async function checkLock(segmentId: number): Promise<LockInfo | null> {
    try {
      const info = await getSegmentLockInfo(segmentId)
      otherUserLock.value = info
      return info
    } catch {
      return null
    }
  }

  onBeforeUnmount(() => {
    release()
  })

  return {
    locked,
    lockInfo,
    otherUserLock,
    lockError,
    acquire,
    release,
    checkLock,
    /** Exposed for testing */
    startHeartbeat,
    stopHeartbeat,
  }
}
