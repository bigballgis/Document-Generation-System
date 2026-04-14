import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { TestCaseDTO } from '@/api/market'

// ── Mock API modules ──
const mockDeleteTestCase = vi.fn()
const mockExportTestCases = vi.fn()
const mockImportTestCases = vi.fn()
const mockRunTestCase = vi.fn()
const mockRunAllCompositeTests = vi.fn()
const mockPreviewCompositeTemplate = vi.fn()
const mockGetTemplateCoverage = vi.fn()

vi.mock('@/api/market', () => ({
  getTestCases: vi.fn().mockResolvedValue([]),
  deleteTestCase: (...args: any[]) => mockDeleteTestCase(...args),
  exportTestCases: (...args: any[]) => mockExportTestCases(...args),
  importTestCases: (...args: any[]) => mockImportTestCases(...args),
  runTestCase: (...args: any[]) => mockRunTestCase(...args),
  createTestCase: vi.fn(),
  updateTestCase: vi.fn(),
}))

vi.mock('@/api/composite-templates', () => ({
  getAssemblyConfig: vi.fn().mockResolvedValue({ segments: [] }),
  getCompositeCoverage: vi.fn().mockResolvedValue({ overallCoveragePercent: 0, segmentCoverages: [] }),
  getCompositeSegments: vi.fn().mockResolvedValue([]),
  runAllCompositeTests: (...args: any[]) => mockRunAllCompositeTests(...args),
  previewCompositeTemplate: (...args: any[]) => mockPreviewCompositeTemplate(...args),
}))

vi.mock('@/api/templates', () => ({
  getTemplate: vi.fn().mockResolvedValue({ id: 1, name: 'T', status: 'DRAFT', templateType: 'COMPOSITE', version: 1 }),
  getAvailableTransitions: vi.fn().mockResolvedValue([]),
  getTemplateCoverage: (...args: any[]) => mockGetTemplateCoverage(...args),
}))

vi.mock('@/api/data-sources', () => ({ getDataSources: vi.fn().mockResolvedValue([]) }))
vi.mock('@/api/expressions', () => ({ getExpressions: vi.fn().mockResolvedValue([]) }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
  onBeforeRouteLeave: vi.fn(),
}))


// Stub child dialog
vi.mock('@/views/template-workspace/components/TestCaseFormDialog.vue', () => ({
  default: {
    template: '<div class="stub-test-case-dialog" />',
    props: ['visible', 'templateId', 'testCase'],
    emits: ['update:visible', 'saved'],
  },
}))

// Mock ElMessageBox.confirm
const mockConfirm = vi.fn()
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<any>()
  return {
    ...actual,
    ElMessageBox: { ...actual.ElMessageBox, confirm: (...args: any[]) => mockConfirm(...args) },
  }
})

import TestingTab from '@/views/template-workspace/components/TestingTab.vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'

// ── Test fixtures ──
const sampleTestCases: TestCaseDTO[] = [
  { id: 1, templateId: 1, name: 'TC1', testData: '{"a":1}', expectedResult: '', compareMode: 'VARIABLE', createdAt: '2024-01-01', updatedAt: '2024-06-01' },
  { id: 2, templateId: 1, name: 'TC2', testData: '{"b":2,"c":"a very long json string that should be truncated after eighty characters to show ellipsis in the table"}', expectedResult: '', compareMode: 'TEXT', createdAt: '2024-02-01', updatedAt: '2024-06-15' },
]

function populateStore(overrides: Record<string, any> = {}) {
  const store = useTemplateWorkspaceStore()
  store.templateId = 1
  store.template = { id: 1, name: 'Test', status: 'DRAFT', templateType: 'COMPOSITE', version: 1 } as any
  store.testCases = [...sampleTestCases]
  store.coverage = { overallCoveragePercent: 75, segmentCoverages: [
    { segmentId: 1, segmentName: 'Seg1', totalVariables: 10, boundVariables: 8, coveragePercent: 80 },
    { segmentId: 2, segmentName: 'Seg2', totalVariables: 5, boundVariables: 3, coveragePercent: 60 },
  ] }
  Object.assign(store, overrides)
  return store
}

function mountTab() {
  return mount(TestingTab)
}

