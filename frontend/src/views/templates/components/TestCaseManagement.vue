<template>
  <div class="test-case-management">
    <!-- Toolbar -->
    <div class="toolbar">
      <el-button type="primary" @click="openCreateDialog">{{ $t('test.create') }}</el-button>
      <el-button :loading="runAllLoading" @click="handleRunAll">
        <el-icon><VideoPlay /></el-icon> {{ $t('test.runAll') }}
      </el-button>
    </div>

    <!-- Test Report Summary -->
    <el-card v-if="report" shadow="never" style="margin-bottom: 16px">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-statistic :title="$t('test.totalCases')" :value="report.totalCases" />
        </el-col>
        <el-col :span="6">
          <el-statistic :title="$t('test.passedCases')" :value="report.passedCases" style="--el-statistic-content-color: #67c23a" />
        </el-col>
        <el-col :span="6">
          <el-statistic :title="$t('test.failedCases')" :value="report.failedCases" style="--el-statistic-content-color: #f56c6c" />
        </el-col>
        <el-col :span="6">
          <div style="font-size: 13px; color: #999">{{ $t('common.updatedAt') }}</div>
          <div>{{ report.executedAt }}</div>
        </el-col>
      </el-row>
    </el-card>

    <!-- Test Cases Table -->
    <el-table :data="testCases" v-loading="loading" border stripe>
      <el-table-column prop="name" :label="$t('test.name')" min-width="160" />
      <el-table-column prop="compareMode" :label="$t('test.compareMode')" width="130">
        <template #default="{ row }">
          <el-tag size="small" :type="compareModeType(row.compareMode)">
            {{ $t(`test.mode${compareModeLabel(row.compareMode)}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" :label="$t('common.createdAt')" width="170" />
      <el-table-column :label="$t('test.report')" width="100">
        <template #default="{ row }">
          <template v-if="resultMap[row.id]">
            <el-tag v-if="resultMap[row.id].passed" type="success" size="small">{{ $t('test.passed') }}</el-tag>
            <el-tag v-else type="danger" size="small">{{ $t('test.failed') }}</el-tag>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="handleRunSingle(row)" :loading="runningId === row.id">
            {{ $t('test.run') }}
          </el-button>
          <el-button size="small" @click="openEditDialog(row)">{{ $t('common.edit') }}</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingCase ? $t('test.edit') : $t('test.create')" width="640px">
      <el-form :model="form" label-width="130px">
        <el-form-item :label="$t('test.name')" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="$t('test.compareMode')">
          <el-select v-model="form.compareMode">
            <el-option :label="$t('test.modeVariable')" value="VARIABLE" />
            <el-option :label="$t('test.modeText')" value="TEXT" />
            <el-option :label="$t('test.modeSnapshot')" value="SNAPSHOT" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('test.testData')">
          <el-input v-model="form.testData" type="textarea" :rows="6" placeholder='{"key": "value"}' />
        </el-form-item>
        <el-form-item :label="$t('test.expectedResult')">
          <el-input v-model="form.expectedResult" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VideoPlay } from '@element-plus/icons-vue'
import {
  getTestCases, createTestCase, updateTestCase, deleteTestCase,
  runTestCase, runAllTestCases,
  type TestCaseDTO, type TestResultDTO, type TestReportDTO,
} from '@/api/market'

const props = defineProps<{ templateId: number }>()
const { t } = useI18n()

const loading = ref(false)
const testCases = ref<TestCaseDTO[]>([])
const report = ref<TestReportDTO | null>(null)
const resultMap = ref<Record<number, TestResultDTO>>({})
const runAllLoading = ref(false)
const runningId = ref<number | null>(null)

// Dialog
const dialogVisible = ref(false)
const editingCase = ref<TestCaseDTO | null>(null)
const saving = ref(false)
const form = reactive({
  name: '',
  testData: '',
  expectedResult: '',
  compareMode: 'VARIABLE' as 'VARIABLE' | 'TEXT' | 'SNAPSHOT',
})

function compareModeType(mode: string) {
  return mode === 'VARIABLE' ? 'primary' : mode === 'TEXT' ? 'success' : 'warning'
}

function compareModeLabel(mode: string) {
  return mode === 'VARIABLE' ? 'Variable' : mode === 'TEXT' ? 'Text' : 'Snapshot'
}

async function fetchTestCases() {
  loading.value = true
  try {
    testCases.value = await getTestCases(props.templateId)
  } catch { /* handled */ } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingCase.value = null
  form.name = ''
  form.testData = ''
  form.expectedResult = ''
  form.compareMode = 'VARIABLE'
  dialogVisible.value = true
}

function openEditDialog(row: TestCaseDTO) {
  editingCase.value = row
  form.name = row.name
  form.testData = row.testData
  form.expectedResult = row.expectedResult
  form.compareMode = row.compareMode
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.name.trim()) return
  saving.value = true
  try {
    const data = { ...form }
    if (editingCase.value) {
      await updateTestCase(editingCase.value.id, data)
    } else {
      await createTestCase(props.templateId, data)
    }
    ElMessage.success(t('message.saveSuccess'))
    dialogVisible.value = false
    fetchTestCases()
  } catch { /* handled */ } finally {
    saving.value = false
  }
}

async function handleDelete(row: TestCaseDTO) {
  try {
    await ElMessageBox.confirm(t('confirm.deleteMessage'), t('confirm.deleteTitle'))
    await deleteTestCase(row.id)
    ElMessage.success(t('message.deleteSuccess'))
    fetchTestCases()
  } catch { /* cancelled */ }
}

async function handleRunSingle(row: TestCaseDTO) {
  runningId.value = row.id
  try {
    const result = await runTestCase(row.id)
    resultMap.value[row.id] = result
    ElMessage[result.passed ? 'success' : 'warning'](
      result.passed ? t('test.passed') : t('test.failed'),
    )
  } catch { /* handled */ } finally {
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
    ElMessage.success(`${rpt.passedCases}/${rpt.totalCases} ${t('test.passed')}`)
  } catch { /* handled */ } finally {
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
}
</style>
