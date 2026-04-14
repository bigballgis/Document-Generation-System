<template>
  <el-dialog
    :model-value="visible"
    :title="$t('workspace.wizard.title')"
    width="560px"
    @update:model-value="$emit('update:visible', $event)"
    :close-on-click-modal="false"
  >
    <el-steps :active="currentStep" simple style="margin-bottom: 24px">
      <el-step :title="$t('workspace.wizard.step1Title')" />
      <el-step :title="$t('workspace.wizard.step2Title')" />
    </el-steps>

    <el-form
      v-if="currentStep === 0"
      ref="step1FormRef"
      :model="form"
      :rules="step1Rules"
      label-width="100px"
    >
      <el-form-item :label="$t('workspace.wizard.name')" prop="name">
        <el-input v-model="form.name" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item :label="$t('workspace.wizard.description')" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
      </el-form-item>
      <el-form-item :label="$t('workspace.wizard.category')">
        <el-tree-select
          v-model="form.categoryId"
          :data="categories"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          clearable
          check-strictly
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item :label="$t('workspace.wizard.tags')">
        <el-select v-model="form.tagIds" multiple style="width: 100%">
          <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-form v-if="currentStep === 1" :model="form" label-width="100px">
      <el-form-item :label="$t('workspace.wizard.outputFormat')">
        <el-radio-group v-model="form.outputFormat">
          <el-radio value="WORD">WORD</el-radio>
          <el-radio value="PDF">PDF</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <el-alert v-if="errorMsg" :title="errorMsg" type="error" show-icon :closable="false" style="margin-top: 12px" />

    <template #footer>
      <el-button @click="$emit('update:visible', false)">{{ $t('workspace.wizard.cancel') }}</el-button>
      <el-button v-if="currentStep > 0" @click="currentStep--">{{ $t('workspace.wizard.prev') }}</el-button>
      <el-button v-if="currentStep === 0" type="primary" @click="goNext">{{ $t('workspace.wizard.next') }}</el-button>
      <el-button v-if="currentStep === 1" type="primary" :loading="submitting" @click="handleSubmit">{{ $t('workspace.wizard.create') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import { createCompositeTemplate } from '@/api/composite-templates'
import { addTagToTemplate, type CategoryDTO, type TagDTO, type TemplateDTO } from '@/api/templates'

const { t } = useI18n()

const props = defineProps<{
  visible: boolean
  categories: CategoryDTO[]
  tags: TagDTO[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'created', template: TemplateDTO): void
}>()

const currentStep = ref(0)
const submitting = ref(false)
const errorMsg = ref('')
const step1FormRef = ref<FormInstance>()

const form = reactive({
  name: '',
  description: '',
  categoryId: null as number | null,
  tagIds: [] as number[],
  outputFormat: 'WORD',
})

const step1Rules: FormRules = {
  name: [
    { required: true, message: () => t('workspace.wizard.nameRequired'), trigger: 'blur' },
    { max: 200, message: () => t('workspace.wizard.nameMax'), trigger: 'blur' },
  ],
  description: [
    { max: 500, message: () => t('workspace.wizard.descMax'), trigger: 'blur' },
  ],
}

watch(() => props.visible, (val) => {
  if (val) {
    currentStep.value = 0
    form.name = ''
    form.description = ''
    form.categoryId = null
    form.tagIds = []
    form.outputFormat = 'WORD'
    errorMsg.value = ''
  }
})

async function goNext() {
  if (!step1FormRef.value) return
  try {
    await step1FormRef.value.validate()
    currentStep.value = 1
  } catch { /* validation failed */ }
}

async function handleSubmit() {
  submitting.value = true
  errorMsg.value = ''
  try {
    const created = await createCompositeTemplate({
      name: form.name,
      description: form.description || undefined,
      outputFormat: form.outputFormat,
      categoryId: form.categoryId,
    })
    // Associate tags after creation
    for (const tagId of form.tagIds) {
      try {
        await addTagToTemplate(tagId, created.id)
      } catch { /* best effort */ }
    }
    emit('created', created)
    emit('update:visible', false)
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || e.message || 'Creation failed'
  } finally {
    submitting.value = false
  }
}
</script>
