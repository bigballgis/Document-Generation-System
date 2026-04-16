import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { AssemblySegmentEntry, AssemblyConfig } from '@/types/segment'

// ── Mock API modules ──

const mockUpdateAssemblyConfig = vi.fn()
const mockUploadSegment = vi.fn()

vi.mock('@/api/composite-templates', () => ({
  getAssemblyConfig: vi.fn().mockResolvedValue({ segments: [] }),
  updateAssemblyConfig: (...args: any[]) => mockUpdateAssemblyConfig(...args),
  getCompositeCoverage: vi.fn().mockResolvedValue({ overallCoveragePercent: 0, segmentCoverages: [] }),
  uploadSegment: (...args: any[]) => mockUploadSegment(...args),
}))

vi.mock('@/api/templates', () => ({
  getTemplate: vi.fn().mockResolvedValue({ id: 1, name: 'T', status: 'DRAFT', templateType: 'COMPOSITE', version: 1 }),
  getAvailableTransitions: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/parameters', () => ({
  getParameters: vi.fn().mockResolvedValue([]),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
  onBeforeRouteLeave: vi.fn(),
}))

// Stub KeyValueEditor to avoid deep rendering
vi.mock('@/components/KeyValueEditor.vue', () => ({
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

// ── Test fixtures (inline mode) ──

const sampleEntries: AssemblySegmentEntry[] = [
  { filePath: 'segments/1/cover.docx', name: 'Cover Page', segmentType: 'COVER', position: 0, enabled: true, pageBreakBefore: false, conditionExpression: null, dataScope: null },
  { filePath: 'segments/1/toc.docx', name: 'Table of Contents', segmentType: 'TOC', position: 1, enabled: true, pageBreakBefore: true, conditionExpression: 'data.show', dataScope: null },
  { filePath: 'segments/1/ch1.docx', name: 'Chapter One', segmentType: 'CHAPTER', position: 2, enabled: false, pageBreakBefore: false, conditionExpression: null, dataScope: { key1: 'val1' } },
]

const sampleConfig: AssemblyConfig = { segments: sampleEntries }

// ── Helpers ──

function populateStore() {
  const store = useTemplateWorkspaceStore()
  store.templateId = 1
  store.assemblyConfig = { segments: [...sampleEntries.map(e => ({ ...e }))] }
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
    mockUploadSegment.mockReset()
    mockConfirm.mockReset()
    mockUpdateAssemblyConfig.mockResolvedValue({ segments: [] })
  })

  // ── Rendering with inline data ──

  describe('inline segment rendering', () => {
    it('renders segment entries with names from assembly config', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const text = wrapper.text()
      expect(text).toContain('Cover Page')
      expect(text).toContain('Table of Contents')
      expect(text).toContain('Chapter One')
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

  // ── Empty state ──

  describe('empty state', () => {
    it('shows empty state when assembly config has zero segments', async () => {
      const store = useTemplateWorkspaceStore()
      store.templateId = 1
      store.assemblyConfig = { segments: [] }

      const wrapper = mountTab()
      await flushPromises()

      expect(wrapper.find('.el-empty').exists()).toBe(true)
    })

    it('does not show empty state when segments exist', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      expect(wrapper.find('.segment-list').exists()).toBe(true)
    })
  })

  // ── Drag & drop ──

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
  })

  // ── Save arrangement ──

  describe('save arrangement', () => {
    it('calls updateAssemblyConfig API and refreshes store on save', async () => {
      const store = populateStore()
      const refreshConfigSpy = vi.spyOn(store, 'refreshAssemblyConfig').mockResolvedValue()
      const refreshCoverageSpy = vi.spyOn(store, 'refreshCoverage').mockResolvedValue()
      mockUpdateAssemblyConfig.mockResolvedValue(sampleConfig)

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      await vm.handleSave()
      await flushPromises()

      expect(mockUpdateAssemblyConfig).toHaveBeenCalledWith(1, expect.objectContaining({ segments: expect.any(Array) }))
      expect(refreshConfigSpy).toHaveBeenCalled()
      expect(refreshCoverageSpy).toHaveBeenCalled()
    })
  })

  // ── Keyboard shortcuts ──

  describe('keyboard shortcuts', () => {
    it('Ctrl+Z triggers undo', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      vm.assemblyConfig.removeSegment(0)
      await flushPromises()
      expect(vm.assemblyConfig.segments.value.length).toBe(2)

      const event = new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true })
      document.dispatchEvent(event)
      await flushPromises()

      expect(vm.assemblyConfig.segments.value.length).toBe(3)
    })
  })

  // ── Unsaved changes ──

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
  })

  // ── Remove segment ──

  describe('remove segment', () => {
    it('shows confirm dialog and removes segment on confirmation', async () => {
      populateStore()
      mockConfirm.mockResolvedValue('confirm')

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      expect(vm.assemblyConfig.segments.value.length).toBe(3)

      await vm.handleRemove(0)
      await flushPromises()

      expect(mockConfirm).toHaveBeenCalled()
      expect(vm.assemblyConfig.segments.value.length).toBe(2)
      expect(vm.assemblyConfig.segments.value[0].name).toBe('Table of Contents')
    })
  })
})
