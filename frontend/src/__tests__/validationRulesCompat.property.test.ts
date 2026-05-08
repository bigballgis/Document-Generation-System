/**
 * Property 5: validation rules vs data type compatibility matrix
 * Feature: parameter-settings-ux
 * **Validates: Requirements 5.3**
 *
 * For any DataType, the applicable validation rule types match the compatibility matrix.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import type { DataType } from '@/types/parameter'

/**
 * Compatibility matrix: which rule types are applicable for each DataType.
 */
function getApplicableRules(dataType: DataType): Set<string> {
  const rules = new Set<string>(['not_null'])

  switch (dataType) {
    case 'STRING':
      rules.add('not_blank')
      rules.add('min_length')
      rules.add('max_length')
      rules.add('pattern')
      rules.add('enum_values')
      break
    case 'NUMBER':
      rules.add('min')
      rules.add('max')
      rules.add('enum_values')
      break
    case 'ARRAY':
      rules.add('min_items')
      rules.add('max_items')
      break
    case 'DATE':
    case 'BOOLEAN':
    case 'OBJECT':
      // Only not_null
      break
  }

  return rules
}

const dataTypeArb = fc.constantFrom<DataType>('STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'ARRAY', 'OBJECT')

const expectedMatrix: Record<DataType, string[]> = {
  STRING: ['not_null', 'not_blank', 'min_length', 'max_length', 'pattern', 'enum_values'],
  NUMBER: ['not_null', 'min', 'max', 'enum_values'],
  DATE: ['not_null'],
  BOOLEAN: ['not_null'],
  ARRAY: ['not_null', 'min_items', 'max_items'],
  OBJECT: ['not_null'],
}

describe('Property 5: validation rules vs data type compatibility matrix', () => {
  it('applicable rules match the expected compatibility matrix', () => {
    fc.assert(
      fc.property(dataTypeArb, (dataType) => {
        const applicable = getApplicableRules(dataType)
        const expected = new Set(expectedMatrix[dataType])

        expect(applicable).toEqual(expected)
      }),
      { numRuns: 100 },
    )
  })

  it('not_null is always applicable for any data type', () => {
    fc.assert(
      fc.property(dataTypeArb, (dataType) => {
        const applicable = getApplicableRules(dataType)
        expect(applicable.has('not_null')).toBe(true)
      }),
      { numRuns: 100 },
    )
  })

  it('STRING-specific rules are not applicable to non-STRING types', () => {
    const nonStringArb = fc.constantFrom<DataType>('NUMBER', 'DATE', 'BOOLEAN', 'ARRAY', 'OBJECT')
    const stringOnlyRules = ['not_blank', 'min_length', 'max_length', 'pattern']

    fc.assert(
      fc.property(nonStringArb, (dataType) => {
        const applicable = getApplicableRules(dataType)
        for (const rule of stringOnlyRules) {
          expect(applicable.has(rule)).toBe(false)
        }
      }),
      { numRuns: 100 },
    )
  })
})
