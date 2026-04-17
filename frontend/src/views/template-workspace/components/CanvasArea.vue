<template>
  <div class="canvas-area">
    <!-- Empty state -->
    <div v-if="nodes.length === 0" class="canvas-empty">
      <el-empty :description="t('workspace.design.canvas.emptyHint')" />
    </div>

    <!-- Canvas node list -->
    <div v-else ref="canvasListRef" class="canvas-list">
      <div
        v-for="(node, index) in nodes"
        :key="node.id"
        class="canvas-node-wrapper"
        :data-node-id="node.id"
        :data-node-type="node.type"
      >
        <!-- Content Segment Card -->
        <div
          v-if="node.type === 'content'"
          class="content-segment-card"
          :class="{ 'is-expanded': expandedNodeId === node.id, 'is-disabled': !getSegment(node)?.enabled }"
          @click="toggleExpand(node.id)"
        >
          <div class="segment-type-bar" :style="{ backgroundColor: getSegmentColor(node) }" />
          <div class="segment-main">
            <div class="segment-header">
              <span class="segment-name">{{ getSegment(node)?.name || '—' }}</span>
              <span class="segment-type-label">{{ getSegmentTypeLabel(node) }}</span>
              <div class="segment-influence-icons">
                <el-icon v-if="hasHeaderInfluence(index)" class="influence-icon influence-header" :title="t('workspace.design.canvas.header')"><Postcard /></el-icon>
                <el-icon v-if="hasPageNumberInfluence(index)" class="influence-icon influence-page-number"><Odometer /></el-icon>
                <el-icon v-if="hasFooterInfluence(index)" class="influence-icon influence-footer" :title="t('workspace.design.canvas.footer')"><Tickets /></el-icon>
              </div>
            </div>
            <!-- Expanded config panel (Task 2.5) -->
            <div v-if="expandedNodeId === node.id && !readonly" class="segment-config" @click.stop>
              <div class="config-row">
                <label>{{ t('common.name') }}</label>
                <el-input
                  :model-value="getSegment(node)?.name"
                  size="small"
                  @change="(val: string) => updateSegmentField(node, 'name', val)"
                />
              </div>
              <div class="config-row">
                <label>{{ t('workspace.segment.enabled') }}</label>
                <el-switch
                  :model-value="getSegment(node)?.enabled"
                  size="small"
                  @change="(val: string | number | boolean) => updateSegmentField(node, 'enabled', val)"
                />
              </div>
              <div class="config-row">
                <label>{{ t('workspace.segment.conditionExpr') }}</label>
                <el-input
                  :model-value="getSegment(node)?.conditionExpression || ''"
                  size="small"
                  :placeholder="t('assembly.conditionPlaceholder')"
                  @change="(val: string) => updateSegmentField(node, 'conditionExpression', val || null)"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- Control Node Cards -->
        <div
          v-else
          class="control-node-card"
          :class="`control-node--${node.type}`"
        >
          <el-icon :size="14"><component :is="controlNodeIcon(node.type)" /></el-icon>
          <span class="control-label">{{ controlNodeLabel(node.type) }}</span>

          <!-- Page Number config (Task 2.7) -->
          <template v-if="node.type === 'page-number'">
            <el-select
              v-if="!readonly"
              :model-value="node.pageNumberFormat || 'ARABIC'"
              size="small"
              style="width: 100px; margin-left: auto;"
              @change="(val: string) => updatePageNumberFormat(index, val as any)"
              @click.stop
            >
              <el-option value="ARABIC" :label="t('workspace.design.canvas.pageNumberFormat.arabic')" />
              <el-option value="ROMAN" :label="t('workspace.design.canvas.pageNumberFormat.roman')" />
              <el-option value="ALPHA" :label="t('workspace.design.canvas.pageNumberFormat.alpha')" />
            </el-select>
            <span v-else class="page-number-badge">{{ pageNumberFormatLabel(node.pageNumberFormat) }}</span>
            <el-checkbox
              v-if="!readonly"
              :model-value="node.pageNumberStart === 1"
              size="small"
              :label="t('workspace.design.canvas.restartPageNumber')"
              @change="(val: string | number | boolean) => updatePageNumberStart(index, val ? 1 : null)"
              @click.stop
            />
          </template>

          <!-- Header/Footer edit button -->
          <el-button
            v-if="(node.type === 'header' || node.type === 'footer') && !readonly"
            size="small"
            text
            type="primary"
            style="margin-left: auto;"
            @click.stop="$emit('edit-header-footer', node)"
          >
            {{ t('workspace.design.canvas.editHeaderFooter') }}
          </el-button>

          <!-- Delete button -->
          <el-button
            v-if="!readonly"
            size="small"
            text
            type="danger"
            class="control-delete-btn"
            @click.stop="handleDeleteNode(index)"
          >
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <!-- Naming dialog for new content segments -->
    <el-dialog
      v-model="namingDialogVisible"
      :title="t('workspace.segment.createNew')"
      width="400px"
      :close-on-click-modal="false"
      @closed="resetNamingDialog"
    >
      <el-form @submit.prevent="confirmCreateSegment">
        <el-form-item :error="namingError">
          <el-input
            ref="namingInputRef"
            v-model="newSegmentName"
            :placeholder="t('workspace.segment.namePlaceholder')"
            @keyup.enter="confirmCreateSegment"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="namingDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="confirmCreateSegment">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Delete, DArrowRight, Postcard, Tickets, Odometer } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import type { CanvasNode } from '@/composables/useCanvasNodes'
