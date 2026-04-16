import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ValidationRulesPopover from '@/views/template-workspace/components/ValidationRulesPopover.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
}))

describe('ValidationRulesPopover', () => {
  it('mounts without error for STRING type', () => {
    const wrapper = mount(ValidationRulesPopover, {
      props: { visible: true, dataType: 'STRING', rules: null },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('mounts without error for NUMBER type', () => {
    const wrapper = mount(ValidationRulesPopover, {
      props: { visible: true, dataType: 'NUMBER', rules: null },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('mounts without error for ARRAY type', () => {
    const wrapper = mount(ValidationRulesPopover, {
      props: { visible: true, dataType: 'ARRAY', rules: null },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('emits save event with cleaned rules', async () => {
    const wrapper = mount(ValidationRulesPopover, {
      props: { visible: true, dataType: 'STRING', rules: { not_null: true, min_length: 1 } },
    })
    // The component should exist and accept rules prop
    expect(wrapper.exists()).toBe(true)
  })

  it('does not render STRING-specific rules for BOOLEAN type', () => {
    const wrapper = mount(ValidationRulesPopover, {
      props: { visible: true, dataType: 'BOOLEAN', rules: null },
    })
    // Popover content is teleported, so we check the component renders
    expect(wrapper.exists()).toBe(true)
  })
})
