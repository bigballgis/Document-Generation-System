/**
 * Property 8: parameter deep copy completeness
 * Feature: parameter-settings-ux
 * **Validates: Requirements 8.5, 11.2**
 *
 * For any parameter tree, a deep copy SHALL produce a new tree where all attribute
 * values are identical to the source (except id, name suffix, parentId).
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import type { ParameterDTO, DataType, ParameterType, ExpressionType } from '@/types/parameter'

function makeParam(overrides: Partial<ParameterDTO> = {}): ParameterDTO {
  return {
    id: 1,
    templateId: 1,
    parentId: null,
    name: 'test',
    parameterType: 'REQUEST',
    dataType: 'STRING',
    required: false,
    defaultValue: null,
    description: null,
    sortOrder: 0,
    expressionText: null,
    expressionType: null,
    validationRules: null,
    version: 0,
    parameterPath: 'test',
    children: [],
    createdAt: '',
    updatedAt: '',
    ...overrides,
  }
}

/**
 * Pure deep copy function — mirrors the logic in ParameterTableTab.
 */
function deepCopyParam(source: ParameterDTO, nameSuffix: string): ParameterDTO {
  const copy: ParameterDTO = {
    ...source,
    id: -1, // new ID would be assigned by server
    name: source.name + nameSuffix,
    parentId: null, // adjusted by caller
    children: source.children.map(child => deepCopyParam(child, '')),
  }
  return copy
}

function verifyDeepCopy(source: ParameterDTO, copy: ParameterDTO, isRoot: boolean) {
  // Attributes that should be identical
  expect(copy.dataType).toBe(source.dataType)
  expect(copy.required).toBe(source.required)
  expect(copy.defaultValue).toBe(source.defaultValue)
  expect(copy.description).toBe(source.description)
  expect(copy.parameterType).toBe(source.parameterType)
  expect(copy.expressionText).toBe(source.expressionText)
  expect(copy.expressionType).toBe(source.expressionType)
  expect(JSON.stringify(copy.validationRules)).toBe(JSON.stringify(source.validationRules))

  // Name should have suffix only at root
  if (isRoot) {
    expect(copy.name).toBe(source.name + '_copy')
  } else {
    expect(copy.name).toBe(source.name)
  }

  // Children should be recursively copied
  expect(copy.children.length).toBe(source.children.length)
  for (let i = 0; i < source.children.length; i++) {
    verifyDeepCopy(source.children[i], copy.children[i], false)
  }
}

const dataTypeArb = fc.constantFrom<DataType>('STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'ARRAY', 'OBJECT')
const paramTypeArb = fc.constantFrom<ParameterType>('REQUEST', 'DERIVED')
const exprTypeArb = fc.constantFrom<ExpressionType | null>('JAVASCRIPT', 'EXCEL_FORMULA', null)
const nameArb = fc.stringMatching(/^[a-zA-Z_][a-zA-Z0-9_]{0,8}$/)

const leafParamArb = fc.record({
  name: nameArb,
  dataType: dataTypeArb,
  parameterType: paramTypeArb,
  required: fc.boolean(),
  defaultValue: fc.option(fc.string({ maxLength: 20 }), { nil: null }),
  description: fc.option(fc.string({ maxLength: 30 }), { nil: null }),
  expressionText: fc.option(fc.string({ maxLength: 20 }), { nil: null }),
  expressionType: exprTypeArb,
}).map(r => makeParam({
  ...r,
  id: Math.floor(Math.random() * 10000),
}))

describe('Property 8: parameter deep copy completeness', () => {
  it('deep copy preserves all attributes except id, name suffix, parentId', () => {
    fc.assert(
      fc.property(leafParamArb, (source) => {
        const copy = deepCopyParam(source, '_copy')
        verifyDeepCopy(source, copy, true)
      }),
      { numRuns: 200 },
    )
  })

  it('deep copy with children preserves nested structure', () => {
    fc.assert(
      fc.property(
        leafParamArb,
        fc.array(leafParamArb, { minLength: 1, maxLength: 3 }),
        (parent, children) => {
          parent.dataType = 'OBJECT'
          parent.children = children.map((c, i) => ({ ...c, id: i + 100, parentId: parent.id }))

          const copy = deepCopyParam(parent, '_copy')
          verifyDeepCopy(parent, copy, true)
        },
      ),
      { numRuns: 100 },
    )
  })
})
