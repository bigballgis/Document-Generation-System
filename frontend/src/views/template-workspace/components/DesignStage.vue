<template>
  <div class="design-stage">
    <!-- Unified Toolbar -->
    <div class="design-toolbar">
      <div class="toolbar-left">
        <el-button v-if="!readonly && isDraft" @click="handleImportZip">
          {{ t('workspace.design.importZip') }}
        </el-button>
        <input
          ref="zipInputRef"
          type="file"
          accept=".zip"
          style="display: none"
          @change="handleZipFileSelected"
        />
      </div>
      <div class="toolbar-right">
        <el-button @click="overviewVisible = true">
          {{ t('workspace.design.parameterOverview') }}
        </el-button>
        <!-- Segment selector: only visible in step 3 -->
        <el-select
          v-if="currentStep === 'segment-detail' && enabledSegments.length > 1"
          v-model="selectedSegmentIndex"
          size="default"
          style="width: 200px"
          :placeholder="t('workspace.design.selectSegment')"
        >
          <el-option
            v-for="(seg, idx) in enabledSegments"
            :key="idx"
            :label="`${seg.position + 1}. ${seg.name}`"
            :value="idx"
          />
        </el-select>
        <el-button circle @click="settingsVisible = true">
          <el-icon><Setting /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- Step Indicator -->
    <DesignStepIndicator
      :current-step="currentStep"
      :step-statuses="stepStatuses"
      @update:current-step="goToStep"
    />

    <!-- Step View Area -->
    <div class="step-view-area">
      <ParameterTableDesign v-if="currentStep === 'parameter-table'" :readonly="readonly" />
      <SegmentCanvas v-else-if="currentStep === 'segment-canvas'" :readonly="readonly" />
      <SegmentDetailDesign v-else-if="currentStep === 'segment-detail'" :readonly="readonly" />
    </div>

    <!-- Prev / Next Navigation (hidden for parameter-table, it has its own) -->
    <div v-if="currentStep !== 'parameter-table'" class="step-navigation">
      <el-button
        @click="goPrev"
      >
        {{ t('workspace.design.prev') }}
      </el-button>
      <el-button
        v-if="currentStep !== 'segment-detail'"
        type="primary"
        @click="goNext"
      >
        {{ t('workspace.design.next') }}
      </el-button>
      <span v-else />
    </div>

    <!-- Parameter Overview Panel -->
    <ParameterOverviewPanel
      :visible="overviewVisible"
      @update:visible="overviewVisible = $event"
      @navigate-to-param="handleNavigateToParam"
    />

    <!-- Settings Popover -->
    <SettingsPopover
      :visible="settingsVisible"
      @update:visible="settingsVisible = $event"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { useDesignStep } from '@/composables/useDesignStep'
import { importCompositeFromZip } from '@/api/composite-templates'
import DesignStepIndicator from './DesignStepIndicator.vue'
import ParameterTableDesign from './ParameterTableDesign.vue'
import SegmentCanvas from './SegmentCanvas.vue'
import SegmentDetailDesign from './SegmentDetailDesign.vue'
import ParameterOverviewPanel from './ParameterOverviewPanel.vue'
import SettingsPopover from './SettingsPopover.vue'
import type { AssemblySegmentEntry } from '@/types/segment'

defineProps<{
  readonly: boolean
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()
const { currentStep, stepStatuses, goToStep, goNext, goPrev } = useDesignStep()

const overviewVisible = ref(false)
const settingsVisible = ref(false)
const selectedSegmentIndex = ref(0)
const zipInputRef = ref<HTMLInputElement | null>(null)

const isDraft = computed(() => store.isDraft)

const segments = computed<AssemblySegmentEntry[]>(() => {
  return store.assemblyConfig?.segments ?? []
})

const enabledSegments = computed(() => {
  return segments.value.filter(s => s.enabled && s.filePath)
})

// ── Import ZIP ──

function handleImportZip() {
  zipInputRef.value?.click()
}

async function handleZipFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    await importCompositeFromZip(file)
    await store.refreshAssemblyConfig()
    await store.refreshParameters()
    ElMessage.success(t('message.importSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.importFailed'))
  } finally {
    input.value = ''
  }
}

// ── Navigate to param from ParameterOverviewPanel ──

function handleNavigateToParam(_paramId: number) {
  goToStep('parameter-table')
}
</script>

<style scoped>
.design-stage {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 280px);
  min-height: 400px;
}

.design-toolbar {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  gap: 8px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.step-view-area {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.step-navigation {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
}

/* ── Global drag CSS classes ── */
:deep(.drag-source-active) {
  opacity: 0.5;
  transition: opacity 0.15s ease;
}

:deep(.drop-indicator) {
  position: relative;
}

:deep(.drop-indicator)::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--el-color-primary);
  border-radius: 1px;
  z-index: 10;
}

:deep(.drop-indicator--top)::before {
  top: -1px;
}

:deep(.drop-indicator--bottom)::before {
  bottom: -1px;
}

:deep(.drop-forbidden) {
  cursor: not-allowed;
}
</style>
