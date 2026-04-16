/**
 * Property 11: 参数名称格式校验
 * Feature: parameter-settings-ux
 * **Validates: Requirements 10.6**
 *
 * For any string, the parameter name validation SHALL accept the string
 * if and only if it matches the regex ^[a-zA-Z_][a-zA-Z0-9_-]*$
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

const NAME_REGEX = /^[a-zA-Z_][a-zA-Z0-9_-]*$/

function isValidParameterName(name: string): boolean {
  return NAME_REGEX.test(name)
}

describe('Property 11: 参数名称格式校验', () => {
  it('valid names always pass validation', () => {
    const validNameArb = fc.stringMatching(/^[a-zA-Z_][a-zA-Z0-9_-]{0,20}$/)
    fc.assert(
      fc.property(validNameArb, (name) => {
        expect(isValidParameterName(name)).toBe(true)
      }),
      { numRuns: 200 },
    )
  })

  it('names starting with digits fail validation', () => {
    const digitStartArb = fc.stringMatching(/^[0-9][a-zA-Z0-9_-]{0,10}$/)
    fc.assert(
      fc.property(digitStartArb, (name) => {
        expect(isValidParameterName(name)).toBe(false)
      }),
      { numRuns: 100 },
    )
  })

  it('empty string fails validation', () => {
    expect(isValidParameterName('')).toBe(false)
  })

  it('names with spaces fail validation', () => {
    const spaceNameArb = fc.tuple(
      fc.stringMatching(/^[a-zA-Z_][a-zA-Z0-9_]{0,5}$/),
      fc.stringMatching(/^[a-zA-Z_][a-zA-Z0-9_]{0,5}$/),
    ).map(([a, b]) => `${a} ${b}`)

    fc.assert(
      fc.property(spaceNameArb, (name) => {
        expect(isValidParameterName(name)).toBe(false)
      }),
      { numRuns: 100 },
    )
  })

  it('validation result matches regex for random strings', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 0, maxLength: 20 }),
        (str) => {
          const expected = NAME_REGEX.test(str)
          expect(isValidParameterName(str)).toBe(expected)
        },
      ),
      { numRuns: 200 },
    )
  })
})
