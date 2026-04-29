import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockGet = vi.fn()
const mockPost = vi.fn()

vi.mock('@/api/request', () => ({
  default: {
    get: (...args: any[]) => mockGet(...args),
    post: (...args: any[]) => mockPost(...args),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

import { exportTestCases, importTestCases, getTestCases } from '@/api/templateTesting'

describe('templateTesting.ts API helpers', () => {
  beforeEach(() => {
    mockGet.mockClear()
    mockPost.mockClear()
    mockGet.mockResolvedValue('[]')
    mockPost.mockResolvedValue([])
  })

  describe('exportTestCases', () => {
    it('sends GET to /templates/{templateId}/test-cases/export', async () => {
      await exportTestCases(42)

      expect(mockGet).toHaveBeenCalledWith('/templates/42/test-cases/export')
    })
  })

  describe('importTestCases', () => {
    it('sends POST to /templates/{templateId}/test-cases/import with JSON body', async () => {
      const json = '[{"name":"test1"}]'
      await importTestCases(42, json)

      expect(mockPost).toHaveBeenCalledWith(
        '/templates/42/test-cases/import',
        json,
        { headers: { 'Content-Type': 'application/json' } },
      )
    })
  })

  describe('getTestCases', () => {
    it('sends GET with pagination and search params', async () => {
      mockGet.mockResolvedValue({
        content: [], totalElements: 0, totalPages: 0, size: 10, number: 1,
      })
      await getTestCases(7, { page: 1, size: 10, q: '  smoke  ' })

      expect(mockGet).toHaveBeenCalledWith('/templates/7/test-cases', {
        params: { page: 1, size: 10, q: 'smoke' },
      })
    })
  })
})
