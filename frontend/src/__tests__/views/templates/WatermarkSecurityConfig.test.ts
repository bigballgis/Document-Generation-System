import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('@/api/templates', () => ({
  getTemplate: vi.fn(),
  updateTemplateRenderConfig: vi.fn(),
  clearTemplateRenderConfig: vi.fn(),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      error: vi.fn(),
    },
  }
})

import WatermarkSecurityConfig from '@/views/templates/components/WatermarkSecurityConfig.vue'
import { getTemplate, clearTemplateRenderConfig } from '@/api/templates'

function minimalTemplateDto(
  id: number,
  templateType: 'SINGLE' | 'COMPOSITE',
  renderConfig: string | null = null,
) {
  return {
    id,
    name: 'Test',
    description: '',
    status: 'DRAFT' as const,
    categoryId: null,
    tags: [],
    version: 1,
    outputFormat: 'WORD',
    reviewRequired: false,
    tenantId: 1,
    templateType,
    renderConfig,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  }
}

describe('WatermarkSecurityConfig', () => {
  beforeEach(() => {
    vi.mocked(getTemplate).mockReset()
    vi.mocked(clearTemplateRenderConfig).mockReset()
    vi.mocked(getTemplate).mockResolvedValue(minimalTemplateDto(1, 'SINGLE', null) as any)
    vi.mocked(clearTemplateRenderConfig).mockResolvedValue(minimalTemplateDto(1, 'SINGLE', null) as any)
  })

  it('disables Save for SINGLE template after load', async () => {
    vi.mocked(getTemplate).mockResolvedValueOnce(minimalTemplateDto(9, 'SINGLE', null) as any)

    const wrapper = mount(WatermarkSecurityConfig, { props: { templateId: 9 } })
    await flushPromises()

    const actionButtons = wrapper.find('.watermark-actions').findAll('button')
    expect(actionButtons.length).toBeGreaterThanOrEqual(2)
    expect(actionButtons[0].attributes('disabled')).toBeDefined()
  })

  it('does not disable Save for COMPOSITE template after load', async () => {
    vi.mocked(getTemplate).mockResolvedValueOnce(minimalTemplateDto(9, 'COMPOSITE', null) as any)

    const wrapper = mount(WatermarkSecurityConfig, { props: { templateId: 9 } })
    await flushPromises()

    const actionButtons = wrapper.find('.watermark-actions').findAll('button')
    expect(actionButtons[0].attributes('disabled')).toBeUndefined()
  })

  it('allows Clear for SINGLE and calls clearTemplateRenderConfig', async () => {
    vi.mocked(getTemplate).mockResolvedValueOnce(minimalTemplateDto(9, 'SINGLE', null) as any)

    const wrapper = mount(WatermarkSecurityConfig, { props: { templateId: 9 } })
    await flushPromises()

    const actionButtons = wrapper.find('.watermark-actions').findAll('button')
    await actionButtons[1].trigger('click')
    await flushPromises()

    expect(clearTemplateRenderConfig).toHaveBeenCalledWith(9)
  })
})
