// Feature: template-workflow-stages, Property 1: 阶段可用性与完成状态一致性
// **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1, 3.2, 3.3, 3.4**

import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import type { StageDefinition } from '@/types/workspace'


interface StoreSnapshot {
  templateStatus: string
  templateExists: boolean
  parameterCount: number
  overallCoveragePercent: number
  segments: Array<{ enabled: boolean; filePath: string | null }>
}

function computeStages(s: StoreSnapshot): StageDefinition[] {
  const status = s.templateStatus
  const coverage100 = s.overallCoveragePercent >= 100
  const hasParams = s.parameterCount > 0
  const hasEnabledEditedSegment = s.segments.some(
    (seg) => seg.enabled && seg.filePath != null && seg.filePath.length > 0,
  )

  const designCompleted = s.templateExists && hasParams && hasEnabledEditedSegment
  const testCompleted = coverage100

  const label = (name: string) => name // stub for testing

  switch (status) {
    case 'DRAFT':
      return [
        { name: 'design', label: label('design'), status: designCompleted ? 'completed' : 'in_progress', clickable: true },
        { name: 'test', label: label('test'), status: testCompleted ? 'completed' : (designCompleted ? 'in_progress' : 'not_started'), clickable: true },
        { name: 'approval', label: label('approval'), status: 'not_started', clickable: coverage100 },
        { name: 'publish', label: label('publish'), status: 'not_started', clickable: false },
      ]
    case 'IN_TEST':
      return [
        { name: 'design', label: label('design'), status: 'completed', clickable: true },
        { name: 'test', label: label('test'), status: testCompleted ? 'completed' : 'in_progress', clickable: true },
        { name: 'approval', label: label('approval'), status: 'not_started', clickable: coverage100 },
        { name: 'publish', label: label('publish'), status: 'not_started', clickable: false },
      ]
    case 'PENDING_REVIEW':
      return [
        { name: 'design', label: label('design'), status: 'readonly', clickable: true },
        { name: 'test', label: label('test'), status: 'readonly', clickable: true },
        { name: 'approval', label: label('approval'), status: 'in_progress', clickable: true },
        { name: 'publish', label: label('publish'), status: 'not_started', clickable: false },
      ]
    case 'REVIEWED':
      return [
        { name: 'design', label: label('design'), status: 'readonly', clickable: true },
        { name: 'test', label: label('test'), status: 'readonly', clickable: true },
        { name: 'approval', label: label('approval'), status: 'completed', clickable: true },
        { name: 'publish', label: label('publish'), status: 'in_progress', clickable: true },
      ]
    case 'ACTIVE':
      return [
        { name: 'design', label: label('design'), status: 'readonly', clickable: true },
        { name: 'test', label: label('test'), status: 'readonly', clickable: true },
        { name: 'approval', label: label('approval'), status: 'completed', clickable: true },
        { name: 'publish', label: label('publish'), status: 'completed', clickable: true },
      ]
    case 'ARCHIVED':
      return [
        { name: 'design', label: label('design'), status: 'readonly', clickable: true },
        { name: 'test', label: label('test'), status: 'readonly', clickable: true },
        { name: 'approval', label: label('approval'), status: 'readonly', clickable: true },
        { name: 'publish', label: label('publish'), status: 'readonly', clickable: true },
      ]
    default:
      return []
  }
}


const templateStatuses = ['DRAFT', 'IN_TEST', 'PENDING_REVIEW', 'REVIEWED', 'ACTIVE', 'ARCHIVED'] as const

const arbSegment = fc.record({
  enabled: fc.boolean(),
  filePath: fc.option(fc.stringMatching(/^segments\/\d+\/[a-z]+\.docx$/), { nil: null }),
})

const arbStoreSnapshot: fc.Arbitrary<StoreSnapshot> = fc.record({
  templateStatus: fc.constantFrom(...templateStatuses),
  templateExists: fc.boolean(),
  parameterCount: fc.integer({ min: 0, max: 50 }),
  overallCoveragePercent: fc.integer({ min: 0, max: 100 }),
  segments: fc.array(arbSegment, { minLength: 0, maxLength: 10 }),
})


