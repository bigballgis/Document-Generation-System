<template>
  <div class="segment-arrangement-tab" tabindex="0" @keydown="handleKeydown">
    <!-- Header -->
    <div class="section-header">
      <h3>{{ t('workspace.tabSegments') }}</h3>
      <div class="header-actions">
        <el-button @click="createFormVisible = !createFormVisible">
          {{ t('workspace.segment.createNew') }}
        </el-button>
        <el-button
          :disabled="!assemblyConfig.canUndo.value"
          @click="assemblyConfig.undo()"
        >
          {{ t('assembly.undo') }}
        </el-button>
        <el-button
          :disabled="!assemblyConfig.canRedo.value"
          @click="assemblyConfig.redo()"
        >
          {{ t('assembly.redo') }}
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="handleSave"
        >
          <el-badge v-if="hasUnsavedChanges" is-dot class="save-badge">
            {{ t('common.save') }}
          </el-badge>
          <span v-else>{{ t('common.save') }}</span>
        </el-button>
      </div>
    </div>

    <!-- Create new segment inline form (upload .docx) -->
    <div v-if="createFormVisible" class="create-segment-form">
      <el-form
        ref="createFormRef"
        :model="newSegmentForm"
        :rules="createFormRules"
        label-width="140px"
        @submit.prevent="handleCreateSegment"
      >
        <el-form-item :label="t('common.name')" prop="name">
          <el-input
            v-model="newSegmentForm.name"
            maxlength="200"
            show-word-limit
            :placeholder="t('workspace.segment.namePlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('common.type')" prop="segmentType">
          <el-select
            v-model="newSegmentForm.segmentType"
            :placeholder="t('common.all')"
            clearable
            style="width: 100%"
          >
            <el-option v-for="st in segmentTypeOptions" :key="st.value" :label="st.label" :value="st.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('workspace.segment.file')">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".docx"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
          >
            <el-button type="primary">{{ t('common.upload') }}</el-button>
          </el-upload>
          <div class="file-hint">{{ t('workspace.segment.fileHint') }}</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="creating" @click="handleCreateSegment">
            {{ t('common.create') }}
          </el-button>
          <el-button @click="resetCreateForm">{{ t('common.cancel') }}</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- Empty state -->
    <el-empty
      v-if="assemblyConfig.segments.value.length === 0 && !createFormVisible"
      :description="t('assembly.emptyHint')"
    />

    <!-- Segment list -->
    <div v-else-if="assemblyConfig.segments.value.length > 0" class="segment-list">
      <div
        v-for="(seg, index) in assemblyConfig.segments.value"
        :key="seg.filePath + '-' + index"
        class="segment-card"
        :class="{
          'is-dragging': segmentDrag.draggingIndex.value === index,
          'is-drop-target': segmentDrag.dropTargetIndex.value === index,
          'is-selected': selectedIndex === index,
          'is-disabled': !seg.enabled,
          'is-expanded': expandedIndex === index,
        }"
        draggable="true"
        @dragstart="handleDragStart(index)"
        @dragover.prevent="handleDragOver(index)"
        @dragend="handleDragEnd"
        @drop.prevent="handleDrop"
        @click="selectedIndex = index"
      >
        <div class="card-row">
          <div class="card-content">
            <span class="position-number">{{ seg.position + 1 }}</span>
            <span class="segment-name">{{ seg.name }}</span>
            <el-tag v-if="seg.segmentType" size="small" type="info">
              {{ seg.segmentType }}
            </el-tag>
            <el-icon v-if="!seg.enabled" class="disabled-icon" color="var(--el-color-info)">
              <Hide />
            </el-icon>
          </div>
          <div class="card-actions">
            <el-button link type="primary" @click.stop="toggleExpand(index)">
              {{ expandedIndex === index ? t('common.collapse') : t('common.expand') }}
            </el-button>
            <el-button link type="danger" @click.stop="handleRemove(index)">
              {{ t('common.delete') }}
            </el-button>
          </div>
        </div>

        <!-- Inline configuration panel -->
        <div v-if="expandedIndex === index" class="config-panel" @click.stop>
          <el-form label-width="160px" size="default">
            <el-form-item :label="t('workspace.segment.enabled')">
              <el-switch
                :model-value="seg.enabled"
                @change="(val: string | number | boolean) => handleConfigChange(index, { enabled: !!val })"
              />
            </el-form-item>
            <el-form-item :label="t('workspace.segment.pageBreakBefore')">
              <el-switch
                :model-value="seg.pageBreakBefore"
                @change="(val: string | number | boolean) => handleConfigChange(index, { pageBreakBefore: !!val })"
              />
            </el-form-item>
            <el-form-item :label="t('workspace.segment.conditionExpr')">
              <el-input
                :model-value="seg.conditionExpression ?? ''"
                :placeholder="t('assembly.conditionPlaceholder')"
                @input="(val: string) => handleConfigChange(index, { conditionExpression: val || null })"
              />
            </el-form-item>
            <el-form-item :label="t('workspace.segment.dataScope')">
              <KeyValueEditor
                :model-value="seg.dataScope ?? {}"
                @update:model-value="(val: Record<string, string>) => handleDataScopeChange(index, val)"
              />
            </el-form-item>
          </el-form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import { Hide } from '@element-plus/icons-vue'
