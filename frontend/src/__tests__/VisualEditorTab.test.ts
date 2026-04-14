import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { AssemblyConfig } from '@/types/segment'

// ── Mock API modules ──

const mockPreviewCompositeTemplate = vi.fn()
const mockPreviewSelectiveSegments = vi.fn()

vi.mock('@/api/composite-templates', () => ({
  getAssemblyConfig: vi.fn().mockResolvedValue({ segments: [] }),
  getCompositeCoverage: vi.fn().mockResolvedValue({ overallCoveragePercent: 0, segmentCoverages: [] }),
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

// ── Test fixtures (inline mode) ──

const sampleAssemblyConfig: AssemblyConfig = {
  segments: [
    { filePath: 'seg/cover.docx', name: 'Cover Page', segmentType: 'COVER', position: 0, enabled: true, pageBreakBefore: false, conditionExpression: null, dataScope: null },
    { filePath: 'seg/toc.docx', name: 'Table of Contents', segmentType: 'TOC', position: 1, enabled: true, pageBreakBefore: true, conditionExpression: null, dataScope: null },
    { filePath: 'seg/ch1.docx', name: 'Main Chapter', segmentType: 'CHAPTER', position: 2, enabled: true, pageBreakBefore: false, conditionExpression: null, dataScope: null },
  ],
}

function mountTab() {
  return mount(VisualEditorTab)
}

function populateStore() {
  const store = useTemplateWorkspaceStore()
  store.templateId = 1
  store.assemblyConfig = JSON.parse(JSON.stringify(sampleAssemblyConfig))
  return store
}

describe('VisualEditorTab', () => {
  let windowOpenSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    setActivePinia(createPinia())
    mockPreviewCompositeTemplate.mockReset()
    mockPreviewSelectiveSegments.mockReset()
    windowOpenSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
  })

  describe('inline segment rendering', () => {
    it('renders segment list with names from assembly config', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const text = wrapper.text()
      expect(text).toContain('Cover Page')
      expect(text).toContain('Table of Contents')
      expect(text).toContain('Main Chapter')
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
  })

  describe('preview composite', () => {
    it('calls previewCompositeTemplate API and opens previewUrl', async () => {
      populateStore()
      mockPreviewCompositeTemplate.mockResolvedValue({ previewUrl: 'https://preview.example.com/doc1' })

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      await vm.handlePreviewComposite()
      await flushPromises()

      expect(mockPreviewCompositeTemplate).toHaveBeenCalledWith(1)
      expect(windowOpenSpy).toHaveBeenCalledWith('https://preview.example.com/doc1', '_blank')
    })
  })

  describe('selective preview', () => {
    it('calls previewSelectiveSegments API with selected positions', async () => {
      populateStore()
      mockPreviewSelectiveSegments.mockResolvedValue({ previewUrl: 'https://preview.example.com/selective1' })

      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      vm.selectiveMode = true
      vm.selectedPositions = [0, 2]
      await flushPromises()

      await vm.handleSelectivePreview()
      await flushPromises()

      expect(mockPreviewSelectiveSegments).toHaveBeenCalledWith(1, { positions: [0, 2] })
      expect(windowOpenSpy).toHaveBeenCalledWith('https://preview.example.com/selective1', '_blank')
    })

    it('does not call API when selectedPositions is empty', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()

      const vm = wrapper.vm as any
      vm.selectiveMode = true
      vm.selectedPositions = []
      await flushPromises()

      await vm.handleSelectivePreview()
      await flushPromises()

      expect(mockPreviewSelectiveSegments).not.toHaveBeenCalled()
    })
  })

  describe('empty state', () => {
    it('shows empty state when assembly config has no segments', async () => {
      const store = useTemplateWorkspaceStore()
      store.templateId = 1
      store.assemblyConfig = { segments: [] }

      const wrapper = mountTab()
      await flushPromises()

      expect(wrapper.find('.el-empty').exists()).toBe(true)
    })

    it('emits switchToSegments event when link in empty state is clicked', async () => {
      const store = useTemplateWorkspaceStore()
      store.templateId = 1
      store.assemblyConfig = { segments: [] }

      const wrapper = mountTab()
      await flushPromises()

      const linkBtn = wrapper.find('.el-empty .el-button')
      expect(linkBtn.exists()).toBe(true)

      await linkBtn.trigger('click')
      await flushPromises()

      expect(wrapper.emitted('switchToSegments')).toBeTruthy()
    })
  })
})
