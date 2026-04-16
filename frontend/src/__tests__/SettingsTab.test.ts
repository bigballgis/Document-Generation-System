import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const mockRefreshVersions = vi.fn().mockResolvedValue(undefined)
const mockRefreshPermissions = vi.fn().mockResolvedValue(undefined)

vi.mock('@/api/templates', () => ({
  getTemplate: vi.fn().mockResolvedValue({ id: 1, name: 'T', status: 'DRAFT', templateType: 'COMPOSITE', version: 1, tenantId: 1 }),
  getAvailableTransitions: vi.fn().mockResolvedValue([]),
  getTemplateVersions: (...args: any[]) => mockRefreshVersions(...args),
  rollbackVersion: vi.fn(),
  getVersionDiff: vi.fn(),
}))

vi.mock('@/api/composite-templates', () => ({
  getAssemblyConfig: vi.fn().mockResolvedValue({ segments: [] }),
  getCompositeCoverage: vi.fn().mockResolvedValue({ overallCoveragePercent: 0, segmentCoverages: [] }),
  getCompositeSegments: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/parameters', () => ({ getParameters: vi.fn().mockResolvedValue([]) }))

vi.mock('@/api/market', () => ({ getTestCases: vi.fn().mockResolvedValue([]) }))
vi.mock('@/api/admin', () => ({
  getTemplateReviews: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
  getTemplatePermissions: (...args: any[]) => mockRefreshPermissions(...args),
  revokePermission: vi.fn(),
  grantPermission: vi.fn(),
  getUsers: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
}))
vi.mock('@/api/teams', () => ({ listTeams: vi.fn().mockResolvedValue([]) }))

// Stub child components
vi.mock('@/views/template-workspace/components/VersionHistoryPanel.vue', () => ({
  default: { name: 'VersionHistoryPanel', template: '<div class="stub-version-history">VersionHistoryPanel</div>' },
}))
vi.mock('@/views/template-workspace/components/VersionDiffPanel.vue', () => ({
  default: { name: 'VersionDiffPanel', template: '<div class="stub-version-diff">VersionDiffPanel</div>' },
}))
vi.mock('@/views/template-workspace/components/PermissionPanel.vue', () => ({
  default: { name: 'PermissionPanel', template: '<div class="stub-permission-panel">PermissionPanel</div>' },
}))
vi.mock('@/views/templates/components/WebhookPanel.vue', () => ({
  default: { name: 'WebhookPanel', template: '<div class="stub-webhook-panel">WebhookPanel</div>', props: ['templateId'] },
}))
vi.mock('@/views/templates/components/WatermarkSecurityConfig.vue', () => ({
  default: { name: 'WatermarkSecurityConfig', template: '<div class="stub-watermark">WatermarkSecurityConfig</div>', props: ['templateId'] },
}))
vi.mock('@/views/templates/components/ScheduledTaskManagement.vue', () => ({
  default: { name: 'ScheduledTaskManagement', template: '<div class="stub-scheduled-task">ScheduledTaskManagement</div>', props: ['templateId'] },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
  onBeforeRouteLeave: vi.fn(),
}))

import SettingsTab from '@/views/template-workspace/components/SettingsTab.vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'

function populateStore() {
  const store = useTemplateWorkspaceStore()
  store.templateId = 1
  store.template = {
    id: 1, name: 'Test', status: 'DRAFT', templateType: 'COMPOSITE',
    version: 1, description: '', tenantId: 1, createdAt: '', updatedAt: '',
    categoryId: null, tags: [], outputFormat: 'DOCX', reviewRequired: false,
  }
  store.loading = false
  store.criticalError = null
  return store
}

describe('SettingsTab', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockRefreshVersions.mockReset().mockResolvedValue([])
    mockRefreshPermissions.mockReset().mockResolvedValue([])
  })

  it('renders 6 el-collapse-item panels', async () => {
    populateStore()
    const wrapper = mount(SettingsTab)
    await flushPromises()

    const collapseItems = wrapper.findAll('.el-collapse-item')
    expect(collapseItems.length).toBe(6)
  })

  it('calls refreshVersions and refreshPermissions on mount', async () => {
    populateStore()
    mount(SettingsTab)
    await flushPromises()

    expect(mockRefreshVersions).toHaveBeenCalled()
    expect(mockRefreshPermissions).toHaveBeenCalled()
  })

  it('renders WebhookPanel stub', async () => {
    populateStore()
    const wrapper = mount(SettingsTab)
    await flushPromises()

    expect(wrapper.find('.stub-webhook-panel').exists()).toBe(true)
  })

  it('renders WatermarkSecurityConfig stub', async () => {
    populateStore()
    const wrapper = mount(SettingsTab)
    await flushPromises()

    expect(wrapper.find('.stub-watermark').exists()).toBe(true)
  })

  it('renders ScheduledTaskManagement stub', async () => {
    populateStore()
    const wrapper = mount(SettingsTab)
    await flushPromises()

    expect(wrapper.find('.stub-scheduled-task').exists()).toBe(true)
  })
})
