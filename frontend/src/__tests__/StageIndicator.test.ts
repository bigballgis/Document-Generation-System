import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StageIndicator from '@/views/template-workspace/components/StageIndicator.vue'
import type { StageDefinition } from '@/types/workspace'

function makeStages(overrides?: Partial<Record<string, Partial<StageDefinition>>>): StageDefinition[] {
  const defaults: StageDefinition[] = [
    { name: 'design', label: 'Design', status: 'in_progress', clickable: true },
    { name: 'test', label: 'Test', status: 'not_started', clickable: true },
    { name: 'approval', label: 'Approval', status: 'not_started', clickable: false },
    { name: 'publish', label: 'Publish', status: 'not_started', clickable: false },
  ]
  if (overrides) {
    return defaults.map((s) => ({ ...s, ...(overrides[s.name] ?? {}) }))
  }
  return defaults
}

describe('StageIndicator', () => {
  it('renders four stage items', () => {
    const wrapper = mount(StageIndicator, {
      props: { stages: makeStages(), currentStage: 'design' },
    })
    const stageItems = wrapper.findAll('.stage-item')
    expect(stageItems).toHaveLength(4)
  })

  it('applies circle-in-progress class for in_progress status', () => {
    const wrapper = mount(StageIndicator, {
      props: { stages: makeStages(), currentStage: 'design' },
    })
    const designCircle = wrapper.find('[data-testid="stage-design"] .stage-circle')
    expect(designCircle.classes()).toContain('circle-in-progress')
  })

  it('applies circle-completed class for completed status', () => {
    const stages = makeStages({ design: { status: 'completed' } })
    const wrapper = mount(StageIndicator, {
      props: { stages, currentStage: 'design' },
    })
    const designCircle = wrapper.find('[data-testid="stage-design"] .stage-circle')
    expect(designCircle.classes()).toContain('circle-completed')
  })

  it('applies circle-completed class for readonly status', () => {
    const stages = makeStages({ design: { status: 'readonly' } })
    const wrapper = mount(StageIndicator, {
      props: { stages, currentStage: 'design' },
    })
    const designCircle = wrapper.find('[data-testid="stage-design"] .stage-circle')
    expect(designCircle.classes()).toContain('circle-completed')
  })

  it('applies circle-not-started class for not_started status', () => {
    const wrapper = mount(StageIndicator, {
      props: { stages: makeStages(), currentStage: 'design' },
    })
    const testCircle = wrapper.find('[data-testid="stage-test"] .stage-circle')
    expect(testCircle.classes()).toContain('circle-not-started')
  })

  it('emits stage-click when clicking a clickable stage', async () => {
    const wrapper = mount(StageIndicator, {
      props: { stages: makeStages(), currentStage: 'design' },
    })
    await wrapper.find('[data-testid="stage-test"]').trigger('click')
    expect(wrapper.emitted('stage-click')).toBeTruthy()
    expect(wrapper.emitted('stage-click')![0]).toEqual(['test'])
  })

  it('does NOT emit stage-click when clicking a non-clickable stage', async () => {
    const wrapper = mount(StageIndicator, {
      props: { stages: makeStages(), currentStage: 'design' },
    })
    // approval has clickable=false
    await wrapper.find('[data-testid="stage-approval"]').trigger('click')
    expect(wrapper.emitted('stage-click')).toBeFalsy()
  })

  it('renders three connecting lines between four stages', () => {
    const wrapper = mount(StageIndicator, {
      props: { stages: makeStages(), currentStage: 'design' },
    })
    const lines = wrapper.findAll('[data-testid="stage-line"]')
    expect(lines).toHaveLength(3)
  })

  it('applies line-completed class when both adjacent stages are completed', () => {
    const stages = makeStages({
      design: { status: 'completed' },
      test: { status: 'completed' },
    })
    const wrapper = mount(StageIndicator, {
      props: { stages, currentStage: 'design' },
    })
    const lines = wrapper.findAll('[data-testid="stage-line"]')
    // First line: between design(completed) and test(completed) → line-completed
    expect(lines[0].classes()).toContain('line-completed')
    // Second line: between test(completed) and approval(not_started) → line-pending
    expect(lines[1].classes()).toContain('line-pending')
  })

  it('applies line-pending class when adjacent stages are not both completed', () => {
    const wrapper = mount(StageIndicator, {
      props: { stages: makeStages(), currentStage: 'design' },
    })
    const lines = wrapper.findAll('[data-testid="stage-line"]')
    // All lines should be pending since only design is in_progress
    for (const line of lines) {
      expect(line.classes()).toContain('line-pending')
    }
  })
})
