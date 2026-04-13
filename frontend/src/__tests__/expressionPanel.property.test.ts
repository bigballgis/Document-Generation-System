// Feature: document-generation-frontend, Property 3: Expression validation result rendering
// **Validates: Requirements 8.7**

import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as fc from 'fast-check'
import { ElMessage } from 'element-plus'

// Mock the expressions API
const mockGetExpressions = vi.fn()
const mockDeleteExpression = vi.fn()
const mockValidateExpression = vi.fn()

vi.mock('@/api/expressions', () => ({
  getExpressions: (...args: any[]) => mockGetExpressions(...args),
  deleteExpression: (...args: any[]) => mockDeleteExpression(...args),
  validateExpression: (...args: any[]) => mockValidateExpression(...args),
  createExpression: vi.fn(),
  updateExpression: vi.fn(),
}))

// Mock ExpressionFormDialog child component
vi.mock('@/views/templates/components/ExpressionFormDialog.vue', () => ({
  default: { template: '<div />', props: ['visible', 'templateId', 'data'], emits: ['update:visible', 'saved'] },
}))

// Spy on ElMessage
vi.spyOn(ElMessage, 'success')
vi.spyOn(ElMessage, 'error')

import { mount, flushPromises } from '@vue/test-utils'
import ExpressionPanel from '@/views/templates/components/ExpressionPanel.vue'
import type { ExpressionValidationResult } from '@/types/document'

// A sample expression row for the table
const sampleExpression = {
  id: 1,
  templateId: 10,
  name: 'testExpr',
  expressionType: 'JAVASCRIPT',
  expressionText: '1 + 1',
  description: null,
  executionOrder: 0,
  createdAt: '2024-01-01T00:00:00',
}

describe('Property 3: Expression validation result rendering', () => {
  beforeEach(() => {
    mockGetExpressions.mockClear()
    mockDeleteExpression.mockClear()
    mockValidateExpression.mockClear()
    vi.mocked(ElMessage.success).mockClear()
    vi.mocked(ElMessage.error).mockClear()

    mockGetExpressions.mockResolvedValue([sampleExpression])
  })

  it('should render success for valid=true and error message for valid=false', async () => {
    // Mount the component once
    mockGetExpressions.mockResolvedValue([sampleExpression])
    const wrapper = mount(ExpressionPanel, {
      props: { templateId: 10 },
    })
    await flushPromises()

    // Find the Validate button once
    const findValidateBtn = () => wrapper.findAll('button').find((btn) => btn.text().includes('Validate'))

    // Arbitrary for ExpressionValidationResult
    const arbValidResult: fc.Arbitrary<ExpressionValidationResult> = fc.record({
      valid: fc.constant(true as const),
      errorMessage: fc.constant(undefined),
      errorPosition: fc.constant(undefined),
    })

    const arbInvalidResult: fc.Arbitrary<ExpressionValidationResult> = fc.record({
      valid: fc.constant(false as const),
      errorMessage: fc.option(
        fc.string({ minLength: 1, maxLength: 50 }).filter((s) => s.trim().length > 0),
        { nil: undefined },
      ),
      errorPosition: fc.option(fc.integer({ min: 0, max: 200 }), { nil: undefined }),
    })

    const arbResult = fc.oneof(arbValidResult, arbInvalidResult)

    await fc.assert(
      fc.asyncProperty(arbResult, async (result) => {
        mockValidateExpression.mockClear()
        vi.mocked(ElMessage.success).mockClear()
        vi.mocked(ElMessage.error).mockClear()

        mockValidateExpression.mockResolvedValue(result)

        const validateBtn = findValidateBtn()
        expect(validateBtn).toBeTruthy()
        await validateBtn!.trigger('click')
        await flushPromises()

        if (result.valid) {
          // Should show success message
          expect(ElMessage.success).toHaveBeenCalled()
        } else {
          // Should show error message
          expect(ElMessage.error).toHaveBeenCalled()
          const errorCall = vi.mocked(ElMessage.error).mock.calls[0][0] as string
          // Error message should contain the errorMessage if present
          if (result.errorMessage) {
            expect(errorCall).toContain(result.errorMessage)
          }
          // Error message should contain position info if errorPosition is present
          if (result.errorPosition != null) {
            expect(errorCall).toContain(String(result.errorPosition))
          }
        }
      }),
      { numRuns: 100 },
    )

    wrapper.unmount()
  }, 30000)
})
