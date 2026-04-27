<template>
  <div class="validation-workspace">
    <div class="validation-toolbar">
      <el-button
        type="primary"
        :disabled="readonly"
        @click="openNewScenarioTab"
      >
        {{ t('workspace.validation.newScenario') }}
      </el-button>
      <el-button
        :loading="runAllLoading"
        :disabled="readonly"
        @click="handleRunAllTrials"
      >
        <el-icon><VideoPlay /></el-icon>
        {{ t('workspace.validation.runAllTrials') }}
      </el-button>
    </div>

    <div v-if="store.warnings.testCases" class="inline-warn">
      <el-alert type="warning" :title="store.warnings.testCases" show-icon :closable="false" />
    </div>

    <div class="validation-body">
      <div class="col-left">
        <BusinessScenarioListPanel
          v-model:search="listSearch"
          :scenarios="store.testCases"
          :selected-id="selectedScenarioId"
          :loading="listLoading"
          :running-id="runningId"
          :readonly="readonly"
          :readiness-by-test-case-id="readinessByTestCaseId"
          @select="onListSelect"
          @run-trial="onRunTrialFromList"
          @open="onOpenFromList"
          @trial-records="openTrialRecords"
        />
      </div>
      <div class="col-center">
        <div v-if="openTabs.length > 0" class="center-tabs">
          <div class="tab-bar">
            <div
              v-for="tab in openTabs"
              :key="tab.key"
              class="tab-item"
              :class="{ active: activeTabKey === tab.key }"
              @click="activeTabKey = tab.key"
            >
              <span class="tab-title">{{ tabTitle(tab) }}</span>
              <el-icon class="tab-close" @click.stop="closeTab(tab.key)"><Close /></el-icon>
            </div>
          </div>
          <div class="tab-panels">
            <div
              v-for="tab in openTabs"
              :key="`panel-${tab.key}`"
              v-show="activeTabKey === tab.key"
              class="tab-panel"
            >
              <BusinessScenarioEditorTabContent
                v-if="store.templateId"
                :template-id="store.templateId"
                :tab-key="tab.key"
                :is-new="tab.isNew"
                :test-case="testCaseForTab(tab)"
                :readonly="readonly"
                @saved="onScenarioSaved"
                @deleted="onScenarioDeleted"
                @run-complete="onRunComplete"
              />
            </div>
          </div>
        </div>
        <div v-else class="empty-center">
          <el-empty :description="t('workspace.validation.noOpenScenario')" />
        </div>
      </div>
      <div class="col-right">
        <TemplateReadinessDashboard
          :key="readinessKey"
          :refreshing="readinessRefreshing"
          @refresh="onRefreshReadiness"
        />
      </div>
    </div>

    <el-drawer
      v-model="trialRecordsVisible"
      :title="t('workspace.validation.trialRecordsTitle')"
      size="480px"
      destroy-on-close
    >
      <TrialRecordsPanel
        v-if="trialRecordsVisible && trialRecordsContext"
        :key="trialRecordsKey"
        :test-case-id="trialRecordsContext.id"
        :scenario-name="trialRecordsContext.name"
      />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { VideoPlay, Close } from '@element-plus/icons-vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { runAllTestCases, runTestCase, isTestPassed, type TestCaseDTO } from '@/api/market'
import { getScenarioReadiness } from '@/api/templates'
import type { ScenarioReadinessReportDTO } from '@/types/scenarioReadiness'
import BusinessScenarioListPanel from './BusinessScenarioListPanel.vue'
import BusinessScenarioEditorTabContent from './BusinessScenarioEditorTabContent.vue'
import TemplateReadinessDashboard from './TemplateReadinessDashboard.vue'
import TrialRecordsPanel from './TrialRecordsPanel.vue'

