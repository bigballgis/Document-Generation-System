// ── Segment Types ──

export type SegmentType = 'COVER' | 'TOC' | 'CHAPTER' | 'TABLE' | 'SIGNATURE' | 'LEGAL' | 'APPENDIX'

export interface Segment {
  id: number
  name: string
  description: string | null
  filePath: string
  isComponent: boolean
  segmentType: SegmentType | string | null
  createdBy: number
  categoryId: number | null
  tenantId: number
  createdAt: string
  updatedAt: string
}

export interface SegmentVersion {
  id: number
  segmentId: number
  versionNumber: number
  filePath: string
  createdBy: number
  createdAt: string
}

export interface SegmentVariable {
  name: string
  type: string
  required: boolean
  defaultValue: string | null
}

export interface SegmentReview {
  id: number
  templateReviewId: number
  segmentId: number
  reviewerId: number
  status: SegmentReviewStatus
  comment: string | null
  createdAt: string
  completedAt: string | null
}

export type SegmentReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface SegmentTestData {
  id: number
  segmentId: number
  name: string
  testDataJson: string
  createdBy: number
  createdAt: string
  updatedAt: string
}

export interface SegmentTestResult {
  segmentId: number
  testDataId: number
  testDataName: string
  success: boolean
  renderTimeMs: number
  errorMessage: string | null
}

export interface LockInfo {
  segmentId: number
  lockedBy: number
  lockedByUsername: string
  lockedAt: string
  expiresAt: string
}

// ── Assembly / Composite Types ──

export interface AssemblySegmentEntry {
  segmentId: number
  position: number
  enabled: boolean
  pageBreakBefore: boolean
  lockedVersion: number | null
  conditionExpression: string | null
  dataScope: Record<string, string> | null
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
  segmentIds: number[]
  testData?: Record<string, unknown>
}

// ── Composite Preview ──

export interface SegmentPreviewEntry {
  segmentId: number
  segmentName: string
  status: string
  errorMessage: string | null
}

export interface CompositePreview {
  previewUrl: string
  segmentPreviews: SegmentPreviewEntry[]
}

// ── Coverage ──

export interface SegmentCoverageEntry {
  segmentId: number
  segmentName: string
  totalVariables: number
  boundVariables: number
  coveragePercent: number
}

export interface CompositeCoverageReport {
  overallCoveragePercent: number
  segmentCoverages: SegmentCoverageEntry[]
}

// ── Review Submission ──

export interface SegmentReviewerAssignment {
  segmentId: number
  reviewerId: number
}

export interface SubmitCompositeReviewRequest {
  assignments: SegmentReviewerAssignment[]
  reviewLevel?: number
}

export interface ReviewActionRequest {
  comment: string
}

// ── Test Report ──

export interface CompositeTestReport {
  compositeTemplateId: number
  templateName: string
  segmentResults: SegmentTestResult[]
  totalTests: number
  passedTests: number
  failedTests: number
  executedAt: string
}

// ── Migration ──

export interface MigrationResult {
  compositeTemplateId: number
  segmentId: number
  migratedDataSources: number
  migratedExpressions: number
  migratedVariableBindings: number
  archivedOriginalTemplateId: number
}

// ── Recommendation & Duplicate ──

export interface SegmentRecommendation {
  segmentId: number
  segmentName: string
  segmentType: string | null
  reason: string
  relevanceScore: number
}

export interface DuplicatePair {
  segmentIdA: number
  segmentIdB: number
  segmentNameA: string
  segmentNameB: string
  similarityPercent: number
}

export interface DuplicateAnalysis {
  duplicates: DuplicatePair[]
}

// ── Segment Template (preset / custom) ──

export interface SegmentTemplate {
  id: number
  name: string
  description: string | null
  segmentType: string | null
  preset: boolean
  createdBy: number | null
}

// ── Permission ──

export type PermissionType = 'VIEW' | 'EDIT' | 'ADMIN'

export interface SegmentPermission {
  id: number
  templateId: number | null
  userId: number | null
  teamId: number | null
  permissionType: PermissionType
  grantedAt: string
  grantedBy: number
}

export interface GrantSegmentPermissionRequest {
  userId?: number | null
  teamId?: number | null
  permissionType: PermissionType
}

// ── Query ──

export interface SegmentQuery {
  keyword?: string
  categoryId?: number | null
  tagId?: number | null
  segmentType?: SegmentType | string | ''
  isComponent?: boolean
  page: number
  size: number
}

// ── Create / Update Requests ──

export interface CreateSegmentRequest {
  name: string
  description?: string
  segmentType?: string
  categoryId?: number | null
  tagIds?: number[]
}

export interface UpdateSegmentRequest {
  name?: string
  description?: string
  segmentType?: string
  categoryId?: number | null
  tagIds?: number[]
}

export interface CreateSegmentTestDataRequest {
  name: string
  testDataJson: string
}

export interface CreateSegmentTemplateRequest {
  segmentId: number
}
