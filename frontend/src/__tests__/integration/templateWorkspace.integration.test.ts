import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'

vi.mock('@/api/templates', () => ({
  getTemplate: vi.fn().mockResolvedValue({ id: 1, name: 'Test', status: 'DRAFT', templateType: 'COMPOSITE', version: 1 }),
  getAvailableTransitions: vi.fn().mockResolvedValue(['PENDING_REVIEW']),
}))

vi.mock('@/api/composite-templates', () => ({
  getAssemblyConfig: vi.fn().mockResolvedValue({ segments: [] }),
  getCompositeCoverage: vi.fn().mockResolvedValue({ overallCoveragePercent: 0, segmentCoverages: [] }),
}))

vi.mock('@/api/parameters', () => ({
  getParameters: vi.fn().mockResolvedValue([]),
}))

describe('Template Workspace Integration', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('loads all APIs in parallel on initWorkspace', async () => {
    const store = useTemplateWorkspaceStore()
    await store.initWorkspace(1)

    expect(store.loading).toBe(false)
    expect(store.criticalError).toBeNull()
    expect(store.template).toBeTruthy()
    expect(store.template?.name).toBe('Test')
    expect(store.assemblyConfig).toBeTruthy()
  })

  it('sets criticalError when template API fails', async () => {
    const { getTemplate } = await import('@/api/templates')
    vi.mocked(getTemplate).mockRejectedValueOnce(new Error('Not found'))

    const store = useTemplateWorkspaceStore()
    await store.initWorkspace(999)

    expect(store.criticalError).toBe('Not found')
    expect(store.loading).toBe(false)
  })

  it('sets warnings when non-critical API fails', async () => {
    const { getParameters } = await import('@/api/parameters')
    vi.mocked(getParameters).mockRejectedValueOnce(new Error('Params failed'))

    const store = useTemplateWorkspaceStore()
    await store.initWorkspace(1)

    expect(store.criticalError).toBeNull()
    expect(store.warnings.parameters).toBe('Params failed')
    expect(store.template).toBeTruthy()
  })

  it('resets state on $reset', async () => {
    const store = useTemplateWorkspaceStore()
    await store.initWorkspace(1)
    expect(store.template).toBeTruthy()

    store.$reset()
    expect(store.template).toBeNull()
    expect(store.templateId).toBe(0)
    expect(store.loading).toBe(false)
  })
})
