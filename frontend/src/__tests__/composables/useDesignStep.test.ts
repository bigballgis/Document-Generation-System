/**
 * Unit tests for useDesignStep composable.
 * Tests step switching, state persistence (CP-4), and boundary conditions.
 * _Requirements: 1.1, 1.4, 1.5_
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useDesignStep } from '@/composables/useDesignStep'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'

describe('useDesignStep', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  describe('step switching', () => {
    it('starts at parameter-table step', () => {
      const { currentStep } = useDesignStep()
      expect(currentStep.value).toBe('parameter-table')
    })

    it('goToStep switches to any step', () => {
      const { currentStep, goToStep } = useDesignStep()
      goToStep('segment-canvas')
      expect(currentStep.value).toBe('segment-canvas')
      goToStep('segment-detail')
      expect(currentStep.value).toBe('segment-detail')
      goToStep('parameter-table')
      expect(currentStep.value).toBe('parameter-table')
    })

    it('goNext advances through steps in order', () => {
      const { currentStep, goNext } = useDesignStep()
      expect(currentStep.value).toBe('parameter-table')
      goNext()
      expect(currentStep.value).toBe('segment-canvas')
      goNext()
      expect(currentStep.value).toBe('segment-detail')
    })

    it('goNext does nothing at last step', () => {
      const { currentStep, goToStep, goNext } = useDesignStep()
      goToStep('segment-detail')
      goNext()
      expect(currentStep.value).toBe('segment-detail')
    })

    it('goPrev goes back through steps in order', () => {
      const { currentStep, goToStep, goPrev } = useDesignStep()
      goToStep('segment-detail')
      goPrev()
      expect(currentStep.value).toBe('segment-canvas')
      goPrev()
      expect(currentStep.value).toBe('parameter-table')
    })

    it('goPrev does nothing at first step', () => {
      const { currentStep, goPrev } = useDesignStep()
      goPrev()
      expect(currentStep.value).toBe('parameter-table')
    })
  })


  describe('state persistence (CP-4)', () => {
    it('stepStates preserves breadcrumb path across step switches', () => {
      const { stepStates, goToStep } = useDesignStep()

      // Set breadcrumb in parameter-table step
      stepStates['parameter-table'].breadcrumbPath = [
        { id: null, name: '主表', tableType: 'main' as const },
        { id: 1, name: 'items', tableType: 'sub' as const },
      ]

      // Switch away and back
      goToStep('segment-canvas')
      goToStep('parameter-table')

      // Breadcrumb should be preserved
      expect(stepStates['parameter-table'].breadcrumbPath).toHaveLength(2)
      expect(stepStates['parameter-table'].breadcrumbPath[1].name).toBe('items')
    })

    it('stepStates preserves selectedSegmentIndex across step switches', () => {
      const { stepStates, goToStep } = useDesignStep()

      // Set selected segment in detail step
      stepStates['segment-detail'].selectedSegmentIndex = 3

      // Switch away and back
      goToStep('parameter-table')
      goToStep('segment-detail')

      expect(stepStates['segment-detail'].selectedSegmentIndex).toBe(3)
    })
  })

  describe('step statuses', () => {
    it('all steps not_started when store is empty', () => {
      const { stepStatuses } = useDesignStep()
      expect(stepStatuses.value['parameter-table']).toBe('not_started')
      expect(stepStatuses.value['segment-canvas']).toBe('not_started')
      expect(stepStatuses.value['segment-detail']).toBe('not_started')
    })

    it('parameter-table completed when parameters exist', () => {
      const store = useTemplateWorkspaceStore()
      store.parameters = [{ id: 1, name: 'test', dataType: 'STRING' } as any]
      const { stepStatuses } = useDesignStep()
      expect(stepStatuses.value['parameter-table']).toBe('completed')
    })

    it('segment-canvas completed when segments exist', () => {
      const store = useTemplateWorkspaceStore()
      store.assemblyConfig = {
        segments: [{ filePath: 'test.docx', name: 'seg1', position: 0, enabled: true } as any],
      }
      const { stepStatuses } = useDesignStep()
      expect(stepStatuses.value['segment-canvas']).toBe('completed')
    })

    it('segment-detail completed when all enabled segments have filePath', () => {
      const store = useTemplateWorkspaceStore()
      store.assemblyConfig = {
        segments: [
          { filePath: 'a.docx', name: 'a', position: 0, enabled: true } as any,
          { filePath: 'b.docx', name: 'b', position: 1, enabled: true } as any,
        ],
      }
      const { stepStatuses } = useDesignStep()
      expect(stepStatuses.value['segment-detail']).toBe('completed')
    })

    it('segment-detail not_started when some enabled segments lack filePath', () => {
      const store = useTemplateWorkspaceStore()
      store.assemblyConfig = {
        segments: [
          { filePath: 'a.docx', name: 'a', position: 0, enabled: true } as any,
          { filePath: null, name: 'b', position: 1, enabled: true } as any,
        ],
      }
      const { stepStatuses } = useDesignStep()
      expect(stepStatuses.value['segment-detail']).toBe('not_started')
    })

    it('segment-detail not_started when no enabled segments', () => {
      const store = useTemplateWorkspaceStore()
      store.assemblyConfig = {
        segments: [
          { filePath: 'a.docx', name: 'a', position: 0, enabled: false } as any,
        ],
      }
      const { stepStatuses } = useDesignStep()
      expect(stepStatuses.value['segment-detail']).toBe('not_started')
    })
  })

  describe('boundary conditions', () => {
    it('STEP_ORDER has exactly 3 steps', () => {
      const { STEP_ORDER } = useDesignStep()
      expect(STEP_ORDER).toEqual(['parameter-table', 'segment-canvas', 'segment-detail'])
    })

    it('handles null assemblyConfig gracefully', () => {
      const store = useTemplateWorkspaceStore()
      store.assemblyConfig = null
      const { stepStatuses } = useDesignStep()
      expect(stepStatuses.value['segment-canvas']).toBe('not_started')
      expect(stepStatuses.value['segment-detail']).toBe('not_started')
    })
  })
})
