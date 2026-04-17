/**
 * Property tests for useCanvasNodes composable.
 * Feature: design-stage-layout
 */
import { describe, it, expect, beforeEach } from 'vitest'
import * as fc from 'fast-check'
import { useCanvasNodes } from '@/composables/useCanvasNodes'
import type { CanvasNode } from '@/composables/useCanvasNodes'
import type { AssemblySegmentEntry } from '@/types/segment'

// Feature: design-stage-layout, Property 5: Control node attribute propagation correctness
// **Validates: Requirements 4.5, 4.15**

// Feature: design-stage-layout, Property 6: Control node affected range calculation
// **Validates: Requirements 4.16**

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

let _nodeCounter = 0
function makeNode(type: CanvasNode['type'], extra?: Partial<CanvasNode>): CanvasNode {
  return { id: `test-${++_nodeCounter}`, type, ...extra }
}

// ── Arbitraries ──


describe('Property 5: Control node attribute propagation correctness', () => {
  beforeEach(() => { _nodeCounter = 0 })

  it('page-break node sets pageBreakBefore=true on next content segment', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 2, max: 10 }),
        fc.nat(),
        (segCount, insertRaw) => {
          const { toSegments } = useCanvasNodes()
          const segments = Array.from({ length: segCount }, (_, i) => makeSegment(`s${i}`, i))

          // Insert page-break before a random content node (not the first)
          const insertBefore = 1 + (insertRaw % (segCount - 1))

          // Build canvas nodes: content nodes with a page-break inserted
          const nodes: CanvasNode[] = []
          for (let i = 0; i < segCount; i++) {
            if (i === insertBefore) {
              nodes.push(makeNode('page-break'))
            }
            nodes.push(makeNode('content', { segmentIndex: i }))
          }

          const result = toSegments(nodes, segments)

          // The segment at insertBefore position should have pageBreakBefore=true
          expect(result[insertBefore].pageBreakBefore).toBe(true)

          // All other segments should have pageBreakBefore=false
          for (let i = 0; i < result.length; i++) {
            if (i !== insertBefore) {
              expect(result[i].pageBreakBefore).toBe(false)
            }
          }
        },
      ),
      { numRuns: 100 },
    )
  })

  it('removing page-break restores pageBreakBefore=false', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 2, max: 10 }),
        (segCount) => {
          const { toSegments } = useCanvasNodes()
          const segments = Array.from({ length: segCount }, (_, i) => makeSegment(`s${i}`, i))

          // Build nodes WITHOUT page-break (simulating removal)
          const nodes: CanvasNode[] = segments.map((_, i) =>
            makeNode('content', { segmentIndex: i }),
          )

          const result = toSegments(nodes, segments)

          // All segments should have pageBreakBefore=false
          for (const seg of result) {
            expect(seg.pageBreakBefore).toBe(false)
          }
        },
      ),
      { numRuns: 100 },
    )
  })

  it('header node propagates headerFilePath to subsequent content segments', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 2, max: 8 }),
        fc.nat(),
        (segCount, insertRaw) => {
          const { toSegments } = useCanvasNodes()
          const segments = Array.from({ length: segCount }, (_, i) => makeSegment(`s${i}`, i))
          const insertBefore = insertRaw % segCount
          const headerPath = 'segments/1/headers/test-header.docx'

          const nodes: CanvasNode[] = []
          for (let i = 0; i < segCount; i++) {
            if (i === insertBefore) {
              nodes.push(makeNode('header', { headerFilePath: headerPath }))
            }
            nodes.push(makeNode('content', { segmentIndex: i }))
          }

          const result = toSegments(nodes, segments)

          // Segments from insertBefore onward should have the headerFilePath
          for (let i = 0; i < result.length; i++) {
            if (i >= insertBefore) {
              expect(result[i].headerFilePath).toBe(headerPath)
            } else {
              expect(result[i].headerFilePath).toBeNull()
            }
          }
        },
      ),
      { numRuns: 100 },
    )
  })

  it('footer node propagates footerFilePath to subsequent content segments', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 2, max: 8 }),
        fc.nat(),
        (segCount, insertRaw) => {
          const { toSegments } = useCanvasNodes()
          const segments = Array.from({ length: segCount }, (_, i) => makeSegment(`s${i}`, i))
          const insertBefore = insertRaw % segCount
          const footerPath = 'segments/1/footers/test-footer.docx'

          const nodes: CanvasNode[] = []
          for (let i = 0; i < segCount; i++) {
            if (i === insertBefore) {
              nodes.push(makeNode('footer', { footerFilePath: footerPath }))
            }
            nodes.push(makeNode('content', { segmentIndex: i }))
          }

          const result = toSegments(nodes, segments)

          for (let i = 0; i < result.length; i++) {
            if (i >= insertBefore) {
              expect(result[i].footerFilePath).toBe(footerPath)
            } else {
              expect(result[i].footerFilePath).toBeNull()
            }
          }
        },
      ),
      { numRuns: 100 },
    )
  })
})


