import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { ReviewDTO } from '@/api/admin'

// ── Mock API modules ──
const mockSubmitForReview = vi.fn()
const mockGetApiKeys = vi.fn()
const mockCreateApiKey = vi.fn()
const mockActivateTemplate = vi.fn()
const mockSubmitReview = vi.fn()

vi.mock('@/api/admin', () => ({
  submitForReview: (...args: any[]) => mockSubmitForReview(...args),
  getApiKeys: (...args: any[]) => mockGetApiKeys(...args),
  createApiKey: (...args: any[]) => mockCreateApiKey(...args),
  getUsers: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
  getTemplateReviews: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
}))

vi.mock('@/api/templates', () => ({
  getTemplate: vi.fn().mockResolvedValue({ id: 1, name: 'T', status: 'DRAFT', templateType: 'COMPOSITE', version: 1 }),
  getAvailableTransitions: vi.fn().mockResolvedValue([]),
  activateTemplate: (...args: any[]) => mockActivateTemplate(...args),
  submitReview: (...args: any[]) => mockSubmitReview(...args),
}))

vi.mock('@/api/composite-templates', () => ({
  getAssemblyConfig: vi.fn().mockResolvedValue({ segments: [] }),
  getCompositeCoverage: vi.fn().mockResolvedValue({ overallCoveragePercent: 100, segmentCoverages: [] }),
  getCompositeSegments: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/data-sources', () => ({ getDataSources: vi.fn().mockResolvedValue([]) }))
vi.mock('@/api/expressions', () => ({ getExpressions: vi.fn().mockResolvedValue([]) }))
vi.mock('@/api/market', () => ({ getTestCases: vi.fn().mockResolvedValue([]) }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
  onBeforeRouteLeave: vi.fn(),
}))

// Stub child components
vi.mock('@/views/template-workspace/components/ApiEndpointInfo.vue', () => ({
  default: {
    name: 'ApiEndpointInfo',
    template: '<div class="stub-api-endpoint-info">ApiEndpointInfo</div>',
    props: ['templateId', 'apiKeys', 'curlExample', 'apiKeysLoading', 'creatingApiKey'],
    emits: ['copy', 'create-api-key'],
  },
}))

vi.mock('@/views/template-workspace/components/SubmitReviewDialog.vue', () => ({
  default: {
    name: 'SubmitReviewDialog',
    template: '<div class="stub-submit-review-dialog" />',
    props: ['visible'],
    emits: ['update:visible', 'submit'],
  },
}))


const mockConfirm = vi.fn()
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<any>()
  return {
    ...actual,
    ElMessageBox: { ...actual.ElMessageBox, confirm: (...args: any[]) => mockConfirm(...args) },
  }
})

import ReviewPublishTab from '@/views/template-workspace/components/ReviewPublishTab.vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'

// ── Fixtures ──
const approvedReviews: ReviewDTO[] = [
  { id: 1, templateId: 1, reviewerId: 10, status: 'APPROVED', level: 'INITIAL', createdAt: '2024-01-01', updatedAt: '2024-06-01' },
  { id: 2, templateId: 1, reviewerId: 11, status: 'CONDITIONAL_APPROVED', level: 'FINAL', createdAt: '2024-01-02', updatedAt: '2024-06-02' },
]

const rejectedReviews: ReviewDTO[] = [
  { id: 1, templateId: 1, reviewerId: 10, status: 'APPROVED', level: 'INITIAL', createdAt: '2024-01-01', updatedAt: '2024-06-01' },
  { id: 2, templateId: 1, reviewerId: 11, status: 'REJECTED', level: 'FINAL', comment: 'Needs work', createdAt: '2024-01-02', updatedAt: '2024-06-02' },
]

function populateStore(overrides: Record<string, any> = {}) {
  const store = useTemplateWorkspaceStore()
  store.templateId = 1
  store.template = { id: 1, name: 'Test', status: 'DRAFT', templateType: 'COMPOSITE', version: 1 } as any
  store.reviews = []
  store.coverage = { overallCoveragePercent: 100, segmentCoverages: [{ segmentId: 1, segmentName: 'S1', totalVariables: 5, boundVariables: 5, coveragePercent: 100 }] }
  Object.assign(store, overrides)
  return store
}

function mountTab() {
  return mount(ReviewPublishTab)
}

