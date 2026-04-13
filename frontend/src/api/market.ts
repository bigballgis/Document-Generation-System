import request from './request'
import type { PageResult } from '@/types'

// --- Market Types ---

export interface MarketTemplateDTO {
  id: number
  name: string
  description: string
  category: string
  tags: string[]
  author: string
  usageCount: number
  rating: number
  shareScope: 'TENANT_INTERNAL' | 'GLOBAL'
  createdAt: string
}

export interface MarketQuery {
  keyword?: string
  category?: string
  tag?: string
  sort?: 'popular' | 'recent' | 'rating'
  page: number
  size: number
}

export interface ShareRequest {
  scope: 'TENANT_INTERNAL' | 'GLOBAL'
}

// --- Test Case Types ---

export interface TestCaseDTO {
  id: number
  templateId: number
  name: string
  testData: string
  expectedResult: string
  compareMode: 'VARIABLE' | 'TEXT' | 'SNAPSHOT'
  createdAt: string
  updatedAt: string
}

export interface CreateTestCaseRequest {
  name: string
  testData: string
  expectedResult: string
  compareMode: 'VARIABLE' | 'TEXT' | 'SNAPSHOT'
}

export interface TestResultDTO {
  testCaseId: number
  testCaseName: string
  passed: boolean
  actualResult?: string
  diffDetails?: string
  executedAt: string
}

export interface TestReportDTO {
  templateId: number
  totalCases: number
  passedCases: number
  failedCases: number
  results: TestResultDTO[]
  executedAt: string
}

// --- Scheduled Task Types ---

export interface ScheduledTaskDTO {
  id: number
  templateId: number
  name: string
  cronExpression: string
  enabled: boolean
  dataSourceParams: string
  maxRetries: number
  lastExecution?: string
  nextExecution?: string
  createdAt: string
  updatedAt: string
}

export interface CreateScheduledTaskRequest {
  name: string
  cronExpression: string
  dataSourceParams?: string
  maxRetries?: number
}

export interface TaskExecutionDTO {
  id: number
  scheduledTaskId: number
  executionTime: string
  result: 'SUCCESS' | 'FAILED' | 'SKIPPED'
  documentId?: number
  errorMessage?: string
}

// --- Market API ---

export function searchMarketTemplates(query: MarketQuery) {
  return request.get<any, PageResult<MarketTemplateDTO>>('/market/templates', {
    params: {
      keyword: query.keyword || undefined,
      category: query.category || undefined,
      tag: query.tag || undefined,
      sort: query.sort || undefined,
      page: query.page,
      size: query.size,
    },
  })
}

export function copyFromMarket(marketTemplateId: number) {
  return request.post(`/market/templates/${marketTemplateId}/copy`)
}

export function shareToMarket(templateId: number, data: ShareRequest) {
  return request.post(`/templates/${templateId}/share`, data)
}

// --- Test Case API ---

export function getTestCases(templateId: number) {
  return request.get<any, TestCaseDTO[]>(`/templates/${templateId}/test-cases`)
}

export function createTestCase(templateId: number, data: CreateTestCaseRequest) {
  return request.post<any, TestCaseDTO>(`/templates/${templateId}/test-cases`, data)
}

export function updateTestCase(testCaseId: number, data: CreateTestCaseRequest) {
  return request.put<any, TestCaseDTO>(`/test-cases/${testCaseId}`, data)
}

export function deleteTestCase(testCaseId: number) {
  return request.delete(`/test-cases/${testCaseId}`)
}

export function runTestCase(testCaseId: number) {
  return request.post<any, TestResultDTO>(`/test-cases/${testCaseId}/run`)
}

export function runAllTestCases(templateId: number) {
  return request.post<any, TestReportDTO>(`/templates/${templateId}/test-cases/run-all`)
}

// --- Scheduled Task API ---

export function getScheduledTasks(templateId: number) {
  return request.get<any, ScheduledTaskDTO[]>(`/templates/${templateId}/scheduled-tasks`)
}

export function createScheduledTask(templateId: number, data: CreateScheduledTaskRequest) {
  return request.post<any, ScheduledTaskDTO>(`/templates/${templateId}/scheduled-tasks`, data)
}

export function updateScheduledTask(taskId: number, data: CreateScheduledTaskRequest) {
  return request.put<any, ScheduledTaskDTO>(`/scheduled-tasks/${taskId}`, data)
}

export function deleteScheduledTask(taskId: number) {
  return request.delete(`/scheduled-tasks/${taskId}`)
}

export function enableScheduledTask(taskId: number) {
  return request.put(`/scheduled-tasks/${taskId}/enable`)
}

export function disableScheduledTask(taskId: number) {
  return request.put(`/scheduled-tasks/${taskId}/disable`)
}

export function getTaskExecutions(taskId: number, page = 0, size = 20) {
  return request.get<any, PageResult<TaskExecutionDTO>>(`/scheduled-tasks/${taskId}/executions`, {
    params: { page, size },
  })
}

/** Export test cases as JSON string */
export function exportTestCases(templateId: number) {
  return request.get<any, string>(`/templates/${templateId}/test-cases/export`)
}

/** Import test cases from JSON string */
export function importTestCases(templateId: number, json: string) {
  return request.post<any, TestCaseDTO[]>(`/templates/${templateId}/test-cases/import`, json, {
    headers: { 'Content-Type': 'application/json' },
  })
}