import KeyValueEditor from '@/views/data-sources/KeyValueEditor.vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { useAssemblyConfig } from '@/composables/useAssemblyConfig'
import { useSegmentDrag } from '@/composables/useSegmentDrag'
import { updateAssemblyConfig, uploadSegment } from '@/api/composite-templates'
import type { AssemblySegmentEntry } from '@/types/segment'

const { t } = useI18n()
const store = useTemplateWorkspaceStore()
const assemblyConfig = useAssemblyConfig()
const segmentDrag = useSegmentDrag()

const saving = ref(false)
const selectedIndex = ref<number | null>(null)
const expandedIndex = ref<number | null>(null)

// ── Create segment form ──
const createFormVisible = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()
const uploadRef = ref()
const selectedFile = ref<File | null>(null)

const newSegmentForm = reactive({
  name: '',
  segmentType: '',
})

const segmentTypeOptions = [
  { label: 'Cover', value: 'COVER' },
  { label: 'TOC', value: 'TOC' },
  { label: 'Chapter', value: 'CHAPTER' },
  { label: 'Table', value: 'TABLE' },
  { label: 'Signature', value: 'SIGNATURE' },
  { label: 'Legal', value: 'LEGAL' },
  { label: 'Appendix', value: 'APPENDIX' },
]

const createFormRules: FormRules = {
  name: [
    { required: true, message: () => t('validation.required', { field: t('common.name') }), trigger: 'blur' },
    { max: 200, message: () => t('validation.maxLength', { field: t('common.name'), max: 200 }), trigger: 'blur' },
  ],
}

function onFileChange(file: UploadFile) {
  selectedFile.value = file.raw || null
}

function onFileRemove() {
  selectedFile.value = null
}

function resetCreateForm() {
  newSegmentForm.name = ''
  newSegmentForm.segmentType = ''
  selectedFile.value = null
  createFormRef.value?.resetFields()
  createFormVisible.value = false
}

async function handleCreateSegment() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!selectedFile.value) {
    ElMessage.warning(t('workspace.segment.fileHint'))
    return
  }

  creating.value = true
  try {
    const entry = await uploadSegment(
      store.templateId,
      selectedFile.value,
      newSegmentForm.name,
      newSegmentForm.segmentType || undefined,
    )

    assemblyConfig.addSegment({
      ...entry,
      position: assemblyConfig.segments.value.length,
    })

    try {
      await updateAssemblyConfig(store.templateId, assemblyConfig.serialize())
      await store.refreshAssemblyConfig()
      assemblyConfig.deserialize(store.assemblyConfig!)
      updateSavedSnapshot()
      ElMessage.success(t('message.createSuccess'))
    } catch {
      ElMessage.warning(t('workspace.segment.createdButNotAdded'))
    }

    resetCreateForm()
  } catch { /* handled by interceptor */ } finally {
    creating.value = false
  }
}

// ── Unsaved changes detection ──
const lastSavedSnapshot = ref<string>('')

const hasUnsavedChanges = computed(() => {
  return JSON.stringify(assemblyConfig.segments.value) !== lastSavedSnapshot.value
})

function updateSavedSnapshot() {
  lastSavedSnapshot.value = JSON.stringify(assemblyConfig.segments.value)
}

defineExpose({ hasUnsavedChanges })

