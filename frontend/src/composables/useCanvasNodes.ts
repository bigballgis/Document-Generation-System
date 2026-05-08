import { ref } from 'vue'
import type { AssemblySegmentEntry, PageNumberFormat } from '@/types/segment'

export interface CanvasNode {
  id: string
  type: 'content' | 'page-break' | 'header' | 'footer' | 'page-number'
  segmentIndex?: number
  headerFilePath?: string
  footerFilePath?: string
  pageNumberFormat?: PageNumberFormat
  pageNumberStart?: number
}

let nodeIdCounter = 0
function generateNodeId(): string {
  return `node-${Date.now()}-${++nodeIdCounter}`
}

export function useCanvasNodes() {
  const nodes = ref<CanvasNode[]>([])

  /**
   * Reconstruct CanvasNode[] from AssemblySegmentEntry[].
   * Control nodes are inferred from segment extended fields.
   */
  function fromSegments(segments: AssemblySegmentEntry[]): CanvasNode[] {
    const result: CanvasNode[] = []
    segments.forEach((seg, idx) => {
      if (seg.pageBreakBefore) {
        result.push({ id: generateNodeId(), type: 'page-break' })
      }
      if (seg.headerFilePath && (idx === 0 || segments[idx - 1]?.headerFilePath !== seg.headerFilePath)) {
        result.push({ id: generateNodeId(), type: 'header', headerFilePath: seg.headerFilePath })
      }
      if (seg.footerFilePath && (idx === 0 || segments[idx - 1]?.footerFilePath !== seg.footerFilePath)) {
        result.push({ id: generateNodeId(), type: 'footer', footerFilePath: seg.footerFilePath })
      }
      if (seg.pageNumberFormat && (idx === 0 || segments[idx - 1]?.pageNumberFormat !== seg.pageNumberFormat || segments[idx - 1]?.pageNumberStart !== seg.pageNumberStart)) {
        result.push({
          id: generateNodeId(),
          type: 'page-number',
          pageNumberFormat: seg.pageNumberFormat,
          pageNumberStart: seg.pageNumberStart ?? undefined,
        })
      }
      result.push({ id: generateNodeId(), type: 'content', segmentIndex: idx })
    })
    return result
  }

  /**
   * Serialize CanvasNode[] back to AssemblySegmentEntry[].
   * Control node properties are propagated to subsequent content segments.
   */
  function toSegments(canvasNodes: CanvasNode[], currentSegments: AssemblySegmentEntry[]): AssemblySegmentEntry[] {
    const result: AssemblySegmentEntry[] = []
    let pendingPageBreak = false
    let currentHeaderPath: string | null = null
    let currentFooterPath: string | null = null
    let currentPageFormat: PageNumberFormat | null = null
    let currentPageStart: number | null = null

    for (const node of canvasNodes) {
      switch (node.type) {
        case 'page-break':
          pendingPageBreak = true
          break
        case 'header':
          currentHeaderPath = node.headerFilePath ?? null
          break
        case 'footer':
          currentFooterPath = node.footerFilePath ?? null
          break
        case 'page-number':
          currentPageFormat = node.pageNumberFormat ?? null
          currentPageStart = node.pageNumberStart ?? null
          break
        case 'content': {
          const segIdx = node.segmentIndex
          if (segIdx != null && segIdx < currentSegments.length) {
            const seg = { ...currentSegments[segIdx] }
            seg.position = result.length
            seg.pageBreakBefore = pendingPageBreak
            seg.headerFilePath = currentHeaderPath
            seg.footerFilePath = currentFooterPath
            seg.pageNumberFormat = currentPageFormat
            seg.pageNumberStart = currentPageStart
            result.push(seg)
            pendingPageBreak = false
          }
          break
        }
      }
    }
    return result
  }

  /**
   * Calculate the affected range of a control node.
   * Returns indices of content nodes affected (from this node to next same-type node).
   */
  function getAffectedRange(nodeIndex: number): { start: number; end: number } {
    const node = nodes.value[nodeIndex]
    if (!node || node.type === 'content') return { start: -1, end: -1 }

    let start = -1
    let end = nodes.value.length

    for (let i = nodeIndex + 1; i < nodes.value.length; i++) {
      if (nodes.value[i].type === 'content') {
        if (start === -1) start = i
      }
      if (nodes.value[i].type === node.type && i !== nodeIndex) {
        end = i
        break
      }
    }

    return { start: start === -1 ? nodeIndex + 1 : start, end }
  }

  function setNodes(newNodes: CanvasNode[]) {
    nodes.value = newNodes
  }

  function addNode(node: CanvasNode, atIndex?: number) {
    if (atIndex != null) {
      nodes.value.splice(atIndex, 0, node)
    } else {
      nodes.value.push(node)
    }
  }

  function removeNode(index: number) {
    nodes.value.splice(index, 1)
  }

  return { nodes, fromSegments, toSegments, getAffectedRange, setNodes, addNode, removeNode, generateNodeId }
}
