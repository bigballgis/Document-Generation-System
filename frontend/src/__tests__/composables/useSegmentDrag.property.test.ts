/**
 * Property tests for useSegmentDrag composable.
 * Feature: design-stage-layout, Property 4: Drag sort index continuity
 * **Validates: Requirements 2.9, 4.10**
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { useSegmentDrag } from '@/composables/useSegmentDrag'
import type { AssemblySegmentEntry } from '@/types/segment'

// Feature: design-stage-layout, Property 4: Drag sort index continuity

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

describe('Property 4: Drag sort index continuity', () => {
  it('after reorder, positions form contiguous [0..N-1] and moved element is at target', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 2, max: 30 }),
        fc.nat(),
        fc.nat(),
        (count, fromRaw, toRaw) => {
          const { reorder } = useSegmentDrag()
          const segments = Array.from({ length: count }, (_, i) => makeEntry(`s${i}`, i))

          const fromIndex = fromRaw % count
          let toIndex = toRaw % count
          if (toIndex === fromIndex) toIndex = (toIndex + 1) % count

          const result = reorder(segments, fromIndex, toIndex)

          // Positions must be contiguous 0..N-1
          expect(result.length).toBe(count)
          for (let i = 0; i < result.length; i++) {
            expect(result[i].position).toBe(i)
          }

          // The moved element must be at the target position
          expect(result[toIndex].name).toBe(segments[fromIndex].name)

          // All original names must be preserved
          const originalNames = new Set(segments.map(s => s.name))
          const resultNames = new Set(result.map(s => s.name))
          expect(resultNames).toEqual(originalNames)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('moveUp preserves contiguous positions', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 2, max: 20 }),
        fc.nat(),
        (count, indexRaw) => {
          const { moveUp } = useSegmentDrag()
          const segments = Array.from({ length: count }, (_, i) => makeEntry(`s${i}`, i))
          const index = 1 + (indexRaw % (count - 1)) // valid moveUp index: 1..count-1

          const result = moveUp(segments, index)
          expect(result).not.toBeNull()
          expect(result!.length).toBe(count)
          for (let i = 0; i < result!.length; i++) {
            expect(result![i].position).toBe(i)
          }
          // Moved element should be one position up
          expect(result![index - 1].name).toBe(segments[index].name)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('moveDown preserves contiguous positions', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 2, max: 20 }),
        fc.nat(),
        (count, indexRaw) => {
          const { moveDown } = useSegmentDrag()
          const segments = Array.from({ length: count }, (_, i) => makeEntry(`s${i}`, i))
          const index = indexRaw % (count - 1) // valid moveDown index: 0..count-2

          const result = moveDown(segments, index)
          expect(result).not.toBeNull()
          expect(result!.length).toBe(count)
          for (let i = 0; i < result!.length; i++) {
            expect(result![i].position).toBe(i)
          }
          // Moved element should be one position down
          expect(result![index + 1].name).toBe(segments[index].name)
        },
      ),
      { numRuns: 100 },
    )
  })
})
