import request from './request'
import type { TemplateDTO } from './templates'

export function uploadFile<T = TemplateDTO>(url: string, file: File): Promise<T> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<any, T>(url, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function importDocx(file: File): Promise<TemplateDTO> {
  return uploadFile('/templates/import', file)
}

export function exportDocx(templateId: number): Promise<Blob> {
  return request.get<any, Blob>(`/templates/${templateId}/export`, { responseType: 'blob' })
}

export function exportConfig(templateId: number): Promise<Blob> {
  return request.get<any, Blob>(`/templates/${templateId}/export-config`, { responseType: 'blob' })
}

export function importConfig(file: File): Promise<TemplateDTO> {
  return uploadFile('/templates/import-config', file)
}