import type { AssemblySegmentEntry, PageNumberFormat } from '@/types/segment'
import { createBlankSegment } from '@/api/composite-templates'

const props = defineProps<{
  nodes: CanvasNode[]
  segments: AssemblySegmentEntry[]
  templateId: number
  readonly: boolean
}>()

const emit = defineEmits<{
  (e: 'update:nodes', nodes: CanvasNode[]): void
  (e: 'add-content-node', entry: AssemblySegmentEntry, insertIndex: number): void
  (e: 'add-control-node', type: CanvasNode['type'], insertIndex: number): void
  (e: 'remove-node', index: number): void
  (e: 'reorder', nodes: CanvasNode[]): void
  (e: 'update-segment', segmentIndex: number, patch: Partial<AssemblySegmentEntry>): void
  (e: 'update-node', nodeIndex: number, patch: Partial<CanvasNode>): void
  (e: 'edit-header-footer', node: CanvasNode): void
}>()

const { t } = useI18n()

const canvasListRef = ref<HTMLElement | null>(null)
const namingInputRef = ref<any>(null)
let sortableInstance: Sortable | null = null

// Expand state
const expandedNodeId = ref<string | null>(null)

// Naming dialog state
const namingDialogVisible = ref(false)
const newSegmentName = ref('')
const namingError = ref('')
const creating = ref(false)
const pendingSegmentType = ref('')
const pendingInsertIndex = ref(0)

// Segment color map
const segmentColorMap: Record<string, string> = {
  COVER: '#409EFF',
  TOC: '#67C23A',
  CHAPTER: '#E6A23C',
  TABLE: '#F56C6C',
  SIGNATURE: '#909399',
  LEGAL: '#8B5CF6',
  APPENDIX: '#06B6D4',
}

function getSegment(node: CanvasNode): AssemblySegmentEntry | undefined {
  if (node.segmentIndex != null && node.segmentIndex < props.segments.length) {
    return props.segments[node.segmentIndex]
  }
  return undefined
}

function getSegmentColor(node: CanvasNode): string {
  const seg = getSegment(node)
  return segmentColorMap[seg?.segmentType || ''] || '#C0C4CC'
}

function getSegmentTypeLabel(node: CanvasNode): string {
  const seg = getSegment(node)
  const st = seg?.segmentType
  if (st) {
    const key = `workspace.design.segmentType.${st}`
    return t(key)
  }
  return ''
}

