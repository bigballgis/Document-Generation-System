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

import { getRateLimits, getUsageStats } from '@/api/rate-limits'

describe('rate-limits API', () => {
  beforeEach(() => {
    mockGet.mockClear()
    mockGet.mockResolvedValue({})
  })

  describe('getRateLimits', () => {
    it('sends GET to /rate-limits', async () => {
      await getRateLimits()

      expect(mockGet).toHaveBeenCalledWith('/rate-limits')
    })
  })

  describe('getUsageStats', () => {
    it('sends GET to /usage-stats', async () => {
      await getUsageStats()

      expect(mockGet).toHaveBeenCalledWith('/usage-stats')
    })
  })
})
