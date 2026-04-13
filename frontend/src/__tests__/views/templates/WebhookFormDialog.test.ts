import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('@/api/webhooks', () => ({
  createWebhook: vi.fn().mockResolvedValue({}),
  updateWebhook: vi.fn().mockResolvedValue({}),
  listWebhooks: vi.fn(),
  deleteWebhook: vi.fn(),
  getWebhookLogs: vi.fn(),
}))

import WebhookFormDialog from '@/views/templates/components/WebhookFormDialog.vue'
import { createWebhook, updateWebhook } from '@/api/webhooks'

describe('WebhookFormDialog', () => {
  beforeEach(() => {
    vi.mocked(createWebhook).mockClear()
    vi.mocked(updateWebhook).mockClear()
    vi.mocked(createWebhook).mockResolvedValue({} as any)
    vi.mocked(updateWebhook).mockResolvedValue({} as any)
  })

  it('renders empty form in create mode', async () => {
    const wrapper = mount(WebhookFormDialog, {
      props: { visible: true, data: null, templateId: 10 },
    })
    await flushPromises()

    // Dialog title should indicate create mode
    expect(wrapper.text()).toContain('Create')

    // Form inputs should be empty
    const inputs = wrapper.findAll('input')
    for (const input of inputs) {
      expect(input.element.value).toBe('')
    }
  })

  it('renders pre-filled form in edit mode', async () => {
    const existingWebhook = {
      id: 1,
      templateId: 10,
      url: 'https://example.com/hook',
      payloadTemplate: '{"key":"value"}',
      enabled: true,
      createdAt: '2024-01-01',
      updatedAt: '2024-01-01',
    }

    // Mount with visible=false first, then set to true to trigger the watch
    const wrapper = mount(WebhookFormDialog, {
      props: { visible: false, data: existingWebhook, templateId: 10 },
    })
    await flushPromises()
    await wrapper.setProps({ visible: true })
    await flushPromises()

    // Dialog title should indicate edit mode
    expect(wrapper.text()).toContain('Edit')

    // URL should be pre-filled
    const urlInput = wrapper.findAll('input').find(i => i.element.value === 'https://example.com/hook')
    expect(urlInput).toBeDefined()
  })

  it('calls createWebhook on submit in create mode', async () => {
    const wrapper = mount(WebhookFormDialog, {
      props: { visible: true, data: null, templateId: 10 },
    })
    await flushPromises()

    // Fill in required fields
    const inputs = wrapper.findAll('input')
    // URL input
    await inputs[0].setValue('https://example.com/new-hook')
    // Secret input (password field)
    await inputs[1].setValue('my-secret')
    await flushPromises()

    // Click save button
    const saveBtn = wrapper.findAll('.el-button').find(b => b.text().includes('Save'))
    expect(saveBtn).toBeDefined()
    await saveBtn!.trigger('click')
    await flushPromises()
    // Wait for async validation
    await new Promise(r => setTimeout(r, 100))
    await flushPromises()

    expect(createWebhook).toHaveBeenCalledWith(10, expect.objectContaining({
      url: 'https://example.com/new-hook',
      secret: 'my-secret',
    }))
  })

  it('calls updateWebhook on submit in edit mode', async () => {
    const existingWebhook = {
      id: 5,
      templateId: 10,
      url: 'https://example.com/hook',
      payloadTemplate: '',
      enabled: true,
      createdAt: '2024-01-01',
      updatedAt: '2024-01-01',
    }

    // Mount with visible=false first, then set to true to trigger the watch
    const wrapper = mount(WebhookFormDialog, {
      props: { visible: false, data: existingWebhook, templateId: 10 },
    })
    await flushPromises()
    await wrapper.setProps({ visible: true })
    await flushPromises()

    // Fill in secret (required)
    const inputs = wrapper.findAll('input')
    const secretInput = inputs[1]
    await secretInput.setValue('new-secret')
    await flushPromises()

    // Click save
    const saveBtn = wrapper.findAll('.el-button').find(b => b.text().includes('Save'))
    await saveBtn!.trigger('click')
    await flushPromises()
    await new Promise(r => setTimeout(r, 100))
    await flushPromises()

    expect(updateWebhook).toHaveBeenCalledWith(5, expect.objectContaining({
      url: 'https://example.com/hook',
    }))
  })
})
