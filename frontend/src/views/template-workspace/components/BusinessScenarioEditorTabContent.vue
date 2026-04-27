<template>
  <div class="scenario-editor-tab">
    <el-form label-position="top" size="default" @submit.prevent>
      <el-form-item :label="t('workspace.testing.name')" required>
        <el-input v-model="form.name" maxlength="200" show-word-limit :disabled="readonly" />
      </el-form-item>
      <el-form-item :label="t('workspace.testing.compareMode')">
        <el-select v-model="form.comparisonType" style="width: 100%" :disabled="readonly">
          <el-option :label="t('test.modeVariable')" value="VARIABLE_VALUE" />
          <el-option :label="t('test.modeText')" value="TEXT_CONTENT" />
          <el-option :label="t('test.modeSnapshot')" value="FILE_SNAPSHOT" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('workspace.testing.testDataJson')">
        <el-input
          v-model="form.testDataJson"
          type="textarea"
          :rows="10"
          :readonly="readonly"
        />
      </el-form-item>
      <el-form-item :label="t('workspace.testing.expectedResult')">
        <el-input
          v-model="form.expectedResultJson"
          type="textarea"
          :rows="5"
          :readonly="readonly"
        />
      </el-form-item>
      <div v-if="testCase?.lastRun" class="last-run">
        <span class="last-run-label">{{ t('workspace.validation.lastTrial') }}:</span>
        <el-tag :type="isTestPassed(testCase.lastRun) ? 'success' : 'danger'" size="small">
          {{ isTestPassed(testCase.lastRun) ? t('workspace.validation.statusReady') : t('workspace.validation.statusNeedsFix') }}
        </el-tag>
        <span class="last-run-time">{{ formatTime(testCase.lastRun.executedAt) }}</span>
      </div>
      <div class="form-actions">
        <el-button v-if="!readonly && !isNew" type="danger" plain @click="handleDelete">
          {{ t('common.delete') }}
        </el-button>
        <el-button v-if="!readonly" type="primary" :loading="saving" @click="handleSave">
          {{ t('common.save') }}
        </el-button>
        <el-button
          v-if="!isNew && testCase"
          type="primary"
          plain
          :loading="running"
          :disabled="readonly"
          @click="handleRun"
        >
          {{ t('workspace.validation.runTrial') }}
        </el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createTestCase,
  updateTestCase,
  deleteTestCase,
  runTestCase,
  isTestPassed,
  type TestCaseDTO,
  type ComparisonType,
} from '@/api/market'

const props = defineProps<{
  templateId: number
  tabKey: string
  testCase: TestCaseDTO | null
  isNew: boolean
  readonly: boolean
}>()

const emit = defineEmits<{
  saved: [payload: { id: number; previousKey: string }]
  deleted: [id: number]
  'run-complete': []
}>()

const { t } = useI18n()

const form = reactive({
  name: '',
  testDataJson: '{}',
  expectedResultJson: '',
  comparisonType: 'VARIABLE_VALUE' as ComparisonType,
})

const saving = ref(false)
const running = ref(false)

function resetFromTestCase(tc: TestCaseDTO | null) {
  if (!tc) {
    form.name = ''
    form.testDataJson = '{}'
    form.expectedResultJson = ''
    form.comparisonType = 'VARIABLE_VALUE'
    return
  }
  form.name = tc.name
  form.testDataJson = tc.testDataJson ?? '{}'
  form.expectedResultJson = tc.expectedResultJson ?? ''
  form.comparisonType = tc.comparisonType
}

watch(
  () => [props.testCase, props.isNew] as const,
  () => {
    if (props.isNew) {
      form.name = ''
      form.testDataJson = '{}'
      form.expectedResultJson = ''
      form.comparisonType = 'VARIABLE_VALUE'
    } else {
      resetFromTestCase(props.testCase)
    }
  },
  { immediate: true },
)

function formatTime(v: string | string[] | undefined): string {
  if (v == null) return ''
  const s = Array.isArray(v) ? v.join(',') : String(v)
  return s.replace('T', ' ').replace('Z', ' UTC').slice(0, 32)
}

function validateJson(raw: string, label: string): boolean {
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

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning(t('workspace.testing.nameRequired'))
    return
  }
  if (!validateJson(form.testDataJson, t('workspace.testing.testDataJson'))) return
  if (!validateJson(form.expectedResultJson, t('workspace.testing.expectedResult'))) return

  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      testDataJson: form.testDataJson.trim() || '{}',
      expectedResultJson: form.expectedResultJson.trim() ? form.expectedResultJson.trim() : '{}',
      comparisonType: form.comparisonType,
    }
    if (props.isNew) {
      const created = await createTestCase(props.templateId, payload)
      ElMessage.success(t('message.saveSuccess'))
      emit('saved', { id: created.id, previousKey: props.tabKey })
    } else if (props.testCase) {
      await updateTestCase(props.testCase.id, payload)
      ElMessage.success(t('message.saveSuccess'))
      emit('saved', { id: props.testCase.id, previousKey: props.tabKey })
    }
  } catch {
    /* interceptor */
  } finally {
    saving.value = false
  }
}

async function handleDelete() {
  if (!props.testCase) return
  try {
    await ElMessageBox.confirm(t('workspace.testing.deleteConfirm'), t('confirm.deleteTitle'))
    await deleteTestCase(props.testCase.id)
    ElMessage.success(t('message.deleteSuccess'))
    emit('deleted', props.testCase.id)
  } catch {
    /* cancelled */
  }
}

async function handleRun() {
  if (!props.testCase) return
  running.value = true
  try {
    const result = await runTestCase(props.testCase.id)
    ElMessage[isTestPassed(result) ? 'success' : 'warning'](
      isTestPassed(result) ? t('test.passed') : t('test.failed'),
    )
    emit('run-complete')
  } catch {
    /* interceptor */
  } finally {
    running.value = false
  }
}
</script>

<style scoped>
.scenario-editor-tab {
  padding: 8px 0;
  max-width: 720px;
}
.last-run {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
  font-size: 13px;
}
.last-run-label {
  color: var(--el-text-color-secondary);
}
.last-run-time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.form-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
}
</style>
