<template>
  <div class="audit-page">
    <h2>{{ $t('audit.title') }}</h2>

    <!-- Filters -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item :label="$t('audit.filterByType')">
          <el-select v-model="filters.operationType" clearable style="width: 180px">
            <el-option :label="$t('audit.typeTemplateCreate')" value="TEMPLATE_CREATE" />
            <el-option :label="$t('audit.typeTemplateUpdate')" value="TEMPLATE_UPDATE" />
            <el-option :label="$t('audit.typeTemplateDelete')" value="TEMPLATE_DELETE" />
            <el-option :label="$t('audit.typePermissionChange')" value="PERMISSION_CHANGE" />
            <el-option :label="$t('audit.typeApiCall')" value="API_CALL" />
            <el-option :label="$t('audit.typeUserLogin')" value="USER_LOGIN" />
            <el-option :label="$t('audit.typeUserLogout')" value="USER_LOGOUT" />
            <el-option :label="$t('audit.typeStatusChange')" value="STATUS_CHANGE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('audit.filterByOperator')">
          <el-input v-model="filters.operator" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item :label="$t('audit.startDate')">
          <el-date-picker v-model="filters.startDate" type="datetime" style="width: 200px" />
        </el-form-item>
        <el-form-item :label="$t('audit.endDate')">
          <el-date-picker v-model="filters.endDate" type="datetime" style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">{{ $t('common.search') }}</el-button>
          <el-button @click="resetFilters">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Export buttons -->
    <div class="export-bar">
      <el-button @click="handleExport('CSV')">{{ $t('audit.exportCsv') }}</el-button>
      <el-button @click="handleExport('JSON')">{{ $t('audit.exportJson') }}</el-button>
    </div>

    <!-- Table -->
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="operationType" :label="$t('audit.operationType')" width="180">
        <template #default="{ row }">
          <el-tag size="small">{{ row.operationType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="operator" :label="$t('audit.operator')" width="140" />
      <el-table-column :label="$t('audit.operationTime')" width="180">
        <template #default="{ row }">{{ formatDate(row.operationTime) }}</template>
      </el-table-column>
      <el-table-column prop="resourceType" :label="$t('audit.resourceType')" width="140" />
      <el-table-column prop="resourceId" :label="$t('audit.resourceId')" width="120" />
      <el-table-column :label="$t('audit.result')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
            {{ row.result === 'SUCCESS' ? $t('audit.resultSuccess') : $t('audit.resultFailure') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ipAddress" :label="$t('audit.ipAddress')" width="140" />
      <el-table-column prop="operationDetail" :label="$t('audit.operationDetail')" min-width="200" show-overflow-tooltip />
    </el-table>

    <!-- Pagination -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[20, 50, 100]"
        @current-change="loadData"
        @size-change="loadData"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getAuditLogs, exportAuditLogs, type AuditLogDTO } from '@/api/audit'

const { t } = useI18n()

const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const list = ref<AuditLogDTO[]>([])

const filters = reactive({
  operationType: '',
  operator: '',
  startDate: '' as string | Date,
  endDate: '' as string | Date,
})

function formatDate(iso: string) {
  return iso ? new Date(iso).toLocaleString() : ''
}

function toISOOrUndefined(val: string | Date) {
  if (!val) return undefined
  return val instanceof Date ? val.toISOString() : val
}

async function loadData() {
  loading.value = true
  try {
    const res = await getAuditLogs({
      operationType: filters.operationType || undefined,
      operator: filters.operator || undefined,
      startDate: toISOOrUndefined(filters.startDate),
      endDate: toISOOrUndefined(filters.endDate),
      page: page.value - 1,
      size: pageSize.value,
    })
    list.value = res.content || []
    total.value = res.totalElements || 0
  } catch { /* interceptor */ } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.operationType = ''
  filters.operator = ''
  filters.startDate = ''
  filters.endDate = ''
  page.value = 1
  loadData()
}

async function handleExport(format: 'CSV' | 'JSON') {
  try {
    const blob = await exportAuditLogs(
      {
        operationType: filters.operationType || undefined,
        operator: filters.operator || undefined,
        startDate: toISOOrUndefined(filters.startDate),
        endDate: toISOOrUndefined(filters.endDate),
      },
      format,
    ) as unknown as Blob
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `audit-logs.${format.toLowerCase()}`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(t('message.exportSuccess'))
  } catch { /* interceptor */ }
}

onMounted(() => loadData())
</script>

<style scoped>
.audit-page {
  padding: 20px;
}
.audit-page h2 {
  margin: 0 0 16px;
}
.filter-card {
  margin-bottom: 16px;
}
.filter-card :deep(.el-card__body) {
  padding-bottom: 0;
}
.export-bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
