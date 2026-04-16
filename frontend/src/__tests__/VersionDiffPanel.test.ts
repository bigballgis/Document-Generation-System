import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const mockGetVersionDiff = vi.fn()

vi.mock('@/api/templates', () => ({
  getTemplate: vi.fn().mockResolvedValue({ id: 1, name: 'T', status: 'DRAFT', templateType: 'COMPOSITE', version: 1, tenantId: 1 }),
  getAvailableTransitions: vi.fn().mockResolvedValue([]),
  getTemplateVersions: vi.fn().mockResolvedValue([]),
  getVersionDiff: (...args: any[]) => mockGetVersionDiff(...args),
}))

vi.mock('@/api/composite-templates', () => ({
  getAssemblyConfig: vi.fn().mockResolvedValue({ segments: [] }),
  getCompositeCoverage: vi.fn().mockResolvedValue({ overallCoveragePercent: 0, segmentCoverages: [] }),
  getCompositeSegments: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/parameters', () => ({ getParameters: vi.fn().mockResolvedValue([]) }))

vi.mock('@/api/market', () => ({ getTestCases: vi.fn().mockResolvedValue([]) }))
vi.mock('@/api/admin', () => ({
  getTemplateReviews: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
  getTemplatePermissions: vi.fn().mockResolvedValue([]),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
  onBeforeRouteLeave: vi.fn(),
}))

import VersionDiffPanel from '@/views/template-workspace/components/VersionDiffPanel.vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'

const sampleVersions = [
  { id: 10, templateId: 1, versionNumber: 1, createdAt: '2024-01-01', createdBy: 'admin', configJson: '{}' },
  { id: 11, templateId: 1, versionNumber: 2, createdAt: '2024-02-01', createdBy: 'admin', configJson: '{}' },
]

function populateStore() {
  const store = useTemplateWorkspaceStore()
  store.templateId = 1
  store.template = {
    id: 1, name: 'Test', status: 'DRAFT', templateType: 'COMPOSITE',
    version: 1, description: '', tenantId: 1, createdAt: '', updatedAt: '',
    categoryId: null, tags: [], outputFormat: 'DOCX', reviewRequired: false,
  }
  store.versions = sampleVersions as any
  store.loading = false
  store.criticalError = null
  return store
}

describe('VersionDiffPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockGetVersionDiff.mockReset()
  })

  it('renders two version selectors and a Compare button', async () => {
    populateStore()
    const wrapper = mount(VersionDiffPanel)
    await flushPromises()

    const selects = wrapper.findAll('.el-select')
    expect(selects.length).toBe(2)

    const compareBtn = wrapper.find('.el-button--primary')
    expect(compareBtn.exists()).toBe(true)
  })

  it('Compare button is disabled when no versions selected', async () => {
    populateStore()
    const wrapper = mount(VersionDiffPanel)
    await flushPromises()

    const compareBtn = wrapper.find('.el-button--primary')
    expect(compareBtn.attributes('disabled')).toBeDefined()
  })

  it('calls getVersionDiff when Compare is clicked with valid selections', async () => {
    populateStore()
    mockGetVersionDiff.mockResolvedValue({
      versionA: 1, versionB: 2,
      textDiffs: [{ field: 'name', type: 'MODIFIED', oldValue: 'A', newValue: 'B' }],
      variableDiffs: [], dataSourceDiffs: [], expressionDiffs: [],
      summary: { added: 0, removed: 0, modified: 1 },
    })

    const wrapper = mount(VersionDiffPanel)
    await flushPromises()

    // Set version selections via component internals
    const vm = wrapper.vm as any
    vm.versionA = 1
    vm.versionB = 2
    await wrapper.vm.$nextTick()

    const compareBtn = wrapper.find('.el-button--primary')
    await compareBtn.trigger('click')
    await flushPromises()

    expect(mockGetVersionDiff).toHaveBeenCalledWith(1, 1, 2)
  })

  it('renders diff result after successful comparison', async () => {
    populateStore()
    mockGetVersionDiff.mockResolvedValue({
      versionA: 1, versionB: 2,
      textDiffs: [{ field: 'name', type: 'MODIFIED', oldValue: 'A', newValue: 'B' }],
      variableDiffs: [], dataSourceDiffs: [], expressionDiffs: [],
      summary: { added: 1, removed: 2, modified: 3 },
    })

    const wrapper = mount(VersionDiffPanel)
    await flushPromises()

    const vm = wrapper.vm as any
    vm.versionA = 1
    vm.versionB = 2
    await wrapper.vm.$nextTick()

    await wrapper.find('.el-button--primary').trigger('click')
    await flushPromises()

    // Check summary tags
    const tags = wrapper.findAll('.diff-summary .el-tag')
    expect(tags.length).toBe(3)
  })
})
