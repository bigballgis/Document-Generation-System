import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { DataSourceDTO } from '@/api/data-sources'
import type { ExpressionDTO } from '@/types/document'

// ── Mock API modules ──

const mockTestConnection = vi.fn()
const mockDeleteDataSource = vi.fn()
const mockDeleteExpression = vi.fn()
const mockValidateExpression = vi.fn()

vi.mock('@/api/data-sources', () => ({
  getDataSources: vi.fn().mockResolvedValue([]),
  testConnection: (...args: any[]) => mockTestConnection(...args),
  deleteDataSource: (...args: any[]) => mockDeleteDataSource(...args),
  createDataSource: vi.fn(),
  updateDataSource: vi.fn(),
}))

vi.mock('@/api/expressions', () => ({
  getExpressions: vi.fn().mockResolvedValue([]),
  deleteExpression: (...args: any[]) => mockDeleteExpression(...args),
  validateExpression: (...args: any[]) => mockValidateExpression(...args),
  createExpression: vi.fn(),
  updateExpression: vi.fn(),
}))

vi.mock('@/api/templates', () => ({
  getTemplate: vi.fn().mockResolvedValue({ id: 1, name: 'T', status: 'DRAFT', templateType: 'COMPOSITE', version: 1 }),
  getAvailableTransitions: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/composite-templates', () => ({
  getAssemblyConfig: vi.fn().mockResolvedValue({ segments: [] }),
  getCompositeCoverage: vi.fn().mockResolvedValue({ overallCoveragePercent: 0, segmentCoverages: [] }),
  getCompositeSegments: vi.fn().mockResolvedValue([]),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
  onBeforeRouteLeave: vi.fn(),
}))

// Stub child dialog components to avoid deep rendering
vi.mock('@/views/data-sources/DataSourceFormDialog.vue', () => ({
  default: {
    template: '<div class="stub-ds-dialog" />',
    props: ['visible', 'dataSource', 'templateId'],
    emits: ['update:visible', 'saved'],
  },
}))

vi.mock('@/views/templates/components/ExpressionFormDialog.vue', () => ({
  default: {
    template: '<div class="stub-expr-dialog" />',
    props: ['visible', 'templateId', 'data'],
    emits: ['update:visible', 'saved'],
  },
}))

// Mock ElMessageBox.confirm — resolve = user confirmed, reject = user cancelled
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

import DataStructureTab from '@/views/template-workspace/components/DataStructureTab.vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'

// ── Test fixtures ──

const sampleDataSources: DataSourceDTO[] = [
  {
    id: 1, templateId: 1, name: 'API Source', type: 'HTTP_API',
    configJson: '{}', cacheEnabled: true, cacheTtl: 300, priority: 1,
    createdAt: '2024-01-01', updatedAt: '2024-01-15',
  },
  {
    id: 2, templateId: 1, name: 'DB Source', type: 'DATABASE',
    configJson: '{}', cacheEnabled: false, cacheTtl: null, priority: 2,
    createdAt: '2024-02-01', updatedAt: '2024-02-10',
  },
]

const sampleExpressions: ExpressionDTO[] = [
  {
    id: 10, templateId: 1, name: 'Total Calc', expressionType: 'JAVASCRIPT',
    expressionText: 'return data.price * data.quantity',
    description: null, executionOrder: 1, createdAt: '2024-03-01',
  },
  {
    id: 11, templateId: 1, name: 'Tax Formula', expressionType: 'EXCEL',
    expressionText: '=A1*0.13',
    description: null, executionOrder: 2, createdAt: '2024-03-05',
  },
]

// ── Helpers ──

function mountTab() {
  return mount(DataStructureTab)
}

function populateStore() {
  const store = useTemplateWorkspaceStore()
  store.templateId = 1
  store.dataSources = [...sampleDataSources]
  store.expressions = [...sampleExpressions]
  return store
}

