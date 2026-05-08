import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import TemplateCreationWizard from '@/views/template-workspace/components/TemplateCreationWizard.vue'

vi.mock('@/api/composite-templates', () => ({
  createCompositeTemplate: vi.fn().mockResolvedValue({ id: 1, name: 'Test' }),
}))
vi.mock('@/api/templates', () => ({
  addTagToTemplate: vi.fn().mockResolvedValue({}),
}))

const messages = {
  'en-US': {
    'workspace.wizard.title': 'Create New Template',
    'workspace.wizard.step1Title': 'Basic Info',
    'workspace.wizard.step2Title': 'Output Format',
    'workspace.wizard.name': 'Name',
    'workspace.wizard.description': 'Description',
    'workspace.wizard.category': 'Category',
    'workspace.wizard.tags': 'Tags',
    'workspace.wizard.outputFormat': 'Output Format',
    'workspace.wizard.create': 'Create',
    'workspace.wizard.next': 'Next',
    'workspace.wizard.prev': 'Previous',
    'workspace.wizard.cancel': 'Cancel',
    'workspace.wizard.nameRequired': 'Required',
    'workspace.wizard.nameMax': 'Max 200',
    'workspace.wizard.descMax': 'Max 500',
  },
}
const i18n = createI18n({ legacy: false, locale: 'en-US', messages })

describe('TemplateCreationWizard', () => {
  it('renders basic form fields without a multi-step wizard', () => {
    const wrapper = mount(TemplateCreationWizard, {
      props: { visible: true, tags: [] },
      global: {
        plugins: [i18n],
        stubs: {
          teleport: true,
          'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
          'el-steps': { template: '<div><slot /></div>' },
          'el-step': { template: '<div />' },
          'el-form': { template: '<form><slot /></form>' },
          'el-form-item': { props: ['label'], template: '<div><label>{{ label }}</label><slot /></div>' },
          'el-input': { template: '<input />' },
          'el-tree-select': { template: '<div />' },
          'el-select': { template: '<select><slot /></select>' },
          'el-option': { template: '<option />' },
          'el-radio-group': { template: '<div><slot /></div>' },
          'el-radio': { template: '<div><slot /></div>' },
          'el-alert': { template: '<div />' },
          'el-button': { template: '<button><slot /></button>' },
        },
      },
    })
    expect(wrapper.text()).toContain('Name')
    expect(wrapper.text()).not.toContain('Next')
  })

  it('shows cancel button', () => {
    const wrapper = mount(TemplateCreationWizard, {
      props: { visible: true, tags: [] },
      global: {
        plugins: [i18n],
        stubs: {
          teleport: true,
          'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
          'el-steps': { template: '<div><slot /></div>' },
          'el-step': { template: '<div />' },
          'el-form': { template: '<form><slot /></form>' },
          'el-form-item': { props: ['label'], template: '<div><label>{{ label }}</label><slot /></div>' },
          'el-input': { template: '<input />' },
          'el-tree-select': { template: '<div />' },
          'el-select': { template: '<select><slot /></select>' },
          'el-option': { template: '<option />' },
          'el-radio-group': { template: '<div><slot /></div>' },
          'el-radio': { template: '<div><slot /></div>' },
          'el-alert': { template: '<div />' },
          'el-button': { template: '<button><slot /></button>' },
        },
      },
    })
    expect(wrapper.text()).toContain('Cancel')
  })
})
