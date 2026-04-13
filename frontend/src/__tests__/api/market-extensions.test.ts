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

import { exportTestCases, importTestCases } from '@/api/market'

describe('market.ts extension functions', () => {
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
})
