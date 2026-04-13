import request from './request'
import type { PageResult } from '@/types'
import type { AsyncTaskDTO, TaskProgress, TaskQuery } from '@/types/document'

export function getTasks(query: TaskQuery) {
  return request.get<any, PageResult<AsyncTaskDTO>>('/tasks', {
    params: {
      status: query.status || undefined,
      templateId: query.templateId || undefined,
      page: (query.page || 1) - 1,
      size: query.size,
    },
  })
}

export function getTaskStatus(taskId: string) {
  return request.get<any, AsyncTaskDTO>(`/tasks/${taskId}`)
}

export function getTaskProgress(taskId: string) {
  return request.get<any, TaskProgress>(`/tasks/${taskId}/progress`)
}

export function downloadTaskResult(taskId: string) {
  return request.get(`/tasks/${taskId}/download`, { responseType: 'blob' })
}
