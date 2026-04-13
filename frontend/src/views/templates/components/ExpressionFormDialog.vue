<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? $t('expression.edit') : $t('expression.create')"
    width="640px"
    @update:model-value="$emit('update:visible', $event)"
    @close="resetForm"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="140px"
    >
      <el-form-item :label="$t('expression.name')" prop="name">
        <el-input v-model="form.name" maxlength="100" />
      </el-form-item>
      <el-form-item :label="$t('expression.type')" prop="expressionType">
        <el-select v-model="form.expressionType" style="width: 100%">
          <el-option :label="$t('expression.typeJavaScript')" value="JAVASCRIPT" />
          <el-option :label="$t('expression.typeExcel')" value="EXCEL" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('expression.content')" prop="expressionText">
        <el-input v-model="form.expressionText" type="textarea" :rows="6" />
      </el-form-item>
      <el-form-item :label="$t('expression.description')">
        <el-input v-model="form.description" />
      </el-form-item>
      <el-form-item :label="$t('expression.executionOrder')">
        <el-input-number v-model="form.executionOrder" :min="0" />
      </el-form-item>

      <!-- Validation Result -->
      <el-form-item v-if="validationResult">
        <el-alert
          v-if="validationResult.valid"
          :title="$t('expression.validationSuccess')"
          type="success"
          show-icon
          :closable="false"
        />
        <el-alert
          v-else
          :title="$t('expression.validationFailed')"
          :description="validationResultDescription"
          type="error"
          show-icon
          :closable="false"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">{{ $t('common.cancel') }}</el-button>
      <el-button :loading="validating" @click="handleValidate">{{ $t('expression.validate') }}</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { ExpressionDTO, ExpressionValidationResult } from '@/types/document'
import { createExpression, updateExpression, validateExpression } from '@/api/expressions'

const props = defineProps<{
  visible: boolean
  templateId: number
  data: ExpressionDTO | null
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  saved: []
}>()

const { t } = useI18n()
const formRef = ref<FormInstance>()
const saving = ref(false)
const validating = ref(false)
const validationResult = ref<ExpressionValidationResult | null>(null)

const isEdit = computed(() => !!props.data)

const form = reactive({
  name: '',
  expressionType: 'JAVASCRIPT',
  expressionText: '',
  description: '',
  executionOrder: 0,
})

const rules: FormRules = {
  name: [{ required: true, message: () => t('validation.required', { field: t('expression.name') }), trigger: 'blur' }],
  expressionType: [{ required: true, message: () => t('validation.required', { field: t('expression.type') }), trigger: 'change' }],
  expressionText: [{ required: true, message: () => t('validation.required', { field: t('expression.content') }), trigger: 'blur' }],
}

const validationResultDescription = computed(() => {
  if (!validationResult.value || validationResult.value.valid) return ''
  let msg = validationResult.value.errorMessage || ''
  if (validationResult.value.errorPosition != null) {
    msg += ` ${t('expression.errorPosition', { pos: validationResult.value.errorPosition })}`
  }
  return msg
})

watch(() => props.visible, (val) => {
  if (val && props.data) {
    form.name = props.data.name
    form.expressionType = props.data.expressionType
    form.expressionText = props.data.expressionText
    form.description = props.data.description || ''
    form.executionOrder = props.data.executionOrder
  } else if (val) {
    resetForm()
  }
})

function resetForm() {
  form.name = ''
  form.expressionType = 'JAVASCRIPT'
  form.expressionText = ''
  form.description = ''
  form.executionOrder = 0
  validationResult.value = null
  formRef.value?.resetFields()
}

async function handleValidate() {
  if (!form.expressionText.trim()) return
  validating.value = true
  try {
    validationResult.value = await validateExpression({
      expression: form.expressionText,
      expressionType: form.expressionType,
    })
  } catch { /* handled */ } finally {
    validating.value = false
  }
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = {
      name: form.name,
      expressionType: form.expressionType,
      expressionText: form.expressionText,
      description: form.description || undefined,
      executionOrder: form.executionOrder,
    }
    if (isEdit.value && props.data) {
      await updateExpression(props.data.id, payload)
      ElMessage.success(t('message.updateSuccess'))
    } else {
      await createExpression(props.templateId, payload)
      ElMessage.success(t('message.createSuccess'))
    }
    emit('saved')
    emit('update:visible', false)
  } catch { /* handled */ } finally {
    saving.value = false
  }
}
</script>
