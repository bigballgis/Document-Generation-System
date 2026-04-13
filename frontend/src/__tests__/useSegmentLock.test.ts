import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useSegmentLock } from '@/composables/useSegmentLock'

// Mock the segment API
const mockAcquire = vi.fn()
const mockRelease = vi.fn()
const mockRenew = vi.fn()
const mockGetLockInfo = vi.fn()

vi.mock('@/api/segments', () => ({
  acquireSegmentLock: (...args: any[]) => mockAcquire(...args),
  releaseSegmentLock: (...args: any[]) => mockRelease(...args),
  renewSegmentLock: (...args: any[]) => mockRenew(...args),
  getSegmentLockInfo: (...args: any[]) => mockGetLockInfo(...args),
}))

// Mock Vue lifecycle hooks since we're testing outside a component
vi.mock('vue', async () => {
  const actual = await vi.importActual('vue')
  return {
    ...(actual as any),
    onBeforeUnmount: vi.fn(),
  }
})

describe('useSegmentLock', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mockAcquire.mockReset()
    mockRelease.mockReset()
    mockRenew.mockReset()
    mockGetLockInfo.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('acquires lock successfully', async () => {
    const lockInfo = { segmentId: 1, lockedBy: 10, lockedByUsername: 'alice', lockedAt: '2024-01-01', expiresAt: '2024-01-01' }
    mockGetLockInfo.mockResolvedValue(null)
    mockAcquire.mockResolvedValue(lockInfo)

    const { acquire, locked, lockInfo: info } = useSegmentLock({ heartbeatInterval: 60000 })
    const result = await acquire(1)

    expect(result).toBe(true)
    expect(locked.value).toBe(true)
    expect(info.value).toEqual(lockInfo)
    expect(mockAcquire).toHaveBeenCalledWith(1)
  })

  it('returns false when acquire fails', async () => {
    mockGetLockInfo.mockResolvedValue(null)
    mockAcquire.mockRejectedValue(new Error('Lock held'))

    const { acquire, locked, lockError } = useSegmentLock()
    const result = await acquire(1)

    expect(result).toBe(false)
    expect(locked.value).toBe(false)
    expect(lockError.value).toBe('acquire_failed')
  })

  it('detects other user lock before acquiring', async () => {
    const otherLock = { segmentId: 1, lockedBy: 20, lockedByUsername: 'bob', lockedAt: '2024-01-01', expiresAt: '2024-01-01' }
    mockGetLockInfo.mockResolvedValue(otherLock)
    mockAcquire.mockRejectedValue(new Error('Lock held'))

    const { acquire, otherUserLock } = useSegmentLock()
    await acquire(1)

    expect(otherUserLock.value).toEqual(otherLock)
  })

  it('sends heartbeat renewals at the configured interval', async () => {
    const lockInfo = { segmentId: 1, lockedBy: 10, lockedByUsername: 'alice', lockedAt: '2024-01-01', expiresAt: '2024-01-01' }
    mockGetLockInfo.mockResolvedValue(null)
    mockAcquire.mockResolvedValue(lockInfo)
    mockRenew.mockResolvedValue(lockInfo)

    const { acquire, stopHeartbeat } = useSegmentLock({ heartbeatInterval: 1000 })
    await acquire(1)

    expect(mockRenew).not.toHaveBeenCalled()

    // Advance timer by 1 heartbeat interval
    await vi.advanceTimersByTimeAsync(1000)
    expect(mockRenew).toHaveBeenCalledTimes(1)
    expect(mockRenew).toHaveBeenCalledWith(1)

    // Advance again
    await vi.advanceTimersByTimeAsync(1000)
    expect(mockRenew).toHaveBeenCalledTimes(2)

    stopHeartbeat()
  })

  it('sets lockError when heartbeat renewal fails', async () => {
    const lockInfo = { segmentId: 1, lockedBy: 10, lockedByUsername: 'alice', lockedAt: '2024-01-01', expiresAt: '2024-01-01' }
    mockGetLockInfo.mockResolvedValue(null)
    mockAcquire.mockResolvedValue(lockInfo)
    mockRenew.mockRejectedValue(new Error('Expired'))

    const { acquire, locked, lockError } = useSegmentLock({ heartbeatInterval: 1000 })
    await acquire(1)

    expect(locked.value).toBe(true)

    await vi.advanceTimersByTimeAsync(1000)

    expect(locked.value).toBe(false)
    expect(lockError.value).toBe('lock_expired')
  })

  it('releases lock and stops heartbeat', async () => {
    const lockInfo = { segmentId: 1, lockedBy: 10, lockedByUsername: 'alice', lockedAt: '2024-01-01', expiresAt: '2024-01-01' }
    mockGetLockInfo.mockResolvedValue(null)
    mockAcquire.mockResolvedValue(lockInfo)
    mockRelease.mockResolvedValue(undefined)
    mockRenew.mockResolvedValue(lockInfo)

    const { acquire, release, locked } = useSegmentLock({ heartbeatInterval: 1000 })
    await acquire(1)
    expect(locked.value).toBe(true)

    await release()
    expect(locked.value).toBe(false)
    expect(mockRelease).toHaveBeenCalledWith(1)

    // Heartbeat should be stopped — no more renew calls
    mockRenew.mockClear()
    await vi.advanceTimersByTimeAsync(2000)
    expect(mockRenew).not.toHaveBeenCalled()
  })

  it('checkLock returns lock info for a segment', async () => {
    const otherLock = { segmentId: 5, lockedBy: 20, lockedByUsername: 'bob', lockedAt: '2024-01-01', expiresAt: '2024-01-01' }
    mockGetLockInfo.mockResolvedValue(otherLock)

    const { checkLock, otherUserLock } = useSegmentLock()
    const result = await checkLock(5)

    expect(result).toEqual(otherLock)
    expect(otherUserLock.value).toEqual(otherLock)
  })
})
