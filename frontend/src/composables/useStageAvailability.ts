import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import type { StageName, StageDefinition } from '@/types/workspace'

export function useStageAvailability(store: ReturnType<typeof useTemplateWorkspaceStore>) {
  const { t } = useI18n()

  const stages = computed<StageDefinition[]>(() => {
    const status = store.templateStatus
    const coverage100 = (store.coverage?.overallCoveragePercent ?? 0) >= 100
    const hasParams = store.parameters.length > 0
    const hasEnabledEditedSegment =
      store.assemblyConfig?.segments?.some(
        (s) => s.enabled && s.filePath != null && s.filePath.length > 0,
      ) ?? false

    // Completion conditions (Requirement 3) — used by the switch-case status assignments
    const designCompleted = !!store.template && hasParams && hasEnabledEditedSegment
    const testCompleted = coverage100
    // approvalCompleted and publishCompleted are encoded directly in the switch cases
    // (REVIEWED/ACTIVE → completed, etc.) rather than as separate variables

    const label = (name: StageName) => t(`workspace.stage.${name}`)

    switch (status) {
      case 'DRAFT':
        return [
          { name: 'design' as const, label: label('design'), status: designCompleted ? 'completed' : 'in_progress', clickable: true },
          { name: 'test' as const, label: label('test'), status: testCompleted ? 'completed' : (designCompleted ? 'in_progress' : 'not_started'), clickable: true },
          { name: 'approval' as const, label: label('approval'), status: 'not_started', clickable: coverage100 },
          { name: 'publish' as const, label: label('publish'), status: 'not_started', clickable: false },
        ]
      case 'IN_TEST':
        return [
          { name: 'design' as const, label: label('design'), status: 'completed', clickable: true },
          { name: 'test' as const, label: label('test'), status: testCompleted ? 'completed' : 'in_progress', clickable: true },
          { name: 'approval' as const, label: label('approval'), status: 'not_started', clickable: coverage100 },
          { name: 'publish' as const, label: label('publish'), status: 'not_started', clickable: false },
        ]
      case 'PENDING_REVIEW':
        return [
          { name: 'design' as const, label: label('design'), status: 'readonly', clickable: true },
          { name: 'test' as const, label: label('test'), status: 'readonly', clickable: true },
          { name: 'approval' as const, label: label('approval'), status: 'in_progress', clickable: true },
          { name: 'publish' as const, label: label('publish'), status: 'not_started', clickable: false },
        ]
      case 'REVIEWED':
        return [
          { name: 'design' as const, label: label('design'), status: 'readonly', clickable: true },
          { name: 'test' as const, label: label('test'), status: 'readonly', clickable: true },
          { name: 'approval' as const, label: label('approval'), status: 'completed', clickable: true },
          { name: 'publish' as const, label: label('publish'), status: 'in_progress', clickable: true },
        ]
      case 'ACTIVE':
        return [
          { name: 'design' as const, label: label('design'), status: 'readonly', clickable: true },
          { name: 'test' as const, label: label('test'), status: 'readonly', clickable: true },
          { name: 'approval' as const, label: label('approval'), status: 'completed', clickable: true },
          { name: 'publish' as const, label: label('publish'), status: 'completed', clickable: true },
        ]
      case 'ARCHIVED':
        return [
          { name: 'design' as const, label: label('design'), status: 'readonly', clickable: true },
          { name: 'test' as const, label: label('test'), status: 'readonly', clickable: true },
          { name: 'approval' as const, label: label('approval'), status: 'readonly', clickable: true },
          { name: 'publish' as const, label: label('publish'), status: 'readonly', clickable: true },
        ]
      default:
        return []
    }
  })

  const activeStage = computed<StageName>(() => {
    const s = store.templateStatus
    if (s === 'DRAFT') return 'design'
    if (s === 'IN_TEST') return 'test'
    if (s === 'PENDING_REVIEW') return 'approval'
    if (s === 'REVIEWED' || s === 'ACTIVE') return 'publish'
    return 'design'
  })

  const isReadonly = computed(
    () => store.templateStatus !== 'DRAFT' && store.templateStatus !== 'IN_TEST',
  )

  return { stages, activeStage, isReadonly }
}
