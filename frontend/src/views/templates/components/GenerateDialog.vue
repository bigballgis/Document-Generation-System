<template>
  <el-dialog
    :model-value="visible"
    :title="$t('document.generate')"
    width="600px"
    @update:model-value="emit('update:visible', $event)"
    @closed="resetForm"
  >
    <el-form label-width="140px" label-position="top">
      <el-form-item :label="$t('document.generateMode')">
        <el-radio-group v-model="form.mode">
          <el-radio value="sync">{{ $t('document.modeSync') }}</el-radio>
          <el-radio value="async">{{ $t('document.modeAsync') }}</el-radio>
          <el-radio value="batch">{{ $t('document.modeBatch') }}</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="form.mode !== 'batch'" :label="$t('document.parameters')">
        <el-input
          v-model="form.parameters"
          type="textarea"
          :rows="5"
          :placeholder="$t('document.parametersHint')"
        />
      </el-form-item>

      <el-form-item :label="$t('document.outputFormat')">
        <el-select v-model="form.outputFormat" style="width: 100%">
          <el-option label="Word (.docx)" value="WORD" />
          <el-option label="PDF" value="PDF" />
          <el-option :label="$t('document.formatBoth')" value="BOTH" />
        </el-select>
      </el-form-item>

      <el-form-item :label="$t('document.storageMode')">
        <el-select v-model="form.storageStrategy" style="width: 100%">
          <el-option :label="$t('document.storageTemporary')" value="TEMP" />
          <el-option :label="$t('document.storagePersistent')" value="PERSISTENT" />
        </el-select>
      </el-form-item>

      <template v-if="form.mode === 'batch'">
        <el-form-item :label="$t('document.dataSets')">
          <el-input
            v-model="form.dataSets"
            type="textarea"
            :rows="8"
            :placeholder="$t('document.dataSetsHint')"
          />
        </el-form-item>

        <el-form-item :label="$t('document.failureStrategy')">
          <el-select v-model="form.failureStrategy" style="width: 100%">
            <el-option :label="$t('document.strategyContinue')" value="CONTINUE" />
            <el-option :label="$t('document.strategyThreshold')" value="THRESHOLD" />
          </el-select>
        </el-form-item>

        <el-form-item
          v-if="form.failureStrategy === 'THRESHOLD'"
          :label="$t('document.failureThreshold')"
        >
          <el-input-number v-model="form.failureThreshold" :min="1" :max="100" />
        </el-form-item>
      </template>
    </el-form>

    <div v-if="syncResult" style="margin-top: 12px">
      <el-alert :title="$t('document.generateSuccess')" type="success" show-icon :closable="false">
        <a :href="syncResult.downloadUrl" target="_blank">{{ $t('common.download') }}</a>
      </el-alert>
    </div>

    <div v-if="asyncTaskId" style="margin-top: 12px">
      <el-alert :title="`Task ID: ${asyncTaskId}`" type="info" show-icon :closable="false">
        <router-link to="/tasks">{{ $t('task.title') }}</router-link>
      </el-alert>
    </div>

    <template #footer>
      <el-button @click="emit('update:visible', false)">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ $t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { generateDocument, generateDocumentAsync, generateDocumentBatch } from '@/api/generate'
import type { GenerateDocumentResponse } from '@/types/document'

const props = defineProps<{
  visible: boolean
  templateId: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'generated'): void
}>()

const router = useRouter()
const { t } = useI18n()

const submitting = ref(false)
const syncResult = ref<GenerateDocumentResponse | null>(null)
const asyncTaskId = ref<string | null>(null)

const form = reactive({
  mode: 'sync' as 'sync' | 'async' | 'batch',
  parameters: '',
  outputFormat: 'WORD',
  storageStrategy: 'TEMP',
  dataSets: '',
  failureStrategy: 'CONTINUE',
  failureThreshold: 10,
})

function parseJson(text: string): Record<string, unknown> | null {
  if (!text.trim()) return {}
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function parseDataSets(text: string): Array<Record<string, unknown>> | null {
  if (!text.trim()) return null
  try {
    const lines = text.trim().split('\n').filter(l => l.trim())
    return lines.map(line => JSON.parse(line))
  } catch {
    return null
  }
}

async function handleSubmit() {
  submitting.value = true
  syncResult.value = null
  asyncTaskId.value = null

  try {
    if (form.mode === 'sync') {
      const params = parseJson(form.parameters)
      if (params === null) {
        ElMessage.error(t('document.parametersHint'))
        return
      }
      const res = await generateDocument(props.templateId, {
        parameters: params,
        outputFormat: form.outputFormat,
        storageStrategy: form.storageStrategy,
      })
      syncResult.value = res
      emit('generated')
    } else if (form.mode === 'async') {
      const params = parseJson(form.parameters)
      if (params === null) {
        ElMessage.error(t('document.parametersHint'))
        return
      }
      const res = await generateDocumentAsync(props.templateId, {
        parameters: params,
        outputFormat: form.outputFormat,
        storageStrategy: form.storageStrategy,
      })
      asyncTaskId.value = res.taskId
      emit('generated')
    } else {
      const dataSets = parseDataSets(form.dataSets)
      if (!dataSets || dataSets.length === 0) {
        ElMessage.error(t('document.dataSetsHint'))
        return
      }
      await generateDocumentBatch(props.templateId, {
        dataSets,
        outputFormat: form.outputFormat,
        storageStrategy: form.storageStrategy,
        failureStrategy: form.failureStrategy,
        failureThreshold: form.failureStrategy === 'THRESHOLD' ? form.failureThreshold : undefined,
      })
      emit('generated')
      emit('update:visible', false)
      router.push('/tasks')
    }
  } catch (err: any) {
    ElMessage.error(err?.message || t('common.error'))
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.mode = 'sync'
  form.parameters = ''
  form.outputFormat = 'WORD'
  form.storageStrategy = 'TEMP'
  form.dataSets = ''
  form.failureStrategy = 'CONTINUE'
  form.failureThreshold = 10
  syncResult.value = null
  asyncTaskId.value = null
}
</script>

