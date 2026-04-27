import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'

const mockUpdateAssemblyConfig = vi.fn().mockResolvedValue(undefined)

vi.mock('@/api/composite-templates', () => ({
  updateAssemblyConfig: (...args: unknown[]) => mockUpdateAssemblyConfig(...args),
}))

vi.mock('@/api/parameters', () => ({
  createParameter: vi.fn(),
}))

import ParameterSidebar from '@/views/template-workspace/components/ParameterSidebar.vue'

function mountSidebar(readonly = false) {
  return mount(ParameterSidebar, {
    props: { collapsed: false, readonly },
  })
}

function seedDraftStore() {
  const store = useTemplateWorkspaceStore()
  store.templateId = 1
  store.template = {
    id: 1,
    name: 'T',
    status: 'DRAFT',
    templateType: 'COMPOSITE',
    version: 1,
    description: null,
    filePath: null,
    tenantId: 1,
    createdBy: 1,
    categoryId: null,
    createdAt: '2024-01-01',
    updatedAt: '2024-01-01',
  } as any
  store.assemblyConfig = { segments: [] }
  store.parameters = []
  return store
}

describe('ParameterSidebar (WP-01 workflow)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockUpdateAssemblyConfig.mockClear()
  })

  it('does not offer a template-level submit-for-review control in the sidebar (draft)', () => {
    seedDraftStore()
    const wrapper = mountSidebar(false)
    const actions = wrapper.find('.sidebar-actions')
    expect(actions.exists()).toBe(true)
    const buttons = actions.findAll('button')
    expect(buttons.length).toBe(1)
    expect(buttons[0].text()).toContain('Save')
    expect(wrapper.text()).not.toMatch(/Submitted for review/i)
  })

  it('shows the review workflow hint under Save', () => {
    seedDraftStore()
    const wrapper = mountSidebar(false)
    expect(wrapper.find('.sidebar-workflow-hint').text()).toMatch(/Test and Approval/i)
  })

  it('Save only persists assembly config, does not call submit-for-review', async () => {
    seedDraftStore()
    const wrapper = mountSidebar(false)
    await wrapper.find('.sidebar-save-btn').trigger('click')
    await flushPromises()
    expect(mockUpdateAssemblyConfig).toHaveBeenCalledWith(1, { segments: [] })
  })

  it('hides save strip when not draft or readonly', () => {
    seedDraftStore()
    const store = useTemplateWorkspaceStore()
    store.template = { ...store.template!, status: 'PENDING_REVIEW' } as any
    const wrapper = mount(ParameterSidebar, {
      props: { collapsed: false, readonly: false },
    })
    expect(wrapper.find('.sidebar-actions').exists()).toBe(false)
  })
})
