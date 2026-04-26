<template>
  <div class="canvas-area">
    <div v-if="nodes.length === 0" class="canvas-empty">
      <el-empty :description="t('workspace.design.canvas.emptyHint')" />
    </div>

    <div v-else ref="canvasListRef" class="canvas-list">
      <div
        v-for="(node, index) in nodes"
        :key="node.id"
        class="canvas-node-wrapper"
        :data-node-id="node.id"
        :data-node-type="node.type"
      >
        <!-- ═══ Content Segment Card ═══ -->
        <div v-if="node.type === 'content'" class="segment-card" :class="{ 'is-disabled': !getSegment(node)?.enabled }">
          <div class="segment-type-bar" :style="{ backgroundColor: getSegmentColor(node) }" />

          <!-- Row 1: identity + actions -->
          <div class="card-row card-row-top">
            <div class="card-identity">
              <el-tag size="small" :color="getSegmentColor(node)" effect="dark" class="type-tag">
                {{ getSegmentTypeLabel(node) }}
              </el-tag>
              <span class="segment-name">{{ getSegment(node)?.name || '—' }}</span>
            </div>
            <div v-if="!readonly" class="card-actions">
              <el-button size="small" type="success" plain @click.stop="$emit('publish-segment', node)">
                {{ t('workspace.segment.publish') }}
              </el-button>
              <el-button size="small" plain @click.stop="$emit('show-segment-versions', node)">
                {{ t('workspace.segment.versions') }}
              </el-button>
              <el-button size="small" type="primary" @click.stop="$emit('edit-segment', node)">
                {{ t('common.edit') }}
              </el-button>
              <el-popconfirm :title="t('workspace.segment.removeConfirm')" @confirm="$emit('remove-node', index)">
                <template #reference>
                  <el-button size="small" type="danger" plain @click.stop>{{ t('common.delete') }}</el-button>
                </template>
              </el-popconfirm>
            </div>
            <div v-else class="card-actions">
              <el-button size="small" plain @click.stop="$emit('show-segment-versions', node)">
                {{ t('workspace.segment.versions') }}
              </el-button>
            </div>
          </div>

          <!-- Row 2: config toggles -->
          <div v-if="!readonly" class="card-row card-row-config">
            <el-checkbox
              :model-value="getSegment(node)?.enabled ?? true"
              @change="(val: string | number | boolean) => updateField(node, 'enabled', val)"
              @click.stop
            >{{ t('workspace.segment.enabled') }}</el-checkbox>
            <el-checkbox
              :model-value="getSegment(node)?.pageBreakBefore ?? false"
              @change="(val: string | number | boolean) => updateField(node, 'pageBreakBefore', val)"
              @click.stop
            >{{ t('workspace.segment.pageBreakBefore') }}</el-checkbox>
          </div>
        </div>

        <!-- ═══ Control Node Card (Header / Footer / Page Number) ═══ -->
        <div v-else class="control-node-card" :class="`control-node--${node.type}`">
          <el-icon :size="14"><component :is="controlNodeIcon(node.type)" /></el-icon>
          <span class="control-label">{{ controlNodeLabel(node.type) }}</span>

          <!-- Page Number config -->
          <template v-if="node.type === 'page-number'">
            <el-select
              v-if="!readonly"
              :model-value="node.pageNumberFormat || 'ARABIC'"
              size="small"
              style="width: 110px; margin-left: auto;"
              @change="(val: string) => $emit('update-node', index, { pageNumberFormat: val as any })"
              @click.stop
            >
              <el-option value="ARABIC" :label="t('workspace.design.canvas.pageNumberFormat.arabic')" />
              <el-option value="ROMAN" :label="t('workspace.design.canvas.pageNumberFormat.roman')" />
              <el-option value="ALPHA" :label="t('workspace.design.canvas.pageNumberFormat.alpha')" />
            </el-select>
            <el-checkbox
              v-if="!readonly"
              :model-value="node.pageNumberStart === 1"
              size="small"
              :label="t('workspace.design.canvas.restartPageNumber')"
              @change="(val: string | number | boolean) => $emit('update-node', index, { pageNumberStart: val ? 1 : undefined })"
              @click.stop
            />
          </template>

          <!-- Header/Footer edit button -->
          <el-button
            v-if="(node.type === 'header' || node.type === 'footer') && !readonly"
            size="small" text type="primary" style="margin-left: auto;"
            @click.stop="$emit('edit-header-footer', node)"
          >{{ t('workspace.design.canvas.editHeaderFooter') }}</el-button>

          <!-- Delete -->
          <el-button v-if="!readonly" size="small" text type="danger" class="control-delete-btn" @click.stop="$emit('remove-node', index)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <!-- Naming dialog -->
    <el-dialog v-model="namingDialogVisible" :title="t('workspace.segment.createNew')" width="400px" :close-on-click-modal="false" @closed="resetNamingDialog">
      <el-form @submit.prevent="confirmCreateSegment">
        <el-form-item :error="namingError">
          <el-input ref="namingInputRef" v-model="newSegmentName" :placeholder="t('workspace.segment.namePlaceholder')" @keyup.enter="confirmCreateSegment" />
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
import { Delete, Postcard, Tickets, Odometer } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import type { CanvasNode } from '@/composables/useCanvasNodes'
import type { AssemblySegmentEntry } from '@/types/segment'
import { createBlankSegment } from '@/api/composite-templates'

