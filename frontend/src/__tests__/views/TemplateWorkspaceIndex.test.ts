import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

// ── Capture onBeforeRouteLeave callback ──
let routeLeaveGuard: ((...args: any[]) => any) | null = null
const mockRouterPush = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '1' } }),
  useRouter: () => ({ push: mockRouterPush }),
  onBeforeRouteLeave: (fn: any) => { routeLeaveGuard = fn },
}))

vi.mock('@/api/templates', () => ({
  getTemplate: vi.fn().mockResolvedValue({ id: 1, name: 'Test', status: 'DRAFT', templateType: 'COMPOSITE', version: 1 }),
  getAvailableTransitions: vi.fn().mockResolvedValue([]),
  createDraftVersion: vi.fn(),
}))

vi.mock('@/api/composite-templates', () => ({
  getAssemblyConfig: vi.fn().mockResolvedValue({ segments: [] }),
  getCompositeCoverage: vi.fn().mockResolvedValue({ overallCoveragePercent: 0, segmentCoverages: [] }),
  getCompositeSegments: vi.fn().mockResolvedValue([]),
  migrateToComposite: vi.fn(),
  updateAssemblyConfig: vi.fn(),
  previewCompositeTemplate: vi.fn(),
  previewSelectiveSegments: vi.fn(),
}))

vi.mock('@/api/data-sources', () => ({
  getDataSources: vi.fn().mockResolvedValue([]),
  testConnection: vi.fn(),
  deleteDataSource: vi.fn(),
}))

vi.mock('@/api/expressions', () => ({
  getExpressions: vi.fn().mockResolvedValue([]),
  deleteExpression: vi.fn(),
  validateExpression: vi.fn(),
}))

vi.mock('@/api/segments', () => ({
  createSegment: vi.fn(),
  getSegments: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
  getSegmentLockInfo: vi.fn(),
}))

vi.mock('@/api/market', () => ({
  getTestCases: vi.fn().mockResolvedValue([]),
  deleteTestCase: vi.fn(),
  exportTestCases: vi.fn(),
  importTestCases: vi.fn(),
  runTestCase: vi.fn(),
  createTestCase: vi.fn(),
  updateTestCase: vi.fn(),
}))

vi.mock('@/api/admin', () => ({
  submitForReview: vi.fn(),
  getApiKeys: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
  createApiKey: vi.fn(),
  getUsers: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
  getTemplateReviews: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
}))

// ── Stub child components ──
vi.mock('@/views/template-workspace/components/DataStructureTab.vue', () => ({
  default: {
    name: 'DataStructureTab',
    template: '<div class="stub-data-structure-tab">DataStructureTab</div>',
  },
}))

vi.mock('@/views/template-workspace/components/SegmentArrangementTab.vue', () => ({
  default: {
    name: 'SegmentArrangementTab',
    template: '<div class="stub-segment-arrangement-tab">SegmentArrangementTab</div>',
    setup(_props: any, { expose }: any) {
      const hasUnsavedChanges = false
      expose({ hasUnsavedChanges })
      return { hasUnsavedChanges }
    },
  },
}))

vi.mock('@/views/template-workspace/components/VisualEditorTab.vue', () => ({
  default: {
    name: 'VisualEditorTab',
    template: '<div class="stub-visual-editor-tab">VisualEditorTab</div>',
    emits: ['switch-to-segments'],
    setup(_props: any, { expose }: any) {
      const checkLocks = vi.fn()
      expose({ checkLocks })
      return { checkLocks }
    },
  },
}))

vi.mock('@/views/template-workspace/components/TestingTab.vue', () => ({
  default: {
    name: 'TestingTab',
    template: '<div class="stub-testing-tab">TestingTab</div>',
  },
}))

vi.mock('@/views/template-workspace/components/ReviewPublishTab.vue', () => ({
  default: {
    name: 'ReviewPublishTab',
    template: '<div class="stub-review-publish-tab">ReviewPublishTab</div>',
  },
}))

vi.mock('@/views/template-workspace/components/ExportImportTab.vue', () => ({
  default: {
    name: 'ExportImportTab',
    template: '<div class="stub-export-import-tab">ExportImportTab</div>',
  },
}))

