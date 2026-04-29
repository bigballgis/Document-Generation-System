import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockPost = vi.fn()

vi.mock('@/api/request', () => ({
  default: {
    get: vi.fn(),
    post: (...args: any[]) => mockPost(...args),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

import {
  generateDocument,
  generateDocumentAsync,
  generateDocumentBatch,
  generateDocumentWord,
  generateDocumentPdf,
  generateDocumentAsyncWord,
  generateDocumentAsyncPdf,
} from '@/api/generate'

describe('generate API', () => {
  beforeEach(() => {
    mockPost.mockClear()
    mockPost.mockResolvedValue({})
  })

  describe('generateDocument', () => {
    it('sends POST to /generate/{templateId} with data', async () => {
      const data = { parameters: { key: 'value' }, outputFormat: 'PDF' }
      await generateDocument(42, data)

      expect(mockPost).toHaveBeenCalledWith('/generate/42', data, { params: undefined })
    })

    it('sends POST with version query param when provided', async () => {
      await generateDocument(42, undefined, 3)

      expect(mockPost).toHaveBeenCalledWith('/generate/42', {}, { params: { version: 3 } })
    })

    it('sends POST with empty object when data is undefined', async () => {
      await generateDocument(10)

      expect(mockPost).toHaveBeenCalledWith('/generate/10', {}, { params: undefined })
    })
  })

  describe('generateDocumentWord', () => {
    it('sends POST to /generate/{templateId}/word', async () => {
      await generateDocumentWord(42, { parameters: { x: 1 } })

      expect(mockPost).toHaveBeenCalledWith('/generate/42/word', { parameters: { x: 1 } }, { params: undefined })
    })

    it('supports version query param', async () => {
      await generateDocumentWord(5, {}, 2)

      expect(mockPost).toHaveBeenCalledWith('/generate/5/word', {}, { params: { version: 2 } })
    })
  })

  describe('generateDocumentPdf', () => {
    it('sends POST to /generate/{templateId}/pdf', async () => {
      await generateDocumentPdf(42)

      expect(mockPost).toHaveBeenCalledWith('/generate/42/pdf', {}, { params: undefined })
    })
  })

  describe('generateDocumentAsyncWord', () => {
    it('sends POST to /generate/{templateId}/async/word', async () => {
      await generateDocumentAsyncWord(9, { parameters: { x: 1 } })

      expect(mockPost).toHaveBeenCalledWith('/generate/9/async/word', { parameters: { x: 1 } })
    })
  })

  describe('generateDocumentAsyncPdf', () => {
    it('sends POST to /generate/{templateId}/async/pdf', async () => {
      await generateDocumentAsyncPdf(9)

      expect(mockPost).toHaveBeenCalledWith('/generate/9/async/pdf', {})
    })
  })

  describe('generateDocumentAsync', () => {
    it('sends POST to /generate/{templateId}/async', async () => {
      const data = { parameters: { x: 1 } }
      await generateDocumentAsync(5, data)

      expect(mockPost).toHaveBeenCalledWith('/generate/5/async', data)
    })

    it('sends POST with empty object when data is undefined', async () => {
      await generateDocumentAsync(5)

      expect(mockPost).toHaveBeenCalledWith('/generate/5/async', {})
    })
  })

  describe('generateDocumentBatch', () => {
    it('sends POST to /generate/{templateId}/batch with batch data', async () => {
      const data = {
        dataSets: [{ a: 1 }, { b: 2 }],
        outputFormat: 'WORD',
        failureStrategy: 'CONTINUE',
      }
      await generateDocumentBatch(7, data)

      expect(mockPost).toHaveBeenCalledWith('/generate/7/batch', data)
    })
  })
})
