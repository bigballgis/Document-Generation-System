import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import WorkflowStepIndicator from '@/views/template-workspace/components/WorkflowStepIndicator.vue'
import type { WorkflowStep } from '@/types/workspace'

const i18n = createI18n({ legacy: false, locale: 'en-US', messages: { 'en-US': {} } })

function makeSteps(overrides: Partial<WorkflowStep>[] = []): WorkflowStep[] {
  const defaults: WorkflowStep[] = Array.from({ length: 8 }, (_, i) => ({
    key: `step${i}`, label: `Step ${i + 1}`, completed: false, active: false, alwaysAvailable: i >= 6,
  }))
  overrides.forEach((o, i) => { if (i < defaults.length) Object.assign(defaults[i], o) })
  return defaults
}

describe('WorkflowStepIndicator', () => {
  it('renders 8 steps', () => {
    const wrapper = mount(WorkflowStepIndicator, {
      props: { steps: makeSteps(), currentTab: 'dataStructure' },
      global: { plugins: [i18n] },
    })
    expect(wrapper.findAll('.el-step')).toHaveLength(8)
  })

  it('emits step-click on click', async () => {
    const wrapper = mount(WorkflowStepIndicator, {
      props: { steps: makeSteps(), currentTab: 'dataStructure' },
      global: { plugins: [i18n] },
    })
    await wrapper.findAll('.el-step')[1].trigger('click')
    expect(wrapper.emitted('step-click')).toBeTruthy()
  })

  it('applies pulse class to active step', () => {
    const steps = makeSteps()
    steps[1].active = true
    const wrapper = mount(WorkflowStepIndicator, {
      props: { steps, currentTab: 'dataStructure' },
      global: { plugins: [i18n] },
    })
    expect(wrapper.findAll('.el-step')[1].classes()).toContain('step-pulse')
  })
})
