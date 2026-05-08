import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const mockRollback = vi.fn()

vi.mock('@/api/templates', () => ({
  getTemplate: vi.fn().mockResolvedValue({ id: 1, name: 'T', status: 'DRAFT', templateType: 'COMPOSITE', version: 1, tenantId: 1 }),
  getAvailableTransitions: vi.fn().mockResolvedValue([]),
  getTemplateVersions: vi.fn().mockResolvedValue([]),
  rollbackVersion: (...args: any[]) => mockRollback(...args),
}))

vi.mock('@/api/composite-templates', () => ({
  getAssemblyConfig: vi.fn().mockResolvedValue({ segments: [] }),
  getCompositeCoverage: vi.fn().mockResolvedValue({ overallCoveragePercent: 0, segmentCoverages: [] }),
  getCompositeSegments: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/parameters', () => ({ getParameters: vi.fn().mockResolvedValue([]) }))

vi.mock('@/api/templateTesting', () => ({
  getTestCases: vi.fn().mockResolvedValue({
    content: [], totalElements: 0, totalPages: 0, size: 20, number: 0,
  }),
}))
vi.mock('@/api/admin', () => ({
  getTemplateReviews: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
  getTemplatePermissions: vi.fn().mockResolvedValue([]),
}))

const mockConfirm = vi.fn()
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<any>()
  return {
    ...actual,
    ElMessageBox: { ...actual.ElMessageBox, confirm: (...args: any[]) => mockConfirm(...args) },
  }
})

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
  onBeforeRouteLeave: vi.fn(),
}))

import VersionHistoryPanel from '@/views/template-workspace/components/VersionHistoryPanel.vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'

const sampleVersions = [
  { id: 10, templateId: 1, versionNumber: 1, createdAt: '2024-01-01', createdBy: 'admin', comment: 'Initial', configJson: '{}' },
  { id: 11, templateId: 1, versionNumber: 2, createdAt: '2024-02-01', createdBy: 'admin', comment: 'Update', configJson: '{}' },
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

describe('VersionHistoryPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockRollback.mockReset()
    mockConfirm.mockReset()
  })

  it('renders version table with correct columns', async () => {
    populateStore()
    const wrapper = mount(VersionHistoryPanel)
    await flushPromises()

    const table = wrapper.find('.el-table')
    expect(table.exists()).toBe(true)
    // Should have 2 rows of data
    const rows = wrapper.findAll('.el-table__body-wrapper tr')
    expect(rows.length).toBeGreaterThanOrEqual(2)
  })

  it('shows rollback confirmation dialog on button click', async () => {
    populateStore()
    mockConfirm.mockResolvedValue('confirm')
    mockRollback.mockResolvedValue({})
    const wrapper = mount(VersionHistoryPanel)
    await flushPromises()

    const rollbackBtns = wrapper.findAll('.el-table .el-button')
    expect(rollbackBtns.length).toBeGreaterThanOrEqual(1)

    await rollbackBtns[0].trigger('click')
    await flushPromises()

    expect(mockConfirm).toHaveBeenCalledTimes(1)
  })

  it('calls rollbackVersion API after confirmation', async () => {
    populateStore()
    mockConfirm.mockResolvedValue('confirm')
    mockRollback.mockResolvedValue({})
    const wrapper = mount(VersionHistoryPanel)
    await flushPromises()

    const rollbackBtns = wrapper.findAll('.el-table .el-button')
    await rollbackBtns[0].trigger('click')
    await flushPromises()

    expect(mockRollback).toHaveBeenCalledWith(1, 10)
  })

  it('does not call rollbackVersion when user cancels', async () => {
    populateStore()
    mockConfirm.mockRejectedValue('cancel')
    const wrapper = mount(VersionHistoryPanel)
    await flushPromises()

    const rollbackBtns = wrapper.findAll('.el-table .el-button')
    await rollbackBtns[0].trigger('click')
    await flushPromises()

    expect(mockRollback).not.toHaveBeenCalled()
  })
})
