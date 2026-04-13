import request from './request'
import type { PageResult } from '@/types'

export interface WebhookConfigDTO {
  id: number
  templateId: number
  url: string
  payloadTemplate: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateWebhookRequest {
  url: string
  secret: string
  payloadTemplate?: string
}

export interface UpdateWebhookRequest {
  url?: string
  secret?: string
  payloadTemplate?: string
  enabled?: boolean
}

export interface WebhookLogDTO {
  id: number
  webhookConfigId: number
  eventType: string
  payload: string
  responseStatus: number
  responseBody: string
  sentAt: string
}

export function createWebhook(templateId: number, data: CreateWebhookRequest) {
  return request.post<any, WebhookConfigDTO>(`/templates/${templateId}/webhooks`, data)
}

export function listWebhooks(templateId: number) {
  return request.get<any, WebhookConfigDTO[]>(`/templates/${templateId}/webhooks`)
}

export function updateWebhook(webhookId: number, data: UpdateWebhookRequest) {
  return request.put<any, WebhookConfigDTO>(`/webhooks/${webhookId}`, data)
}

export function deleteWebhook(webhookId: number) {
  return request.delete(`/webhooks/${webhookId}`)
}

export function getWebhookLogs(webhookId: number, params: { page: number; size: number }) {
  return request.get<any, PageResult<WebhookLogDTO>>(`/webhooks/${webhookId}/logs`, { params })
}
