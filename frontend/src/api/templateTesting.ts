import request from './request'
import type { PageResult } from '@/types'

export type ComparisonType = 'VARIABLE_VALUE' | 'TEXT_CONTENT' | 'FILE_SNAPSHOT'

export interface TestCaseDTO {
  id: number
  templateId: number
  name: string
  testDataJson: string
  expectedResultJson: string | null
  comparisonType: ComparisonType
  createdAt: string
  updatedAt: string
  /** Latest run from list API; not included in export JSON. */
  lastRun?: TestResultDTO | null
}

export interface CreateTestCaseRequest {
  name: string
  testDataJson: string
  /** Use "{}" when no expectations are defined. */
  expectedResultJson?: string | null
  comparisonType: ComparisonType
}

export type TestStatus = 'PASSED' | 'FAILED'

export interface TestResultDTO {
  id: number
  testCaseId: number
  testCaseName?: string | null
  status: TestStatus
  actualResultJson?: string | null
  diffDetails?: string | null
  executedAt: string
  /** Set when the trial stored a temp sample Word/DOCX in storage. */
  sampleDocumentId?: number | null
  sampleDocumentDownloadUrl?: string | null
  /** Optional PDF sample stored alongside the Word trial artifact. */
  samplePdfDocumentId?: number | null
  samplePdfDocumentDownloadUrl?: string | null
}

export interface TestReportDTO {
  templateId: number
  totalCount: number
  passedCount: number
  failedCount: number
  results: TestResultDTO[]
  executedAt: string
}

export function isTestPassed(result: TestResultDTO): boolean {
  return result.status === 'PASSED'
}

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

export interface ListTestCasesQuery {
  page?: number
  size?: number
  /** Case-insensitive name contains filter */
  q?: string
}

export function getTestCases(templateId: number, opts?: ListTestCasesQuery) {
  return request.get<any, PageResult<TestCaseDTO>>(`/templates/${templateId}/test-cases`, {
    params: {
      page: opts?.page ?? 0,
      size: opts?.size ?? 20,
      q: opts?.q?.trim() || undefined,
    },
  })
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

/** Paged trial run history for a business scenario (test case). Newest first. */
export function getTestCaseResults(testCaseId: number, page = 0, size = 20) {
  return request.get<any, PageResult<TestResultDTO>>(`/test-cases/${testCaseId}/results`, {
    params: { page, size },
  })
}

export function runAllTestCases(templateId: number) {
  return request.post<any, TestReportDTO>(`/templates/${templateId}/test-cases/run-all`)
}

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
