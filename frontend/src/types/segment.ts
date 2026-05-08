
export type SegmentType = 'COVER' | 'TOC' | 'CHAPTER' | 'TABLE' | 'SIGNATURE' | 'LEGAL' | 'APPENDIX'

export type PageNumberFormat = 'ARABIC' | 'ROMAN' | 'ALPHA'


export interface AssemblySegmentEntry {
  filePath: string
  name: string
  segmentType: SegmentType | string | null
  position: number
  enabled: boolean
  pageBreakBefore: boolean
  conditionExpression: string | null
  dataScope: Record<string, string> | null
  headerFilePath: string | null
  footerFilePath: string | null
  pageNumberFormat: PageNumberFormat | null
  pageNumberStart: number | null
}

export interface AssemblyConfig {
  segments: AssemblySegmentEntry[]
}

export interface CreateCompositeTemplateRequest {
  name: string
  description?: string
  outputFormat?: string
  teamId?: number | null
  categoryId?: number | null
}

export interface UpdateAssemblyConfigRequest {
  segments: AssemblySegmentEntry[]
}

export interface SelectivePreviewRequest {
  positions: number[]
  testData?: Record<string, unknown>
}


export interface SegmentPreviewEntry {
  segmentName: string
  status: string
  errorMessage: string | null
}

export interface CompositePreview {
  previewUrl: string
  segmentPreviews: SegmentPreviewEntry[]
}


export interface SegmentCoverageEntry {
  segmentName: string
  totalVariables: number
  boundVariables: number
  coveragePercent: number
}

export interface CompositeCoverageReport {
  overallCoveragePercent: number
  segmentCoverages: SegmentCoverageEntry[]
}


export interface MigrationResult {
  compositeTemplateId: number
  segmentId: number
  migratedDataSources: number
  migratedExpressions: number
  migratedVariableBindings: number
  archivedOriginalTemplateId: number
}

