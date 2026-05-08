<template>
  <div class="generation-hub">
    <div class="page-header">
      <div>
        <h2>{{ $t('generation.title') }}</h2>
        <p class="subtitle">{{ $t('generation.subtitle') }}</p>
      </div>
      <div class="header-actions">
        <el-button @click="router.push('/documents')">{{ $t('nav.documents') }}</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" type="border-card" class="hub-tabs" @tab-click="onTabClick">
      <el-tab-pane :label="$t('generation.tabApi')" name="api">
        <el-alert type="info" :closable="false" show-icon class="mb-16">
          {{ $t('generation.queueExplain') }}
        </el-alert>

        <div class="endpoint-grid">
          <el-card v-for="ep in endpointDefs" :key="ep.id" shadow="hover" class="endpoint-card">
            <div class="ep-method">
              <el-tag :type="ep.method === 'GET' ? 'success' : 'primary'" size="small">{{ ep.method }}</el-tag>
              <code class="ep-path">{{ ep.path }}</code>
            </div>
            <p class="ep-desc">{{ $t(ep.descKey) }}</p>
            <el-button size="small" @click="copyText(ep.path)">{{ $t('generation.copyPath') }}</el-button>
          </el-card>
        </div>

        <el-divider />
        <h3>{{ $t('generation.externalClients') }}</h3>
        <p class="hint">{{ $t('generation.apiKeyHint') }}</p>
        <ul class="bullet-list">
          <li>{{ $t('generation.hintSync') }}</li>
          <li>{{ $t('generation.hintAsync') }}</li>
          <li>{{ $t('generation.hintBatch') }}</li>
          <li>{{ $t('generation.hintTasks') }}</li>
        </ul>
      </el-tab-pane>

      <el-tab-pane :label="$t('generation.tabSubmit')" name="submit">
        <el-form label-width="160px" class="submit-form">
          <el-form-item :label="$t('generation.fieldTemplateId')" required>
            <el-input-number v-model="submitForm.templateId" :min="1" :controls="true" />
          </el-form-item>
          <el-form-item :label="$t('generation.fieldVersion')">
            <el-input-number v-model="submitForm.version" :min="1" :controls="true" />
          </el-form-item>
          <el-form-item :label="$t('generation.fieldOutputFormat')">
            <el-select v-model="submitForm.outputFormat" style="width: 220px">
              <el-option label="WORD" value="WORD" />
              <el-option label="PDF" value="PDF" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('generation.fieldStorage')">
            <el-select v-model="submitForm.storageStrategy" style="width: 220px">
              <el-option label="TEMP" value="TEMP" />
              <el-option label="PERSISTENT" value="PERSISTENT" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('generation.fieldParametersJson')">
            <el-input v-model="submitForm.parametersJson" type="textarea" :rows="8" class="mono" />
          </el-form-item>
          <el-form-item :label="$t('generation.fieldBatchDataSets')">
            <el-input v-model="submitForm.batchDataSetsJson" type="textarea" :rows="6" class="mono" />
            <div class="field-hint">{{ $t('generation.batchJsonHint') }}</div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submittingSync" @click="runSync">
              {{ $t('generation.btnSync') }}
            </el-button>
            <el-button type="warning" :loading="submittingAsync" @click="runAsync">
              {{ $t('generation.btnAsync') }}
            </el-button>
            <el-button type="danger" :loading="submittingBatch" @click="runBatch">
              {{ $t('generation.btnBatch') }}
            </el-button>
          </el-form-item>
        </el-form>

        <el-card v-if="lastSyncResult" shadow="never" class="result-card">
          <template #header>{{ $t('generation.lastSyncResult') }}</template>
          <p><strong>documentId:</strong> {{ lastSyncResult.documentId }}</p>
          <p v-if="lastSyncResult.downloadUrl">
            <el-link :href="lastSyncResult.downloadUrl" target="_blank" type="primary">
              {{ $t('generation.openDownload') }}
            </el-link>
          </p>
        </el-card>

        <el-card v-if="lastTaskId" shadow="never" class="result-card">
          <template #header>{{ $t('generation.taskQueued') }}</template>
          <code>{{ lastTaskId }}</code>
          <el-button text type="primary" @click="goQueueTab">{{ $t('generation.viewInQueue') }}</el-button>
        </el-card>
      </el-tab-pane>

      <el-tab-pane :label="$t('generation.tabQueue')" name="queue">
        <AsyncTasksPanel />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import AsyncTasksPanel from '@/views/tasks/AsyncTasksPanel.vue'
import {
  generateDocument,
  generateDocumentAsync,
  generateDocumentBatch,
} from '@/api/generate'
import type { GenerateDocumentResponse } from '@/types/document'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const TAB_NAMES = ['api', 'submit', 'queue'] as const
type TabName = (typeof TAB_NAMES)[number]

const activeTab = ref<TabName>('api')

const endpointDefs = [
  { id: 'sync', method: 'POST', path: '/api/generate/{templateId}', descKey: 'generation.epSyncDesc' },
  { id: 'word', method: 'POST', path: '/api/generate/{templateId}/word', descKey: 'generation.epWordDesc' },
  { id: 'pdf', method: 'POST', path: '/api/generate/{templateId}/pdf', descKey: 'generation.epPdfDesc' },
  { id: 'asyncW', method: 'POST', path: '/api/generate/{templateId}/async/word', descKey: 'generation.epAsyncWordDesc' },
  { id: 'asyncP', method: 'POST', path: '/api/generate/{templateId}/async/pdf', descKey: 'generation.epAsyncPdfDesc' },
  { id: 'async', method: 'POST', path: '/api/generate/{templateId}/async', descKey: 'generation.epAsyncDesc' },
  { id: 'batch', method: 'POST', path: '/api/generate/{templateId}/batch', descKey: 'generation.epBatchDesc' },
  { id: 'tasks', method: 'GET', path: '/api/tasks', descKey: 'generation.epTasksDesc' },
  { id: 'taskOne', method: 'GET', path: '/api/tasks/{taskId}', descKey: 'generation.epTaskOneDesc' },
]

