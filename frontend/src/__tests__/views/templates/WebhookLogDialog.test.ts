import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('@/api/webhooks', () => ({
  getWebhookLogs: vi.fn(),
  createWebhook: vi.fn(),
  listWebhooks: vi.fn(),
  updateWebhook: vi.fn(),
  deleteWebhook: vi.fn(),
}))

import WebhookLogDialog from '@/views/templates/components/WebhookLogDialog.vue'
import { getWebhookLogs } from '@/api/webhooks'

const sampleLogs = {
  content: [
    {
      id: 1,
      webhookConfigId: 5,
      eventType: 'DOCUMENT_GENERATED',
      payload: '{"templateId":10}',
      responseStatus: 200,
      responseBody: 'OK',
      sentAt: '2024-01-15T10:00:00Z',
    },
    {
      id: 2,
      webhookConfigId: 5,
      eventType: 'TEMPLATE_UPDATED',
      payload: '{"templateId":10}',
      responseStatus: 500,
      responseBody: 'Internal Server Error',
      sentAt: '2024-01-15T11:00:00Z',
    },
  ],
  totalElements: 2,
  totalPages: 1,
  size: 10,
  number: 0,
}

describe('WebhookLogDialog', () => {
  beforeEach(() => {
    vi.mocked(getWebhookLogs).mockReset()
    vi.mocked(getWebhookLogs).mockResolvedValue(sampleLogs as any)
  })

  it('renders log table when dialog becomes visible', async () => {
    // Mount with visible=false first, then set to true to trigger the watch
    const wrapper = mount(WebhookLogDialog, {
      props: { visible: false, webhookId: 5 },
    })
    await flushPromises()
    await wrapper.setProps({ visible: true })
    await flushPromises()

    expect(getWebhookLogs).toHaveBeenCalledWith(5, { page: 1, size: 10 })

    const text = wrapper.text()
    expect(text).toContain('DOCUMENT_GENERATED')
    expect(text).toContain('TEMPLATE_UPDATED')
    expect(text).toContain('200')
    expect(text).toContain('500')
  })

  it('renders pagination component', async () => {
    const wrapper = mount(WebhookLogDialog, {
      props: { visible: true, webhookId: 5 },
    })
    await flushPromises()

    expect(wrapper.find('.el-pagination').exists()).toBe(true)
  })
})
