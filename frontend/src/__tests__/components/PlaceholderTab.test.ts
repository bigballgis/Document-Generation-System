import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import PlaceholderTab from '@/views/template-workspace/components/PlaceholderTab.vue'

const messages = {
  'en-US': {
    'workspace.placeholder.dataStructure': 'Data structure management coming in Phase 2',
    'workspace.placeholder.dataStructureDesc': 'Manage data sources and expressions',
  },
}
const i18n = createI18n({ legacy: false, locale: 'en-US', messages })

describe('PlaceholderTab', () => {
  it('renders phase number', () => {
    const wrapper = mount(PlaceholderTab, {
      props: {
        icon: 'DataLine',
        phase: 2,
        messageKey: 'workspace.placeholder.dataStructure',
        descriptionKey: 'workspace.placeholder.dataStructureDesc',
      },
      global: { plugins: [i18n] },
    })
    expect(wrapper.text()).toContain('Phase 2')
  })

  it('renders i18n message and description', () => {
    const wrapper = mount(PlaceholderTab, {
      props: {
        icon: 'DataLine',
        phase: 2,
        messageKey: 'workspace.placeholder.dataStructure',
        descriptionKey: 'workspace.placeholder.dataStructureDesc',
      },
      global: { plugins: [i18n] },
    })
    expect(wrapper.text()).toContain('Data structure management coming in Phase 2')
    expect(wrapper.text()).toContain('Manage data sources and expressions')
  })
})
