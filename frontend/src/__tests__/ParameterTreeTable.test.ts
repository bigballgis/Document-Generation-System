import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ParameterTreeTable from '@/views/template-workspace/components/ParameterTreeTable.vue'
import type { ParameterDTO } from '@/types/parameter'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
}))

function makeParam(overrides: Partial<ParameterDTO> = {}): ParameterDTO {
  return {
    id: 1,
    templateId: 1,
    parentId: null,
    name: 'test_param',
    parameterType: 'REQUEST',
    dataType: 'STRING',
    required: false,
    defaultValue: null,
    description: null,
    sortOrder: 0,
    expressionText: null,
    expressionType: null,
    validationRules: null,
    version: 0,
    parameterPath: 'test_param',
    children: [],
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    ...overrides,
  }
}

describe('ParameterTreeTable', () => {
  it('renders empty state when no parameters', () => {
    const wrapper = mount(ParameterTreeTable, {
      props: { parameters: [], selectedIds: new Set<number>() },
    })
    expect(wrapper.find('.el-empty').exists()).toBe(true)
  })

  it('renders table when parameters exist', () => {
    const params = [makeParam()]
    const wrapper = mount(ParameterTreeTable, {
      props: { parameters: params, selectedIds: new Set<number>() },
    })
    expect(wrapper.find('.el-table').exists()).toBe(true)
  })

  it('exposes expandAll and collapseAll methods', () => {
    const params = [makeParam({ dataType: 'OBJECT', children: [makeParam({ id: 2, name: 'child', parentId: 1 })] })]
    const wrapper = mount(ParameterTreeTable, {
      props: { parameters: params, selectedIds: new Set<number>() },
    })
    // Verify exposed methods exist
    expect(typeof wrapper.vm.expandAll).toBe('function')
    expect(typeof wrapper.vm.collapseAll).toBe('function')
  })

  it('renders context menu element', () => {
    const params = [makeParam()]
    const wrapper = mount(ParameterTreeTable, {
      props: { parameters: params, selectedIds: new Set<number>() },
    })
    expect(wrapper.find('.context-menu').exists()).toBe(true)
  })

  it('exposes activateInlineEdit method instead of dialog', () => {
    const params = [makeParam()]
    const wrapper = mount(ParameterTreeTable, {
      props: { parameters: params, selectedIds: new Set<number>() },
    })
    // el-dialog was removed in favor of inline editing
    expect(wrapper.findComponent({ name: 'ElDialog' }).exists()).toBe(false)
    // Verify inline edit method is exposed
    expect(typeof wrapper.vm.activateInlineEdit).toBe('function')
  })
})
