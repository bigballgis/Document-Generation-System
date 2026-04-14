import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

// ── Mock API modules ──
const mockExportZip = vi.fn()
const mockExportConfig = vi.fn()
const mockImportZip = vi.fn()
const mockImportConfig = vi.fn()
const mockUpdateDataSource = vi.fn()

vi.mock('@/api/composite-templates', () => ({
  exportCompositeAsZip: (...args: any[]) => mockExportZip(...args),
  exportCompositeConfig: (...args: any[]) => mockExportConfig(...args),
  importCompositeFromZip: (...args: any[]) => mockImportZip(...args),
  getAssemblyConfig: vi.fn().mockResolvedValue({ segments: [] }),
  getCompositeCoverage: vi.fn().mockResolvedValue({ overallCoveragePercent: 0, segmentCoverages: [] }),
  getCompositeSegments: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/import-export', () => ({
  importConfig: (...args: any[]) => mockImportConfig(...args),
}))

vi.mock('@/api/data-sources', () => ({
  getDataSources: vi.fn().mockResolvedValue([]),
  updateDataSource: (...args: any[]) => mockUpdateDataSource(...args),
}))

vi.mock('@/api/templates', () => ({
  getTemplate: vi.fn().mockResolvedValue({ id: 1, name: 'T', status: 'DRAFT', templateType: 'COMPOSITE', version: 1, tenantId: 1 }),
  getAvailableTransitions: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/expressions', () => ({ getExpressions: vi.fn().mockResolvedValue([]) }))
vi.mock('@/api/market', () => ({ getTestCases: vi.fn().mockResolvedValue([]) }))
vi.mock('@/api/admin', () => ({
  getTemplateReviews: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
  onBeforeRouteLeave: vi.fn(),
}))

import ExportImportTab from '@/views/template-workspace/components/ExportImportTab.vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'

function populateStore(overrides: Record<string, any> = {}) {
  const store = useTemplateWorkspaceStore()
  store.templateId = 1
  store.template = {
    id: 1, name: 'Test', status: overrides.status || 'ACTIVE', templateType: 'COMPOSITE',
    version: 1, description: '', tenantId: 1, createdAt: '', updatedAt: '',
    categoryId: null, tags: [], outputFormat: 'DOCX', reviewRequired: false,
  }
  store.assemblyConfig = { segments: overrides.segments || [{ filePath: 'f1.docx', name: 'S1' }, { filePath: 'f2.docx', name: 'S2' }] } as any
  store.dataSources = overrides.dataSources || [{ id: 1 }] as any
  store.expressions = overrides.expressions || [{ id: 1 }, { id: 2 }] as any
  store.testCases = overrides.testCases || [{ id: 1 }] as any
  store.loading = false
  store.criticalError = null
  return store
}

describe('ExportImportTab', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockExportZip.mockReset()
    mockExportConfig.mockReset()
    mockImportZip.mockReset()
    mockImportConfig.mockReset()
    mockUpdateDataSource.mockReset()
  })

  it('renders export and import sections with divider', async () => {
    populateStore()
    const wrapper = mount(ExportImportTab)
    await flushPromises()

    const sections = wrapper.findAll('.section')
    expect(sections.length).toBe(2)
    expect(wrapper.find('.el-divider').exists()).toBe(true)
  })

  it('displays 4 export summary statistics', async () => {
    populateStore({ segments: [{ filePath: 'f1.docx', name: 'S1' }, { filePath: 'f2.docx', name: 'S2' }, { filePath: 'f3.docx', name: 'S3' }], dataSources: [{ id: 1 }], expressions: [{ id: 1 }, { id: 2 }], testCases: [{ id: 1 }] })
    const wrapper = mount(ExportImportTab)
    await flushPromises()

    const statCards = wrapper.findAll('.stat-card')
    expect(statCards.length).toBe(4)
    expect(statCards[0].find('.stat-value').text()).toBe('3') // segments
    expect(statCards[1].find('.stat-value').text()).toBe('1') // dataSources
    expect(statCards[2].find('.stat-value').text()).toBe('2') // expressions
    expect(statCards[3].find('.stat-value').text()).toBe('1') // testCases
  })

  it('disables Export Complete Package button when status is DRAFT', async () => {
    populateStore({ status: 'DRAFT' })
    const wrapper = mount(ExportImportTab)
    await flushPromises()

    const buttons = wrapper.findAll('.export-actions .el-button')
    const exportPackageBtn = buttons[0]
    expect(exportPackageBtn.attributes('disabled')).toBeDefined()
  })

  it('enables Export Complete Package button when status is ACTIVE', async () => {
    populateStore({ status: 'ACTIVE' })
    const wrapper = mount(ExportImportTab)
    await flushPromises()

    const buttons = wrapper.findAll('.export-actions .el-button')
    const exportPackageBtn = buttons[0]
    expect(exportPackageBtn.attributes('disabled')).toBeUndefined()
  })

  it('calls exportCompositeAsZip on Export Complete Package click', async () => {
    populateStore({ status: 'ACTIVE' })
    mockExportZip.mockResolvedValue(new Blob(['test']))
    const wrapper = mount(ExportImportTab)
    await flushPromises()

    const buttons = wrapper.findAll('.export-actions .el-button')
    await buttons[0].trigger('click')
    await flushPromises()

    expect(mockExportZip).toHaveBeenCalledWith(1)
  })

  it('calls exportCompositeConfig on Export Config Only click', async () => {
    populateStore({ status: 'ACTIVE' })
    mockExportConfig.mockResolvedValue(new Blob(['{}']))
    const wrapper = mount(ExportImportTab)
    await flushPromises()

    const buttons = wrapper.findAll('.export-actions .el-button')
    await buttons[1].trigger('click')
    await flushPromises()

    expect(mockExportConfig).toHaveBeenCalledWith(1)
  })
})
