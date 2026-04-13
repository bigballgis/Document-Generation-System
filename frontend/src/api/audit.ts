import request from './request'
import type { PageResult } from '@/types'

export interface AuditLogDTO {
  id: number
  operationType: string
  operator: string
  operationTime: string
  operationDetail: string
  ipAddress: string
  resourceType: string
  resourceId: string
  result: 'SUCCESS' | 'FAILURE'
}

export interface AuditLogQuery {
  operationType?: string
  operator?: string
  startDate?: string
  endDate?: string
  page: number
  size: number
}

export function getAuditLogs(query: AuditLogQuery) {
  return request.get<any, PageResult<AuditLogDTO>>('/audit-logs', {
    params: {
      operationType: query.operationType || undefined,
      operator: query.operator || undefined,
      startDate: query.startDate || undefined,
      endDate: query.endDate || undefined,
      page: query.page,
      size: query.size,
    },
  })
}

export function exportAuditLogs(query: Omit<AuditLogQuery, 'page' | 'size'>, format: 'CSV' | 'JSON') {
  return request.get('/audit-logs/export', {
    params: {
      operationType: query.operationType || undefined,
      operator: query.operator || undefined,
      startDate: query.startDate || undefined,
      endDate: query.endDate || undefined,
      format,
    },
    responseType: 'blob',
  })
}
