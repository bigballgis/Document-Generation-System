/**
 * Property 24: JSON Schema generation
 * Feature: template-parameter-redesign
 * **Validates: Requirements 4.23**
 *
 * For any parameter tree, OBJECT parameters produce "properties" key
 * and ARRAY parameters produce "items" key in the generated JSON Schema.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { generateJsonSchema } from '@/composables/useParameterUtils'
import type { ParameterDTO, DataType } from '@/types/parameter'

const leafTypes: DataType[] = ['STRING', 'NUMBER', 'DATE', 'BOOLEAN']

function makeParam(
  id: number,
  name: string,
  dataType: DataType,
  children: ParameterDTO[] = [],
): ParameterDTO {
  return {
    id,
    templateId: 1,
    parentId: null,
    name,
    parameterType: 'REQUEST',
    dataType,
    required: false,
    defaultValue: null,
    description: null,
    sortOrder: 0,
    expressionText: null,
    expressionType: null,
    validationRules: null,
    version: 0,
    parameterPath: name,
    children,
    createdAt: '',
    updatedAt: '',
  }
}

const paramNameArb = fc.stringMatching(/^[a-zA-Z_][a-zA-Z0-9_]{0,9}$/)

describe('Property 24: JSON Schema generation', () => {
  it('root schema is always type object with properties', () => {
    fc.assert(
      fc.property(
        fc.array(paramNameArb, { minLength: 0, maxLength: 5 }),
        (names) => {
          const uniqueNames = [...new Set(names)]
          const params = uniqueNames.map((n, i) => makeParam(i + 1, n, 'STRING'))
          const schema = generateJsonSchema(params)
          expect(schema.type).toBe('object')
          expect(schema).toHaveProperty('properties')
        },
      ),
      { numRuns: 100 },
    )
  })

  it('OBJECT parameters produce properties key in schema', () => {
    fc.assert(
      fc.property(
        paramNameArb,
        paramNameArb,
        (objName, childName) => {
          if (objName === childName) return // skip duplicate
          const child = makeParam(2, childName, 'STRING')
          child.parentId = 1
          const obj = makeParam(1, objName, 'OBJECT', [child])
          const schema = generateJsonSchema([obj])
          const objSchema = (schema.properties as any)[objName]
          expect(objSchema.type).toBe('object')
          expect(objSchema).toHaveProperty('properties')
          expect(objSchema.properties).toHaveProperty(childName)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('ARRAY parameters produce items key in schema', () => {
    fc.assert(
      fc.property(
        paramNameArb,
        paramNameArb,
        (arrName, childName) => {
          if (arrName === childName) return
          const child = makeParam(2, childName, 'STRING')
          child.parentId = 1
          const arr = makeParam(1, arrName, 'ARRAY', [child])
          const schema = generateJsonSchema([arr])
          const arrSchema = (schema.properties as any)[arrName]
          expect(arrSchema.type).toBe('array')
          expect(arrSchema).toHaveProperty('items')
          expect(arrSchema.items).toHaveProperty('properties')
          expect(arrSchema.items.properties).toHaveProperty(childName)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('leaf type parameters map to correct JSON Schema types', () => {
    fc.assert(
      fc.property(
        paramNameArb,
        fc.constantFrom(...leafTypes),
        (name, dataType) => {
          const param = makeParam(1, name, dataType)
          const schema = generateJsonSchema([param])
          const propSchema = (schema.properties as any)[name]
          const expectedType = dataType === 'DATE' ? 'string'
            : dataType === 'NUMBER' ? 'number'
            : dataType === 'BOOLEAN' ? 'boolean'
            : 'string'
          expect(propSchema.type).toBe(expectedType)
        },
      ),
      { numRuns: 100 },
    )
  })
})
