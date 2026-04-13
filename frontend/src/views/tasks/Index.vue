<template>
  <div class="tasks-page">
    <div class="page-header">
      <h2>{{ $t('task.title') }}</h2>
    </div>

    <!-- Filters -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item :label="$t('task.status')">
          <el-select
            v-model="query.status"
            :placeholder="$t('common.all')"
            clearable
            style="width: 160px"
            @change="handleSearch"
          >
            <el-option :label="$t('task.statusPending')" value="PENDING" />
            <el-option :label="$t('task.statusRunning')" value="RUNNING" />
            <el-option :label="$t('task.statusCompleted')" value="COMPLETED" />
            <el-option :label="$t('task.statusFailed')" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('task.templateId')">
          <el-input-number
            v-model="query.templateId"
            :placeholder="$t('task.templateId')"
            :min="1"
            :controls="false"
            clearable
            style="width: 160px"
            @change="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
          <el-button @click="resetFilters">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-card shadow="never" style="margin-top: 16px">
      <el-table :data="tasks" v-loading="loading" stripe>
        <el-table-column prop="taskId" :label="$t('task.taskId')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="taskType" :label="$t('task.taskType')" width="120" />
        <el-table-column prop="templateId" :label="$t('task.templateId')" width="120" />
        <el-table-column :label="$t('task.status')" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('task.progress')" min-width="180">
          <template #default="{ row }">
            <el-progress
              :percentage="getTaskProgress(row)"
              :status="progressStatus(row.status)"
              :stroke-width="14"
              :text-inside="true"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="$t('task.createdAt')" width="170" />
        <el-table-column prop="completedAt" :label="$t('task.completedAt')" width="170">
          <template #default="{ row }">
            {{ row.completedAt || '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.actions')" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'COMPLETED'"
              link type="primary" size="small"
              @click="handleDownload(row)"
            >
              {{ $t('task.downloadResult') }}
            </el-button>
            <el-tooltip
              v-if="row.status === 'FAILED' && row.errorMessage"
              :content="row.errorMessage"
              placement="top"
              :show-after="200"
            >
              <el-button link type="danger" size="small">
                {{ $t('task.errorMessage') }}
              </el-button>
            </el-tooltip>
            <span v-if="row.status === 'RUNNING' && pollingTaskId === row.taskId" class="polling-hint">
              {{ $t('task.pollingActive') }}
            </span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchTasks"
          @current-change="fetchTasks"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getTasks, downloadTaskResult } from '@/api/tasks'
import { useTaskPolling } from '@/composables/useTaskPolling'
import type { AsyncTaskDTO, TaskQuery } from '@/types/document'

const { t } = useI18n()

const loading = ref(false)
const tasks = ref<AsyncTaskDTO[]>([])
const total = ref(0)

const query = reactive<TaskQuery>({
  status: undefined,
  templateId: undefined,
  page: 1,
  size: 10,
})

// Polling for the first RUNNING task found
const pollingTaskId = ref<string | null>(null)
const { task: polledTask, progress: polledProgress, stop: stopPolling } = useTaskPolling(pollingTaskId)

type ElTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

function statusTagType(status: string): ElTagType {
  const map: Record<string, ElTagType> = {
    PENDING: 'info',
    RUNNING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger',
  }
  return map[status] || 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    PENDING: t('task.statusPending'),
    RUNNING: t('task.statusRunning'),
    COMPLETED: t('task.statusCompleted'),
    FAILED: t('task.statusFailed'),
  }
  return map[status] || status
}

function getTaskProgress(row: AsyncTaskDTO): number {
  // If this is the polled task, use live progress
  if (polledProgress.value && pollingTaskId.value === row.taskId) {
    return polledProgress.value.progress
  }
  return row.progress
}

function progressStatus(status: string) {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'exception'
  return undefined
}

async function fetchTasks() {
  loading.value = true
  try {
    const res = await getTasks(query)
    tasks.value = res.content
    total.value = res.totalElements
    startPollingForRunningTask()
  } catch { /* interceptor handles */ } finally {
    loading.value = false
  }
}

function startPollingForRunningTask() {
  const runningTask = tasks.value.find((t) => t.status === 'RUNNING')
  if (runningTask) {
    pollingTaskId.value = runningTask.taskId
  } else {
    pollingTaskId.value = null
  }
}

function handleSearch() {
  query.page = 1
  fetchTasks()
}

function resetFilters() {
  query.status = undefined
  query.templateId = undefined
  handleSearch()
}

function triggerBlobDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

async function handleDownload(row: AsyncTaskDTO) {
  try {
    const blob = await downloadTaskResult(row.taskId) as unknown as Blob
    const filename = `task-${row.taskId}-result.zip`
    triggerBlobDownload(blob, filename)
    ElMessage.success(t('message.downloadStarted'))
  } catch { /* interceptor handles */ }
}

// Watch polled task for status changes — refresh list when task completes
watch(polledTask, (newTask) => {
  if (newTask && (newTask.status === 'COMPLETED' || newTask.status === 'FAILED')) {
    // Update the task in the list with latest data
    const idx = tasks.value.findIndex((t) => t.taskId === newTask.taskId)
    if (idx !== -1) {
      tasks.value[idx] = { ...tasks.value[idx], ...newTask }
    }
    // Look for next running task
    startPollingForRunningTask()
  }
})

onMounted(() => {
  fetchTasks()
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<style scoped>
.tasks-page {
  padding: 0;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
}
.filter-card :deep(.el-form-item) {
  margin-bottom: 0;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.polling-hint {
  color: var(--el-color-warning);
  font-size: 12px;
  margin-left: 4px;
}
</style>
