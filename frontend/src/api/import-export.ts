import request from './request'
import type { TemplateDTO } from './templates'

/** Upload a file via multipart POST and return TemplateDTO */
function uploadFile(url: string, file: File): Promise<TemplateDTO> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<any, TemplateDTO>(url, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** Import a .docx file as a new template */
export function importDocx(file: File): Promise<TemplateDTO> {
  return uploadFile('/templates/import', file)
}

/** Export template as .docx — triggers browser download */
export function exportDocx(templateId: number): Promise<Blob> {
  return request.get<any, Blob>(`/templates/${templateId}/export`, { responseType: 'blob' })
}

/** Export full template configuration as JSON — triggers browser download */
export function exportConfig(templateId: number): Promise<Blob> {
  return request.get<any, Blob>(`/templates/${templateId}/export-config`, { responseType: 'blob' })
}

/** Import a JSON configuration file to restore a template */
export function importConfig(file: File): Promise<TemplateDTO> {
  return uploadFile('/templates/import-config', file)
}
