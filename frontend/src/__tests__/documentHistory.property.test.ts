// Feature: document-generation-frontend, Property 2: Document history filter-to-API mapping
// **Validates: Requirements 7.4**

import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as fc from 'fast-check'

// Mock the documents API module
const mockGetDocuments = vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 })

vi.mock('@/api/documents', () => ({
  getDocuments: (...args: any[]) => mockGetDocuments(...args),
  downloadDocument: vi.fn(),
}))

// Mock MergeDialog child component
vi.mock('@/views/documents/components/MergeDialog.vue', () => ({
  default: { template: '<div />', props: ['visible', 'documentIds'], emits: ['update:visible', 'merged'] },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ query: {} }),
}))

import { mount, flushPromises } from '@vue/test-utils'
import DocumentsIndex from '@/views/documents/Index.vue'

describe('Property 2: Document history filter-to-API mapping', () => {
  beforeEach(() => {
    mockGetDocuments.mockClear()
    mockGetDocuments.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 })
  })

  it('should call getDocuments with params matching active filter values', async () => {
    // Mount the component once
    const wrapper = mount(DocumentsIndex, {
      global: {
        stubs: {
          'el-date-picker': true,
          'el-input-number': true,
          'el-select': true,
          'el-option': true,
          MergeDialog: true,
        },
      },
    })
    await flushPromises()

    const vm = wrapper.vm as any

    // Arbitrary for filter combinations
    const arbFilters = fc.record({
      templateId: fc.option(fc.integer({ min: 1, max: 10000 }), { nil: undefined }),
      status: fc.option(fc.constantFrom('GENERATED', 'EXPIRED', 'DELETED'), { nil: undefined }),
      hasDateRange: fc.boolean(),
    })

    await fc.assert(
      fc.asyncProperty(arbFilters, async (filters) => {
        mockGetDocuments.mockClear()
        mockGetDocuments.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 })

        // Set filter values on the component's reactive query
        vm.query.templateId = filters.templateId
        vm.query.status = filters.status
        if (filters.hasDateRange) {
          vm.query.startTime = '2024-01-01T00:00:00'
          vm.query.endTime = '2024-12-31T23:59:59'
        } else {
          vm.query.startTime = undefined
          vm.query.endTime = undefined
        }

        // Trigger search
        vm.handleSearch()
        await flushPromises()

        expect(mockGetDocuments).toHaveBeenCalledTimes(1)
        const calledQuery = mockGetDocuments.mock.calls[0][0]

        // Verify filter values match
        expect(calledQuery.templateId).toBe(filters.templateId)
        expect(calledQuery.status).toBe(filters.status)

        if (filters.hasDateRange) {
          expect(calledQuery.startTime).toBe('2024-01-01T00:00:00')
          expect(calledQuery.endTime).toBe('2024-12-31T23:59:59')
        } else {
          expect(calledQuery.startTime).toBeUndefined()
          expect(calledQuery.endTime).toBeUndefined()
        }

        // Page should be reset to 1 on search
        expect(calledQuery.page).toBe(1)
      }),
      { numRuns: 100 },
    )

    wrapper.unmount()
  }, 30000)
})
