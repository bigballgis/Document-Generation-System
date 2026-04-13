import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

// Mock the templates API
vi.mock('@/api/templates', () => ({
  getTemplates: vi.fn().mockResolvedValue({
    content: [
      {
        id: 1,
        name: 'Contract Template',
        description: 'A standard contract',
        status: 'ACTIVE',
        categoryId: 1,
        categoryName: 'Legal',
        tags: [{ id: 1, name: 'contract' }],
        version: 3,
        outputFormat: 'WORD',
        reviewRequired: true,
        tenantId: 1,
        createdAt: '2024-01-01',
        updatedAt: '2024-01-15',
      },
      {
        id: 2,
        name: 'Invoice Template',
        description: 'Monthly invoice',
        status: 'DRAFT',
        categoryId: 2,
        categoryName: 'Finance',
        tags: [],
        version: 1,
        outputFormat: 'PDF',
        reviewRequired: false,
        tenantId: 1,
        createdAt: '2024-02-01',
        updatedAt: '2024-02-10',
      },
    ],
    totalElements: 2,
    totalPages: 1,
    size: 10,
    number: 0,
  }),
  getCategories: vi.fn().mockResolvedValue([
    { id: 1, name: 'Legal', parentId: null, sortOrder: 0, children: [] },
    { id: 2, name: 'Finance', parentId: null, sortOrder: 1, children: [] },
  ]),
  getTags: vi.fn().mockResolvedValue([
    { id: 1, name: 'contract' },
    { id: 2, name: 'report' },
  ]),
  deleteTemplate: vi.fn().mockResolvedValue(undefined),
  cloneTemplate: vi.fn().mockResolvedValue({}),
  activateTemplate: vi.fn().mockResolvedValue(undefined),
  archiveTemplate: vi.fn().mockResolvedValue(undefined),
}))

// Mock vue-router
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {} }),
}))

// Stub the TemplateFormDialog child component
vi.mock('@/views/templates/components/TemplateFormDialog.vue', () => ({
  default: { template: '<div class="stub-form-dialog" />', props: ['visible', 'templateData', 'categories', 'tags'] },
}))

import TemplateIndex from '@/views/templates/Index.vue'
import { getTemplates } from '@/api/templates'

describe('Template Index Page', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(getTemplates).mockClear()
  })

  it('renders the page header with title and create button', async () => {
    const wrapper = mount(TemplateIndex)
    await flushPromises()

    expect(wrapper.find('.page-header h2').text()).toBe('Template Management')
    const createBtn = wrapper.find('.page-header .el-button--primary')
    expect(createBtn.exists()).toBe(true)
    expect(createBtn.text()).toBe('Create Template')
  })

  it('fetches and displays templates in the table', async () => {
    const wrapper = mount(TemplateIndex)
    await flushPromises()

    expect(getTemplates).toHaveBeenCalledTimes(1)

    // Table should contain template names
    const tableText = wrapper.text()
    expect(tableText).toContain('Contract Template')
    expect(tableText).toContain('Invoice Template')
  })

  it('renders status tags for templates', async () => {
    const wrapper = mount(TemplateIndex)
    await flushPromises()

    const tags = wrapper.findAll('.el-tag')
    // At least the status tags + tag chips
    expect(tags.length).toBeGreaterThanOrEqual(2)
  })

  it('renders filter controls (search, category, status)', async () => {
    const wrapper = mount(TemplateIndex)
    await flushPromises()

    // Search input
    const searchInput = wrapper.find('.filter-card .el-input')
    expect(searchInput.exists()).toBe(true)

    // Status select
    const selects = wrapper.findAll('.filter-card .el-select')
    expect(selects.length).toBeGreaterThanOrEqual(1)
  })

  it('calls getTemplates with page=1 when search button is clicked', async () => {
    const wrapper = mount(TemplateIndex)
    await flushPromises()
    vi.mocked(getTemplates).mockClear()

    // Find the search button in the filter card
    const buttons = wrapper.findAll('.filter-card .el-button')
    const searchBtn = buttons.find((b) => b.text() === 'Search')
    expect(searchBtn).toBeDefined()

    await searchBtn!.trigger('click')
    await flushPromises()

    expect(getTemplates).toHaveBeenCalledWith(
      expect.objectContaining({ page: 1 }),
    )
  })

  it('renders action buttons for each template row', async () => {
    const wrapper = mount(TemplateIndex)
    await flushPromises()

    // Should have edit, clone, delete buttons for each row
    const actionButtons = wrapper.findAll('.el-table .el-button')
    expect(actionButtons.length).toBeGreaterThanOrEqual(4) // at least edit+clone+delete per row
  })

  it('shows activate button for DRAFT templates and archive for ACTIVE', async () => {
    const wrapper = mount(TemplateIndex)
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('Activate') // DRAFT row
    expect(text).toContain('Archive')  // ACTIVE row
  })
})
