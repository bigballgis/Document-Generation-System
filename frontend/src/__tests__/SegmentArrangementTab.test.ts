import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { AssemblySegmentEntry, Segment, AssemblyConfig } from '@/types/segment'

// ── Mock API modules ──

const mockUpdateAssemblyConfig = vi.fn()
const mockCreateSegment = vi.fn()
const mockGetSegments = vi.fn()

vi.mock('@/api/composite-templates', () => ({
  getAssemblyConfig: vi.fn().mockResolvedValue({ segments: [] }),
  updateAssemblyConfig: (...args: any[]) => mockUpdateAssemblyConfig(...args),
  getCompositeCoverage: vi.fn().mockResolvedValue({ overallCoveragePercent: 0, segmentCoverages: [] }),
  getCompositeSegments: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/segments', () => ({
  createSegment: (...args: any[]) => mockCreateSegment(...args),
  getSegments: (...args: any[]) => mockGetSegments(...args),
}))

vi.mock('@/api/templates', () => ({
  getTemplate: vi.fn().mockResolvedValue({ id: 1, name: 'T', status: 'DRAFT', templateType: 'COMPOSITE', version: 1 }),
  getAvailableTransitions: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/data-sources', () => ({
  getDataSources: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/expressions', () => ({
  getExpressions: vi.fn().mockResolvedValue([]),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
  onBeforeRouteLeave: vi.fn(),
}))

