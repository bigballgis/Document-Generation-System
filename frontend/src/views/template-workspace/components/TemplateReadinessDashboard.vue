<template>
  <div class="readiness-dashboard">
    <div class="panel-header">
      <div class="header-left">
        <span class="panel-title">{{ t('workspace.validation.readinessDashboard') }}</span>
        <span v-if="report?.checkedAt" class="panel-subtitle">{{ formatWhen(report.checkedAt) }}</span>
      </div>
      <el-button size="small" :loading="refreshing || loading" @click="onRefreshClick">
        {{ t('workspace.testing.refreshCoverage') }}
      </el-button>
    </div>
    <div class="dashboard-body">
      <el-skeleton v-if="loading && !report" :rows="3" animated />
      <template v-else-if="report">
        <el-card shadow="never" class="hero-card">
          <div class="hero-top">
            <div class="hero-metric">
              <div class="hero-metric-label">{{ t('workspace.validation.readinessOverall') }}</div>
              <div class="hero-metric-value">
                <span class="hero-pct">{{ formatPct(report.readiness.overallReadiness) }}</span>
                <el-tag :type="overallTagType" size="small">{{ overallBand }}</el-tag>
              </div>
            </div>
            <div v-if="coverageOverallLabel" class="hero-metric">
              <div class="hero-metric-label">{{ t('workspace.validation.coverageOverall') }}</div>
              <div class="hero-metric-value">
                <span class="hero-pct">{{ formatPct(coverageOverallPct) }}</span>
                <el-tag type="info" size="small">{{ coverageOverallLabel }}</el-tag>
              </div>
            </div>
          </div>
          <CoverageBar
            :branch-coverage="report.readiness.conditionalClauseReadiness"
            :loop-coverage="report.readiness.repeatingDetailReadiness"
            :parameter-coverage="report.readiness.requiredInformationReadiness"
          />
          <div v-if="hasAnyCoverageTotals" class="coverage-detail">
            <div class="coverage-detail-row">
              <span class="coverage-detail-label">{{ t('workspace.validation.coverageBranch') }}</span>
              <span class="coverage-detail-value">
                {{ coverageLabel(report.readiness.coveredBranches, report.readiness.totalBranches) }}
              </span>
            </div>
            <div class="coverage-detail-row">
              <span class="coverage-detail-label">{{ t('workspace.validation.coverageLoop') }}</span>
              <span class="coverage-detail-value">
                {{ coverageLabel(report.readiness.coveredLoopScenarios, report.readiness.totalLoopScenarios) }}
              </span>
            </div>
            <div class="coverage-detail-row">
              <span class="coverage-detail-label">{{ t('workspace.validation.coverageParam') }}</span>
              <span class="coverage-detail-value">
                {{ coverageLabel(report.readiness.coveredParameters, report.readiness.totalParameters) }}
              </span>
            </div>
          </div>
        </el-card>
        <el-alert
          v-for="(w, idx) in localizedWarnings"
          :key="`w-${idx}`"
          type="warning"
          :title="w"
          show-icon
          :closable="false"
          class="warn-alert"
        />
        <el-alert
          v-if="coverageWarning"
          type="warning"
          :title="coverageWarning"
          show-icon
          :closable="false"
          class="warn-alert"
        />
        <el-card v-if="report.readiness.missingSituations?.length" shadow="never" class="detail-card">
          <template #header>
            <span class="card-header-text">{{ t('workspace.validation.missingSituationsTitle') }}</span>
          </template>
          <ul class="situation-list">
            <li v-for="(m, i) in report.readiness.missingSituations" :key="`m-${i}`">
              {{ formatMissingSituation(m) }}
            </li>
          </ul>
        </el-card>
        <el-card v-if="report.suggestions?.length" shadow="never" class="detail-card">
          <template #header>
            <span class="card-header-text">{{ t('workspace.validation.suggestedNextSteps') }}</span>
          </template>
          <ul class="suggestion-list">
            <li v-for="(s, i) in report.suggestions" :key="`s-${i}`">
              <strong>{{ suggestionTitle(s) }}</strong>
              <span v-if="s.reason" class="suggestion-reason"> — {{ s.reason }}</span>
            </li>
          </ul>
        </el-card>
      </template>
      <el-empty v-else-if="loadError" :description="loadError" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { getScenarioReadiness } from '@/api/templates'
import type { MissingBusinessSituationDTO, ScenarioReadinessReportDTO, ScenarioSuggestionDTO } from '@/types/scenarioReadiness'
import CoverageBar from './CoverageBar.vue'

defineProps<{
  refreshing: boolean
}>()

const emit = defineEmits<{
  refresh: []
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

const report = ref<ScenarioReadinessReportDTO | null>(null)
const loading = ref(false)
const loadError = ref('')

const coverageWarning = computed(() => store.warnings.coverage || '')

function formatPct(v: number) {
  return `${Math.round(v * 100) / 100}%`
}

function formatWhen(iso: string) {
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return iso
  }
}

function safeRatioPct(covered: number, total: number): number {
  const c = Number.isFinite(covered) ? covered : 0
  const t = Number.isFinite(total) ? total : 0
  if (t <= 0) return 0
  return Math.round((c / t) * 10000) / 100
}

function coverageLabel(covered: number, total: number): string {
  const pct = safeRatioPct(covered, total)
  return `${covered}/${total} (${formatPct(pct)})`
}

