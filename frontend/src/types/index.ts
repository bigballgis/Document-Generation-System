// Common API response types
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

// Auth types
export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface TokenPair {
  accessToken: string
  refreshToken: string
}

// Template types
export interface Template {
  id: number
  name: string
  description: string
  status: TemplateStatus
  tenantId: number
  createdAt: string
  updatedAt: string
}

export type TemplateStatus = 'DRAFT' | 'PENDING_REVIEW' | 'REVIEWED' | 'ACTIVE' | 'ARCHIVED'

// User roles
export type UserRole = 'SUPER_ADMIN' | 'TENANT_ADMIN' | 'TEAM_ADMIN' | 'USER'