const submitForm = reactive({
  templateId: 1 as number | undefined,
  version: undefined as number | undefined,
  outputFormat: 'WORD',
  storageStrategy: 'TEMP',
  parametersJson: '{}',
  batchDataSetsJson: '[\n  { "param_example": "value" }\n]',
})

const submittingSync = ref(false)
const submittingAsync = ref(false)
const submittingBatch = ref(false)
const lastSyncResult = ref<GenerateDocumentResponse | null>(null)
const lastTaskId = ref<string | null>(null)

function syncTabFromRoute() {
  const tab = route.query.tab as string
  if (tab && TAB_NAMES.includes(tab as TabName)) {
    activeTab.value = tab as TabName
  }
}

function onTabClick(pane: { paneName?: string | number }) {
  if (pane.paneName === undefined) return
  const name = String(pane.paneName) as TabName
  router.replace({ path: '/generation', query: { tab: name } })
}

watch(
  () => route.query.tab,
  () => syncTabFromRoute(),
  { immediate: true },
)

function copyText(text: string) {
  navigator.clipboard.writeText(text).then(
    () => ElMessage.success(t('generation.copied')),
    () => ElMessage.error(t('generation.copyFailed')),
  )
}

function parseParameters(): Record<string, unknown> {
  const raw = submitForm.parametersJson.trim() || '{}'
  try {
    const v = JSON.parse(raw)
    if (v === null || typeof v !== 'object' || Array.isArray(v)) {
      throw new Error('invalid')
    }
    return v as Record<string, unknown>
  } catch {
    throw new Error('invalid')
  }
}

function buildBody() {
  return {
    parameters: parseParameters(),
    outputFormat: submitForm.outputFormat,
    storageStrategy: submitForm.storageStrategy,
  }
}

async function runSync() {
  if (!submitForm.templateId) {
    ElMessage.warning(t('generation.needTemplateId'))
    return
  }
  let body: ReturnType<typeof buildBody>
  try {
    body = buildBody()
  } catch {
    ElMessage.error(t('generation.invalidParametersJson'))
    return
  }
  submittingSync.value = true
  lastSyncResult.value = null
  try {
    const res = await generateDocument(
      submitForm.templateId,
      body,
      submitForm.version ?? undefined,
    )
    lastSyncResult.value = res
    ElMessage.success(t('message.updateSuccess'))
  } finally {
    submittingSync.value = false
  }
}

async function runAsync() {
  if (!submitForm.templateId) {
    ElMessage.warning(t('generation.needTemplateId'))
    return
  }
  let body: ReturnType<typeof buildBody>
  try {
    body = buildBody()
  } catch {
    ElMessage.error(t('generation.invalidParametersJson'))
    return
  }
  submittingAsync.value = true
  lastTaskId.value = null
  try {
    const task = await generateDocumentAsync(submitForm.templateId, body)
    lastTaskId.value = task.taskId
    ElMessage.success(t('generation.asyncSubmitted'))
    activeTab.value = 'queue'
    router.replace({ path: '/generation', query: { tab: 'queue' } })
  } finally {
    submittingAsync.value = false
  }
}

async function runBatch() {
  if (!submitForm.templateId) {
    ElMessage.warning(t('generation.needTemplateId'))
    return
  }
  let dataSets: Array<Record<string, unknown>>
  try {
    const raw = submitForm.batchDataSetsJson.trim()
    const parsed = JSON.parse(raw || '[]')
    if (!Array.isArray(parsed)) throw new Error('not array')
    dataSets = parsed
  } catch {
    ElMessage.error(t('generation.invalidBatchJson'))
    return
  }
  if (dataSets.length === 0) {
    ElMessage.warning(t('generation.batchEmpty'))
    return
  }
  submittingBatch.value = true
  lastTaskId.value = null
  try {
    const task = await generateDocumentBatch(submitForm.templateId, {
      dataSets,
      outputFormat: submitForm.outputFormat,
      storageStrategy: submitForm.storageStrategy,
      failureStrategy: 'CONTINUE',
    })
    lastTaskId.value = task.taskId
    ElMessage.success(t('generation.batchSubmitted'))
    activeTab.value = 'queue'
    router.replace({ path: '/generation', query: { tab: 'queue' } })
  } finally {
    submittingBatch.value = false
  }
}

function goQueueTab() {
  activeTab.value = 'queue'
  router.replace({ path: '/generation', query: { tab: 'queue' } })
}
</script>

<style scoped>
.generation-hub {
  max-width: 1200px;
  margin: 0 auto;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0 0 8px;
}
.subtitle {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.hub-tabs :deep(.el-tabs__content) {
  padding: 16px 0 0;
}
.mb-16 {
  margin-bottom: 16px;
}
.endpoint-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.endpoint-card .ep-method {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.ep-path {
  font-size: 13px;
  word-break: break-all;
}
.ep-desc {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.bullet-list {
  margin: 8px 0 0;
  padding-left: 20px;
  color: var(--el-text-color-regular);
  line-height: 1.7;
}
.submit-form {
  max-width: 720px;
}
.mono :deep(textarea) {
  font-family: ui-monospace, monospace;
  font-size: 13px;
}
.field-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 6px;
}
.result-card {
  margin-top: 20px;
  max-width: 720px;
}
h3 {
  margin: 0 0 8px;
  font-size: 16px;
}
</style>
