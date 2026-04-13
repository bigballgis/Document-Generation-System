import request from './request'
import type { PageResult } from '@/types'
import type { GeneratedDocumentDTO, DocumentQuery, MergeDocumentsRequest } from '@/types/document'

export function getDocuments(query: DocumentQuery) {
  return request.get<any, PageResult<GeneratedDocumentDTO>>('/documents', {
    params: {
      templateId: query.templateId || undefined,
      status: query.status || undefined,
      startTime: query.startTime || undefined,
      endTime: query.endTime || undefined,
      page: (query.page || 1) - 1,
      size: query.size,
    },
  })
}

export function getDocument(id: number) {
  return request.get<any, GeneratedDocumentDTO>(`/documents/${id}`)
}

export function downloadDocument(id: number) {
  return request.get(`/documents/${id}/download`, { responseType: 'blob' })
}

export function mergeDocuments(data: MergeDocumentsRequest) {
  return request.post<any, GeneratedDocumentDTO>('/documents/merge', data)
}
