import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const mockGetUsers = vi.fn()

vi.mock('@/api/admin', () => ({
  getUsers: (...args: any[]) => mockGetUsers(...args),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
}))

// Mock ElMessage
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<any>()
  return {
    ...actual,
    ElMessage: { ...actual.ElMessage, warning: vi.fn(), error: vi.fn(), success: vi.fn() },
  }
})

import SubmitReviewDialog from '@/views/template-workspace/components/SubmitReviewDialog.vue'
import { ElMessage } from 'element-plus'

const sampleUsers = {
  content: [
    { id: 1, username: 'alice', email: 'alice@test.com' },
    { id: 2, username: 'bob', email: 'bob@test.com' },
  ],
  totalElements: 2,
}

function mountDialog(visible = true) {
  return mount(SubmitReviewDialog, {
    props: { visible },
  })
}

describe('SubmitReviewDialog', () => {
  beforeEach(() => {
    mockGetUsers.mockReset()
    mockGetUsers.mockResolvedValue(sampleUsers)
    vi.mocked(ElMessage.warning).mockReset()
  })

  // Requirement 4.3: Opens and loads user list
  it('loads user list when dialog becomes visible', async () => {
    const wrapper = mountDialog(false)
    await flushPromises()
    // Trigger the watch by setting visible to true
    await wrapper.setProps({ visible: true })
    await flushPromises()
    expect(mockGetUsers).toHaveBeenCalledWith({ page: 0, size: 100 })
  })

  // Requirement 4.3: Review level select defaults to 1
  it('defaults review level to 1 (Initial Review)', async () => {
    const wrapper = mountDialog(true)
    await flushPromises()
    const vm = wrapper.vm as any
    expect(vm.form.reviewLevel).toBe(1)
  })

  // Requirement 4.3: No reviewers selected → warning
  it('shows warning when submitting without selecting reviewers', async () => {
    const wrapper = mountDialog(true)
    await flushPromises()
    const vm = wrapper.vm as any
    vm.handleSubmit()
    expect(ElMessage.warning).toHaveBeenCalled()
    expect(wrapper.emitted('submit')).toBeFalsy()
  })

  // Requirement 4.3: Submit emits event with reviewerIds and reviewLevel
  it('emits submit event with reviewerIds and reviewLevel', async () => {
    const wrapper = mountDialog(true)
    await flushPromises()
    const vm = wrapper.vm as any
    vm.form.reviewerIds = [1, 2]
    vm.form.reviewLevel = 2
    vm.handleSubmit()
    expect(wrapper.emitted('submit')).toBeTruthy()
    const emitted = wrapper.emitted('submit')![0]
    expect(emitted).toEqual([[1, 2], 2])
  })

  // Resets form when reopened
  it('resets form when dialog is reopened', async () => {
    const wrapper = mountDialog(true)
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
})