// ── Initialization ──
onMounted(() => {
  if (store.assemblyConfig) {
    assemblyConfig.deserialize(store.assemblyConfig)
    updateSavedSnapshot()
  }
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
})

// ── Drag & drop ──
function handleDragStart(index: number) {
  segmentDrag.onDragStart(index)
}

function handleDragOver(index: number) {
  segmentDrag.onDragOver(index)
}

function handleDragEnd() {
  segmentDrag.onDragEnd()
}

function handleDrop() {
  const reordered = segmentDrag.onDrop(assemblyConfig.segments.value)
  if (reordered !== assemblyConfig.segments.value) {
    assemblyConfig.setSegments(reordered)
  }
}

// ── Keyboard shortcuts ──
function handleKeydown(e: KeyboardEvent) {
  if (e.ctrlKey && e.key === 'z' && !e.shiftKey) {
    e.preventDefault()
    assemblyConfig.undo()
  } else if (e.ctrlKey && e.shiftKey && (e.key === 'Z' || e.key === 'z')) {
    e.preventDefault()
    assemblyConfig.redo()
  } else if (e.altKey && e.key === 'ArrowUp' && selectedIndex.value != null) {
    e.preventDefault()
    const result = segmentDrag.moveUp(assemblyConfig.segments.value, selectedIndex.value)
    if (result) {
      assemblyConfig.setSegments(result)
      selectedIndex.value--
    }
  } else if (e.altKey && e.key === 'ArrowDown' && selectedIndex.value != null) {
    e.preventDefault()
    const result = segmentDrag.moveDown(assemblyConfig.segments.value, selectedIndex.value)
    if (result) {
      assemblyConfig.setSegments(result)
      selectedIndex.value++
    }
  }
}

// ── Expand / collapse config ──
function toggleExpand(index: number) {
  expandedIndex.value = expandedIndex.value === index ? null : index
}

// ── Inline config change handlers ──
function handleConfigChange(index: number, patch: Partial<AssemblySegmentEntry>) {
  assemblyConfig.updateSegment(index, patch)
}

function handleDataScopeChange(index: number, val: Record<string, string>) {
  const hasKeys = Object.keys(val).length > 0
  assemblyConfig.updateSegment(index, { dataScope: hasKeys ? val : null })
}

// ── Remove segment ──
async function handleRemove(index: number) {
  try {
    await ElMessageBox.confirm(
      t('workspace.segment.removeConfirm'),
      t('confirm.deleteTitle'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  assemblyConfig.removeSegment(index)
  if (selectedIndex.value === index) selectedIndex.value = null
  if (expandedIndex.value === index) expandedIndex.value = null
}

// ── Save arrangement ──
async function handleSave() {
  saving.value = true
  try {
    await updateAssemblyConfig(store.templateId, assemblyConfig.serialize())
    await Promise.all([
      store.refreshAssemblyConfig(),
      store.refreshCoverage(),
    ])
    assemblyConfig.deserialize(store.assemblyConfig!)
    updateSavedSnapshot()
    ElMessage.success(t('message.saveSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.segment-arrangement-tab {
  padding: 16px;
  outline: none;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--el-text-color-primary);
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.save-badge :deep(.el-badge__content) {
  top: 2px;
  right: -2px;
}

.segment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.segment-card {
  display: flex;
  flex-direction: column;
  padding: 12px 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-bg-color);
  cursor: grab;
  transition: border-color 0.2s, box-shadow 0.2s;
  user-select: none;
}

.segment-card:hover {
  border-color: var(--el-color-primary-light-5);
}

.segment-card.is-selected {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-7);
}

.segment-card.is-dragging {
  opacity: 0.5;
}

.segment-card.is-drop-target {
  border-color: var(--el-color-primary);
  border-style: dashed;
}

.segment-card.is-disabled {
  opacity: 0.6;
}

.card-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-content {
  display: flex;
  align-items: center;
  gap: 12px;
}

.position-number {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.segment-name {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.disabled-icon {
  font-size: 16px;
}

.card-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.create-segment-form {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-bg-color-page);
}

.file-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.config-panel {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  cursor: default;
}

.config-panel :deep(.el-form-item) {
  margin-bottom: 12px;
}

.config-panel :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.segment-card.is-expanded {
  border-color: var(--el-color-primary-light-5);
}
</style>
