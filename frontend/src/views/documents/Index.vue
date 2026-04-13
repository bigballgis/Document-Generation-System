<template>
  <div class="documents-page">
    <div class="page-header">
      <h2>{{ $t('document.history') }}</h2>
      <el-button
        v-if="selectedIds.length >= 2"
        type="primary"
        @click="mergeDialogVisible = true"
      >
        {{ $t('document.merge') }} ({{ selectedIds.length }})
      </el-button>
    </div>

    <!-- Filters -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item :label="$t('document.templateId')">
          <el-input-number
            v-model="query.templateId"
            :placeholder="$t('document.templateId')"
            :min="1"
            :controls="false"
            clearable
            style="width: 160px"
            @change="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('document.status')">
          <el-select
            v-model="query.status"
            :placeholder="$t('common.all')"
            clearable
            style="width: 160px"
            @change="handleSearch"
          >
            <el-option label="GENERATED" value="GENERATED" />
            <el-option label="EXPIRED" value="EXPIRED" />
            <el-option label="DELETED" value="DELETED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('document.dateRange')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :start-placeholder="$t('document.startDate')"
            :end-placeholder="$t('document.endDate')"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 300px"
            @change="handleDateRangeChange"
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
      <el-table
        :data="documents"
        v-loading="loading"
        stripe
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" :label="$t('document.id')" width="80" />
        <el-table-column prop="templateId" :label="$t('document.templateId')" width="120" />
        <el-table-column prop="format" :label="$t('document.format')" width="100" />
        <el-table-column :label="$t('document.status')" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('document.fileSize')" width="120">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('document.pageCount')" width="100">
          <template #default="{ row }">
            {{ row.pageCount ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="generatedAt" :label="$t('document.generatedAt')" width="170" />
        <el-table-column :label="$t('document.expiresAt')" width="170">
          <template #default="{ row }">
            {{ row.expiresAt || '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.actions')" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleDownload(row)">
              {{ $t('common.download') }}
            </el-button>
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
          @size-change="fetchDocuments"
          @current-change="fetchDocuments"
        />
      </div>
    </el-card>

    <!-- Merge Dialog -->
    <MergeDialog
      v-model:visible="mergeDialogVisible"
      :document-ids="selectedIds"
      @merged="onMerged"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getDocuments, downloadDocument } from '@/api/documents'
import MergeDialog from './components/MergeDialog.vue'
import type { GeneratedDocumentDTO, DocumentQuery } from '@/types/document'

const { t } = useI18n()

const loading = ref(false)
const documents = ref<GeneratedDocumentDTO[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])
const mergeDialogVisible = ref(false)
const dateRange = ref<[string, string] | null>(null)

const query = reactive<DocumentQuery>({
  templateId: undefined,
  status: undefined,
  startTime: undefined,
  endTime: undefined,
  page: 1,
  size: 10,
})

type ElTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

function statusTagType(status: string): ElTagType {
  const map: Record<string, ElTagType> = {
    GENERATED: 'success',
    EXPIRED: 'warning',
    DELETED: 'danger',
  }
  return map[status] || 'info'
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

async function fetchDocuments() {
  loading.value = true
  try {
    const res = await getDocuments(query)
    documents.value = res.content
    total.value = res.totalElements
  } catch { /* interceptor handles */ } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  fetchDocuments()
}

function handleDateRangeChange(val: [string, string] | null) {
  if (val) {
    query.startTime = val[0]
    query.endTime = val[1]
  } else {
    query.startTime = undefined
    query.endTime = undefined
  }
  handleSearch()
}

function resetFilters() {
  query.templateId = undefined
  query.status = undefined
  query.startTime = undefined
  query.endTime = undefined
  dateRange.value = null
  handleSearch()
}

function handleSelectionChange(rows: GeneratedDocumentDTO[]) {
  selectedIds.value = rows.map((r) => r.id)
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

async function handleDownload(row: GeneratedDocumentDTO) {
  try {
    const blob = await downloadDocument(row.id) as unknown as Blob
    const filename = `document-${row.id}.${row.format.toLowerCase()}`
    triggerBlobDownload(blob, filename)
    ElMessage.success(t('message.downloadStarted'))
  } catch { /* interceptor handles */ }
}

function onMerged() {
  fetchDocuments()
}

onMounted(() => {
  fetchDocuments()
})
</script>

<style scoped>
.documents-page {
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
</style>
