import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockGet = vi.fn()

vi.mock('@/api/request', () => ({
  default: {
    get: (...args: any[]) => mockGet(...args),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

import { getTasks, getTaskStatus, getTaskProgress, downloadTaskResult } from '@/api/tasks'

describe('tasks API', () => {
  beforeEach(() => {
    mockGet.mockClear()
    mockGet.mockResolvedValue({})
  })

  describe('getTasks', () => {
    it('sends GET to /tasks with query params and zero-based page', async () => {
      await getTasks({ status: 'RUNNING', templateId: 5, page: 2, size: 20 })

      expect(mockGet).toHaveBeenCalledWith('/tasks', {
        params: {
          status: 'RUNNING',
          templateId: 5,
          page: 1, // zero-based: (2) - 1
          size: 20,
        },
      })
    })

    it('omits optional fields when empty', async () => {
      await getTasks({ page: 1, size: 10 })

      expect(mockGet).toHaveBeenCalledWith('/tasks', {
        params: {
          status: undefined,
          templateId: undefined,
          page: 0,
          size: 10,
        },
      })
    })
  })

  describe('getTaskStatus', () => {
    it('sends GET to /tasks/{taskId}', async () => {
      await getTaskStatus('abc-123')

      expect(mockGet).toHaveBeenCalledWith('/tasks/abc-123')
    })
  })

  describe('getTaskProgress', () => {
    it('sends GET to /tasks/{taskId}/progress', async () => {
      await getTaskProgress('xyz-456')

      expect(mockGet).toHaveBeenCalledWith('/tasks/xyz-456/progress')
    })
  })

  describe('downloadTaskResult', () => {
    it('sends GET to /tasks/{taskId}/download with blob responseType', async () => {
      await downloadTaskResult('task-789')

      expect(mockGet).toHaveBeenCalledWith('/tasks/task-789/download', { responseType: 'blob' })
    })
  })
})
