import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import OnlyOfficeEditor from '@/components/OnlyOfficeEditor.vue'

vi.mock('@/api/composite-templates', () => ({
  signOnlyOfficeConfig: vi.fn().mockResolvedValue({ token: 'mock-token' }),
}))

/** Must match `OnlyOfficeEditor.vue`: `import.meta.env.VITE_ONLYOFFICE_URL || 'http://localhost:8088'` */
const ONLYOFFICE_BASE = 'http://localhost:8088'
const SCRIPT_SRC = `${ONLYOFFICE_BASE}/web-apps/apps/api/documents/api.js`

function seedUserStore() {
  setActivePinia(createPinia())
  const user = useUserStore()
  user.setUserInfo({
    id: 1,
    username: 'tester',
    email: 'tester@example.com',
    role: 'USER',
    tenantId: 1,
    locale: 'en-US',
  })
}

function installPreloadedScriptAndDocsApi(DocEditor: ReturnType<typeof vi.fn>) {
  const script = document.createElement('script')
  script.src = SCRIPT_SRC
  document.head.appendChild(script)
  ;(window as any).DocsAPI = { DocEditor }
}

function teardownOnlyOfficeMock() {
  delete (window as any).DocsAPI
  document.querySelectorAll('script').forEach((el) => {
    if (el.getAttribute('src') === SCRIPT_SRC) el.remove()
  })
}

describe('OnlyOfficeEditor', () => {
  let DocEditor: ReturnType<typeof vi.fn>
  const editorInstances: Array<{ destroyEditor: ReturnType<typeof vi.fn> }> = []

  beforeEach(() => {
    seedUserStore()
    editorInstances.length = 0
    DocEditor = vi.fn(function DocEditorMock(
      this: { destroyEditor: ReturnType<typeof vi.fn> },
      _containerId: string,
      _config: unknown,
    ) {
      this.destroyEditor = vi.fn()
      editorInstances.push(this)
    })
    installPreloadedScriptAndDocsApi(DocEditor)
  })

  afterEach(() => {
    teardownOnlyOfficeMock()
  })

  it('constructs DocEditor once on mount after script is ready', async () => {
    mount(OnlyOfficeEditor, {
      props: {
        documentUrl: 'http://example.com/doc-a.docx',
        documentKey: 'key-a',
      },
    })
    await flushPromises()
    expect(DocEditor).toHaveBeenCalledTimes(1)
  })

  /**
   * WS-06-T04: When `documentUrl` or `documentKey` changes, `createEditor()` runs again;
   * it calls `destroyEditor()` before constructing a new `DocEditor`.
   */
  describe('WS-06-T04 document identity change', () => {
    it('destroys the previous editor and builds a new DocEditor when url and key change', async () => {
      const wrapper = mount(OnlyOfficeEditor, {
        props: {
          documentUrl: 'http://example.com/doc-a.docx',
          documentKey: 'key-a',
        },
      })
      await flushPromises()

      expect(DocEditor).toHaveBeenCalledTimes(1)
      expect(editorInstances).toHaveLength(1)

      await wrapper.setProps({
        documentUrl: 'http://example.com/doc-b.docx',
        documentKey: 'key-b',
      })
      await flushPromises()

      expect(editorInstances[0].destroyEditor).toHaveBeenCalled()
      expect(DocEditor).toHaveBeenCalledTimes(2)
      const [, secondConfig] = DocEditor.mock.calls[1]
      expect((secondConfig as any).document.url).toBe('http://example.com/doc-b.docx')
      expect((secondConfig as any).document.key).toBe('key-b')
    })

    it('recreates editor when only documentUrl changes', async () => {
      const wrapper = mount(OnlyOfficeEditor, {
        props: {
          documentUrl: 'http://example.com/v1.docx',
          documentKey: 'same-key',
        },
      })
      await flushPromises()
      expect(DocEditor).toHaveBeenCalledTimes(1)

      await wrapper.setProps({ documentUrl: 'http://example.com/v2.docx' })
      await flushPromises()

      expect(editorInstances[0].destroyEditor).toHaveBeenCalled()
      expect(DocEditor).toHaveBeenCalledTimes(2)
    })

    it('recreates editor when only documentKey changes', async () => {
      const wrapper = mount(OnlyOfficeEditor, {
        props: {
          documentUrl: 'http://example.com/same.docx',
          documentKey: 'key-1',
        },
      })
      await flushPromises()
      expect(DocEditor).toHaveBeenCalledTimes(1)

      await wrapper.setProps({ documentKey: 'key-2' })
      await flushPromises()

      expect(editorInstances[0].destroyEditor).toHaveBeenCalled()
      expect(DocEditor).toHaveBeenCalledTimes(2)
    })
  })
})
