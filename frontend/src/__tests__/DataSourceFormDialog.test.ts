import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DataSourceFormDialog from '@/views/data-sources/DataSourceFormDialog.vue'

// Mock the data-sources API
vi.mock('@/api/data-sources', () => ({
  createDataSource: vi.fn().mockResolvedValue({}),
  updateDataSource: vi.fn().mockResolvedValue({}),
}))

// Mock vue-router
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {} }),
}))

// Stub child components
vi.mock('@/views/data-sources/KeyValueEditor.vue', () => ({
  default: { template: '<div class="stub-kv-editor" />', props: ['modelValue'] },
}))
vi.mock('@/views/data-sources/TransformRulesEditor.vue', () => ({
  default: { template: '<div class="stub-transform-editor" />', props: ['modelValue'] },
}))

// Helper: el-dialog teleports content to body, so we query document.body
function getDialogText(): string {
  const overlay = document.body.querySelector('.el-overlay')
  return overlay?.textContent ?? ''
}

function getDialogEl(): Element | null {
  return document.body.querySelector('.el-overlay')
}

describe('DataSourceFormDialog', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    // Clean up any teleported dialog content from previous tests
    document.body.innerHTML = '<div id="app"></div>'
  })

  const baseProps = {
    visible: true,
    dataSource: null,
    templateId: 1,
  }

  function mountDialog(props = baseProps) {
    return mount(DataSourceFormDialog, {
      props,
      attachTo: document.getElementById('app')!,
    })
  }

  it('renders the dialog with create title when no dataSource is provided', async () => {
    mountDialog()
    await flushPromises()
    expect(getDialogText()).toContain('Create Data Source')
  })

  it('renders the dialog with edit title when dataSource is provided', async () => {
    mountDialog({
      ...baseProps,
      dataSource: {
        id: 1,
        templateId: 1,
        name: 'Test DS',
        type: 'HTTP_API' as const,
        configJson: '{"url":"https://example.com","method":"GET","headers":{},"params":{},"authType":"NONE","authConfig":{},"timeout":5000,"retryEnabled":false,"retryCount":3,"retryInterval":1000,"retryBackoff":true}',
        cacheEnabled: false,
        cacheTtl: null,
        priority: 0,
        createdAt: '2024-01-01',
        updatedAt: '2024-01-01',
      },
    })
    await flushPromises()
    expect(getDialogText()).toContain('Edit Data Source')
  })

  it('defaults to HTTP_API type with relevant fields visible', async () => {
    mountDialog()
    await flushPromises()

    const text = getDialogText()
    expect(text).toContain('URL')
    expect(text).toContain('Authentication Type')

    // Should NOT show database-specific fields
    expect(text).not.toContain('Database Type')
    expect(text).not.toContain('SQL Query')
  })

  it('shows database fields when type is switched to DATABASE', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.form.type = 'DATABASE'
    vm.onTypeChange()
    await flushPromises()

    const text = getDialogText()
    expect(text).toContain('Database Type')
    expect(text).toContain('Host')
    expect(text).toContain('Port')
    expect(text).toContain('Database Name')
  })

  it('shows internal system fields when type is INTERNAL_SYSTEM', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.form.type = 'INTERNAL_SYSTEM'
    vm.onTypeChange()
    await flushPromises()

    const text = getDialogText()
    expect(text).toContain('Service Name')
    expect(text).toContain('Service URL')
  })

  it('shows cache TTL field only when cache is enabled', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    const vm = wrapper.vm as any
    expect(vm.form.cacheEnabled).toBe(false)

    // TTL label should not be visible when cache is disabled
    expect(getDialogText()).not.toContain('Cache TTL')

    // Enable cache
    vm.form.cacheEnabled = true
    await flushPromises()

    expect(getDialogText()).toContain('Cache TTL')
  })

  it('shows retry config fields when retry is enabled for HTTP_API', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.form.httpConfig.retryEnabled = true
    await flushPromises()

    const text = getDialogText()
    expect(text).toContain('Retry Count')
    expect(text).toContain('Retry Interval')
    expect(text).toContain('Exponential Backoff')
  })

  it('renders save and cancel buttons in the dialog footer', async () => {
    mountDialog()
    await flushPromises()

    const overlay = getDialogEl()
    expect(overlay).not.toBeNull()

    const buttons = overlay!.querySelectorAll('.el-button')
    const buttonTexts = Array.from(buttons).map((b) => b.textContent?.trim())
    expect(buttonTexts).toContain('Cancel')
    expect(buttonTexts).toContain('Save')
  })

  it('emits update:visible false when cancel is clicked', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    const overlay = getDialogEl()
    const buttons = overlay!.querySelectorAll('.el-button')
    const cancelBtn = Array.from(buttons).find((b) => b.textContent?.trim() === 'Cancel') as HTMLElement
    expect(cancelBtn).toBeDefined()

    cancelBtn.click()
    await flushPromises()

    expect(wrapper.emitted('update:visible')).toBeTruthy()
    expect(wrapper.emitted('update:visible')![0]).toEqual([false])
  })

  it('resets form configs when type changes', async () => {
    const wrapper = mountDialog()
    await flushPromises()

    const vm = wrapper.vm as any
    // Set some HTTP config
    vm.form.httpConfig.url = 'https://example.com'

    // Switch to DATABASE
    vm.form.type = 'DATABASE'
    vm.onTypeChange()
    await flushPromises()

    // HTTP config should be reset
    expect(vm.form.httpConfig.url).toBe('')
  })
})
