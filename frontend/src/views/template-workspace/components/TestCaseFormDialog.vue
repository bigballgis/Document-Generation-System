<template>
  <el-dialog
    :model-value="visible"
    :title="testCase ? t('common.edit') : t('workspace.testing.addTestCase')"
    width="640px"
    @update:model-value="emit('update:visible', $event)"
    @closed="resetForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="140px">
      <el-form-item :label="t('workspace.testing.name')" prop="name">
        <el-input v-model="form.name" maxlength="100" show-word-limit />
      </el-form-item>
      <el-form-item :label="t('workspace.testing.compareMode')" prop="comparisonType">
        <el-select v-model="form.comparisonType" style="width: 100%">
          <el-option :label="t('test.modeVariable')" value="VARIABLE_VALUE" />
          <el-option :label="t('test.modeText')" value="TEXT_CONTENT" />
          <el-option :label="t('test.modeSnapshot')" value="FILE_SNAPSHOT" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('workspace.testing.testDataJson')" prop="testDataJson">
        <el-input
          v-model="form.testDataJson"
          type="textarea"
          :rows="6"
          :placeholder="t('workspace.testing.testDataPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('workspace.testing.expectedResult')" prop="expectedResultJson">
        <el-input
          v-model="form.expectedResultJson"
          type="textarea"
          :rows="4"
          :placeholder="t('workspace.testing.expectedResultPlaceholder')"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { createTestCase, updateTestCase } from '@/api/market'
import type { TestCaseDTO, CreateTestCaseRequest, ComparisonType } from '@/api/market'

const props = defineProps<{
  visible: boolean
  templateId: number
  testCase: TestCaseDTO | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  saved: []
}>()

const { t } = useI18n()
const formRef = ref<FormInstance | null>(null)
const saving = ref(false)

const form = reactive({
  name: '',
  testDataJson: '{}',
  expectedResultJson: '',
  comparisonType: 'VARIABLE_VALUE' as ComparisonType,
})

function validateJson(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!value) { callback(); return }
  try {
    JSON.parse(value)
    callback()
  } catch {
    callback(new Error(t('workspace.testing.invalidJson')))
  }
}

function validateOptionalJson(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!value || !String(value).trim()) { callback(); return }
  validateJson(_rule, value, callback)
}

const rules: FormRules = {
  name: [
    { required: true, message: () => t('workspace.testing.nameRequired'), trigger: 'blur' },
    { max: 100, message: () => t('workspace.testing.nameMaxLength'), trigger: 'blur' },
  ],
  testDataJson: [
    { required: true, message: () => t('workspace.testing.testDataRequired'), trigger: 'blur' },
    { validator: validateJson, trigger: 'blur' },
  ],
  expectedResultJson: [{ validator: validateOptionalJson, trigger: 'blur' }],
}

watch(() => props.visible, (val) => {
  if (val && props.testCase) {
    form.name = props.testCase.name
    form.testDataJson = props.testCase.testDataJson ?? '{}'
    form.expectedResultJson = props.testCase.expectedResultJson ?? ''
    form.comparisonType = props.testCase.comparisonType
  } else if (val) {
    form.name = ''
    form.testDataJson = '{}'
    form.expectedResultJson = ''
    form.comparisonType = 'VARIABLE_VALUE'
  }
})

function resetForm() {
  formRef.value?.resetFields()
}

async function handleSubmit() {
  try {
    await formRef.value?.validate()
  } catch { return }

  saving.value = true
  try {
    const data: CreateTestCaseRequest = {
      name: form.name,
      testDataJson: form.testDataJson,
      expectedResultJson: form.expectedResultJson.trim() ? form.expectedResultJson.trim() : '{}',
      comparisonType: form.comparisonType,
    }
    if (props.testCase) {
      await updateTestCase(props.testCase.id, data)
    } else {
      await createTestCase(props.templateId, data)
    }
    emit('saved')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.saveFailed'))
  } finally {
    saving.value = false
  }
}
</script>
