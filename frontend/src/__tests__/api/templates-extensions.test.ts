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

import { submitReview, getAvailableTransitions, scanVariables, exportCoverageReport } from '@/api/templates'

describe('templates.ts extension functions', () => {
  beforeEach(() => {
    mockGet.mockClear()
    mockPost.mockClear()
    mockGet.mockResolvedValue({})
    mockPost.mockResolvedValue({})
  })

  describe('submitReview', () => {
    it('sends POST to /templates/{templateId}/submit-review', async () => {
      await submitReview(42)

      expect(mockPost).toHaveBeenCalledWith('/templates/42/submit-review')
    })
  })

  describe('getAvailableTransitions', () => {
    it('sends GET to /templates/{templateId}/available-transitions', async () => {
      await getAvailableTransitions(42)

      expect(mockGet).toHaveBeenCalledWith('/templates/42/available-transitions')
    })
  })

  describe('scanVariables', () => {
    it('sends POST to /templates/{templateId}/variables/scan', async () => {
      await scanVariables(42)

      expect(mockPost).toHaveBeenCalledWith('/templates/42/variables/scan')
    })
  })

  describe('exportCoverageReport', () => {
    it('sends GET to /templates/{templateId}/coverage/export with responseType blob', async () => {
      await exportCoverageReport(42)

      expect(mockGet).toHaveBeenCalledWith('/templates/42/coverage/export', { responseType: 'blob' })
    })
  })
})
