import request from './request'

// --- Types ---

export interface SystemOverviewDTO {
  totalTemplates: number
  activeTemplates: number
  totalApiCalls: number
  totalDocuments: number
}

export interface ApiCallMetricDTO {
  timestamp: string
  callCount: number
  avgResponseTimeMs: number
}

export interface DataSourceHealthDTO {
  id: number
  name: string
  type: string
  reachable: boolean
  avgResponseTimeMs: number
  lastError: string | null
}

export interface JvmMemory {
  usedBytes: number
  maxBytes: number
  usagePercent: number
}

export interface DbPool {
  activeConnections: number
  idleConnections: number
  totalConnections: number
  maxConnections: number
  usagePercent: number
}

export interface RedisMemory {
  usedMemoryBytes: number
  maxMemoryBytes: number
  usagePercent: number
}

export interface SystemResourceDTO {
  jvmMemory: JvmMemory
  dbPool: DbPool
  redisMemory: RedisMemory
}

// --- API Functions ---

export function getOverview() {
  return request.get<any, SystemOverviewDTO>('/dashboard/overview')
}

export function getApiMetrics(minutes: number = 60) {
  return request.get<any, ApiCallMetricDTO[]>('/dashboard/api-metrics', {
    params: { minutes },
  })
}

export function getDataSourceHealth() {
  return request.get<any, DataSourceHealthDTO[]>('/dashboard/data-source-health')
}

export function getSystemResources() {
  return request.get<any, SystemResourceDTO>('/dashboard/system-resources')
}

// --- Segment & Component Stats ---

export interface SegmentStatsDTO {
  segmentCount: number
  componentCount: number
  compositeTemplateCount: number
}

export interface ComponentRankingDTO {
  segmentId: number
  segmentName: string
  referenceCount: number
}

export function getSegmentStats() {
  return request.get<any, SegmentStatsDTO>('/dashboard/segment-stats')
}

export function getComponentRanking() {
  return request.get<any, ComponentRankingDTO[]>('/dashboard/component-ranking')
}
