/**
 * Property 3: JSON 导入类型推断正确性
 * Feature: parameter-settings-ux
 * **Validates: Requirements 3.3**
 *
 * For any JSON value, the type inference SHALL produce the correct DataType.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { inferPrimitiveType } from '@/composables/useJsonImport'

describe('Property 3: JSON 导入类型推断正确性', () => {
  it('string values infer to STRING', () => {
    fc.assert(
      fc.property(fc.string(), (val) => {
        expect(inferPrimitiveType(val)).toBe('STRING')
      }),
      { numRuns: 100 },
    )
  })

  it('number values infer to NUMBER', () => {
    fc.assert(
      fc.property(
        fc.oneof(fc.integer(), fc.double({ noNaN: true, noDefaultInfinity: true })),
        (val) => {
          expect(inferPrimitiveType(val)).toBe('NUMBER')
        },
      ),
      { numRuns: 100 },
    )
  })

  it('boolean values infer to BOOLEAN', () => {
    fc.assert(
      fc.property(fc.boolean(), (val) => {
        expect(inferPrimitiveType(val)).toBe('BOOLEAN')
      }),
      { numRuns: 100 },
    )
  })

  it('null infers to STRING', () => {
    expect(inferPrimitiveType(null)).toBe('STRING')
  })

  it('plain objects infer to OBJECT', () => {
    fc.assert(
      fc.property(
        fc.dictionary(fc.string({ minLength: 1, maxLength: 5 }), fc.string()),
        (val) => {
          expect(inferPrimitiveType(val)).toBe('OBJECT')
        },
      ),
      { numRuns: 100 },
    )
  })

  it('arrays infer to ARRAY', () => {
    fc.assert(
      fc.property(fc.array(fc.integer()), (val) => {
        expect(inferPrimitiveType(val)).toBe('ARRAY')
      }),
      { numRuns: 100 },
    )
  })
})
