<template>
  <div class="design-stage">
    <!-- Single toolbar: design tabs + editor tabs + actions -->
    <div class="design-toolbar">
      <div class="toolbar-tabs">
        <div
          class="tab-item"
          :class="{ active: activeView === 'parameter-table' }"
          @click="switchToView('parameter-table')"
        >{{ t('workspace.design.parameters') }}</div>
        <div
          class="tab-item"
          :class="{ active: activeView === 'segment-canvas' }"
          @click="switchToView('segment-canvas')"
        >{{ t('workspace.design.segmentArrangement') }}</div>
        <!-- Editor tabs from SegmentCanvas -->
        <div
          v-for="tab in editorTabs"
          :key="tab.id"
          class="tab-item tab-item-editor"
          :class="{ active: activeView === tab.id }"
          @click="switchToView(tab.id)"
        >
          <span>{{ tab.title }}</span>
          <el-icon class="tab-close" @click.stop="closeEditorTab(tab.id)"><Close /></el-icon>
        </div>
      </div>
      <div class="toolbar-actions">
        <el-button v-if="!readonly && isDraft" size="small" @click="handleImportZip">
          {{ t('workspace.design.importZip') }}
        </el-button>
        <input ref="zipInputRef" type="file" accept=".zip" style="display: none" @change="handleZipFileSelected" />
        <el-button size="small" @click="overviewVisible = true">
          {{ t('workspace.design.parameterOverview') }}
        </el-button>
        <el-button size="small" circle @click="settingsVisible = true">
          <el-icon><Setting /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- View area -->
    <div class="view-area">
      <ParameterTableDesign v-show="activeView === 'parameter-table'" :readonly="readonly" />
      <SegmentCanvas
        v-show="activeView === 'segment-canvas'"
        ref="segmentCanvasRef"
        :readonly="readonly"
        :hide-tabs="true"
        @open-editor="handleOpenEditor"
      />
      <!-- Editor tab content -->
      <div
        v-for="tab in editorTabs"
        :key="tab.id"
        v-show="activeView === tab.id"
        class="editor-tab-content"
      >
        <div v-if="tab.hint" class="isolation-hint" :class="`isolation-hint--${tab.type}`">
          <el-icon><WarningFilled /></el-icon>
          <span>{{ tab.hint }}</span>
        </div>
        <div class="editor-with-sidebar">
          <!-- Parameter sidebar for inserting variables -->
          <ParameterSidebar
            v-if="!readonly"
            :collapsed="sidebarCollapsed"
            :readonly="readonly"
            @update:collapsed="sidebarCollapsed = $event"
            @insert-variable="(name: string) => insertToEditor(tab.id, 'variable', name)"
            @insert-loop="(name: string) => insertToEditor(tab.id, 'loop', name)"
            @insert-condition="(expr: string) => insertToEditor(tab.id, 'condition', expr)"
          />
          <div class="editor-container">
            <template v-if="tab.error">
              <div class="editor-error">
                <el-empty :description="tab.error">
                  <el-button type="primary" @click="loadEditorTab(tab)">{{ t('common.refresh') }}</el-button>
                </el-empty>
              </div>
            </template>
            <template v-else-if="tab.documentUrl">
              <OnlyOfficeEditor
                :ref="(el: any) => { if (el) editorRefs[tab.id] = el }"
                :document-url="tab.documentUrl"
                :document-key="tab.documentKey"
                :document-title="tab.title + '.docx'"
                :callback-url="tab.callbackUrl"
                :view-only="readonly"
                :token="tab.token"
                @error="(msg: string) => ElMessage.error(msg)"
              />
            </template>
            <template v-else>
              <div class="editor-loading"><el-skeleton :rows="6" animated /></div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <ParameterOverviewPanel :visible="overviewVisible" @update:visible="overviewVisible = $event" @navigate-to-param="handleNavigateToParam" />
    <SettingsPopover :visible="settingsVisible" @update:visible="settingsVisible = $event" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Setting, Close, WarningFilled } from '@element-plus/icons-vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { importCompositeFromZip, getSegmentOnlyOfficeUrl } from '@/api/composite-templates'
import ParameterTableDesign from './ParameterTableDesign.vue'
import SegmentCanvas from './SegmentCanvas.vue'
import ParameterOverviewPanel from './ParameterOverviewPanel.vue'
import SettingsPopover from './SettingsPopover.vue'
import ParameterSidebar from './ParameterSidebar.vue'
import OnlyOfficeEditor from '@/components/OnlyOfficeEditor.vue'

defineProps<{ readonly: boolean }>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

const activeView = ref('parameter-table')
const overviewVisible = ref(false)
const settingsVisible = ref(false)
const zipInputRef = ref<HTMLInputElement | null>(null)
const segmentCanvasRef = ref()

const isDraft = computed(() => store.isDraft)
const sidebarCollapsed = ref(false)
const editorRefs: Record<string, any> = {}

// ── Editor tabs (managed here, not in SegmentCanvas) ──
interface EditorTab {
  id: string; title: string; type: 'segment' | 'header' | 'footer'
  segmentIndex: number; filePath: string; documentUrl: string
  documentKey: string; callbackUrl: string; hint: string
  loading: boolean; error: string; token: string
}

const editorTabs = reactive<EditorTab[]>([])

