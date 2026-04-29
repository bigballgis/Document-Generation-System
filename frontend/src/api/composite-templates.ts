import request from './request'
import type { TemplateDTO } from './templates'
import { uploadFile } from './import-export'
import type {
  AssemblyConfig,
  UpdateAssemblyConfigRequest,
  CreateCompositeTemplateRequest,
  SelectivePreviewRequest,
  CompositePreview,
  CompositeCoverageReport,
  AssemblySegmentEntry,
  MigrationResult,
} from '@/types/segment'


export function createCompositeTemplate(data: CreateCompositeTemplateRequest) {
  return request.post<any, TemplateDTO>('/composite-templates', data)
}


export function getAssemblyConfig(id: number) {
  return request.get<any, AssemblyConfig>(`/composite-templates/${id}/assembly-config`)
}

export function updateAssemblyConfig(id: number, data: UpdateAssemblyConfigRequest) {
  return request.put<any, AssemblyConfig>(`/composite-templates/${id}/assembly-config`, data)
}


export function previewCompositeTemplate(id: number) {
  return request.post<any, CompositePreview>(`/composite-templates/${id}/preview`)
}

export function previewSelectiveSegments(id: number, data: SelectivePreviewRequest) {
  return request.post<any, CompositePreview>(`/composite-templates/${id}/preview/selective`, data)
}


export function getCompositeCoverage(id: number) {
  return request.get<any, CompositeCoverageReport>(`/composite-templates/${id}/coverage`)
}


export function uploadSegment(templateId: number, file: File, name: string, segmentType?: string) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('name', name)
  if (segmentType) {
    formData.append('segmentType', segmentType)
  }
  return request.post<any, AssemblySegmentEntry>(
    `/composite-templates/${templateId}/upload-segment`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  )
}


export function exportCompositeAsZip(id: number) {
  return request.get(`/composite-templates/${id}/export`, {
    responseType: 'blob',
  })
}

export function exportCompositeConfig(id: number) {
  return request.get(`/composite-templates/${id}/export-config`, {
    responseType: 'blob',
  })
}

export function importCompositeFromZip(file: File) {
  return uploadFile<TemplateDTO>('/composite-templates/import', file)
}


export function migrateToComposite(templateId: number) {
  return request.post<any, MigrationResult>(`/templates/${templateId}/migrate-to-composite`)
}


export function createBlankSegment(templateId: number, name: string, segmentType?: string) {
  return request.post<any, AssemblySegmentEntry>(
    `/composite-templates/${templateId}/create-blank-segment`,
    { name, segmentType },
  )
}

export function createBlankHeaderFooter(templateId: number, type: 'header' | 'footer') {
  return request.post<any, { filePath: string }>(
    `/composite-templates/${templateId}/create-blank-header-footer`,
    { type },
  )
}

export function getSegmentOnlyOfficeUrl(templateId: number, segmentIndex: number) {
  return request.get<any, { url: string }>(
    `/composite-templates/${templateId}/segments/${segmentIndex}/onlyoffice-url`,
  )
}

/** Sign an OnlyOffice editor config with the server-side JWT secret */
export function signOnlyOfficeConfig(config: Record<string, any>): Promise<{ token: string }> {
  return request.post<any, { token: string }>('/templates/onlyoffice/sign', config)
}


export interface SegmentVersionDTO {
  id: number
  templateId: number
  segmentName: string
  versionNumber: number
  filePath: string
  segmentType: string | null
  configSnapshot: string | null
  comment: string | null
  createdBy: number
  createdAt: string
}

export interface SegmentVersionDiffResult {
  templateId: number
  segmentName: string
  versionA: number
  versionB: number
  diffs: SegmentDiffEntry[]
  filePathChanged: boolean
  oldFilePath: string | null
  newFilePath: string | null
  contentDiffs: ContentDiffLine[]
  contentChanged: boolean
  truncated: boolean
}

export interface SegmentDiffEntry {
  field: string
  changeType: 'ADDED' | 'REMOVED' | 'MODIFIED'
  oldValue: string | null
  newValue: string | null
}

export interface ContentDiffLine {
  type: 'EQUAL' | 'ADDED' | 'REMOVED' | 'MODIFIED'
  oldLineNumber: number | null
  newLineNumber: number | null
  oldText: string | null
  newText: string | null
}

export function publishSegment(templateId: number, segmentName: string, comment?: string) {
  return request.post<any, SegmentVersionDTO>(
    `/composite-templates/${templateId}/segments/publish`,
    { segmentName, comment },
  )
}

export function getSegmentVersions(templateId: number, segmentName: string) {
  return request.get<any, SegmentVersionDTO[]>(
    `/composite-templates/${templateId}/segments/${encodeURIComponent(segmentName)}/versions`,
  )
}

export function compareSegmentVersions(
  templateId: number,
  segmentName: string,
  versionA: number,
  versionB: number,
  includeContentDiff = false,
) {
  return request.get<any, SegmentVersionDiffResult>(
    `/composite-templates/${templateId}/segments/${encodeURIComponent(segmentName)}/versions/diff`,
    { params: { versionA, versionB, includeContentDiff } },
  )
}

export function rollbackSegmentVersion(
  templateId: number,
  segmentName: string,
  targetVersion: number,
) {
  return request.post<any, SegmentVersionDTO>(
    `/composite-templates/${templateId}/segments/${encodeURIComponent(segmentName)}/rollback/${targetVersion}`,
  )
}

