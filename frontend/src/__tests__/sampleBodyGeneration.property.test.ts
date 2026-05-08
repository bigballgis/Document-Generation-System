/**
 * Property 26: Sample request body generation
 * Feature: template-parameter-redesign
 * **Validates: Requirements 4.24**
 *
 * For any parameter tree, the sample request body shall contain all parameters
 * in the correct nested JSON structure with example values based on data_type.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { generateSampleBody } from '@/composables/useParameterUtils'
import type { ParameterDTO, DataType } from '@/types/parameter'

function makeParam(
  id: number,
  name: string,
  dataType: DataType,
  defaultValue: string | null = null,
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
    defaultValue,
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

describe('Property 26: Sample request body generation', () => {
  it('STRING parameters produce string example values', () => {
    fc.assert(
      fc.property(paramNameArb, (name) => {
        const param = makeParam(1, name, 'STRING')
        const body = generateSampleBody([param])
        expect(typeof body[name]).toBe('string')
      }),
      { numRuns: 100 },
    )
  })

  it('NUMBER parameters produce number example values', () => {
    fc.assert(
      fc.property(paramNameArb, (name) => {
        const param = makeParam(1, name, 'NUMBER')
        const body = generateSampleBody([param])
        expect(typeof body[name]).toBe('number')
      }),
      { numRuns: 100 },
    )
  })

  it('BOOLEAN parameters produce boolean example values', () => {
    fc.assert(
      fc.property(paramNameArb, (name) => {
        const param = makeParam(1, name, 'BOOLEAN')
        const body = generateSampleBody([param])
        expect(typeof body[name]).toBe('boolean')
      }),
      { numRuns: 100 },
    )
  })

  it('OBJECT parameters produce nested object', () => {
    fc.assert(
      fc.property(paramNameArb, paramNameArb, (objName, childName) => {
        if (objName === childName) return
        const child = makeParam(2, childName, 'STRING')
        child.parentId = 1
        const obj = makeParam(1, objName, 'OBJECT', null, [child])
        const body = generateSampleBody([obj])
        expect(typeof body[objName]).toBe('object')
        expect(body[objName]).not.toBeNull()
        expect((body[objName] as any)[childName]).toBeDefined()
      }),
      { numRuns: 100 },
    )
  })

  it('ARRAY parameters produce array with one element', () => {
    fc.assert(
      fc.property(paramNameArb, paramNameArb, (arrName, childName) => {
        if (arrName === childName) return
        const child = makeParam(2, childName, 'NUMBER')
        child.parentId = 1
        const arr = makeParam(1, arrName, 'ARRAY', null, [child])
        const body = generateSampleBody([arr])
        expect(Array.isArray(body[arrName])).toBe(true)
        expect((body[arrName] as any[]).length).toBe(1)
        expect((body[arrName] as any[])[0]).toHaveProperty(childName)
      }),
      { numRuns: 100 },
    )
  })

  it('default_value is used when defined', () => {
    fc.assert(
      fc.property(
        paramNameArb,
        fc.stringMatching(/^[a-zA-Z]{1,10}$/),
        (name, defaultVal) => {
          const param = makeParam(1, name, 'STRING', defaultVal)
          const body = generateSampleBody([param])
          expect(body[name]).toBe(defaultVal)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('DERIVED parameters are excluded from sample body', () => {
    fc.assert(
      fc.property(paramNameArb, (name) => {
        const param = makeParam(1, name, 'STRING')
        param.parameterType = 'DERIVED'
        param.expressionText = 'test'
        param.expressionType = 'JAVASCRIPT'
        const body = generateSampleBody([param])
        expect(body).not.toHaveProperty(name)
      }),
      { numRuns: 100 },
    )
  })
})
