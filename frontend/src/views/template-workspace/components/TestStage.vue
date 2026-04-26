<template>
  <div class="test-stage">
    <!-- Coverage Bar -->
    <CoverageBar ref="coverageBarRef" />

    <!-- Smart Guidance -->
    <div v-if="uncoveredItems.length > 0" class="guidance-section">
      <div class="guidance-header">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ t('workspace.testForm.guidance') }}</span>
        <el-tag size="small" type="danger" round>{{ uncoveredItems.length }}</el-tag>
      </div>

      <!-- Grouped by type -->
      <div v-for="group in groupedUncovered" :key="group.type" class="guidance-group">
        <div class="guidance-group-header" @click="toggleGroup(group.type)">
          <el-icon class="group-arrow" :class="{ expanded: expandedGroups[group.type] }"><ArrowRight /></el-icon>
          <el-tag size="small" :type="groupTagType(group.type)">{{ group.type }}</el-tag>
          <span class="group-summary">{{ group.items.length }} {{ t('workspace.testForm.uncoveredCount') }}</span>
        </div>
        <transition name="collapse">
          <div v-show="expandedGroups[group.type]" class="guidance-group-body">
            <div
              v-for="item in group.items"
              :key="item.missingPath"
              class="guidance-item"
              @click="highlightParam(item)"
            >
              <span class="item-name">{{ item.name }}</span>
              <span class="item-path">{{ item.missingPath }}</span>
            </div>
          </div>
        </transition>
      </div>
    </div>
    <div v-else-if="coverageBarRef?.coverageData" class="guidance-section guidance-success">
      <el-icon color="var(--el-color-success)"><CircleCheckFilled /></el-icon>
      <span>{{ t('workspace.testForm.noUncovered') }}</span>
    </div>

    <el-collapse v-if="store.templateId" class="saved-tests-collapse">
      <el-collapse-item :title="t('workspace.testing.savedTestCasesTitle')" name="saved">
        <div class="saved-tests-body">
          <TestCaseManagement :template-id="store.templateId" />
        </div>
      </el-collapse-item>
    </el-collapse>

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
import { ref, computed, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { InfoFilled, CircleCheckFilled, ArrowRight } from '@element-plus/icons-vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { previewCompositeTemplate } from '@/api/composite-templates'
import type { UncoveredItem } from '@/types/parameter'
import CoverageBar from './CoverageBar.vue'
import TestDataForm from './TestDataForm.vue'
import TestCaseManagement from '@/views/templates/components/TestCaseManagement.vue'

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

interface UncoveredGroup {
  type: string
  items: UncoveredItem[]
}

const groupedUncovered = computed<UncoveredGroup[]>(() => {
  const map = new Map<string, UncoveredItem[]>()
  for (const item of uncoveredItems.value) {
    const list = map.get(item.type) || []
    list.push(item)
    map.set(item.type, list)
  }
  // Order: BRANCH → LOOP → PARAMETER
  const order = ['BRANCH', 'LOOP', 'PARAMETER']
  return order
    .filter(t => map.has(t))
    .map(t => ({ type: t, items: map.get(t)! }))
})

const expandedGroups = reactive<Record<string, boolean>>({})

function toggleGroup(type: string) {
  expandedGroups[type] = !expandedGroups[type]
}

function groupTagType(type: string): 'warning' | 'success' | 'info' {
  if (type === 'BRANCH') return 'warning'
  if (type === 'LOOP') return 'success'
  return 'info'
}

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
.saved-tests-collapse {
  flex-shrink: 0;
}
.saved-tests-body {
  max-height: min(480px, 45vh);
  overflow: auto;
}
.guidance-section {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 14px;
  background: var(--el-color-warning-light-9);
  border-radius: 6px;
  font-size: 13px;
  max-height: 240px;
  overflow-y: auto;
}
.guidance-section.guidance-success {
  flex-direction: row;
  align-items: center;
  gap: 8px;
  background: var(--el-color-success-light-9);
  max-height: none;
  overflow: visible;
}
.guidance-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  margin-bottom: 2px;
}
.guidance-group {
  border-radius: 4px;
  overflow: hidden;
}
.guidance-group-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  cursor: pointer;
  border-radius: 4px;
  user-select: none;
  transition: background 0.15s;
}
.guidance-group-header:hover {
  background: var(--el-color-warning-light-7);
}
.group-arrow {
  transition: transform 0.2s;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.group-arrow.expanded {
  transform: rotate(90deg);
}
.group-summary {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.guidance-group-body {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 4px;
  padding: 4px 0 4px 22px;
}
.guidance-item {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 3px 8px;
  border-radius: 4px;
  transition: background 0.15s;
  overflow: hidden;
  font-size: 12px;
}
.guidance-item:hover {
  background: var(--el-color-warning-light-7);
}
.item-name {
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 120px;
}
.item-path {
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.collapse-enter-active,
.collapse-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}
.collapse-enter-from,
.collapse-leave-to {
  opacity: 0;
  max-height: 0;
}
.collapse-enter-to,
.collapse-leave-from {
  opacity: 1;
  max-height: 500px;
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
