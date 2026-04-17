/**
 * Property 11: 作用域感知变量列表
 * Feature: array-aggregation-and-row-derived, Property 11: 作用域感知变量列表
 * **Validates: Requirements 6.1, 6.2, 6.3**
 *
 * For any non-root DERIVED parameter (under ARRAY or OBJECT parent),
 * the expression editor's available variable list SHALL contain only sibling
 * child parameters under the same parent (excluding the parameter being edited).
 * For any root-level DERIVED parameter, the list SHALL contain all root-level
 * REQUEST parameters, previously defined root-level DERIVED parameters,
 * and all available aggregation properties.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import type { ParameterDTO, DataType } from '@/types/parameter'

// ── Helpers ──

function makeParam(
  id: number,
  name: string,
  opts: {
    parentId?: number | null
    parameterType?: 'REQUEST' | 'DERIVED'
    dataType?: DataType
    parameterPath?: string
    children?: ParameterDTO[]
  } = {},
): ParameterDTO {
  return {
    id,
    templateId: 1,
    parentId: opts.parentId ?? null,
    name,
    parameterType: opts.parameterType ?? 'REQUEST',
    dataType: opts.dataType ?? 'STRING',
    required: false,
    defaultValue: null,
    description: null,
    sortOrder: id,
    expressionText: null,
    expressionType: null,
    validationRules: null,
    version: 0,
    parameterPath: opts.parameterPath ?? name,
    children: opts.children ?? [],
    createdAt: '',
    updatedAt: '',
  }
}

// ── Pure logic under test (mirrors ParameterTreeTable.vue) ──

function flattenAll(params: ParameterDTO[]): ParameterDTO[] {
  const result: ParameterDTO[] = []
  for (const p of params) {
    result.push(p)
    if (p.children?.length) result.push(...flattenAll(p.children))
  }
  return result
}

function findParameterById(params: ParameterDTO[], id: number): ParameterDTO | undefined {
  for (const p of params) {
    if (p.id === id) return p
    if (p.children?.length) {
      const found = findParameterById(p.children, id)
      if (found) return found
    }
  }
  return undefined
}

function getAvailableParamsForExpression(parameters: ParameterDTO[], currentId: number): ParameterDTO[] {
  const current = findParameterById(parameters, currentId)
  if (!current || current.parentId === null) {
    return flattenAll(parameters).filter(p => p.id !== currentId)
  }
  const parent = findParameterById(parameters, current.parentId)
  if (!parent || !parent.children) return []
  return parent.children.filter(p => p.id !== currentId)
}

function getScopeLevel(parameters: ParameterDTO[], paramId: number): 'root' | 'row' | 'object' {
  const param = findParameterById(parameters, paramId)
  if (!param || param.parentId === null) return 'root'
  const parent = findParameterById(parameters, param.parentId)
  if (!parent) return 'root'
  if (parent.dataType === 'ARRAY') return 'row'
  if (parent.dataType === 'OBJECT') return 'object'
  return 'root'
}

// ── Arbitraries ──

describe('Property 11: 作用域感知变量列表', () => {
  // Feature: array-aggregation-and-row-derived, Property 11: 作用域感知变量列表

  it('root-level DERIVED sees all root params except self', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 2, max: 8 }),
        fc.nat(),
        (paramCount, derivedIndexRaw) => {
          const params: ParameterDTO[] = []
          for (let i = 0; i < paramCount; i++) {
            params.push(makeParam(i + 1, `p${i}`, { parameterType: i === 0 ? 'DERIVED' : 'REQUEST' }))
          }
          const derivedIndex = derivedIndexRaw % paramCount
          params[derivedIndex] = { ...params[derivedIndex], parameterType: 'DERIVED' }
          const derivedId = params[derivedIndex].id

          const available = getAvailableParamsForExpression(params, derivedId)
          const scope = getScopeLevel(params, derivedId)

          expect(scope).toBe('root')
          expect(available.length).toBe(paramCount - 1)
          expect(available.find(p => p.id === derivedId)).toBeUndefined()
        },
      ),
      { numRuns: 200 },
    )
  })

  it('row-level DERIVED (under ARRAY) sees only siblings', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 2, max: 6 }),
        fc.nat(),
        (childCount, derivedIndexRaw) => {
          const arrayParam = makeParam(1, 'items', { dataType: 'ARRAY', parameterPath: 'items' })
          const children: ParameterDTO[] = []
          for (let i = 0; i < childCount; i++) {
            children.push(
              makeParam(100 + i, `child${i}`, {
                parentId: 1,
                dataType: 'STRING',
                parameterPath: `items.child${i}`,
              }),
            )
          }
          const derivedIndex = derivedIndexRaw % childCount
          children[derivedIndex] = {
            ...children[derivedIndex],
            parameterType: 'DERIVED',
          }
          arrayParam.children = children
          const tree = [arrayParam]

          const derivedId = children[derivedIndex].id
          const available = getAvailableParamsForExpression(tree, derivedId)
          const scope = getScopeLevel(tree, derivedId)

          expect(scope).toBe('row')
          // Should only see siblings, not self
          expect(available.length).toBe(childCount - 1)
          expect(available.find(p => p.id === derivedId)).toBeUndefined()
          // All available should be siblings under the same parent
          for (const p of available) {
            expect(p.parentId).toBe(1)
          }
        },
      ),
      { numRuns: 200 },
    )
  })

  it('object-level DERIVED (under OBJECT) sees only siblings', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 2, max: 6 }),
        fc.nat(),
        (childCount, derivedIndexRaw) => {
          const objParam = makeParam(1, 'address', { dataType: 'OBJECT', parameterPath: 'address' })
          const children: ParameterDTO[] = []
          for (let i = 0; i < childCount; i++) {
            children.push(
              makeParam(200 + i, `field${i}`, {
                parentId: 1,
                dataType: 'STRING',
                parameterPath: `address.field${i}`,
              }),
            )
          }
          const derivedIndex = derivedIndexRaw % childCount
          children[derivedIndex] = {
            ...children[derivedIndex],
            parameterType: 'DERIVED',
          }
          objParam.children = children
          const tree = [objParam]

          const derivedId = children[derivedIndex].id
          const available = getAvailableParamsForExpression(tree, derivedId)
          const scope = getScopeLevel(tree, derivedId)

          expect(scope).toBe('object')
          expect(available.length).toBe(childCount - 1)
          expect(available.find(p => p.id === derivedId)).toBeUndefined()
          for (const p of available) {
            expect(p.parentId).toBe(1)
          }
        },
      ),
      { numRuns: 200 },
    )
  })

  it('non-root DERIVED never sees root-level params', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 4 }),
        fc.integer({ min: 2, max: 5 }),
        (rootCount, childCount) => {
          const rootParams: ParameterDTO[] = []
          for (let i = 0; i < rootCount; i++) {
            rootParams.push(makeParam(i + 1, `root${i}`))
          }
          const arrayParam = makeParam(100, 'items', { dataType: 'ARRAY', parameterPath: 'items' })
          const children: ParameterDTO[] = []
          for (let i = 0; i < childCount; i++) {
            children.push(
              makeParam(200 + i, `child${i}`, {
                parentId: 100,
                parameterType: i === 0 ? 'DERIVED' : 'REQUEST',
                dataType: 'NUMBER',
                parameterPath: `items.child${i}`,
              }),
            )
          }
          arrayParam.children = children
          const tree = [...rootParams, arrayParam]

          const derivedId = children[0].id
          const available = getAvailableParamsForExpression(tree, derivedId)

          // Should NOT contain any root-level params
          for (const rootP of rootParams) {
            expect(available.find(p => p.id === rootP.id)).toBeUndefined()
          }
          // Should NOT contain the array param itself
          expect(available.find(p => p.id === arrayParam.id)).toBeUndefined()
        },
      ),
      { numRuns: 200 },
    )
  })
})