const props = defineProps<{
  nodes: CanvasNode[]
  segments: AssemblySegmentEntry[]
  templateId: number
  readonly: boolean
}>()

const emit = defineEmits<{
  (e: 'add-content-node', entry: AssemblySegmentEntry, insertIndex: number): void
  (e: 'add-control-node', type: CanvasNode['type'], insertIndex: number): void
  (e: 'remove-node', index: number): void
  (e: 'reorder', nodes: CanvasNode[]): void
  (e: 'update-segment', segmentIndex: number, patch: Partial<AssemblySegmentEntry>): void
  (e: 'update-node', nodeIndex: number, patch: Partial<CanvasNode>): void
  (e: 'edit-segment', node: CanvasNode): void
  (e: 'edit-header-footer', node: CanvasNode): void
  (e: 'publish-segment', node: CanvasNode): void
  (e: 'show-segment-versions', node: CanvasNode): void
}>()

const { t } = useI18n()
const canvasListRef = ref<HTMLElement | null>(null)
const namingInputRef = ref<any>(null)
let sortableInstance: Sortable | null = null

const namingDialogVisible = ref(false)
const newSegmentName = ref('')
const namingError = ref('')
const creating = ref(false)
const pendingSegmentType = ref('')
const pendingInsertIndex = ref(0)

const segmentColorMap: Record<string, string> = {
  COVER: '#409EFF', TOC: '#67C23A', CHAPTER: '#E6A23C', TABLE: '#F56C6C',
  SIGNATURE: '#909399', LEGAL: '#8B5CF6', APPENDIX: '#06B6D4',
}

function getSegment(node: CanvasNode): AssemblySegmentEntry | undefined {
  if (node.segmentIndex != null && node.segmentIndex < props.segments.length) return props.segments[node.segmentIndex]
  return undefined
}
function getSegmentColor(node: CanvasNode): string { return segmentColorMap[getSegment(node)?.segmentType || ''] || '#C0C4CC' }
function getSegmentTypeLabel(node: CanvasNode): string {
  const st = getSegment(node)?.segmentType
  return st ? t(`workspace.design.segmentType.${st}`) : ''
}
function updateField(node: CanvasNode, field: string, value: any) {
  if (node.segmentIndex != null) emit('update-segment', node.segmentIndex, { [field]: value })
}

function controlNodeIcon(type: string) {
  const map: Record<string, any> = { 'header': Postcard, 'footer': Tickets, 'page-number': Odometer }
  return map[type] || Postcard
}
function controlNodeLabel(type: string): string {
  const map: Record<string, string> = { 'header': 'workspace.design.canvas.header', 'footer': 'workspace.design.canvas.footer', 'page-number': 'workspace.design.canvas.pageNumber' }
  return t(map[type] || type)
}

