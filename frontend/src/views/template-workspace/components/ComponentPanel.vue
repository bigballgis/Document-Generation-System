<template>
  <div class="component-panel">
    <!-- Content Segments -->
    <div class="panel-section">
      <div class="section-title">{{ t('workspace.design.canvas.contentSegments') }}</div>
      <div ref="segmentListRef" class="component-list">
        <div
          v-for="item in segmentTypes"
          :key="item.type"
          class="component-item component-item--segment"
          :data-type="item.type"
          :data-category="'content'"
        >
          <div class="type-bar" :style="{ backgroundColor: item.color }" />
          <el-icon :size="16"><Document /></el-icon>
          <span class="component-label">{{ t(item.labelKey) }}</span>
        </div>
      </div>
    </div>

    <el-divider />

    <!-- Control Nodes (Header, Footer, Page Number Rule) -->
    <div class="panel-section">
      <div class="section-title">{{ t('workspace.design.canvas.controlNodes') }}</div>
      <div ref="controlListRef" class="component-list">
        <div
          v-for="item in controlNodes"
          :key="item.type"
          class="component-item component-item--control"
          :data-type="item.type"
          :data-category="'control'"
        >
          <el-icon :size="14"><component :is="item.icon" /></el-icon>
          <span class="component-label">{{ t(item.labelKey) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { Document, Postcard, Tickets, Odometer } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'

const props = defineProps<{ readonly?: boolean }>()
const { t } = useI18n()
const segmentListRef = ref<HTMLElement | null>(null)
const controlListRef = ref<HTMLElement | null>(null)
let segmentSortable: Sortable | null = null
let controlSortable: Sortable | null = null

const segmentTypes = [
  { type: 'COVER', labelKey: 'workspace.design.segmentType.COVER', color: '#409EFF' },
  { type: 'TOC', labelKey: 'workspace.design.segmentType.TOC', color: '#67C23A' },
  { type: 'CHAPTER', labelKey: 'workspace.design.segmentType.CHAPTER', color: '#E6A23C' },
  { type: 'TABLE', labelKey: 'workspace.design.segmentType.TABLE', color: '#F56C6C' },
  { type: 'SIGNATURE', labelKey: 'workspace.design.segmentType.SIGNATURE', color: '#909399' },
  { type: 'LEGAL', labelKey: 'workspace.design.segmentType.LEGAL', color: '#8B5CF6' },
  { type: 'APPENDIX', labelKey: 'workspace.design.segmentType.APPENDIX', color: '#06B6D4' },
]

const controlNodes = [
  { type: 'header', labelKey: 'workspace.design.canvas.header', icon: Postcard },
  { type: 'footer', labelKey: 'workspace.design.canvas.footer', icon: Tickets },
  { type: 'page-number', labelKey: 'workspace.design.canvas.pageNumber', icon: Odometer },
]

onMounted(() => {
  if (props.readonly) return
  if (segmentListRef.value) {
    segmentSortable = Sortable.create(segmentListRef.value, {
      group: { name: 'canvas', pull: 'clone', put: false },
      sort: false, animation: 150, ghostClass: 'drag-source-active',
    })
  }
  if (controlListRef.value) {
    controlSortable = Sortable.create(controlListRef.value, {
      group: { name: 'canvas', pull: 'clone', put: false },
      sort: false, animation: 150, ghostClass: 'drag-source-active',
    })
  }
})

onBeforeUnmount(() => { segmentSortable?.destroy(); controlSortable?.destroy() })
</script>

<style scoped>
.component-panel { height: 100%; overflow-y: auto; padding: 12px; background: var(--el-bg-color-page); border-right: 1px solid var(--el-border-color-lighter); }
.panel-section { margin-bottom: 4px; }
.section-title { font-size: 12px; font-weight: 600; color: var(--el-text-color-secondary); margin-bottom: 8px; text-transform: uppercase; letter-spacing: 0.5px; }
.component-list { display: flex; flex-direction: column; gap: 6px; }
.component-item { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-radius: 6px; cursor: grab; user-select: none; transition: box-shadow 0.15s, border-color 0.15s; }
.component-item:active { cursor: grabbing; }
.component-item--segment { background: #fff; border: 1px solid var(--el-border-color); position: relative; overflow: hidden; }
.component-item--segment:hover { box-shadow: 0 2px 6px rgba(0,0,0,0.08); border-color: var(--el-color-primary-light-5); }
.type-bar { position: absolute; left: 0; top: 0; bottom: 0; width: 4px; border-radius: 6px 0 0 6px; }
.component-item--segment .el-icon, .component-item--segment .component-label { margin-left: 4px; }
.component-item--control { background: var(--el-fill-color-light); border: 1px dashed var(--el-border-color); }
.component-item--control:hover { background: var(--el-fill-color); border-color: var(--el-color-primary-light-5); }
.component-label { font-size: 13px; color: var(--el-text-color-primary); }
.drag-source-active { opacity: 0.5; }
.el-divider { margin: 12px 0; }
</style>
