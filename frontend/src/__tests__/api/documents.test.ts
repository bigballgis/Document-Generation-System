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

import { getDocuments, getDocument, downloadDocument, mergeDocuments } from '@/api/documents'

describe('documents API', () => {
  beforeEach(() => {
    mockGet.mockClear()
    mockPost.mockClear()
    mockGet.mockResolvedValue({})
    mockPost.mockResolvedValue({})
  })

  describe('getDocuments', () => {
    it('sends GET to /documents with query params and zero-based page', async () => {
      await getDocuments({
        templateId: 10,
        status: 'GENERATED',
        startTime: '2024-01-01T00:00:00',
        endTime: '2024-12-31T23:59:59',
        page: 3,
        size: 20,
      })

      expect(mockGet).toHaveBeenCalledWith('/documents', {
        params: {
          templateId: 10,
          status: 'GENERATED',
          startTime: '2024-01-01T00:00:00',
          endTime: '2024-12-31T23:59:59',
          page: 2, // zero-based: (3) - 1
          size: 20,
        },
      })
    })

    it('omits optional fields when empty', async () => {
      await getDocuments({ page: 1, size: 10 })

      expect(mockGet).toHaveBeenCalledWith('/documents', {
        params: {
          templateId: undefined,
          status: undefined,
          startTime: undefined,
          endTime: undefined,
          page: 0,
          size: 10,
        },
      })
    })
  })

  describe('getDocument', () => {
    it('sends GET to /documents/{id}', async () => {
      await getDocument(42)

      expect(mockGet).toHaveBeenCalledWith('/documents/42')
    })
  })

  describe('downloadDocument', () => {
    it('sends GET to /documents/{id}/download with blob responseType', async () => {
      await downloadDocument(99)

      expect(mockGet).toHaveBeenCalledWith('/documents/99/download', { responseType: 'blob' })
    })
  })

  describe('mergeDocuments', () => {
    it('sends POST to /documents/merge with merge request data', async () => {
      const data = {
        documentIds: [1, 2, 3],
        insertPageBreaks: true,
        generateToc: false,
        outputFormat: 'DOCX',
      }
      await mergeDocuments(data)

      expect(mockPost).toHaveBeenCalledWith('/documents/merge', data)
    })
  })
})
