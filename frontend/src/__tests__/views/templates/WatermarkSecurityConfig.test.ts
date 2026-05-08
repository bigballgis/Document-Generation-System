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

import { ElSwitch, ElMessage } from 'element-plus'
import WatermarkSecurityConfig from '@/views/templates/components/WatermarkSecurityConfig.vue'
import { getTemplate, clearTemplateRenderConfig, updateTemplateRenderConfig } from '@/api/templates'

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
    vi.mocked(updateTemplateRenderConfig).mockReset()
    vi.mocked(getTemplate).mockResolvedValue(minimalTemplateDto(1, 'SINGLE', null) as any)
    vi.mocked(clearTemplateRenderConfig).mockResolvedValue(minimalTemplateDto(1, 'SINGLE', null) as any)
    vi.mocked(updateTemplateRenderConfig).mockResolvedValue(minimalTemplateDto(1, 'COMPOSITE', '{}') as any)
    vi.mocked(ElMessage.success).mockClear()
    vi.mocked(ElMessage.error).mockClear()
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
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('calls updateTemplateRenderConfig when COMPOSITE saves text watermark', async () => {
    vi.mocked(getTemplate).mockResolvedValue(minimalTemplateDto(11, 'COMPOSITE', null) as any)

    const wrapper = mount(WatermarkSecurityConfig, { props: { templateId: 11 } })
    await flushPromises()

    const switches = wrapper.findAllComponents(ElSwitch)
    expect(switches.length).toBeGreaterThanOrEqual(1)
    await switches[0].vm.$emit('update:modelValue', true)
    await flushPromises()

    const ta = wrapper.find('textarea')
    expect(ta.exists()).toBe(true)
    await ta.setValue('SECRET')

    const actionButtons = wrapper.find('.watermark-actions').findAll('button')
    await actionButtons[0].trigger('click')
    await flushPromises()

    expect(updateTemplateRenderConfig).toHaveBeenCalledWith(
      11,
      expect.objectContaining({
        schemaVersion: 1,
        textWatermark: expect.objectContaining({
          text: 'SECRET',
          fontSize: 36,
        }),
      }),
    )
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('calls clearTemplateRenderConfig when COMPOSITE saves with no watermarks enabled', async () => {
    vi.mocked(getTemplate).mockResolvedValue(minimalTemplateDto(22, 'COMPOSITE', null) as any)

    const wrapper = mount(WatermarkSecurityConfig, { props: { templateId: 22 } })
    await flushPromises()

    const actionButtons = wrapper.find('.watermark-actions').findAll('button')
    await actionButtons[0].trigger('click')
    await flushPromises()

    expect(clearTemplateRenderConfig).toHaveBeenCalledWith(22)
    expect(updateTemplateRenderConfig).not.toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('does not call updateTemplateRenderConfig when SINGLE Save is triggered while disabled', async () => {
    vi.mocked(getTemplate).mockResolvedValueOnce(minimalTemplateDto(14, 'SINGLE', null) as any)

    const wrapper = mount(WatermarkSecurityConfig, { props: { templateId: 14 } })
    await flushPromises()

    const actionButtons = wrapper.find('.watermark-actions').findAll('button')
    await actionButtons[0].trigger('click')
    await flushPromises()

    expect(updateTemplateRenderConfig).not.toHaveBeenCalled()
    expect(clearTemplateRenderConfig).not.toHaveBeenCalled()
    expect(ElMessage.success).not.toHaveBeenCalled()
    expect(ElMessage.error).not.toHaveBeenCalled()
  })
})
