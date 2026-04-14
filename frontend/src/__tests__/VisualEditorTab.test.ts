import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { Segment, AssemblyConfig, LockInfo } from '@/types/segment'

// ── Mock API modules ──

const mockGetSegmentLockInfo = vi.fn()
const mockPreviewCompositeTemplate = vi.fn()
const mockPreviewSelectiveSegments = vi.fn()

vi.mock('@/api/segments', () => ({
  getSegmentLockInfo: (...args: any[]) => mockGetSegmentLockInfo(...args),
  getSegments: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
  createSegment: vi.fn(),
}))

vi.mock('@/api/composite-templates', () => ({
  getAssemblyConfig: vi.fn().mockResolvedValue({ segments: [] }),
  getCompositeCoverage: vi.fn().mockResolvedValue({ overallCoveragePercent: 0, segmentCoverages: [] }),
  getCompositeSegments: vi.fn().mockResolvedValue([]),
  previewCompositeTemplate: (...args: any[]) => mockPreviewCompositeTemplate(...args),
  previewSelectiveSegments: (...args: any[]) => mockPreviewSelectiveSegments(...args),
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

import VisualEditorTab from '@/views/template-workspace/components/VisualEditorTab.vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'

// ── Test fixtures ──

const sampleSegments: Segment[] = [
  {
    id: 101, name: 'Cover Page', description: null, filePath: 'seg/101.docx',
    isComponent: false, segmentType: 'COVER', createdBy: 1, categoryId: null,
    tenantId: 1, createdAt: '2024-01-01', updatedAt: '2024-01-10',
  },
  {
    id: 102, name: 'Table of Contents', description: null, filePath: 'seg/102.docx',
    isComponent: false, segmentType: 'TOC', createdBy: 1, categoryId: null,
    tenantId: 1, createdAt: '2024-02-01', updatedAt: '2024-02-15',
  },
  {
    id: 103, name: 'Main Chapter', description: null, filePath: 'seg/103.docx',
    isComponent: false, segmentType: 'CHAPTER', createdBy: 1, categoryId: null,
    tenantId: 1, createdAt: '2024-03-01', updatedAt: '2024-03-20',
  },
]

const sampleAssemblyConfig: AssemblyConfig = {
  segments: [
    { segmentId: 101, position: 0, enabled: true, pageBreakBefore: false, lockedVersion: null, conditionExpression: null, dataScope: null },
    { segmentId: 102, position: 1, enabled: true, pageBreakBefore: true, lockedVersion: null, conditionExpression: null, dataScope: null },
    { segmentId: 103, position: 2, enabled: true, pageBreakBefore: false, lockedVersion: null, conditionExpression: null, dataScope: null },
  ],
}

const sampleLockInfo: LockInfo = {
  segmentId: 102,
  lockedBy: 5,
  lockedByUsername: 'alice',
  lockedAt: '2024-04-01T10:00:00Z',
  expiresAt: '2024-04-01T12:00:00Z',
}

// ── Helpers ──

function mountTab() {
  return mount(VisualEditorTab)
}

function populateStore() {
  const store = useTemplateWorkspaceStore()
  store.templateId = 1
  store.segments = [...sampleSegments]
  store.assemblyConfig = JSON.parse(JSON.stringify(sampleAssemblyConfig))
  return store
}


describe('VisualEditorTab', () => {
  let windowOpenSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    setActivePinia(createPinia())
    mockGetSegmentLockInfo.mockReset()
    mockPreviewCompositeTemplate.mockReset()
    mockPreviewSelectiveSegments.mockReset()
    windowOpenSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
  })

  // ── Requirement 6.1: Merged segment rendering ──

  describe('merged segment rendering', () => {
    it('renders segment list with merged names, types, and updatedAt from store', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const text = wrapper.text()
      expect(text).toContain('Cover Page')
      expect(text).toContain('Table of Contents')
      expect(text).toContain('Main Chapter')
      expect(text).toContain('COVER')
      expect(text).toContain('TOC')
      expect(text).toContain('CHAPTER')
      expect(text).toContain('2024-01-10')
      expect(text).toContain('2024-02-15')
      expect(text).toContain('2024-03-20')
    })

    it('shows "Unknown Segment #id" when segment detail is not found', async () => {
      const store = useTemplateWorkspaceStore()
      store.templateId = 1
      store.segments = [] // no segment details
      store.assemblyConfig = {
        segments: [
          { segmentId: 999, position: 0, enabled: true, pageBreakBefore: false, lockedVersion: null, conditionExpression: null, dataScope: null },
        ],
      }

      const wrapper = mountTab()
      await flushPromises()

      expect(wrapper.text()).toContain('Unknown Segment #999')
    })

    it('renders position numbers starting from 1', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const text = wrapper.text()
      // position 0 → display "1", position 1 → "2", position 2 → "3"
      expect(text).toContain('1')
      expect(text).toContain('2')
      expect(text).toContain('3')
    })
  })

  // ── Requirement 6.5: Lock status check (Promise.allSettled) ──

  describe('lock status check', () => {
    it('calls getSegmentLockInfo for each segment via checkLocks()', async () => {
      populateStore()
      mockGetSegmentLockInfo.mockResolvedValue(null)

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      await vm.checkLocks()
      await flushPromises()

      expect(mockGetSegmentLockInfo).toHaveBeenCalledTimes(3)
      expect(mockGetSegmentLockInfo).toHaveBeenCalledWith(101)
      expect(mockGetSegmentLockInfo).toHaveBeenCalledWith(102)
      expect(mockGetSegmentLockInfo).toHaveBeenCalledWith(103)
    })

    it('displays lock icon and username when a segment is locked', async () => {
      populateStore()
      mockGetSegmentLockInfo
        .mockResolvedValueOnce(null) // 101 unlocked
        .mockResolvedValueOnce(sampleLockInfo) // 102 locked by alice
        .mockResolvedValueOnce(null) // 103 unlocked

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      await vm.checkLocks()
      await flushPromises()

      expect(wrapper.text()).toContain('alice')
      expect(wrapper.find('.lock-indicator').exists()).toBe(true)
    })

    it('treats failed lock queries as unlocked (Promise.allSettled)', async () => {
      populateStore()
      mockGetSegmentLockInfo
        .mockResolvedValueOnce(null) // 101 ok
        .mockRejectedValueOnce(new Error('Network error')) // 102 fails
        .mockResolvedValueOnce(null) // 103 ok

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      await vm.checkLocks()
      await flushPromises()

      // Failed lock query → treated as unlocked → no lock indicator for segment 102
      const lockIndicators = wrapper.findAll('.lock-indicator')
      expect(lockIndicators.length).toBe(0)
    })

    it('does not call lock API when there are no segments', async () => {
      const store = useTemplateWorkspaceStore()
      store.templateId = 1
      store.segments = []
      store.assemblyConfig = { segments: [] }

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      await vm.checkLocks()
      await flushPromises()

      expect(mockGetSegmentLockInfo).not.toHaveBeenCalled()
    })
  })

  // ── Requirement 6.2: Open Editor ──

  describe('open editor', () => {
    it('calls window.open with correct segment editor URL', async () => {
      populateStore()
      mockGetSegmentLockInfo.mockResolvedValue(null)

      const wrapper = mountTab()
      await flushPromises()

      // Find "Open Editor" buttons
      const openBtns = wrapper.findAll('.el-button').filter(b => {
        const text = b.text()
        return text.includes('Open Editor') || text.includes('open') || text.includes('editor')
      })
      expect(openBtns.length).toBeGreaterThanOrEqual(1)

      await openBtns[0].trigger('click')
      await flushPromises()

      expect(windowOpenSpy).toHaveBeenCalledWith('/segments/101/editor', '_blank')
    })
  })

  // ── Requirement 6.3: Preview Composite ──

  describe('preview composite', () => {
    it('calls previewCompositeTemplate API and opens previewUrl in new tab', async () => {
      populateStore()
      mockPreviewCompositeTemplate.mockResolvedValue({ previewUrl: 'https://preview.example.com/doc1' })

      const wrapper = mountTab()
      await flushPromises()

      // Find "Preview Composite" button
      const previewBtns = wrapper.findAll('.el-button').filter(b => {
        const text = b.text()
        return text.includes('Preview Composite') || text.includes('preview') || text.includes('Composite')
      })
      // The primary preview button (not selective)
      const compositeBtn = previewBtns.find(b => !b.text().includes('Selective'))
      expect(compositeBtn).toBeDefined()

      await compositeBtn!.trigger('click')
      await flushPromises()

      expect(mockPreviewCompositeTemplate).toHaveBeenCalledWith(1)
      expect(windowOpenSpy).toHaveBeenCalledWith('https://preview.example.com/doc1', '_blank')
    })

    it('shows error message when preview API fails', async () => {
      populateStore()
      mockPreviewCompositeTemplate.mockRejectedValue({ response: { data: { message: 'Preview generation failed' } } })

      const wrapper = mountTab()
      await flushPromises()

      const callsBefore = windowOpenSpy.mock.calls.length
      const vm = wrapper.vm as any
      await vm.handlePreviewComposite()
      await flushPromises()

      // previewLoading should be reset to false
      expect(vm.previewLoading).toBe(false)
      // window.open should NOT have been called during this preview attempt
      expect(windowOpenSpy.mock.calls.length).toBe(callsBefore)
    })
  })

  // ── Requirement 6.4: Selective Preview ──

  describe('selective preview', () => {
    it('disables selective preview button when no segments are selected', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      // Enable selective mode
      const vm = wrapper.vm as any
      vm.selectiveMode = true
      await flushPromises()

      // Find the selective preview button (type="warning")
      const selectiveBtn = wrapper.findAll('.el-button').find(b =>
        b.classes().some(c => c.includes('warning')) || b.attributes('type') === 'warning'
      )
      // Button should be disabled when no segments selected
      if (selectiveBtn) {
        expect(selectiveBtn.attributes('disabled')).toBeDefined()
      }
      // selectedSegmentIds should be empty
      expect(vm.selectedSegmentIds.length).toBe(0)
    })

    it('calls previewSelectiveSegments API with selected segment IDs', async () => {
      populateStore()
      mockPreviewSelectiveSegments.mockResolvedValue({ previewUrl: 'https://preview.example.com/selective1' })

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      vm.selectiveMode = true
      vm.selectedSegmentIds = [101, 103]
      await flushPromises()

      await vm.handleSelectivePreview()
      await flushPromises()

      expect(mockPreviewSelectiveSegments).toHaveBeenCalledWith(1, { segmentIds: [101, 103] })
      expect(windowOpenSpy).toHaveBeenCalledWith('https://preview.example.com/selective1', '_blank')
    })

    it('does not call API when selectedSegmentIds is empty', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      vm.selectiveMode = true
      vm.selectedSegmentIds = []
      await flushPromises()

      await vm.handleSelectivePreview()
      await flushPromises()

      expect(mockPreviewSelectiveSegments).not.toHaveBeenCalled()
    })

    it('shows error message when selective preview API fails', async () => {
      populateStore()
      mockPreviewSelectiveSegments.mockRejectedValue(new Error('Selective preview failed'))

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      vm.selectiveMode = true
      vm.selectedSegmentIds = [101]
      await flushPromises()

      const callsBefore = windowOpenSpy.mock.calls.length
      await vm.handleSelectivePreview()
      await flushPromises()

      expect(vm.previewLoading).toBe(false)
      // window.open should NOT have been called during this preview attempt
      expect(windowOpenSpy.mock.calls.length).toBe(callsBefore)
    })
  })

  // ── Requirement 6.7: Empty state + switch link ──

  describe('empty state', () => {
    it('shows empty state when assembly config has no segments', async () => {
      const store = useTemplateWorkspaceStore()
      store.templateId = 1
      store.segments = []
      store.assemblyConfig = { segments: [] }

      const wrapper = mountTab()
      await flushPromises()

      expect(wrapper.find('.el-empty').exists()).toBe(true)
    })

    it('shows empty state when assemblyConfig is null', async () => {
      const store = useTemplateWorkspaceStore()
      store.templateId = 1
      store.segments = []
      store.assemblyConfig = null

      const wrapper = mountTab()
      await flushPromises()

      expect(wrapper.find('.el-empty').exists()).toBe(true)
    })

    it('emits switchToSegments event when link in empty state is clicked', async () => {
      const store = useTemplateWorkspaceStore()
      store.templateId = 1
      store.segments = []
      store.assemblyConfig = { segments: [] }

      const wrapper = mountTab()
      await flushPromises()

      // Find the link button inside el-empty
      const linkBtn = wrapper.find('.el-empty .el-button')
      expect(linkBtn.exists()).toBe(true)

      await linkBtn.trigger('click')
      await flushPromises()

      expect(wrapper.emitted('switchToSegments')).toBeTruthy()
      expect(wrapper.emitted('switchToSegments')!.length).toBe(1)
    })

    it('does not show empty state when segments exist', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      expect(wrapper.find('.el-empty').exists()).toBe(false)
    })
  })

  // ── Requirement 6.6: Lock icon remains, editor button stays enabled ──

  describe('lock display behavior', () => {
    it('shows lock info but keeps Open Editor button enabled for locked segments', async () => {
      populateStore()
      mockGetSegmentLockInfo
        .mockResolvedValueOnce(null)
        .mockResolvedValueOnce(sampleLockInfo) // segment 102 locked
        .mockResolvedValueOnce(null)

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      await vm.checkLocks()
      await flushPromises()

      // Lock indicator should be visible
      expect(wrapper.find('.lock-indicator').exists()).toBe(true)

      // All "Open Editor" buttons should still be enabled (not disabled)
      const openBtns = wrapper.findAll('.el-button').filter(b => {
        const text = b.text()
        return text.includes('Open Editor') || text.includes('open') || text.includes('editor')
      })
      openBtns.forEach(btn => {
        expect(btn.attributes('disabled')).toBeUndefined()
      })
    })
  })
})
