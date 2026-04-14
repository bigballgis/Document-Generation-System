<template>
  <div class="assembly-editor" @keydown="handleKeydown">
    <div class="page-header">
      <div class="header-left">
        <el-button @click="router.push(`/composite-templates/${templateId}`)">
          <el-icon><ArrowLeft /></el-icon> {{ $t('common.back') }}
        </el-button>
        <h2>{{ $t('composite.assemblyEditor') }}</h2>
      </div>
      <div class="header-actions">
        <el-button :disabled="!canUndo" @click="undo">{{ $t('assembly.undo') }}</el-button>
        <el-button :disabled="!canRedo" @click="redo">{{ $t('assembly.redo') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.save') }}</el-button>
      </div>
    </div>

    <div class="editor-layout">
      <!-- Left: Outline Navigation -->
      <div class="editor-sidebar">
        <OutlineNavigation
          :segments="assemblyConfig.segments.value"
          :selected-index="selectedIndex"
          @select="selectedIndex = $event"
        />
        <div class="estimated-pages">
          {{ $t('assembly.estimatedPages') }}: {{ estimatedPages }}
        </div>
      </div>

      <!-- Center: Segment List -->
      <div class="editor-main">
        <!-- Empty State -->
        <div v-if="assemblyConfig.segments.value.length === 0" class="empty-state">
          <el-empty :description="$t('assembly.emptyHint')" />
        </div>

        <!-- Batch Actions -->
        <div v-if="selectedIndices.length > 0" class="batch-actions">
          <span>{{ $t('common.selected') }}: {{ selectedIndices.length }}</span>
          <el-button size="small" @click="handleBatchEnable">{{ $t('common.enable') }}</el-button>
          <el-button size="small" @click="handleBatchDisable">{{ $t('common.disable') }}</el-button>
          <el-button size="small" type="danger" @click="handleBatchRemove">{{ $t('common.delete') }}</el-button>
        </div>

        <!-- Segment Cards -->
        <div class="segment-list">
          <div
            v-for="(entry, index) in assemblyConfig.segments.value"
            :key="entry.filePath + '-' + index"
            class="segment-card"
            :class="{
              'is-dragging': drag.draggingIndex.value === index,
              'is-drop-target': drag.dropTargetIndex.value === index,
              'is-disabled': !entry.enabled,
              'is-selected': selectedIndex === index,
            }"
            draggable="true"
            @dragstart="drag.onDragStart(index)"
            @dragover.prevent="drag.onDragOver(index)"
            @drop="handleDrop"
            @dragend="drag.onDragEnd()"
            @click="selectedIndex = index"
          >
            <div class="card-header">
              <el-checkbox
                :model-value="selectedIndices.includes(index)"
                @change="toggleSelect(index, $event as boolean)"
                @click.stop
              />
              <span class="position-badge">{{ index + 1 }}</span>
              <span class="segment-name">{{ entry.name || `#${index + 1}` }}</span>
              <el-tag v-if="!entry.enabled" size="small" type="info">{{ $t('common.disable') }}</el-tag>
              <el-tag v-if="entry.segmentType" size="small" type="info">{{ entry.segmentType }}</el-tag>
            </div>

            <!-- Segment Config -->
            <div v-if="selectedIndex === index" class="card-config">
              <el-form :inline="true" size="small">
                <el-form-item :label="$t('common.enable')">
                  <el-switch v-model="entry.enabled" @change="markChanged(index, { enabled: entry.enabled })" />
                </el-form-item>
                <el-form-item :label="$t('assembly.pageBreak')">
                  <el-switch v-model="entry.pageBreakBefore" @change="markChanged(index, { pageBreakBefore: entry.pageBreakBefore })" />
                </el-form-item>
              </el-form>
              <el-form size="small" label-width="100px">
                <el-form-item :label="$t('assembly.condition')">
                  <el-input
                    v-model="entry.conditionExpression"
                    :placeholder="$t('assembly.conditionPlaceholder')"
                    @change="markChanged(index, { conditionExpression: entry.conditionExpression })"
                  />
                </el-form-item>
              </el-form>
              <DataScopeMapper
                :data-scope="entry.dataScope"
                @update="(ds: Record<string, string> | null) => markChanged(index, { dataScope: ds })"
              />
              <el-button size="small" type="danger" @click="handleRemove(index)">{{ $t('common.delete') }}</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getAssemblyConfig, updateAssemblyConfig } from '@/api/composite-templates'