describe('TestingTab', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockDeleteTestCase.mockReset()
    mockExportTestCases.mockReset()
    mockImportTestCases.mockReset()
    mockRunTestCase.mockReset()
    mockRunAllCompositeTests.mockReset()
    mockPreviewCompositeTemplate.mockReset()
    mockGetTemplateCoverage.mockReset()
    mockConfirm.mockReset()
    mockDeleteTestCase.mockResolvedValue(undefined)
    mockGetTemplateCoverage.mockResolvedValue({ templateId: 1, totalVariables: 15, boundVariables: 11, unboundVariables: 4, coverageRate: 73, boundTags: [], unboundTags: ['tag1', 'tag2'], unusedFields: ['field1'] })
  })

  // ── Requirement 1.1: Three sections with dividers ──
  describe('layout', () => {
    it('renders three sections separated by dividers', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()
      const dividers = wrapper.findAll('.el-divider')
      expect(dividers.length).toBe(2)
      const headers = wrapper.findAll('.section-header h3')
      expect(headers.length).toBe(3)
    })
  })

  // ── Requirement 1.2: First mount calls refreshTestCases ──
  describe('first mount', () => {
    it('calls store.refreshTestCases on mount', async () => {
      const store = populateStore()
      const spy = vi.spyOn(store, 'refreshTestCases').mockResolvedValue()
      mountTab()
      await flushPromises()
      expect(spy).toHaveBeenCalledTimes(1)
    })
  })

  // ── Requirement 1.3: Test case table columns ──
  describe('test case table', () => {
    it('renders test case rows with name, compareMode tag, testData, updatedAt, actions', async () => {
      const store = populateStore()
      // refreshTestCases is called on mount and resets testCases, so we spy and prevent that
      vi.spyOn(store, 'refreshTestCases').mockResolvedValue()
      const wrapper = mountTab()
      await flushPromises()
      const text = wrapper.text()
      expect(text).toContain('TC1')
      expect(text).toContain('TC2')
      expect(text).toContain('VARIABLE')
      expect(text).toContain('TEXT')
      expect(text).toContain('2024-06-01')
    })
  })

  // ── Requirement 1.10: Empty state ──
  describe('empty state', () => {
    it('shows empty state when testCases is empty', async () => {
      populateStore({ testCases: [] })
      const wrapper = mountTab()
      await flushPromises()
      expect(wrapper.find('.el-empty').exists()).toBe(true)
    })
  })

  // ── Requirement 1.4: Add test case dialog ──
  describe('add test case', () => {
    it('opens dialog when Add Test Case button is clicked', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()
      const vm = wrapper.vm as any
      vm.openAddTestCase()
      expect(vm.testCaseDialogVisible).toBe(true)
      expect(vm.editingTestCase).toBeNull()
    })
  })

  // ── Requirement 1.6: Edit test case dialog ──
  describe('edit test case', () => {
    it('opens dialog with test case data when Edit is clicked', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()
      const vm = wrapper.vm as any
      vm.openEditTestCase(sampleTestCases[0])
      expect(vm.testCaseDialogVisible).toBe(true)
      expect(vm.editingTestCase).toEqual(sampleTestCases[0])
    })
  })

  // ── Requirement 1.5/1.6: Saved event refreshes store ──
  describe('saved event', () => {
    it('calls store.refreshTestCases when saved', async () => {
      const store = populateStore()
      const spy = vi.spyOn(store, 'refreshTestCases').mockResolvedValue()
      const wrapper = mountTab()
      await flushPromises()
      spy.mockClear()
      const vm = wrapper.vm as any
      await vm.onTestCaseSaved()
      await flushPromises()
      expect(spy).toHaveBeenCalledTimes(1)
    })
  })

  // ── Requirement 1.7: Delete confirmation flow ──
  describe('delete test case', () => {
    it('confirms and deletes, then refreshes store', async () => {
      const store = populateStore()
      const spy = vi.spyOn(store, 'refreshTestCases').mockResolvedValue()
      mockConfirm.mockResolvedValue('confirm')
      const wrapper = mountTab()
      await flushPromises()
      spy.mockClear()
      const vm = wrapper.vm as any
      await vm.handleDeleteTestCase(1)
      await flushPromises()
      expect(mockConfirm).toHaveBeenCalledTimes(1)
      expect(mockDeleteTestCase).toHaveBeenCalledWith(1)
      expect(spy).toHaveBeenCalledTimes(1)
    })

    it('recovers deletingIds when delete fails', async () => {
      populateStore()
      mockConfirm.mockResolvedValue('confirm')
      mockDeleteTestCase.mockRejectedValue(new Error('fail'))
      const wrapper = mountTab()
      await flushPromises()
      const vm = wrapper.vm as any
      await vm.handleDeleteTestCase(1)
      await flushPromises()
      expect(vm.deletingIds.has(1)).toBe(false)
    })
  })

  // ── Requirement 1.8: Export ──
  describe('export', () => {
    it('calls exportTestCases API', async () => {
      populateStore()
      mockExportTestCases.mockResolvedValue('[]')
      const wrapper = mountTab()
      await flushPromises()
      const vm = wrapper.vm as any
      // Mock URL.createObjectURL and createElement
      ;(globalThis as any).URL.createObjectURL = vi.fn().mockReturnValue('blob:test')
      ;(globalThis as any).URL.revokeObjectURL = vi.fn()
      await vm.handleExport()
      await flushPromises()
      expect(mockExportTestCases).toHaveBeenCalledWith(1)
    })
  })

  // ── Requirement 2.1: Run All Tests ──
  describe('run all tests', () => {
    it('calls runAllCompositeTests and sets store.testReport', async () => {
      const store = populateStore()
      const report = { totalTests: 2, passedTests: 1, failedTests: 1, executedAt: '2024-06-01', segmentResults: [] }
      mockRunAllCompositeTests.mockResolvedValue(report)
      const wrapper = mountTab()
      await flushPromises()
      const vm = wrapper.vm as any
      await vm.handleRunAll()
      await flushPromises()
      expect(mockRunAllCompositeTests).toHaveBeenCalledWith(1)
      expect(store.testReport).toEqual(report)
    })

    it('re-enables button on failure', async () => {
      populateStore()
      mockRunAllCompositeTests.mockRejectedValue(new Error('fail'))
      const wrapper = mountTab()
      await flushPromises()
      const vm = wrapper.vm as any
      await vm.handleRunAll()
      await flushPromises()
      expect(vm.runAllLoading).toBe(false)
    })
  })

  // ── Requirement 2.4: Single Run ──
  describe('single run', () => {
    it('calls runTestCase API', async () => {
      populateStore()
      mockRunTestCase.mockResolvedValue({ passed: true })
      const wrapper = mountTab()
      await flushPromises()
      const vm = wrapper.vm as any
      await vm.handleRunSingle(1)
      await flushPromises()
      expect(mockRunTestCase).toHaveBeenCalledWith(1)
      expect(vm.runningTestCaseId).toBeNull()
    })
  })

  // ── Requirement 3.1: Coverage display ──
  describe('coverage display', () => {
    it('shows coverage percentage and progress bar when variables exist', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()
      expect(wrapper.text()).toContain('75.0%')
      expect(wrapper.text()).toContain('11')
      expect(wrapper.text()).toContain('15')
    })

    it('shows coverage warning when below 100%', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()
      expect(wrapper.find('.el-alert--warning').exists()).toBe(true)
    })

    it('shows noVariables empty state when totalVariables is 0', async () => {
      populateStore({ coverage: { overallCoveragePercent: 0, segmentCoverages: [] } })
      const wrapper = mountTab()
      await flushPromises()
      // Should show el-empty in coverage section
      const empties = wrapper.findAll('.el-empty')
      expect(empties.length).toBeGreaterThanOrEqual(1)
    })

    it('shows green progress when coverage is 100%', async () => {
      populateStore({ coverage: { overallCoveragePercent: 100, segmentCoverages: [
        { segmentId: 1, segmentName: 'S1', totalVariables: 5, boundVariables: 5, coveragePercent: 100 },
      ] } })
      const wrapper = mountTab()
      await flushPromises()
      expect(wrapper.text()).toContain('100.0%')
      // No warning alert
      expect(wrapper.find('.el-alert--warning').exists()).toBe(false)
    })
  })

  // ── Requirement 3.6: Refresh Coverage ──
  describe('refresh coverage', () => {
    it('calls store.refreshCoverage', async () => {
      const store = populateStore()
      const spy = vi.spyOn(store, 'refreshCoverage').mockResolvedValue()
      mockGetTemplateCoverage.mockResolvedValue({ templateId: 1, totalVariables: 10, boundVariables: 10, unboundVariables: 0, coverageRate: 100, boundTags: [], unboundTags: [], unusedFields: [] })
      const wrapper = mountTab()
      await flushPromises()
      const vm = wrapper.vm as any
      await vm.handleRefreshCoverage()
      await flushPromises()
      expect(spy).toHaveBeenCalled()
    })
  })
})
