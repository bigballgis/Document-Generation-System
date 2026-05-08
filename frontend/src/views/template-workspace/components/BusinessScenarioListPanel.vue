<template>
  <div class="scenario-list-panel">
    <div class="panel-header">
      <span class="panel-title">{{ t('workspace.validation.scenarioList') }}</span>
    </div>
    <el-input
      :model-value="search"
      clearable
      size="small"
      :placeholder="t('workspace.validation.searchPlaceholder')"
      class="search-input"
      @update:model-value="$emit('update:search', $event)"
    />
    <div v-loading="loading" class="list-body">
      <el-empty
        v-if="!loading && filteredScenarios.length === 0"
        :description="t('workspace.testing.emptyTestData')"
        :image-size="80"
      />
      <ul v-else class="scenario-list">
        <li
          v-for="row in filteredScenarios"
          :key="row.id"
          class="scenario-row"
          :class="{ active: selectedId === row.id }"
          @click="$emit('select', row)"
        >
          <div class="row-main">
            <span class="row-name" :title="row.name">{{ row.name }}</span>
            <div class="row-badges">
              <el-tag v-if="readinessPct(row) != null" size="small" type="info">
                {{ readinessPct(row) }}%
              </el-tag>
              <el-tag size="small" :type="statusTagType(row)">{{ statusLabel(row) }}</el-tag>
            </div>
          </div>
          <div class="row-meta">
            <span v-if="row.lastRun" class="meta-time">{{ formatTime(row.lastRun.executedAt) }}</span>
            <span v-else class="meta-draft">{{ t('workspace.validation.statusDraft') }}</span>
          </div>
          <div class="row-actions" @click.stop>
            <el-button
              type="primary"
              link
              size="small"
              :loading="runningId === row.id"
              :disabled="readonly"
              @click="$emit('run-trial', row)"
            >
              {{ t('workspace.validation.runTrial') }}
            </el-button>
            <el-button type="primary" link size="small" @click="$emit('open', row)">
              {{ t('workspace.validation.open') }}
            </el-button>
            <el-button type="primary" link size="small" @click="$emit('trial-records', row)">
              {{ t('workspace.validation.trialRecords') }}
            </el-button>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { isTestPassed, type TestCaseDTO } from '@/api/templateTesting'

const props = defineProps<{
  scenarios: TestCaseDTO[]
  selectedId: number | null
  search: string
  loading: boolean
  runningId: number | null
  readonly: boolean
  readinessByTestCaseId?: Record<number, number | null | undefined>
}>()

defineEmits<{
  'update:search': [v: string]
  select: [row: TestCaseDTO]
  'run-trial': [row: TestCaseDTO]
  open: [row: TestCaseDTO]
  'trial-records': [row: TestCaseDTO]
}>()

const { t } = useI18n()

const filteredScenarios = computed(() => {
  const q = props.search.trim().toLowerCase()
  if (!q) return props.scenarios
  return props.scenarios.filter(s => s.name.toLowerCase().includes(q))
})

function formatTime(v: string | string[] | undefined): string {
  if (v == null) return ''
  const s = Array.isArray(v) ? v.join(',') : String(v)
  return s.replace('T', ' ').replace('Z', ' UTC').slice(0, 32)
}

function statusLabel(row: TestCaseDTO): string {
  if (!row.lastRun) return t('workspace.validation.statusDraft')
  return isTestPassed(row.lastRun) ? t('workspace.validation.statusReady') : t('workspace.validation.statusNeedsFix')
}

function statusTagType(row: TestCaseDTO): 'success' | 'warning' | 'info' | 'danger' {
  if (!row.lastRun) return 'info'
  return isTestPassed(row.lastRun) ? 'success' : 'danger'
}

function readinessPct(row: TestCaseDTO): number | null {
  const v = props.readinessByTestCaseId?.[row.id]
  if (v == null) return null
  if (Number.isNaN(Number(v))) return null
  return Math.round(Number(v) * 100) / 100
}
</script>

<style scoped>
.scenario-list-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-bg-color);
}
.panel-header {
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
}
.panel-title {
  font-weight: 600;
  font-size: 13px;
}
.search-input {
  padding: 8px 12px;
  flex-shrink: 0;
}
.list-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 8px 8px;
}
.scenario-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.scenario-row {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px 10px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.scenario-row:hover {
  background: var(--el-fill-color-light);
}
.scenario-row.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.row-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}
.row-badges {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.row-name {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}
.row-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}
.meta-draft {
  font-style: italic;
}
</style>
