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
    mockGet.mockReset()
    mockPost.mockReset()
    mockGet.mockResolvedValue({})
    mockPost.mockResolvedValue({})
  })

  describe('submitReview', () => {
    it('sends POST to /templates/{templateId}/submit-review', async () => {
      await submitReview(42)

      expect(mockPost).toHaveBeenCalledWith('/templates/42/submit-review')
      expect(mockGet).not.toHaveBeenCalled()
    })

    it('interpolates different templateId values correctly', async () => {
      await submitReview(1)
      expect(mockPost).toHaveBeenCalledWith('/templates/1/submit-review')
    })

    it('returns the resolved value from the API call', async () => {
      const mockTemplate = { id: 42, name: 'Test', status: 'PENDING_REVIEW' }
      mockPost.mockResolvedValueOnce(mockTemplate)

      const result = await submitReview(42)
      expect(result).toEqual(mockTemplate)
    })
  })

  describe('getAvailableTransitions', () => {
    it('sends GET to /templates/{templateId}/available-transitions', async () => {
      await getAvailableTransitions(42)

      expect(mockGet).toHaveBeenCalledWith('/templates/42/available-transitions')
      expect(mockPost).not.toHaveBeenCalled()
    })

    it('interpolates different templateId values correctly', async () => {
      await getAvailableTransitions(99)
      expect(mockGet).toHaveBeenCalledWith('/templates/99/available-transitions')
    })

    it('returns the resolved transitions array', async () => {
      const transitions = ['ACTIVE', 'ARCHIVED']
      mockGet.mockResolvedValueOnce(transitions)

      const result = await getAvailableTransitions(42)
      expect(result).toEqual(transitions)
    })
  })

  describe('scanVariables', () => {
    it('sends POST to /templates/{templateId}/variables/scan', async () => {
      await scanVariables(42)

      expect(mockPost).toHaveBeenCalledWith('/templates/42/variables/scan')
      expect(mockGet).not.toHaveBeenCalled()
    })

    it('interpolates different templateId values correctly', async () => {
      await scanVariables(7)
      expect(mockPost).toHaveBeenCalledWith('/templates/7/variables/scan')
    })

    it('returns the resolved variables array', async () => {
      const variables = [{ id: 1, name: 'userName', type: 'STRING', bound: false }]
      mockPost.mockResolvedValueOnce(variables)

      const result = await scanVariables(42)
      expect(result).toEqual(variables)
    })
  })

  describe('exportCoverageReport', () => {
    it('sends GET with responseType blob', async () => {
      await exportCoverageReport(42)

      expect(mockGet).toHaveBeenCalledWith('/templates/42/coverage/export', { responseType: 'blob' })
      expect(mockPost).not.toHaveBeenCalled()
    })

    it('interpolates different templateId values correctly', async () => {
      await exportCoverageReport(15)
      expect(mockGet).toHaveBeenCalledWith('/templates/15/coverage/export', { responseType: 'blob' })
    })

    it('returns the blob response', async () => {
      const blob = new Blob(['report-data'])
      mockGet.mockResolvedValueOnce(blob)

      const result = await exportCoverageReport(42)
      expect(result).toBe(blob)
    })
  })
})
