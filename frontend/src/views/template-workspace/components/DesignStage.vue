<template>
  <div class="design-stage">
    <!-- Toolbar -->
    <div class="design-toolbar">
      <div class="toolbar-left">
        <el-button v-if="!isSingleTemplate" @click="segmentPopoverVisible = true">
          {{ t('workspace.design.segmentArrangement') }}
        </el-button>
        <el-button v-if="!readonly" @click="handleImportZip">
          {{ t('workspace.design.importZip') }}
        </el-button>
        <input
          ref="zipInputRef"
          type="file"
          accept=".zip"
          style="display: none"
          @change="handleZipFileSelected"
        />
      </div>
      <div class="toolbar-right">
        <!-- Segment selector for multi-segment templates -->
        <el-select
          v-if="segments.length > 1 && !isSingleTemplate"
          v-model="selectedSegmentIndex"
          size="default"
          style="width: 200px"
          :placeholder="t('workspace.design.selectSegment')"
        >
          <el-option
            v-for="(seg, idx) in enabledSegments"
            :key="idx"
            :label="`${seg.position + 1}. ${seg.name}`"
            :value="idx"
          />
        </el-select>
        <el-button circle @click="settingsPopoverVisible = true">
          <el-icon><Setting /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- Main area: Editor + ParameterDrawer -->
    <div class="design-main">
      <div class="editor-area" :class="{ 'full-width': drawerCollapsed }">
        <div v-if="editorReady && (currentSegment || isSingleTemplate)" class="editor-wrapper">
          <OnlyOfficeEditor
            :document-url="documentUrl"
            :document-key="documentKey"
            :document-title="documentTitle"
            :callback-url="callbackUrl"
            :view-only="readonly"
            @ready="onEditorReady"
            @error="onEditorError"
          />
        </div>
        <div v-else-if="editorLoading" class="editor-placeholder">
          <el-skeleton :rows="8" animated />
        </div>
        <div v-else class="editor-placeholder">
          <el-empty :description="editorError || t('workspace.design.noSegments')">
            <el-button v-if="editorError" type="primary" @click="loadEditorForSegment">
              {{ t('common.refresh') }}
            </el-button>
          </el-empty>
        </div>
      </div>

      <ParameterDrawer
        :collapsed="drawerCollapsed"
        :readonly="readonly"
        @update:collapsed="drawerCollapsed = $event"
      />
    </div>

    <!-- Segment Popover -->
    <SegmentPopover
      :visible="segmentPopoverVisible"
      @update:visible="segmentPopoverVisible = $event"
    />

    <!-- Settings Popover -->
    <SettingsPopover
      :visible="settingsPopoverVisible"
      @update:visible="settingsPopoverVisible = $event"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { getOnlyOfficeUrl } from '@/api/templates'
import { importCompositeFromZip } from '@/api/composite-templates'
import OnlyOfficeEditor from '@/components/OnlyOfficeEditor.vue'
import ParameterDrawer from './ParameterDrawer.vue'
import SegmentPopover from './SegmentPopover.vue'
import SettingsPopover from './SettingsPopover.vue'
import type { AssemblySegmentEntry } from '@/types/segment'

defineProps<{
  readonly: boolean
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

const drawerCollapsed = ref(false)
const segmentPopoverVisible = ref(false)
const settingsPopoverVisible = ref(false)
const selectedSegmentIndex = ref(0)
const zipInputRef = ref<HTMLInputElement | null>(null)

// Editor state
const editorReady = ref(false)
const editorLoading = ref(false)
const editorError = ref('')
const onlyOfficeDocUrl = ref('')

const segments = computed<AssemblySegmentEntry[]>(() => {
  return store.assemblyConfig?.segments ?? []
})

const isSingleTemplate = computed(() => {
  return store.template?.templateType !== 'COMPOSITE'
})

const enabledSegments = computed(() => {
  return segments.value.filter(s => s.enabled && s.filePath)
})

const currentSegment = computed(() => {
  return enabledSegments.value[selectedSegmentIndex.value] ?? null
})

const documentUrl = computed(() => onlyOfficeDocUrl.value)

const documentKey = computed(() => {
  if (!store.template || !currentSegment.value) return ''
  return `design-${store.templateId}-seg${selectedSegmentIndex.value}-v${store.template.version}-${Date.now()}`
})

const documentTitle = computed(() => {
  return currentSegment.value?.name
    ? `${currentSegment.value.name}.docx`
    : 'Template.docx'
})

const callbackUrl = computed(() => {
  const backendUrl = import.meta.env.VITE_BACKEND_INTERNAL_URL || 'http://app:8080'
  return `${backendUrl}/api/templates/${store.templateId}/onlyoffice-callback`
})

async function loadEditorForSegment() {
  if (!store.templateId || !currentSegment.value) return
  editorLoading.value = true
  editorError.value = ''
  editorReady.value = false
  try {
    const { url } = await getOnlyOfficeUrl(store.templateId)
    onlyOfficeDocUrl.value = url
    editorReady.value = true
  } catch {
    editorError.value = t('message.serverError')
  } finally {
    editorLoading.value = false
  }
}

function onEditorReady() {
  // Editor loaded
}

function onEditorError(message: string) {
  editorError.value = message
  ElMessage.error(message)
}

// Import ZIP
function handleImportZip() {
  zipInputRef.value?.click()
}

async function handleZipFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    await importCompositeFromZip(file)
    await store.refreshAssemblyConfig()
    await store.refreshParameters()
    ElMessage.success(t('message.importSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.importFailed'))
  } finally {
    input.value = ''
  }
}

watch(selectedSegmentIndex, () => {
  loadEditorForSegment()
})

onMounted(() => {
  // For SINGLE templates or templates with enabled segments, load editor directly
  if (enabledSegments.value.length > 0) {
    loadEditorForSegment()
  } else if (store.template && store.template.templateType !== 'COMPOSITE') {
    // SINGLE template — load editor directly using template's own file
    loadEditorForSingleTemplate()
  }
})

async function loadEditorForSingleTemplate() {
  if (!store.templateId) return
  editorLoading.value = true
  editorError.value = ''
  editorReady.value = false
  try {
    const { url } = await getOnlyOfficeUrl(store.templateId)
    onlyOfficeDocUrl.value = url
    editorReady.value = true
  } catch {
    editorError.value = t('message.serverError')
  } finally {
    editorLoading.value = false
  }
}
</script>

<style scoped>
.design-stage {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 200px);
  min-height: 500px;
}
.design-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  margin-bottom: 8px;
  gap: 8px;
}
.toolbar-left, .toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.design-main {
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
