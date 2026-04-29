<template>
  <div class="scheduled-task-management">
    <div class="toolbar">
      <el-button type="primary" @click="openCreateDialog">{{ $t('schedule.create') }}</el-button>
    </div>

    <el-table :data="tasks" v-loading="loading" border stripe>
      <el-table-column prop="name" :label="$t('common.name')" min-width="140" />
      <el-table-column prop="cronExpression" :label="$t('schedule.cronExpression')" width="160" />
      <el-table-column :label="$t('common.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? $t('schedule.enabled') : $t('schedule.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastExecution" :label="$t('schedule.lastExecution')" width="170">
        <template #default="{ row }">{{ row.lastExecution || '-' }}</template>
      </el-table-column>
      <el-table-column prop="nextExecution" :label="$t('schedule.nextExecution')" width="170">
        <template #default="{ row }">{{ row.nextExecution || '-' }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEditDialog(row)">{{ $t('common.edit') }}</el-button>
          <el-button
            v-if="!row.enabled"
            size="small"
            type="success"
            @click="handleToggle(row, true)"
          >{{ $t('common.enable') }}</el-button>
          <el-button
            v-else
            size="small"
            type="warning"
            @click="handleToggle(row, false)"
          >{{ $t('common.disable') }}</el-button>
          <el-button size="small" @click="openHistory(row)">{{ $t('schedule.executionHistory') }}</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingTask ? $t('schedule.edit') : $t('schedule.create')" width="520px">
      <el-form :model="form" label-width="140px">
        <el-form-item :label="$t('common.name')" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="$t('schedule.cronExpression')" required>
          <el-input v-model="form.cronExpression" :placeholder="'0 0 9 * * ?'" />
          <div style="font-size: 12px; color: #999; margin-top: 4px">{{ $t('schedule.cronHelp') }}</div>
        </el-form-item>
        <el-form-item :label="$t('schedule.maxRetries')">
          <el-input-number v-model="form.maxRetries" :min="0" :max="10" />
        </el-form-item>
        <el-form-item label="Data Source Params">
          <el-input v-model="form.dataSourceParams" type="textarea" :rows="4" placeholder='{"key": "value"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="historyVisible" :title="$t('schedule.executionHistory')" width="700px">
      <el-table :data="executions" v-loading="historyLoading" border stripe>
        <el-table-column prop="executionTime" :label="$t('schedule.executionTime')" width="180" />
        <el-table-column :label="$t('schedule.executionResult')" width="120">
          <template #default="{ row }">
            <el-tag :type="executionResultType(row.result)" size="small">
              {{ $t(`schedule.execution${row.result.charAt(0) + row.result.slice(1).toLowerCase()}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="documentId" label="Document ID" width="120">
          <template #default="{ row }">{{ row.documentId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="errorMessage" :label="$t('task.errorMessage')">
          <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
        </el-table-column>
      </el-table>
      <div v-if="executionTotal > 0" style="text-align: center; margin-top: 12px">
        <el-pagination
          v-model:current-page="executionPage"
          :total="executionTotal"
          :page-size="20"
          layout="prev, pager, next"
          @current-change="fetchExecutions"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getScheduledTasks, createScheduledTask, updateScheduledTask,
  deleteScheduledTask, enableScheduledTask, disableScheduledTask,
  getTaskExecutions,
  type ScheduledTaskDTO, type TaskExecutionDTO,
} from '@/api/market'

const props = defineProps<{ templateId: number }>()
const { t } = useI18n()

const loading = ref(false)
const tasks = ref<ScheduledTaskDTO[]>([])

const dialogVisible = ref(false)
const editingTask = ref<ScheduledTaskDTO | null>(null)
const saving = ref(false)
const form = reactive({
  name: '',
  cronExpression: '',
  maxRetries: 3,
  dataSourceParams: '',
})

const historyVisible = ref(false)
const historyLoading = ref(false)
const executions = ref<TaskExecutionDTO[]>([])
const executionTotal = ref(0)
const executionPage = ref(1)
const currentTaskId = ref<number | null>(null)

function executionResultType(result: string) {
  return result === 'SUCCESS' ? 'success' : result === 'FAILED' ? 'danger' : 'warning'
}

async function fetchTasks() {
  loading.value = true
  try {
    tasks.value = await getScheduledTasks(props.templateId)
  } catch {} finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingTask.value = null
  form.name = ''
  form.cronExpression = ''
  form.maxRetries = 3
  form.dataSourceParams = ''
  dialogVisible.value = true
}

function openEditDialog(row: ScheduledTaskDTO) {
  editingTask.value = row
  form.name = row.name
  form.cronExpression = row.cronExpression
  form.maxRetries = row.maxRetries
  form.dataSourceParams = row.dataSourceParams || ''
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.name.trim() || !form.cronExpression.trim()) return
  saving.value = true
  try {
    const data = { ...form }
    if (editingTask.value) {
      await updateScheduledTask(editingTask.value.id, data)
    } else {
      await createScheduledTask(props.templateId, data)
    }
    ElMessage.success(t('message.saveSuccess'))
    dialogVisible.value = false
    fetchTasks()
  } catch {} finally {
    saving.value = false
  }
}

async function handleToggle(row: ScheduledTaskDTO, enable: boolean) {
  const confirmKey = enable ? 'schedule.enableConfirm' : 'schedule.disableConfirm'
  try {
    await ElMessageBox.confirm(t(confirmKey), t('common.warning'))
    if (enable) {
      await enableScheduledTask(row.id)
    } else {
      await disableScheduledTask(row.id)
    }
    ElMessage.success(t('message.operationSuccess'))
    fetchTasks()
  } catch {}
}

async function handleDelete(row: ScheduledTaskDTO) {
  try {
    await ElMessageBox.confirm(t('confirm.deleteMessage'), t('confirm.deleteTitle'))
    await deleteScheduledTask(row.id)
    ElMessage.success(t('message.deleteSuccess'))
    fetchTasks()
  } catch {}
}

async function openHistory(row: ScheduledTaskDTO) {
  currentTaskId.value = row.id
  executionPage.value = 1
  historyVisible.value = true
  await fetchExecutions()
}

async function fetchExecutions() {
  if (!currentTaskId.value) return
  historyLoading.value = true
  try {
    const result = await getTaskExecutions(currentTaskId.value, executionPage.value - 1)
    executions.value = result.content
    executionTotal.value = result.totalElements
  } catch {} finally {
    historyLoading.value = false
  }
}

onMounted(fetchTasks)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
</style>

