
export interface GenerateDocumentRequest {
  parameters?: Record<string, unknown>
  outputFormat?: string       // WORD | PDF; BOTH rejected if sent explicitly; legacy template BOTH treated as WORD server-side
  storageStrategy?: string    // 'TEMP' | 'PERSISTENT'
}

export interface SegmentRenderStat {
  segmentName: string
  renderTimeMs: number
  success: boolean
  errorMessage: string | null
}

export interface GenerateDocumentResponse {
  documentId: number
  templateId: number
  format: string
  storageStrategy: string
  downloadUrl: string
  fileSize: number
  generatedAt: string
  content: string | null
  secondaryDocument: GenerateDocumentResponse | null
  segmentRenderStats: SegmentRenderStat[] | null
  totalRenderTimeMs: number | null
}

export interface BatchGenerateRequest {
  dataSets: Array<Record<string, unknown>>
  outputFormat?: string
  storageStrategy?: string
  failureStrategy?: string    // 'CONTINUE' | 'THRESHOLD'
  failureThreshold?: number
}


export type TaskStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface AsyncTaskDTO {
  taskId: string
  taskType: string
  templateId: number
  status: TaskStatus | string
  progress: number
  totalCount: number
  completedCount: number
  successCount: number
  failCount: number
  documentId: number | null
  errorMessage: string | null
  downloadUrl: string | null
  createdAt: string
  completedAt: string | null
}

export interface TaskProgress {
  taskId: string
  status: string
  progress: number
  totalCount: number
  completedCount: number
  successCount: number
  failCount: number
}

export interface TaskQuery {
  status?: string
  templateId?: number
  page: number
  size: number
}


export interface GeneratedDocumentDTO {
  id: number
  templateId: number
  filePath: string
  format: string
  status: string
  storageStrategy: string
  fileSize: number
  pageCount: number | null
  downloadUrl: string
  generatedAt: string
  expiresAt: string | null
}

export interface DocumentQuery {
  templateId?: number
  status?: string
  startTime?: string
  endTime?: string
  page: number
  size: number
}

export interface MergeDocumentsRequest {
  documentIds: number[]
  insertPageBreaks: boolean
  generateToc: boolean
  outputFormat: string        // 'DOCX' | 'PDF'
}


export type ExpressionType = 'JAVASCRIPT' | 'EXCEL'

export interface ExpressionDTO {
  id: number
  templateId: number
  name: string
  expressionType: string
  expressionText: string
  description: string | null
  executionOrder: number
  createdAt: string
}

export interface CreateExpressionRequest {
  name: string
  expressionType: string
  expressionText: string
  description?: string
  executionOrder?: number
}

export interface UpdateExpressionRequest {
  name?: string
  expressionType?: string
  expressionText?: string
  description?: string
  executionOrder?: number
}

export interface ValidateExpressionRequest {
  expression: string
  expressionType: string
}

export interface ExpressionValidationResult {
  valid: boolean
  errorMessage?: string
  errorPosition?: number
}