function overallBandLabel(pct: number): string {
  if (pct >= 90) return t('workspace.validation.readinessBand90')
  if (pct >= 70) return t('workspace.validation.readinessBand70')
  if (pct > 0) return t('workspace.validation.readinessBand1')
  return t('workspace.validation.readinessBand0')
}

const overallBand = computed(() => {
  if (!report.value) return ''
  return overallBandLabel(report.value.readiness.overallReadiness)
})

const hasAnyCoverageTotals = computed(() => {
  const r = report.value?.readiness
  if (!r) return false
  return (r.totalBranches ?? 0) > 0 || (r.totalLoopScenarios ?? 0) > 0 || (r.totalParameters ?? 0) > 0
})

const coverageOverallPct = computed(() => {
  const r = report.value?.readiness
  if (!r) return 0
  const covered = (r.coveredBranches ?? 0) + (r.coveredLoopScenarios ?? 0) + (r.coveredParameters ?? 0)
  const total = (r.totalBranches ?? 0) + (r.totalLoopScenarios ?? 0) + (r.totalParameters ?? 0)
  return safeRatioPct(covered, total)
})

const coverageOverallLabel = computed(() => {
  const r = report.value?.readiness
  if (!r) return ''
  const total = (r.totalBranches ?? 0) + (r.totalLoopScenarios ?? 0) + (r.totalParameters ?? 0)
  if (total <= 0) return ''
  return t('workspace.validation.coverageFromSituations', { total })
})

const overallTagType = computed(() => {
  const p = report.value?.readiness.overallReadiness ?? 0
  if (p >= 90) return 'success'
  if (p >= 70) return 'warning'
  return 'info'
})

const localizedWarnings = computed(() => {
  const raw = report.value?.warnings ?? []
  return raw.map((w) => {
    if (w === 'COMPOSITE_READINESS_USES_VARIABLE_AGGREGATE_ONLY') {
      return t('workspace.validation.warningCompositeReadinessLimited')
    }
    if (w.startsWith('PLACEHOLDER_SCAN_FAILED:')) {
      return t('workspace.validation.warningPlaceholderScanFailed')
    }
    if (w === 'TEMPLATE_FILE_MISSING_PLACEHOLDER_SCAN_SKIPPED') {
      return t('workspace.validation.warningNoTemplateFile')
    }
    if (w.startsWith('TEST_CASE_JSON_INVALID:')) {
      return t('workspace.validation.warningInvalidScenarioJson')
    }
    return w
  })
})

function formatMissingSituation(m: MissingBusinessSituationDTO) {
  if (m.type === 'BRANCH') {
    return t('workspace.validation.missingBranch', { name: m.name, path: m.missingPath })
  }
  if (m.type === 'LOOP') {
    return t('workspace.validation.missingLoop', { name: m.name, path: m.missingPath })
  }
  if (m.type === 'PARAMETER') {
    return t('workspace.validation.missingParameter', { name: m.name })
  }
  return `${m.type}: ${m.name} (${m.missingPath})`
}

function suggestionTitle(s: ScenarioSuggestionDTO) {
  const code = s.code
  const name = s.sourceMissingSituation?.name ?? ''
  if (code === 'BRANCH_MISSING_TRUE') return t('workspace.validation.suggestionBranchTrue', { name })
  if (code === 'BRANCH_MISSING_FALSE') return t('workspace.validation.suggestionBranchFalse', { name })
  if (code === 'LOOP_MISSING_EMPTY') return t('workspace.validation.suggestionLoopEmpty', { name })
  if (code === 'LOOP_MISSING_NONEMPTY') return t('workspace.validation.suggestionLoopNonempty', { name })
  if (code === 'PARAMETER_MISSING_VALUE') return t('workspace.validation.suggestionParameter', { name })
  return s.title || t('workspace.validation.suggestionGeneric')
}

async function loadReport() {
  if (!store.templateId) {
    report.value = null
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    report.value = await getScenarioReadiness(store.templateId)
  } catch (e: any) {
    report.value = null
    loadError.value = e?.message || t('workspace.validation.readinessLoadFailed')
  } finally {
    loading.value = false
  }
}

async function onRefreshClick() {
  await loadReport()
  emit('refresh')
}

onMounted(() => {
  void loadReport()
})

watch(
  () => store.templateId,
  () => {
    void loadReport()
  },
)
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
.header-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.panel-subtitle {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
.overall-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
}
.overall-label {
  font-weight: 500;
  color: var(--el-text-color-regular);
}
.overall-pct {
  color: var(--el-text-color-secondary);
}
.hero-card {
  border-radius: 10px;
}
.hero-top {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  margin-bottom: 10px;
}
.hero-metric-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.hero-metric-value {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.hero-pct {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.2px;
}
.coverage-detail {
  display: grid;
  grid-template-columns: 1fr;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 6px;
  border: 1px dashed var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
  margin-top: 10px;
}
.coverage-detail-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.coverage-detail-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.coverage-detail-value {
  font-size: 12px;
  color: var(--el-text-color-regular);
  font-variant-numeric: tabular-nums;
}
.warn-alert {
  flex-shrink: 0;
}
.detail-card {
  flex-shrink: 0;
}
.card-header-text {
  font-size: 13px;
  font-weight: 500;
}
.situation-list,
.suggestion-list {
  margin: 0;
  padding-left: 1.1rem;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
}
.suggestion-reason {
  color: var(--el-text-color-secondary);
  font-weight: normal;
}
</style>