function toggleExpand(nodeId: string) {
  expandedNodeId.value = expandedNodeId.value === nodeId ? null : nodeId
}

function controlNodeIcon(type: string) {
  const map: Record<string, any> = {
    'page-break': DArrowRight,
    'header': Postcard,
    'footer': Tickets,
    'page-number': Odometer,
  }
  return map[type] || DArrowRight
}

function controlNodeLabel(type: string): string {
  const map: Record<string, string> = {
    'page-break': 'workspace.design.canvas.pageBreak',
    'header': 'workspace.design.canvas.header',
    'footer': 'workspace.design.canvas.footer',
    'page-number': 'workspace.design.canvas.pageNumber',
  }
  return t(map[type] || type)
}

function pageNumberFormatLabel(format?: PageNumberFormat): string {
  const map: Record<string, string> = {
    ARABIC: 'workspace.design.canvas.pageNumberFormat.arabic',
    ROMAN: 'workspace.design.canvas.pageNumberFormat.roman',
    ALPHA: 'workspace.design.canvas.pageNumberFormat.alpha',
  }
  return t(map[format || 'ARABIC'])
}

// ── Influence range markers (Task 2.4) ──

function hasHeaderInfluence(nodeIndex: number): boolean {
  const node = props.nodes[nodeIndex]
  if (node.type !== 'content') return false
  for (let i = nodeIndex - 1; i >= 0; i--) {
    if (props.nodes[i].type === 'header') return true
    if (props.nodes[i].type === 'content') continue
  }
  return false
}

function hasFooterInfluence(nodeIndex: number): boolean {
  const node = props.nodes[nodeIndex]
  if (node.type !== 'content') return false
  for (let i = nodeIndex - 1; i >= 0; i--) {
    if (props.nodes[i].type === 'footer') return true
    if (props.nodes[i].type === 'content') continue
  }
  return false
}

function hasPageNumberInfluence(nodeIndex: number): boolean {
  const node = props.nodes[nodeIndex]
  if (node.type !== 'content') return false
  for (let i = nodeIndex - 1; i >= 0; i--) {
    if (props.nodes[i].type === 'page-number') return true
    if (props.nodes[i].type === 'content') continue
  }
  return false
}

// ── Segment field update (Task 2.5) ──

function updateSegmentField(node: CanvasNode, field: string, value: any) {
  if (node.segmentIndex != null) {
    emit('update-segment', node.segmentIndex, { [field]: value })
  }
}

// ── Page number config (Task 2.7) ──

function updatePageNumberFormat(nodeIndex: number, format: PageNumberFormat) {
  emit('update-node', nodeIndex, { pageNumberFormat: format })
}

function updatePageNumberStart(nodeIndex: number, start: number | null) {
  emit('update-node', nodeIndex, { pageNumberStart: start ?? undefined })
}

// ── Delete control node (Task 2.6) ──

function handleDeleteNode(index: number) {
  emit('remove-node', index)
}

// ── Drag receive from ComponentPanel (Task 2.2) ──

function handleDragAdd(evt: Sortable.SortableEvent) {
  const itemEl = evt.item
  const category = itemEl.getAttribute('data-category')
  const type = itemEl.getAttribute('data-type')
  const insertIndex = evt.newIndex ?? props.nodes.length

  // Remove the cloned DOM element — we manage rendering via Vue
  itemEl.parentNode?.removeChild(itemEl)

  if (category === 'content' && type) {
    pendingSegmentType.value = type
    pendingInsertIndex.value = insertIndex
    namingDialogVisible.value = true
    nextTick(() => namingInputRef.value?.focus())
  } else if (category === 'control' && type) {
    emit('add-control-node', type as CanvasNode['type'], insertIndex)
  }
}

// ── Naming dialog (Task 2.2) ──