// Stub KeyValueEditor to avoid deep rendering
vi.mock('@/views/data-sources/KeyValueEditor.vue', () => ({
  default: {
    template: '<div class="stub-kv-editor" />',
    props: ['modelValue'],
    emits: ['update:modelValue'],
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

import SegmentArrangementTab from '@/views/template-workspace/components/SegmentArrangementTab.vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'

// ── Test fixtures ──

const sampleEntries: AssemblySegmentEntry[] = [
  { segmentId: 1, position: 0, enabled: true, pageBreakBefore: false, lockedVersion: null, conditionExpression: null, dataScope: null },
  { segmentId: 2, position: 1, enabled: true, pageBreakBefore: true, lockedVersion: 3, conditionExpression: 'data.show', dataScope: null },
  { segmentId: 3, position: 2, enabled: false, pageBreakBefore: false, lockedVersion: null, conditionExpression: null, dataScope: { key1: 'val1' } },
]

const sampleSegments: Segment[] = [
  { id: 1, name: 'Cover Page', description: null, filePath: 'f1.docx', isComponent: false, segmentType: 'COVER', createdBy: 1, categoryId: null, tenantId: 1, createdAt: '2024-01-01', updatedAt: '2024-01-10' },
  { id: 2, name: 'Table of Contents', description: null, filePath: 'f2.docx', isComponent: false, segmentType: 'TOC', createdBy: 1, categoryId: null, tenantId: 1, createdAt: '2024-01-02', updatedAt: '2024-01-11' },
  { id: 3, name: 'Chapter One', description: null, filePath: 'f3.docx', isComponent: false, segmentType: 'CHAPTER', createdBy: 1, categoryId: null, tenantId: 1, createdAt: '2024-01-03', updatedAt: '2024-01-12' },
]

const sampleConfig: AssemblyConfig = { segments: sampleEntries }

// ── Helpers ──

function populateStore() {
  const store = useTemplateWorkspaceStore()
  store.templateId = 1
  store.assemblyConfig = { segments: [...sampleEntries.map(e => ({ ...e }))] }
  store.segments = [...sampleSegments]
  return store
}

function mountTab() {
  return mount(SegmentArrangementTab, {
    attachTo: document.body,
  })
}

describe('SegmentArrangementTab', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockUpdateAssemblyConfig.mockReset()
    mockCreateSegment.mockReset()
    mockGetSegments.mockReset()
    mockConfirm.mockReset()
    mockUpdateAssemblyConfig.mockResolvedValue({ segments: [] })
  })

  // ── Requirement 3.1: Store data merge rendering ──

  describe('merge rendering', () => {
    it('renders merged segment entries with names from store.segments', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const text = wrapper.text()
      expect(text).toContain('Cover Page')
      expect(text).toContain('Table of Contents')
      expect(text).toContain('Chapter One')
    })

    it('shows "Unknown Segment #id" when segment detail is not found', async () => {
      const store = useTemplateWorkspaceStore()
      store.templateId = 1
      store.assemblyConfig = {
        segments: [
          { segmentId: 999, position: 0, enabled: true, pageBreakBefore: false, lockedVersion: null, conditionExpression: null, dataScope: null },
        ],
      }
      store.segments = [] // no details

      const wrapper = mountTab()
      await flushPromises()

      expect(wrapper.text()).toContain('Unknown Segment #999')
    })

    it('renders segment type tags', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const text = wrapper.text()
      expect(text).toContain('COVER')
      expect(text).toContain('TOC')
      expect(text).toContain('CHAPTER')
    })

    it('renders position numbers starting from 1', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const positionNumbers = wrapper.findAll('.position-number')
      expect(positionNumbers.length).toBe(3)
      expect(positionNumbers[0].text()).toBe('1')
      expect(positionNumbers[1].text()).toBe('2')
      expect(positionNumbers[2].text()).toBe('3')
    })
  })

  // ── Requirement 3.7: Empty state ──

  describe('empty state', () => {
    it('shows empty state when assembly config has zero segments', async () => {
      const store = useTemplateWorkspaceStore()
      store.templateId = 1
      store.assemblyConfig = { segments: [] }
      store.segments = []

      const wrapper = mountTab()
      await flushPromises()

      expect(wrapper.find('.el-empty').exists()).toBe(true)
    })

    it('does not show empty state when segments exist', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      // segment-list should exist, el-empty should not (outside create form context)
      expect(wrapper.find('.segment-list').exists()).toBe(true)
    })
  })

  // ── Requirement 3.2: Drag & drop reorder ──

  describe('drag and drop', () => {
    it('applies drag classes on drag start', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const cards = wrapper.findAll('.segment-card')
      await cards[0].trigger('dragstart')
      await flushPromises()

      expect(cards[0].classes()).toContain('is-dragging')
    })

    it('applies drop target class on drag over', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const cards = wrapper.findAll('.segment-card')
      await cards[0].trigger('dragstart')
      await cards[2].trigger('dragover')
      await flushPromises()

      expect(cards[2].classes()).toContain('is-drop-target')
    })
  })

  // ── Requirement 3.3 / 3.4: Save arrangement flow ──

  describe('save arrangement', () => {
    it('calls updateAssemblyConfig API and refreshes store on save', async () => {
      const store = populateStore()
      const refreshConfigSpy = vi.spyOn(store, 'refreshAssemblyConfig').mockResolvedValue()
      const refreshSegmentsSpy = vi.spyOn(store, 'refreshSegments').mockResolvedValue()
      const refreshCoverageSpy = vi.spyOn(store, 'refreshCoverage').mockResolvedValue()
      mockUpdateAssemblyConfig.mockResolvedValue(sampleConfig)

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      await vm.handleSave()
      await flushPromises()

      expect(mockUpdateAssemblyConfig).toHaveBeenCalledWith(1, expect.objectContaining({ segments: expect.any(Array) }))
      expect(refreshConfigSpy).toHaveBeenCalled()
      expect(refreshSegmentsSpy).toHaveBeenCalled()
      expect(refreshCoverageSpy).toHaveBeenCalled()
    })

    it('shows error message and retains local state when save fails', async () => {
      populateStore()
      mockUpdateAssemblyConfig.mockRejectedValue(new Error('Network error'))

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      await vm.handleSave()
      await flushPromises()

      // saving should be false after failure
      expect(vm.saving).toBe(false)
      // segments should still be present (local state retained)
      expect(vm.mergedSegments.length).toBe(3)
    })
  })

  // ── Requirement 3.5 / 3.6: Keyboard shortcuts & undo/redo ──

  describe('keyboard shortcuts', () => {
    it('Ctrl+Z triggers undo', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      // Make a change first so undo has something to revert
      vm.assemblyConfig.removeSegment(0)
      await flushPromises()
      expect(vm.mergedSegments.length).toBe(2)

      // Trigger Ctrl+Z
      const event = new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true })
      document.dispatchEvent(event)
      await flushPromises()

      expect(vm.mergedSegments.length).toBe(3)
    })

    it('Ctrl+Shift+Z triggers redo', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      // Make a change and undo it
      vm.assemblyConfig.removeSegment(0)
      await flushPromises()
      vm.assemblyConfig.undo()
      await flushPromises()
      expect(vm.mergedSegments.length).toBe(3)

      // Trigger Ctrl+Shift+Z
      const event = new KeyboardEvent('keydown', { key: 'Z', ctrlKey: true, shiftKey: true, bubbles: true })
      document.dispatchEvent(event)
      await flushPromises()

      expect(vm.mergedSegments.length).toBe(2)
    })

    it('Alt+ArrowUp moves selected segment up', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      // Select the second segment
      vm.selectedIndex = 1
      await flushPromises()

      const event = new KeyboardEvent('keydown', { key: 'ArrowUp', altKey: true, bubbles: true })
      document.dispatchEvent(event)
      await flushPromises()

      // The second segment (Table of Contents) should now be at position 0
      expect(vm.mergedSegments[0].name).toBe('Table of Contents')
      expect(vm.selectedIndex).toBe(0)
    })

    it('Alt+ArrowDown moves selected segment down', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      // Select the first segment
      vm.selectedIndex = 0
      await flushPromises()

      const event = new KeyboardEvent('keydown', { key: 'ArrowDown', altKey: true, bubbles: true })
      document.dispatchEvent(event)
      await flushPromises()

      // The first segment (Cover Page) should now be at position 1
      expect(vm.mergedSegments[1].name).toBe('Cover Page')
      expect(vm.selectedIndex).toBe(1)
    })
  })

  // ── Requirement 3.8: Unsaved changes indicator ──

  describe('unsaved changes detection', () => {
    it('hasUnsavedChanges is false after initial mount', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      expect(vm.hasUnsavedChanges).toBe(false)
    })

    it('hasUnsavedChanges becomes true after a modification', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      vm.assemblyConfig.removeSegment(0)
      await flushPromises()

      expect(vm.hasUnsavedChanges).toBe(true)
    })

    it('shows save badge when there are unsaved changes', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      vm.assemblyConfig.removeSegment(0)
      await flushPromises()

      expect(wrapper.find('.save-badge').exists()).toBe(true)
    })
  })

  // ── Requirement 5.1: Expand config panel ──

  describe('inline config panel', () => {
    it('toggles config panel when expand button is clicked', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      // Initially no config panel
      expect(wrapper.find('.config-panel').exists()).toBe(false)

      // Click expand on first card
      const expandBtn = wrapper.findAll('.card-actions .el-button').filter(b => b.text().includes('Expand') || b.text().includes('expand'))
      expect(expandBtn.length).toBeGreaterThan(0)
      await expandBtn[0].trigger('click')
      await flushPromises()

      expect(wrapper.find('.config-panel').exists()).toBe(true)
      expect(wrapper.find('.segment-card.is-expanded').exists()).toBe(true)
    })

    it('collapses config panel when clicking expand again', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      vm.expandedIndex = 0
      await flushPromises()
      expect(wrapper.find('.config-panel').exists()).toBe(true)

      // Click collapse
      vm.toggleExpand(0)
      await flushPromises()
      expect(vm.expandedIndex).toBeNull()
    })
  })

  // ── Requirement 4.1-4.3: Create new segment ──

  describe('create new segment', () => {
    it('toggles create form visibility', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      expect(wrapper.find('.create-segment-form').exists()).toBe(false)

      // Click "Create New" button
      const createBtn = wrapper.findAll('.header-actions .el-button').filter(b => {
        const text = b.text().toLowerCase()
        return text.includes('create') || text.includes('new')
      })
      expect(createBtn.length).toBeGreaterThan(0)
      await createBtn[0].trigger('click')
      await flushPromises()

      expect(wrapper.find('.create-segment-form').exists()).toBe(true)
    })

    it('calls createSegment API and adds to assembly config on submit', async () => {
      const store = populateStore()
      vi.spyOn(store, 'refreshAssemblyConfig').mockResolvedValue()
      vi.spyOn(store, 'refreshSegments').mockResolvedValue()
      mockCreateSegment.mockResolvedValue({ id: 10, name: 'New Seg', segmentType: 'CHAPTER' })
      mockUpdateAssemblyConfig.mockResolvedValue(sampleConfig)

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      vm.createFormVisible = true
      vm.newSegmentForm.name = 'New Seg'
      vm.newSegmentForm.segmentType = 'CHAPTER'
      await flushPromises()

      // Mock form validation to pass
      vm.createFormRef = { validate: vi.fn().mockResolvedValue(true), resetFields: vi.fn() }
      await vm.handleCreateSegment()
      await flushPromises()

      expect(mockCreateSegment).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'New Seg', segmentType: 'CHAPTER' }),
        undefined,
      )
      expect(mockUpdateAssemblyConfig).toHaveBeenCalled()
    })

    it('shows warning when segment created but assembly save fails', async () => {
      const store = populateStore()
      vi.spyOn(store, 'refreshAssemblyConfig').mockResolvedValue()
      vi.spyOn(store, 'refreshSegments').mockResolvedValue()
      mockCreateSegment.mockResolvedValue({ id: 10, name: 'New Seg' })
      mockUpdateAssemblyConfig.mockRejectedValue(new Error('Save failed'))

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      vm.createFormVisible = true
      vm.newSegmentForm.name = 'New Seg'
      vm.createFormRef = { validate: vi.fn().mockResolvedValue(true), resetFields: vi.fn() }

      await vm.handleCreateSegment()
      await flushPromises()

      // Should not throw, segment was created but assembly save failed
      expect(mockCreateSegment).toHaveBeenCalled()
      expect(vm.creating).toBe(false)
    })
  })

  // ── Requirement 4.4-4.5: Add existing segment ──

  describe('add existing segment', () => {
    it('opens existing segment dialog', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      vm.openExistingSegmentDialog()
      await flushPromises()

      expect(vm.existingSegmentDialogVisible).toBe(true)
    })

    it('filters out already-present segments from search results', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      // Simulate search results that include segments already in config (ids 1, 2, 3)
      vm.searchResults = [
        { id: 1, name: 'Cover Page' },
        { id: 4, name: 'New Segment' },
        { id: 5, name: 'Another Segment' },
      ]
      await flushPromises()

      // filteredSearchResults should exclude id=1 (already in config)
      expect(vm.filteredSearchResults.length).toBe(2)
      expect(vm.filteredSearchResults.map((s: any) => s.id)).toEqual([4, 5])
    })

    it('calls getSegments API when searching', async () => {
      populateStore()
      mockGetSegments.mockResolvedValue({ content: [], totalElements: 0 })

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      vm.searchQuery = 'test'
      await vm.searchExistingSegments()
      await flushPromises()

      expect(mockGetSegments).toHaveBeenCalledWith(expect.objectContaining({
        keyword: 'test',
        page: expect.any(Number),
        size: 20,
      }))
    })

    it('adds selected segments and saves assembly config', async () => {
      const store = populateStore()
      vi.spyOn(store, 'refreshAssemblyConfig').mockResolvedValue()
      vi.spyOn(store, 'refreshSegments').mockResolvedValue()
      vi.spyOn(store, 'refreshCoverage').mockResolvedValue()
      mockUpdateAssemblyConfig.mockResolvedValue(sampleConfig)

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      vm.selectedExistingSegments = [
        { id: 10, name: 'Seg A' },
        { id: 11, name: 'Seg B' },
      ]

      await vm.handleAddExisting()
      await flushPromises()

      expect(mockUpdateAssemblyConfig).toHaveBeenCalled()
    })
  })

  // ── Requirement 5.3: Remove segment ──

  describe('remove segment', () => {
    it('shows confirm dialog and removes segment on confirmation', async () => {
      populateStore()
      mockConfirm.mockResolvedValue('confirm')

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      expect(vm.mergedSegments.length).toBe(3)

      await vm.handleRemove(0)
      await flushPromises()

      expect(mockConfirm).toHaveBeenCalled()
      expect(vm.mergedSegments.length).toBe(2)
      // First segment should now be "Table of Contents"
      expect(vm.mergedSegments[0].name).toBe('Table of Contents')
    })

    it('does not remove segment when user cancels confirmation', async () => {
      populateStore()
      mockConfirm.mockRejectedValue('cancel')

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      await vm.handleRemove(0)
      await flushPromises()

      expect(vm.mergedSegments.length).toBe(3)
    })

    it('marks arrangement as having unsaved changes after removal', async () => {
      populateStore()
      mockConfirm.mockResolvedValue('confirm')

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      expect(vm.hasUnsavedChanges).toBe(false)

      await vm.handleRemove(0)
      await flushPromises()

      expect(vm.hasUnsavedChanges).toBe(true)
    })
  })
})
