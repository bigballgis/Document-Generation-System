/**
 * Property 1: 智能命名唯一性
 * Feature: parameter-settings-ux
 * **Validates: Requirements 2.1, 2.2, 2.3, 2.4**
 *
 * For any scope type and any set of existing sibling names,
 * the generated name (a) follows the correct prefix pattern and (b) does not collide.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { generateNamePure, getPrefix } from '@/composables/useParameterNaming'

const scopeArb = fc.constantFrom(
  { parentId: null, parentDataType: null, expectedPrefix: 'param' },
  { parentId: 1, parentDataType: 'OBJECT' as const, expectedPrefix: 'field' },
  { parentId: 2, parentDataType: 'ARRAY' as const, expectedPrefix: 'item' },
)

const existingNamesArb = fc.array(
  fc.stringMatching(/^[a-zA-Z_][a-zA-Z0-9_]{0,15}$/),
  { minLength: 0, maxLength: 30 },
)

describe('Property 1: 智能命名唯一性', () => {
  it('generated name follows correct prefix pattern and is unique among siblings', () => {
    fc.assert(
      fc.property(scopeArb, existingNamesArb, (scope, existingNames) => {
        const prefix = getPrefix(scope.parentId, scope.parentDataType)
        expect(prefix).toBe(scope.expectedPrefix)

        const name = generateNamePure(prefix, existingNames)

        // (a) follows prefix_N pattern
        const regex = new RegExp(`^${scope.expectedPrefix}_\\d+$`)
        expect(name).toMatch(regex)

        // (b) does not collide with existing names
        expect(existingNames).not.toContain(name)
      }),
      { numRuns: 200 },
    )
  })

  it('handles consecutive generation without collision', () => {
    fc.assert(
      fc.property(
        scopeArb,
        fc.integer({ min: 1, max: 20 }),
        (scope, count) => {
          const prefix = getPrefix(scope.parentId, scope.parentDataType)
          const names: string[] = []
          for (let i = 0; i < count; i++) {
            const name = generateNamePure(prefix, names)
            expect(names).not.toContain(name)
            names.push(name)
          }
          // All generated names are unique
          expect(new Set(names).size).toBe(count)
        },
      ),
      { numRuns: 100 },
    )
  })
})
