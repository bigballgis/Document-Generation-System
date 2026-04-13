import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('@/api/rate-limits', () => ({
  getRateLimits: vi.fn(),
  getUsageStats: vi.fn(),
}))

import RateLimitPanel from '@/views/admin/RateLimitPanel.vue'
import { getRateLimits, getUsageStats } from '@/api/rate-limits'

const sampleRateLimits = [
  {
    apiKeyId: 1,
    apiKeyName: 'Production Key',
    limitPerSecond: 10,
    limitPerMinute: 100,
    limitPerHour: 1000,
    remainingPerSecond: 8,
    remainingPerMinute: 90,
    remainingPerHour: 950,
  },
]

const sampleUsageStats = {
  tenantId: 1,
  monthlyQuota: 10000,
  currentMonthUsage: 2500,
  remainingQuota: 7500,
  resetAt: '2024-02-01T00:00:00Z',
}

describe('RateLimitPanel', () => {
  beforeEach(() => {
    vi.mocked(getRateLimits).mockReset()
    vi.mocked(getUsageStats).mockReset()
    vi.mocked(getRateLimits).mockResolvedValue(sampleRateLimits as any)
    vi.mocked(getUsageStats).mockResolvedValue(sampleUsageStats as any)
  })

  it('renders rate limit table with data', async () => {
    const wrapper = mount(RateLimitPanel)
    await flushPromises()

    expect(getRateLimits).toHaveBeenCalled()
    const text = wrapper.text()
    expect(text).toContain('Production Key')
  })

  it('renders usage stats card with data', async () => {
    const wrapper = mount(RateLimitPanel)
    await flushPromises()

    expect(getUsageStats).toHaveBeenCalled()
    const text = wrapper.text()
    expect(text).toContain('10000')
    expect(text).toContain('2500')
    expect(text).toContain('7500')
  })

  it('reloads data when Refresh button is clicked', async () => {
    const wrapper = mount(RateLimitPanel)
    await flushPromises()

    vi.mocked(getRateLimits).mockClear()
    vi.mocked(getUsageStats).mockClear()
    vi.mocked(getRateLimits).mockResolvedValue(sampleRateLimits as any)
    vi.mocked(getUsageStats).mockResolvedValue(sampleUsageStats as any)

    // Find the Refresh button
    const refreshBtn = wrapper.findAll('.el-button').find(b => b.text().includes('Refresh'))
    expect(refreshBtn).toBeDefined()
    await refreshBtn!.trigger('click')
    await flushPromises()

    expect(getRateLimits).toHaveBeenCalled()
    expect(getUsageStats).toHaveBeenCalled()
  })

  it('shows el-empty when no rate limits', async () => {
    vi.mocked(getRateLimits).mockResolvedValue([])
    vi.mocked(getUsageStats).mockResolvedValue(null as any)

    const wrapper = mount(RateLimitPanel)
    await flushPromises()

    expect(wrapper.find('.el-empty').exists()).toBe(true)
  })
})
