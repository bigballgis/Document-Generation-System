<template>
  <div class="segment-detail-design">
    <div v-if="enabledSegments.length === 0" class="empty-state">
      <el-empty :description="t('workspace.editor.empty')">
        <template #image>
          <el-icon :size="64" color="var(--el-text-color-secondary)"><Document /></el-icon>
        </template>
      </el-empty>
    </div>

    <template v-else>
      <div class="detail-header">
        <el-select
          v-model="selectedSegmentIndex"
          size="default"
          style="width: 240px"
          :placeholder="t('workspace.design.selectSegment')"
        >
          <el-option
            v-for="(seg, idx) in enabledSegments"
            :key="idx"
            :label="`${seg.position + 1}. ${seg.name}`"
            :value="idx"
          />
        </el-select>
        <div class="isolation-hint">
          <el-icon><WarningFilled /></el-icon>
          <span>{{ t('workspace.design.isolation.bodyHint') }}</span>
        </div>
      </div>

      <div class="detail-main">
        <div class="editor-area" :class="{ 'full-width': sidebarCollapsed }">
          <div v-if="editorReady && currentSegment" class="editor-wrapper">
            <OnlyOfficeEditor
              ref="editorRef"
              :document-url="documentUrl"
              :document-key="documentKey"
              :document-title="documentTitle"
              :callback-url="callbackUrl"
              :view-only="readonly"
              :token="onlyOfficeToken"
              @ready="onEditorReady"
              @error="onEditorError"
            />
          </div>
          <div v-else-if="editorLoading" class="editor-placeholder">
            <el-skeleton :rows="8" animated />
          </div>
          <div v-else class="editor-placeholder">
            <el-empty :description="editorError || t('workspace.design.noSegments')">
              <el-button v-if="editorError" type="primary" @click="loadEditor">
                {{ t('common.refresh') }}
              </el-button>
            </el-empty>
          </div>
        </div>

        <ParameterSidebar
          :collapsed="sidebarCollapsed"
          :readonly="readonly"
          @update:collapsed="sidebarCollapsed = $event"
          @insert-variable="handleInsertVariable"
          @insert-loop="handleInsertLoop"
          @insert-condition="handleInsertCondition"
        />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Document, WarningFilled } from '@element-plus/icons-vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { getSegmentOnlyOfficeUrl } from '@/api/composite-templates'
import OnlyOfficeEditor from '@/components/OnlyOfficeEditor.vue'
import ParameterSidebar from './ParameterSidebar.vue'
import type { AssemblySegmentEntry } from '@/types/segment'

defineProps<{
  readonly: boolean
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

const selectedSegmentIndex = ref(0)
const sidebarCollapsed = ref(false)
const editorRef = ref<InstanceType<typeof OnlyOfficeEditor> | null>(null)

const editorReady = ref(false)
const editorLoading = ref(false)
const editorError = ref('')
const onlyOfficeDocUrl = ref('')
const onlyOfficeToken = ref('')

const segments = computed<AssemblySegmentEntry[]>(() => {
  return store.assemblyConfig?.segments ?? []
})

const enabledSegments = computed(() => {
  return segments.value
    .filter(s => s.enabled && s.filePath)
    .slice()
    .sort((a, b) => (a.position ?? 0) - (b.position ?? 0))
})

const currentSegment = computed(() => {
  return enabledSegments.value[selectedSegmentIndex.value] ?? null
})

const documentUrl = computed(() => onlyOfficeDocUrl.value)

const documentKey = computed(() => {
  if (!store.template || !currentSegment.value) return ''
  return `detail-${store.templateId}-seg${selectedSegmentIndex.value}-v${store.template.version}-${Date.now()}`
})

const documentTitle = computed(() => {
  return currentSegment.value?.name
    ? `${currentSegment.value.name}.docx`
    : 'Template.docx'
})

const callbackUrl = computed(() => {
  const backendUrl = import.meta.env.VITE_BACKEND_INTERNAL_URL || 'http://app:8080'
  const segIdx = enabledSegments.value.indexOf(currentSegment.value!)
  const actualIndex = segIdx >= 0 ? segments.value.indexOf(currentSegment.value!) : 0
  return `${backendUrl}/api/composite-templates/${store.templateId}/segments/${actualIndex}/onlyoffice-callback?contentType=body`
})

async function loadEditor() {
  if (!store.templateId || !currentSegment.value) return
  const actualIndex = segments.value.indexOf(currentSegment.value)
  if (actualIndex < 0) return

  editorLoading.value = true
  editorError.value = ''
  editorReady.value = false
  try {
    const { url } = await getSegmentOnlyOfficeUrl(store.templateId, actualIndex)
    onlyOfficeDocUrl.value = url
    editorReady.value = true
  } catch {
    editorError.value = t('message.serverError')
  } finally {
    editorLoading.value = false
  }
}

function onEditorReady() {}

function onEditorError(message: string) {
  editorError.value = message
  ElMessage.error(message)
}

function handleInsertVariable(paramPath: string) {
  editorRef.value?.insertVariable(paramPath)
}

function handleInsertLoop(loopText: string) {
  editorRef.value?.insertLoop(loopText)
}

function handleInsertCondition(expr: string) {
  editorRef.value?.insertCondition(expr)
}

watch(selectedSegmentIndex, () => {
  if (enabledSegments.value.length > 0) {
    loadEditor()
  }
}, { immediate: true })
</script>

<style scoped>
.segment-detail-design {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.detail-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 0;
  flex-shrink: 0;
}
.isolation-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: 4px;
  font-size: 12px;
  color: var(--el-color-warning-dark-2);
  flex: 1;
}
.detail-main {
  flex: 1;
  display: flex;
  gap: 0;
  min-height: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  overflow: hidden;
}
.editor-area {
  flex: 7;
  min-width: 0;
  display: flex;
  flex-direction: column;
  transition: flex 0.3s ease;
}
.editor-area.full-width {
  flex: 1;
}
.editor-wrapper {
  flex: 1;
  min-height: 0;
}
.editor-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
</style>

