import request from './request'
import type { TemplateDTO } from './templates'
import { uploadFile } from './import-export'
import type {
  Segment,
  AssemblyConfig,
  UpdateAssemblyConfigRequest,
  CreateCompositeTemplateRequest,
  SelectivePreviewRequest,
  CompositePreview,
  CompositeCoverageReport,
  SegmentReview,
  SubmitCompositeReviewRequest,
  ReviewActionRequest,
  CompositeTestReport,
  SegmentRecommendation,
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

// ── Segments ──

export function getCompositeSegments(id: number) {
  return request.get<any, Segment[]>(`/composite-templates/${id}/segments`)
}

// ── Reviews ──

export function submitCompositeReview(id: number, data: SubmitCompositeReviewRequest) {
  return request.post<any, SegmentReview[]>(`/composite-templates/${id}/reviews`, data)
}

export function getSegmentReviews(id: number, reviewId: number) {
  return request.get<any, SegmentReview[]>(`/composite-templates/${id}/reviews/${reviewId}/segments`)
}

export function approveSegmentReview(reviewId: number, data?: ReviewActionRequest) {
  return request.put<any, SegmentReview>(`/segment-reviews/${reviewId}/approve`, data ?? {})
}

export function rejectSegmentReview(reviewId: number, data: ReviewActionRequest) {
  return request.put<any, SegmentReview>(`/segment-reviews/${reviewId}/reject`, data)
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

// ── Recommendations ──

export function getSegmentRecommendations(id: number) {
  return request.get<any, SegmentRecommendation[]>(`/composite-templates/${id}/recommendations`)
}

// ── Composite Tests ──

export function runAllCompositeTests(id: number) {
  return request.post<any, CompositeTestReport>(`/composite-templates/${id}/tests/run`)
}

// ── Migration ──

export function migrateToComposite(templateId: number) {
  return request.post<any, MigrationResult>(`/templates/${templateId}/migrate-to-composite`)
}
