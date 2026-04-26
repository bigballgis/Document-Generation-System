<template>
  <div class="test-case-management">
    <el-alert v-if="!hideIntro" type="info" show-icon :closable="false" class="hint-alert">
      <template #title>{{ $t('test.hintTitle') }}</template>
      <p class="hint-body">{{ $t('test.hintBody') }}</p>
    </el-alert>

    <!-- Toolbar -->
    <div class="toolbar">
      <el-button type="primary" @click="openCreateDialog">{{ $t('test.create') }}</el-button>
      <el-button :loading="runAllLoading" @click="handleRunAll">
        <el-icon><VideoPlay /></el-icon> {{ $t('test.runAll') }}
      </el-button>
    </div>
    <div class="toolbar toolbar-row2">
      <el-input
        v-model="searchText"
        clearable
        :placeholder="$t('test.searchPlaceholder')"
        style="max-width: 280px"
        @keyup.enter="handleSearch"
      />
      <el-button @click="handleSearch">{{ $t('common.search') }}</el-button>
    </div>

    <!-- Test Report Summary -->
    <el-card v-if="report" shadow="never" style="margin-bottom: 16px">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-statistic :title="$t('test.totalCases')" :value="report.totalCount" />
        </el-col>
        <el-col :span="6">
          <el-statistic :title="$t('test.passedCases')" :value="report.passedCount" style="--el-statistic-content-color: #67c23a" />
        </el-col>
        <el-col :span="6">
          <el-statistic :title="$t('test.failedCases')" :value="report.failedCount" style="--el-statistic-content-color: #f56c6c" />
        </el-col>
        <el-col :span="6">
          <div style="font-size: 13px; color: #999">{{ $t('test.executedAt') }}</div>
          <div>{{ formatInstant(report.executedAt) }}</div>
        </el-col>
      </el-row>
    </el-card>

    <!-- Test Cases Table -->
    <el-table :data="testCases" v-loading="loading" border stripe>
      <el-table-column prop="name" :label="$t('test.name')" min-width="160" />
      <el-table-column prop="comparisonType" :label="$t('test.compareMode')" width="150">
        <template #default="{ row }">
          <el-tag size="small" :type="comparisonTypeTagType(row.comparisonType)">
            {{ $t(`test.mode${comparisonTypeI18nSuffix(row.comparisonType)}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" :label="$t('common.createdAt')" width="170">
        <template #default="{ row }">
          {{ formatInstant(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column :label="$t('test.lastRunAt')" width="180">
        <template #default="{ row }">
          {{ formatInstant(row.lastRun?.executedAt) }}
        </template>
      </el-table-column>
      <el-table-column :label="$t('test.report')" width="110">
        <template #default="{ row }">
          <template v-if="resultMap[row.id]">
            <el-tag v-if="isTestPassed(resultMap[row.id])" type="success" size="small">{{ $t('test.passed') }}</el-tag>
            <el-tag v-else type="danger" size="small">{{ $t('test.failed') }}</el-tag>
          </template>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="handleRunSingle(row)" :loading="runningId === row.id">
            {{ $t('test.run') }}
          </el-button>
          <el-button
            v-if="resultMap[row.id]"
            size="small"
            link
            type="primary"
            @click="openResultDrawer(row, resultMap[row.id])"
          >
            {{ $t('test.viewDetails') }}
          </el-button>
          <el-button size="small" @click="openEditDialog(row)">{{ $t('common.edit') }}</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="totalElements > 0" class="pager">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="totalElements"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="fetchTestCases"
        @size-change="onPageSizeChange"
      />
    </div>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingCase ? $t('test.edit') : $t('test.create')" width="720px">
      <el-form :model="form" label-width="150px">
        <el-form-item :label="$t('test.name')" required>
          <el-input v-model="form.name" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item :label="$t('test.compareMode')">
          <el-select v-model="form.comparisonType" style="width: 100%">
            <el-option :label="$t('test.modeVariable')" value="VARIABLE_VALUE" />
            <el-option :label="$t('test.modeText')" value="TEXT_CONTENT" />
            <el-option :label="$t('test.modeSnapshot')" value="FILE_SNAPSHOT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <template #label>
            <span>{{ $t('test.testData') }}</span>
            <el-tooltip :content="$t('test.testDataTooltip')" placement="top">
              <el-icon class="label-help"><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
          <el-input v-model="form.testDataJson" type="textarea" :rows="8" placeholder='{"paramKey": "value"}' />
        </el-form-item>
        <el-form-item>
          <template #label>
            <span>{{ $t('test.expectedResult') }}</span>
            <el-tooltip :content="$t('test.expectedTooltip')" placement="top">
              <el-icon class="label-help"><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
          <el-input v-model="form.expectedResultJson" type="textarea" :rows="5" placeholder="{}" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="resultDrawerVisible" :title="$t('test.resultDrawerTitle')" size="480px" destroy-on-close>
      <template v-if="resultDrawerContext">
        <p><strong>{{ $t('test.name') }}:</strong> {{ resultDrawerContext.caseName }}</p>
        <p><strong>{{ $t('test.report') }}:</strong>
          <el-tag :type="isTestPassed(resultDrawerContext.result) ? 'success' : 'danger'" size="small">
            {{ isTestPassed(resultDrawerContext.result) ? $t('test.passed') : $t('test.failed') }}
          </el-tag>
        </p>
        <p><strong>{{ $t('test.executedAt') }}:</strong> {{ formatInstant(resultDrawerContext.result.executedAt) }}</p>
        <el-divider />
        <div v-if="resultDrawerContext.result.diffDetails" class="mono-block">
          <div class="section-title">{{ $t('test.diffDetails') }}</div>
          <pre>{{ resultDrawerContext.result.diffDetails }}</pre>
        </div>
        <div v-if="resultDrawerContext.result.actualResultJson" class="mono-block">
          <div class="section-title">{{ $t('test.actualSummary') }}</div>
          <pre>{{ prettyJson(resultDrawerContext.result.actualResultJson) }}</pre>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, withDefaults } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VideoPlay, QuestionFilled } from '@element-plus/icons-vue'
import {
  getTestCases, createTestCase, updateTestCase, deleteTestCase,
  runTestCase, runAllTestCases,
  isTestPassed,
  type TestCaseDTO, type TestResultDTO, type TestReportDTO,
  type ComparisonType,
} from '@/api/market'

const props = withDefaults(defineProps<{ templateId: number; hideIntro?: boolean }>(), {
  hideIntro: false,
})
const { t } = useI18n()

const loading = ref(false)
const testCases = ref<TestCaseDTO[]>([])
const searchText = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const totalElements = ref(0)
const report = ref<TestReportDTO | null>(null)
const resultMap = ref<Record<number, TestResultDTO>>({})
const runAllLoading = ref(false)
const runningId = ref<number | null>(null)

const dialogVisible = ref(false)
const editingCase = ref<TestCaseDTO | null>(null)
const saving = ref(false)
const form = reactive({
  name: '',
  testDataJson: '{}',
  expectedResultJson: '',
  comparisonType: 'VARIABLE_VALUE' as ComparisonType,
})

const resultDrawerVisible = ref(false)
const resultDrawerContext = ref<{ caseName: string; result: TestResultDTO } | null>(null)

function comparisonTypeTagType(ct: ComparisonType) {
  if (ct === 'VARIABLE_VALUE') return 'primary'
  if (ct === 'TEXT_CONTENT') return 'success'
  return 'warning'
}

function comparisonTypeI18nSuffix(ct: ComparisonType): 'Variable' | 'Text' | 'Snapshot' {
  if (ct === 'VARIABLE_VALUE') return 'Variable'
  if (ct === 'TEXT_CONTENT') return 'Text'
  return 'Snapshot'
}

function formatInstant(value: string | string[] | undefined): string {
  if (value == null) return '—'
  const s = Array.isArray(value) ? value.join(',') : String(value)
  return s.replace('T', ' ').replace('Z', ' UTC').slice(0, 32)
}

function prettyJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function validateJsonOrAlert(raw: string, label: string): boolean {
  const trimmed = raw?.trim() ?? ''
  if (!trimmed) return true
  try {
    JSON.parse(trimmed)
    return true
  } catch {
    ElMessage.error(t('test.invalidJson', { label }))
    return false
  }
}

function handleSearch() {
  currentPage.value = 1
  fetchTestCases()
}

function onPageSizeChange() {
  currentPage.value = 1
  fetchTestCases()
}

async function fetchTestCases() {
  loading.value = true
  try {
    const page = await getTestCases(props.templateId, {
      page: currentPage.value - 1,
      size: pageSize.value,
      q: searchText.value.trim() || undefined,
    })
    testCases.value = page.content
    totalElements.value = page.totalElements
    if (page.content.length === 0 && currentPage.value > 1 && page.totalElements > 0) {
      currentPage.value -= 1
      await fetchTestCases()
      return
    }
    const merged: Record<number, TestResultDTO> = { ...resultMap.value }
    for (const tc of page.content) {
      if (tc.lastRun) merged[tc.id] = tc.lastRun
    }
    resultMap.value = merged
  } catch { /* handled by interceptor */ } finally {
    loading.value = false
  }
}

defineExpose({ refreshTestCases: fetchTestCases })

function openCreateDialog() {
  editingCase.value = null
  form.name = ''
  form.testDataJson = '{}'
  form.expectedResultJson = ''
  form.comparisonType = 'VARIABLE_VALUE'
  dialogVisible.value = true
}

function openEditDialog(row: TestCaseDTO) {
  editingCase.value = row
  form.name = row.name
  form.testDataJson = row.testDataJson ?? '{}'
  form.expectedResultJson = row.expectedResultJson ?? ''
  form.comparisonType = row.comparisonType
  dialogVisible.value = true
}

function openResultDrawer(row: TestCaseDTO, result: TestResultDTO) {
  resultDrawerContext.value = { caseName: row.name, result }
  resultDrawerVisible.value = true
}

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning(t('test.nameRequired'))
    return
  }
  if (!validateJsonOrAlert(form.testDataJson, t('test.testData'))) return
  if (!validateJsonOrAlert(form.expectedResultJson, t('test.expectedResult'))) return

  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      testDataJson: form.testDataJson.trim() || '{}',
      expectedResultJson: form.expectedResultJson.trim() ? form.expectedResultJson.trim() : '{}',
      comparisonType: form.comparisonType,
    }
    if (editingCase.value) {
      await updateTestCase(editingCase.value.id, payload)
    } else {
      await createTestCase(props.templateId, payload)
      currentPage.value = 1
    }
    ElMessage.success(t('message.saveSuccess'))
    dialogVisible.value = false
    fetchTestCases()
  } catch { /* interceptor */ } finally {
    saving.value = false
  }
}

async function handleDelete(row: TestCaseDTO) {
  try {
    await ElMessageBox.confirm(t('confirm.deleteMessage'), t('confirm.deleteTitle'))
    await deleteTestCase(row.id)
    ElMessage.success(t('message.deleteSuccess'))
    const nextMap = { ...resultMap.value }
    delete nextMap[row.id]
    resultMap.value = nextMap
    fetchTestCases()
  } catch { /* cancelled */ }
}

async function handleRunSingle(row: TestCaseDTO) {
  runningId.value = row.id
  try {
    const result = await runTestCase(row.id)
    resultMap.value = { ...resultMap.value, [row.id]: result }
    ElMessage[isTestPassed(result) ? 'success' : 'warning'](
      isTestPassed(result) ? t('test.passed') : t('test.failed'),
    )
    await fetchTestCases()
  } catch { /* interceptor */ } finally {
    runningId.value = null
  }
}

async function handleRunAll() {
  runAllLoading.value = true
  try {
    const rpt = await runAllTestCases(props.templateId)
    report.value = rpt
    const map: Record<number, TestResultDTO> = {}
    rpt.results.forEach((r) => { map[r.testCaseId] = r })
    resultMap.value = map
    ElMessage.success(`${rpt.passedCount}/${rpt.totalCount} ${t('test.passed')}`)
    currentPage.value = 1
    await fetchTestCases()
  } catch { /* interceptor */ } finally {
    runAllLoading.value = false
  }
}

onMounted(fetchTestCases)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  margin-top: 12px;
}
.toolbar-row2 {
  margin-top: 0;
  margin-bottom: 12px;
  flex-wrap: wrap;
  align-items: center;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.hint-alert {
  margin-bottom: 12px;
}
.hint-body {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
}
.label-help {
  margin-left: 4px;
  vertical-align: middle;
  cursor: help;
  color: var(--el-color-info);
}
.mono-block {
  margin-bottom: 16px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}
pre {
  margin: 0;
  padding: 10px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  font-size: 12px;
  max-height: 280px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
