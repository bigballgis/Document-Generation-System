<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="80%"
    top="5vh"
    destroy-on-close
    @update:model-value="$emit('update:visible', $event)"
  >
    <div class="isolation-hint" :class="`isolation-hint--${nodeType}`">
      <el-icon><WarningFilled /></el-icon>
      <span>{{ isolationHintText }}</span>
    </div>

    <div class="editor-container">
      <OnlyOfficeEditor
        v-if="visible && documentUrl"
        :document-url="documentUrl"
        :document-key="documentKey"
        :document-title="documentTitle"
        :callback-url="callbackUrl"
        :view-only="readonly"
        :token="documentToken"
        @ready="onEditorReady"
        @error="onEditorError"
      />
      <div v-else-if="loading" class="editor-loading">
        <el-skeleton :rows="6" animated />
      </div>
      <div v-else-if="error" class="editor-error">
        <el-empty :description="error">
          <el-button type="primary" @click="loadDocument">{{ t('common.refresh') }}</el-button>
        </el-empty>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import OnlyOfficeEditor from '@/components/OnlyOfficeEditor.vue'
import { getSegmentOnlyOfficeUrl } from '@/api/composite-templates'

const props = defineProps<{
  visible: boolean
  nodeType: 'header' | 'footer'
  filePath: string
  templateId: number
  readonly: boolean
  segmentIndex?: number
}>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved'): void
}>()

const { t } = useI18n()

const documentUrl = ref('')
const documentToken = ref('')
const loading = ref(false)
const error = ref('')

const dialogTitle = computed(() => {
  return props.nodeType === 'header'
    ? t('workspace.design.canvas.header')
    : t('workspace.design.canvas.footer')
})

const isolationHintText = computed(() => {
  return props.nodeType === 'header'
    ? t('workspace.design.isolation.headerHint')
    : t('workspace.design.isolation.footerHint')
})

const documentKey = computed(() => {
  return `${props.nodeType}-${props.templateId}-${props.filePath}-${Date.now()}`
})

const documentTitle = computed(() => {
  return `${props.nodeType}.docx`
})

const callbackUrl = computed(() => {
  const backendUrl = import.meta.env.VITE_BACKEND_INTERNAL_URL || 'http://app:8080'
  const idx = props.segmentIndex ?? 0
  return `${backendUrl}/api/composite-templates/${props.templateId}/segments/${idx}/onlyoffice-callback?contentType=${props.nodeType}`
})

async function loadDocument() {
  if (!props.templateId || props.segmentIndex == null) return
  loading.value = true
  error.value = ''
  try {
    const { url } = await getSegmentOnlyOfficeUrl(props.templateId, props.segmentIndex)
    documentUrl.value = url
  } catch (e: any) {
    error.value = e.message || t('message.serverError')
  } finally {
    loading.value = false
  }
}

function onEditorReady() {
  // Editor loaded successfully
}

function onEditorError(message: string) {
  error.value = message
  ElMessage.error(message)
}

watch(() => props.visible, (val) => {
  if (val) {
    loadDocument()
  } else {
    documentUrl.value = ''
    documentToken.value = ''
    error.value = ''
  }
})
</script>

<style scoped>
.isolation-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  margin-bottom: 12px;
}

.isolation-hint--header {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  border: 1px solid var(--el-color-primary-light-7);
}

.isolation-hint--footer {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
  border: 1px solid var(--el-color-success-light-7);
}

.editor-container {
  height: 60vh;
  min-height: 400px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  overflow: hidden;
}

.editor-loading,
.editor-error {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 24px;
}
</style>

