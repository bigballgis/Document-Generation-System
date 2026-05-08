<template>
  <el-dialog
    :model-value="visible"
    :title="$t('document.mergeDocuments')"
    width="500px"
    @update:model-value="emit('update:visible', $event)"
    @close="emit('update:visible', false)"
  >
    <el-form :model="form" label-width="160px">
      <el-form-item :label="$t('document.insertPageBreak')">
        <el-switch v-model="form.insertPageBreaks" />
      </el-form-item>
      <el-form-item :label="$t('document.generateToc')">
        <el-switch v-model="form.generateToc" />
      </el-form-item>
      <el-form-item :label="$t('document.outputFormat')">
        <el-select v-model="form.outputFormat" style="width: 200px">
          <el-option :label="$t('document.formatWord')" value="DOCX" />
          <el-option :label="$t('document.formatPdf')" value="PDF" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ $t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { mergeDocuments } from '@/api/documents'

const props = defineProps<{
  visible: boolean
  documentIds: number[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'merged'): void
}>()

const { t } = useI18n()
const submitting = ref(false)

const form = reactive({
  insertPageBreaks: true,
  generateToc: false,
  outputFormat: 'DOCX',
})

watch(() => props.visible, (val) => {
  if (val) {
    form.insertPageBreaks = true
    form.generateToc = false
    form.outputFormat = 'DOCX'
  }
})

async function handleSubmit() {
  submitting.value = true
  try {
    await mergeDocuments({
      documentIds: props.documentIds,
      insertPageBreaks: form.insertPageBreaks,
      generateToc: form.generateToc,
      outputFormat: form.outputFormat,
    })
    ElMessage.success(t('document.mergeSuccess'))
    emit('merged')
    emit('update:visible', false)
  } catch {} finally {
    submitting.value = false
  }
}
</script>
