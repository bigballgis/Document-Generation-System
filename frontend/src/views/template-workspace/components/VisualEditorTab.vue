<template>
  <div class="visual-editor-tab">
    <!-- Empty state -->
    <el-empty
      v-if="segments.length === 0"
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
            :disabled="selectedPositions.length === 0"
            @click="handleSelectivePreview"
          >
            {{ t('workspace.editor.selectivePreview') }} ({{ selectedPositions.length }})
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
        :data="segments"
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
          :label="t('common.actions')"
          width="140"
          align="center"
        >
          <template #default="{ row }">
            <el-tag v-if="!row.enabled" size="small" type="info">{{ t('common.disable') }}</el-tag>
            <el-tag v-else-if="row.filePath" size="small" type="success">{{ t('common.enable') }}</el-tag>
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
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { previewCompositeTemplate, previewSelectiveSegments } from '@/api/composite-templates'
import type { AssemblySegmentEntry } from '@/types/segment'

defineEmits<{
  switchToSegments: []
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

const previewLoading = ref(false)
const selectiveMode = ref(false)
const selectedPositions = ref<number[]>([])

// ── Segments from assembly config ──
const segments = computed<AssemblySegmentEntry[]>(() => {
  return store.assemblyConfig?.segments ?? []
})

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
function handleSelectionChange(rows: AssemblySegmentEntry[]) {
  selectedPositions.value = rows.map(r => r.position)
}

async function handleSelectivePreview() {
  if (selectedPositions.value.length === 0) return
  previewLoading.value = true
  try {
    const result = await previewSelectiveSegments(store.templateId, {
      positions: selectedPositions.value,
    })
    window.open(result.previewUrl, '_blank')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('workspace.editor.previewFailed'))
  } finally {
    previewLoading.value = false
  }
}
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
</style>
