<template>
  <div class="quick-add-bar">
    <el-input
      ref="inputRef"
      v-model="inputValue"
      :placeholder="t('parameter.quickAdd.placeholder')"
      size="default"
      clearable
      @keyup.enter="handleAdd"
    />
    <div v-if="errorMsg" class="quick-add-error">{{ errorMsg }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { createParameter } from '@/api/parameters'
import { ElMessage } from 'element-plus'
import type { ParameterDTO, CreateParameterRequest } from '@/types/parameter'

const NAME_REGEX = /^[a-zA-Z_][a-zA-Z0-9_-]*$/

const props = defineProps<{
  templateId: number
  existingNames: string[]
}>()

const emit = defineEmits<{
  created: [param: ParameterDTO]
}>()

const { t } = useI18n()
const inputRef = ref<any>(null)
const inputValue = ref('')
const errorMsg = ref('')

async function handleAdd() {
  errorMsg.value = ''
  const raw = inputValue.value.trim()
  if (!raw) return

  // Dot syntax: split into segments
  const segments = raw.split('.')

  // Validate each segment
  for (const seg of segments) {
    if (!NAME_REGEX.test(seg)) {
      errorMsg.value = t('parameter.quickAdd.invalidNameError')
      return
    }
  }

  // Check root name duplication
  const rootName = segments[0]
  if (props.existingNames.includes(rootName) && segments.length === 1) {
    errorMsg.value = t('parameter.quickAdd.duplicateError')
    return
  }

  try {
    if (segments.length === 1) {
      // Simple parameter
      const param = await createParameter(props.templateId, { name: rootName })
      emit('created', param)
    } else {
      // Dot syntax: create nested structure
      let parentId: number | undefined
      for (let i = 0; i < segments.length; i++) {
        const isLast = i === segments.length - 1
        const req: CreateParameterRequest = {
          name: segments[i],
          dataType: isLast ? 'STRING' : 'OBJECT',
          parentId: parentId ?? undefined,
        }
        const created = await createParameter(props.templateId, req)
        parentId = created.id
        if (isLast) {
          emit('created', created)
        }
      }
    }
    inputValue.value = ''
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  }

  // Keep focus
  await nextTick()
  inputRef.value?.focus()
}
</script>

<style scoped>
.quick-add-bar {
  padding: 8px 0;
}
.quick-add-error {
  color: var(--el-color-danger);
  font-size: 12px;
  margin-top: 4px;
  padding-left: 4px;
}
</style>
