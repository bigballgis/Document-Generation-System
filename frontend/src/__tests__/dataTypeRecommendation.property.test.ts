/**
 * Property 15: Data type recommendation from placeholder name
 * Feature: template-parameter-redesign
 * **Validates: Requirements 4.16**
 *
 * For any placeholder name containing specific keywords, the recommended
 * data_type shall match the expected type. All other names default to STRING.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { recommendDataType } from '@/composables/useParameterUtils'

const numberKeywords = ['price', 'amount', 'count', 'total', 'qty', 'quantity']
const dateKeywords = ['date', 'time', 'created', 'updated']
const boolKeywords = ['is', 'has', 'enable', 'active', 'flag']

// Generator for names containing a specific keyword
function nameWithKeyword(keyword: string): fc.Arbitrary<string> {
  return fc.tuple(
    fc.stringMatching(/^[a-zA-Z]{0,5}$/),
    fc.stringMatching(/^[a-zA-Z]{0,5}$/),
  ).map(([prefix, suffix]) => `${prefix}${keyword}${suffix}`)
}

// Generator for names that do NOT contain any keyword
const neutralNameArb = fc.stringMatching(/^[a-zA-Z]{3,10}$/).filter((name) => {
  const lower = name.toLowerCase()
  return ![...numberKeywords, ...dateKeywords, ...boolKeywords].some(k => lower.includes(k))
})

describe('Property 15: Data type recommendation from placeholder name', () => {
  it('names containing number keywords recommend NUMBER', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...numberKeywords).chain(kw => nameWithKeyword(kw)),
        (name) => {
          expect(recommendDataType(name)).toBe('NUMBER')
        },
      ),
      { numRuns: 100 },
    )
  })

  it('names containing date keywords recommend DATE', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...dateKeywords).chain(kw => nameWithKeyword(kw)),
        (name) => {
          // Only if no number keyword takes precedence
          const lower = name.toLowerCase()
          if (numberKeywords.some(k => lower.includes(k))) return // skip — number takes precedence
          expect(recommendDataType(name)).toBe('DATE')
        },
      ),
      { numRuns: 100 },
    )
  })

  it('names containing boolean keywords recommend BOOLEAN', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...boolKeywords).chain(kw => nameWithKeyword(kw)),
        (name) => {
          const lower = name.toLowerCase()
          if (numberKeywords.some(k => lower.includes(k))) return
          if (dateKeywords.some(k => lower.includes(k))) return
          expect(recommendDataType(name)).toBe('BOOLEAN')
        },
      ),
      { numRuns: 100 },
    )
  })

  it('names without any keyword default to STRING', () => {
    fc.assert(
      fc.property(neutralNameArb, (name) => {
        expect(recommendDataType(name)).toBe('STRING')
      }),
      { numRuns: 100 },
    )
  })
})
