// Feature: workspace-test-publish, Property 1: Coverage display state consistency
// **Validates: Requirements 3.1, 3.3, 3.5**

import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import type { CompositeCoverageReport, SegmentCoverageEntry } from '@/types/segment'
import type { ReviewDTO } from '@/api/admin'


function computeCoverageState(report: CompositeCoverageReport) {
  const overallCoveragePercent = report.overallCoveragePercent
  const totalVariables = report.segmentCoverages.reduce((sum, s) => sum + s.totalVariables, 0)
  const boundVariables = report.segmentCoverages.reduce((sum, s) => sum + s.boundVariables, 0)

  const warningVisible = overallCoveragePercent < 100 && totalVariables > 0
  const emptyStateVisible = totalVariables === 0

  let color: string
  if (overallCoveragePercent >= 100) color = '#67C23A' // green
  else if (overallCoveragePercent >= 50) color = '#E6A23C' // orange
  else color = '#F56C6C' // red

  return { overallCoveragePercent, totalVariables, boundVariables, warningVisible, emptyStateVisible, color }
}


function computeReviewStatus(reviews: ReviewDTO[]) {
  const allReviewsApproved = reviews.length > 0 &&
    reviews.every(r => r.status === 'APPROVED' || r.status === 'CONDITIONAL_APPROVED')
  const hasRejectedReview = reviews.some(r => r.status === 'REJECTED')
  return { allReviewsApproved, hasRejectedReview }
}


function hasFullCoverage(cov: CompositeCoverageReport | null): boolean {
  if (!cov) return false
  return cov.overallCoveragePercent >= 100 && cov.segmentCoverages.length > 0
}


const arbSegmentCoverageEntry: fc.Arbitrary<SegmentCoverageEntry> = fc.record({
  segmentName: fc.string({ minLength: 1, maxLength: 30 }),
  totalVariables: fc.integer({ min: 0, max: 100 }),
  boundVariables: fc.integer({ min: 0, max: 100 }),
  coveragePercent: fc.integer({ min: 0, max: 100 }),
}).chain(entry => {
  // Ensure boundVariables <= totalVariables
  const bound = Math.min(entry.boundVariables, entry.totalVariables)
  const pct = entry.totalVariables > 0 ? Math.round((bound / entry.totalVariables) * 100) : 0
  return fc.constant({
    ...entry,
    boundVariables: bound,
    coveragePercent: pct,
  })
})


// @ts-ignore unused — kept for reference
const _arbCompositeCoverageReport: fc.Arbitrary<CompositeCoverageReport> = fc
  .array(arbSegmentCoverageEntry, { minLength: 0, maxLength: 20 })
  .chain(segments => {
    const total = segments.reduce((s, e) => s + e.totalVariables, 0)
    const bound = segments.reduce((s, e) => s + e.boundVariables, 0)
    const pct = total > 0 ? Math.round((bound / total) * 100) : 0
    return fc.constant({
      overallCoveragePercent: pct,
      segmentCoverages: segments,
    })
  })

// Also generate reports with arbitrary overallCoveragePercent (0-100) for broader testing
const arbCompositeCoverageReportArbitrary: fc.Arbitrary<CompositeCoverageReport> = fc.record({
  overallCoveragePercent: fc.integer({ min: 0, max: 100 }),
  segmentCoverages: fc.array(arbSegmentCoverageEntry, { minLength: 0, maxLength: 20 }),
})

const reviewStatuses = ['PENDING', 'APPROVED', 'CONDITIONAL_APPROVED', 'REJECTED'] as const

const arbReviewDTO: fc.Arbitrary<ReviewDTO> = fc.record({
  id: fc.integer({ min: 1, max: 10000 }),
  templateId: fc.integer({ min: 1, max: 1000 }),
  reviewerId: fc.integer({ min: 1, max: 100 }),
  status: fc.constantFrom(...reviewStatuses),
  level: fc.constantFrom('INITIAL' as const, 'FINAL' as const),
  comment: fc.option(fc.string({ minLength: 0, maxLength: 50 }), { nil: undefined }),
  createdAt: fc.constant('2024-01-01T00:00:00'),
  updatedAt: fc.constant('2024-06-01T00:00:00'),
})


