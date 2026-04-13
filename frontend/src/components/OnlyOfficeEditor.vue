<template>
  <div class="onlyoffice-editor-wrapper">
    <TemplateTagToolbar
      v-if="!viewOnly"
      @insert-variable="insertVariable"
      @insert-loop="insertLoop"
      @insert-condition="insertCondition"
    />
    <div :id="editorContainerId" class="editor-container" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { useLocale } from '@/composables/useLocale'
import TemplateTagToolbar from './TemplateTagToolbar.vue'

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
}

const props = withDefaults(defineProps<OnlyOfficeEditorProps>(), {
  documentTitle: 'Template.docx',
  callbackUrl: '',
  viewOnly: false,
  documentType: 'word',
})

const emit = defineEmits<{
  (e: 'ready'): void
  (e: 'save', url: string): void
  (e: 'close'): void
  (e: 'error', message: string): void
}>()

const userStore = useUserStore()
const { locale } = useLocale()

const editorContainerId = `onlyoffice-editor-${Date.now()}`
let editorInstance: any = null
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
        existing.addEventListener('error', () => reject(new Error('Failed to load OnlyOffice API')))
      }
      return
    }

    const script = document.createElement('script')
    script.src = scriptUrl
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('Failed to load OnlyOffice API script'))
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
        // Disable review/comment — template editing doesn't need these
        review: false,
        comment: false,
        // Allow copy/paste for template tag insertion
        copy: true,
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
        // Keep full toolbar with buttons visible (compactToolbar hides them)
        compactToolbar: false,
        compactHeader: false,
        // Hide right panel on initial load for more editing space
        hideRightMenu: true,
        // Hide rulers for cleaner look
        hideRulers: true,
        // Disable comments — not needed for template editing
        comments: false,
        help: false,
        chat: false,
        feedback: false,
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

  return config
}

function createEditor() {
  if (!(window as any).DocsAPI) {
    emit('error', 'OnlyOffice API not loaded')
    return
  }

  destroyEditor()

  const config = buildConfig()
  editorInstance = new (window as any).DocsAPI.DocEditor(editorContainerId, config)
}

function destroyEditor() {
  if (editorInstance) {
    try {
      editorInstance.destroyEditor()
    } catch {
      // ignore cleanup errors
    }
    editorInstance = null
  }
}

/** Insert text at cursor position via the OnlyOffice command API */
function insertTextAtCursor(text: string) {
  if (!editorInstance) return
  // Use the connector to execute commands in the editor
  const connector = editorInstance.createConnector?.()
  if (connector) {
    connector.executeMethod('PasteText', [text])
  }
}

function insertVariable(name: string) {
  insertTextAtCursor(`{${name}}`)
}

function insertLoop(arrayName: string) {
  insertTextAtCursor(`{#${arrayName}}\n\n{/${arrayName}}`)
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

onMounted(async () => {
  try {
    await loadScript()
    scriptLoaded.value = true
    createEditor()
  } catch (err: any) {
    emit('error', err.message || 'Failed to initialize OnlyOffice editor')
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
}

.editor-container {
  flex: 1;
  min-height: 600px;
  width: 100%;
}
</style>
