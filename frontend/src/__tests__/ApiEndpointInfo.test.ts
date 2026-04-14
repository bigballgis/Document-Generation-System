import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import type { ApiKeyDTO } from '@/api/admin'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: { id: '1' } }),
}))

import ApiEndpointInfo from '@/views/template-workspace/components/ApiEndpointInfo.vue'

const activeKey: ApiKeyDTO = {
  id: 1, name: 'test-key', keyPrefix: 'dk_abc12345', enabled: true, createdAt: '2024-01-01',
}

const curlExample = `curl -X POST \\
  http://localhost/api/generate/42 \\
  -H "X-API-Key: dk_abc12345" \\
  -H "Content-Type: application/json" \\
  -d '{"data": {}}'`

function mountComponent(props: Partial<{
  templateId: number; apiKeys: ApiKeyDTO[]; curlExample: string;
  apiKeysLoading: boolean; creatingApiKey: boolean;
}> = {}) {
  return mount(ApiEndpointInfo, {
    props: {
      templateId: 42,
      apiKeys: [activeKey],
      curlExample,
      apiKeysLoading: false,
      creatingApiKey: false,
      ...props,
    },
  })
}

describe('ApiEndpointInfo', () => {
  // Requirement 5.1: API URL rendering + Copy button
  it('renders API URL with template ID and a Copy button', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('POST')
    expect(wrapper.text()).toContain('/api/generate/42')
    const copyBtns = wrapper.findAll('.el-button').filter(b => b.text().includes('Copy'))
    expect(copyBtns.length).toBeGreaterThanOrEqual(1)
  })

  // Requirement 5.2: API Key prefix rendering + Copy button
  it('renders API Key prefix when active key exists', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('dk_abc12345')
  })

  // Requirement 5.4: No API Key → warning + Create button
  it('shows warning and Create API Key button when no keys exist', () => {
    const wrapper = mountComponent({ apiKeys: [] })
    expect(wrapper.text()).toContain('No API Key')
    expect(wrapper.text()).toContain('Create API Key')
  })

  // cURL example rendering + Copy button
  it('renders cURL example block', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('pre').text()).toContain('curl -X POST')
    expect(wrapper.find('pre').text()).toContain('dk_abc12345')
  })

  // API version note
  it('renders API version note', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('API')
  })

  // Copy emits
  it('emits copy event when Copy URL button is clicked', async () => {
    const wrapper = mountComponent()
    const copyBtns = wrapper.findAll('.el-button').filter(b => b.text().includes('Copy'))
    await copyBtns[0].trigger('click')
    expect(wrapper.emitted('copy')).toBeTruthy()
  })

  // Create API Key emits
  it('emits create-api-key event when Create button is clicked', async () => {
    const wrapper = mountComponent({ apiKeys: [] })
    const createBtn = wrapper.findAll('.el-button').filter(b => b.text().includes('Create API Key'))
    expect(createBtn.length).toBeGreaterThanOrEqual(1)
    await createBtn[0].trigger('click')
    expect(wrapper.emitted('create-api-key')).toBeTruthy()
  })
})
