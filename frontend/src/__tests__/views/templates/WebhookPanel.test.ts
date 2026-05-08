import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

// Mock the webhooks API
vi.mock('@/api/webhooks', () => ({
  listWebhooks: vi.fn(),
  deleteWebhook: vi.fn(),
  createWebhook: vi.fn(),
  updateWebhook: vi.fn(),
  getWebhookLogs: vi.fn(),
}))

// Stub child dialog components
vi.mock('@/views/templates/components/WebhookFormDialog.vue', () => ({
  default: { template: '<div class="stub-form-dialog" />', props: ['visible', 'data', 'templateId'] },
}))
vi.mock('@/views/templates/components/WebhookLogDialog.vue', () => ({
  default: { template: '<div class="stub-log-dialog" />', props: ['visible', 'webhookId'] },
}))

// Mock ElMessageBox
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessageBox: {
      confirm: vi.fn().mockResolvedValue('confirm'),
    },
  }
})

import WebhookPanel from '@/views/templates/components/WebhookPanel.vue'
import { listWebhooks, deleteWebhook } from '@/api/webhooks'
import { ElMessageBox } from 'element-plus'

const sampleWebhooks = [
  {
    id: 1,
    templateId: 10,
    url: 'https://example.com/hook1',
    payloadTemplate: '{"event":"{{event}}"}',
    enabled: true,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  },
  {
    id: 2,
    templateId: 10,
    url: 'https://example.com/hook2',
    payloadTemplate: '',
    enabled: false,
    createdAt: '2024-02-01T00:00:00Z',
    updatedAt: '2024-02-01T00:00:00Z',
  },
]

describe('WebhookPanel', () => {
  beforeEach(() => {
    vi.mocked(listWebhooks).mockReset()
    vi.mocked(deleteWebhook).mockReset()
    vi.mocked(deleteWebhook).mockResolvedValue(undefined as any)
  })

  it('renders webhook table when webhooks exist', async () => {
    vi.mocked(listWebhooks).mockResolvedValue(sampleWebhooks as any)

    const wrapper = mount(WebhookPanel, { props: { templateId: 10 } })
    await flushPromises()

    expect(listWebhooks).toHaveBeenCalledWith(10)
    const text = wrapper.text()
    expect(text).toContain('https://example.com/hook1')
    expect(text).toContain('https://example.com/hook2')
  })

  it('shows el-empty when no webhooks', async () => {
    vi.mocked(listWebhooks).mockResolvedValue([])

    const wrapper = mount(WebhookPanel, { props: { templateId: 10 } })
    await flushPromises()

    expect(wrapper.find('.el-empty').exists()).toBe(true)
  })

  it('opens create dialog when Create Webhook button is clicked', async () => {
    vi.mocked(listWebhooks).mockResolvedValue([])

    const wrapper = mount(WebhookPanel, { props: { templateId: 10 } })
    await flushPromises()

    const createBtn = wrapper.findAll('.el-button').find(b => b.text().includes('Create'))
    expect(createBtn).toBeDefined()
    await createBtn!.trigger('click')
    await flushPromises()

    expect(wrapper.find('.stub-form-dialog').exists()).toBe(true)
  })

  it('calls deleteWebhook after confirmation when Delete is clicked', async () => {
    vi.mocked(listWebhooks).mockResolvedValue(sampleWebhooks as any)

    const wrapper = mount(WebhookPanel, { props: { templateId: 10 } })
    await flushPromises()

    // Find delete buttons in the table
    const deleteBtn = wrapper.findAll('.el-button--danger')[0]
    expect(deleteBtn).toBeDefined()
    await deleteBtn!.trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(deleteWebhook).toHaveBeenCalledWith(1)
  })
})
