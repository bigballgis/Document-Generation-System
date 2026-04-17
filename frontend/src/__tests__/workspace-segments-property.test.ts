// Feature: remove-segment-library, Property 6: useAssemblyConfig 操作不变量
// **Validates: Requirements 6.5**

import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import type { AssemblySegmentEntry } from '@/types/segment'
import { useAssemblyConfig } from '@/composables/useAssemblyConfig'

// ── Generators (inline mode: filePath/name/segmentType instead of segmentId) ──

const arbAssemblySegmentEntry: fc.Arbitrary<AssemblySegmentEntry> = fc.record({
  filePath: fc.stringMatching(/^segments\/\d+\/[a-z]+\.docx$/),
  name: fc.string({ minLength: 1, maxLength: 50 }),
  segmentType: fc.option(
    fc.constantFrom('COVER', 'TOC', 'CHAPTER', 'TABLE', 'SIGNATURE', 'LEGAL', 'APPENDIX'),
    { nil: null },
  ),
  position: fc.integer({ min: 0, max: 99 }),
  enabled: fc.boolean(),
  pageBreakBefore: fc.boolean(),
  conditionExpression: fc.option(fc.string({ minLength: 0, maxLength: 30 }), { nil: null }),
  dataScope: fc.option(
    fc.dictionary(
      fc.string({ minLength: 1, maxLength: 10 }),
      fc.string({ minLength: 0, maxLength: 20 }),
      { minKeys: 0, maxKeys: 3 },
    ),
    { nil: null },
  ),
  headerFilePath: fc.option(fc.stringMatching(/^segments\/\d+\/headers\/[a-z]+\.docx$/), { nil: null }),
  footerFilePath: fc.option(fc.stringMatching(/^segments\/\d+\/footers\/[a-z]+\.docx$/), { nil: null }),
  pageNumberFormat: fc.option(fc.constantFrom('ARABIC', 'ROMAN', 'ALPHA'), { nil: null }),
  pageNumberStart: fc.option(fc.integer({ min: 1, max: 100 }), { nil: null }),
})

type Operation =
  | { type: 'setSegments'; segments: AssemblySegmentEntry[] }
  | { type: 'addSegment'; entry: AssemblySegmentEntry }
  | { type: 'removeSegment'; index: number }
  | { type: 'updateSegment'; index: number; patch: Partial<AssemblySegmentEntry> }

function applyOperation(config: ReturnType<typeof useAssemblyConfig>, op: Operation) {
  switch (op.type) {
    case 'setSegments':
      config.setSegments(op.segments)
      break
    case 'addSegment':
      config.addSegment(op.entry)
      break
    case 'removeSegment':
      config.removeSegment(op.index)
      break
    case 'updateSegment':
      config.updateSegment(op.index, op.patch)
      break
  }
}

describe('Property 6: useAssemblyConfig 操作不变量 (inline mode)', () => {
  it('after each operation, positions are consecutive 0..length-1, and undo restores previous state', () => {
    fc.assert(
      fc.property(
        fc.array(arbAssemblySegmentEntry, { minLength: 1, maxLength: 20 }),
        fc.integer({ min: 0, max: 3 }),
        fc.array(arbAssemblySegmentEntry, { minLength: 0, maxLength: 20 }),
        arbAssemblySegmentEntry,
        fc.integer({ min: 0, max: 19 }),
        fc.boolean(),
        (initialSegments, opType, newSegments, newEntry, rawIndex, patchValue) => {
          const config = useAssemblyConfig(initialSegments)

          // Capture state before operation
          const stateBefore = JSON.parse(JSON.stringify(config.segments.value))

          // Build a valid operation
          const len = config.segments.value.length
          let operation: Operation

          switch (opType) {
            case 0:
              operation = { type: 'setSegments', segments: newSegments }
              break
            case 1:
              operation = { type: 'addSegment', entry: newEntry }
              break
            case 2:
              operation = { type: 'removeSegment', index: rawIndex % len }
              break
            case 3:
            default:
              operation = {
                type: 'updateSegment',
                index: rawIndex % len,
                patch: { enabled: patchValue },
              }
              break
          }

          // Apply the operation
          applyOperation(config, operation)

          // After operation: for removeSegment, positions must be consecutive 0..length-1
          const segments = config.segments.value
          if (operation.type === 'removeSegment') {
            for (let i = 0; i < segments.length; i++) {
              expect(segments[i].position).toBe(i)
            }
          }

          // Capture state after operation
          const stateAfter = JSON.parse(JSON.stringify(config.segments.value))

          // canUndo should be true after an operation
          expect(config.canUndo.value).toBe(true)

          // Undo should restore previous state
          config.undo()
          expect(JSON.parse(JSON.stringify(config.segments.value))).toEqual(stateBefore)

          // Redo should restore post-operation state
          config.redo()
          expect(JSON.parse(JSON.stringify(config.segments.value))).toEqual(stateAfter)
        },
      ),
      { numRuns: 100 },
    )
  })
})