// ── Drag from ComponentPanel ──
function handleDragAdd(evt: Sortable.SortableEvent) {
  const itemEl = evt.item
  const category = itemEl.getAttribute('data-category')
  const type = itemEl.getAttribute('data-type')
  const insertIndex = evt.newIndex ?? props.nodes.length
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

async function confirmCreateSegment() {
  const name = newSegmentName.value.trim()
  if (!name) { namingError.value = t('workspace.design.canvas.segmentNameRequired'); return }
  if (props.segments.some(s => s.name === name)) { namingError.value = t('workspace.design.canvas.segmentNameDuplicate'); return }
  namingError.value = ''
  creating.value = true
  try {
    const entry = await createBlankSegment(props.templateId, name, pendingSegmentType.value)
    emit('add-content-node', entry, pendingInsertIndex.value)
    namingDialogVisible.value = false
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  } finally { creating.value = false }
}
function resetNamingDialog() { newSegmentName.value = ''; namingError.value = '' }

// ── Sortable ──
function initSortable() {
  if (!canvasListRef.value || props.readonly) return
  sortableInstance = Sortable.create(canvasListRef.value, {
    group: { name: 'canvas', pull: false, put: true },
    animation: 150,
    ghostClass: 'drag-ghost',
    onAdd: handleDragAdd,
    onEnd: (evt: Sortable.SortableEvent) => {
      if (evt.oldIndex == null || evt.newIndex == null || evt.oldIndex === evt.newIndex) return
      if (evt.from !== evt.to) return
      const newNodes = [...props.nodes]
      const [moved] = newNodes.splice(evt.oldIndex, 1)
      newNodes.splice(evt.newIndex, 0, moved)
      emit('reorder', newNodes)
    },
  })
}

watch(() => props.readonly, (val) => {
  if (val) { sortableInstance?.destroy(); sortableInstance = null }
  else nextTick(() => initSortable())
})
onMounted(() => { if (!props.readonly) nextTick(() => initSortable()) })
onBeforeUnmount(() => { sortableInstance?.destroy() })
</script>

<style scoped>
.canvas-area { flex: 1; overflow-y: auto; padding: 16px; min-height: 200px; }
.canvas-empty { display: flex; align-items: center; justify-content: center; height: 100%; min-height: 300px; }
.canvas-list { display: flex; flex-direction: column; gap: 10px; }

/* ── Content Segment Card ── */
.segment-card {
  position: relative;
  background: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  padding: 16px 18px 14px 22px;
  cursor: grab;
  transition: box-shadow 0.15s, border-color 0.15s;
  overflow: hidden;
}
.segment-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.07); border-color: var(--el-color-primary-light-5); }
.segment-card:active { cursor: grabbing; }
.segment-card.is-disabled { opacity: 0.45; }

.segment-type-bar { position: absolute; left: 0; top: 0; bottom: 0; width: 5px; border-radius: 10px 0 0 10px; }

.card-row { display: flex; align-items: center; gap: 10px; }
.card-row-top { justify-content: space-between; }
.card-row-config { margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--el-border-color-extra-light); gap: 16px; flex-wrap: wrap; }

.card-identity { display: flex; align-items: center; gap: 10px; min-width: 0; }
.type-tag { border: none; font-size: 11px; }
.segment-name { font-size: 15px; font-weight: 500; color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.card-actions { display: flex; gap: 8px; flex-shrink: 0; }

/* ── Control Node Card ── */
.control-node-card {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 14px; min-height: 36px;
  background: var(--el-fill-color-light); border: 1px dashed var(--el-border-color); border-radius: 8px;
  font-size: 13px; color: var(--el-text-color-secondary); cursor: grab;
}
.control-node-card:active { cursor: grabbing; }
.control-node--header { border-left: 3px solid var(--el-color-primary); }
.control-node--footer { border-left: 3px solid var(--el-color-success); }
.control-node--page-number { border-left: 3px solid var(--el-color-warning); }
.control-label { font-weight: 500; }
.control-delete-btn { margin-left: 4px; flex-shrink: 0; }

.drag-ghost { opacity: 0.4; background: var(--el-color-primary-light-9); }
.canvas-node-wrapper { position: relative; }
</style>
