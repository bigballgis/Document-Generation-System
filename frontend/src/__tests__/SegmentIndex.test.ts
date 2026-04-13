import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

// Mock the segments API
vi.mock('@/api/segments', () => ({
  getSegments: vi.fn().mockResolvedValue({
    content: [
      {
        id: 1,
        name: 'Cover Page',
        description: 'A cover page segment',
        filePath: 'segments/1/uuid_cover.docx',
        isComponent: false,
        segmentType: 'COVER',
        createdBy: 1,
        categoryId: 1,
        tenantId: 1,
        createdAt: '2024-01-01',
        updatedAt: '2024-01-15',
      },
      {
        id: 2,
        name: 'Legal Disclaimer',
        description: 'Standard legal text',
        filePath: 'segments/1/uuid_legal.docx',
        isComponent: true,
        segmentType: 'LEGAL',
        createdBy: 1,
        categoryId: null,
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
  deleteSegment: vi.fn().mockResolvedValue(undefined),
  cloneSegment: vi.fn().mockResolvedValue({}),
  promoteToComponent: vi.fn().mockResolvedValue({}),
  demoteFromComponent: vi.fn().mockResolvedValue({}),
  addSegmentFavorite: vi.fn().mockResolvedValue(undefined),
  removeSegmentFavorite: vi.fn().mockResolvedValue(undefined),
  createSegment: vi.fn().mockResolvedValue({}),
  updateSegment: vi.fn().mockResolvedValue({}),
}))

vi.mock('@/api/templates', () => ({
  getCategories: vi.fn().mockResolvedValue([
    { id: 1, name: 'Legal', parentId: null, sortOrder: 0, children: [] },
  ]),
  getTags: vi.fn().mockResolvedValue([
    { id: 1, name: 'contract' },
  ]),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {} }),
}))

vi.mock('@/views/segments/components/SegmentFormDialog.vue', () => ({
  default: { template: '<div class="stub-form-dialog" />', props: ['visible', 'segmentData', 'categories', 'tags'] },
}))

import SegmentIndex from '@/views/segments/Index.vue'
import { getSegments } from '@/api/segments'

describe('Segment Index Page', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(getSegments).mockClear()
  })

  it('renders the page header with title and create button', async () => {
    const wrapper = mount(SegmentIndex)
    await flushPromises()

    expect(wrapper.find('.page-header h2').text()).toBe('Segment Management')
    const createBtn = wrapper.find('.page-header .el-button--primary')
    expect(createBtn.exists()).toBe(true)
  })

  it('fetches and displays segments in the table', async () => {
    const wrapper = mount(SegmentIndex)
    await flushPromises()

    expect(getSegments).toHaveBeenCalledTimes(1)
    const text = wrapper.text()
    expect(text).toContain('Cover Page')
    expect(text).toContain('Legal Disclaimer')
  })

  it('renders filter controls (search, category, tags, type)', async () => {
    const wrapper = mount(SegmentIndex)
    await flushPromises()

    const searchInput = wrapper.find('.filter-card .el-input')
    expect(searchInput.exists()).toBe(true)

    const selects = wrapper.findAll('.filter-card .el-select')
    expect(selects.length).toBeGreaterThanOrEqual(2) // tags + type
  })

  it('calls getSegments with page=1 when search button is clicked', async () => {
    const wrapper = mount(SegmentIndex)
    await flushPromises()
    vi.mocked(getSegments).mockClear()

    const buttons = wrapper.findAll('.filter-card .el-button')
    const searchBtn = buttons.find((b) => b.text() === 'Search')
    expect(searchBtn).toBeDefined()

    await searchBtn!.trigger('click')
    await flushPromises()

    expect(getSegments).toHaveBeenCalledWith(
      expect.objectContaining({ page: 1 }),
    )
  })

  it('resets filters when reset button is clicked', async () => {
    const wrapper = mount(SegmentIndex)
    await flushPromises()
    vi.mocked(getSegments).mockClear()

    const buttons = wrapper.findAll('.filter-card .el-button')
    const resetBtn = buttons.find((b) => b.text() === 'Reset')
    expect(resetBtn).toBeDefined()

    await resetBtn!.trigger('click')
    await flushPromises()

    expect(getSegments).toHaveBeenCalledWith(
      expect.objectContaining({ keyword: '', categoryId: null, tagId: null, segmentType: '', page: 1 }),
    )
  })

  it('shows component/normal tags for segments', async () => {
    const wrapper = mount(SegmentIndex)
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('Component')
    expect(text).toContain('Normal')
  })

  it('renders action buttons for each segment row', async () => {
    const wrapper = mount(SegmentIndex)
    await flushPromises()

    const actionButtons = wrapper.findAll('.el-table .el-button')
    expect(actionButtons.length).toBeGreaterThanOrEqual(4)
  })
})
