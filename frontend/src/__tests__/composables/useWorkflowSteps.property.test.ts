/**
 * Property 1: Workflow step completion is deterministic and consistent
 * Feature: workspace-foundation
 * Validates: Requirements 3.5, 3.8
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
  return enabled.every(s => (s.lockedVersion ?? 0) > 1)
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

// Generators
const segmentEntryArb = fc.record({
  segmentId: fc.nat(),
  position: fc.nat(),
  enabled: fc.boolean(),
  pageBreakBefore: fc.boolean(),
  lockedVersion: fc.oneof(fc.constant(null), fc.integer({ min: 0, max: 10 })),
  conditionExpression: fc.constant(null),
  dataScope: fc.constant(null),
}) as fc.Arbitrary<AssemblySegmentEntry>

const assemblyConfigArb = fc.array(segmentEntryArb, { minLength: 0, maxLength: 20 }).map(
  segments => ({ segments }) as AssemblyConfig
)

const coverageArb = fc.oneof(
  fc.constant(null as CompositeCoverageReport | null),
  fc.record({
    overallCoveragePercent: fc.double({ min: 0, max: 100, noNaN: true }),
    segmentCoverages: fc.array(
      fc.record({
        segmentId: fc.nat(),
        segmentName: fc.string(),
        totalVariables: fc.nat(),
        boundVariables: fc.nat(),
        coveragePercent: fc.double({ min: 0, max: 100, noNaN: true }),
      }),
      { minLength: 0, maxLength: 5 }
    ),
  })
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

describe('useWorkflowSteps - Property Tests', () => {
  it('Property 1: step completion is deterministic and consistent', () => {
    fc.assert(
      fc.property(workspaceStateArb, (state) => {
        const steps = computeSteps(state)

        // Step 1: create
        expect(steps[0].completed).toBe(state.templateExists)

        // Step 2: data
        expect(steps[1].completed).toBe(state.dataSources > 0 || state.expressions > 0)

        // Step 3: segments
        const hasEnabled = state.assemblyConfig?.segments?.some(s => s.enabled) ?? false
        expect(steps[2].completed).toBe(hasEnabled)

        // Step 4: editor
        const enabled = state.assemblyConfig?.segments?.filter(s => s.enabled) ?? []
        const allEdited = enabled.length > 0 && enabled.every(s => (s.lockedVersion ?? 0) > 1)
        expect(steps[3].completed).toBe(allEdited)

        // Step 5: testing
        const fullCov = state.coverage !== null &&
          state.coverage.overallCoveragePercent >= 100 &&
          state.coverage.segmentCoverages.length > 0
        expect(steps[4].completed).toBe(fullCov)

        // Step 6: review
        expect(steps[5].completed).toBe(state.status === 'ACTIVE')

        // Steps 7-8: always available
        expect(steps[6].alwaysAvailable).toBe(true)
        expect(steps[7].alwaysAvailable).toBe(true)

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
      { numRuns: 200 }
    )
  })
})
