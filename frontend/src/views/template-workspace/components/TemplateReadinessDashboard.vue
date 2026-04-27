<template>
  <div class="readiness-dashboard">
    <div class="panel-header">
      <span class="panel-title">{{ t('workspace.validation.readinessDashboard') }}</span>
      <el-button size="small" :loading="refreshing" @click="$emit('refresh')">
        {{ t('workspace.testing.refreshCoverage') }}
      </el-button>
    </div>
    <div class="dashboard-body">
      <CoverageBar ref="coverageBarRef" />
      <el-alert
        v-if="coverageWarning"
        type="warning"
        :title="coverageWarning"
        show-icon
        :closable="false"
        class="warn-alert"
      />
      <el-card shadow="never" class="placeholder-card">
        <template #header>
          <span class="card-header-text">{{ t('workspace.validation.readinessDetailTitle') }}</span>
        </template>
        <p class="placeholder-text">{{ t('workspace.validation.readinessPlaceholder') }}</p>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import CoverageBar from './CoverageBar.vue'

defineProps<{
  refreshing: boolean
}>()

defineEmits<{
  refresh: []
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()
const coverageBarRef = ref<InstanceType<typeof CoverageBar> | null>(null)

const coverageWarning = computed(() => store.warnings.coverage || '')
</script>

<style scoped>
.readiness-dashboard {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-bg-color);
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
  gap: 8px;
}
.panel-title {
  font-weight: 600;
  font-size: 13px;
}
.dashboard-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.warn-alert {
  flex-shrink: 0;
}
.placeholder-card {
  flex-shrink: 0;
}
.card-header-text {
  font-size: 13px;
  font-weight: 500;
}
.placeholder-text {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>
