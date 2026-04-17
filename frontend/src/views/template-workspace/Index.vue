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
      <!-- Page header with inline stage indicator -->
      <div class="workspace-header">
        <div class="header-left">
          <h2 class="template-name">{{ store.template.name }}</h2>
          <el-tag :type="statusTagMap[store.template.status] || 'info'" size="small">{{ store.template.status }}</el-tag>
          <span class="version-badge">v{{ store.template.version }}</span>
        </div>
        <div class="header-center">
          <StageIndicator
            :stages="stageAvailability.stages.value"
            :current-stage="currentStage"
            @stage-click="handleStageClick"
          />
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

      <!-- Stage loading skeleton -->
      <el-skeleton v-if="stageLoading" :rows="8" animated style="margin-top: 16px" />

      <!-- Stage views (replaces el-tabs) -->
      <KeepAlive v-else>
        <component
          :is="currentStageComponent"
          :readonly="stageAvailability.isReadonly.value"
          @stage-change="handleStageChange"
        />
      </KeepAlive>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, onBeforeUnmount, defineComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { useStageAvailability } from '@/composables/useStageAvailability'
import { createDraftVersion } from '@/api/templates'
import { migrateToComposite } from '@/api/composite-templates'
import StageIndicator from './components/StageIndicator.vue'
import DesignStage from './components/DesignStage.vue'
import TestStage from './components/TestStage.vue'
import ApprovalStage from './components/ApprovalStage.vue'
import PublishStage from './components/PublishStage.vue'
import type { StageName } from '@/types/workspace'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const store = useTemplateWorkspaceStore()
const stageAvailability = useStageAvailability(store)

const currentStage = ref<StageName>('design')
const creatingDraft = ref(false)
const migrating = ref(false)
const stageLoading = ref(false)
const stageDataLoaded = reactive({ test: false, approval: false })

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'
const statusTagMap: Record<string, TagType> = {
  DRAFT: 'info', PENDING_REVIEW: 'warning', REVIEWED: 'primary', ACTIVE: 'success', ARCHIVED: 'danger',
}

const stageComponentMap: Record<StageName, ReturnType<typeof defineComponent>> = {
  design: DesignStage as any,
  test: TestStage as any,
  approval: ApprovalStage as any,
  publish: PublishStage as any,
}

const currentStageComponent = computed(() => stageComponentMap[currentStage.value])

async function handleStageClick(stage: StageName) {
  if (stage === currentStage.value) return

  stageLoading.value = true
  try {
    if (stage === 'test' && !stageDataLoaded.test) {
      await Promise.all([
        store.refreshTestCases(),
        store.refreshCoverage(),
      ])
      stageDataLoaded.test = true
    }
    if (stage === 'approval' && !stageDataLoaded.approval) {
      await store.refreshReviews()
      stageDataLoaded.approval = true
    }
    currentStage.value = stage
  } catch {
    // Load failed — still switch to stage, component shows error + retry
    currentStage.value = stage
  } finally {
    stageLoading.value = false
  }
}

function handleStageChange(stage: StageName) {
  // Reset data loaded flags when navigating back to design (new version / return to edit)
  if (stage === 'design') {
    stageDataLoaded.test = false
    stageDataLoaded.approval = false
  }
  handleStageClick(stage)
}

async function handleCreateDraft() {
  if (!store.templateId) return
  creatingDraft.value = true
  try {
    await createDraftVersion(store.templateId)
    await store.refreshTemplate()
    currentStage.value = 'design'
    stageDataLoaded.test = false
    stageDataLoaded.approval = false
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
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  padding: 0;
  overflow: hidden;
}
.workspace-header {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
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
