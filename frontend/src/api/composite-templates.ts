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

// ── Composite Template CRUD ──

export function createCompositeTemplate(data: CreateCompositeTemplateRequest) {
  return request.post<any, TemplateDTO>('/composite-templates', data)
}

// ── Assembly Config ──

export function getAssemblyConfig(id: number) {
  return request.get<any, AssemblyConfig>(`/composite-templates/${id}/assembly-config`)
}

export function updateAssemblyConfig(id: number, data: UpdateAssemblyConfigRequest) {
  return request.put<any, AssemblyConfig>(`/composite-templates/${id}/assembly-config`, data)
}

// ── Preview ──

export function previewCompositeTemplate(id: number) {
  return request.post<any, CompositePreview>(`/composite-templates/${id}/preview`)
}

export function previewSelectiveSegments(id: number, data: SelectivePreviewRequest) {
  return request.post<any, CompositePreview>(`/composite-templates/${id}/preview/selective`, data)
}

// ── Coverage ──

export function getCompositeCoverage(id: number) {
  return request.get<any, CompositeCoverageReport>(`/composite-templates/${id}/coverage`)
}

// ── Upload Segment ──

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

// ── Import / Export ──

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

// ── Migration ──

export function migrateToComposite(templateId: number) {
  return request.post<any, MigrationResult>(`/templates/${templateId}/migrate-to-composite`)
}

// ── Blank Segment / Header-Footer / OnlyOffice URL ──

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
