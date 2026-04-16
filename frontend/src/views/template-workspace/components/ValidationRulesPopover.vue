<template>
  <el-popover :visible="visible" placement="right" :width="320" trigger="click">
    <template #reference>
      <slot />
    </template>
    <div class="validation-rules-popover">
      <h4>{{ t('parameter.validation.title') }}</h4>

      <!-- Universal rules -->
      <div class="rule-row">
        <span>{{ t('parameter.validation.notNull') }}</span>
        <el-switch v-model="localRules.not_null" size="small" />
      </div>
      <div v-if="dataType === 'STRING'" class="rule-row">
        <span>{{ t('parameter.validation.notBlank') }}</span>
        <el-switch v-model="localRules.not_blank" size="small" />
      </div>

      <!-- STRING rules -->
      <template v-if="dataType === 'STRING'">
        <div class="rule-row">
          <span>{{ t('parameter.validation.minLength') }}</span>
          <el-input-number v-model="localRules.min_length" :min="0" size="small" controls-position="right" />
        </div>
        <div class="rule-row">
          <span>{{ t('parameter.validation.maxLength') }}</span>
          <el-input-number v-model="localRules.max_length" :min="0" size="small" controls-position="right" />
        </div>
        <div class="rule-row">
          <span>{{ t('parameter.validation.pattern') }}</span>
          <el-input v-model="localRules.pattern" size="small" placeholder="^[A-Z].*" />
        </div>
      </template>

      <!-- NUMBER rules -->
      <template v-if="dataType === 'NUMBER'">
        <div class="rule-row">
          <span>{{ t('parameter.validation.min') }}</span>
          <el-input-number v-model="localRules.min" size="small" controls-position="right" />
        </div>
        <div class="rule-row">
          <span>{{ t('parameter.validation.max') }}</span>
          <el-input-number v-model="localRules.max" size="small" controls-position="right" />
        </div>
      </template>

      <!-- ARRAY rules -->
      <template v-if="dataType === 'ARRAY'">
        <div class="rule-row">
          <span>{{ t('parameter.validation.minItems') }}</span>
          <el-input-number v-model="localRules.min_items" :min="0" size="small" controls-position="right" />
        </div>
        <div class="rule-row">
          <span>{{ t('parameter.validation.maxItems') }}</span>
          <el-input-number v-model="localRules.max_items" :min="0" size="small" controls-position="right" />
        </div>
      </template>

      <!-- Enum values (STRING, NUMBER) -->
      <template v-if="dataType === 'STRING' || dataType === 'NUMBER'">
        <div class="rule-row rule-row-vertical">
          <span>{{ t('parameter.validation.enumValues') }}</span>
          <el-select
            v-model="localRules.enum_values"
            multiple
            filterable
            allow-create
            default-first-option
            size="small"
            style="width: 100%"
          />
        </div>
      </template>

      <!-- Custom message -->
      <div class="rule-row rule-row-vertical">
        <span>{{ t('parameter.validation.customMessage') }}</span>
        <el-input v-model="localRules.custom_message" size="small" />
      </div>

      <div class="rule-actions">
        <el-button size="small" @click="emit('update:visible', false)">{{ t('common.cancel') }}</el-button>
        <el-button size="small" type="primary" @click="handleSave">{{ t('parameter.validation.save') }}</el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ValidationRules, DataType } from '@/types/parameter'

const props = defineProps<{
  visible: boolean
  dataType: DataType
  rules: ValidationRules | null
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  save: [rules: ValidationRules]
}>()

const { t } = useI18n()

const localRules = reactive<ValidationRules>({})

watch(() => props.rules, (val) => {
  Object.assign(localRules, {
    not_null: undefined,
    not_blank: undefined,
    min_length: undefined,
    max_length: undefined,
    min: undefined,
    max: undefined,
    pattern: undefined,
    enum_values: undefined,
    min_items: undefined,
    max_items: undefined,
    custom_message: undefined,
    ...val,
  })
}, { immediate: true })

function handleSave() {
  // Clean up undefined/null values
  const cleaned: ValidationRules = {}
  if (localRules.not_null) cleaned.not_null = true
  if (localRules.not_blank) cleaned.not_blank = true
  if (localRules.min_length != null) cleaned.min_length = localRules.min_length
  if (localRules.max_length != null) cleaned.max_length = localRules.max_length
  if (localRules.min != null) cleaned.min = localRules.min
  if (localRules.max != null) cleaned.max = localRules.max
  if (localRules.pattern) cleaned.pattern = localRules.pattern
  if (localRules.enum_values?.length) cleaned.enum_values = localRules.enum_values
  if (localRules.min_items != null) cleaned.min_items = localRules.min_items
  if (localRules.max_items != null) cleaned.max_items = localRules.max_items
  if (localRules.custom_message) cleaned.custom_message = localRules.custom_message

  emit('save', cleaned)
  emit('update:visible', false)
}
</script>

<style scoped>
.validation-rules-popover h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
}
.rule-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  gap: 8px;
}
.rule-row span {
  font-size: 13px;
  white-space: nowrap;
}
.rule-row-vertical {
  flex-direction: column;
  align-items: flex-start;
}
.rule-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
