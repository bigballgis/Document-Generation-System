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

import { importDocx, exportDocx, exportConfig, importConfig } from '@/api/import-export'

describe('import-export API', () => {
  beforeEach(() => {
    mockGet.mockClear()
    mockPost.mockClear()
    mockGet.mockResolvedValue(new Blob())
    mockPost.mockResolvedValue({})
  })

  describe('importDocx', () => {
    it('sends multipart POST to /templates/import', async () => {
      const file = new File(['content'], 'test.docx', { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })
      await importDocx(file)

      expect(mockPost).toHaveBeenCalledTimes(1)
      const [url, body, config] = mockPost.mock.calls[0]
      expect(url).toBe('/templates/import')
      expect(body).toBeInstanceOf(FormData)
      expect(body.get('file')).toBe(file)
      expect(config).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } })
    })
  })

  describe('exportDocx', () => {
    it('sends GET to /templates/{id}/export with responseType blob', async () => {
      await exportDocx(42)

      expect(mockGet).toHaveBeenCalledWith('/templates/42/export', { responseType: 'blob' })
    })
  })

  describe('exportConfig', () => {
    it('sends GET to /templates/{id}/export-config with responseType blob', async () => {
      await exportConfig(7)

      expect(mockGet).toHaveBeenCalledWith('/templates/7/export-config', { responseType: 'blob' })
    })
  })

  describe('importConfig', () => {
    it('sends multipart POST to /templates/import-config', async () => {
      const file = new File(['{}'], 'config.json', { type: 'application/json' })
      await importConfig(file)

      expect(mockPost).toHaveBeenCalledTimes(1)
      const [url, body, config] = mockPost.mock.calls[0]
      expect(url).toBe('/templates/import-config')
      expect(body).toBeInstanceOf(FormData)
      expect(body.get('file')).toBe(file)
      expect(config).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } })
    })
  })
})
