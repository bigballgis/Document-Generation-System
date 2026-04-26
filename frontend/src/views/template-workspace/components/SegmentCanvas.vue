<template>
  <div class="segment-canvas">
    <div class="canvas-body">
      <div class="panel-col"><ComponentPanel :readonly="readonly" :can-undo="assemblyConfig.canUndo.value" :can-redo="assemblyConfig.canRedo.value" @undo="handleUndo" @redo="handleRedo" /></div>
      <div class="canvas-col">
        <CanvasArea
          :nodes="canvasNodes.nodes.value"
          :segments="assemblyConfig.segments.value"
          :template-id="store.templateId"
          :readonly="readonly"
          @add-content-node="(...args: any[]) => handleAddContentNode(args[0], args[1])"
          @add-control-node="(...args: any[]) => handleAddControlNode(args[0], args[1])"
          @remove-node="(...args: any[]) => handleRemoveNode(args[0])"
          @reorder="(...args: any[]) => handleReorder(args[0])"
          @update-segment="(...args: any[]) => handleUpdateSegment(args[0], args[1])"
          @update-node="(...args: any[]) => handleUpdateNode(args[0], args[1])"
          @edit-segment="(...args: any[]) => handleEditSegment(args[0])"
          @edit-header-footer="(...args: any[]) => handleEditHeaderFooter(args[0])"
          @publish-segment="(...args: any[]) => handlePublishSegment(args[0])"
          @show-segment-versions="(...args: any[]) => handleShowSegmentVersions(args[0])"
        />
      </div>
    </div>

    <!-- Segment Version Dialog -->
    <SegmentVersionDialog
      v-model="versionDialogVisible"
      :template-id="store.templateId"
      :segment-name="activeSegmentName"
      @published="handleVersionPublished"
      @rolled-back="handleVersionRolledBack"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { useAssemblyConfig } from '@/composables/useAssemblyConfig'
import { useCanvasNodes } from '@/composables/useCanvasNodes'
import { updateAssemblyConfig, createBlankHeaderFooter, publishSegment } from '@/api/composite-templates'
import type { CanvasNode } from '@/composables/useCanvasNodes'
import type { AssemblySegmentEntry } from '@/types/segment'
import ComponentPanel from './ComponentPanel.vue'
import CanvasArea from './CanvasArea.vue'
import SegmentVersionDialog from './SegmentVersionDialog.vue'

defineProps<{ readonly: boolean }>()

