import request from './request'
import type { PageResult } from '@/types'

// ---- Tenant Types ----
export interface TenantDTO {
  id: number
  name: string
  enabled: boolean
  maxTemplates: number
  maxApiCallsPerMonth: number
  maxStorageGb: number
  createdAt: string
  updatedAt: string
}

export interface CreateTenantRequest {
  name: string
  maxTemplates: number
  maxApiCallsPerMonth: number
  maxStorageGb: number
}

export interface UpdateTenantRequest extends CreateTenantRequest {}

export interface TenantUsageDTO {
  tenantId: number
  usedTemplates: number
  usedApiCalls: number
  usedStorageGb: number
  maxTemplates: number
  maxApiCallsPerMonth: number
  maxStorageGb: number
}

// ---- User Types ----
export interface UserDTO {
  id: number
  username: string
  email: string
  role: string
  tenantId: number
  tenantName?: string
  teamId?: number
  teamName?: string
  lastLogin?: string
  status: string
  createdAt: string
}

export interface UpdateUserRequest {
  role: string
  teamId?: number | null
}

// ---- Permission Types ----
export interface PermissionDTO {
  id: number
  templateId: number
  templateName?: string
  granteeId: number
  granteeName?: string
  granteeType: 'USER' | 'TEAM'
  permissionType: 'VIEW' | 'EDIT' | 'DELETE' | 'CALL_API'
  createdAt: string
}

export interface GrantPermissionRequest {
  granteeId: number
  granteeType: 'USER' | 'TEAM'
  permissionType: 'VIEW' | 'EDIT' | 'DELETE' | 'CALL_API'
}

// ---- API Key Types ----
export interface ApiKeyDTO {
  id: number
  name: string
  keyPrefix?: string
  enabled: boolean
  expiresAt?: string
  rateLimitPerSecond?: number
  rateLimitPerMinute?: number
  rateLimitPerHour?: number
  createdAt: string
}

export interface CreateApiKeyRequest {
  name: string
  expiresAt?: string
  rateLimitPerSecond?: number
  rateLimitPerMinute?: number
  rateLimitPerHour?: number
}

export interface CreateApiKeyResponse {
  id: number
  name: string
  key: string
  expiresAt?: string
}

// ---- Review Types ----
export interface ReviewDTO {
  id: number
  templateId: number
  templateName?: string
  reviewerId: number
  reviewerName?: string
  status: 'PENDING' | 'APPROVED' | 'CONDITIONAL_APPROVED' | 'REJECTED'
  level: 'INITIAL' | 'FINAL'
  comment?: string
  reason?: string
  suggestions?: string[]
  createdAt: string
  updatedAt: string
}

// ---- Tenant API ----
export function getTenants(params: { page: number; size: number; keyword?: string }) {
  return request.get<any, PageResult<TenantDTO>>('/tenants', { params })
}

export function createTenant(data: CreateTenantRequest) {
  return request.post<any, TenantDTO>('/tenants', data)
}

export function updateTenant(id: number, data: UpdateTenantRequest) {
  return request.put<any, TenantDTO>(`/tenants/${id}`, data)
}

export function enableTenant(id: number) {
  return request.put(`/tenants/${id}/enable`)
}

export function disableTenant(id: number) {
  return request.put(`/tenants/${id}/disable`)
}

export function getTenantUsage(id: number) {
  return request.get<any, TenantUsageDTO>(`/tenants/${id}/usage`)
}

// ---- User API ----
export function getUsers(params: { page: number; size: number; keyword?: string }) {
  return request.get<any, PageResult<UserDTO>>('/users', { params })
}

export function updateUser(id: number, data: UpdateUserRequest) {
  return request.put<any, UserDTO>(`/users/${id}`, data)
}

// ---- Permission API ----
export function getTemplatePermissions(templateId: number) {
  return request.get<any, PermissionDTO[]>(`/templates/${templateId}/permissions`)
}

export function grantPermission(templateId: number, data: GrantPermissionRequest) {
  return request.post<any, PermissionDTO>(`/templates/${templateId}/permissions`, data)
}

export function revokePermission(templateId: number, permId: number) {
  return request.delete(`/templates/${templateId}/permissions/${permId}`)
}

// ---- API Key API ----
export function getApiKeys(params: { page: number; size: number }) {
  return request.get<any, PageResult<ApiKeyDTO>>('/api-keys', { params })
}

export function createApiKey(data: CreateApiKeyRequest) {
  return request.post<any, CreateApiKeyResponse>('/api-keys', data)
}

export function deleteApiKey(id: number) {
  return request.delete(`/api-keys/${id}`)
}

export function enableApiKey(id: number) {
  return request.put(`/api-keys/${id}/enable`)
}

export function disableApiKey(id: number) {
  return request.put(`/api-keys/${id}/disable`)
}

// ---- Review API ----
export function getReviews(params: { page: number; size: number; status?: string }) {
  return request.get<any, PageResult<ReviewDTO>>('/reviews', { params })
}

export function approveReview(id: number, comment: string) {
  return request.put(`/reviews/${id}/approve`, { comment })
}

export function rejectReview(id: number, reason: string) {
  return request.put(`/reviews/${id}/reject`, { comment: reason })
}

/** Submit a template for review (create review records) */
export function submitForReview(templateId: number, data: { reviewerIds: number[]; reviewLevel?: number }) {
  return request.post(`/templates/${templateId}/reviews`, data)
}

/** Get paginated reviews for a specific template */
export function getTemplateReviews(templateId: number, params: { page: number; size: number }) {
  return request.get<any, PageResult<ReviewDTO>>(`/templates/${templateId}/reviews`, { params })
}

/** Conditionally approve a review with suggestions */
export function conditionalApproveReview(reviewId: number, data: { comment?: string; suggestions: string[] }) {
  return request.put<any, ReviewDTO>(`/reviews/${reviewId}/conditional-approve`, data)
}

/** Get OnlyOffice review editor URL for a template */
export function getReviewEditorUrl(templateId: number) {
  return request.get<any, string>(`/templates/${templateId}/reviews/editor-url`)
}

// ---- User Detail & Delete ----
export function getUser(id: number) {
  return request.get<any, UserDTO>(`/users/${id}`)
}

export function deleteUser(id: number) {
  return request.delete(`/users/${id}`)
}

// ---- Tenant Detail ----
export function getTenant(id: number) {
  return request.get<any, TenantDTO>(`/tenants/${id}`)
}
