<template>
  <div class="test-stage">
    <!-- Coverage Bar -->
    <CoverageBar ref="coverageBarRef" />

    <!-- Smart Guidance -->
    <div v-if="uncoveredItems.length > 0" class="guidance-section">
      <div class="guidance-header">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ t('workspace.testForm.guidance') }}</span>
      </div>
      <div
        v-for="item in uncoveredItems"
        :key="item.missingPath"
        class="guidance-item"
        @click="highlightParam(item)"
      >
        <el-tag size="small" :type="item.type === 'BRANCH' ? 'warning' : 'info'">{{ item.type }}</el-tag>
        <span>{{ item.name }} — {{ item.missingPath }}</span>
      </div>
    </div>
    <div v-else-if="coverageBarRef?.coverageData" class="guidance-section guidance-success">
      <el-icon color="var(--el-color-success)"><CircleCheckFilled /></el-icon>
      <span>{{ t('workspace.testForm.noUncovered') }}</span>
    </div>

    <!-- Split layout: Form (40%) + Preview (60%) -->
    <div class="test-split">
      <div class="test-form-panel">
        <TestDataForm
          :parameters="store.parameters"
          :readonly="readonly"
          :highlighted-paths="highlightedPaths"
          @update:form-data="handleFormDataChange"
        />
      </div>
      <div class="test-preview-panel">
        <div v-if="previewLoading" class="preview-loading">
          <el-skeleton :rows="8" animated />
        </div>
        <div v-else-if="previewError" class="preview-error">
          <el-result icon="error" :title="t('workspace.testForm.previewFailed')" :sub-title="previewError">
            <template #extra>
              <el-button type="primary" @click="triggerPreview">{{ t('workspace.testForm.retryPreview') }}</el-button>
            </template>
          </el-result>
        </div>
        <div v-else-if="previewUrl" class="preview-frame">
          <iframe :src="previewUrl" class="preview-iframe" />
        </div>
        <div v-else class="preview-placeholder">
          <el-empty :description="t('common.noData')" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { InfoFilled, CircleCheckFilled } from '@element-plus/icons-vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { previewCompositeTemplate } from '@/api/composite-templates'
import type { UncoveredItem } from '@/types/parameter'
import CoverageBar from './CoverageBar.vue'
import TestDataForm from './TestDataForm.vue'

defineProps<{
  readonly: boolean
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

const coverageBarRef = ref<InstanceType<typeof CoverageBar> | null>(null)
const previewUrl = ref('')
const previewLoading = ref(false)
const previewError = ref('')
const highlightedPaths = ref<string[]>([])
let debounceTimer: ReturnType<typeof setTimeout> | null = null

const uncoveredItems = computed<UncoveredItem[]>(() => {
  return coverageBarRef.value?.coverageData?.uncoveredItems ?? []
})

function highlightParam(item: UncoveredItem) {
  highlightedPaths.value = [item.missingPath]
  // Auto-clear after 5s
  setTimeout(() => {
    highlightedPaths.value = []
  }, 5000)
}

function handleFormDataChange(_data: Record<string, unknown>) {
  // Debounce 500ms before triggering preview
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    triggerPreview()
  }, 500)
}

async function triggerPreview() {
  previewLoading.value = true
  previewError.value = ''
  try {
    const result = await previewCompositeTemplate(store.templateId)
    previewUrl.value = result.previewUrl
  } catch (e: any) {
    previewError.value = e.response?.data?.message || e.message || t('workspace.testForm.previewFailed')
  } finally {
    previewLoading.value = false
  }
}
</script>

<style scoped>
.test-stage {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: calc(100vh - 200px);
  min-height: 500px;
}
.guidance-section {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--el-color-warning-light-9);
  border-radius: 6px;
  font-size: 13px;
}
.guidance-section.guidance-success {
  background: var(--el-color-success-light-9);
}
.guidance-header {
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
  width: 100%;
  margin-bottom: 4px;
}
.guidance-item {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 2px 8px;
  border-radius: 4px;
  transition: background 0.2s;
}
.guidance-item:hover {
  background: var(--el-color-warning-light-7);
}
.test-split {
  flex: 1;
  display: flex;
  gap: 12px;
  min-height: 0;
}
.test-form-panel {
  flex: 4;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
.test-preview-panel {
  flex: 6;
  min-width: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  overflow: hidden;
}
.preview-loading, .preview-error, .preview-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 24px;
}
.preview-frame {
  height: 100%;
}
.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}
</style>
