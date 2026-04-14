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
      <el-form-item :label="t('workspace.testing.compareMode')" prop="compareMode">
        <el-select v-model="form.compareMode" style="width: 100%">
          <el-option label="VARIABLE" value="VARIABLE" />
          <el-option label="TEXT" value="TEXT" />
          <el-option label="SNAPSHOT" value="SNAPSHOT" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('workspace.testing.testDataJson')" prop="testData">
        <el-input
          v-model="form.testData"
          type="textarea"
          :rows="6"
          :placeholder="t('workspace.testing.testDataPlaceholder')"
        />
      </el-form-item>
      <el-form-item :label="t('workspace.testing.expectedResult')" prop="expectedResult">
        <el-input
          v-model="form.expectedResult"
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
import type { TestCaseDTO, CreateTestCaseRequest } from '@/api/market'

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
  testData: '{}',
  expectedResult: '',
  compareMode: 'VARIABLE' as 'VARIABLE' | 'TEXT' | 'SNAPSHOT',
})

function validateJson(_rule: any, value: string, callback: (error?: Error) => void) {
  if (!value) { callback(); return }
  try {
    JSON.parse(value)
    callback()
  } catch {
    callback(new Error(t('workspace.testing.invalidJson')))
  }
}

const rules: FormRules = {
  name: [
    { required: true, message: () => t('workspace.testing.nameRequired'), trigger: 'blur' },
    { max: 100, message: () => t('workspace.testing.nameMaxLength'), trigger: 'blur' },
  ],
  testData: [
    { required: true, message: () => t('workspace.testing.testDataRequired'), trigger: 'blur' },
    { validator: validateJson, trigger: 'blur' },
  ],
}

watch(() => props.visible, (val) => {
  if (val && props.testCase) {
    form.name = props.testCase.name
    form.testData = props.testCase.testData
    form.expectedResult = props.testCase.expectedResult ?? ''
    form.compareMode = props.testCase.compareMode
  } else if (val) {
    form.name = ''
    form.testData = '{}'
    form.expectedResult = ''
    form.compareMode = 'VARIABLE'
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
      testData: form.testData,
      expectedResult: form.expectedResult || '',
      compareMode: form.compareMode,
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