const emit = defineEmits<{
  (e: 'open-editor', info: { id: string; title: string; type: string; segmentIndex: number; filePath: string }): void
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()
const assemblyConfig = useAssemblyConfig()
const canvasNodes = useCanvasNodes()
const saving = ref(false)
const versionDialogVisible = ref(false)
const activeSegmentName = ref('')

function syncFromStore() {
  const segments = store.assemblyConfig?.segments ?? []
  assemblyConfig.deserialize({ segments })
  canvasNodes.setNodes(canvasNodes.fromSegments(segments))
}
onMounted(() => syncFromStore())
watch(() => store.assemblyConfig, () => syncFromStore(), { deep: true })

function syncNodesToSegments() {
  assemblyConfig.setSegments(canvasNodes.toSegments(canvasNodes.nodes.value, assemblyConfig.segments.value))
}

function handleAddContentNode(entry: AssemblySegmentEntry, _idx: number) {
  assemblyConfig.addSegment(entry)
  canvasNodes.setNodes(canvasNodes.fromSegments(assemblyConfig.segments.value))
  scheduleAutoSave()
}
function handleAddControlNode(type: CanvasNode['type'], insertIndex: number) {
  const node: CanvasNode = { id: canvasNodes.generateNodeId(), type }
  if (type === 'page-number') node.pageNumberFormat = 'ARABIC'
  canvasNodes.addNode(node, insertIndex)
  syncNodesToSegments()
  scheduleAutoSave()
}
function handleRemoveNode(index: number) { canvasNodes.removeNode(index); syncNodesToSegments(); scheduleAutoSave() }
function handleReorder(newNodes: CanvasNode[]) { canvasNodes.setNodes(newNodes); syncNodesToSegments(); scheduleAutoSave() }
function handleUpdateSegment(segmentIndex: number, patch: Partial<AssemblySegmentEntry>) {
  assemblyConfig.updateSegment(segmentIndex, patch)
  canvasNodes.setNodes(canvasNodes.fromSegments(assemblyConfig.segments.value))
  scheduleAutoSave()
}
function handleUpdateNode(nodeIndex: number, patch: Partial<CanvasNode>) {
  const node = canvasNodes.nodes.value[nodeIndex]
  if (node) { Object.assign(node, patch); syncNodesToSegments(); scheduleAutoSave() }
}

function handleEditSegment(node: CanvasNode) {
  if (node.segmentIndex == null) return
  const seg = assemblyConfig.segments.value[node.segmentIndex]
  if (!seg?.filePath) { ElMessage.warning('No file to edit'); return }
  emit('open-editor', { id: `segment-${node.segmentIndex}`, title: seg.name, type: 'segment', segmentIndex: node.segmentIndex, filePath: seg.filePath })
}

async function handleEditHeaderFooter(node: CanvasNode) {
  const nodeType = node.type as 'header' | 'footer'
  let filePath = nodeType === 'header' ? node.headerFilePath : node.footerFilePath
  const nodeIdx = canvasNodes.nodes.value.indexOf(node)
  let segIdx = 0
  for (let i = nodeIdx + 1; i < canvasNodes.nodes.value.length; i++) {
    const n = canvasNodes.nodes.value[i]
    if (n.type === 'content' && n.segmentIndex != null) { segIdx = n.segmentIndex; break }
  }
  if (!filePath) {
    try {
      const result = await createBlankHeaderFooter(store.templateId, nodeType)
      filePath = result.filePath
      if (nodeIdx >= 0) {
        if (nodeType === 'header') canvasNodes.nodes.value[nodeIdx].headerFilePath = filePath
        else canvasNodes.nodes.value[nodeIdx].footerFilePath = filePath
        syncNodesToSegments()
      }
    } catch (e: any) { ElMessage.error(e.response?.data?.message || e.message); return }
  }
  const title = nodeType === 'header' ? t('workspace.design.canvas.header') : t('workspace.design.canvas.footer')
  emit('open-editor', { id: `${nodeType}-${segIdx}`, title, type: nodeType, segmentIndex: segIdx, filePath: filePath || '' })
}

function handlePublishSegment(node: CanvasNode) {
  if (node.segmentIndex == null) return
  const seg = assemblyConfig.segments.value[node.segmentIndex]
  if (!seg?.name) return
  activeSegmentName.value = seg.name
  // Quick publish without opening dialog
  publishSegment(store.templateId, seg.name)
    .then(() => ElMessage.success(t('workspace.segment.publishSuccess')))
    .catch((e: any) => ElMessage.error(e.response?.data?.message || e.message))
}

function handleShowSegmentVersions(node: CanvasNode) {
  if (node.segmentIndex == null) return
  const seg = assemblyConfig.segments.value[node.segmentIndex]
  if (!seg?.name) return
  activeSegmentName.value = seg.name
  versionDialogVisible.value = true
}

function handleVersionPublished() {
  // Refresh after publish
}

function handleVersionRolledBack() {
  // Refresh assembly config after rollback
  store.refreshAssemblyConfig()
}

// ── Auto-save with debounce ──
let autoSaveTimer: ReturnType<typeof setTimeout> | null = null
const dirty = ref(false)

function scheduleAutoSave() {
  dirty.value = true
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  autoSaveTimer = setTimeout(() => doAutoSave(), 2000)
}

async function doAutoSave() {
  if (!dirty.value || saving.value) return
  saving.value = true
  try {
    await updateAssemblyConfig(store.templateId, { segments: assemblyConfig.segments.value })
    dirty.value = false
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.saveFailed'))
  } finally { saving.value = false }
}

onBeforeUnmount(() => {
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  if (dirty.value) doAutoSave()
})

function handleUndo() { assemblyConfig.undo(); canvasNodes.setNodes(canvasNodes.fromSegments(assemblyConfig.segments.value)); scheduleAutoSave() }
function handleRedo() { assemblyConfig.redo(); canvasNodes.setNodes(canvasNodes.fromSegments(assemblyConfig.segments.value)); scheduleAutoSave() }
</script>

<style scoped>
.segment-canvas { display: flex; flex-direction: column; height: 100%; }
.canvas-body { flex: 1; display: flex; min-height: 0; overflow: hidden; }
.panel-col { width: 200px; flex-shrink: 0; }
.canvas-col { flex: 1; display: flex; flex-direction: column; min-width: 0; overflow: hidden; }
</style>