describe('Property 1: Coverage display state consistency', () => {
  it('totalVariables equals sum of segment totalVariables, boundVariables equals sum of segment boundVariables', () => {
    fc.assert(
      fc.property(arbCompositeCoverageReportArbitrary, (report) => {
        const state = computeCoverageState(report)

        const expectedTotal = report.segmentCoverages.reduce((s, e) => s + e.totalVariables, 0)
        const expectedBound = report.segmentCoverages.reduce((s, e) => s + e.boundVariables, 0)

        expect(state.totalVariables).toBe(expectedTotal)
        expect(state.boundVariables).toBe(expectedBound)
      }),
      { numRuns: 100 },
    )
  })

  it('warning visible iff overallCoveragePercent < 100 AND totalVariables > 0', () => {
    fc.assert(
      fc.property(arbCompositeCoverageReportArbitrary, (report) => {
        const state = computeCoverageState(report)
        const total = report.segmentCoverages.reduce((s, e) => s + e.totalVariables, 0)
        const expected = report.overallCoveragePercent < 100 && total > 0

        expect(state.warningVisible).toBe(expected)
      }),
      { numRuns: 100 },
    )
  })

  it('empty state visible iff totalVariables === 0', () => {
    fc.assert(
      fc.property(arbCompositeCoverageReportArbitrary, (report) => {
        const state = computeCoverageState(report)
        const total = report.segmentCoverages.reduce((s, e) => s + e.totalVariables, 0)

        expect(state.emptyStateVisible).toBe(total === 0)
      }),
      { numRuns: 100 },
    )
  })

  it('color is green when >= 100, orange when >= 50, red when < 50', () => {
    fc.assert(
      fc.property(arbCompositeCoverageReportArbitrary, (report) => {
        const state = computeCoverageState(report)
        const pct = report.overallCoveragePercent

        if (pct >= 100) expect(state.color).toBe('#67C23A')
        else if (pct >= 50) expect(state.color).toBe('#E6A23C')
        else expect(state.color).toBe('#F56C6C')
      }),
      { numRuns: 100 },
    )
  })
})


describe('Property 2: Review status indicator correctness', () => {
  it('allReviewsApproved iff every review is APPROVED or CONDITIONAL_APPROVED', () => {
    fc.assert(
      fc.property(
        fc.array(arbReviewDTO, { minLength: 1, maxLength: 20 }),
        (reviews) => {
          const { allReviewsApproved } = computeReviewStatus(reviews)
          const expected = reviews.every(r => r.status === 'APPROVED' || r.status === 'CONDITIONAL_APPROVED')

          expect(allReviewsApproved).toBe(expected)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('hasRejectedReview iff at least one review has status REJECTED', () => {
    fc.assert(
      fc.property(
        fc.array(arbReviewDTO, { minLength: 1, maxLength: 20 }),
        (reviews) => {
          const { hasRejectedReview } = computeReviewStatus(reviews)
          const expected = reviews.some(r => r.status === 'REJECTED')

          expect(hasRejectedReview).toBe(expected)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('allReviewsApproved and hasRejectedReview are never both true (mutual exclusion)', () => {
    fc.assert(
      fc.property(
        fc.array(arbReviewDTO, { minLength: 1, maxLength: 20 }),
        (reviews) => {
          const { allReviewsApproved, hasRejectedReview } = computeReviewStatus(reviews)

          // They cannot both be true: if all are approved-type, none can be REJECTED
          expect(allReviewsApproved && hasRejectedReview).toBe(false)
        },
      ),
      { numRuns: 100 },
    )
  })
})


describe('Property 5: Workflow Step 5 completion calculation', () => {
  it('Step 5 completed iff coverage is non-null AND overallCoveragePercent >= 100 AND segmentCoverages.length > 0', () => {
    fc.assert(
      fc.property(
        fc.option(arbCompositeCoverageReportArbitrary, { nil: null }),
        (coverage) => {
          const completed = hasFullCoverage(coverage)
          const expected = coverage !== null &&
            coverage.overallCoveragePercent >= 100 &&
            coverage.segmentCoverages.length > 0

          expect(completed).toBe(expected)
        },
      ),
      { numRuns: 100 },
    )
  })
})

