import request from './request'
import type { PageResult } from '@/types'
import type {
  Segment,
  SegmentQuery,
  CreateSegmentRequest,
  UpdateSegmentRequest,
  SegmentVersion,
  SegmentVariable,
  SegmentPermission,
  GrantSegmentPermissionRequest,
  LockInfo,
  SegmentTestData,
  CreateSegmentTestDataRequest,
  SegmentTestResult,
  DuplicateAnalysis,
  SegmentTemplate,
  CreateSegmentTemplateRequest,
} from '@/types/segment'
import type { VersionDiffResult } from './templates'

// ── Segment CRUD ──

export function getSegments(query: SegmentQuery) {
  return request.get<any, PageResult<Segment>>('/segments', {
    params: {
      keyword: query.keyword || undefined,
      categoryId: query.categoryId || undefined,
      tagId: query.tagId || undefined,
      segmentType: query.segmentType || undefined,
      isComponent: query.isComponent ?? undefined,
      page: (query.page || 1) - 1,
      size: query.size,
    },
  })
}

export function getSegment(id: number) {
  return request.get<any, Segment>(`/segments/${id}`)
}

export function createSegment(data: CreateSegmentRequest, file?: File) {
  const formData = new FormData()
  if (file) {
    formData.append('file', file)
  }
  formData.append('request', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  return request.post<any, Segment>('/segments', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function updateSegment(id: number, data: UpdateSegmentRequest, file?: File) {
  const formData = new FormData()
  if (file) formData.append('file', file)
  formData.append('request', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  return request.put<any, Segment>(`/segments/${id}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deleteSegment(id: number) {
  return request.delete(`/segments/${id}`)
}

export function cloneSegment(id: number) {
  return request.post<any, Segment>(`/segments/${id}/clone`)
}

export function promoteToComponent(id: number) {
  return request.post<any, Segment>(`/segments/${id}/promote`)
}

export function demoteFromComponent(id: number) {
  return request.post<any, Segment>(`/segments/${id}/demote`)
}

// ── Versions ──

export function getSegmentVersions(id: number) {
  return request.get<any, SegmentVersion[]>(`/segments/${id}/versions`)
}

export function rollbackSegmentVersion(id: number, versionId: number) {
  return request.post<any, Segment>(`/segments/${id}/rollback/${versionId}`)
}

export function getSegmentVersionDiff(id: number, versionA: number, versionB: number) {
  return request.get<any, VersionDiffResult>(`/segments/${id}/versions/diff`, {
    params: { versionA, versionB },
  })
}

// ── Variables ──

export function getSegmentVariables(id: number) {
  return request.get<any, SegmentVariable[]>(`/segments/${id}/variables`)
}

// ── Permissions ──

export function getSegmentPermissions(id: number) {
  return request.get<any, SegmentPermission[]>(`/segments/${id}/permissions`)
}

export function grantSegmentPermission(id: number, data: GrantSegmentPermissionRequest) {
  return request.post<any, SegmentPermission>(`/segments/${id}/permissions`, data)
}

export function revokeSegmentPermission(id: number, permissionId: number) {
  return request.delete(`/segments/${id}/permissions/${permissionId}`)
}

// ── Edit Lock ──

export function acquireSegmentLock(id: number) {
  return request.post<any, LockInfo>(`/segments/${id}/lock`)
}

export function releaseSegmentLock(id: number) {
  return request.delete(`/segments/${id}/lock`)
}

export function renewSegmentLock(id: number) {
  return request.post<any, LockInfo>(`/segments/${id}/lock/renew`)
}

export function getSegmentLockInfo(id: number) {
  return request.get<any, LockInfo | null>(`/segments/${id}/lock`)
}

// ── Test Data ──

export function getSegmentTestData(id: number) {
  return request.get<any, SegmentTestData[]>(`/segments/${id}/test-data`)
}

export function createSegmentTestData(id: number, data: CreateSegmentTestDataRequest) {
  return request.post<any, SegmentTestData>(`/segments/${id}/test-data`, data)
}

export function deleteSegmentTestData(id: number, testDataId: number) {
  return request.delete(`/segments/${id}/test-data/${testDataId}`)
}

export function runSegmentTest(id: number, testDataId: number) {
  return request.post<any, SegmentTestResult>(`/segments/${id}/test-data/${testDataId}/run`)
}

// ── Favorites ──

export function addSegmentFavorite(id: number) {
  return request.post(`/segments/${id}/favorite`)
}

export function removeSegmentFavorite(id: number) {
  return request.delete(`/segments/${id}/favorite`)
}

// ── Duplicate Analysis ──

export function analyzeDuplicates() {
  return request.get<any, DuplicateAnalysis>('/segments/duplicate-analysis')
}

// ── Segment Templates (preset / custom) ──

export function getSegmentTemplates() {
  return request.get<any, SegmentTemplate[]>('/segments/templates')
}

export function saveAsSegmentTemplate(data: CreateSegmentTemplateRequest) {
  return request.post<any, SegmentTemplate>('/segments/templates', data)
}
