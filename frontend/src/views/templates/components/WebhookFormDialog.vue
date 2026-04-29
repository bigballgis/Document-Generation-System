<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? $t('webhook.edit') : $t('webhook.create')"
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
      <el-form-item :label="$t('webhook.url')" prop="url">
        <el-input v-model="form.url" placeholder="https://" />
      </el-form-item>
      <el-form-item :label="$t('webhook.secret')" prop="secret">
        <el-input v-model="form.secret" type="password" show-password />
      </el-form-item>
      <el-form-item :label="$t('webhook.payloadTemplate')">
        <el-input v-model="form.payloadTemplate" type="textarea" :rows="4" />
      </el-form-item>
      <el-form-item v-if="isEdit" :label="$t('common.status')">
        <el-switch v-model="form.enabled" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { WebhookConfigDTO } from '@/api/webhooks'
import { createWebhook, updateWebhook } from '@/api/webhooks'

const props = defineProps<{
  visible: boolean
  data: WebhookConfigDTO | null
  templateId: number
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  saved: []
}>()

const { t } = useI18n()
const formRef = ref<FormInstance>()
const saving = ref(false)

const isEdit = computed(() => !!props.data)

const form = reactive({
  url: '',
  secret: '',
  payloadTemplate: '',
  enabled: true,
})

const rules: FormRules = {
  url: [{ required: true, message: () => t('validation.required', { field: t('webhook.url') }), trigger: 'blur' }],
  secret: [{ required: true, message: () => t('validation.required', { field: t('webhook.secret') }), trigger: 'blur' }],
}

watch(() => props.visible, (val) => {
  if (val && props.data) {
    form.url = props.data.url
    form.secret = ''
    form.payloadTemplate = props.data.payloadTemplate || ''
    form.enabled = props.data.enabled
  } else if (val) {
    resetForm()
  }
})

function resetForm() {
  form.url = ''
  form.secret = ''
  form.payloadTemplate = ''
  form.enabled = true
  formRef.value?.resetFields()
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (isEdit.value && props.data) {
      await updateWebhook(props.data.id, {
        url: form.url,
        secret: form.secret || undefined,
        payloadTemplate: form.payloadTemplate || undefined,
        enabled: form.enabled,
      })
      ElMessage.success(t('message.updateSuccess'))
    } else {
      await createWebhook(props.templateId, {
        url: form.url,
        secret: form.secret,
        payloadTemplate: form.payloadTemplate || undefined,
      })
      ElMessage.success(t('message.createSuccess'))
    }
    emit('saved')
    emit('update:visible', false)
  } catch {} finally {
    saving.value = false
  }
}
</script>
