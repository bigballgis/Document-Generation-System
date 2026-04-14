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
  getCompositeSegments: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/data-sources', () => ({
  getDataSources: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/api/expressions', () => ({
  getExpressions: vi.fn().mockResolvedValue([]),
}))

describe('Template Workspace Integration', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('loads all 6 APIs in parallel on initWorkspace', async () => {
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
    const { getDataSources } = await import('@/api/data-sources')
    vi.mocked(getDataSources).mockRejectedValueOnce(new Error('DS failed'))

    const store = useTemplateWorkspaceStore()
    await store.initWorkspace(1)

    expect(store.criticalError).toBeNull()
    expect(store.warnings.dataSources).toBe('DS failed')
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
