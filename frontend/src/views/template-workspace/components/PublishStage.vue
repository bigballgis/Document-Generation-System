<template>
  <div class="publish-stage">
    <el-card class="summary-card">
      <template #header>
        <span>{{ t('workspace.publish.summaryTitle') }}</span>
      </template>
      <div class="summary-grid">
        <div class="summary-item">
          <span class="summary-label">{{ t('workspace.publish.summary.version') }}</span>
          <span class="summary-value">v{{ store.template?.version ?? '-' }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">{{ t('workspace.publish.summary.params') }}</span>
          <span class="summary-value">{{ store.parameters.length }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">{{ t('workspace.publish.summary.coverage') }}</span>
          <span class="summary-value">{{ store.coverage?.overallCoveragePercent ?? 0 }}%</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">{{ t('workspace.publish.summary.reviewStatus') }}</span>
          <el-tag :type="statusTagType[store.templateStatus]" size="small">
            {{ store.templateStatus }}
          </el-tag>
        </div>
      </div>
    </el-card>

    <div class="publish-actions">
      <el-button
        v-if="store.templateStatus === 'REVIEWED'"
        type="primary"
        size="large"
        :loading="activating"
        @click="handleActivate"
      >
        {{ t('workspace.publish.activate') }}
      </el-button>

      <template v-if="store.templateStatus === 'ACTIVE'">
        <el-button size="large" @click="handleExportZip" :loading="exporting">
          {{ t('workspace.publish.exportZip') }}
        </el-button>
        <el-button size="large" type="primary" @click="handleNewVersion" :loading="creatingVersion">
          {{ t('workspace.publish.newVersion') }}
        </el-button>
      </template>
    </div>

    <el-card v-if="store.isActive" class="capabilities-card">
      <template #header>
        <span>{{ t('workspace.publish.capabilitiesTitle') }}</span>
      </template>
      <p class="capabilities-hint">{{ t('workspace.publish.capabilitiesHint') }}</p>
      <div class="capability-buttons">
        <el-button type="primary" plain size="large" @click="openApiManagement">
          {{ t('workspace.publish.openApiManagement') }}
        </el-button>
        <el-button size="large" @click="openDocumentHistory">
          {{ t('workspace.publish.openDocumentHistory') }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { activateTemplate, createDraftVersion } from '@/api/templates'
import { exportCompositeAsZip } from '@/api/composite-templates'
import type { StageName } from '@/types/workspace'

defineProps<{
  readonly: boolean
}>()

const emit = defineEmits<{
  'stage-change': [stage: StageName]
}>()

const { t } = useI18n()
const router = useRouter()
const store = useTemplateWorkspaceStore()

const activating = ref(false)
const exporting = ref(false)
const creatingVersion = ref(false)

type TagType = 'info' | 'warning' | 'primary' | 'success' | 'danger'
const statusTagType: Record<string, TagType> = {
  DRAFT: 'info',
  IN_TEST: 'warning',
  PENDING_REVIEW: 'warning',
  REVIEWED: 'primary',
  ACTIVE: 'success',
  ARCHIVED: 'danger',
}

function openApiManagement() {
  router.push({ name: 'TemplateApiManagement', params: { id: String(store.templateId) } })
}

function openDocumentHistory() {
  router.push({
    path: '/documents',
    query: { templateId: String(store.templateId) },
  })
}

async function handleActivate() {
  activating.value = true
  try {
    await activateTemplate(store.templateId)
    await Promise.all([store.refreshTemplate(), store.refreshTransitions()])
    ElMessage.success(t('workspace.publish.activateSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('workspace.publish.activateFailed'))
  } finally {
    activating.value = false
  }
}

async function handleExportZip() {
  exporting.value = true
  try {
    const blob = await exportCompositeAsZip(store.templateId) as unknown as Blob
    const url = URL.createObjectURL(blob as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${store.template?.name ?? 'template'}-v${store.template?.version ?? 1}.zip`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.exportFailed'))
  } finally {
    exporting.value = false
  }
}

async function handleNewVersion() {
  creatingVersion.value = true
  try {
    await createDraftVersion(store.templateId)
    await store.refreshTemplate()
    ElMessage.success(t('workspace.draftCreated'))
    emit('stage-change', 'design')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  } finally {
    creatingVersion.value = false
  }
}
</script>

<style scoped>
.publish-stage {
  padding: 16px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
}
.summary-card,
.capabilities-card {
  width: 100%;
  max-width: 600px;
}
.summary-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.summary-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.summary-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.publish-actions {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: center;
}
.capabilities-hint {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
.capability-buttons {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
}
</style>
