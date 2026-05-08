/**
 * Unit tests for useCanvasNodes composable.
 * Tests fromSegments/toSegments round-trip, empty list, control node insert/delete.
 * _Requirements: 4.5, 4.15, 4.16_
 */
import { describe, it, expect } from 'vitest'
import { useCanvasNodes } from '@/composables/useCanvasNodes'
import type { AssemblySegmentEntry } from '@/types/segment'

function makeSegment(name: string, position: number, overrides?: Partial<AssemblySegmentEntry>): AssemblySegmentEntry {
  return {
    filePath: `segments/1/${name}.docx`,
    name,
    segmentType: 'CHAPTER',
    position,
    enabled: true,
    pageBreakBefore: false,
    conditionExpression: null,
    dataScope: null,
    headerFilePath: null,
    footerFilePath: null,
    pageNumberFormat: null,
    pageNumberStart: null,
    ...overrides,
  }
}

describe('useCanvasNodes', () => {
  describe('fromSegments', () => {
    it('returns empty array for empty segments', () => {
      const { fromSegments } = useCanvasNodes()
      expect(fromSegments([])).toEqual([])
    })

    it('creates content nodes for plain segments', () => {
      const { fromSegments } = useCanvasNodes()
      const segments = [makeSegment('a', 0), makeSegment('b', 1)]
      const nodes = fromSegments(segments)
      expect(nodes.filter(n => n.type === 'content')).toHaveLength(2)
      expect(nodes[0].type).toBe('content')
      expect(nodes[0].segmentIndex).toBe(0)
      expect(nodes[1].type).toBe('content')
      expect(nodes[1].segmentIndex).toBe(1)
    })

    it('inserts page-break node before segment with pageBreakBefore', () => {
      const { fromSegments } = useCanvasNodes()
      const segments = [
        makeSegment('a', 0),
        makeSegment('b', 1, { pageBreakBefore: true }),
      ]
      const nodes = fromSegments(segments)
      expect(nodes[0].type).toBe('content')
      expect(nodes[1].type).toBe('page-break')
      expect(nodes[2].type).toBe('content')
    })

    it('inserts header node when headerFilePath changes', () => {
      const { fromSegments } = useCanvasNodes()
      const headerPath = 'segments/1/headers/h1.docx'
      const segments = [
        makeSegment('a', 0, { headerFilePath: headerPath }),
        makeSegment('b', 1, { headerFilePath: headerPath }),
      ]
      const nodes = fromSegments(segments)
      const headerNodes = nodes.filter(n => n.type === 'header')
      expect(headerNodes).toHaveLength(1)
      expect(headerNodes[0].headerFilePath).toBe(headerPath)
    })


    it('inserts footer node when footerFilePath changes', () => {
      const { fromSegments } = useCanvasNodes()
      const footerPath = 'segments/1/footers/f1.docx'
      const segments = [
        makeSegment('a', 0),
        makeSegment('b', 1, { footerFilePath: footerPath }),
      ]
      const nodes = fromSegments(segments)
      const footerNodes = nodes.filter(n => n.type === 'footer')
      expect(footerNodes).toHaveLength(1)
      expect(footerNodes[0].footerFilePath).toBe(footerPath)
    })

    it('inserts page-number node when format changes', () => {
      const { fromSegments } = useCanvasNodes()
      const segments = [
        makeSegment('a', 0, { pageNumberFormat: 'ARABIC', pageNumberStart: 1 }),
        makeSegment('b', 1, { pageNumberFormat: 'ARABIC', pageNumberStart: 1 }),
        makeSegment('c', 2, { pageNumberFormat: 'ROMAN', pageNumberStart: 1 }),
      ]
      const nodes = fromSegments(segments)
      const pageNumNodes = nodes.filter(n => n.type === 'page-number')
      expect(pageNumNodes).toHaveLength(2)
      expect(pageNumNodes[0].pageNumberFormat).toBe('ARABIC')
      expect(pageNumNodes[1].pageNumberFormat).toBe('ROMAN')
    })
  })

  describe('toSegments', () => {
    it('returns empty array for empty nodes', () => {
      const { toSegments } = useCanvasNodes()
      expect(toSegments([], [])).toEqual([])
    })

    it('converts content-only nodes back to segments with correct positions', () => {
      const { toSegments, generateNodeId } = useCanvasNodes()
      const segments = [makeSegment('a', 0), makeSegment('b', 1)]
      const nodes = [
        { id: generateNodeId(), type: 'content' as const, segmentIndex: 0 },
        { id: generateNodeId(), type: 'content' as const, segmentIndex: 1 },
      ]
      const result = toSegments(nodes, segments)
      expect(result).toHaveLength(2)
      expect(result[0].name).toBe('a')
      expect(result[0].position).toBe(0)
      expect(result[1].name).toBe('b')
      expect(result[1].position).toBe(1)
    })

    it('applies page-break to next content segment', () => {
      const { toSegments, generateNodeId } = useCanvasNodes()
      const segments = [makeSegment('a', 0), makeSegment('b', 1)]
      const nodes = [
        { id: generateNodeId(), type: 'content' as const, segmentIndex: 0 },
        { id: generateNodeId(), type: 'page-break' as const },
        { id: generateNodeId(), type: 'content' as const, segmentIndex: 1 },
      ]
      const result = toSegments(nodes, segments)
      expect(result[0].pageBreakBefore).toBe(false)
      expect(result[1].pageBreakBefore).toBe(true)
    })

    it('propagates header/footer paths to subsequent segments', () => {
      const { toSegments, generateNodeId } = useCanvasNodes()
      const segments = [makeSegment('a', 0), makeSegment('b', 1), makeSegment('c', 2)]
      const headerPath = 'segments/1/headers/h1.docx'
      const nodes = [
        { id: generateNodeId(), type: 'content' as const, segmentIndex: 0 },
        { id: generateNodeId(), type: 'header' as const, headerFilePath: headerPath },
        { id: generateNodeId(), type: 'content' as const, segmentIndex: 1 },
        { id: generateNodeId(), type: 'content' as const, segmentIndex: 2 },
      ]
      const result = toSegments(nodes, segments)
      expect(result[0].headerFilePath).toBeNull()
      expect(result[1].headerFilePath).toBe(headerPath)
      expect(result[2].headerFilePath).toBe(headerPath)
    })
  })

  describe('fromSegments/toSegments round-trip', () => {
    it('round-trip preserves segment data for plain segments', () => {
      const { fromSegments, toSegments } = useCanvasNodes()
      const segments = [makeSegment('a', 0), makeSegment('b', 1), makeSegment('c', 2)]
      const nodes = fromSegments(segments)
      const result = toSegments(nodes, segments)
      expect(result).toHaveLength(3)
      for (let i = 0; i < 3; i++) {
        expect(result[i].name).toBe(segments[i].name)
        expect(result[i].position).toBe(i)
        expect(result[i].filePath).toBe(segments[i].filePath)
      }
    })

    it('round-trip preserves page-break attributes', () => {
      const { fromSegments, toSegments } = useCanvasNodes()
      const segments = [
        makeSegment('a', 0),
        makeSegment('b', 1, { pageBreakBefore: true }),
        makeSegment('c', 2),
      ]
      const nodes = fromSegments(segments)
      const result = toSegments(nodes, segments)
      expect(result[0].pageBreakBefore).toBe(false)
      expect(result[1].pageBreakBefore).toBe(true)
      expect(result[2].pageBreakBefore).toBe(false)
    })

    it('round-trip preserves header/footer paths', () => {
      const { fromSegments, toSegments } = useCanvasNodes()
      const headerPath = 'segments/1/headers/h1.docx'
      const segments = [
        makeSegment('a', 0, { headerFilePath: headerPath }),
        makeSegment('b', 1, { headerFilePath: headerPath }),
      ]
      const nodes = fromSegments(segments)
      const result = toSegments(nodes, segments)
      expect(result[0].headerFilePath).toBe(headerPath)
      expect(result[1].headerFilePath).toBe(headerPath)
    })
  })

  describe('node operations', () => {
    it('addNode appends to end by default', () => {
      const { nodes, addNode, generateNodeId } = useCanvasNodes()
      addNode({ id: generateNodeId(), type: 'content', segmentIndex: 0 })
      addNode({ id: generateNodeId(), type: 'content', segmentIndex: 1 })
      expect(nodes.value).toHaveLength(2)
    })

    it('addNode inserts at specific index', () => {
      const { nodes, addNode, generateNodeId } = useCanvasNodes()
      addNode({ id: generateNodeId(), type: 'content', segmentIndex: 0 })
      addNode({ id: generateNodeId(), type: 'content', segmentIndex: 2 })
      addNode({ id: generateNodeId(), type: 'page-break' }, 1)
      expect(nodes.value).toHaveLength(3)
      expect(nodes.value[1].type).toBe('page-break')
    })

    it('removeNode removes at index', () => {
      const { nodes, addNode, removeNode, generateNodeId } = useCanvasNodes()
      addNode({ id: generateNodeId(), type: 'content', segmentIndex: 0 })
      addNode({ id: generateNodeId(), type: 'page-break' })
      addNode({ id: generateNodeId(), type: 'content', segmentIndex: 1 })
      removeNode(1) // remove page-break
      expect(nodes.value).toHaveLength(2)
      expect(nodes.value.every(n => n.type === 'content')).toBe(true)
    })
  })

  describe('getAffectedRange', () => {
    it('returns (-1, -1) for content nodes', () => {
      const { setNodes, getAffectedRange, generateNodeId } = useCanvasNodes()
      setNodes([{ id: generateNodeId(), type: 'content', segmentIndex: 0 }])
      expect(getAffectedRange(0)).toEqual({ start: -1, end: -1 })
    })

    it('range extends to end for single control node', () => {
      const { setNodes, getAffectedRange, generateNodeId } = useCanvasNodes()
      setNodes([
        { id: generateNodeId(), type: 'header', headerFilePath: 'h.docx' },
        { id: generateNodeId(), type: 'content', segmentIndex: 0 },
        { id: generateNodeId(), type: 'content', segmentIndex: 1 },
      ])
      const range = getAffectedRange(0)
      expect(range.start).toBe(1)
      expect(range.end).toBe(3)
    })

    it('range ends at next same-type control node', () => {
      const { setNodes, getAffectedRange, generateNodeId } = useCanvasNodes()
      setNodes([
        { id: generateNodeId(), type: 'header', headerFilePath: 'h1.docx' },
        { id: generateNodeId(), type: 'content', segmentIndex: 0 },
        { id: generateNodeId(), type: 'header', headerFilePath: 'h2.docx' },
        { id: generateNodeId(), type: 'content', segmentIndex: 1 },
      ])
      const range1 = getAffectedRange(0)
      expect(range1.start).toBe(1)
      expect(range1.end).toBe(2)

      const range2 = getAffectedRange(2)
      expect(range2.start).toBe(3)
      expect(range2.end).toBe(4)
    })
  })
})
