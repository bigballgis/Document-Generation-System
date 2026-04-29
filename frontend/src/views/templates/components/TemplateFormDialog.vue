<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? $t('template.edit') : $t('template.create')"
    width="600px"
    @update:model-value="$emit('update:visible', $event)"
    @close="resetForm"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="120px"
    >
      <el-form-item :label="$t('template.name')" prop="name">
        <el-input v-model="form.name" maxlength="100" />
      </el-form-item>
      <el-form-item :label="$t('template.description')" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" />
      </el-form-item>
      <el-form-item :label="$t('template.tags')">
        <el-select v-model="form.tagIds" multiple :placeholder="$t('tag.addTag')" style="width: 100%">
          <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('template.reviewRequired')">
        <el-switch v-model="form.reviewRequired" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createTemplate, updateTemplate,
  type TemplateDTO, type TagDTO,
} from '@/api/templates'

const props = defineProps<{
  visible: boolean
  templateData: TemplateDTO | null
  tags: TagDTO[]
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  saved: []
}>()

const { t } = useI18n()
const formRef = ref<FormInstance>()
const saving = ref(false)
const isEdit = ref(false)

const form = reactive({
  name: '',
  description: '',
  tagIds: [] as number[],
  reviewRequired: false,
})

const rules: FormRules = {
  name: [{ required: true, message: () => t('validation.required', { field: t('template.name') }), trigger: 'blur' }],
}

watch(() => props.visible, (val) => {
  if (val && props.templateData) {
    isEdit.value = true
    form.name = props.templateData.name
    form.description = props.templateData.description || ''
    form.tagIds = props.templateData.tags?.map(t => t.id) || []
    form.reviewRequired = props.templateData.reviewRequired ?? false
  } else if (val) {
    isEdit.value = false
  }
})

function resetForm() {
  form.name = ''
  form.description = ''
  form.tagIds = []
  form.reviewRequired = false
  formRef.value?.resetFields()
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = {
      name: form.name,
      description: form.description,
      tagIds: form.tagIds,
      outputFormat: 'WORD',
      reviewRequired: form.reviewRequired,
    }
    if (isEdit.value && props.templateData) {
      await updateTemplate(props.templateData.id, payload)
      ElMessage.success(t('message.updateSuccess'))
    } else {
      await createTemplate(payload)
      ElMessage.success(t('message.createSuccess'))
    }
    emit('saved')
  } catch {} finally {
    saving.value = false
  }
}
</script>
