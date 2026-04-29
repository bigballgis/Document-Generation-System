<template>
  <div class="onlyoffice-editor-wrapper">
    <div :id="editorContainerId" class="editor-container" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { signOnlyOfficeConfig } from '@/api/composite-templates'

export interface OnlyOfficeEditorProps {
  /** URL of the document to edit (from MinIO / backend) */
  documentUrl: string
  /** Document file key for OnlyOffice (unique per document version) */
  documentKey: string
  /** Document title shown in the editor */
  documentTitle?: string
  /** Callback URL for OnlyOffice to notify on save */
  callbackUrl?: string
  /** View-only / preview mode */
  viewOnly?: boolean
  /** Document type: word, cell, slide */
  documentType?: string
  /** JWT token for OnlyOffice Document Server authentication */
  token?: string
}

const props = withDefaults(defineProps<OnlyOfficeEditorProps>(), {
  documentTitle: 'Template.docx',
  callbackUrl: '',
  viewOnly: false,
  documentType: 'word',
  token: '',
})

const emit = defineEmits<{
  (e: 'ready'): void
  (e: 'save', url: string): void
  (e: 'close'): void
  (e: 'error', message: string): void
}>()

const userStore = useUserStore()
const { locale, t } = useI18n()

const editorContainerId = `onlyoffice-editor-${Date.now()}`
const editorInstanceRef = ref<any>(null)
const scriptLoaded = ref(false)

const onlyofficeUrl = computed(() => {
  return import.meta.env.VITE_ONLYOFFICE_URL || 'http://localhost:8088'
})

/** Map vue-i18n locale codes to OnlyOffice language codes */
function mapLocale(loc: string): string {
  const map: Record<string, string> = {
    'en-US': 'en',
    'zh-CN': 'zh',
    'zh-TW': 'zh-TW',
  }
  return map[loc] || 'en'
}

/** Dynamically load the OnlyOffice API script */
function loadScript(): Promise<void> {
  return new Promise((resolve, reject) => {
    const scriptUrl = `${onlyofficeUrl.value}/web-apps/apps/api/documents/api.js`
    const existing = document.querySelector(`script[src="${scriptUrl}"]`)
    if (existing) {
      // Already loaded
      if ((window as any).DocsAPI) {
        resolve()
      } else {
        existing.addEventListener('load', () => resolve())
        existing.addEventListener('error', () => reject(new Error(t('workspace.editor.loadScriptFailed'))))
      }
      return
    }

    const script = document.createElement('script')
    script.src = scriptUrl
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error(t('workspace.editor.loadScriptFailed')))
    document.head.appendChild(script)
  })
}

function buildConfig() {
  const editorLang = mapLocale(locale.value)

  const config: Record<string, any> = {
    document: {
      fileType: 'docx',
      key: props.documentKey,
      title: props.documentTitle,
      url: props.documentUrl,
      permissions: {
        edit: !props.viewOnly,
        download: true,
        print: true,
        review: false,
        comment: false,
        copy: true,
        // Enable connector for programmatic text insertion
        modifyContentControl: true,
        modifyFilter: true,
      },
    },
    documentType: props.documentType,
    editorConfig: {
      mode: props.viewOnly ? 'view' : 'edit',
      lang: editorLang,
      callbackUrl: props.callbackUrl || undefined,
      user: {
        id: String(userStore.userInfo?.id || 'anonymous'),
        name: userStore.userInfo?.username || 'Anonymous',
      },
      customization: {
        autosave: true,
        forcesave: true,
        compactToolbar: false,
        compactHeader: false,
        hideRightMenu: true,
        hideRulers: true,
        comments: false,
        help: false,
        chat: false,
        feedback: false,
        macros: true,
        plugins: true,
      },
      plugins: {
        autostart: ['asc.{A8705DEE-7544-4C33-B3D5-168406D92F72}'],
        pluginsData: [
          onlyofficeUrl.value + '/sdkjs-plugins/insert-text/config.json',
        ],
      },
    },
    events: {
      onReady: () => {
        emit('ready')
      },
      onDocumentStateChange: () => {
        // document state changed (modified/saved)
      },
      onRequestClose: () => {
        emit('close')
      },
      onError: (event: any) => {
        emit('error', event?.data?.message || 'Unknown editor error')
      },
    },
  }

  // Add JWT token for OnlyOffice Document Server authentication
  // Token is set dynamically in createEditor() after signing
  return config
}

async function createEditor() {
  if (!(window as any).DocsAPI) {
    emit('error', t('workspace.editor.apiNotLoaded'))
    return
  }

  destroyEditor()

  const config = buildConfig()

  // Sign the full config with the backend JWT secret
  try {
    const { token } = await signOnlyOfficeConfig(config)
    config.token = token
  } catch (err: any) {
    console.warn('Failed to sign OnlyOffice config, proceeding without token:', err.message)
  }

  editorInstanceRef.value = new (window as any).DocsAPI.DocEditor(editorContainerId, config)
}

function destroyEditor() {
  if (editorInstanceRef.value) {
    try {
      editorInstanceRef.value.destroyEditor()
    } catch {
      // ignore cleanup errors
    }
    editorInstanceRef.value = null
  }
}

/** Insert text at cursor position */
function insertTextAtCursor(text: string) {
  const instance = editorInstanceRef.value
  if (!instance) return

  // Method 1: Connector API (if Euro-Office unlocks it in future)
  try {
    const connector = instance.createConnector?.()
    if (connector) {
      connector.executeMethod('PasteText', [text])
      return
    }
  } catch { /* not available */ }

  // Method 2: Clipboard + auto-focus (current best approach for Euro-Office CE)
  const iframe = document.querySelector('iframe[name="frameEditor"]') as HTMLIFrameElement | null
    ?? document.getElementById(editorContainerId)?.querySelector('iframe') as HTMLIFrameElement | null

  navigator.clipboard.writeText(text).then(() => {
    if (iframe) {
      iframe.focus()
      iframe.contentWindow?.focus()
    }
    import('element-plus').then(({ ElMessage }) => {
      ElMessage.success({ message: t('workspace.editor.insertTextCopied', { text }), duration: 2000 })
    })
  }).catch(() => {
    window.prompt(t('workspace.editor.insertTextPrompt'), text)
  })
}

function insertVariable(name: string) {
  insertTextAtCursor(`{${name}}`)
}

function insertLoop(loopText: string) {
  insertTextAtCursor(loopText)
}

function insertCondition(conditionExpr: string) {
  insertTextAtCursor(`{#if ${conditionExpr}}\n\n{/if}`)
}

// Watch locale changes to recreate editor with new language
watch(locale, () => {
  if (scriptLoaded.value) {
    createEditor()
  }
})

// Reload editor when the document identity changes (createEditor destroys any previous instance first)
watch(
  () => [props.documentUrl, props.documentKey] as const,
  () => {
    if (scriptLoaded.value) {
      createEditor()
    }
  },
)

onMounted(async () => {
  try {
    await loadScript()
    scriptLoaded.value = true
    await createEditor()
  } catch (err: any) {
    emit('error', err.message || t('workspace.editor.initFailed'))
  }
})

onBeforeUnmount(() => {
  destroyEditor()
})

defineExpose({
  insertVariable,
  insertLoop,
  insertCondition,
})
</script>

<style scoped>
.onlyoffice-editor-wrapper {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  overflow: hidden;
}

.editor-container {
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 100%;
}

.onlyoffice-editor-wrapper :deep(iframe) {
  height: 100% !important;
}
</style>
