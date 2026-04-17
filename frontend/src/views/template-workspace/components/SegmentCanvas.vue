<template>
  <div class="segment-canvas">
    <!-- Canvas toolbar -->
    <div class="canvas-toolbar">
      <div class="toolbar-left">
        <el-button
          v-if="!readonly"
          type="primary"
          :loading="saving"
          @click="handleSave"
        >
          {{ t('common.save') }}
        </el-button>
        <el-button
          v-if="!readonly"
          :disabled="!assemblyConfig.canUndo.value"
          @click="handleUndo"
        >
          {{ t('assembly.undo') }}
        </el-button>
        <el-button
          v-if="!readonly"
          :disabled="!assemblyConfig.canRedo.value"
          @click="handleRedo"
        >
          {{ t('assembly.redo') }}
        </el-button>
      </div>
    </div>

    <!-- Main layout: ComponentPanel + CanvasArea -->
    <div class="canvas-body">
      <div class="panel-col">
        <ComponentPanel :readonly="readonly" />
      </div>
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
          @edit-header-footer="handleEditHeaderFooter"
        />
      </div>
    </div>

    <!-- ControlNodeEditor dialog -->
    <ControlNodeEditor
      :visible="editorVisible"
      :node-type="editorNodeType"
      :file-path="editorFilePath"
      :template-id="store.templateId"
      :readonly="readonly"
      :segment-index="editorSegmentIndex"
      @update:visible="editorVisible = $event"
      @saved="onEditorSaved"
    />
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
import ControlNodeEditor from './ControlNodeEditor.vue'

defineProps<{
  readonly: boolean
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

const assemblyConfig = useAssemblyConfig()
const canvasNodes = useCanvasNodes()
const saving = ref(false)

// ControlNodeEditor state
const editorVisible = ref(false)
const editorNodeType = ref<'header' | 'footer'>('header')
const editorFilePath = ref('')
const editorSegmentIndex = ref<number | undefined>(undefined)

// ── Initialize from store ──

function syncFromStore() {
  const segments = store.assemblyConfig?.segments ?? []
  assemblyConfig.deserialize({ segments })
  const nodes = canvasNodes.fromSegments(segments)
  canvasNodes.setNodes(nodes)
}

onMounted(() => {
  syncFromStore()
})

watch(() => store.assemblyConfig, () => {
  syncFromStore()
}, { deep: true })

// ── Sync canvas nodes back to segments ──

function syncNodesToSegments() {
  const newSegments = canvasNodes.toSegments(canvasNodes.nodes.value, assemblyConfig.segments.value)
  assemblyConfig.setSegments(newSegments)
}

// ── Event handlers ──

function handleAddContentNode(entry: AssemblySegmentEntry, _insertIndex: number) {
  // Add segment to assemblyConfig
  assemblyConfig.addSegment(entry)
  // Rebuild canvas nodes from updated segments
  const nodes = canvasNodes.fromSegments(assemblyConfig.segments.value)
  canvasNodes.setNodes(nodes)
}

function handleAddControlNode(type: CanvasNode['type'], insertIndex: number) {
  const nodeId = canvasNodes.generateNodeId()
  const node: CanvasNode = { id: nodeId, type }
  if (type === 'page-number') {
    node.pageNumberFormat = 'ARABIC'
  }
  canvasNodes.addNode(node, insertIndex)
  syncNodesToSegments()
}

function handleRemoveNode(index: number) {
  const node = canvasNodes.nodes.value[index]
  if (!node) return

  // Task 2.6: Control node deletion logic
  if (node.type === 'page-break') {
    // Restore pageBreakBefore=false on the next content segment
    // This is handled automatically by syncNodesToSegments after removal
  } else if (node.type === 'header' || node.type === 'footer') {
    // After removal, subsequent segments will inherit from previous same-type node
    // This is handled automatically by toSegments
  }

  canvasNodes.removeNode(index)
  syncNodesToSegments()
}

function handleReorder(newNodes: CanvasNode[]) {
  canvasNodes.setNodes(newNodes)
  syncNodesToSegments()
}

function handleUpdateSegment(segmentIndex: number, patch: Partial<AssemblySegmentEntry>) {
  assemblyConfig.updateSegment(segmentIndex, patch)
  // Rebuild canvas nodes to reflect changes
  const nodes = canvasNodes.fromSegments(assemblyConfig.segments.value)
  canvasNodes.setNodes(nodes)
}

function handleUpdateNode(nodeIndex: number, patch: Partial<CanvasNode>) {
  const node = canvasNodes.nodes.value[nodeIndex]
  if (!node) return
  Object.assign(node, patch)
  syncNodesToSegments()
}

async function handleEditHeaderFooter(node: CanvasNode) {
  const nodeType = node.type as 'header' | 'footer'
  let filePath = nodeType === 'header' ? node.headerFilePath : node.footerFilePath

  // If no file path yet, create a blank header/footer
  if (!filePath) {
    try {
      const result = await createBlankHeaderFooter(store.templateId, nodeType)
      filePath = result.filePath
      // Update the node with the new file path
      const idx = canvasNodes.nodes.value.indexOf(node)
      if (idx >= 0) {
        if (nodeType === 'header') {
          canvasNodes.nodes.value[idx].headerFilePath = filePath
        } else {
          canvasNodes.nodes.value[idx].footerFilePath = filePath
        }
        syncNodesToSegments()
      }
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
      return
    }
  }

  editorNodeType.value = nodeType
  editorFilePath.value = filePath || ''
  // Find the segment index that this header/footer affects
  const nodeIdx = canvasNodes.nodes.value.indexOf(node)
  const range = canvasNodes.getAffectedRange(nodeIdx)
  if (range.start >= 0 && range.start < canvasNodes.nodes.value.length) {
    const affectedNode = canvasNodes.nodes.value[range.start]
    editorSegmentIndex.value = affectedNode?.segmentIndex
  }
  editorVisible.value = true
}

function onEditorSaved() {
  // Refresh after editor save
  store.refreshAssemblyConfig()
}

// ── Save / Undo / Redo ──

async function handleSave() {
  saving.value = true
  try {
    await updateAssemblyConfig(store.templateId, { segments: assemblyConfig.segments.value })
    await store.refreshAssemblyConfig()
    ElMessage.success(t('message.saveSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.saveFailed'))
  } finally {
    saving.value = false
  }
}

function handleUndo() {
  assemblyConfig.undo()
  const nodes = canvasNodes.fromSegments(assemblyConfig.segments.value)
  canvasNodes.setNodes(nodes)
}

function handleRedo() {
  assemblyConfig.redo()
  const nodes = canvasNodes.fromSegments(assemblyConfig.segments.value)
  canvasNodes.setNodes(nodes)
}
</script>

<style scoped>
.segment-canvas {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.canvas-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

.toolbar-left {
  display: flex;
  gap: 8px;
}

.canvas-body {
  flex: 1;
  display: flex;
  min-height: 0;
  overflow: hidden;
}

.panel-col {
  width: 25%;
  min-width: 180px;
  max-width: 280px;
  flex-shrink: 0;
}

.canvas-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}
</style>
