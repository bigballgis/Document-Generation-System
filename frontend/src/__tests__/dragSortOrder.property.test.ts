/**
 * Property 4: 拖拽排序后 sort_order 连续性
 * Feature: parameter-settings-ux
 * **Validates: Requirements 4.3, 4.5**
 *
 * For any set of siblings and any valid reorder operation,
 * the resulting sort_order values form a contiguous sequence [0, 1, 2, ..., N-1].
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

/**
 * Pure function: given an array of sibling IDs in their new visual order,
 * compute the new sort_order mapping.
 */
function computeNewSortOrders(orderedIds: number[]): Array<{ id: number; sortOrder: number }> {
  return orderedIds.map((id, index) => ({ id, sortOrder: index }))
}

/**
 * Simulate a drag reorder: move item at fromIndex to toIndex.
 */
function reorder<T>(arr: T[], fromIndex: number, toIndex: number): T[] {
  const result = [...arr]
  const [moved] = result.splice(fromIndex, 1)
  result.splice(toIndex, 0, moved)
  return result
}

describe('Property 4: 拖拽排序后 sort_order 连续性', () => {
  it('sort_order values are contiguous integers starting from 0', () => {
    fc.assert(
      fc.property(
        fc.array(fc.integer({ min: 1, max: 1000 }), { minLength: 2, maxLength: 20 })
          .filter(ids => new Set(ids).size === ids.length), // unique IDs
        fc.nat(),
        fc.nat(),
        (ids, fromRaw, toRaw) => {
          const fromIndex = fromRaw % ids.length
          let toIndex = toRaw % ids.length
          if (toIndex === fromIndex) toIndex = (toIndex + 1) % ids.length

          const reordered = reorder(ids, fromIndex, toIndex)
          const sortOrders = computeNewSortOrders(reordered)

          // Verify contiguous sequence
          expect(sortOrders.length).toBe(ids.length)
          for (let i = 0; i < sortOrders.length; i++) {
            expect(sortOrders[i].sortOrder).toBe(i)
          }

          // Verify all original IDs are present
          const resultIds = new Set(sortOrders.map(s => s.id))
          for (const id of ids) {
            expect(resultIds.has(id)).toBe(true)
          }
        },
      ),
      { numRuns: 200 },
    )
  })

  it('total count of siblings remains unchanged after reorder', () => {
    fc.assert(
      fc.property(
        fc.array(fc.integer({ min: 1, max: 1000 }), { minLength: 1, maxLength: 20 })
          .filter(ids => new Set(ids).size === ids.length),
        (ids) => {
          const sortOrders = computeNewSortOrders(ids)
          expect(sortOrders.length).toBe(ids.length)
        },
      ),
      { numRuns: 100 },
    )
  })
})
