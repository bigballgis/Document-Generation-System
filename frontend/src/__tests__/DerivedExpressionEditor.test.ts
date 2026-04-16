import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import DerivedExpressionEditor from '@/views/template-workspace/components/DerivedExpressionEditor.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
}))

describe('DerivedExpressionEditor', () => {
  it('renders two mode tabs (visual and advanced)', () => {
    const wrapper = mount(DerivedExpressionEditor, {
      props: {
        expressionText: '',
        expressionType: 'JAVASCRIPT',
        availableParameters: [],
      },
    })
    const tabs = wrapper.findAll('.el-tabs__item')
    expect(tabs.length).toBe(2)
    expect(tabs[0].text()).toContain('Visual Mode')
    expect(tabs[1].text()).toContain('Advanced Mode')
  })

  it('renders expression type radio buttons', () => {
    const wrapper = mount(DerivedExpressionEditor, {
      props: {
        expressionText: 'price * qty',
        expressionType: 'JAVASCRIPT',
        availableParameters: [],
      },
    })
    expect(wrapper.text()).toContain('JavaScript')
    expect(wrapper.text()).toContain('Excel')
  })

  it('renders test button', () => {
    const wrapper = mount(DerivedExpressionEditor, {
      props: {
        expressionText: '',
        expressionType: 'JAVASCRIPT',
        availableParameters: [],
      },
    })
    expect(wrapper.text()).toContain('Test')
  })
})