vi.mock('@/views/template-workspace/components/SettingsTab.vue', () => ({
  default: {
    name: 'SettingsTab',
    template: '<div class="stub-settings-tab">SettingsTab</div>',
  },
}))

vi.mock('@/views/template-workspace/components/PlaceholderTab.vue', () => ({
  default: {
    name: 'PlaceholderTab',
    template: '<div class="stub-placeholder-tab">PlaceholderTab({{ phase }})</div>',
    props: ['icon', 'phase', 'messageKey', 'descriptionKey'],
  },
}))

vi.mock('@/views/template-workspace/components/WorkflowStepIndicator.vue', () => ({
  default: {
    name: 'WorkflowStepIndicator',
    template: '<div class="stub-step-indicator" />',
    props: ['steps', 'currentTab'],
    emits: ['step-click'],
  },
}))

// Mock ElMessageBox.confirm
const mockConfirm = vi.fn()
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<any>()
  return {
    ...actual,
    ElMessageBox: {
      ...actual.ElMessageBox,
      confirm: (...args: any[]) => mockConfirm(...args),
    },
  }
})

import IndexVue from '@/views/template-workspace/Index.vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'

// ── Helpers ──

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
  store.dataSources = []
  store.expressions = []
  store.segments = []
  return store
}

function mountIndex() {
  return mount(IndexVue)
}

