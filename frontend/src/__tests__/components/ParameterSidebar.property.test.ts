/**
 * Property tests for ParameterSidebar component logic.
 * Feature: design-stage-layout
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import type { DataType } from '@/types/parameter'

// Feature: design-stage-layout, Property 7: Parameter tag insert text correctness
// **Validates: Requirements 5.5, 5.7, 5.9**

// Feature: design-stage-layout, Property 8: Loop block section only contains ARRAY type parameters
// **Validates: Requirements 5.6**

// Feature: design-stage-layout, Property 10: Parameter search and filter correctness
// **Validates: Requirements 9.2, 9.3**

// ── Pure functions extracted from ParameterSidebar logic ──

/** Generate insert text for a parameter variable tag */
function generateVariableInsertText(parameterPath: string): string {
  return `{${parameterPath}}`
}

/** Generate insert text for a loop block */
function generateLoopInsertText(arrayName: string): string {
  return `{#${arrayName}}\n\n{/${arrayName}}`
}

/** Generate insert text for a condition block */
function generateConditionInsertText(expr: string): string {
  return `{#if ${expr}}\n\n{/if}`
}

/** Filter parameters for the loop block section (ARRAY only) */
function filterLoopBlockParams(params: Array<{ name: string; dataType: DataType }>): Array<{ name: string; dataType: DataType }> {
  return params.filter(p => p.dataType === 'ARRAY')
}

/** Search and filter parameters */
function searchAndFilter(
  params: Array<{ name: string; dataType: DataType }>,
  searchText: string,
  typeFilter: DataType | null,
): Array<{ name: string; dataType: DataType }> {
  return params.filter(p => {
    const matchesSearch = searchText === '' || p.name.toLowerCase().includes(searchText.toLowerCase())
    const matchesType = typeFilter === null || p.dataType === typeFilter
    return matchesSearch && matchesType
  })
}

// ── Arbitraries ──

const ALL_DATA_TYPES: DataType[] = ['STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'ARRAY', 'OBJECT']
const dataTypeArb: fc.Arbitrary<DataType> = fc.constantFrom(...ALL_DATA_TYPES)

const paramPathArb = fc.array(
  fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9]{0,9}$/),
  { minLength: 1, maxLength: 4 },
).map(parts => parts.join('.'))

const paramArb = fc.record({
  name: fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9]{0,9}$/),
  dataType: dataTypeArb,
})


describe('Property 7: Parameter tag insert text correctness', () => {
  it('variable insert text equals {parameterPath}', () => {
    fc.assert(
      fc.property(
        paramPathArb,
        (paramPath) => {
          const text = generateVariableInsertText(paramPath)
          expect(text).toBe(`{${paramPath}}`)
          expect(text.startsWith('{')).toBe(true)
          expect(text.endsWith('}')).toBe(true)
          expect(text.slice(1, -1)).toBe(paramPath)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('loop insert text equals {#name}\\n\\n{/name}', () => {
    fc.assert(
      fc.property(
        fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9]{0,14}$/),
        (arrayName) => {
          const text = generateLoopInsertText(arrayName)
          expect(text).toBe(`{#${arrayName}}\n\n{/${arrayName}}`)
          // Verify structure: opening tag, two newlines, closing tag
          const parts = text.split('\n\n')
          expect(parts.length).toBe(2)
          expect(parts[0]).toBe(`{#${arrayName}}`)
          expect(parts[1]).toBe(`{/${arrayName}}`)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('condition insert text equals {#if expr}\\n\\n{/if}', () => {
    fc.assert(
      fc.property(
        fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9 .><=!]{0,29}$/),
        (expr) => {
          const text = generateConditionInsertText(expr)
          expect(text).toBe(`{#if ${expr}}\n\n{/if}`)
          const parts = text.split('\n\n')
          expect(parts.length).toBe(2)
          expect(parts[0]).toBe(`{#if ${expr}}`)
          expect(parts[1]).toBe('{/if}')
        },
      ),
      { numRuns: 100 },
    )
  })
})

describe('Property 8: Loop block section only contains ARRAY type parameters', () => {
  it('all params in loop block are ARRAY, and all ARRAY params are included', () => {
    fc.assert(
      fc.property(
        fc.array(paramArb, { minLength: 0, maxLength: 30 }),
        (params) => {
          const loopParams = filterLoopBlockParams(params)

          // All returned params must be ARRAY
          for (const p of loopParams) {
            expect(p.dataType).toBe('ARRAY')
          }

          // All ARRAY params from input must be in the result (completeness)
          const allArrayParams = params.filter(p => p.dataType === 'ARRAY')
          expect(loopParams.length).toBe(allArrayParams.length)

          // Verify exact match
          for (let i = 0; i < allArrayParams.length; i++) {
            expect(loopParams[i]).toEqual(allArrayParams[i])
          }
        },
      ),
      { numRuns: 100 },
    )
  })
})

describe('Property 10: Parameter search and filter correctness', () => {
  it('filtered results contain exactly params matching search text and type filter', () => {
    fc.assert(
      fc.property(
        fc.array(paramArb, { minLength: 0, maxLength: 30 }),
        fc.stringMatching(/^[a-zA-Z]{0,5}$/),
        fc.oneof(fc.constant(null as DataType | null), dataTypeArb),
        (params, searchText, typeFilter) => {
          const result = searchAndFilter(params, searchText, typeFilter)

          // Every result must match both criteria
          for (const p of result) {
            if (searchText !== '') {
              expect(p.name.toLowerCase()).toContain(searchText.toLowerCase())
            }
            if (typeFilter !== null) {
              expect(p.dataType).toBe(typeFilter)
            }
          }

          // Every param matching both criteria must be in result (completeness)
          const expected = params.filter(p => {
            const matchesSearch = searchText === '' || p.name.toLowerCase().includes(searchText.toLowerCase())
            const matchesType = typeFilter === null || p.dataType === typeFilter
            return matchesSearch && matchesType
          })
          expect(result.length).toBe(expected.length)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('empty search text and null type filter returns all params', () => {
    fc.assert(
      fc.property(
        fc.array(paramArb, { minLength: 0, maxLength: 20 }),
        (params) => {
          const result = searchAndFilter(params, '', null)
          expect(result.length).toBe(params.length)
        },
      ),
      { numRuns: 100 },
    )
  })
})
