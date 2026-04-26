import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const mockRouterPush = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '1' } }),
  useRouter: () => ({ push: mockRouterPush }),
}))

vi.mock('@/api/templates', () => ({
  getTemplate: vi.fn().mockResolvedValue({ id: 1, name: 'Test', status: 'DRAFT', templateType: 'COMPOSITE', version: 1 }),
  getAvailableTransitions: vi.fn().mockResolvedValue([]),
  createDraftVersion: vi.fn(),
  getTemplateVersions: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/composite-templates', () => ({
  getAssemblyConfig: vi.fn().mockResolvedValue({ segments: [] }),
  getCompositeCoverage: vi.fn().mockResolvedValue({ overallCoveragePercent: 0, segmentCoverages: [] }),
  migrateToComposite: vi.fn(),
}))

vi.mock('@/api/parameters', () => ({
  getParameters: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/market', () => ({
  getTestCases: vi.fn().mockResolvedValue({
    content: [], totalElements: 0, totalPages: 0, size: 20, number: 0,
  }),
}))

vi.mock('@/api/admin', () => ({
  getTemplateReviews: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
  getTemplatePermissions: vi.fn().mockResolvedValue([]),
}))

// Stub stage components
vi.mock('@/views/template-workspace/components/DesignStage.vue', () => ({
  default: {
    name: 'DesignStage',
    template: '<div class="stub-design-stage">DesignStage</div>',
    props: ['readonly'],
  },
}))

vi.mock('@/views/template-workspace/components/TestStage.vue', () => ({
  default: {
    name: 'TestStage',
    template: '<div class="stub-test-stage">TestStage</div>',
    props: ['readonly'],
  },
}))

vi.mock('@/views/template-workspace/components/ApprovalStage.vue', () => ({
  default: {
    name: 'ApprovalStage',
    template: '<div class="stub-approval-stage">ApprovalStage</div>',
    props: ['readonly'],
  },
}))

vi.mock('@/views/template-workspace/components/PublishStage.vue', () => ({
  default: {
    name: 'PublishStage',
    template: '<div class="stub-publish-stage">PublishStage</div>',
    props: ['readonly'],
  },
}))

vi.mock('@/views/template-workspace/components/StageIndicator.vue', () => ({
  default: {
    name: 'StageIndicator',
    template: '<div class="stub-stage-indicator" />',
    props: ['stages', 'currentStage'],
    emits: ['stage-click'],
  },
}))

import IndexVue from '@/views/template-workspace/Index.vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'

function populateStore() {
  const store = useTemplateWorkspaceStore()
  store.templateId = 1
  store.template = {
    id: 1, name: 'Test Template', status: 'DRAFT', templateType: 'COMPOSITE',
    version: 1, description: null, filePath: null, tenantId: 1, createdBy: 1,
    categoryId: null, createdAt: '2024-01-01', updatedAt: '2024-01-01',
  } as any
  store.loading = false
  store.criticalError = null
  store.assemblyConfig = { segments: [] }
  store.parameters = []
  return store
}

function mountIndex() {
  return mount(IndexVue)
}

describe('TemplateWorkspace Index', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockRouterPush.mockReset()
  })

  it('can be imported without errors', async () => {
    const mod = await import('@/views/template-workspace/Index.vue')
    expect(mod.default).toBeDefined()
  })

  describe('four-stage architecture', () => {
    it('renders StageIndicator component', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()
      expect(wrapper.find('.stub-stage-indicator').exists()).toBe(true)
    })

    it('renders DesignStage by default (currentStage = design)', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()
      expect(wrapper.find('.stub-design-stage').exists()).toBe(true)
    })

    it('does NOT render old el-tabs', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()
      expect(wrapper.find('.el-tabs').exists()).toBe(false)
    })
  })
})
