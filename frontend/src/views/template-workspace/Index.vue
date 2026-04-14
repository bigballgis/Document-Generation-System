<template>
  <div class="template-workspace">
    <!-- Loading skeleton -->
    <el-skeleton v-if="store.loading" :rows="12" animated />

    <!-- Critical error -->
    <div v-else-if="store.criticalError" class="error-page">
      <el-result icon="error" :title="$t('workspace.criticalError')" :sub-title="store.criticalError">
        <template #extra>
          <el-button type="primary" @click="retry">{{ $t('workspace.retry') }}</el-button>
          <el-button @click="router.push('/templates')">{{ $t('workspace.backToList') }}</el-button>
        </template>
      </el-result>
    </div>

    <!-- Workspace content -->
    <template v-else-if="store.template">
      <!-- Page header -->
      <div class="workspace-header">
        <div class="header-left">
          <h2 class="template-name">{{ store.template.name }}</h2>
          <el-tag :type="statusTagMap[store.template.status] || 'info'" size="small">{{ store.template.status }}</el-tag>
          <span class="version-badge">v{{ store.template.version }}</span>
        </div>
        <el-button @click="router.push('/templates')">{{ $t('workspace.backToList') }}</el-button>
      </div>

      <!-- ACTIVE banner -->
      <el-alert
        v-if="store.isActive"
        type="warning"
        :title="$t('workspace.activeBanner')"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      >
        <el-button size="small" type="primary" :loading="creatingDraft" @click="handleCreateDraft">
          {{ $t('workspace.editAsNewVersion') }}
        </el-button>
      </el-alert>

      <!-- SINGLE migration prompt -->
      <el-alert
        v-if="store.template.templateType === 'SINGLE'"
        type="info"
        :title="$t('workspace.migrationPrompt')"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      >
        <el-button size="small" type="primary" :loading="migrating" @click="handleMigrate">
          {{ $t('workspace.convert') }}
        </el-button>
      </el-alert>

      <!-- Non-critical warnings -->
      <el-alert
        v-for="(msg, section) in store.warnings"
        :key="section"
        type="warning"
        :title="`${$t('workspace.warningBanner')}: ${section}`"
        :description="msg"
        show-icon
        closable
        style="margin-bottom: 8px"
      />

      <!-- Step indicator -->
      <WorkflowStepIndicator
        :steps="steps"
        :current-tab="activeTab"
        @step-click="handleStepClick"
      />

      <!-- Tabs -->
      <el-tabs v-model="activeTab" :before-leave="handleBeforeLeave" style="margin-top: 16px">
        <!-- Real tabs (P2) -->
        <el-tab-pane :label="$t('workspace.tabDataStructure')" name="dataStructure">
          <DataStructureTab />
        </el-tab-pane>
        <el-tab-pane :label="$t('workspace.tabSegments')" name="segments">
          <SegmentArrangementTab ref="segmentArrangementRef" />
        </el-tab-pane>
        <el-tab-pane :label="$t('workspace.tabEditor')" name="editor">
          <VisualEditorTab ref="visualEditorRef" @switch-to-segments="activeTab = 'segments'" />
        </el-tab-pane>

        <!-- Real tabs (P3) -->
        <el-tab-pane :label="$t('workspace.tabTesting')" name="testing">
          <TestingTab />
        </el-tab-pane>
        <el-tab-pane :label="$t('workspace.tabReviewPublish')" name="reviewPublish">
          <ReviewPublishTab />
        </el-tab-pane>

        <!-- Real tabs (P4) -->
        <el-tab-pane :label="$t('workspace.tabExportImport')" name="exportImport">
          <ExportImportTab />
        </el-tab-pane>
        <el-tab-pane :label="$t('workspace.tabSettings')" name="settings">
          <SettingsTab />
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { useWorkflowSteps } from '@/composables/useWorkflowSteps'
import { createDraftVersion } from '@/api/templates'
import { migrateToComposite } from '@/api/composite-templates'
import WorkflowStepIndicator from './components/WorkflowStepIndicator.vue'
import DataStructureTab from './components/DataStructureTab.vue'
import SegmentArrangementTab from './components/SegmentArrangementTab.vue'
import VisualEditorTab from './components/VisualEditorTab.vue'
import TestingTab from './components/TestingTab.vue'
import ReviewPublishTab from './components/ReviewPublishTab.vue'
import ExportImportTab from './components/ExportImportTab.vue'
import SettingsTab from './components/SettingsTab.vue'
import type { TabName } from '@/types/workspace'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const store = useTemplateWorkspaceStore()
const { steps, stepToTab } = useWorkflowSteps(store)

const activeTab = ref<TabName>('dataStructure')
const creatingDraft = ref(false)
const migrating = ref(false)
const segmentArrangementRef = ref<InstanceType<typeof SegmentArrangementTab> | null>(null)
const visualEditorRef = ref<InstanceType<typeof VisualEditorTab> | null>(null)

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'
const statusTagMap: Record<string, TagType> = {
  DRAFT: 'info', PENDING_REVIEW: 'warning', REVIEWED: 'primary', ACTIVE: 'success', ARCHIVED: 'danger',
}

function handleStepClick(stepKey: string) {
  const tab = stepToTab[stepKey]
  if (tab) {
    handleBeforeLeave(tab as TabName, activeTab.value).then((allowed) => {
      if (allowed !== false) activeTab.value = tab as TabName
    })
  }
}

// ── Tab change with unsaved changes guard ──
async function handleBeforeLeave(newTab: TabName | string | number, oldTab: TabName | string | number): Promise<boolean> {
  if (String(oldTab) === 'segments' && segmentArrangementRef.value?.hasUnsavedChanges) {
    try {
      await ElMessageBox.confirm(
        t('workspace.segment.unsavedConfirm'),
        t('common.confirm'),
        { type: 'warning' },
      )
    } catch {
      return false // user cancelled — stay on current tab
    }
  }
  if (String(newTab) === 'editor') {
    // No lock checking needed in inline mode
  }
  return true
}

// ── Route leave guard ──
onBeforeRouteLeave(async () => {
  if (segmentArrangementRef.value?.hasUnsavedChanges) {
    try {
      await ElMessageBox.confirm(
        t('workspace.segment.unsavedConfirm'),
        t('common.confirm'),
        { type: 'warning' },
      )
      return true
    } catch {
      return false
    }
  }
  return true
})

async function handleCreateDraft() {
  if (!store.templateId) return
  creatingDraft.value = true
  try {
    await createDraftVersion(store.templateId)
    await store.refreshTemplate()
    ElMessage.success(t('workspace.draftCreated'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || 'Failed')
  } finally {
    creatingDraft.value = false
  }
}

async function handleMigrate() {
  if (!store.templateId) return
  migrating.value = true
  try {
    const result = await migrateToComposite(store.templateId)
    router.push(`/templates/${result.compositeTemplateId}/workspace`)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || 'Migration failed')
  } finally {
    migrating.value = false
  }
}

function retry() {
  store.initWorkspace(Number(route.params.id))
}

onMounted(() => {
  const id = Number(route.params.id)
  store.initWorkspace(id)
})

onBeforeUnmount(() => {
  store.$reset()
})
</script>

<style scoped>
.template-workspace {
  padding: 0;
}
.workspace-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.template-name {
  margin: 0;
  font-size: 20px;
}
.version-badge {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.error-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}
</style>
