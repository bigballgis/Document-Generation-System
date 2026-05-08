/**
 * Property 12: paste depth check
 * Feature: parameter-settings-ux
 * **Validates: Requirements 11.5**
 *
 * For any target position depth D and any paste source tree with max depth S,
 * the paste operation SHALL be allowed if and only if D + S <= 5.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

const MAX_DEPTH = 5

/**
 * Pure function: check if paste is allowed.
 * @param targetDepth - depth of the target position (root = 1)
 * @param sourceMaxDepth - max depth of the source tree (single node = 1)
 */
function isPasteAllowed(targetDepth: number, sourceMaxDepth: number): boolean {
  return targetDepth + sourceMaxDepth - 1 <= MAX_DEPTH
}

describe('Property 12: paste depth check', () => {
  it('paste is allowed when D + S - 1 <= 5', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 5 }),
        fc.integer({ min: 1, max: 5 }),
        (targetDepth, sourceMaxDepth) => {
          const allowed = isPasteAllowed(targetDepth, sourceMaxDepth)
          const expected = targetDepth + sourceMaxDepth - 1 <= MAX_DEPTH
          expect(allowed).toBe(expected)
        },
      ),
      { numRuns: 200 },
    )
  })

  it('paste at root (depth=1) with source depth 5 is allowed', () => {
    expect(isPasteAllowed(1, 5)).toBe(true)
  })

  it('paste at root (depth=1) with source depth 6 is NOT allowed', () => {
    expect(isPasteAllowed(1, 6)).toBe(false)
  })

  it('paste at depth 5 only allows single-node source', () => {
    expect(isPasteAllowed(5, 1)).toBe(true)
    expect(isPasteAllowed(5, 2)).toBe(false)
  })

  it('paste at depth 3 allows source up to depth 3', () => {
    expect(isPasteAllowed(3, 3)).toBe(true)
    expect(isPasteAllowed(3, 4)).toBe(false)
  })
})
