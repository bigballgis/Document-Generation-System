/** Workflow step definition */
export interface WorkflowStep {
  key: string
  label: string
  completed: boolean
  active: boolean
  alwaysAvailable: boolean
}

/** Step key type */
export type StepKey = 'create' | 'data' | 'segments' | 'editor' | 'testing' | 'review' | 'export' | 'settings'

/** Tab name type */
export type TabName = 'dataStructure' | 'segments' | 'editor' | 'testing' | 'reviewPublish' | 'exportImport' | 'settings'

/** Placeholder tab configuration */
export interface PlaceholderTabConfig {
  tabName: TabName
  icon: string
  phase: number
  messageKey: string
  descriptionKey: string
}
