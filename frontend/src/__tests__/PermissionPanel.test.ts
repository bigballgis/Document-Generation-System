import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const mockRevokePermission = vi.fn()

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
  revokePermission: (...args: any[]) => mockRevokePermission(...args),
  grantPermission: vi.fn(),
  getUsers: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
}))
vi.mock('@/api/teams', () => ({ listTeams: vi.fn().mockResolvedValue([]) }))

// Stub GrantPermissionDialog
vi.mock('@/views/template-workspace/components/GrantPermissionDialog.vue', () => ({
  default: {
    name: 'GrantPermissionDialog',
    template: '<div class="stub-grant-dialog" />',
    props: ['visible', 'templateId'],
    emits: ['update:visible', 'saved'],
  },
}))

const mockConfirm = vi.fn()
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<any>()
  return {
    ...actual,
    ElMessageBox: { ...actual.ElMessageBox, confirm: (...args: any[]) => mockConfirm(...args) },
  }
})

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
  onBeforeRouteLeave: vi.fn(),
}))

import PermissionPanel from '@/views/template-workspace/components/PermissionPanel.vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'

const samplePermissions = [
  { id: 1, templateId: 1, granteeId: 10, granteeName: 'Alice', granteeType: 'USER', permissionType: 'VIEW', createdAt: '2024-01-01' },
  { id: 2, templateId: 1, granteeId: 20, granteeName: 'Team A', granteeType: 'TEAM', permissionType: 'EDIT', createdAt: '2024-02-01' },
]

function populateStore(permissions: any[] = samplePermissions) {
  const store = useTemplateWorkspaceStore()
  store.templateId = 1
  store.template = {
    id: 1, name: 'Test', status: 'DRAFT', templateType: 'COMPOSITE',
    version: 1, description: '', tenantId: 1, createdAt: '', updatedAt: '',
    categoryId: null, tags: [], outputFormat: 'DOCX', reviewRequired: false,
  }
  store.permissions = permissions as any
  store.loading = false
  store.criticalError = null
  return store
}

describe('PermissionPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockRevokePermission.mockReset()
    mockConfirm.mockReset()
  })

  it('shows empty state when no permissions', async () => {
    populateStore([])
    const wrapper = mount(PermissionPanel)
    await flushPromises()

    expect(wrapper.find('.el-empty').exists()).toBe(true)
  })

  it('renders permission table when permissions exist', async () => {
    populateStore()
    const wrapper = mount(PermissionPanel)
    await flushPromises()

    expect(wrapper.find('.el-table').exists()).toBe(true)
  })

  it('renders Grant Permission button', async () => {
    populateStore()
    const wrapper = mount(PermissionPanel)
    await flushPromises()

    const grantBtn = wrapper.find('.toolbar .el-button')
    expect(grantBtn.exists()).toBe(true)
  })

  it('calls revokePermission after confirmation', async () => {
    populateStore()
    mockConfirm.mockResolvedValue('confirm')
    mockRevokePermission.mockResolvedValue(undefined)
    const wrapper = mount(PermissionPanel)
    await flushPromises()

    const revokeBtns = wrapper.findAll('.el-table .el-button--danger')
    expect(revokeBtns.length).toBeGreaterThanOrEqual(1)

    await revokeBtns[0].trigger('click')
    await flushPromises()

    expect(mockConfirm).toHaveBeenCalledTimes(1)
    expect(mockRevokePermission).toHaveBeenCalledWith(1, 1)
  })

  it('does not call revokePermission when user cancels', async () => {
    populateStore()
    mockConfirm.mockRejectedValue('cancel')
    const wrapper = mount(PermissionPanel)
    await flushPromises()

    const revokeBtns = wrapper.findAll('.el-table .el-button--danger')
    await revokeBtns[0].trigger('click')
    await flushPromises()

    expect(mockRevokePermission).not.toHaveBeenCalled()
  })
})