describe('ReviewPublishTab', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockSubmitForReview.mockReset()
    mockGetApiKeys.mockReset()
    mockCreateApiKey.mockReset()
    mockActivateTemplate.mockReset()
    mockSubmitReview.mockReset()
    mockConfirm.mockReset()
    mockGetApiKeys.mockResolvedValue({ content: [], totalElements: 0 })
  })

  // ── Requirement 4.1: Status badge rendering ──
  describe('status badge', () => {
    it('renders DRAFT status as info tag', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()
      const tag = wrapper.find('.status-section .el-tag')
      expect(tag.exists()).toBe(true)
      expect(tag.classes()).toContain('el-tag--info')
    })

    it('renders ACTIVE status as success tag', async () => {
      populateStore()
      const store = useTemplateWorkspaceStore()
      store.template = { ...store.template!, status: 'ACTIVE' } as any
      const wrapper = mountTab()
      await flushPromises()
      const tag = wrapper.find('.status-section .el-tag')
      expect(tag.classes()).toContain('el-tag--success')
    })

    it('renders PENDING_REVIEW status as warning tag', async () => {
      populateStore()
      const store = useTemplateWorkspaceStore()
      store.template = { ...store.template!, status: 'PENDING_REVIEW' } as any
      const wrapper = mountTab()
      await flushPromises()
      const tag = wrapper.find('.status-section .el-tag')
      expect(tag.classes()).toContain('el-tag--warning')
    })
  })

  // ── Requirement 4.2: Submit for Review button ──
  describe('submit for review', () => {
    it('shows Submit for Review button when DRAFT', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()
      expect(wrapper.text()).toContain('Submit for Review')
    })

    it('hides Submit for Review button when PENDING_REVIEW', async () => {
      populateStore()
      const store = useTemplateWorkspaceStore()
      store.template = { ...store.template!, status: 'PENDING_REVIEW' } as any
      const wrapper = mountTab()
      await flushPromises()
      const buttons = wrapper.findAll('.el-button').filter(b => b.text().includes('Submit for Review'))
      expect(buttons.length).toBe(0)
    })
  })

  // ── Requirement 4.4: Submit review flow ──
  describe('submit review flow', () => {
    it('calls APIs and refreshes store on successful submit', async () => {
      const store = populateStore()
      const refreshTemplateSpy = vi.spyOn(store, 'refreshTemplate').mockResolvedValue()
      const refreshReviewsSpy = vi.spyOn(store, 'refreshReviews').mockResolvedValue()
      mockSubmitForReview.mockResolvedValue({})
      mockSubmitReview.mockResolvedValue({})
      const wrapper = mountTab()
      await flushPromises()
      const vm = wrapper.vm as any
      await vm.handleSubmitReview([1, 2], 1)
      await flushPromises()
      expect(mockSubmitForReview).toHaveBeenCalledWith(1, { reviewerIds: [1, 2], reviewLevel: 1 })
      expect(mockSubmitReview).toHaveBeenCalledWith(1)
      expect(refreshTemplateSpy).toHaveBeenCalled()
      expect(refreshReviewsSpy).toHaveBeenCalled()
      expect(vm.submitDialogVisible).toBe(false)
    })

    it('keeps dialog open on submit failure', async () => {
      populateStore()
      mockSubmitForReview.mockRejectedValue(new Error('fail'))
      const wrapper = mountTab()
      await flushPromises()
      const vm = wrapper.vm as any
      vm.submitDialogVisible = true
      await vm.handleSubmitReview([1], 1)
      await flushPromises()
      // Dialog should remain open (submitDialogVisible not set to false on error)
      expect(vm.submitDialogVisible).toBe(true)
    })
  })

  // ── Requirement 4.6: Review table ──
  describe('review table', () => {
    it('shows review table when PENDING_REVIEW', async () => {
      populateStore({ reviews: approvedReviews })
      const store = useTemplateWorkspaceStore()
      store.template = { ...store.template!, status: 'PENDING_REVIEW' } as any
      const wrapper = mountTab()
      await flushPromises()
      expect(wrapper.find('.el-table').exists()).toBe(true)
    })
  })

  // ── Requirement 4.8: All reviews approved → Ready to Publish ──
  describe('review indicators', () => {
    it('shows Ready to Publish when all reviews approved and PENDING_REVIEW', async () => {
      const store = populateStore({ reviews: approvedReviews })
      store.template = { ...store.template!, status: 'PENDING_REVIEW' } as any
      // Prevent refreshReviews from clearing our test data
      vi.spyOn(store, 'refreshReviews').mockResolvedValue()
      const wrapper = mountTab()
      await flushPromises()
      // Check for success alert by text content
      expect(wrapper.text()).toContain('All reviews approved')
    })

    it('shows Review Rejected when a review is rejected', async () => {
      const store = populateStore({ reviews: rejectedReviews })
      store.template = { ...store.template!, status: 'PENDING_REVIEW' } as any
      vi.spyOn(store, 'refreshReviews').mockResolvedValue()
      const wrapper = mountTab()
      await flushPromises()
      expect(wrapper.text()).toContain('Review Rejected')
    })
  })

  // ── Requirement 5.5: Auto-activation failed ──
  describe('auto-activation failed', () => {
    it('shows error alert when REVIEWED and all reviews approved', async () => {
      const store = populateStore({ reviews: approvedReviews })
      store.template = { ...store.template!, status: 'REVIEWED' } as any
      vi.spyOn(store, 'refreshReviews').mockResolvedValue()
      const wrapper = mountTab()
      await flushPromises()
      expect(wrapper.text()).toContain('Auto-activation failed')
    })
  })

  // ── Requirement 5.6 / 6.1: Manual activate ──
  describe('manual activate', () => {
    it('shows Activate button when REVIEWED', async () => {
      populateStore()
      const store = useTemplateWorkspaceStore()
      store.template = { ...store.template!, status: 'REVIEWED' } as any
      const wrapper = mountTab()
      await flushPromises()
      expect(wrapper.text()).toContain('Activate')
    })

    it('activates directly when coverage is 100%', async () => {
      const store = populateStore()
      store.template = { ...store.template!, status: 'REVIEWED' } as any
      const refreshTemplateSpy = vi.spyOn(store, 'refreshTemplate').mockResolvedValue()
      const refreshTransitionsSpy = vi.spyOn(store, 'refreshTransitions').mockResolvedValue()
      mockActivateTemplate.mockResolvedValue({})
      const wrapper = mountTab()
      await flushPromises()
      const vm = wrapper.vm as any
      await vm.handleActivate()
      await flushPromises()
      expect(mockActivateTemplate).toHaveBeenCalledWith(1)
      expect(refreshTemplateSpy).toHaveBeenCalled()
      expect(refreshTransitionsSpy).toHaveBeenCalled()
    })

    it('shows warning dialog when coverage < 100%', async () => {
      const store = populateStore({ coverage: { overallCoveragePercent: 80, segmentCoverages: [] } })
      store.template = { ...store.template!, status: 'REVIEWED' } as any
      mockConfirm.mockResolvedValue('confirm')
      mockActivateTemplate.mockResolvedValue({})
      vi.spyOn(store, 'refreshTemplate').mockResolvedValue()
      vi.spyOn(store, 'refreshTransitions').mockResolvedValue()
      const wrapper = mountTab()
      await flushPromises()
      const vm = wrapper.vm as any
      await vm.handleActivate()
      await flushPromises()
      expect(mockConfirm).toHaveBeenCalledTimes(1)
      expect(mockActivateTemplate).toHaveBeenCalledWith(1)
    })

    it('does not activate when user cancels low coverage warning', async () => {
      const store = populateStore({ coverage: { overallCoveragePercent: 50, segmentCoverages: [] } })
      store.template = { ...store.template!, status: 'REVIEWED' } as any
      mockConfirm.mockRejectedValue('cancel')
      const wrapper = mountTab()
      await flushPromises()
      const vm = wrapper.vm as any
      await vm.handleActivate()
      await flushPromises()
      expect(mockActivateTemplate).not.toHaveBeenCalled()
    })

    it('shows error on activation failure', async () => {
      const store = populateStore()
      store.template = { ...store.template!, status: 'REVIEWED' } as any
      mockActivateTemplate.mockRejectedValue(new Error('Cannot activate'))
      const wrapper = mountTab()
      await flushPromises()
      const vm = wrapper.vm as any
      await vm.handleActivate()
      await flushPromises()
      expect(vm.activating).toBe(false)
    })
  })

  // ── Requirement 5.1: API Endpoint Info when ACTIVE ──
  describe('API endpoint info', () => {
    it('shows ApiEndpointInfo when ACTIVE', async () => {
      populateStore()
      const store = useTemplateWorkspaceStore()
      store.template = { ...store.template!, status: 'ACTIVE' } as any
      const wrapper = mountTab()
      await flushPromises()
      expect(wrapper.find('.stub-api-endpoint-info').exists()).toBe(true)
    })

    it('does not show ApiEndpointInfo when DRAFT', async () => {
      populateStore()
      const wrapper = mountTab()
      await flushPromises()
      expect(wrapper.find('.stub-api-endpoint-info').exists()).toBe(false)
    })
  })

  // ── Requirement 4.10: Revise & Resubmit ──
  describe('revise and resubmit', () => {
    it('refreshes template and reviews', async () => {
      const store = populateStore({ reviews: rejectedReviews })
      store.template = { ...store.template!, status: 'PENDING_REVIEW' } as any
      const refreshTemplateSpy = vi.spyOn(store, 'refreshTemplate').mockResolvedValue()
      const refreshReviewsSpy = vi.spyOn(store, 'refreshReviews').mockResolvedValue()
      const wrapper = mountTab()
      await flushPromises()
      const vm = wrapper.vm as any
      await vm.handleReviseResubmit()
      await flushPromises()
      expect(refreshTemplateSpy).toHaveBeenCalled()
      expect(refreshReviewsSpy).toHaveBeenCalled()
    })
  })
})
