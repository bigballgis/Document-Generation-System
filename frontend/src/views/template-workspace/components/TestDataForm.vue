<template>
  <div class="test-data-form">
    <!-- Mode toggle -->
    <div class="form-header">
      <el-button size="small" @click="toggleMode">
        {{ mode === 'form' ? t('workspace.testForm.viewJson') : t('workspace.testForm.viewForm') }}
      </el-button>
    </div>

    <!-- Form mode -->
    <div v-if="mode === 'form'" class="form-body">
      <el-form label-position="top" size="default">
        <FormFieldRenderer
          v-for="field in fields"
          :key="field.parameterId"
          :field="field"
          :model-value="getFieldValue(field)"
          :readonly="readonly"
          :highlighted-paths="highlightedPaths"
          @update:model-value="setFieldValue(field, $event)"
        />
      </el-form>
    </div>

    <!-- JSON mode -->
    <div v-else class="json-body">
      <el-input
        v-model="jsonText"
        type="textarea"
        :rows="20"
        :readonly="readonly"
        @blur="handleJsonBlur"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { buildFormFields, buildInitialValues } from '@/composables/useTestDataForm'
import type { ParameterDTO } from '@/types/parameter'
import type { FormField } from '@/types/testDataForm'
import FormFieldRenderer from './FormFieldRenderer.vue'

const props = defineProps<{
  parameters: ParameterDTO[]
  readonly: boolean
  highlightedPaths?: string[]
}>()

const emit = defineEmits<{
  'update:formData': [data: Record<string, unknown>]
  submit: []
}>()

const { t } = useI18n()

const mode = ref<'form' | 'json'>('form')
const formValues = ref<Record<string, unknown>>({})
const jsonText = ref('')

const fields = computed<FormField[]>(() => buildFormFields(props.parameters))

// Initialize values when parameters change
watch(() => props.parameters, (params) => {
  if (params.length > 0) {
    formValues.value = buildInitialValues(fields.value)
    jsonText.value = JSON.stringify(formValues.value, null, 2)
    emit('update:formData', formValues.value)
  }
}, { immediate: true })

function getFieldValue(field: FormField): unknown {
  return formValues.value[field.name]
}

function setFieldValue(field: FormField, value: unknown) {
  formValues.value[field.name] = value
  jsonText.value = JSON.stringify(formValues.value, null, 2)
  emit('update:formData', { ...formValues.value })
}

function toggleMode() {
  if (mode.value === 'form') {
    jsonText.value = JSON.stringify(formValues.value, null, 2)
    mode.value = 'json'
  } else {
    mode.value = 'form'
  }
}

function handleJsonBlur() {
  try {
    formValues.value = JSON.parse(jsonText.value)
    emit('update:formData', { ...formValues.value })
  } catch {
    // Invalid JSON — keep current values
  }
}
</script>

<style scoped>
.test-data-form {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.form-header {
  display: flex;
  justify-content: flex-end;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 8px;
}
.form-body {
  flex: 1;
  overflow-y: auto;
  padding-right: 8px;
}
.json-body {
  flex: 1;
  overflow: hidden;
}
.json-body :deep(.el-textarea__inner) {
  font-family: monospace;
  font-size: 13px;
  height: 100%;
}
</style>
