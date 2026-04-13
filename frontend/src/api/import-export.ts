import request from './request'
import type { TemplateDTO } from './templates'

/** Import a .docx file as a new template */
export function importDocx(file: File): Promise<TemplateDTO> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/templates/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** Export template as .docx — triggers browser download */
export function exportDocx(templateId: number): Promise<Blob> {
  return request.get(`/templates/${templateId}/export`, { responseType: 'blob' })
}

/** Export full template configuration as JSON — triggers browser download */
export function exportConfig(templateId: number): Promise<Blob> {
  return request.get(`/templates/${templateId}/export-config`, { responseType: 'blob' })
}

/** Import a JSON configuration file to restore a template */
export function importConfig(file: File): Promise<TemplateDTO> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/templates/import-config', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