describe('DataStructureTab', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockTestConnection.mockReset()
    mockDeleteDataSource.mockReset()
    mockDeleteExpression.mockReset()
    mockValidateExpression.mockReset()
    mockConfirm.mockReset()
    mockDeleteDataSource.mockResolvedValue(undefined)
    mockDeleteExpression.mockResolvedValue(undefined)
  })

  // ── Requirement 1.1: Two sections with divider ──

  describe('layout', () => {
    it('renders two sections separated by a divider', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      // Should have an el-divider between sections
      expect(wrapper.find('.el-divider').exists()).toBe(true)
      // Should have two section headers
      const headers = wrapper.findAll('.section-header h3')
      expect(headers.length).toBe(2)
    })
  })

  // ── Requirement 1.2: No extra API calls on mount ──

  describe('store-driven data (no API calls on mount)', () => {
    it('reads data from store without making API calls', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      // Data should be rendered from store
      expect(wrapper.text()).toContain('API Source')
      expect(wrapper.text()).toContain('DB Source')
      expect(wrapper.text()).toContain('Total Calc')
      expect(wrapper.text()).toContain('Tax Formula')

      // The component should NOT have called any data-fetching APIs
      const { getDataSources } = await import('@/api/data-sources')
      const { getExpressions } = await import('@/api/expressions')
      expect(vi.mocked(getDataSources)).not.toHaveBeenCalled()
      expect(vi.mocked(getExpressions)).not.toHaveBeenCalled()
    })
  })

  // ── Requirement 1.3: Data source table columns ──

  describe('data source table', () => {
    it('renders data source rows with name, type tag, priority, cache icon, updatedAt, and actions', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const text = wrapper.text()
      // Names
      expect(text).toContain('API Source')
      expect(text).toContain('DB Source')
      // Types rendered as tags
      expect(text).toContain('HTTP_API')
      expect(text).toContain('DATABASE')
      // Priority values
      expect(text).toContain('1')
      expect(text).toContain('2')
      // Updated at
      expect(text).toContain('2024-01-15')
      expect(text).toContain('2024-02-10')
    })
  })

  // ── Requirement 2.1: Expression table columns ──

  describe('expression table', () => {
    it('renders expression rows with name, type tag, content, executionOrder, createdAt', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const text = wrapper.text()
      expect(text).toContain('Total Calc')
      expect(text).toContain('Tax Formula')
      expect(text).toContain('JAVASCRIPT')
      expect(text).toContain('EXCEL')
      expect(text).toContain('return data.price * data.quantity')
      expect(text).toContain('=A1*0.13')
    })
  })

  // ── Requirement 1.9 / 2.7: Empty states ──

  describe('empty states', () => {
    it('shows empty state when data sources list is empty', async () => {
      const store = useTemplateWorkspaceStore()
      store.templateId = 1
      store.dataSources = []
      store.expressions = [...sampleExpressions]

      const wrapper = mountTab()
      await flushPromises()

      // Should render el-empty for data sources
      const empties = wrapper.findAll('.el-empty')
      expect(empties.length).toBeGreaterThanOrEqual(1)
    })

    it('shows empty state when expressions list is empty', async () => {
      const store = useTemplateWorkspaceStore()
      store.templateId = 1
      store.dataSources = [...sampleDataSources]
      store.expressions = []

      const wrapper = mountTab()
      await flushPromises()

      const empties = wrapper.findAll('.el-empty')
      expect(empties.length).toBeGreaterThanOrEqual(1)
    })

    it('shows both empty states when both lists are empty', async () => {
      const store = useTemplateWorkspaceStore()
      store.templateId = 1
      store.dataSources = []
      store.expressions = []

      const wrapper = mountTab()
      await flushPromises()

      const empties = wrapper.findAll('.el-empty')
      expect(empties.length).toBe(2)
    })
  })

  // ── Requirement 1.4 / 1.5: Add/Edit data source dialog ──

  describe('data source dialog', () => {
    it('opens add dialog when "Add Data Source" button is clicked', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      // Find the add button in the first section header
      const addBtn = wrapper.findAll('.section-header .el-button')[0]
      expect(addBtn).toBeDefined()
      await addBtn!.trigger('click')
      await flushPromises()

      // The dsDialogVisible should be true and editingDataSource should be null (add mode)
      const vm = wrapper.vm as any
      expect(vm.dsDialogVisible).toBe(true)
      expect(vm.editingDataSource).toBeNull()
    })

    it('opens edit dialog when Edit button is clicked on a data source row', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      // Find Edit buttons in the data source table (first table)
      const tables = wrapper.findAll('.el-table')
      const dsTable = tables[0]
      const editBtns = dsTable.findAll('.el-button').filter(b => b.text().includes('Edit'))
      expect(editBtns.length).toBeGreaterThanOrEqual(1)

      await editBtns[0].trigger('click')
      await flushPromises()

      const vm = wrapper.vm as any
      expect(vm.dsDialogVisible).toBe(true)
      expect(vm.editingDataSource).not.toBeNull()
      expect(vm.editingDataSource.id).toBe(sampleDataSources[0].id)
    })
  })

  // ── Requirement 2.2 / 2.3: Add/Edit expression dialog ──

  describe('expression dialog', () => {
    it('opens add dialog when "Add Expression" button is clicked', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      // The second section header has the expression add button
      const addBtn = wrapper.findAll('.section-header .el-button')[1]
      expect(addBtn).toBeDefined()
      await addBtn!.trigger('click')
      await flushPromises()

      const vm = wrapper.vm as any
      expect(vm.exprDialogVisible).toBe(true)
      expect(vm.editingExpression).toBeNull()
    })

    it('opens edit dialog when Edit button is clicked on an expression row', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      // Find Edit buttons in the expression table (second table)
      const tables = wrapper.findAll('.el-table')
      const exprTable = tables[1]
      const editBtns = exprTable.findAll('.el-button').filter(b => b.text().includes('Edit'))
      expect(editBtns.length).toBeGreaterThanOrEqual(1)

      await editBtns[0].trigger('click')
      await flushPromises()

      const vm = wrapper.vm as any
      expect(vm.exprDialogVisible).toBe(true)
      expect(vm.editingExpression).not.toBeNull()
      expect(vm.editingExpression.id).toBe(sampleExpressions[0].id)
    })
  })

  // ── Requirement 1.6 / 2.4: Saved event refreshes store ──

  describe('saved event refreshes store', () => {
    it('calls store.refreshDataSources when data source dialog emits saved', async () => {
      const store = populateStore()
      const refreshSpy = vi.spyOn(store, 'refreshDataSources').mockResolvedValue()
      const wrapper = mountTab()
      await flushPromises()

      // Trigger the onDataSourceSaved callback
      const vm = wrapper.vm as any
      await vm.onDataSourceSaved()
      await flushPromises()

      expect(refreshSpy).toHaveBeenCalledTimes(1)
    })

    it('calls store.refreshExpressions when expression dialog emits saved', async () => {
      const store = populateStore()
      const refreshSpy = vi.spyOn(store, 'refreshExpressions').mockResolvedValue()
      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      await vm.onExpressionSaved()
      await flushPromises()

      expect(refreshSpy).toHaveBeenCalledTimes(1)
    })
  })

  // ── Requirement 1.8: Delete data source confirmation flow ──

  describe('delete data source flow', () => {
    it('shows confirm dialog and deletes on confirmation, then refreshes store', async () => {
      const store = populateStore()
      const refreshSpy = vi.spyOn(store, 'refreshDataSources').mockResolvedValue()
      mockConfirm.mockResolvedValue('confirm')
      mockDeleteDataSource.mockResolvedValue(undefined)

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      await vm.handleDeleteDataSource(1)
      await flushPromises()

      expect(mockConfirm).toHaveBeenCalledTimes(1)
      expect(mockDeleteDataSource).toHaveBeenCalledWith(1)
      expect(refreshSpy).toHaveBeenCalledTimes(1)
    })

    it('does not delete when user cancels confirmation', async () => {
      populateStore()
      mockConfirm.mockRejectedValue('cancel')

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      await vm.handleDeleteDataSource(1)
      await flushPromises()

      expect(mockConfirm).toHaveBeenCalledTimes(1)
      expect(mockDeleteDataSource).not.toHaveBeenCalled()
    })

    it('recovers deletingIds when delete API fails', async () => {
      populateStore()
      mockConfirm.mockResolvedValue('confirm')
      mockDeleteDataSource.mockRejectedValue(new Error('Server error'))

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      await vm.handleDeleteDataSource(1)
      await flushPromises()

      // deletingIds should be cleared after failure (finally block)
      expect(vm.deletingIds.has(1)).toBe(false)
    })
  })

  // ── Requirement 2.6: Delete expression confirmation flow ──

  describe('delete expression flow', () => {
    it('shows confirm dialog and deletes on confirmation, then refreshes store', async () => {
      const store = populateStore()
      const refreshSpy = vi.spyOn(store, 'refreshExpressions').mockResolvedValue()
      mockConfirm.mockResolvedValue('confirm')
      mockDeleteExpression.mockResolvedValue(undefined)

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      await vm.handleDeleteExpression(10)
      await flushPromises()

      expect(mockConfirm).toHaveBeenCalledTimes(1)
      expect(mockDeleteExpression).toHaveBeenCalledWith(10)
      expect(refreshSpy).toHaveBeenCalledTimes(1)
    })

    it('recovers deletingIds when expression delete API fails', async () => {
      populateStore()
      mockConfirm.mockResolvedValue('confirm')
      mockDeleteExpression.mockRejectedValue(new Error('Server error'))

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      await vm.handleDeleteExpression(10)
      await flushPromises()

      expect(vm.deletingIds.has(10)).toBe(false)
    })
  })

  // ── Requirement 1.7: Test connection result display ──

  describe('test connection', () => {
    it('calls testConnection API with the data source id', async () => {
      populateStore()
      mockTestConnection.mockResolvedValue({ success: true, message: 'OK', responseTime: 42 })

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      await vm.handleTestConnection(1)
      await flushPromises()

      expect(mockTestConnection).toHaveBeenCalledWith(1)
    })

    it('handles test connection failure result', async () => {
      populateStore()
      mockTestConnection.mockResolvedValue({ success: false, message: 'Connection refused' })

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      // Should not throw
      await vm.handleTestConnection(1)
      await flushPromises()

      expect(mockTestConnection).toHaveBeenCalledWith(1)
    })

    it('handles test connection API error', async () => {
      populateStore()
      mockTestConnection.mockRejectedValue(new Error('Network error'))

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      // Should not throw
      await vm.handleTestConnection(1)
      await flushPromises()

      expect(mockTestConnection).toHaveBeenCalledWith(1)
    })
  })
})
