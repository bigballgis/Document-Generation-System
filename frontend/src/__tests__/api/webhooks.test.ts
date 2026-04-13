import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockGet = vi.fn()
const mockPost = vi.fn()
const mockPut = vi.fn()
const mockDelete = vi.fn()

vi.mock('@/api/request', () => ({
  default: {
    get: (...args: any[]) => mockGet(...args),
    post: (...args: any[]) => mockPost(...args),
    put: (...args: any[]) => mockPut(...args),
    delete: (...args: any[]) => mockDelete(...args),
  },
}))

import { createWebhook, listWebhooks, updateWebhook, deleteWebhook, getWebhookLogs } from '@/api/webhooks'

describe('webhooks API', () => {
  beforeEach(() => {
    mockGet.mockClear()
    mockPost.mockClear()
    mockPut.mockClear()
    mockDelete.mockClear()
    mockGet.mockResolvedValue({})
    mockPost.mockResolvedValue({})
    mockPut.mockResolvedValue({})
    mockDelete.mockResolvedValue(undefined)
  })

  describe('createWebhook', () => {
    it('sends POST to /templates/{templateId}/webhooks with data', async () => {
      const data = { url: 'https://example.com/hook', secret: 's3cret' }
      await createWebhook(10, data)

      expect(mockPost).toHaveBeenCalledWith('/templates/10/webhooks', data)
    })
  })

  describe('listWebhooks', () => {
    it('sends GET to /templates/{templateId}/webhooks', async () => {
      await listWebhooks(10)

      expect(mockGet).toHaveBeenCalledWith('/templates/10/webhooks')
    })
  })

  describe('updateWebhook', () => {
    it('sends PUT to /webhooks/{webhookId} with data', async () => {
      const data = { url: 'https://new-url.com', enabled: false }
      await updateWebhook(5, data)

      expect(mockPut).toHaveBeenCalledWith('/webhooks/5', data)
    })
  })

  describe('deleteWebhook', () => {
    it('sends DELETE to /webhooks/{webhookId}', async () => {
      await deleteWebhook(5)

      expect(mockDelete).toHaveBeenCalledWith('/webhooks/5')
    })
  })

  describe('getWebhookLogs', () => {
    it('sends GET to /webhooks/{webhookId}/logs with pagination params', async () => {
      await getWebhookLogs(5, { page: 1, size: 20 })

      expect(mockGet).toHaveBeenCalledWith('/webhooks/5/logs', { params: { page: 1, size: 20 } })
    })
  })
})
