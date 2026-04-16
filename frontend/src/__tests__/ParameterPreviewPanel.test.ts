import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ParameterPreviewPanel from '@/views/template-workspace/components/ParameterPreviewPanel.vue'
import type { ParameterDTO } from '@/types/parameter'

vi.mock('@/api/parameters', () => ({
  scanPlaceholders: vi.fn().mockResolvedValue({ matched: [], unmatchedPlaceholders: [], unusedParameters: [] }),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
}))

function makeParam(name: string, dataType: string = 'STRING'): ParameterDTO {
  return {
    id: 1,
    templateId: 1,
    parentId: null,
    name,
    parameterType: 'REQUEST',
    dataType: dataType as any,
    required: false,
    defaultValue: null,
    description: null,
    sortOrder: 0,
    expressionText: null,
    expressionType: null,
    validationRules: null,
    version: 0,
    parameterPath: name,
    children: [],
    createdAt: '',
    updatedAt: '',
  }
}

describe('ParameterPreviewPanel', () => {
  it('renders three tabs', () => {
    const wrapper = mount(ParameterPreviewPanel, {
      props: { parameters: [], templateId: 1 },
    })
    const tabs = wrapper.findAll('.el-tabs__item')
    expect(tabs.length).toBe(3)
  })

  it('renders JSON Schema for parameters', () => {
    const params = [makeParam('name')]
    const wrapper = mount(ParameterPreviewPanel, {
      props: { parameters: params, templateId: 1 },
    })
    const code = wrapper.find('.preview-code')
    expect(code.exists()).toBe(true)
    const text = code.text()
    expect(text).toContain('"type": "object"')
    expect(text).toContain('"name"')
  })

  it('renders placeholder match tab with hint when no scan', () => {
    const wrapper = mount(ParameterPreviewPanel, {
      props: { parameters: [], templateId: 1 },
    })
    expect(wrapper.find('.placeholder-stub').exists()).toBe(true)
  })
})
