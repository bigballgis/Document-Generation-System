<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? $t('segment.edit') : $t('segment.create')"
    width="600px"
    @update:model-value="$emit('update:visible', $event)"
    @close="resetForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-form-item :label="$t('segment.name')" prop="name">
        <el-input v-model="form.name" maxlength="200" />
      </el-form-item>
      <el-form-item :label="$t('segment.description')">
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" />
      </el-form-item>
      <el-form-item :label="$t('segment.type')">
        <el-select v-model="form.segmentType" :placeholder="$t('common.all')" clearable style="width: 100%">
          <el-option label="Cover" value="COVER" />
          <el-option label="TOC" value="TOC" />
          <el-option label="Chapter" value="CHAPTER" />
          <el-option label="Table" value="TABLE" />
          <el-option label="Signature" value="SIGNATURE" />
          <el-option label="Legal" value="LEGAL" />
          <el-option label="Appendix" value="APPENDIX" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('segment.category')">
        <el-tree-select
          v-model="form.categoryId"
          :data="categories"
          :props="{ label: 'name', value: 'id', children: 'children' }"
          :placeholder="$t('category.rootCategory')"
          clearable
          check-strictly
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item :label="$t('segment.tags')">
        <el-select v-model="form.tagIds" multiple :placeholder="$t('tag.addTag')" style="width: 100%">
          <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="!isEdit" :label="$t('segment.file')" prop="file">
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          accept=".docx"
          :on-change="onFileChange"
          :on-remove="onFileRemove"
        >
          <el-button type="primary">{{ $t('segment.uploadDocx') }}</el-button>
        </el-upload>
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
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import { createSegment, updateSegment } from '@/api/segments'
import type { Segment } from '@/types/segment'
import type { CategoryDTO, TagDTO } from '@/api/templates'

const props = defineProps<{
  visible: boolean
  segmentData: Segment | null
  categories: CategoryDTO[]
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
const selectedFile = ref<File | null>(null)

const form = reactive({
  name: '',
  description: '',
  segmentType: '',
  categoryId: null as number | null,
  tagIds: [] as number[],
})

const rules: FormRules = {
  name: [{ required: true, message: () => t('validation.required', { field: t('segment.name') }), trigger: 'blur' }],
}

watch(() => props.visible, (val) => {
  if (val && props.segmentData) {
    isEdit.value = true
    form.name = props.segmentData.name
    form.description = props.segmentData.description || ''
    form.segmentType = props.segmentData.segmentType || ''
    form.categoryId = props.segmentData.categoryId
    form.tagIds = []
  } else if (val) {
    isEdit.value = false
  }
})

function resetForm() {
  form.name = ''
  form.description = ''
  form.segmentType = ''
  form.categoryId = null
  form.tagIds = []
  selectedFile.value = null
  formRef.value?.resetFields()
}

function onFileChange(file: UploadFile) {
  selectedFile.value = file.raw || null
}

function onFileRemove() {
  selectedFile.value = null
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (!isEdit.value && !selectedFile.value) {
    ElMessage.warning(t('validation.required', { field: t('segment.file') }))
    return
  }

  saving.value = true
  try {
    const payload = {
      name: form.name,
      description: form.description,
      segmentType: form.segmentType || undefined,
      categoryId: form.categoryId,
      tagIds: form.tagIds,
    }
    if (isEdit.value && props.segmentData) {
      await updateSegment(props.segmentData.id, payload)
      ElMessage.success(t('message.updateSuccess'))
    } else {
      await createSegment(payload, selectedFile.value!)
      ElMessage.success(t('message.createSuccess'))
    }
    emit('saved')
  } catch { /* handled by interceptor */ } finally {
    saving.value = false
  }
}
</script>