defineProps<{
  readonly: boolean
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

interface OpenTab {
  key: string
  testCaseId: number | null
  isNew: boolean
}

const listSearch = ref('')
const listLoading = ref(false)
const openTabs = ref<OpenTab[]>([])
const activeTabKey = ref('')
const selectedScenarioId = ref<number | null>(null)
const runningId = ref<number | null>(null)
const runAllLoading = ref(false)
const trialRecordsVisible = ref(false)
const trialRecordsContext = ref<TestCaseDTO | null>(null)
const trialRecordsKey = ref(0)
const readinessKey = ref(0)
const readinessRefreshing = ref(false)
const readinessByTestCaseId = ref<Record<number, number>>({})

async function refreshScenarioReadiness() {
  if (!store.templateId) {
    readinessByTestCaseId.value = {}
    return
  }
  try {
    const rpt = await getScenarioReadiness(store.templateId)
    const map: Record<number, number> = {}
    for (const s of (rpt as ScenarioReadinessReportDTO).scenarios ?? []) {
      map[s.testCaseId] = s.overallReadiness
    }
    readinessByTestCaseId.value = map
  } catch {
    readinessByTestCaseId.value = {}
  }
}

function testCaseForTab(tab: OpenTab): TestCaseDTO | null {
  if (tab.isNew || tab.testCaseId == null) return null
  return store.testCases.find(c => c.id === tab.testCaseId) ?? null
}

function tabTitle(tab: OpenTab): string {
  if (tab.isNew) return t('workspace.validation.newScenario')
  const tc = testCaseForTab(tab)
  return tc?.name || `Case ${tab.testCaseId}`
}

function openOrFocusCase(tc: TestCaseDTO) {
  const key = `case-${tc.id}`
  const exist = openTabs.value.find(t => t.key === key)
  if (exist) {
    activeTabKey.value = key
  } else {
    openTabs.value = [...openTabs.value, { key, testCaseId: tc.id, isNew: false }]
    activeTabKey.value = key
  }
  selectedScenarioId.value = tc.id
}

function onListSelect(row: TestCaseDTO) {
  selectedScenarioId.value = row.id
  openOrFocusCase(row)
}

function onOpenFromList(row: TestCaseDTO) {
  openOrFocusCase(row)
}

function onRunTrialFromList(row: TestCaseDTO) {
  runSingleTrial(row)
}

async function runSingleTrial(row: TestCaseDTO) {
  runningId.value = row.id
  try {
    const result = await runTestCase(row.id)
    ElMessage[isTestPassed(result) ? 'success' : 'warning'](
      isTestPassed(result) ? t('test.passed') : t('test.failed'),
    )
    await store.refreshTestCases()
    await refreshScenarioReadiness()
    if (trialRecordsVisible.value && trialRecordsContext.value?.id === row.id) {
      trialRecordsKey.value += 1
    }
  } catch {
    /* interceptor */
  } finally {
    runningId.value = null
  }
}

function openNewScenarioTab() {
  const key = `new-${Date.now()}`
  openTabs.value = [...openTabs.value, { key, testCaseId: null, isNew: true }]
  activeTabKey.value = key
  selectedScenarioId.value = null
}

function closeTab(key: string) {
  const idx = openTabs.value.findIndex(t => t.key === key)
  if (idx < 0) return
  const tab = openTabs.value[idx]!
  const next = openTabs.value.filter(t => t.key !== key)
  openTabs.value = next
  if (activeTabKey.value === key) {
    activeTabKey.value = next[0]?.key ?? ''
  }
  if (tab.testCaseId != null && selectedScenarioId.value === tab.testCaseId) {
    const still = next.some(
      t => t.testCaseId === tab.testCaseId,
    )
    if (!still) {
      selectedScenarioId.value = null
    }
  }
}

function onScenarioSaved(payload: { id: number; previousKey: string }) {
  const prev = payload.previousKey
  const idx = openTabs.value.findIndex(t => t.key === prev)
  if (idx >= 0) {
    const nextKey = `case-${payload.id}`
    const next = [...openTabs.value]
    next[idx] = { key: nextKey, testCaseId: payload.id, isNew: false }
    openTabs.value = next
    activeTabKey.value = nextKey
  }
  selectedScenarioId.value = payload.id
  void store.refreshTestCases()
  void refreshScenarioReadiness()
  readinessKey.value += 1
}

function onScenarioDeleted(id: number) {
  const nextKey = `case-${id}`
  void store.refreshTestCases()
  openTabs.value = openTabs.value.filter(t => t.key !== nextKey && t.testCaseId !== id)
  if (activeTabKey.value === nextKey) {
    activeTabKey.value = openTabs.value[0]?.key ?? ''
  }
  if (selectedScenarioId.value === id) {
    selectedScenarioId.value = null
  }
  if (trialRecordsContext.value?.id === id) {
    trialRecordsVisible.value = false
    trialRecordsContext.value = null
  }
  void refreshScenarioReadiness()
  readinessKey.value += 1
}

function onRunComplete() {
  void store.refreshTestCases()
  void refreshScenarioReadiness()
  if (trialRecordsVisible.value && trialRecordsContext.value?.id != null) {
    trialRecordsKey.value += 1
  }
  readinessKey.value += 1
}

async function handleRunAllTrials() {
  if (!store.templateId) return
  runAllLoading.value = true
  try {
    const rpt = await runAllTestCases(store.templateId)
    ElMessage.success(`${rpt.passedCount}/${rpt.totalCount} ${t('test.passed')}`)
    await store.refreshTestCases()
    await refreshScenarioReadiness()
    if (trialRecordsVisible.value) {
      trialRecordsKey.value += 1
    }
    readinessKey.value += 1
  } catch {
    /* interceptor */
  } finally {
    runAllLoading.value = false
  }
}

async function onRefreshReadiness() {
  readinessRefreshing.value = true
  try {
    await store.refreshParameters()
    await store.refreshCoverage()
    await refreshScenarioReadiness()
  } catch {
    /* best effort */
  } finally {
    readinessRefreshing.value = false
  }
}

function openTrialRecords(row: TestCaseDTO) {
  trialRecordsContext.value = row
  trialRecordsVisible.value = true
  trialRecordsKey.value += 1
}

onMounted(async () => {
  listLoading.value = true
  try {
    await store.refreshTestCases()
    await refreshScenarioReadiness()
  } finally {
    listLoading.value = false
  }
})

watch(
  () => store.templateId,
  () => {
    openTabs.value = []
    activeTabKey.value = ''
    selectedScenarioId.value = null
    listSearch.value = ''
    trialRecordsVisible.value = false
    trialRecordsContext.value = null
    trialRecordsKey.value = 0
    readinessByTestCaseId.value = {}
  },
)
</script>

<style scoped>
.validation-workspace {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: calc(100vh - 200px);
  min-height: 500px;
}
.validation-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  flex-wrap: wrap;
}
.inline-warn {
  flex-shrink: 0;
}
.validation-body {
  flex: 1;
  display: flex;
  gap: 12px;
  min-height: 0;
}
.col-left {
  flex: 0 0 min(28%, 320px);
  min-width: 240px;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.col-center {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-bg-color);
  overflow: hidden;
}
.col-right {
  flex: 0 0 min(30%, 360px);
  min-width: 260px;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.center-tabs {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
}
.tab-bar {
  display: flex;
  flex-wrap: nowrap;
  gap: 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  overflow-x: auto;
  flex-shrink: 0;
}
.tab-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 10px 14px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  border-bottom: 2px solid transparent;
  color: var(--el-text-color-secondary);
  font-weight: 500;
  max-width: 220px;
}
.tab-item:hover {
  color: var(--el-color-primary);
  background: var(--el-fill-color-light);
}
.tab-item.active {
  color: var(--el-color-primary);
  border-bottom-color: var(--el-color-primary);
}
.tab-title {
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}
.tab-close {
  font-size: 12px;
  border-radius: 50%;
  padding: 2px;
  flex-shrink: 0;
}
.tab-close:hover {
  background: var(--el-fill-color);
}
.tab-panels {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px 16px 16px;
}
.empty-center {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 200px;
  padding: 24px;
}
</style>