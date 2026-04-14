<template>
  <div class="testing-tab">
    <!-- Section 1: Test Data -->
    <div class="section">
      <div class="section-header">
        <h3>{{ t('workspace.testing.testData') }}</h3>
        <div class="section-actions">
          <el-button type="primary" @click="openAddTestCase">{{ t('workspace.testing.addTestCase') }}</el-button>
          <el-button @click="handleExport" :disabled="store.testCases.length === 0">{{ t('workspace.testing.export') }}</el-button>
          <el-upload :auto-upload="false" :show-file-list="false" accept=".json" :on-change="handleImportChange">
            <el-button :loading="importLoading">{{ t('workspace.testing.import') }}</el-button>
          </el-upload>
        </div>
      </div>
      <el-empty v-if="store.testCases.length === 0" :description="t('workspace.testing.emptyTestData')" />
      <el-table v-else :data="store.testCases" stripe>
        <el-table-column prop="name" :label="t('workspace.testing.name')" min-width="150" />
        <el-table-column :label="t('workspace.testing.compareMode')" width="140">
          <template #default="{ row }">
            <el-tag :type="compareModeTagType(row.compareMode)">{{ row.compareMode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('workspace.testing.testDataJson')" min-width="200">
          <template #default="{ row }">
            <el-tooltip :content="row.testData" placement="top" :show-after="300">
              <span class="truncated-json">{{ truncateJson(row.testData) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" :label="t('common.updatedAt')" width="180" />
        <el-table-column :label="t('common.actions')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="deletingIds.has(row.id)" @click="openEditTestCase(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button link type="primary" :loading="runningTestCaseId === row.id" :disabled="deletingIds.has(row.id)" @click="handleRunSingle(row.id)">
              {{ t('workspace.testing.run') }}
            </el-button>
            <el-button link type="danger" :loading="deletingIds.has(row.id)" @click="handleDeleteTestCase(row.id)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-divider />

    <!-- Section 2: Test Execution -->
    <div class="section">
      <div class="section-header">
        <h3>{{ t('workspace.testing.testExecution') }}</h3>
        <div class="section-actions">
          <el-button type="primary" :loading="runAllLoading" @click="handleRunAll">{{ t('workspace.testing.runAll') }}</el-button>
          <el-dropdown :disabled="store.testCases.length === 0" @command="handleQuickTest">
            <el-button :disabled="store.testCases.length === 0">{{ t('workspace.testing.quickTest') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="tc in store.testCases" :key="tc.id" :command="tc">{{ tc.name }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-dropdown :disabled="store.testCases.length === 0" @command="handleGenerateTestDoc">
            <el-button :disabled="store.testCases.length === 0">{{ t('workspace.testing.generateTestDoc') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="tc in store.testCases" :key="tc.id" :command="tc">{{ tc.name }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      <template v-if="store.testReport">
        <div class="test-summary">
          <span>{{ t('workspace.testing.total') }}: {{ store.testReport.totalTests }}</span>
          <span class="text-success">{{ t('workspace.testing.passed') }}: {{ store.testReport.passedTests }}</span>
          <span class="text-danger">{{ t('workspace.testing.failed') }}: {{ store.testReport.failedTests }}</span>
          <span>{{ t('workspace.testing.executedAt') }}: {{ store.testReport.executedAt }}</span>
        </div>
        <el-table :data="store.testReport.segmentResults" stripe>
          <el-table-column prop="testDataName" :label="t('workspace.testing.name')" min-width="150" />
          <el-table-column :label="t('workspace.testing.status')" width="100">
            <template #default="{ row }">
              <el-icon v-if="row.success" color="var(--el-color-success)"><Check /></el-icon>
              <el-icon v-else color="var(--el-color-danger)"><Close /></el-icon>
            </template>
          </el-table-column>
          <el-table-column prop="renderTimeMs" :label="t('workspace.testing.renderTime')" width="120">
            <template #default="{ row }">{{ row.renderTimeMs }}ms</template>
          </el-table-column>
          <el-table-column prop="errorMessage" :label="t('workspace.testing.errorMessage')" min-width="200" />
        </el-table>
      </template>
    </div>

    <el-divider />

    <!-- Section 3: Variable Coverage -->
    <div class="section">
      <div class="section-header">
        <h3>{{ t('workspace.testing.variableCoverage') }}</h3>
        <el-button :loading="coverageRefreshing" @click="handleRefreshCoverage">{{ t('workspace.testing.refreshCoverage') }}</el-button>
      </div>
      <template v-if="totalVariables === 0">
        <el-empty :description="t('workspace.testing.noVariables')" />
      </template>
      <template v-else>
        <div class="coverage-summary">
          <span class="coverage-percent">{{ overallCoveragePercent.toFixed(1) }}%</span>
          <el-progress :percentage="overallCoveragePercent" :color="coverageColor" :stroke-width="12" style="flex: 1" />
          <span>{{ boundVariables }} / {{ totalVariables }} {{ t('workspace.testing.variablesBound') }}</span>
        </div>
        <el-alert
          v-if="overallCoveragePercent < 100"
          type="warning"
          :title="t('workspace.testing.coverageWarning')"
          show-icon
          :closable="false"
          style="margin-bottom: 12px"
        >
          <div class="unbound-tags" v-if="templateCoverage?.unboundTags?.length">
            <el-tag v-for="tag in templateCoverage.unboundTags" :key="tag" type="danger" size="small" style="margin: 2px">{{ tag }}</el-tag>
          </div>
        </el-alert>
        <el-table :data="store.coverage?.segmentCoverages ?? []" stripe>
          <el-table-column prop="segmentName" :label="t('workspace.testing.segmentName')" min-width="150" />
          <el-table-column prop="totalVariables" :label="t('workspace.testing.totalVars')" width="120" />
          <el-table-column prop="boundVariables" :label="t('workspace.testing.boundVars')" width="120" />
          <el-table-column :label="t('workspace.testing.coveragePercent')" min-width="200">
            <template #default="{ row }">
              <el-progress :percentage="row.coveragePercent" :color="getCoverageColor(row.coveragePercent)" :stroke-width="8" />
            </template>
          </el-table-column>
          <el-table-column :label="t('workspace.testing.status')" width="80" align="center">
            <template #default="{ row }">
              <el-icon v-if="row.coveragePercent >= 100" color="var(--el-color-success)"><Check /></el-icon>
              <el-icon v-else color="var(--el-color-warning)"><Warning /></el-icon>
            </template>
          </el-table-column>
        </el-table>
        <el-collapse style="margin-top: 12px">
          <el-collapse-item :title="t('workspace.testing.unusedFields')">
            <template v-if="templateCoverage?.unusedFields?.length">
              <el-tag v-for="field in templateCoverage.unusedFields" :key="field" type="info" size="small" style="margin: 2px">{{ field }}</el-tag>
            </template>
            <span v-else>{{ t('common.noData') }}</span>
          </el-collapse-item>
        </el-collapse>
      </template>
    </div>

    <!-- Test Case Form Dialog -->
    <TestCaseFormDialog
      v-model:visible="testCaseDialogVisible"
      :template-id="store.templateId"
      :test-case="editingTestCase"
      @saved="onTestCaseSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Check, Close, Warning } from '@element-plus/icons-vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { deleteTestCase, exportTestCases, importTestCases, runTestCase } from '@/api/market'
import { previewCompositeTemplate } from '@/api/composite-templates'
import { getTemplateCoverage } from '@/api/templates'
import type { CoverageReport } from '@/api/templates'
import type { TestCaseDTO } from '@/api/market'
import type { UploadFile } from 'element-plus'
import TestCaseFormDialog from './TestCaseFormDialog.vue'

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

// ── Test Data ──
const testCaseDialogVisible = ref(false)
const editingTestCase = ref<TestCaseDTO | null>(null)
const deletingIds = ref<Set<number>>(new Set())
const testCasesLoaded = ref(false)

// ── Test Execution ──
const runAllLoading = ref(false)
const runningTestCaseId = ref<number | null>(null)

// ── Import/Export ──
const importLoading = ref(false)

// ── Coverage ──
const coverageRefreshing = ref(false)
const templateCoverage = ref<CoverageReport | null>(null)

// ── First load ──
async function loadTestCasesIfNeeded() {
  if (!testCasesLoaded.value) {
    await store.refreshTestCases()
    testCasesLoaded.value = true
  }
  if (!templateCoverage.value) {
    try {
      templateCoverage.value = await getTemplateCoverage(store.templateId)
    } catch { /* silent */ }
  }
}

onMounted(() => { loadTestCasesIfNeeded() })

// ── Test Data CRUD ──
function openAddTestCase() {
  editingTestCase.value = null
  testCaseDialogVisible.value = true
}

function openEditTestCase(row: TestCaseDTO) {
  editingTestCase.value = row
  testCaseDialogVisible.value = true
}

async function onTestCaseSaved() {
  testCaseDialogVisible.value = false
  await store.refreshTestCases()
}

async function handleDeleteTestCase(id: number) {
  try {
    await ElMessageBox.confirm(t('workspace.testing.deleteConfirm'), t('common.confirm'), { type: 'warning' })
  } catch { return }
  deletingIds.value.add(id)
  try {
    await deleteTestCase(id)
    await store.refreshTestCases()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.deleteFailed'))
  } finally {
    deletingIds.value.delete(id)
  }
}

// ── Export ──
async function handleExport() {
  try {
    const json = await exportTestCases(store.templateId)
    const blob = new Blob([typeof json === 'string' ? json : JSON.stringify(json)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `test-cases-${store.templateId}.json`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.exportFailed'))
  }
}

// ── Import ──
async function handleImportChange(uploadFile: UploadFile) {
  if (!uploadFile.raw) return
  importLoading.value = true
  try {
    const text = await uploadFile.raw.text()
    const imported = await importTestCases(store.templateId, text)
    await store.refreshTestCases()
    ElMessage.success(t('workspace.testing.importSuccess', { count: imported.length }))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.importFailed'))
  } finally {
    importLoading.value = false
  }
}

// ── Test Execution ──
async function handleRunAll() {
  runAllLoading.value = true
  try {
    // Run each test case individually and collect results
    const results = await Promise.allSettled(
      store.testCases.map(tc => runTestCase(tc.id)),
    )
    const passed = results.filter(r => r.status === 'fulfilled' && (r.value as any).passed).length
    const failed = results.length - passed
    store.testReport = {
      totalTests: results.length,
      passedTests: passed,
      failedTests: failed,
      segmentResults: results.map((r, i) => ({
        testDataName: store.testCases[i]?.name ?? '',
        success: r.status === 'fulfilled' && (r.value as any).passed,
        renderTimeMs: 0,
        errorMessage: r.status === 'rejected' ? (r.reason?.message ?? 'Failed') : (r.status === 'fulfilled' && !(r.value as any).passed ? (r.value as any).diffDetails : null),
      })),
      executedAt: new Date().toISOString(),
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('workspace.testing.runAllFailed'))
  } finally {
    runAllLoading.value = false
  }
}

async function handleRunSingle(testCaseId: number) {
  runningTestCaseId.value = testCaseId
  try {
    const result = await runTestCase(testCaseId)
    if (result.passed) {
      ElMessage.success(t('workspace.testing.testPassed'))
    } else {
      ElMessage.error(result.diffDetails || t('workspace.testing.testFailed'))
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('workspace.testing.runFailed'))
  } finally {
    runningTestCaseId.value = null
  }
}

async function handleQuickTest(_testCase: TestCaseDTO) {
  try {
    const result = await previewCompositeTemplate(store.templateId)
    window.open(result.previewUrl, '_blank')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('workspace.testing.quickTestFailed'))
  }
}

async function handleGenerateTestDoc(_testCase: TestCaseDTO) {
  try {
    const result = await previewCompositeTemplate(store.templateId)
    window.open(result.previewUrl, '_blank')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('workspace.testing.generateFailed'))
  }
}

// ── Coverage ──
async function handleRefreshCoverage() {
  coverageRefreshing.value = true
  try {
    await store.refreshCoverage()
    templateCoverage.value = await getTemplateCoverage(store.templateId)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.refreshFailed'))
  } finally {
    coverageRefreshing.value = false
  }
}

// ── Coverage computed ──
const overallCoveragePercent = computed(() => store.coverage?.overallCoveragePercent ?? 0)
const totalVariables = computed(() => {
  if (!store.coverage?.segmentCoverages) return 0
  return store.coverage.segmentCoverages.reduce((sum, s) => sum + s.totalVariables, 0)
})
const boundVariables = computed(() => {
  if (!store.coverage?.segmentCoverages) return 0
  return store.coverage.segmentCoverages.reduce((sum, s) => sum + s.boundVariables, 0)
})

const coverageColor = computed(() => {
  const pct = overallCoveragePercent.value
  if (pct >= 100) return '#67C23A'
  if (pct >= 50) return '#E6A23C'
  return '#F56C6C'
})

function getCoverageColor(pct: number): string {
  if (pct >= 100) return '#67C23A'
  if (pct >= 50) return '#E6A23C'
  return '#F56C6C'
}

type TagType = 'success' | 'info' | 'warning' | 'danger'
function compareModeTagType(mode: string): TagType {
  if (mode === 'VARIABLE') return 'success'
  if (mode === 'TEXT') return 'warning'
  return 'info'
}

function truncateJson(json: string, maxLen = 80): string {
  return json && json.length > maxLen ? json.substring(0, maxLen) + '...' : json
}
</script>

<style scoped>
.testing-tab {
  padding: 0;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.section-header h3 {
  margin: 0;
}
.section-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.test-summary {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
  font-size: 14px;
}
.text-success { color: var(--el-color-success); }
.text-danger { color: var(--el-color-danger); }
.coverage-summary {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}
.coverage-percent {
  font-size: 24px;
  font-weight: bold;
}
.truncated-json {
  font-family: monospace;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
