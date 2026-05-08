export interface MissingBusinessSituationDTO {
  type: string
  name: string
  missingPath: string
}

export interface ScenarioSuggestionDTO {
  type: string
  code?: string
  title?: string
  reason?: string
  sourceMissingSituation?: MissingBusinessSituationDTO
}

export interface ScenarioReadinessSummaryDTO {
  overallReadiness: number
  conditionalClauseReadiness: number
  repeatingDetailReadiness: number
  requiredInformationReadiness: number
  totalBranches: number
  coveredBranches: number
  totalLoopScenarios: number
  coveredLoopScenarios: number
  totalParameters: number
  coveredParameters: number
  missingSituations: MissingBusinessSituationDTO[]
}

export interface ScenarioReadinessCaseDTO {
  testCaseId: number
  scenarioName: string
  overallReadiness: number
  conditionalClauseReadiness: number
  repeatingDetailReadiness: number
  requiredInformationReadiness: number
  missingSituations: MissingBusinessSituationDTO[]
}

export interface ScenarioReadinessReportDTO {
  templateId: number
  readiness: ScenarioReadinessSummaryDTO
  scenarios: ScenarioReadinessCaseDTO[]
  suggestions: ScenarioSuggestionDTO[]
  checkedAt: string
  warnings: string[]
}