function handleOpenEditor(tabInfo: { id: string; title: string; type: string; segmentIndex: number; filePath: string }) {
  const existing = editorTabs.find(t => t.id === tabInfo.id)
  if (existing) { activeView.value = tabInfo.id; return }

  const backendUrl = import.meta.env.VITE_BACKEND_INTERNAL_URL || 'http://app:8080'
  const contentType = tabInfo.type === 'segment' ? 'body' : tabInfo.type
  const tab: EditorTab = {
    id: tabInfo.id, title: tabInfo.title, type: tabInfo.type as any,
    segmentIndex: tabInfo.segmentIndex, filePath: tabInfo.filePath,
    documentUrl: '', documentKey: `${tabInfo.type}-${store.templateId}-${tabInfo.segmentIndex}-${Date.now()}`,
    callbackUrl: `${backendUrl}/api/composite-templates/${store.templateId}/segments/${tabInfo.segmentIndex}/onlyoffice-callback?contentType=${contentType}`,
    hint: tabInfo.type === 'header' ? t('workspace.design.isolation.headerHint')
        : tabInfo.type === 'footer' ? t('workspace.design.isolation.footerHint')
        : t('workspace.design.isolation.bodyHint'),
    loading: false, error: '', token: '',
  }
  editorTabs.push(tab)
  activeView.value = tabInfo.id
  loadEditorTab(tab)
}

async function loadEditorTab(tab: EditorTab) {
  const idx = editorTabs.findIndex(t => t.id === tab.id)
  if (idx < 0) return
  editorTabs[idx].loading = true; editorTabs[idx].error = ''; editorTabs[idx].documentUrl = ''; editorTabs[idx].token = ''
  try {
    const { url } = await getSegmentOnlyOfficeUrl(store.templateId, editorTabs[idx].segmentIndex)
    editorTabs[idx].documentUrl = url || ''
    if (!url) editorTabs[idx].error = 'No document URL returned'
  } catch (e: any) {
    editorTabs[idx].error = e.response?.data?.message || e.message || 'Failed to load editor'
  } finally { editorTabs[idx].loading = false }
}

function closeEditorTab(id: string) {
  const idx = editorTabs.findIndex(t => t.id === id)
  if (idx >= 0) editorTabs.splice(idx, 1)
  if (activeView.value === id) activeView.value = 'segment-canvas'
  store.refreshAssemblyConfig()
}

function switchToView(view: string) { activeView.value = view }

function insertToEditor(tabId: string, type: 'variable' | 'loop' | 'condition', value: string) {
  console.log('[DesignStage] insertToEditor called:', { tabId, type, value, availableRefs: Object.keys(editorRefs) })
  const editor = editorRefs[tabId]
  if (!editor) {
    console.warn('[DesignStage] No editor ref found for tab:', tabId)
    return
  }
  console.log('[DesignStage] Editor ref found, type:', typeof editor, 'has insertVariable:', typeof editor.insertVariable)
  if (type === 'variable') editor.insertVariable(value)
  else if (type === 'loop') editor.insertLoop(value)
  else if (type === 'condition') editor.insertCondition(value)
}

function handleNavigateToParam(_paramId: number) { activeView.value = 'parameter-table' }

function handleImportZip() { zipInputRef.value?.click() }

async function handleZipFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    await importCompositeFromZip(file)
    await store.refreshAssemblyConfig(); await store.refreshParameters()
    ElMessage.success(t('message.importSuccess'))
  } catch (e: any) { ElMessage.error(e.response?.data?.message || e.message || t('message.importFailed')) }
  finally { input.value = '' }
}
</script>

<style scoped>
.design-stage { display: flex; flex-direction: column; flex: 1; height: 100%; min-height: 0; overflow: hidden; }

/* ── Single toolbar row ── */
.design-toolbar {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  overflow-x: auto;
}
.toolbar-tabs {
  display: flex;
  align-items: center;
  gap: 0;
  flex-shrink: 0;
}
.tab-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  border-bottom: 2px solid transparent;
  color: var(--el-text-color-secondary);
  transition: all 0.15s;
  font-weight: 500;
}
.tab-item:hover { color: var(--el-color-primary); background: var(--el-fill-color-light); }
.tab-item.active { color: var(--el-color-primary); border-bottom-color: var(--el-color-primary); }
.tab-item-editor { font-weight: 400; }
.tab-close { font-size: 12px; border-radius: 50%; padding: 2px; margin-left: 2px; }
.tab-close:hover { background: var(--el-fill-color); }

.toolbar-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; padding: 0 12px; }

/* ── View area ── */
.view-area { flex: 1; min-height: 0; overflow: hidden; display: flex; flex-direction: column; }

/* ── Editor tab content ── */
.editor-tab-content { flex: 1; display: flex; flex-direction: column; min-height: 0; overflow: hidden; }
.isolation-hint { display: flex; align-items: center; gap: 8px; padding: 6px 12px; border-radius: 6px; font-size: 12px; margin: 4px 8px; flex-shrink: 0; }
.isolation-hint--segment { background: var(--el-color-info-light-9); color: var(--el-color-info); }
.isolation-hint--header { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.isolation-hint--footer { background: var(--el-color-success-light-9); color: var(--el-color-success); }
.editor-with-sidebar { flex: 1; display: flex; min-height: 0; overflow: hidden; }
.editor-container { flex: 1; min-height: 0; border: 1px solid var(--el-border-color-lighter); border-radius: 4px; overflow: hidden; }
.editor-loading, .editor-error { display: flex; align-items: center; justify-content: center; height: 100%; padding: 24px; }

:deep(.drag-source-active) { opacity: 0.5; }
</style>
