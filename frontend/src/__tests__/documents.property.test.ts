// Feature: document-generation-frontend, Property 1: Document query parameter mapping preserves all filter fields
// **Validates: Requirements 3.2**

import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as fc from 'fast-check'

// Mock the request module before importing the API
const mockGet = vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 })

vi.mock('@/api/request', () => ({
  default: {
    get: (...args: any[]) => mockGet(...args),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

import { getDocuments } from '@/api/documents'
import type { DocumentQuery } from '@/types/document'

describe('Property 1: Document query parameter mapping preserves all filter fields', () => {
  beforeEach(() => {
    mockGet.mockClear()
    mockGet.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 })
  })

  it('should map all DocumentQuery fields to Axios params with page converted to zero-based', async () => {
    const arbDocumentQuery: fc.Arbitrary<DocumentQuery> = fc.record({
      templateId: fc.option(fc.integer({ min: 1, max: 10000 }), { nil: undefined }),
      status: fc.option(fc.constantFrom('GENERATED', 'EXPIRED', 'DELETED'), { nil: undefined }),
      startTime: fc.option(fc.constant('2024-01-01T00:00:00'), { nil: undefined }),
      endTime: fc.option(fc.constant('2024-12-31T23:59:59'), { nil: undefined }),
      page: fc.integer({ min: 1, max: 100 }),
      size: fc.constantFrom(10, 20, 50),
    })

    await fc.assert(
      fc.asyncProperty(arbDocumentQuery, async (query) => {
        mockGet.mockClear()
        mockGet.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, size: query.size, number: 0 })

        await getDocuments(query)

        expect(mockGet).toHaveBeenCalledTimes(1)
        const [url, config] = mockGet.mock.calls[0]
        expect(url).toBe('/documents')

        const params = config.params

        // page should be zero-based
        expect(params.page).toBe((query.page || 1) - 1)
        expect(params.size).toBe(query.size)

        // Optional fields: present when truthy, undefined when falsy
        if (query.templateId) {
          expect(params.templateId).toBe(query.templateId)
        } else {
          expect(params.templateId).toBeUndefined()
        }

        if (query.status) {
          expect(params.status).toBe(query.status)
        } else {
          expect(params.status).toBeUndefined()
        }

        if (query.startTime) {
          expect(params.startTime).toBe(query.startTime)
        } else {
          expect(params.startTime).toBeUndefined()
        }

        if (query.endTime) {
          expect(params.endTime).toBe(query.endTime)
        } else {
          expect(params.endTime).toBeUndefined()
        }
      }),
      { numRuns: 100 },
    )
  })
})
