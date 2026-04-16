import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const mockGrantPermission = vi.fn()
const mockGetUsers = vi.fn()
const mockListTeams = vi.fn()

vi.mock('@/api/templates', () => ({
  getTemplate: vi.fn().mockResolvedValue({ id: 1, name: 'T', status: 'DRAFT', templateType: 'COMPOSITE', version: 1, tenantId: 1 }),
  getAvailableTransitions: vi.fn().mockResolvedValue([]),
  getTemplateVersions: vi.fn().mockResolvedValue([]),
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
  getTemplatePermissions: vi.fn().mockResolvedValue([]),
  grantPermission: (...args: any[]) => mockGrantPermission(...args),
  getUsers: (...args: any[]) => mockGetUsers(...args),
}))
vi.mock('@/api/teams', () => ({
  listTeams: (...args: any[]) => mockListTeams(...args),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
  onBeforeRouteLeave: vi.fn(),
}))

import GrantPermissionDialog from '@/views/template-workspace/components/GrantPermissionDialog.vue'
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

describe('GrantPermissionDialog', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockGrantPermission.mockReset()
    mockGetUsers.mockReset().mockResolvedValue({ content: [{ id: 10, username: 'Alice' }], totalElements: 1 })
    mockListTeams.mockReset().mockResolvedValue([{ id: 20, name: 'Team A' }])
  })

  it('loads user list when opened with USER grantee type', async () => {
    populateStore()
    mount(GrantPermissionDialog, {
      props: { visible: true, templateId: 1 },
    })
    await flushPromises()

    expect(mockGetUsers).toHaveBeenCalled()
  })

  it('loads team list when grantee type is switched to TEAM', async () => {
    populateStore()
    const wrapper = mount(GrantPermissionDialog, {
      props: { visible: true, templateId: 1 },
    })
    await flushPromises()

    // Switch to TEAM
    const vm = wrapper.vm as any
    vm.form.granteeType = 'TEAM'
    await flushPromises()

    expect(mockListTeams).toHaveBeenCalled()
  })

  it('calls grantPermission on submit with valid data', async () => {
    populateStore()
    mockGrantPermission.mockResolvedValue({ id: 1 })
    const wrapper = mount(GrantPermissionDialog, {
      props: { visible: true, templateId: 1 },
    })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.form.granteeId = 10
    vm.form.permissionType = 'VIEW'
    await vm.handleSubmit()
    await flushPromises()

    expect(mockGrantPermission).toHaveBeenCalledWith(1, {
      granteeId: 10,
      granteeType: 'USER',
      permissionType: 'VIEW',
    })
  })

  it('emits saved event on successful grant', async () => {
    populateStore()
    mockGrantPermission.mockResolvedValue({ id: 1 })
    const wrapper = mount(GrantPermissionDialog, {
      props: { visible: true, templateId: 1 },
    })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.form.granteeId = 10
    await vm.handleSubmit()
    await flushPromises()

    expect(wrapper.emitted('saved')).toBeTruthy()
  })

  it('does not call grantPermission when granteeId is null', async () => {
    populateStore()
    const wrapper = mount(GrantPermissionDialog, {
      props: { visible: true, templateId: 1 },
    })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.form.granteeId = null
    await vm.handleSubmit()
    await flushPromises()

    expect(mockGrantPermission).not.toHaveBeenCalled()
  })
})
