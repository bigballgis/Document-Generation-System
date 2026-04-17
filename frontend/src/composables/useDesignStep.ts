import { ref, computed, reactive } from 'vue'
import type { DesignStepName, ParameterBreadcrumbItem } from '@/types/workspace'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'

type StepStatus = 'not_started' | 'in_progress' | 'completed'

const STEP_ORDER: DesignStepName[] = ['parameter-table', 'segment-canvas', 'segment-detail']

export function useDesignStep() {
  const store = useTemplateWorkspaceStore()
  const currentStep = ref<DesignStepName>('parameter-table')

  const stepStates = reactive({
    'parameter-table': { breadcrumbPath: [] as ParameterBreadcrumbItem[] },
    'segment-canvas': {},
    'segment-detail': { selectedSegmentIndex: 0 },
  })

  const stepStatuses = computed<Record<DesignStepName, StepStatus>>(() => {
    const params = store.parameters ?? []
    const segments = store.assemblyConfig?.segments ?? []
    const enabledSegments = segments.filter(s => s.enabled)
    const allEdited = enabledSegments.length > 0 && enabledSegments.every(s => s.filePath)

    return {
      'parameter-table': params.length > 0 ? 'completed' : 'not_started',
      'segment-canvas': segments.length > 0 ? 'completed' : 'not_started',
      'segment-detail': allEdited ? 'completed' : 'not_started',
    }
  })

  function goToStep(step: DesignStepName) {
    currentStep.value = step
  }

  function goNext() {
    const idx = STEP_ORDER.indexOf(currentStep.value)
    if (idx < STEP_ORDER.length - 1) {
      currentStep.value = STEP_ORDER[idx + 1]
    }
  }

  function goPrev() {
    const idx = STEP_ORDER.indexOf(currentStep.value)
    if (idx > 0) {
      currentStep.value = STEP_ORDER[idx - 1]
    }
  }

  return { currentStep, stepStates, stepStatuses, goToStep, goNext, goPrev, STEP_ORDER }
}
