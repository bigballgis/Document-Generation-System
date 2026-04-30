/**
 * Property 2: JSON import structural correctness
 * Feature: parameter-settings-ux
 * **Validates: Requirements 3.2, 3.4, 3.5, 3.7**
 *
 * For any valid JSON object, the import engine SHALL produce a correct parameter tree.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { parseAndInfer } from '@/composables/useJsonImport'

const validKeyArb = fc.stringMatching(/^[a-zA-Z_][a-zA-Z0-9_]{0,8}$/)

// Generate shallow JSON objects (depth 1-2) to keep tests fast
const shallowJsonArb = fc.dictionary(
  validKeyArb,
  fc.oneof(
    fc.string(),
    fc.integer(),
    fc.boolean(),
    fc.constant(null),
  ),
  { minKeys: 1, maxKeys: 5 },
)

function getMaxDepth(params: any[], currentDepth: number): number {
  let max = currentDepth
  for (const p of params) {
    if (p.children && p.children.length > 0) {
      max = Math.max(max, getMaxDepth(p.children, currentDepth + 1))
    }
  }
  return max
}

describe('Property 2: JSON import structural correctness', () => {
  it('each top-level key becomes a root parameter', () => {
    fc.assert(
      fc.property(shallowJsonArb, (obj) => {
        const jsonStr = JSON.stringify(obj)
        const result = parseAndInfer(jsonStr)
        const keys = Object.keys(obj)
        expect(result.parameters.length).toBe(keys.length)
        const paramNames = result.parameters.map(p => p.name)
        for (const key of keys) {
          expect(paramNames).toContain(key)
        }
      }),
      { numRuns: 100 },
    )
  })

  it('nested objects become OBJECT parameters with children', () => {
    const nestedObjArb = fc.dictionary(
      validKeyArb,
      fc.dictionary(validKeyArb, fc.string(), { minKeys: 1, maxKeys: 3 }),
      { minKeys: 1, maxKeys: 3 },
    )

    fc.assert(
      fc.property(nestedObjArb, (obj) => {
        const jsonStr = JSON.stringify(obj)
        const result = parseAndInfer(jsonStr)
        for (const [key, value] of Object.entries(obj)) {
          const param = result.parameters.find(p => p.name === key)
          expect(param).toBeDefined()
          expect(param!.dataType).toBe('OBJECT')
          const childNames = ((param as any).children ?? []).map((c: any) => c.name)
          for (const childKey of Object.keys(value)) {
            expect(childNames).toContain(childKey)
          }
        }
      }),
      { numRuns: 100 },
    )
  })

  it('arrays with object elements become ARRAY with children from union of keys', () => {
    const arrObjArb = fc.dictionary(
      validKeyArb,
      fc.array(
        fc.dictionary(validKeyArb, fc.string(), { minKeys: 1, maxKeys: 3 }),
        { minLength: 1, maxLength: 3 },
      ),
      { minKeys: 1, maxKeys: 3 },
    )

    fc.assert(
      fc.property(arrObjArb, (obj) => {
        const jsonStr = JSON.stringify(obj)
        const result = parseAndInfer(jsonStr)
        for (const [key, arr] of Object.entries(obj)) {
          const param = result.parameters.find(p => p.name === key)
          expect(param).toBeDefined()
          expect(param!.dataType).toBe('ARRAY')
          // Union of all keys
          const allKeys = new Set<string>()
          for (const el of arr) {
            for (const k of Object.keys(el)) allKeys.add(k)
          }
          const childNames = new Set(((param as any).children ?? []).map((c: any) => c.name))
          for (const k of allKeys) {
            expect(childNames.has(k)).toBe(true)
          }
        }
      }),
      { numRuns: 100 },
    )
  })

  it('tree depth never exceeds 5 levels', () => {
    // Generate deeply nested JSON
    const deepJsonArb = fc.constant(null).map(() => {
      let obj: any = { leaf: 'value' }
      for (let i = 0; i < 8; i++) {
        obj = { [`level${i}`]: obj }
      }
      return obj
    })

    fc.assert(
      fc.property(deepJsonArb, (obj) => {
        const jsonStr = JSON.stringify(obj)
        const result = parseAndInfer(jsonStr)
        const depth = getMaxDepth(result.parameters, 1)
        expect(depth).toBeLessThanOrEqual(5)
      }),
      { numRuns: 10 },
    )
  })
})
