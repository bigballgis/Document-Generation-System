<template>
  <div class="segment-canvas">
    <!-- Canvas toolbar (Save/Undo/Redo) -->
    <div class="canvas-toolbar">
      <div class="toolbar-left">
        <el-button v-if="!readonly" type="primary" size="small" :loading="saving" @click="handleSave">{{ t('common.save') }}</el-button>
        <el-button v-if="!readonly" size="small" :disabled="!assemblyConfig.canUndo.value" @click="handleUndo">{{ t('assembly.undo') }}</el-button>
        <el-button v-if="!readonly" size="small" :disabled="!assemblyConfig.canRedo.value" @click="handleRedo">{{ t('assembly.redo') }}</el-button>
      </div>
    </div>
    <div class="canvas-body">
      <div class="panel-col"><ComponentPanel :readonly="readonly" /></div>
      <div class="canvas-col">
        <CanvasArea
          :nodes="canvasNodes.nodes.value"
          :segments="assemblyConfig.segments.value"
          :template-id="store.templateId"
          :readonly="readonly"
          @add-content-node="handleAddContentNode"
          @add-control-node="handleAddControlNode"
          @remove-node="handleRemoveNode"
          @reorder="handleReorder"
          @update-segment="handleUpdateSegment"
          @update-node="handleUpdateNode"
          @edit-segment="handleEditSegment"
          @edit-header-footer="handleEditHeaderFooter"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { useAssemblyConfig } from '@/composables/useAssemblyConfig'
import { useCanvasNodes } from '@/composables/useCanvasNodes'
import { updateAssemblyConfig, createBlankHeaderFooter } from '@/api/composite-templates'
import type { CanvasNode } from '@/composables/useCanvasNodes'
import type { AssemblySegmentEntry } from '@/types/segment'
import ComponentPanel from './ComponentPanel.vue'
import CanvasArea from './CanvasArea.vue'

defineProps<{ readonly: boolean }>()

const emit = defineEmits<{
  (e: 'open-editor', info: { id: string; title: string; type: string; segmentIndex: number; filePath: string }): void
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()
const assemblyConfig = useAssemblyConfig()
const canvasNodes = useCanvasNodes()
const saving = ref(false)

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
}
function handleAddControlNode(type: CanvasNode['type'], insertIndex: number) {
  const node: CanvasNode = { id: canvasNodes.generateNodeId(), type }
  if (type === 'page-number') node.pageNumberFormat = 'ARABIC'
  canvasNodes.addNode(node, insertIndex)
  syncNodesToSegments()
}
function handleRemoveNode(index: number) { canvasNodes.removeNode(index); syncNodesToSegments() }
function handleReorder(newNodes: CanvasNode[]) { canvasNodes.setNodes(newNodes); syncNodesToSegments() }
function handleUpdateSegment(segmentIndex: number, patch: Partial<AssemblySegmentEntry>) {
  assemblyConfig.updateSegment(segmentIndex, patch)
  canvasNodes.setNodes(canvasNodes.fromSegments(assemblyConfig.segments.value))
}
function handleUpdateNode(nodeIndex: number, patch: Partial<CanvasNode>) {
  const node = canvasNodes.nodes.value[nodeIndex]
  if (node) { Object.assign(node, patch); syncNodesToSegments() }
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

async function handleSave() {
  saving.value = true
  try {
    await updateAssemblyConfig(store.templateId, { segments: assemblyConfig.segments.value })
    await store.refreshAssemblyConfig()
    ElMessage.success(t('message.saveSuccess'))
  } catch (e: any) { ElMessage.error(e.response?.data?.message || e.message || t('message.saveFailed')) }
  finally { saving.value = false }
}
function handleUndo() { assemblyConfig.undo(); canvasNodes.setNodes(canvasNodes.fromSegments(assemblyConfig.segments.value)) }
function handleRedo() { assemblyConfig.redo(); canvasNodes.setNodes(canvasNodes.fromSegments(assemblyConfig.segments.value)) }
</script>

<style scoped>
.segment-canvas { display: flex; flex-direction: column; height: 100%; }
.canvas-toolbar { display: flex; align-items: center; padding: 6px 12px; border-bottom: 1px solid var(--el-border-color-lighter); background: var(--el-bg-color); flex-shrink: 0; }
.toolbar-left { display: flex; gap: 8px; }
.canvas-body { flex: 1; display: flex; min-height: 0; overflow: hidden; }
.panel-col { width: 200px; flex-shrink: 0; }
.canvas-col { flex: 1; display: flex; flex-direction: column; min-width: 0; overflow: hidden; }
</style>
