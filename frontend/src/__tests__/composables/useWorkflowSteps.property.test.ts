/**
 * Property 7: useWorkflowSteps 内联段落适配
 * Feature: remove-segment-library
 * Validates: Requirements 6.7
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import type { WorkflowStep } from '@/types/workspace'
import type { AssemblyConfig, CompositeCoverageReport, AssemblySegmentEntry } from '@/types/segment'

// Pure logic extracted from useWorkflowSteps for testability
function hasEnabledSegment(config: AssemblyConfig | null): boolean {
  return config?.segments?.some(s => s.enabled) ?? false
}

function allSegmentsEdited(config: AssemblyConfig | null): boolean {
  const enabled = config?.segments?.filter(s => s.enabled) ?? []
  if (enabled.length === 0) return false
  // Changed: check filePath non-empty instead of lockedVersion
  return enabled.every(s => s.filePath != null && s.filePath.length > 0)
}

function hasFullCoverage(cov: CompositeCoverageReport | null): boolean {
  if (!cov) return false
  return cov.overallCoveragePercent >= 100 && cov.segmentCoverages.length > 0
}

function computeSteps(state: {
  templateExists: boolean
  dataSources: number
  expressions: number
  assemblyConfig: AssemblyConfig | null
  coverage: CompositeCoverageReport | null
  status: string
}): WorkflowStep[] {
  const steps: WorkflowStep[] = [
    { key: 'create', label: '', completed: state.templateExists, active: false, alwaysAvailable: false },
    { key: 'data', label: '', completed: state.dataSources > 0 || state.expressions > 0, active: false, alwaysAvailable: false },
    { key: 'segments', label: '', completed: hasEnabledSegment(state.assemblyConfig), active: false, alwaysAvailable: false },
    { key: 'editor', label: '', completed: allSegmentsEdited(state.assemblyConfig), active: false, alwaysAvailable: false },
    { key: 'testing', label: '', completed: hasFullCoverage(state.coverage), active: false, alwaysAvailable: false },
    { key: 'review', label: '', completed: state.status === 'ACTIVE', active: false, alwaysAvailable: false },
    { key: 'export', label: '', completed: false, active: false, alwaysAvailable: true },
    { key: 'settings', label: '', completed: false, active: false, alwaysAvailable: true },
  ]
  if (state.status === 'DRAFT') {
    const idx = steps.findIndex(s => !s.completed && !s.alwaysAvailable)
    if (idx >= 0) steps[idx].active = true
  }
  return steps
}

// Generators (inline mode)
const segmentEntryArb: fc.Arbitrary<AssemblySegmentEntry> = fc.record({
  filePath: fc.oneof(
    fc.constant(''),
    fc.stringMatching(/^segments\/\d+\/[a-z]+\.docx$/),
  ),
  name: fc.string({ minLength: 1, maxLength: 30 }),
  segmentType: fc.option(
    fc.constantFrom('COVER', 'TOC', 'CHAPTER', 'TABLE', 'SIGNATURE', 'LEGAL', 'APPENDIX'),
    { nil: null },
  ),
  position: fc.nat(),
  enabled: fc.boolean(),
  pageBreakBefore: fc.boolean(),
  conditionExpression: fc.constant(null),
  dataScope: fc.constant(null),
})

const assemblyConfigArb = fc.array(segmentEntryArb, { minLength: 0, maxLength: 20 }).map(
  segments => ({ segments }) as AssemblyConfig,
)

const coverageArb = fc.oneof(
  fc.constant(null as CompositeCoverageReport | null),
  fc.record({
    overallCoveragePercent: fc.double({ min: 0, max: 100, noNaN: true }),
    segmentCoverages: fc.array(
      fc.record({
        segmentName: fc.string(),
        totalVariables: fc.nat(),
        boundVariables: fc.nat(),
        coveragePercent: fc.double({ min: 0, max: 100, noNaN: true }),
      }),
      { minLength: 0, maxLength: 5 },
    ),
  }),
)

const statusArb = fc.constantFrom('DRAFT', 'PENDING_REVIEW', 'REVIEWED', 'ACTIVE', 'ARCHIVED')

const workspaceStateArb = fc.record({
  templateExists: fc.boolean(),
  dataSources: fc.integer({ min: 0, max: 10 }),
  expressions: fc.integer({ min: 0, max: 10 }),
  assemblyConfig: assemblyConfigArb,
  coverage: coverageArb,
  status: statusArb,
})

describe('useWorkflowSteps - Property Tests (inline mode)', () => {
  it('Property 7: hasEnabledSegment returns true iff at least one segment has enabled===true; allSegmentsEdited returns true iff all enabled segments have non-empty filePath', () => {
    fc.assert(
      fc.property(workspaceStateArb, (state) => {
        const steps = computeSteps(state)

        // Step 3: segments - hasEnabledSegment
        const hasEnabled = state.assemblyConfig?.segments?.some(s => s.enabled) ?? false
        expect(steps[2].completed).toBe(hasEnabled)

        // Step 4: editor - allSegmentsEdited (inline: check filePath non-empty)
        const enabled = state.assemblyConfig?.segments?.filter(s => s.enabled) ?? []
        const allEdited = enabled.length > 0 && enabled.every(s => s.filePath != null && s.filePath.length > 0)
        expect(steps[3].completed).toBe(allEdited)

        // DRAFT active step
        if (state.status === 'DRAFT') {
          const activeSteps = steps.filter(s => s.active)
          const incompleteNonAlways = steps.filter(s => !s.completed && !s.alwaysAvailable)
          if (incompleteNonAlways.length > 0) {
            expect(activeSteps).toHaveLength(1)
            expect(activeSteps[0].key).toBe(incompleteNonAlways[0].key)
          } else {
            expect(activeSteps).toHaveLength(0)
          }
        } else {
          expect(steps.filter(s => s.active)).toHaveLength(0)
        }
      }),
      { numRuns: 200 },
    )
  })
})