import type { AssemblySegmentEntry } from '@/types/segment'
import { useAssemblyConfig } from '@/composables/useAssemblyConfig'
import { useSegmentDrag } from '@/composables/useSegmentDrag'
import OutlineNavigation from './components/OutlineNavigation.vue'
import DataScopeMapper from './components/DataScopeMapper.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const templateId = Number(route.params.id)
const saving = ref(false)
const selectedIndex = ref<number>(-1)
const selectedIndices = ref<number[]>([])

const assemblyConfig = useAssemblyConfig()
const { canUndo, canRedo, undo, redo } = assemblyConfig
const drag = useSegmentDrag()

const estimatedPages = computed(() => {
  const count = assemblyConfig.segments.value.filter(s => s.enabled).length
  return count > 0 ? `~${count}` : '0'
})

function toggleSelect(index: number, checked: boolean) {
  if (checked) {
    selectedIndices.value = [...selectedIndices.value, index]
  } else {
    selectedIndices.value = selectedIndices.value.filter(i => i !== index)
  }
}

function handleDrop() {
  const result = drag.onDrop(assemblyConfig.segments.value)
  assemblyConfig.setSegments(result)
}

function handleKeydown(e: KeyboardEvent) {
  if (e.altKey && e.key === 'ArrowUp' && selectedIndex.value > 0) {
    e.preventDefault()
    const result = drag.moveUp(assemblyConfig.segments.value, selectedIndex.value)
    if (result) {
      assemblyConfig.setSegments(result)
      selectedIndex.value--
    }
  }
  if (e.altKey && e.key === 'ArrowDown' && selectedIndex.value < assemblyConfig.segments.value.length - 1) {
    e.preventDefault()
    const result = drag.moveDown(assemblyConfig.segments.value, selectedIndex.value)
    if (result) {
      assemblyConfig.setSegments(result)
      selectedIndex.value++
    }
  }
  if (e.ctrlKey && e.key === 'z' && !e.shiftKey) {
    e.preventDefault()
    undo()
  }
  if (e.ctrlKey && e.shiftKey && e.key === 'Z') {
    e.preventDefault()
    redo()
  }
}

function markChanged(index: number, patch: Partial<AssemblySegmentEntry>) {
  assemblyConfig.updateSegment(index, patch)
}

function handleRemove(index: number) {
  assemblyConfig.removeSegment(index)
  selectedIndex.value = -1
}

function handleBatchEnable() {
  assemblyConfig.batchEnable(selectedIndices.value)
  selectedIndices.value = []
}

function handleBatchDisable() {
  assemblyConfig.batchDisable(selectedIndices.value)
  selectedIndices.value = []
}

function handleBatchRemove() {
  assemblyConfig.removeSegments(selectedIndices.value)
  selectedIndices.value = []
  selectedIndex.value = -1
}

async function handleSave() {
  saving.value = true
  try {
    await updateAssemblyConfig(templateId, { segments: assemblyConfig.segments.value })
    ElMessage.success(t('message.saveSuccess'))
  } catch { /* handled */ } finally {
    saving.value = false
  }
}

async function loadConfig() {
  try {
    const config = await getAssemblyConfig(templateId)
    assemblyConfig.deserialize(config)
  } catch { /* handled */ }
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped>
.assembly-editor { padding: 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.header-left { display: flex; align-items: center; gap: 12px; }
.header-left h2 { margin: 0; }
.header-actions { display: flex; gap: 8px; }
.editor-layout { display: flex; gap: 16px; min-height: 500px; }
.editor-sidebar { width: 200px; flex-shrink: 0; }
.editor-main { flex: 1; min-width: 0; }
.empty-state { padding: 60px 0; }
.batch-actions { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; padding: 8px 12px; background: var(--el-fill-color-light); border-radius: 4px; }
.segment-list { display: flex; flex-direction: column; gap: 8px; }
.segment-card { border: 1px solid var(--el-border-color); border-radius: 6px; padding: 12px; cursor: grab; transition: all 0.2s; }
.segment-card:hover { border-color: var(--el-color-primary-light-5); }
.segment-card.is-dragging { opacity: 0.5; }
.segment-card.is-drop-target { border-color: var(--el-color-primary); border-style: dashed; }
.segment-card.is-disabled { opacity: 0.6; background: var(--el-fill-color-lighter); }
.segment-card.is-selected { border-color: var(--el-color-primary); box-shadow: 0 0 0 1px var(--el-color-primary-light-7); }
.card-header { display: flex; align-items: center; gap: 8px; }
.position-badge { background: var(--el-color-primary-light-9); color: var(--el-color-primary); border-radius: 50%; width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600; }
.segment-name { font-weight: 500; flex: 1; }
.card-config { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--el-border-color-lighter); }
.estimated-pages { margin-top: 12px; padding: 8px; background: var(--el-fill-color-light); border-radius: 4px; font-size: 13px; text-align: center; }
</style>
