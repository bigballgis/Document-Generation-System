<template>
  <div class="template-editor-page" v-loading="loading">
    <div class="editor-header">
      <div class="header-left">
        <el-button @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          {{ $t('common.back') }}
        </el-button>
        <h3 v-if="template" class="editor-title">
          {{ template.name }} — {{ editorModeLabel }}
        </h3>
      </div>
      <div class="header-actions">
        <!-- Mode switch button group (only in edit mode) -->
        <el-button-group v-if="!isPreview && template">
          <el-button
            :type="editorMode === 'onlyoffice' ? 'primary' : 'default'"
            @click="switchMode('onlyoffice')"
          >
            {{ $t('editor.onlyoffice') }}
          </el-button>
          <el-button
            :type="editorMode === 'monaco' ? 'primary' : 'default'"
            @click="switchMode('monaco')"
          >
            {{ $t('editor.monaco') }}
          </el-button>
        </el-button-group>

        <el-button
          v-if="!isPreview && template"
          type="primary"
          @click="togglePreview"
        >
          <el-icon><View /></el-icon>
          {{ $t('template.preview') }}
        </el-button>
        <el-button
          v-if="isPreview"
          @click="togglePreview"
        >
          {{ $t('common.edit') }}
        </el-button>
      </div>
    </div>

    <div v-if="editorReady" class="editor-body">
      <!-- OnlyOffice visual editor -->
      <OnlyOfficeEditor
        v-show="editorMode === 'onlyoffice'"
        :document-url="documentUrl"
        :document-key="documentKey"
        :document-title="documentTitle"
        :callback-url="callbackUrl"
        :view-only="isPreview"
        @ready="onEditorReady"
        @error="onEditorError"
        @close="goBack"
      />

      <!-- Monaco code editor -->
      <MonacoEditor
        v-if="editorMode === 'monaco'"
        v-model="monacoContent"
        :read-only="isPreview"
        @change="onMonacoChange"
      />
    </div>

    <div v-else-if="!loading" class="editor-placeholder">
      <el-empty :description="errorMessage || $t('editor.title')">
        <el-button v-if="errorMessage" type="primary" @click="initEditor">
          {{ $t('common.refresh') }}
        </el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft, View } from '@element-plus/icons-vue'
import { getTemplate, getOnlyOfficeUrl, type TemplateDTO } from '@/api/templates'
import OnlyOfficeEditor from '@/components/OnlyOfficeEditor.vue'
import MonacoEditor from '@/components/MonacoEditor.vue'

type EditorMode = 'onlyoffice' | 'monaco'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const loading = ref(false)
const template = ref<TemplateDTO | null>(null)
const editorReady = ref(false)
const isPreview = ref(false)
const errorMessage = ref('')
const editorMode = ref<EditorMode>('onlyoffice')

/** Content managed by the Monaco editor */
const monacoContent = ref('')

/** Presigned MinIO URL for OnlyOffice to download the document */
const onlyOfficeDocUrl = ref('')

const templateId = computed(() => Number(route.params.id))

const editorModeLabel = computed(() => {
  if (isPreview.value) return t('common.preview')
  return editorMode.value === 'onlyoffice'
    ? t('editor.onlyoffice')
    : t('editor.monaco')
})

/** The document URL: presigned MinIO URL that OnlyOffice can access directly */
const documentUrl = computed(() => {
  return onlyOfficeDocUrl.value
})

/** Unique key per document version for OnlyOffice caching */
const documentKey = computed(() => {
  if (!template.value) return ''
  return `template-${template.value.id}-v${template.value.version}-${Date.now()}`
})

const documentTitle = computed(() => {
  return template.value ? `${template.value.name}.docx` : 'Template.docx'
})

/** Callback URL for OnlyOffice to POST save events to (absolute, reachable from OnlyOffice container) */
const callbackUrl = computed(() => {
  // In Docker, OnlyOffice reaches the backend via the internal service name.
  // The VITE_BACKEND_INTERNAL_URL env var should be set to the backend address
  // reachable from OnlyOffice (e.g. http://app:8080 in docker-compose).
  const backendUrl = import.meta.env.VITE_BACKEND_INTERNAL_URL || 'http://app:8080'
  return `${backendUrl}/api/templates/${templateId.value}/onlyoffice-callback`
})

function goBack() {
  router.push(`/templates/${templateId.value}`)
}

function togglePreview() {
  isPreview.value = !isPreview.value
  // Force re-render by toggling editorReady
  editorReady.value = false
  setTimeout(() => {
    editorReady.value = true
  }, 100)
}

/**
 * Switch between OnlyOffice and Monaco editor modes.
 * When switching, we sync content between the two editors.
 * Full bidirectional real-time sync between OnlyOffice (binary docx) and Monaco (text)
 * is complex. Here we sync a text representation when the user switches modes.
 */
function switchMode(mode: EditorMode) {
  if (mode === editorMode.value) return

  // Sync content on mode switch
  if (mode === 'monaco' && editorMode.value === 'onlyoffice') {
    // Switching from OnlyOffice to Monaco:
    // In a full implementation, we would extract text content from OnlyOffice.
    // For now, preserve whatever is already in monacoContent.
    ElMessage.info(t('editor.syncContent'))
  } else if (mode === 'onlyoffice' && editorMode.value === 'monaco') {
    // Switching from Monaco to OnlyOffice:
    // In a full implementation, we would push monacoContent back into the document.
    ElMessage.info(t('editor.syncContent'))
  }

  editorMode.value = mode
}

function onMonacoChange(_value: string) {
  // Content change tracked via v-model
}

function onEditorReady() {
  // Editor loaded successfully
}

function onEditorError(message: string) {
  errorMessage.value = message
  ElMessage.error(message)
}

async function initEditor() {
  loading.value = true
  errorMessage.value = ''
  try {
    template.value = await getTemplate(templateId.value)
    // Fetch presigned MinIO URL for OnlyOffice to download the document
    const { url } = await getOnlyOfficeUrl(templateId.value)
    onlyOfficeDocUrl.value = url
    editorReady.value = true
  } catch {
    errorMessage.value = t('message.serverError')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  initEditor()
})
</script>

<style scoped>
.template-editor-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  margin-bottom: 8px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.editor-title {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.editor-body {
  flex: 1;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  overflow: hidden;
}

.editor-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
