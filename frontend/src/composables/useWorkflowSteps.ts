import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import type { WorkflowStep } from '@/types/workspace'
import type { AssemblyConfig, CompositeCoverageReport } from '@/types/segment'

export function useWorkflowSteps(store: ReturnType<typeof useTemplateWorkspaceStore>) {
  const { t } = useI18n()

  function hasEnabledSegment(config: AssemblyConfig | null): boolean {
    return config?.segments?.some(s => s.enabled) ?? false
  }

  function allSegmentsEdited(config: AssemblyConfig | null): boolean {
    const enabled = config?.segments?.filter(s => s.enabled) ?? []
    if (enabled.length === 0) return false
    return enabled.every(s => (s.lockedVersion ?? 0) > 1)
  }

  function hasFullCoverage(cov: CompositeCoverageReport | null): boolean {
    if (!cov) return false
    return cov.overallCoveragePercent >= 100 && cov.segmentCoverages.length > 0
  }

  const steps = computed<WorkflowStep[]>(() => {
    return [
      { key: 'create', label: t('workspace.step1'), completed: !!store.template, active: false, alwaysAvailable: false },
      { key: 'data', label: t('workspace.step2'), completed: store.dataSources.length > 0 || store.expressions.length > 0, active: false, alwaysAvailable: false },
      { key: 'segments', label: t('workspace.step3'), completed: hasEnabledSegment(store.assemblyConfig), active: false, alwaysAvailable: false },
      { key: 'editor', label: t('workspace.step4'), completed: allSegmentsEdited(store.assemblyConfig), active: false, alwaysAvailable: false },
      { key: 'testing', label: t('workspace.step5'), completed: hasFullCoverage(store.coverage), active: false, alwaysAvailable: false },
      { key: 'review', label: t('workspace.step6'), completed: store.templateStatus === 'ACTIVE', active: false, alwaysAvailable: false },
      { key: 'export', label: t('workspace.step7'), completed: false, active: false, alwaysAvailable: true },
      { key: 'settings', label: t('workspace.step8'), completed: false, active: false, alwaysAvailable: true },
    ]
  })

  const stepsWithActive = computed<WorkflowStep[]>(() => {
    const raw = steps.value
    if (store.templateStatus !== 'DRAFT') return raw
    const firstIncomplete = raw.findIndex(s => !s.completed && !s.alwaysAvailable)
    return raw.map((s, i) => ({ ...s, active: i === firstIncomplete }))
  })

  const stepToTab: Record<string, string> = {
    data: 'dataStructure',
    segments: 'segments',
    editor: 'editor',
    testing: 'testing',
    review: 'reviewPublish',
    export: 'exportImport',
    settings: 'settings',
  }

  return { steps: stepsWithActive, stepToTab }
}