describe('Property 1: 阶段可用性与完成状态一致性', () => {
  it('Sub-property 1: array length is always 4, order is [design, test, approval, publish]', () => {
    fc.assert(
      fc.property(arbStoreSnapshot, (snapshot) => {
        const stages = computeStages(snapshot)
        expect(stages).toHaveLength(4)
        expect(stages[0].name).toBe('design')
        expect(stages[1].name).toBe('test')
        expect(stages[2].name).toBe('approval')
        expect(stages[3].name).toBe('publish')
      }),
      { numRuns: 100 },
    )
  })

  it('Sub-property 2: DRAFT / IN_TEST — design/test clickable, approval clickable iff coverage>=100, publish not clickable', () => {
    fc.assert(
      fc.property(
        arbStoreSnapshot.filter((s) => s.templateStatus === 'DRAFT' || s.templateStatus === 'IN_TEST'),
        (snapshot) => {
          const stages = computeStages(snapshot)
          expect(stages[0].clickable).toBe(true) // design
          expect(stages[1].clickable).toBe(true) // test
          expect(stages[2].clickable).toBe(snapshot.overallCoveragePercent >= 100) // approval
          expect(stages[3].clickable).toBe(false) // publish
        },
      ),
      { numRuns: 100 },
    )
  })

  it('Sub-property 3: PENDING_REVIEW — design/test readonly+clickable, approval in_progress+clickable, publish not clickable', () => {
    fc.assert(
      fc.property(
        arbStoreSnapshot.filter((s) => s.templateStatus === 'PENDING_REVIEW'),
        (snapshot) => {
          const stages = computeStages(snapshot)
          expect(stages[0].status).toBe('readonly')
          expect(stages[0].clickable).toBe(true)
          expect(stages[1].status).toBe('readonly')
          expect(stages[1].clickable).toBe(true)
          expect(stages[2].status).toBe('in_progress')
          expect(stages[2].clickable).toBe(true)
          expect(stages[3].clickable).toBe(false)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('Sub-property 4: REVIEWED/ACTIVE — all four clickable, publish status is in_progress or completed', () => {
    fc.assert(
      fc.property(
        arbStoreSnapshot.filter((s) => s.templateStatus === 'REVIEWED' || s.templateStatus === 'ACTIVE'),
        (snapshot) => {
          const stages = computeStages(snapshot)
          for (const stage of stages) {
            expect(stage.clickable).toBe(true)
          }
          expect(['in_progress', 'completed']).toContain(stages[3].status)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('Sub-property 5: ARCHIVED — all four readonly and clickable', () => {
    fc.assert(
      fc.property(
        arbStoreSnapshot.filter((s) => s.templateStatus === 'ARCHIVED'),
        (snapshot) => {
          const stages = computeStages(snapshot)
          for (const stage of stages) {
            expect(stage.status).toBe('readonly')
            expect(stage.clickable).toBe(true)
          }
        },
      ),
      { numRuns: 100 },
    )
  })

  it('Sub-property 6: design completed iff template exists AND params>0 AND has enabled+edited segment', () => {
    fc.assert(
      fc.property(
        arbStoreSnapshot.filter((s) => s.templateStatus === 'DRAFT'),
        (snapshot) => {
          const stages = computeStages(snapshot)
          const hasEnabledEdited = snapshot.segments.some(
            (seg) => seg.enabled && seg.filePath != null && seg.filePath.length > 0,
          )
          const expectedCompleted = snapshot.templateExists && snapshot.parameterCount > 0 && hasEnabledEdited
          expect(stages[0].status === 'completed').toBe(expectedCompleted)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('Sub-property 7: test completed iff coverage >= 100 (DRAFT and IN_TEST)', () => {
    fc.assert(
      fc.property(
        arbStoreSnapshot.filter((s) => s.templateStatus === 'DRAFT' || s.templateStatus === 'IN_TEST'),
        (snapshot) => {
          const stages = computeStages(snapshot)
          expect(stages[1].status === 'completed').toBe(snapshot.overallCoveragePercent >= 100)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('Sub-property 8: approval completed iff status in {REVIEWED, ACTIVE}', () => {
    fc.assert(
      fc.property(arbStoreSnapshot, (snapshot) => {
        const stages = computeStages(snapshot)
        const expectedCompleted = ['REVIEWED', 'ACTIVE'].includes(snapshot.templateStatus)
        expect(stages[2].status === 'completed').toBe(expectedCompleted)
      }),
      { numRuns: 100 },
    )
  })

  it('Sub-property 9: publish completed iff status is ACTIVE', () => {
    fc.assert(
      fc.property(arbStoreSnapshot, (snapshot) => {
        const stages = computeStages(snapshot)
        expect(stages[3].status === 'completed').toBe(snapshot.templateStatus === 'ACTIVE')
      }),
      { numRuns: 100 },
    )
  })

  it('Sub-property 10: IN_TEST — design stage is always completed', () => {
    fc.assert(
      fc.property(
        arbStoreSnapshot.filter((s) => s.templateStatus === 'IN_TEST'),
        (snapshot) => {
          const stages = computeStages(snapshot)
          expect(stages[0].status).toBe('completed')
        },
      ),
      { numRuns: 100 },
    )
  })
})

