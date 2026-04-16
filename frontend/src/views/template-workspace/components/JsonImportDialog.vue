<template>
  <el-dialog
    :model-value="visible"
    :title="t('parameter.jsonImport.title')"
    width="560px"
    @update:model-value="(val: boolean) => emit('update:visible', val)"
  >
    <el-input
      v-model="jsonText"
      type="textarea"
      :rows="12"
      :placeholder="t('parameter.jsonImport.placeholder')"
    />
    <div v-if="errorMsg" class="json-error">{{ errorMsg }}</div>
    <div v-if="warningMsg" class="json-warning">{{ warningMsg }}</div>

    <template #footer>
      <el-button @click="emit('update:visible', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="importing" @click="handleImport">
        {{ t('parameter.jsonImport.import') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { jsonImportParameters } from '@/api/parameters'
import { validateJson } from '@/composables/useJsonImport'

const props = defineProps<{
  visible: boolean
  templateId: number
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  imported: []
}>()

const { t } = useI18n()
const jsonText = ref('')
const errorMsg = ref('')
const warningMsg = ref('')
const importing = ref(false)

watch(() => props.visible, (val) => {
  if (val) {
    jsonText.value = ''
    errorMsg.value = ''
    warningMsg.value = ''
  }
})

async function handleImport() {
  errorMsg.value = ''
  warningMsg.value = ''

  const raw = jsonText.value.trim()
  if (!raw) return

  // Client-side validation
  const validation = validateJson(raw)
  if (!validation.valid) {
    errorMsg.value = `${t('parameter.jsonImport.parseError')}: ${validation.error}`
    return
  }

  // Check empty JSON
  try {
    const parsed = JSON.parse(raw)
    if (
      (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed) && Object.keys(parsed).length === 0) ||
      (Array.isArray(parsed) && parsed.length === 0)
    ) {
      warningMsg.value = t('parameter.jsonImport.emptyWarning')
      return
    }
  } catch { /* already validated above */ }

  importing.value = true
  try {
    await jsonImportParameters(props.templateId, raw)
    ElMessage.success(t('message.createSuccess'))
    emit('imported')
    emit('update:visible', false)
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || e.message || t('message.operationFailed')
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
.json-error {
  color: var(--el-color-danger);
  font-size: 12px;
  margin-top: 8px;
}
.json-warning {
  color: var(--el-color-warning);
  font-size: 12px;
  margin-top: 8px;
}
</style>
