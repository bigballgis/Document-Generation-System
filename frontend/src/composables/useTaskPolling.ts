import { ref, watch, onBeforeUnmount, type Ref } from 'vue'
import { getTaskProgress, getTaskStatus } from '@/api/tasks'
import type { AsyncTaskDTO, TaskProgress } from '@/types/document'

export function useTaskPolling(taskId: Ref<string | null>, intervalMs = 3000) {
  const task = ref<AsyncTaskDTO | null>(null)
  const progress = ref<TaskProgress | null>(null)
  const isPolling = ref(false)
  const error = ref<string | null>(null)

  let timer: ReturnType<typeof setInterval> | null = null
  let consecutiveFailures = 0
  const MAX_FAILURES = 3

  async function poll() {
    const id = taskId.value
    if (!id) return

    try {
      const [taskData, progressData] = await Promise.all([
        getTaskStatus(id),
        getTaskProgress(id),
      ])
      task.value = taskData
      progress.value = progressData
      consecutiveFailures = 0

      if (taskData.status === 'COMPLETED' || taskData.status === 'FAILED') {
        stop()
      }
    } catch (e: any) {
      consecutiveFailures++
      if (consecutiveFailures >= MAX_FAILURES) {
        error.value = e?.message || 'Polling failed after multiple attempts'
        stop()
      }
    }
  }

  function start() {
    stop()
    if (!taskId.value) return

    error.value = null
    consecutiveFailures = 0
    isPolling.value = true
    poll()
    timer = setInterval(poll, intervalMs)
  }

  function stop() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    isPolling.value = false
  }

  watch(taskId, (newId) => {
    if (newId) {
      start()
    } else {
      stop()
      task.value = null
      progress.value = null
    }
  })

  onBeforeUnmount(() => {
    stop()
  })

  return { task, progress, isPolling, error, start, stop }
}
