<template>
  <div class="visual-editor-tab">
    <!-- Empty state -->
    <el-empty
      v-if="mergedSegments.length === 0"
      :description="t('workspace.editor.empty')"
    >
      <el-button type="primary" link @click="$emit('switchToSegments')">
        {{ t('workspace.editor.goToArrangement') }}
      </el-button>
    </el-empty>

    <template v-else>
      <!-- Header actions -->
      <div class="section-header">
        <h3>{{ t('workspace.editor.title') }}</h3>
        <div class="header-actions">
          <el-switch
            v-model="selectiveMode"
            :active-text="t('workspace.editor.selectivePreview')"
            style="margin-right: 12px"
          />
          <el-button
            v-if="selectiveMode"
            type="warning"
            :loading="previewLoading"
            :disabled="selectedSegmentIds.length === 0"
            @click="handleSelectivePreview"
          >
            {{ t('workspace.editor.selectivePreview') }} ({{ selectedSegmentIds.length }})
          </el-button>
          <el-button
            type="primary"
            :loading="previewLoading"
            @click="handlePreviewComposite"
          >
            {{ t('workspace.editor.previewComposite') }}
          </el-button>
        </div>
      </div>

      <!-- Segment table -->
      <el-table
        v-loading="loadingLocks"
        :data="mergedSegments"
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column
          v-if="selectiveMode"
          type="selection"
          width="45"
        />
        <el-table-column
          :label="t('workspace.editor.position')"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            {{ row.position + 1 }}
          </template>
        </el-table-column>
        <el-table-column
          prop="name"
          :label="t('common.name')"
          min-width="200"
        />
        <el-table-column
          :label="t('common.type')"
          width="120"
        >
          <template #default="{ row }">
            <el-tag v-if="row.segmentType" size="small" type="info">
              {{ row.segmentType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('common.updatedAt')"
          width="170"
        >
          <template #default="{ row }">
            {{ row.updatedAt ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('workspace.editor.lockStatus')"
          width="180"
        >
          <template #default="{ row }">
            <span v-if="getLockInfo(row.segmentId)" class="lock-indicator">
              <el-icon><Lock /></el-icon>
              <span class="lock-user">
                {{ t('workspace.editor.lockedBy', { user: getLockInfo(row.segmentId)!.lockedByUsername }) }}
              </span>
            </span>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('common.actions')"
          width="140"
          align="center"
        >
          <template #default="{ row }">
            <el-button type="primary" link @click="openEditor(row.segmentId)">
              {{ t('workspace.editor.openEditor') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { getSegmentLockInfo } from '@/api/segments'
import { previewCompositeTemplate, previewSelectiveSegments } from '@/api/composite-templates'
import type { AssemblySegmentEntry, LockInfo } from '@/types/segment'

defineEmits<{
  switchToSegments: []
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

const lockInfoMap = ref<Map<number, LockInfo | null>>(new Map())
const loadingLocks = ref(false)
const previewLoading = ref(false)
const selectiveMode = ref(false)
const selectedSegmentIds = ref<number[]>([])

// ── Merged segment entries ──
interface MergedSegmentEntry extends AssemblySegmentEntry {
  name: string
  segmentType: string | null
  updatedAt: string | null
}

const mergedSegments = computed<MergedSegmentEntry[]>(() => {
  const segmentMap = new Map(store.segments.map(s => [s.id, s]))
  return (store.assemblyConfig?.segments ?? []).map(entry => {
    const detail = segmentMap.get(entry.segmentId)
    return {
      ...entry,
      name: detail?.name ?? `Unknown Segment #${entry.segmentId}`,
      segmentType: detail?.segmentType ?? null,
      updatedAt: detail?.updatedAt ?? null,
    }
  })
})

// ── Lock status ──
function getLockInfo(segmentId: number): LockInfo | null {
  return lockInfoMap.value.get(segmentId) ?? null
}

async function checkLocks() {
  const segmentIds = store.assemblyConfig?.segments?.map(s => s.segmentId) ?? []
  if (segmentIds.length === 0) return
  loadingLocks.value = true
  try {
    const results = await Promise.allSettled(
      segmentIds.map(id => getSegmentLockInfo(id)),
    )
    const newMap = new Map<number, LockInfo | null>()
    results.forEach((result, index) => {
      newMap.set(
        segmentIds[index],
        result.status === 'fulfilled' ? result.value : null,
      )
    })
    lockInfoMap.value = newMap
  } finally {
    loadingLocks.value = false
  }
}

// ── Editor ──
function openEditor(segmentId: number) {
  window.open(`/segments/${segmentId}/editor`, '_blank')
}

// ── Preview Composite ──
async function handlePreviewComposite() {
  previewLoading.value = true
  try {
    const result = await previewCompositeTemplate(store.templateId)
    window.open(result.previewUrl, '_blank')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('workspace.editor.previewFailed'))
  } finally {
    previewLoading.value = false
  }
}

// ── Selective Preview ──
function handleSelectionChange(rows: MergedSegmentEntry[]) {
  selectedSegmentIds.value = rows.map(r => r.segmentId)
}

async function handleSelectivePreview() {
  if (selectedSegmentIds.value.length === 0) return
  previewLoading.value = true
  try {
    const result = await previewSelectiveSegments(store.templateId, {
      segmentIds: selectedSegmentIds.value,
    })
    window.open(result.previewUrl, '_blank')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('workspace.editor.previewFailed'))
  } finally {
    previewLoading.value = false
  }
}

defineExpose({ checkLocks })
</script>

<style scoped>
.visual-editor-tab {
  padding: 16px;
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
  align-items: center;
  gap: 8px;
}

.lock-indicator {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--el-color-warning);
  font-size: 13px;
}

.lock-user {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
