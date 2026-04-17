// ── Stage types (four-stage workflow) ──

/** Stage name — fixed order: design → test → approval → publish */
export type StageName = 'design' | 'test' | 'approval' | 'publish'

/** Stage status */
export type StageStatus = 'not_started' | 'in_progress' | 'completed' | 'readonly'

/** Stage definition for StageIndicator */
export interface StageDefinition {
  name: StageName
  label: string
  status: StageStatus
  clickable: boolean
}

// ── Design step types (three-step sub-workflow within design stage) ──

export type DesignStepName = 'parameter-table' | 'segment-canvas' | 'segment-detail'

export interface ParameterBreadcrumbItem {
  id: number | null
  name: string
  tableType: 'main' | 'sub' | 'related'
}
