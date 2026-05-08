import request from './request'
import type { PageResult } from '@/types'
import type { ScenarioReadinessReportDTO } from '@/types/scenarioReadiness'


/** Matches backend RenderConfigDocument / render-config.json (REQ-R7-001). */
export interface RenderConfigDocument {
  schemaVersion?: number
  textWatermark?: {
    text: string
    fontSize?: number
    color?: string
    opacity?: number
    rotation?: number
  }
  imageWatermark?: {
    imageSource: string
    position?: string
    opacity?: number
  }
  barcodes?: unknown[]
}

export interface TemplateDTO {
  id: number
  name: string
  description: string
  status: TemplateStatus
  categoryId: number | null
  categoryName?: string
  tags: TagDTO[]
  version: number
  outputFormat: string
  reviewRequired: boolean
  tenantId: number
  templateType: 'SINGLE' | 'COMPOSITE'
  /** JSON string of {@link RenderConfigDocument} when set */
  renderConfig?: string | null
  createdAt: string
  updatedAt: string
  createdBy?: string
}

export type TemplateStatus = 'DRAFT' | 'IN_TEST' | 'PENDING_REVIEW' | 'REVIEWED' | 'ACTIVE' | 'ARCHIVED'

export interface CreateTemplateRequest {
  name: string
  description: string
  categoryId?: number | null
  tagIds?: number[]
  outputFormat?: string
  reviewRequired?: boolean
}

export interface UpdateTemplateRequest extends CreateTemplateRequest {}

export interface TemplateQuery {
  keyword?: string
  categoryId?: number | null
  tagId?: number | null
  status?: TemplateStatus | ''
  page: number
  size: number
}

export interface TemplateVersionDTO {
  id: number
  templateId: number
  versionNumber: number
  configJson: string
  createdAt: string
  createdBy?: string
  comment?: string
}

export interface VersionDiffResult {
  versionA: number
  versionB: number
  textDiffs: DiffEntry[]
  variableDiffs: DiffEntry[]
  dataSourceDiffs: DiffEntry[]
  expressionDiffs: DiffEntry[]
  summary: { added: number; removed: number; modified: number }
}

export interface DiffEntry {
  field: string
  type: 'ADDED' | 'REMOVED' | 'MODIFIED'
  oldValue?: string
  newValue?: string
}

export interface VariableDTO {
  id: number
  templateId: number
  name: string
  type: string
  defaultValue?: string
  description?: string
  source?: string
  bound: boolean
  boundTo?: string
}

export interface BindVariableRequest {
  dataSourceId?: number
  expressionId?: number
  fieldPath?: string
}

export interface CoverageReport {
  templateId: number
  totalVariables: number
  boundVariables: number
  unboundVariables: number
  coverageRate: number
  boundTags: string[]
  unboundTags: string[]
  unusedFields: string[]
}

export interface CategoryDTO {
  id: number
  name: string
  parentId: number | null
  sortOrder: number
  children?: CategoryDTO[]
}

export interface TagDTO {
  id: number
  name: string
}


export function getTemplates(query: TemplateQuery) {
  return request.get<any, PageResult<TemplateDTO>>('/templates', {
    params: {
      keyword: query.keyword || undefined,
      categoryId: query.categoryId || undefined,
      tagId: query.tagId || undefined,
      status: query.status || undefined,
      page: (query.page || 1) - 1,
      size: query.size,
    },
  })
}

export function getTemplate(id: number) {
  return request.get<any, TemplateDTO>(`/templates/${id}`)
}

/** Same-tenant, same-team users who may review the template (excludes template author). */
export interface ReviewerCandidateDTO {
  id: number
  username: string
  email: string
  teamId: number | null
  role: string
  teamReviewLane?: string | null
}

export function getReviewerCandidates(templateId: number, reviewLevel?: number) {
  return request.get<any, ReviewerCandidateDTO[]>(`/templates/${templateId}/reviewers/candidates`, {
    params: reviewLevel != null ? { reviewLevel } : {},
  })
}

export function createTemplate(data: CreateTemplateRequest) {
  return request.post<any, TemplateDTO>('/templates', data)
}

export function updateTemplate(id: number, data: UpdateTemplateRequest) {
  return request.put<any, TemplateDTO>(`/templates/${id}`, data)
}

export function updateTemplateRenderConfig(templateId: number, body: RenderConfigDocument) {
  return request.put<any, TemplateDTO>(`/templates/${templateId}/render-config`, body)
}

export function clearTemplateRenderConfig(templateId: number) {
  return request.delete<any, TemplateDTO>(`/templates/${templateId}/render-config`)
}

export function deleteTemplate(id: number) {
  return request.delete(`/templates/${id}`)
}

