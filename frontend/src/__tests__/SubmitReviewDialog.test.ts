import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const mockGetReviewerCandidates = vi.fn()

vi.mock('@/api/templates', () => ({
  getReviewerCandidates: (...args: any[]) => mockGetReviewerCandidates(...args),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<any>()
  return {
    ...actual,
    ElMessage: { ...actual.ElMessage, warning: vi.fn(), error: vi.fn(), success: vi.fn() },
  }
})

import SubmitReviewDialog from '@/views/template-workspace/components/SubmitReviewDialog.vue'
import { ElMessage } from 'element-plus'

const sampleCandidates = [
  { id: 1, username: 'alice', email: 'alice@test.com', teamId: 10, role: 'USER' },
  { id: 2, username: 'bob', email: 'bob@test.com', teamId: 10, role: 'USER' },
]

function mountDialog(visible = true, templateId = 1) {
  return mount(SubmitReviewDialog, {
    props: { visible, templateId },
  })
}

describe('SubmitReviewDialog', () => {
  beforeEach(() => {
    mockGetReviewerCandidates.mockReset()
    mockGetReviewerCandidates.mockResolvedValue(sampleCandidates)
    vi.mocked(ElMessage.warning).mockReset()
    vi.mocked(ElMessage.error).mockReset()
  })

  it('loads reviewer candidates when dialog becomes visible', async () => {
    const wrapper = mount(SubmitReviewDialog, { props: { visible: false, templateId: 42 } })
    await flushPromises()
    await wrapper.setProps({ visible: true })
    await flushPromises()
    expect(mockGetReviewerCandidates).toHaveBeenCalledWith(42)
  })

  it('does not request candidates when templateId is missing', async () => {
    mount(SubmitReviewDialog, { props: { visible: true, templateId: 0 } })
    await flushPromises()
    expect(mockGetReviewerCandidates).not.toHaveBeenCalled()
  })

  it('defaults review level to 1 (Initial Review)', async () => {
    const wrapper = mountDialog(true, 1)
    await flushPromises()
    const vm = wrapper.vm as any
    expect(vm.form.reviewLevel).toBe(1)
  })

  it('shows warning when submitting without selecting reviewers', async () => {
    const wrapper = mountDialog(true, 1)
    await flushPromises()
    const vm = wrapper.vm as any
    vm.handleSubmit()
    expect(ElMessage.warning).toHaveBeenCalled()
    expect(wrapper.emitted('submit')).toBeFalsy()
  })

  it('emits submit event with reviewerIds and reviewLevel', async () => {
    const wrapper = mountDialog(true, 1)
    await flushPromises()
    const vm = wrapper.vm as any
    vm.form.reviewerIds = [1, 2]
    vm.form.reviewLevel = 2
    vm.handleSubmit()
    expect(wrapper.emitted('submit')).toBeTruthy()
    const emitted = wrapper.emitted('submit')![0]
    expect(emitted).toEqual([[1, 2], 2])
  })

  it('resets form when dialog is reopened', async () => {
    const wrapper = mountDialog(true, 1)
    await flushPromises()
    const vm = wrapper.vm as any
    vm.form.reviewerIds = [1]
    vm.form.reviewLevel = 2

    await wrapper.setProps({ visible: false })
    await wrapper.setProps({ visible: true })
    await flushPromises()

    expect(vm.form.reviewerIds).toEqual([])
    expect(vm.form.reviewLevel).toBe(1)
  })

  it('disables submit while isSubmitting is true', async () => {
    const wrapper = mount(SubmitReviewDialog, {
      props: { visible: true, templateId: 1, isSubmitting: true },
    })
    await flushPromises()
    const footer = wrapper.find('.el-dialog__footer')
    const primaryBtns = footer.findAll('.el-button--primary')
    const submitBtn = primaryBtns[primaryBtns.length - 1]
    expect(submitBtn?.classes().join(' ')).toMatch(/is-disabled|is-loading/)
  })

  it('refetches candidates each time the dialog opens', async () => {
    const wrapper = mountDialog(false, 7)
    await flushPromises()
    await wrapper.setProps({ visible: true })
    await flushPromises()
    await wrapper.setProps({ visible: false })
    await wrapper.setProps({ visible: true })
    await flushPromises()
    expect(mockGetReviewerCandidates).toHaveBeenCalledTimes(2)
    expect(mockGetReviewerCandidates).toHaveBeenLastCalledWith(7)
  })
})
