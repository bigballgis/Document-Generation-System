/**
 * Property 7: Undo/Redo stack invariants
 * Feature: parameter-settings-ux
 * **Validates: Requirements 8.2, 8.3, 8.6**
 *
 * For any sequence of push, undo, and redo operations:
 * (a) stack size never exceeds maxSize
 * (b) pushing a new operation clears the redo stack
 * (c) undo/redo are symmetric
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { useUndoRedo } from '@/composables/useUndoRedo'
import type { UndoRedoOperation } from '@/composables/useUndoRedo'

function makeOp(type: 'add' | 'edit' | 'delete'): UndoRedoOperation {
  return {
    type,
    timestamp: Date.now(),
    payload: { parameterId: 1, templateId: 1 } as any,
  }
}

const opTypeArb = fc.constantFrom('add' as const, 'edit' as const, 'delete' as const)
const actionArb = fc.constantFrom('push', 'undo', 'redo')

describe('Property 7: Undo/Redo stack invariants', () => {
  it('stack size never exceeds maxSize', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 10 }),
        fc.array(opTypeArb, { minLength: 1, maxLength: 50 }),
        (maxSize, opTypes) => {
          const ur = useUndoRedo(maxSize)
          for (const type of opTypes) {
            ur.push(makeOp(type))
          }
          expect(ur.undoStack.value.length).toBeLessThanOrEqual(maxSize)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('pushing a new operation clears the redo stack', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.integer({ min: 1, max: 5 }),
        fc.integer({ min: 1, max: 5 }),
        async (pushCount, undoCount) => {
          const ur = useUndoRedo(50)

          for (let i = 0; i < pushCount; i++) {
            ur.push(makeOp('add'))
          }

          const actualUndos = Math.min(undoCount, ur.undoStack.value.length)
          for (let i = 0; i < actualUndos; i++) {
            await ur.undo()
          }

          if (actualUndos > 0) {
            expect(ur.redoStack.value.length).toBeGreaterThan(0)
          }

          ur.push(makeOp('edit'))
          expect(ur.redoStack.value.length).toBe(0)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('undo then redo restores the operation to undo stack', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.integer({ min: 1, max: 10 }),
        async (count) => {
          const ur = useUndoRedo(50)

          for (let i = 0; i < count; i++) {
            ur.push(makeOp('add'))
          }

          const beforeUndo = ur.undoStack.value.length

          await ur.undo()
          expect(ur.undoStack.value.length).toBe(beforeUndo - 1)
          expect(ur.redoStack.value.length).toBe(1)

          await ur.redo()
          expect(ur.undoStack.value.length).toBe(beforeUndo)
          expect(ur.redoStack.value.length).toBe(0)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('random action sequences maintain valid state', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.array(actionArb, { minLength: 1, maxLength: 30 }),
        async (actions) => {
          const maxSize = 10
          const ur = useUndoRedo(maxSize)

          for (const action of actions) {
            if (action === 'push') {
              ur.push(makeOp('add'))
            } else if (action === 'undo') {
              await ur.undo()
            } else {
              await ur.redo()
            }

            expect(ur.undoStack.value.length).toBeGreaterThanOrEqual(0)
            expect(ur.redoStack.value.length).toBeGreaterThanOrEqual(0)
            expect(ur.undoStack.value.length).toBeLessThanOrEqual(maxSize)
          }
        },
      ),
      { numRuns: 100 },
    )
  })
})
