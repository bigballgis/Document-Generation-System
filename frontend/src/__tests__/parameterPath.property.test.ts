/**
 * Property 6: Parameter path computation
 * Feature: template-parameter-redesign
 * **Validates: Requirements 1.15**
 *
 * For any parameter in a tree, the computed parameterPath shall equal the
 * dot-joined names traversing from the root ancestor to the parameter itself.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { computeParameterPath } from '@/composables/useParameterUtils'
import type { ParameterDTO } from '@/types/parameter'

// Generator for valid parameter names
const paramNameArb = fc.stringMatching(/^[a-zA-Z_][a-zA-Z0-9_]{0,9}$/)

function makeParam(id: number, name: string, parentId: number | null): ParameterDTO {
  return {
    id,
    templateId: 1,
    parentId,
    name,
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
    parameterPath: '',
    children: [],
    createdAt: '',
    updatedAt: '',
  }
}

describe('Property 6: Parameter path computation', () => {
  it('root parameter path equals its own name', () => {
    fc.assert(
      fc.property(paramNameArb, (name) => {
        const param = makeParam(1, name, null)
        const path = computeParameterPath(param, [param])
        expect(path).toBe(name)
      }),
      { numRuns: 100 },
    )
  })

  it('child parameter path equals parent.child', () => {
    fc.assert(
      fc.property(paramNameArb, paramNameArb, (parentName, childName) => {
        const parent = makeParam(1, parentName, null)
        const child = makeParam(2, childName, 1)
        parent.children = [child]
        const allParams = [parent]

        const path = computeParameterPath(child, allParams)
        expect(path).toBe(`${parentName}.${childName}`)
      }),
      { numRuns: 100 },
    )
  })

  it('deeply nested path equals dot-joined ancestor chain', () => {
    fc.assert(
      fc.property(
        fc.array(paramNameArb, { minLength: 1, maxLength: 5 }),
        (names) => {
          // Build a chain: root → child → grandchild → ...
          const params: ParameterDTO[] = []
          for (let i = 0; i < names.length; i++) {
            const param = makeParam(i + 1, names[i], i === 0 ? null : i)
            params.push(param)
          }
          // Build tree structure
          for (let i = 0; i < params.length - 1; i++) {
            params[i].children = [params[i + 1]]
          }

          const leaf = params[params.length - 1]
          const path = computeParameterPath(leaf, [params[0]])
          expect(path).toBe(names.join('.'))
        },
      ),
      { numRuns: 100 },
    )
  })
})