async function confirmCreateSegment() {
  const name = newSegmentName.value.trim()
  if (!name) {
    namingError.value = t('workspace.design.canvas.segmentNameRequired')
    return
  }
  if (props.segments.some(s => s.name === name)) {
    namingError.value = t('workspace.design.canvas.segmentNameDuplicate')
    return
  }
  namingError.value = ''
  creating.value = true
  try {
    const entry = await createBlankSegment(props.templateId, name, pendingSegmentType.value)
    emit('add-content-node', entry, pendingInsertIndex.value)
    namingDialogVisible.value = false
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  } finally {
    creating.value = false
  }
}

function resetNamingDialog() {
  newSegmentName.value = ''
  namingError.value = ''
  pendingSegmentType.value = ''
  pendingInsertIndex.value = 0
}

// ── Sortable for canvas reorder (Task 2.3) ──

function initSortable() {
  if (!canvasListRef.value || props.readonly) return
  sortableInstance = Sortable.create(canvasListRef.value, {
    group: { name: 'canvas', pull: false, put: true },
    animation: 150,
    ghostClass: 'drag-source-active',
    dragClass: 'drag-source-dragging',
    onAdd: handleDragAdd,
    onEnd: (evt: Sortable.SortableEvent) => {
      if (evt.oldIndex == null || evt.newIndex == null) return
      if (evt.oldIndex === evt.newIndex) return
      // Only handle internal reorder (not cross-panel adds)
      if (evt.from !== evt.to) return
      const newNodes = [...props.nodes]
      const [moved] = newNodes.splice(evt.oldIndex, 1)
      newNodes.splice(evt.newIndex, 0, moved)
      emit('reorder', newNodes)
    },
  })
}

watch(() => props.readonly, (val) => {
  if (val) {
    sortableInstance?.destroy()
    sortableInstance = null
  } else {
    nextTick(() => initSortable())
  }
})

onMounted(() => {
  if (!props.readonly) {
    nextTick(() => initSortable())
  }
})

onBeforeUnmount(() => {
  sortableInstance?.destroy()
})
</script>

<style scoped>
.canvas-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  min-height: 200px;
}

.canvas-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 300px;
}

.canvas-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* ── Content Segment Card ── */
.content-segment-card {
  position: relative;
  background: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 14px 16px 14px 20px;
  min-height: 64px;
  cursor: pointer;
  transition: box-shadow 0.15s ease, border-color 0.15s ease;
  overflow: hidden;
}

.content-segment-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border-color: var(--el-color-primary-light-5);
}

.content-segment-card.is-expanded {
  border-color: var(--el-color-primary);
}

.content-segment-card.is-disabled {
  opacity: 0.5;
}

.segment-type-bar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 5px;
  border-radius: 8px 0 0 8px;
}

.segment-main {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.segment-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.segment-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.segment-type-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  padding: 2px 8px;
  border-radius: 4px;
}

.segment-influence-icons {
  margin-left: auto;
  display: flex;
  gap: 6px;
  align-items: center;
}

.influence-icon {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.influence-header {
  color: var(--el-color-primary);
}

.influence-footer {
  color: var(--el-color-success);
}

.influence-page-number {
  color: var(--el-color-warning);
}

/* ── Segment Config Panel (expanded) ── */
.segment-config {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.config-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.config-row label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  min-width: 80px;
  flex-shrink: 0;
}

/* ── Control Node Card ── */
.control-node-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  min-height: 24px;
  background: var(--el-fill-color-light);
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.control-node--header {
  border-top: 2px solid var(--el-color-primary);
}

.control-node--footer {
  border-bottom: 2px solid var(--el-color-success);
}

.control-label {
  font-weight: 500;
}

.control-delete-btn {
  margin-left: 4px;
  flex-shrink: 0;
}

.page-number-badge {
  font-size: 11px;
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
  padding: 1px 6px;
  border-radius: 3px;
  margin-left: auto;
}

/* ── Drag styles ── */
.drag-source-active {
  opacity: 0.5;
  transition: opacity 0.15s ease;
}

.drag-source-dragging {
  opacity: 0.8;
}

.canvas-node-wrapper {
  position: relative;
}
</style>