describe('TemplateWorkspace Index', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockConfirm.mockReset()
    mockRouterPush.mockReset()
    routeLeaveGuard = null
  })

  it('can be imported without errors', async () => {
    const mod = await import('@/views/template-workspace/Index.vue')
    expect(mod.default).toBeDefined()
  })

  // ── Requirement 8.1–8.3: First 3 tabs render real components ──

  describe('real tab components (Requirements 8.1–8.3)', () => {
    it('renders DataStructureTab in the first tab pane', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()

      expect(wrapper.find('.stub-data-structure-tab').exists()).toBe(true)
      expect(wrapper.text()).toContain('DataStructureTab')
    })

    it('renders SegmentArrangementTab in the second tab pane', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()

      expect(wrapper.find('.stub-segment-arrangement-tab').exists()).toBe(true)
      expect(wrapper.text()).toContain('SegmentArrangementTab')
    })

    it('renders VisualEditorTab in the third tab pane', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()

      expect(wrapper.find('.stub-visual-editor-tab').exists()).toBe(true)
      expect(wrapper.text()).toContain('VisualEditorTab')
    })
  })

  // ── Requirement 8.1–8.3 (P3): Testing and ReviewPublish tabs render real components ──

  describe('P3 real tab components (Requirements 8.1–8.3)', () => {
    it('renders TestingTab in the testing tab pane', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()

      expect(wrapper.find('.stub-testing-tab').exists()).toBe(true)
      expect(wrapper.text()).toContain('TestingTab')
    })

    it('renders ReviewPublishTab in the reviewPublish tab pane', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()

      expect(wrapper.find('.stub-review-publish-tab').exists()).toBe(true)
      expect(wrapper.text()).toContain('ReviewPublishTab')
    })
  })

  // ── P4: ExportImport and Settings tabs render real components ──

  describe('P4 real tab components (Requirements 8.1–8.3)', () => {
    it('renders ExportImportTab in the exportImport tab pane', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()

      expect(wrapper.find('.stub-export-import-tab').exists()).toBe(true)
      expect(wrapper.text()).toContain('ExportImportTab')
    })

    it('renders SettingsTab in the settings tab pane', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()

      expect(wrapper.find('.stub-settings-tab').exists()).toBe(true)
      expect(wrapper.text()).toContain('SettingsTab')
    })

    it('does NOT render any PlaceholderTab instances', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()

      const placeholders = wrapper.findAll('.stub-placeholder-tab')
      expect(placeholders.length).toBe(0)
    })
  })

  // ── Requirement 3.8 / 8.5: handleBeforeLeave guard ──

  describe('handleBeforeLeave — tab switch unsaved changes guard', () => {
    it('allows tab switch when no unsaved changes', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()

      const vm = wrapper.vm as any
      // Switch from segments tab with no unsaved changes
      const result = await vm.handleBeforeLeave('dataStructure', 'segments')
      expect(result).toBe(true)
      // ElMessageBox.confirm should NOT have been called
      expect(mockConfirm).not.toHaveBeenCalled()
    })

    it('allows tab switch when leaving a non-segments tab', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()

      const vm = wrapper.vm as any
      // Switch from dataStructure to editor — no guard needed
      const result = await vm.handleBeforeLeave('editor', 'dataStructure')
      expect(result).toBe(true)
      expect(mockConfirm).not.toHaveBeenCalled()
    })

    it('shows confirm dialog when leaving segments tab with unsaved changes', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()

      // Simulate unsaved changes on the SegmentArrangementTab ref
      const vm = wrapper.vm as any
      vm.segmentArrangementRef = { hasUnsavedChanges: true }

      mockConfirm.mockResolvedValue('confirm')
      const result = await vm.handleBeforeLeave('editor', 'segments')

      expect(mockConfirm).toHaveBeenCalledTimes(1)
      expect(result).toBe(true)
    })

    it('blocks tab switch when user cancels the unsaved changes dialog', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()

      const vm = wrapper.vm as any
      vm.segmentArrangementRef = { hasUnsavedChanges: true }

      mockConfirm.mockRejectedValue('cancel')
      const result = await vm.handleBeforeLeave('editor', 'segments')

      expect(mockConfirm).toHaveBeenCalledTimes(1)
      expect(result).toBe(false)
    })

    it('triggers lock check when switching to editor tab', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()

      const mockCheckLocks = vi.fn()
      const vm = wrapper.vm as any
      vm.visualEditorRef = { checkLocks: mockCheckLocks }

      await vm.handleBeforeLeave('editor', 'dataStructure')

      expect(mockCheckLocks).toHaveBeenCalledTimes(1)
    })
  })

  // ── Requirement 3.8: onBeforeRouteLeave guard ──

  describe('onBeforeRouteLeave guard', () => {
    it('registers a route leave guard on mount', async () => {
      routeLeaveGuard = null
      populateStore()
      mountIndex()
      await flushPromises()

      expect(routeLeaveGuard).not.toBeNull()
      expect(typeof routeLeaveGuard).toBe('function')
    })

    it('allows route leave when no unsaved changes', async () => {
      populateStore()
      mountIndex()
      await flushPromises()

      expect(routeLeaveGuard).not.toBeNull()
      const result = await routeLeaveGuard!()
      expect(result).toBe(true)
      expect(mockConfirm).not.toHaveBeenCalled()
    })

    it('shows confirm dialog on route leave when unsaved changes exist', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()

      const vm = wrapper.vm as any
      vm.segmentArrangementRef = { hasUnsavedChanges: true }

      mockConfirm.mockResolvedValue('confirm')
      const result = await routeLeaveGuard!()

      expect(mockConfirm).toHaveBeenCalledTimes(1)
      expect(result).toBe(true)
    })

    it('blocks route leave when user cancels the unsaved changes dialog', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()

      const vm = wrapper.vm as any
      vm.segmentArrangementRef = { hasUnsavedChanges: true }

      mockConfirm.mockRejectedValue('cancel')
      const result = await routeLeaveGuard!()

      expect(mockConfirm).toHaveBeenCalledTimes(1)
      expect(result).toBe(false)
    })
  })

  // ── Tab count verification ──

  describe('total tab structure', () => {
    it('renders exactly 7 tab panes (all real components, no placeholders)', async () => {
      populateStore()
      const wrapper = mountIndex()
      await flushPromises()

      // All 7 real component stubs should be present
      const realTabs = [
        wrapper.find('.stub-data-structure-tab'),
        wrapper.find('.stub-segment-arrangement-tab'),
        wrapper.find('.stub-visual-editor-tab'),
        wrapper.find('.stub-testing-tab'),
        wrapper.find('.stub-review-publish-tab'),
        wrapper.find('.stub-export-import-tab'),
        wrapper.find('.stub-settings-tab'),
      ]
      realTabs.forEach(tab => expect(tab.exists()).toBe(true))

      // No PlaceholderTab should be rendered
      const placeholders = wrapper.findAll('.stub-placeholder-tab')
      expect(placeholders.length).toBe(0)
    })
  })
})
