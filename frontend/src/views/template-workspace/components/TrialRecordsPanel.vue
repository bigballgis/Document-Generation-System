<template>
  <div class="trial-records-panel">
    <p v-if="scenarioName" class="scenario-name">{{ scenarioName }}</p>
    <el-skeleton v-if="loading && !rows.length" :rows="4" animated />
    <el-alert v-else-if="loadError" type="error" :title="loadError" show-icon :closable="false" />
    <template v-else>
      <el-empty v-if="!rows.length" :description="t('workspace.validation.trialRecordsNoRows')" />
      <div v-else class="table-wrap">
        <el-table :data="rows" stripe size="small" class="records-table">
          <el-table-column :label="t('workspace.validation.trialRecordsExecutedAt')" min-width="150">
            <template #default="{ row }">
              {{ formatWhen(row.executedAt) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('workspace.validation.trialRecordsStatus')" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'PASSED' ? 'success' : 'danger'" size="small">
                {{ row.status === 'PASSED' ? t('workspace.validation.statusReady') : t('workspace.validation.statusNeedsFix') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('workspace.validation.trialRecordsSample')" width="120" align="center">
            <template #default="{ row }">
              <el-button
                v-if="row.sampleDocumentId"
                type="primary"
                link
                size="small"
                :loading="downloadingId === row.sampleDocumentId"
                @click="downloadSample(row)"
              >
                {{ t('workspace.validation.trialRecordsDownloadSample') }}
              </el-button>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('workspace.validation.trialRecordsSummary')" min-width="200">
            <template #default="{ row }">
              <span class="summary-text">{{ summaryText(row) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="totalElements > pageSize"
          class="pager"
          layout="prev, pager, next, total"
          :total="totalElements"
          :page-size="pageSize"
          :current-page="page + 1"
          background
          @current-change="onPageChange"
        />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getTestCaseResults, type TestResultDTO } from '@/api/market'
import { downloadDocument } from '@/api/documents'

const props = defineProps<{
  testCaseId: number
  scenarioName?: string | null
}>()

const { t } = useI18n()
const downloadingId = ref<number | null>(null)

const loading = ref(false)
const loadError = ref('')
const rows = ref<TestResultDTO[]>([])
const page = ref(0)
const pageSize = ref(20)
const totalElements = ref(0)

function formatWhen(iso: string) {
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return iso
  }
}

function summaryText(row: TestResultDTO) {
  const d = row.diffDetails?.trim()
  if (d) return d.length > 160 ? `${d.slice(0, 160)}…` : d
  const j = row.actualResultJson?.trim()
  if (j) return j.length > 120 ? `${j.slice(0, 120)}…` : j
  return '—'
}

async function load() {
  if (!props.testCaseId) return
  loading.value = true
  loadError.value = ''
  try {
    const res = await getTestCaseResults(props.testCaseId, page.value, pageSize.value)
    rows.value = res.content ?? []
    totalElements.value = res.totalElements ?? 0
  } catch (e: any) {
    rows.value = []
    totalElements.value = 0
    loadError.value = e?.message || t('workspace.validation.trialRecordsLoadFailed')
  } finally {
    loading.value = false
  }
}

function onPageChange(p1: number) {
  page.value = p1 - 1
  void load()
}

async function downloadSample(row: TestResultDTO) {
  const id = row.sampleDocumentId
  if (id == null) return
  downloadingId.value = id
  try {
    const blob = await downloadDocument(id)
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `trial-sample-${id}.docx`
    a.click()
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error(t('workspace.validation.trialRecordsDownloadFailed'))
  } finally {
    downloadingId.value = null
  }
}

watch(
  () => props.testCaseId,
  () => {
    page.value = 0
    void load()
  },
  { immediate: true },
)
</script>

<style scoped>
.trial-records-panel {
  min-height: 120px;
}
.scenario-name {
  margin: 0 0 12px;
  font-weight: 600;
  font-size: 14px;
}
.table-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.records-table {
  width: 100%;
}
.summary-text {
  font-size: 12px;
  color: var(--el-text-color-regular);
  line-height: 1.4;
  word-break: break-word;
}
.pager {
  justify-content: flex-end;
}
.muted {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
