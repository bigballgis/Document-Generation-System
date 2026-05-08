import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import SegmentVersionDialog from '@/views/template-workspace/components/SegmentVersionDialog.vue'
import type { SegmentVersionDTO, SegmentVersionDiffResult, ContentDiffLine } from '@/api/composite-templates'

const hoisted = vi.hoisted(() => ({
  getSegmentVersions: vi.fn(),
  compareSegmentVersions: vi.fn(),
  publishSegment: vi.fn(),
  rollbackSegmentVersion: vi.fn(),
}))

vi.mock('@/api/composite-templates', () => ({
  getSegmentVersions: hoisted.getSegmentVersions,
  compareSegmentVersions: hoisted.compareSegmentVersions,
  publishSegment: hoisted.publishSegment,
  rollbackSegmentVersion: hoisted.rollbackSegmentVersion,
}))

function versionRow(n: number): SegmentVersionDTO {
  return {
    id: n,
    templateId: 1,
    segmentName: 'body',
    versionNumber: n,
    filePath: `segments/1/v${n}.docx`,
    segmentType: 'BODY',
    configSnapshot: '{}',
    comment: null,
    createdBy: 1,
    createdAt: '2026-01-01T00:00:00Z',
  }
}

describe('SegmentVersionDialog', () => {
  beforeEach(() => {
    hoisted.getSegmentVersions.mockReset()
    hoisted.compareSegmentVersions.mockReset()
    hoisted.publishSegment.mockReset()
    hoisted.rollbackSegmentVersion.mockReset()
    hoisted.getSegmentVersions.mockResolvedValue([versionRow(1), versionRow(2)])
    hoisted.publishSegment.mockResolvedValue(versionRow(3))
    hoisted.rollbackSegmentVersion.mockResolvedValue(versionRow(1))
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  async function mountOpen() {
    const w = mount(SegmentVersionDialog, {
      props: { modelValue: true, templateId: 1, segmentName: 'body' },
      attachTo: document.body,
      global: {
        stubs: {
          Teleport: true,
          ElDialog: {
            props: ['modelValue'],
            template: '<div class="test-dialog-root" v-show="modelValue"><slot /></div>',
          },
        },
      },
    })
    await flushPromises()
    for (let i = 0; i < 80; i++) {
      if (w.findAll('.el-table__body-wrapper .el-table__row').length >= 2) break
      await new Promise((r) => setTimeout(r, 10))
      await flushPromises()
    }
    return w
  }

  async function selectTwoVersionsAndCompare(wrapper: VueWrapper<any>) {
    const rows = wrapper.findAll('.el-table__body-wrapper .el-table__row')
    expect(rows.length).toBeGreaterThanOrEqual(2)
    const compareBtn = (row: (typeof rows)[number]) =>
      row.findAll('button').find((b) => b.text().includes('Compare'))
    const b0 = compareBtn(rows[0])
    const b1 = compareBtn(rows[1])
    expect(b0?.exists()).toBe(true)
    expect(b1?.exists()).toBe(true)
    await b0!.trigger('click')
    await b1!.trigger('click')
    await flushPromises()
    const primary = wrapper.find('.compare-header .el-button--primary')
    expect(primary.exists()).toBe(true)
    await primary.trigger('click')
    await flushPromises()
  }

  it('compare action calls API with includeContentDiff true', async () => {
    hoisted.compareSegmentVersions.mockResolvedValue({
      templateId: 1,
      segmentName: 'body',
      versionA: 1,
      versionB: 2,
      diffs: [],
      filePathChanged: false,
      oldFilePath: 'a',
      newFilePath: 'b',
      contentDiffs: [],
      contentChanged: false,
      truncated: false,
    } satisfies SegmentVersionDiffResult)

    const wrapper = await mountOpen()
    expect(hoisted.getSegmentVersions).toHaveBeenCalledWith(1, 'body')
    await selectTwoVersionsAndCompare(wrapper)

    expect(hoisted.compareSegmentVersions).toHaveBeenCalledWith(1, 'body', 1, 2, true)
    wrapper.unmount()
  })

  it('contentChanged false shows no content-change state', async () => {
    hoisted.compareSegmentVersions.mockResolvedValue({
      templateId: 1,
      segmentName: 'body',
      versionA: 1,
      versionB: 2,
      diffs: [],
      filePathChanged: false,
      oldFilePath: 'a',
      newFilePath: 'a',
      contentDiffs: [],
      contentChanged: false,
      truncated: false,
    })

    const wrapper = await mountOpen()
    await selectTwoVersionsAndCompare(wrapper)

    expect(wrapper.text()).toContain('No differences')
    expect(wrapper.text()).toContain('No content changes')
    wrapper.unmount()
  })

  it('truncated true shows truncation warning when content changed', async () => {
    hoisted.compareSegmentVersions.mockResolvedValue({
      templateId: 1,
      segmentName: 'body',
      versionA: 1,
      versionB: 2,
      diffs: [],
      filePathChanged: false,
      oldFilePath: 'a',
      newFilePath: 'b',
      contentDiffs: [{ type: 'ADDED', oldLineNumber: null, newLineNumber: 1, oldText: null, newText: 'x' }],
      contentChanged: true,
      truncated: true,
    })

    const wrapper = await mountOpen()
    await selectTwoVersionsAndCompare(wrapper)

    expect(wrapper.find('.el-alert--warning').exists()).toBe(true)
    expect(wrapper.text()).toMatch(/truncated|2000/i)
    wrapper.unmount()
  })

  it('renders added, removed, and modified content diff lines', async () => {
    const lines: ContentDiffLine[] = [
      { type: 'ADDED', oldLineNumber: null, newLineNumber: 1, oldText: null, newText: 'new-only' },
      { type: 'REMOVED', oldLineNumber: 2, newLineNumber: null, oldText: 'gone', newText: null },
      { type: 'MODIFIED', oldLineNumber: 3, newLineNumber: 3, oldText: 'old', newText: 'new' },
    ]
    hoisted.compareSegmentVersions.mockResolvedValue({
      templateId: 1,
      segmentName: 'body',
      versionA: 1,
      versionB: 2,
      diffs: [],
      filePathChanged: false,
      oldFilePath: 'a',
      newFilePath: 'b',
      contentDiffs: lines,
      contentChanged: true,
      truncated: false,
    })

    const wrapper = await mountOpen()
    await selectTwoVersionsAndCompare(wrapper)

    expect(wrapper.text()).toContain('new-only')
    expect(wrapper.text()).toContain('gone')
    expect(wrapper.text()).toContain('old')
    expect(wrapper.text()).toContain('new')
    expect(wrapper.find('.diff-line-added').exists()).toBe(true)
    expect(wrapper.find('.diff-line-removed').exists()).toBe(true)
    wrapper.unmount()
  })

  it('expanding a collapsed equal-line group reveals hidden lines (reactivity)', async () => {
    const lines: ContentDiffLine[] = []
    for (let i = 0; i < 8; i++) {
      lines.push({
        type: 'EQUAL',
        oldLineNumber: i + 1,
        newLineNumber: i + 1,
        oldText: `L${i}`,
        newText: `L${i}`,
      })
    }
    hoisted.compareSegmentVersions.mockResolvedValue({
      templateId: 1,
      segmentName: 'body',
      versionA: 1,
      versionB: 2,
      diffs: [],
      filePathChanged: false,
      oldFilePath: 'a',
      newFilePath: 'b',
      contentDiffs: lines,
      contentChanged: true,
      truncated: false,
    })

    const wrapper = await mountOpen()
    await selectTwoVersionsAndCompare(wrapper)

    const collapsed = wrapper.find('.diff-line-collapsed')
    expect(collapsed.exists()).toBe(true)
    expect(collapsed.text()).toMatch(/unchanged/i)

    await collapsed.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('L3')
    expect(wrapper.findAll('.diff-line-equal').length).toBeGreaterThanOrEqual(6)
    wrapper.unmount()
  })
})
