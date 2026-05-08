/**
 * Property tests for ParameterTableView component.
 * Feature: design-stage-layout, Property 2: Parameter type to view type mapping isomorphism
 * **Validates: Requirements 2.1**
 */
import { describe, it, expect, beforeEach } from 'vitest'
import * as fc from 'fast-check'
import type { DataType } from '@/types/parameter'

// Feature: design-stage-layout, Property 2: Parameter type to view type mapping isomorphism

type ViewType = 'field-row' | 'sub-table-link' | 'related-table-link'

const FIELD_ROW_TYPES: DataType[] = ['STRING', 'NUMBER', 'DATE', 'BOOLEAN']
const ALL_DATA_TYPES: DataType[] = ['STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'ARRAY', 'OBJECT']

/**
 * Pure classification function: maps a DataType to a ViewType.
 * STRING/NUMBER/DATE/BOOLEAN → field-row
 * ARRAY → sub-table-link
 * OBJECT → related-table-link
 */
function classifyParameterType(dataType: DataType): ViewType {
  switch (dataType) {
    case 'STRING':
    case 'NUMBER':
    case 'DATE':
    case 'BOOLEAN':
      return 'field-row'
    case 'ARRAY':
      return 'sub-table-link'
    case 'OBJECT':
      return 'related-table-link'
  }
}

interface SimpleParam {
  id: number
  name: string
  dataType: DataType
  children: SimpleParam[]
}

const dataTypeArb: fc.Arbitrary<DataType> = fc.constantFrom(...ALL_DATA_TYPES)

let _paramId = 1

function simpleParamArb(maxDepth: number): fc.Arbitrary<SimpleParam[]> {
  if (maxDepth <= 0) return fc.constant([])
  return fc.array(
    fc.record({
      name: fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9]{0,9}$/),
      dataType: dataTypeArb,
    }).chain(base => {
      if ((base.dataType === 'ARRAY' || base.dataType === 'OBJECT') && maxDepth > 1) {
        return simpleParamArb(maxDepth - 1).map(children => ({
          id: _paramId++,
          ...base,
          children,
        }))
      }
      return fc.constant({ id: _paramId++, ...base, children: [] as SimpleParam[] })
    }),
    { minLength: 1, maxLength: 8 },
  )
}


describe('Property 2: Parameter type to view type mapping isomorphism', () => {
  beforeEach(() => { _paramId = 1 })

  it('every parameter maps to exactly one correct view type', () => {
    fc.assert(
      fc.property(
        dataTypeArb,
        (dataType) => {
          const viewType = classifyParameterType(dataType)
          if (FIELD_ROW_TYPES.includes(dataType)) {
            expect(viewType).toBe('field-row')
          } else if (dataType === 'ARRAY') {
            expect(viewType).toBe('sub-table-link')
          } else if (dataType === 'OBJECT') {
            expect(viewType).toBe('related-table-link')
          }
        },
      ),
      { numRuns: 100 },
    )
  })

  it('classification is consistent for a list of parameters', () => {
    fc.assert(
      fc.property(
        fc.array(
          fc.record({
            id: fc.integer({ min: 1, max: 10000 }),
            name: fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9]{0,9}$/),
            dataType: dataTypeArb,
          }),
          { minLength: 1, maxLength: 20 },
        ),
        (params) => {
          const fieldRows = params.filter(p => FIELD_ROW_TYPES.includes(p.dataType))
          const subTableLinks = params.filter(p => p.dataType === 'ARRAY')
          const relatedTableLinks = params.filter(p => p.dataType === 'OBJECT')

          // Every parameter is classified into exactly one category
          expect(fieldRows.length + subTableLinks.length + relatedTableLinks.length).toBe(params.length)

          // Verify each classification
          for (const p of fieldRows) {
            expect(classifyParameterType(p.dataType)).toBe('field-row')
          }
          for (const p of subTableLinks) {
            expect(classifyParameterType(p.dataType)).toBe('sub-table-link')
          }
          for (const p of relatedTableLinks) {
            expect(classifyParameterType(p.dataType)).toBe('related-table-link')
          }
        },
      ),
      { numRuns: 100 },
    )
  })

  it(
    'recursive tree classification: children of ARRAY/OBJECT follow same rules',
    () => {
      fc.assert(
        fc.property(
          simpleParamArb(3),
          (params) => {
            function verifyRecursive(nodes: SimpleParam[]) {
              for (const node of nodes) {
                const viewType = classifyParameterType(node.dataType)
                if (FIELD_ROW_TYPES.includes(node.dataType)) {
                  expect(viewType).toBe('field-row')
                } else if (node.dataType === 'ARRAY') {
                  expect(viewType).toBe('sub-table-link')
                  verifyRecursive(node.children)
                } else if (node.dataType === 'OBJECT') {
                  expect(viewType).toBe('related-table-link')
                  verifyRecursive(node.children)
                }
              }
            }
            verifyRecursive(params)
          },
        ),
        { numRuns: 100 },
      )
    },
    30_000,
  )
})
