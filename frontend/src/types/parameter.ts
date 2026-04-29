
export type ParameterType = 'REQUEST' | 'DERIVED'
export type DataType = 'STRING' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'ARRAY' | 'OBJECT'
export type ExpressionType = 'JAVASCRIPT' | 'EXCEL_FORMULA'

export interface ValidationRules {
  not_null?: boolean
  not_blank?: boolean
  min_length?: number
  max_length?: number
  min?: number
  max?: number
  pattern?: string
  enum_values?: string[]
  min_items?: number
  max_items?: number
  date_format?: string
  date_before?: string
  date_after?: string
  custom_message?: string
}

export interface ParameterDTO {
  id: number
  templateId: number
  parentId: number | null
  name: string
  parameterType: ParameterType
  dataType: DataType
  required: boolean
  defaultValue: string | null
  description: string | null
  sortOrder: number
  expressionText: string | null
  expressionType: ExpressionType | null
  validationRules: ValidationRules | null
  version: number
  parameterPath: string
  children: ParameterDTO[]
  createdAt: string
  updatedAt: string
}

export interface CreateParameterRequest {
  name: string
  parameterType?: ParameterType
  dataType?: DataType
  required?: boolean
  defaultValue?: string
  description?: string
  sortOrder?: number
  expressionText?: string
  expressionType?: ExpressionType
  validationRules?: ValidationRules
  parentId?: number | null
}

export interface UpdateParameterRequest {
  name?: string
  parameterType?: ParameterType
  dataType?: DataType
  required?: boolean
  defaultValue?: string
  description?: string
  sortOrder?: number
  expressionText?: string
  expressionType?: ExpressionType
  validationRules?: ValidationRules
  version?: number
}


export interface BatchUpdateItem {
  id: number
  version: number
  name?: string
  parameterType?: ParameterType
  dataType?: DataType
  required?: boolean
  defaultValue?: string
  description?: string
  sortOrder?: number
  expressionText?: string
  expressionType?: ExpressionType
  validationRules?: ValidationRules
}


export type PlaceholderType = 'SIMPLE' | 'OBJECT_PATH' | 'LOOP' | 'CONDITION' | 'AGGREGATION'

export interface PlaceholderInfo {
  name: string
  fullPath: string
  type: PlaceholderType
  segments: string[]
  children: PlaceholderInfo[]
}

export interface ScanResultDTO {
  matched: PlaceholderInfo[]
  unmatchedPlaceholders: PlaceholderInfo[]
  unusedParameters: ParameterDTO[]
}


export interface ParameterSchemaEntry {
  name: string
  dataType: DataType
  required: boolean
  defaultValue: string | null
  description: string | null
  validationRules: ValidationRules | null
  properties?: ParameterSchemaEntry[]
  items?: ParameterSchemaEntry[]
}

export interface ParameterSchemaDTO {
  templateName: string
  templateVersion: number
  totalParameterCount: number
  requiredParameterCount: number
  parameters: ParameterSchemaEntry[]
  sampleRequestBody: Record<string, unknown>
}


export interface AggregationPropertyDTO {
  name: string
  placeholderPath: string
  resultDataType: DataType | 'OBJECT'
  description: string
}

export interface AggregationSchemaDTO {
  arrayName: string
  arrayPath: string
  properties: AggregationPropertyDTO[]
}


export interface UncoveredItem {
  type: 'BRANCH' | 'LOOP' | 'PARAMETER'
  name: string
  missingPath: string
}

export interface CoverageReport {
  templateId: number
  templateName: string
  branchCoverage: number
  loopCoverage: number
  parameterCoverage: number
  overallCoverage: number
  totalBranches: number
  coveredBranches: number
  totalLoopScenarios: number
  coveredLoopScenarios: number
  totalParameters: number
  coveredParameters: number
  uncoveredItems: UncoveredItem[]
  belowThreshold: boolean
  threshold: number
  checkedAt: string
  warnings: string[]
}

