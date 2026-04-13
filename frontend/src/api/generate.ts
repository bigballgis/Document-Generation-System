import request from './request'
import type { AsyncTaskDTO, GenerateDocumentRequest, GenerateDocumentResponse, BatchGenerateRequest } from '@/types/document'

export function generateDocument(templateId: number, data?: GenerateDocumentRequest, version?: number) {
  return request.post<any, GenerateDocumentResponse>(`/generate/${templateId}`, data ?? {}, {
    params: version != null ? { version } : undefined,
  })
}

export function generateDocumentAsync(templateId: number, data?: GenerateDocumentRequest) {
  return request.post<any, AsyncTaskDTO>(`/generate/${templateId}/async`, data ?? {})
}

export function generateDocumentBatch(templateId: number, data: BatchGenerateRequest) {
  return request.post<any, AsyncTaskDTO>(`/generate/${templateId}/batch`, data)
}
