import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref, nextTick } from 'vue'
import { useTaskPolling } from '@/composables/useTaskPolling'

// Mock the tasks API
const mockGetTaskStatus = vi.fn()
const mockGetTaskProgress = vi.fn()

vi.mock('@/api/tasks', () => ({
  getTaskStatus: (...args: any[]) => mockGetTaskStatus(...args),
  getTaskProgress: (...args: any[]) => mockGetTaskProgress(...args),
  downloadTaskResult: vi.fn(),
  getTasks: vi.fn(),
}))

// Mock Vue lifecycle hooks since we're testing outside a component
vi.mock('vue', async () => {
  const actual = await vi.importActual('vue')
  return {
    ...(actual as any),
    onBeforeUnmount: vi.fn(),
  }
})

const makeTask = (status: string) => ({
  taskId: 'task-1',
  taskType: 'GENERATE',
  templateId: 1,
  status,
  progress: 50,
  totalCount: 10,
  completedCount: 5,
  successCount: 5,
  failCount: 0,
  documentId: null,
  errorMessage: null,
  downloadUrl: null,
  createdAt: '2024-01-01T00:00:00',
  completedAt: null,
})

const makeProgress = () => ({
  taskId: 'task-1',
  status: 'RUNNING',
  progress: 50,
  totalCount: 10,
  completedCount: 5,
  successCount: 5,
  failCount: 0,
})

describe('useTaskPolling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mockGetTaskStatus.mockReset()
    mockGetTaskProgress.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  // Helper: create composable and trigger the watcher by changing ref from null → id
  async function setupPolling(intervalMs = 1000) {
    const taskId = ref<string | null>(null)
    const result = useTaskPolling(taskId, intervalMs)

    // Trigger the watcher by setting the value
    taskId.value = 'task-1'
    await nextTick()
    // Let the initial poll() promise resolve
    await vi.advanceTimersByTimeAsync(0)

    return { taskId, ...result }
  }

  it('starts polling when taskId is set', async () => {
    mockGetTaskStatus.mockResolvedValue(makeTask('RUNNING'))
    mockGetTaskProgress.mockResolvedValue(makeProgress())

    const { task, progress, isPolling } = await setupPolling()

    expect(mockGetTaskStatus).toHaveBeenCalledWith('task-1')
    expect(mockGetTaskProgress).toHaveBeenCalledWith('task-1')
    expect(task.value).toBeTruthy()
    expect(progress.value).toBeTruthy()
    expect(isPolling.value).toBe(true)
  })

  it('stops polling when taskId becomes null', async () => {
    mockGetTaskStatus.mockResolvedValue(makeTask('RUNNING'))
    mockGetTaskProgress.mockResolvedValue(makeProgress())

    const { taskId, task, progress, isPolling } = await setupPolling()
    expect(isPolling.value).toBe(true)

    taskId.value = null
    await nextTick()
    await vi.advanceTimersByTimeAsync(0)

    expect(isPolling.value).toBe(false)
    expect(task.value).toBeNull()
    expect(progress.value).toBeNull()
  })

  it('auto-stops when task status becomes COMPLETED', async () => {
    mockGetTaskStatus.mockResolvedValue(makeTask('COMPLETED'))
    mockGetTaskProgress.mockResolvedValue(makeProgress())

    const { isPolling } = await setupPolling()

    expect(isPolling.value).toBe(false)
  })

  it('auto-stops when task status becomes FAILED', async () => {
    mockGetTaskStatus.mockResolvedValue(makeTask('FAILED'))
    mockGetTaskProgress.mockResolvedValue(makeProgress())

    const { isPolling } = await setupPolling()

    expect(isPolling.value).toBe(false)
  })

  it('stops and sets error after 3 consecutive failures', async () => {
    mockGetTaskStatus.mockRejectedValue(new Error('Network error'))
    mockGetTaskProgress.mockRejectedValue(new Error('Network error'))

    const { isPolling, error } = await setupPolling()

    // After initial poll — failure 1
    expect(isPolling.value).toBe(true) // still polling after 1 failure

    // Second poll — failure 2
    await vi.advanceTimersByTimeAsync(1000)
    expect(isPolling.value).toBe(true) // still polling after 2 failures

    // Third poll — failure 3 → should stop
    await vi.advanceTimersByTimeAsync(1000)
    expect(isPolling.value).toBe(false)
    expect(error.value).toBeTruthy()
  })

  it('resets consecutive failures on successful poll', async () => {
    // First call fails (initial poll), second fails, third succeeds
    mockGetTaskStatus
      .mockRejectedValueOnce(new Error('fail'))
      .mockRejectedValueOnce(new Error('fail'))
      .mockResolvedValueOnce(makeTask('RUNNING'))
      .mockRejectedValueOnce(new Error('fail'))
      .mockRejectedValueOnce(new Error('fail'))
      .mockResolvedValue(makeTask('RUNNING'))

    mockGetTaskProgress
      .mockRejectedValueOnce(new Error('fail'))
      .mockRejectedValueOnce(new Error('fail'))
      .mockResolvedValueOnce(makeProgress())
      .mockRejectedValueOnce(new Error('fail'))
      .mockRejectedValueOnce(new Error('fail'))
      .mockResolvedValue(makeProgress())

    const { isPolling, error } = await setupPolling()

    // After initial poll — failure 1
    expect(isPolling.value).toBe(true)

    // Failure 2
    await vi.advanceTimersByTimeAsync(1000)
    expect(isPolling.value).toBe(true)

    // Success — resets counter
    await vi.advanceTimersByTimeAsync(1000)
    expect(isPolling.value).toBe(true)
    expect(error.value).toBeNull()

    // Failure 1 again
    await vi.advanceTimersByTimeAsync(1000)
    expect(isPolling.value).toBe(true)

    // Failure 2 again — still under threshold
    await vi.advanceTimersByTimeAsync(1000)
    expect(isPolling.value).toBe(true)
  })

  it('can be manually stopped and started', async () => {
    mockGetTaskStatus.mockResolvedValue(makeTask('RUNNING'))
    mockGetTaskProgress.mockResolvedValue(makeProgress())

    const { isPolling, stop, start } = await setupPolling()
    expect(isPolling.value).toBe(true)

    stop()
    expect(isPolling.value).toBe(false)

    // No more polls after stop
    mockGetTaskStatus.mockClear()
    await vi.advanceTimersByTimeAsync(2000)
    expect(mockGetTaskStatus).not.toHaveBeenCalled()

    // Restart
    start()
    await vi.advanceTimersByTimeAsync(0)
    expect(isPolling.value).toBe(true)
    expect(mockGetTaskStatus).toHaveBeenCalled()
  })

  it('polls at the configured interval', async () => {
    mockGetTaskStatus.mockResolvedValue(makeTask('RUNNING'))
    mockGetTaskProgress.mockResolvedValue(makeProgress())

    await setupPolling(2000)

    // Initial poll already happened in setupPolling
    expect(mockGetTaskStatus).toHaveBeenCalledTimes(1)

    // After 1 second — no new poll yet
    await vi.advanceTimersByTimeAsync(1000)
    expect(mockGetTaskStatus).toHaveBeenCalledTimes(1)

    // After 2 seconds total — second poll
    await vi.advanceTimersByTimeAsync(1000)
    expect(mockGetTaskStatus).toHaveBeenCalledTimes(2)
  })
})
