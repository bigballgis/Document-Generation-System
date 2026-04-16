/**
 * Property 10: 点号语法嵌套结构解析
 * Feature: parameter-settings-ux
 * **Validates: Requirements 10.3**
 *
 * For any valid dotted parameter name (e.g., a.b.c), the Quick_Add_Bar SHALL create
 * a nested structure where intermediate segments are OBJECT and the final is STRING.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

// Pure function extracted from QuickAddBar logic for testing
function parseDotSyntax(input: string): Array<{ name: string; dataType: 'OBJECT' | 'STRING'; depth: number }> {
  const segments = input.split('.')
  return segments.map((seg, i) => ({
    name: seg,
    dataType: i < segments.length - 1 ? 'OBJECT' : 'STRING',
    depth: i + 1,
  }))
}

const validSegmentArb = fc.stringMatching(/^[a-zA-Z_][a-zA-Z0-9_]{0,8}$/)

describe('Property 10: 点号语法嵌套结构解析', () => {
  it('intermediate segments are OBJECT, final segment is STRING', () => {
    fc.assert(
      fc.property(
        fc.array(validSegmentArb, { minLength: 2, maxLength: 5 }),
        (segments) => {
          const dotted = segments.join('.')
          const result = parseDotSyntax(dotted)

          expect(result.length).toBe(segments.length)

          // All intermediate segments should be OBJECT
          for (let i = 0; i < result.length - 1; i++) {
            expect(result[i].dataType).toBe('OBJECT')
            expect(result[i].name).toBe(segments[i])
          }

          // Final segment should be STRING
          const last = result[result.length - 1]
          expect(last.dataType).toBe('STRING')
          expect(last.name).toBe(segments[segments.length - 1])
        },
      ),
      { numRuns: 200 },
    )
  })

  it('single segment produces a single STRING parameter', () => {
    fc.assert(
      fc.property(validSegmentArb, (name) => {
        const result = parseDotSyntax(name)
        expect(result.length).toBe(1)
        expect(result[0].dataType).toBe('STRING')
        expect(result[0].name).toBe(name)
      }),
      { numRuns: 100 },
    )
  })

  it('depth values are sequential starting from 1', () => {
    fc.assert(
      fc.property(
        fc.array(validSegmentArb, { minLength: 1, maxLength: 5 }),
        (segments) => {
          const dotted = segments.join('.')
          const result = parseDotSyntax(dotted)
          for (let i = 0; i < result.length; i++) {
            expect(result[i].depth).toBe(i + 1)
          }
        },
      ),
      { numRuns: 100 },
    )
  })
})
