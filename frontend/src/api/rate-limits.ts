import request from './request'

export interface RateLimitStatusDTO {
  apiKeyId: number
  apiKeyName: string
  limitPerSecond: number
  limitPerMinute: number
  limitPerHour: number
  remainingPerSecond: number
  remainingPerMinute: number
  remainingPerHour: number
}

export interface UsageStatsDTO {
  tenantId: number
  monthlyQuota: number
  currentMonthUsage: number
  remainingQuota: number
  resetAt: string
}

export function getRateLimits() {
  return request.get<any, RateLimitStatusDTO[]>('/rate-limits')
}

export function getUsageStats() {
  return request.get<any, UsageStatsDTO>('/usage-stats')
}
