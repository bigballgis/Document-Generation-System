<template>
  <el-dialog
    :model-value="visible"
    :title="$t('auth.resetPassword')"
    width="480px"
    @update:model-value="$emit('update:visible', $event)"
    @close="resetForm"
  >
    <p style="margin-bottom: 16px; color: var(--el-text-color-secondary);">{{ $t('auth.resetPasswordEmail') }}</p>
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="80px"
    >
      <el-form-item :label="$t('auth.email')" prop="email">
        <el-input v-model="form.email" type="email" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ $t('common.submit') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { resetPassword } from '@/api/auth'

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
}>()

const { t } = useI18n()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  email: '',
})

const rules: FormRules = {
  email: [
    { required: true, message: () => t('auth.emailRequired'), trigger: 'blur' },
    { type: 'email', message: () => t('auth.emailInvalid'), trigger: 'blur' },
  ],
}

function resetForm() {
  form.email = ''
  formRef.value?.resetFields()
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await resetPassword(form.email)
    ElMessage.success(t('auth.resetPasswordSent'))
    emit('update:visible', false)
  } catch {
    /* error handled by interceptor, dialog stays open */
  } finally {
    submitting.value = false
  }
}
</script>
