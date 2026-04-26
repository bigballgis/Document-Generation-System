import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { TestCaseDTO } from '@/api/market'

const mockCreateTestCase = vi.fn()
const mockUpdateTestCase = vi.fn()

vi.mock('@/api/market', () => ({
  createTestCase: (...args: unknown[]) => mockCreateTestCase(...args),
  updateTestCase: (...args: unknown[]) => mockUpdateTestCase(...args),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
}))

import TestCaseFormDialog from '@/views/template-workspace/components/TestCaseFormDialog.vue'

const sampleTestCase: TestCaseDTO = {
  id: 10,
  templateId: 1,
  name: 'Test Case 1',
  testDataJson: '{"key":"value"}',
  expectedResultJson: '{"result":"ok"}',
  comparisonType: 'TEXT_CONTENT',
  createdAt: '2024-01-01',
  updatedAt: '2024-06-01',
}

function mountDialog(props: { visible?: boolean; testCase?: TestCaseDTO | null } = {}) {
  return mount(TestCaseFormDialog, {
    props: {
      visible: true,
      templateId: 1,
      testCase: null,
      ...props,
    },
  })
}

describe('TestCaseFormDialog', () => {
  beforeEach(() => {
    mockCreateTestCase.mockReset()
    mockUpdateTestCase.mockReset()
    mockCreateTestCase.mockResolvedValue({ ...sampleTestCase, id: 11 })
    mockUpdateTestCase.mockResolvedValue(sampleTestCase)
  })

  it('shows empty form in create mode (testCase=null)', async () => {
    const wrapper = mountDialog({ testCase: null })
    await flushPromises()
    const vm = wrapper.vm as any
    expect(vm.form.name).toBe('')
    expect(vm.form.testDataJson).toBe('{}')
    expect(vm.form.expectedResultJson).toBe('')
    expect(vm.form.comparisonType).toBe('VARIABLE_VALUE')
  })

  it('pre-populates form in edit mode', async () => {
    const wrapper = mountDialog({ visible: false, testCase: sampleTestCase })
    await flushPromises()
    await wrapper.setProps({ visible: true })
    await flushPromises()
    const vm = wrapper.vm as any
    expect(vm.form.name).toBe('Test Case 1')
    expect(vm.form.testDataJson).toBe('{"key":"value"}')
    expect(vm.form.expectedResultJson).toBe('{"result":"ok"}')
    expect(vm.form.comparisonType).toBe('TEXT_CONTENT')
  })

  it('calls createTestCase on submit in create mode', async () => {
    const wrapper = mountDialog({ testCase: null })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.form.name = 'New Test'
    vm.form.testDataJson = '{"a":1}'
    vm.formRef = { validate: vi.fn().mockResolvedValue(true), resetFields: vi.fn() }
    await vm.handleSubmit()
    await flushPromises()
    expect(mockCreateTestCase).toHaveBeenCalledWith(1, expect.objectContaining({ name: 'New Test' }))
    expect(wrapper.emitted('saved')).toBeTruthy()
  })

  it('calls updateTestCase on submit in edit mode', async () => {
    const wrapper = mountDialog({ testCase: sampleTestCase })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.form.name = 'Updated Test'
    vm.formRef = { validate: vi.fn().mockResolvedValue(true), resetFields: vi.fn() }
    await vm.handleSubmit()
    await flushPromises()
    expect(mockUpdateTestCase).toHaveBeenCalledWith(10, expect.objectContaining({ name: 'Updated Test' }))
    expect(wrapper.emitted('saved')).toBeTruthy()
  })

  it('shows error message when submit fails', async () => {
    mockCreateTestCase.mockRejectedValue(new Error('Server error'))
    const wrapper = mountDialog({ testCase: null })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.form.name = 'Fail Test'
    vm.form.testDataJson = '{"a":1}'
    vm.formRef = { validate: vi.fn().mockResolvedValue(true), resetFields: vi.fn() }
    await vm.handleSubmit()
    await flushPromises()
    expect(wrapper.emitted('saved')).toBeFalsy()
  })
})
