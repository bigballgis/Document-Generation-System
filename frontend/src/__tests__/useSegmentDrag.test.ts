import { describe, it, expect } from 'vitest'
import { useSegmentDrag } from '@/composables/useSegmentDrag'
import type { AssemblySegmentEntry } from '@/types/segment'

function makeEntry(name: string, position: number): AssemblySegmentEntry {
  return {
    filePath: `segments/1/${name}.docx`,
    name,
    segmentType: null,
    position,
    enabled: true,
    pageBreakBefore: false,
    conditionExpression: null,
    dataScope: null,
    headerFilePath: null,
    footerFilePath: null,
    pageNumberFormat: null,
    pageNumberStart: null,
  }
}

describe('useSegmentDrag', () => {
  describe('reorder', () => {
    it('moves item forward in the list', () => {
      const { reorder } = useSegmentDrag()
      const segments = [makeEntry('a', 0), makeEntry('b', 1), makeEntry('c', 2)]
      const result = reorder(segments, 0, 2)
      expect(result.map(s => s.name)).toEqual(['b', 'c', 'a'])
      expect(result.map(s => s.position)).toEqual([0, 1, 2])
    })

    it('moves item backward in the list', () => {
      const { reorder } = useSegmentDrag()
      const segments = [makeEntry('a', 0), makeEntry('b', 1), makeEntry('c', 2)]
      const result = reorder(segments, 2, 0)
      expect(result.map(s => s.name)).toEqual(['c', 'a', 'b'])
      expect(result.map(s => s.position)).toEqual([0, 1, 2])
    })

    it('returns same array when from equals to', () => {
      const { reorder } = useSegmentDrag()
      const segments = [makeEntry('a', 0), makeEntry('b', 1)]
      const result = reorder(segments, 1, 1)
      expect(result).toBe(segments)
    })

    it('returns same array for out-of-bounds indices', () => {
      const { reorder } = useSegmentDrag()
      const segments = [makeEntry('a', 0)]
      expect(reorder(segments, -1, 0)).toBe(segments)
      expect(reorder(segments, 0, 5)).toBe(segments)
    })

    it('handles single-element array', () => {
      const { reorder } = useSegmentDrag()
      const segments = [makeEntry('a', 0)]
      const result = reorder(segments, 0, 0)
      expect(result).toBe(segments)
    })
  })

  describe('moveUp', () => {
    it('moves item up by one position', () => {
      const { moveUp } = useSegmentDrag()
      const segments = [makeEntry('a', 0), makeEntry('b', 1), makeEntry('c', 2)]
      const result = moveUp(segments, 2)
      expect(result).not.toBeNull()
      expect(result!.map(s => s.name)).toEqual(['a', 'c', 'b'])
    })

    it('returns null when already at top', () => {
      const { moveUp } = useSegmentDrag()
      const segments = [makeEntry('a', 0), makeEntry('b', 1)]
      expect(moveUp(segments, 0)).toBeNull()
    })
  })

  describe('moveDown', () => {
    it('moves item down by one position', () => {
      const { moveDown } = useSegmentDrag()
      const segments = [makeEntry('a', 0), makeEntry('b', 1), makeEntry('c', 2)]
      const result = moveDown(segments, 0)
      expect(result).not.toBeNull()
      expect(result!.map(s => s.name)).toEqual(['b', 'a', 'c'])
    })

    it('returns null when already at bottom', () => {
      const { moveDown } = useSegmentDrag()
      const segments = [makeEntry('a', 0), makeEntry('b', 1)]
      expect(moveDown(segments, 1)).toBeNull()
    })
  })

  describe('onDrop', () => {
    it('reorders based on dragging and drop target indices', () => {
      const drag = useSegmentDrag()
      const segments = [makeEntry('a', 0), makeEntry('b', 1), makeEntry('c', 2)]
      drag.onDragStart(0)
      drag.onDragOver(2)
      const result = drag.onDrop(segments)
      expect(result.map(s => s.name)).toEqual(['b', 'c', 'a'])
      expect(drag.draggingIndex.value).toBeNull()
      expect(drag.dropTargetIndex.value).toBeNull()
    })

    it('returns original array when no drag state', () => {
      const drag = useSegmentDrag()
      const segments = [makeEntry('a', 0)]
      const result = drag.onDrop(segments)
      expect(result).toBe(segments)
    })
  })
})