export function cloneTemplate(id: number) {
  return request.post<any, TemplateDTO>(`/templates/${id}/clone`)
}

export function activateTemplate(id: number) {
  return request.post(`/templates/${id}/activate`)
}

export function archiveTemplate(id: number) {
  return request.post(`/templates/${id}/archive`)
}

export function getTemplateVersions(id: number) {
  return request.get<any, TemplateVersionDTO[]>(`/templates/${id}/versions`)
}

export function rollbackVersion(templateId: number, versionId: number) {
  return request.post<any, TemplateDTO>(`/templates/${templateId}/rollback/${versionId}`)
}

export function getVersionDiff(templateId: number, versionA: number, versionB: number) {
  return request.get<any, VersionDiffResult>(`/templates/${templateId}/versions/diff`, {
    params: { versionA, versionB },
  })
}

export function getTemplateVariables(templateId: number) {
  return request.get<any, VariableDTO[]>(`/templates/${templateId}/variables`)
}

export function bindVariable(templateId: number, varId: number, data: BindVariableRequest) {
  return request.put(`/templates/${templateId}/variables/${varId}/bind`, data)
}

export function getTemplateCoverage(templateId: number) {
  return request.get<any, CoverageReport>(`/templates/${templateId}/coverage`)
}

export function getCategories() {
  return request.get<any, CategoryDTO[]>('/categories')
}

export function getTags() {
  return request.get<any, TagDTO[]>('/tags')
}

export function getOnlyOfficeUrl(templateId: number) {
  return request.get<any, { url: string }>(`/templates/${templateId}/onlyoffice-url`)
}

/** DRAFT → IN_TEST: enter testing phase (must use workspace flow for composite templates in practice). */
export function submitTemplateToTest(templateId: number) {
  return request.post<any, TemplateDTO>(`/templates/${templateId}/submit-test`)
}

/** IN_TEST → DRAFT: leave testing and return to design. */
export function returnTemplateToDesign(templateId: number) {
  return request.post<any, TemplateDTO>(`/templates/${templateId}/return-design`)
}

/**
 * @deprecated Use {@link submitTemplateToTest} (DRAFT) then admin {@code submitForReview} (IN_TEST).
 * Legacy DRAFT → PENDING_REVIEW in one step is no longer allowed.
 */
export function submitReview(templateId: number) {
  return request.post<any, TemplateDTO>(`/templates/${templateId}/submit-review`)
}

export function getAvailableTransitions(templateId: number) {
  return request.get<any, string[]>(`/templates/${templateId}/available-transitions`)
}

export function scanVariables(templateId: number) {
  return request.post<any, VariableDTO[]>(`/templates/${templateId}/variables/scan`)
}

/** Export coverage report as JSON file download */
export function exportCoverageReport(templateId: number) {
  return request.get(`/templates/${templateId}/coverage/export`, { responseType: 'blob' })
}

export function getCategory(id: number) {
  return request.get<any, CategoryDTO>(`/categories/${id}`)
}

export function createCategory(data: { name: string; parentId?: number | null; sortOrder?: number }) {
  return request.post<any, CategoryDTO>('/categories', data)
}

export function updateCategory(id: number, data: { name?: string; parentId?: number | null; sortOrder?: number }) {
  return request.put<any, CategoryDTO>(`/categories/${id}`, data)
}

export function deleteCategory(id: number) {
  return request.delete(`/categories/${id}`)
}

export function getTag(id: number) {
  return request.get<any, TagDTO>(`/tags/${id}`)
}

export function createTag(data: { name: string }) {
  return request.post<any, TagDTO>('/tags', data)
}

export function updateTag(id: number, data: { name: string }) {
  return request.put<any, TagDTO>(`/tags/${id}`, data)
}

export function deleteTag(id: number) {
  return request.delete(`/tags/${id}`)
}

export function addTagToTemplate(tagId: number, templateId: number) {
  return request.post(`/tags/${tagId}/templates/${templateId}`)
}

export function removeTagFromTemplate(tagId: number, templateId: number) {
  return request.delete(`/tags/${tagId}/templates/${templateId}`)
}

export function getTemplateTags(templateId: number) {
  return request.get<any, TagDTO[]>(`/tags/templates/${templateId}`)
}

/** Create a new draft version from an ACTIVE template */
export function createDraftVersion(templateId: number) {
  return request.post<any, TemplateDTO>(`/templates/${templateId}/create-draft-version`)
}

/** Scenario validation workspace: aggregate + per-scenario readiness (branch/loop/parameter semantics). */
export function getScenarioReadiness(templateId: number) {
  return request.get<any, ScenarioReadinessReportDTO>(`/templates/${templateId}/scenario-readiness`)
}