describe('Property 6: Control node affected range calculation', () => {
  beforeEach(() => { _nodeCounter = 0 })

  it('affected range extends from control node to next same-type node', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 3, max: 10 }),
        fc.constantFrom('header' as const, 'footer' as const, 'page-number' as const),
        fc.nat(),
        fc.nat(),
        (segCount, controlType, pos1Raw, pos2Raw) => {
          const { setNodes, getAffectedRange } = useCanvasNodes()

          // Place two control nodes of the same type at different positions
          const pos1 = pos1Raw % segCount
          let pos2 = pos2Raw % segCount
          if (pos2 <= pos1) pos2 = pos1 + 1
          if (pos2 >= segCount) return // skip if can't place two distinct positions

          const nodeList: CanvasNode[] = []
          let controlIdx1 = -1
          let controlIdx2 = -1

          for (let i = 0; i < segCount; i++) {
            if (i === pos1) {
              controlIdx1 = nodeList.length
              nodeList.push(makeNode(controlType))
            }
            if (i === pos2) {
              controlIdx2 = nodeList.length
              nodeList.push(makeNode(controlType))
            }
            nodeList.push(makeNode('content', { segmentIndex: i }))
          }

          setNodes(nodeList)

          // First control node's range should end at second control node
          const range1 = getAffectedRange(controlIdx1)
          expect(range1.end).toBe(controlIdx2)

          // Second control node's range should extend to end of list
          const range2 = getAffectedRange(controlIdx2)
          expect(range2.end).toBe(nodeList.length)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('single control node affects all subsequent content nodes to end', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 2, max: 10 }),
        fc.constantFrom('header' as const, 'footer' as const, 'page-break' as const),
        fc.nat(),
        (segCount, controlType, posRaw) => {
          const { setNodes, getAffectedRange } = useCanvasNodes()
          const pos = posRaw % segCount

          const nodeList: CanvasNode[] = []
          let controlIdx = -1

          for (let i = 0; i < segCount; i++) {
            if (i === pos) {
              controlIdx = nodeList.length
              nodeList.push(makeNode(controlType))
            }
            nodeList.push(makeNode('content', { segmentIndex: i }))
          }

          setNodes(nodeList)

          const range = getAffectedRange(controlIdx)
          // With only one control node of this type, range extends to end
          expect(range.end).toBe(nodeList.length)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('content nodes return invalid range (-1, -1)', () => {
    const { setNodes, getAffectedRange } = useCanvasNodes()
    const nodeList: CanvasNode[] = [
      makeNode('content', { segmentIndex: 0 }),
      makeNode('content', { segmentIndex: 1 }),
    ]
    setNodes(nodeList)
    const range = getAffectedRange(0)
    expect(range).toEqual({ start: -1, end: -1 })
  })
})
