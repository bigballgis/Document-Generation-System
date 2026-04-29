import request from './request'
import type { AsyncTaskDTO, GenerateDocumentRequest, GenerateDocumentResponse, BatchGenerateRequest } from '@/types/document'

export function generateDocument(templateId: number, data?: GenerateDocumentRequest, version?: number) {
  return request.post<any, GenerateDocumentResponse>(`/generate/${templateId}`, data ?? {}, {
    params: version != null ? { version } : undefined,
  })
}

/** Forces Word (.docx) output only (same body shape as generateDocument). */
export function generateDocumentWord(templateId: number, data?: GenerateDocumentRequest, version?: number) {
  return request.post<any, GenerateDocumentResponse>(`/generate/${templateId}/word`, data ?? {}, {
    params: version != null ? { version } : undefined,
  })
}

/** Forces PDF output only (same body shape as generateDocument). */
export function generateDocumentPdf(templateId: number, data?: GenerateDocumentRequest, version?: number) {
  return request.post<any, GenerateDocumentResponse>(`/generate/${templateId}/pdf`, data ?? {}, {
    params: version != null ? { version } : undefined,
  })
}

export function generateDocumentAsync(templateId: number, data?: GenerateDocumentRequest) {
  return request.post<any, AsyncTaskDTO>(`/generate/${templateId}/async`, data ?? {})
}

export function generateDocumentAsyncWord(templateId: number, data?: GenerateDocumentRequest) {
  return request.post<any, AsyncTaskDTO>(`/generate/${templateId}/async/word`, data ?? {})
}

export function generateDocumentAsyncPdf(templateId: number, data?: GenerateDocumentRequest) {
  return request.post<any, AsyncTaskDTO>(`/generate/${templateId}/async/pdf`, data ?? {})
}

export function generateDocumentBatch(templateId: number, data: BatchGenerateRequest) {
  return request.post<any, AsyncTaskDTO>(`/generate/${templateId}/batch`, data)
}
