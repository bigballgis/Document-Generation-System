import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockGet = vi.fn()
const mockPost = vi.fn()
const mockPut = vi.fn()

vi.mock('@/api/request', () => ({
  default: {
    get: (...args: any[]) => mockGet(...args),
    post: (...args: any[]) => mockPost(...args),
    put: (...args: any[]) => mockPut(...args),
    delete: vi.fn(),
  },
}))

import { submitForReview, getTemplateReviews, conditionalApproveReview, getReviewEditorUrl } from '@/api/admin'

describe('admin.ts extension functions', () => {
  beforeEach(() => {
    mockGet.mockClear()
    mockPost.mockClear()
    mockPut.mockClear()
    mockGet.mockResolvedValue({})
    mockPost.mockResolvedValue({})
    mockPut.mockResolvedValue({})
  })

  describe('submitForReview', () => {
    it('sends POST to /templates/{templateId}/reviews with data', async () => {
      const data = { reviewerIds: [1, 2], reviewLevel: 1 }
      await submitForReview(42, data)

      expect(mockPost).toHaveBeenCalledWith('/templates/42/reviews', data)
    })
  })

  describe('getTemplateReviews', () => {
    it('sends GET to /templates/{templateId}/reviews with pagination params', async () => {
      await getTemplateReviews(42, { page: 1, size: 10 })

      expect(mockGet).toHaveBeenCalledWith('/templates/42/reviews', { params: { page: 1, size: 10 } })
    })
  })

  describe('conditionalApproveReview', () => {
    it('sends PUT to /reviews/{reviewId}/conditional-approve with data', async () => {
      const data = { comment: 'Looks good', suggestions: ['Fix typo'] }
      await conditionalApproveReview(5, data)

      expect(mockPut).toHaveBeenCalledWith('/reviews/5/conditional-approve', data)
    })
  })

  describe('getReviewEditorUrl', () => {
    it('sends GET to /templates/{templateId}/reviews/editor-url', async () => {
      await getReviewEditorUrl(42)

      expect(mockGet).toHaveBeenCalledWith('/templates/42/reviews/editor-url')
    })
  })
})
