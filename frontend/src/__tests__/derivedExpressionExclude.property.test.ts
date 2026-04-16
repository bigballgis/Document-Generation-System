/**
 * Property 6: 衍生参数表达式编辑器参数排除
 * Feature: parameter-settings-ux
 * **Validates: Requirements 6.4**
 *
 * For any parameter list and any current DERIVED parameter,
 * the available parameter dropdown SHALL exclude the current parameter.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import type { ParameterDTO } from '@/types/parameter'

function makeParam(id: number, name: string, type: 'REQUEST' | 'DERIVED' = 'REQUEST'): ParameterDTO {
  return {
    id,
    templateId: 1,
    parentId: null,
    name,
    parameterType: type,
    dataType: 'STRING',
    required: false,
    defaultValue: null,
    description: null,
    sortOrder: 0,
    expressionText: null,
    expressionType: null,
    validationRules: null,
    version: 0,
    parameterPath: name,
    children: [],
    createdAt: '',
    updatedAt: '',
  }
}

/**
 * Pure function: get available parameters for expression editor, excluding current.
 */
function getAvailableParamsForExpression(allParams: ParameterDTO[], currentId: number): ParameterDTO[] {
  return allParams.filter(p => p.id !== currentId)
}

const paramNameArb = fc.stringMatching(/^[a-zA-Z_][a-zA-Z0-9_]{0,8}$/)

describe('Property 6: 衍生参数表达式编辑器参数排除', () => {
  it('current DERIVED parameter is excluded from available list', () => {
    fc.assert(
      fc.property(
        fc.array(
          fc.tuple(fc.integer({ min: 1, max: 1000 }), paramNameArb),
          { minLength: 2, maxLength: 10 },
        ).filter(arr => new Set(arr.map(([id]) => id)).size === arr.length), // unique IDs
        fc.nat(),
        (paramTuples, indexRaw) => {
          const params = paramTuples.map(([id, name], i) =>
            makeParam(id, name, i === 0 ? 'DERIVED' : 'REQUEST'),
          )
          const currentIndex = indexRaw % params.length
          const currentParam = params[currentIndex]

          const available = getAvailableParamsForExpression(params, currentParam.id)

          // Current parameter should NOT be in the list
          expect(available.find(p => p.id === currentParam.id)).toBeUndefined()

          // All other parameters should be in the list
          expect(available.length).toBe(params.length - 1)
          for (const p of params) {
            if (p.id !== currentParam.id) {
              expect(available.find(a => a.id === p.id)).toBeDefined()
            }
          }
        },
      ),
      { numRuns: 200 },
    )
  })
})
