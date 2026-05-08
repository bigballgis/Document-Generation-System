<template>
  <el-popover :visible="visible" placement="right" :width="340" trigger="click">
    <template #reference>
      <slot />
    </template>
    <div class="validation-rules-popover">
      <h4>{{ t('parameter.validation.title') }}</h4>

      <div v-if="availablePresets.length > 0" class="presets-section">
        <span class="presets-label">{{ t('parameter.validation.presets') }}</span>
        <div class="presets-row">
          <el-tag
            v-for="preset in availablePresets"
            :key="preset.key"
            size="small"
            :type="activePreset === preset.key ? 'primary' : 'info'"
            effect="plain"
            class="preset-tag"
            @click="applyPreset(preset)"
          >
            {{ preset.label }}
          </el-tag>
        </div>
      </div>

      <template v-if="dataType === 'STRING'">
        <div class="rule-row">
          <span>{{ t('parameter.validation.notBlank') }}</span>
          <el-switch v-model="localRules.not_blank" size="small" />
        </div>
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

      <template v-if="dataType === 'DATE'">
        <div class="rule-row">
          <span>{{ t('parameter.validation.dateFormat') }}</span>
          <el-select v-model="localRules.date_format" size="small" clearable placeholder="YYYY-MM-DD">
            <el-option label="YYYY-MM-DD" value="YYYY-MM-DD" />
            <el-option label="YYYY/MM/DD" value="YYYY/MM/DD" />
            <el-option label="DD-MM-YYYY" value="DD-MM-YYYY" />
            <el-option label="MM/DD/YYYY" value="MM/DD/YYYY" />
            <el-option label="YYYY-MM-DD HH:mm:ss" value="YYYY-MM-DD HH:mm:ss" />
            <el-option label="ISO 8601" value="ISO_8601" />
          </el-select>
        </div>
        <div class="rule-row">
          <span>{{ t('parameter.validation.dateBefore') }}</span>
          <el-date-picker v-model="localRules.date_before" type="date" size="small" value-format="YYYY-MM-DD" clearable />
        </div>
        <div class="rule-row">
          <span>{{ t('parameter.validation.dateAfter') }}</span>
          <el-date-picker v-model="localRules.date_after" type="date" size="small" value-format="YYYY-MM-DD" clearable />
        </div>
      </template>

      <template v-if="dataType === 'STRING' || dataType === 'NUMBER'">
        <div class="rule-row rule-row-vertical">
          <span>{{ t('parameter.validation.enumValues') }}</span>
          <div class="enum-tags-container">
            <el-tag
              v-for="(val, idx) in (localRules.enum_values || [])"
              :key="idx"
              closable
              size="small"
              class="enum-tag"
              @close="removeEnumValue(idx)"
            >
              {{ val }}
            </el-tag>
            <el-input
              v-model="newEnumValue"
              size="small"
              class="enum-input"
              :placeholder="t('parameter.validation.enumPlaceholder')"
              @keyup.enter="addEnumValue"
            >
              <template #append>
                <el-button :icon="Plus" @click="addEnumValue" />
              </template>
            </el-input>
          </div>
        </div>
      </template>

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
import { reactive, ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus } from '@element-plus/icons-vue'
import type { ValidationRules, DataType } from '@/types/parameter'

interface RulePreset {
  key: string
  label: string
  dataType: DataType
  rules: Partial<ValidationRules>
}

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
const newEnumValue = ref('')
const activePreset = ref<string | null>(null)

const allPresets: RulePreset[] = [
  { key: 'email', label: '📧 Email', dataType: 'STRING', rules: { not_blank: true, max_length: 254, pattern: '^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$' } },
  { key: 'phone', label: '📱 Phone', dataType: 'STRING', rules: { not_blank: true, pattern: '^\\+?[0-9\\-\\s()]{7,20}$' } },
  { key: 'hkid', label: '🪪 HKID', dataType: 'STRING', rules: { not_blank: true, pattern: '^[A-Z]{1,2}[0-9]{6}[0-9A]$' } },
  { key: 'cn_id', label: '🪪 CN ID', dataType: 'STRING', rules: { not_blank: true, pattern: '^[0-9]{17}[0-9Xx]$' } },
  { key: 'passport', label: '🛂 Passport', dataType: 'STRING', rules: { not_blank: true, pattern: '^[A-Z0-9]{5,20}$' } },
  { key: 'br_no', label: '🏢 BR No.', dataType: 'STRING', rules: { not_blank: true, pattern: '^[0-9]{8}-[0-9]{3}-[0-9]{2}-[0-9]{2}$' } },
  { key: 'usci', label: '🏢 USCI', dataType: 'STRING', rules: { not_blank: true, pattern: '^[0-9A-Z]{18}$' } },
  { key: 'lei', label: '🏢 LEI', dataType: 'STRING', rules: { not_blank: true, pattern: '^[A-Z0-9]{20}$' } },
  { key: 'iban', label: '💳 IBAN', dataType: 'STRING', rules: { not_blank: true, pattern: '^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$' } },
  { key: 'swift', label: '🏦 SWIFT', dataType: 'STRING', rules: { not_blank: true, pattern: '^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$' } },
  { key: 'bank_acct', label: '🏦 Bank Acct', dataType: 'STRING', rules: { not_blank: true, pattern: '^[0-9\\-]{6,34}$' } },
  { key: 'currency', label: '💰 Currency', dataType: 'STRING', rules: { not_blank: true, pattern: '^[A-Z]{3}$', enum_values: ['HKD', 'CNY', 'USD', 'EUR', 'GBP', 'JPY', 'SGD', 'MYR', 'THB', 'IDR', 'PHP', 'VND', 'AED', 'SAR', 'QAR', 'BHD', 'KWD', 'OMR', 'TWD', 'KRW', 'AUD', 'CHF'] } },
  { key: 'ref_no', label: '🔢 Ref No.', dataType: 'STRING', rules: { not_blank: true, max_length: 50, pattern: '^[A-Z0-9\\-/]+$' } },
  { key: 'url', label: '🔗 URL', dataType: 'STRING', rules: { not_blank: true, max_length: 2048, pattern: '^https?://.+' } },
  { key: 'postal', label: '📮 Postal', dataType: 'STRING', rules: { not_blank: true, pattern: '^[A-Z0-9\\s\\-]{3,10}$' } },
  { key: 'amount', label: '💰 Amount', dataType: 'NUMBER', rules: { min: 0 } },
  { key: 'rate', label: '% Rate', dataType: 'NUMBER', rules: { min: 0, max: 100 } },
  { key: 'quantity', label: '📦 Quantity', dataType: 'NUMBER', rules: { min: 1 } },
  { key: 'tenor', label: '📅 Tenor', dataType: 'NUMBER', rules: { min: 1, max: 360 } },
  { key: 'iso_date', label: '📅 ISO', dataType: 'DATE', rules: { date_format: 'YYYY-MM-DD' } },
  { key: 'datetime', label: '🕐 DateTime', dataType: 'DATE', rules: { date_format: 'YYYY-MM-DD HH:mm:ss' } },
  { key: 'dd_mm_yyyy', label: '📅 DD/MM/YYYY', dataType: 'DATE', rules: { date_format: 'DD-MM-YYYY' } },
]

const availablePresets = computed(() =>
  allPresets.filter(p => p.dataType === props.dataType)
)

function applyPreset(preset: RulePreset) {
  activePreset.value = preset.key
  Object.assign(localRules, {
    not_blank: undefined,
    min_length: undefined,
    max_length: undefined,
    min: undefined,
    max: undefined,
    pattern: undefined,
    enum_values: undefined,
    min_items: undefined,
    max_items: undefined,
    date_format: undefined,
    date_before: undefined,
    date_after: undefined,
    custom_message: localRules.custom_message, // preserve custom message
    ...preset.rules,
  })
}

function addEnumValue() {
  const val = newEnumValue.value.trim()
  if (!val) return
  if (!localRules.enum_values) localRules.enum_values = []
  if (localRules.enum_values.includes(val)) return
  localRules.enum_values.push(val)
  newEnumValue.value = ''
}

function removeEnumValue(index: number) {
  localRules.enum_values?.splice(index, 1)
}

watch(() => props.rules, (val) => {
  activePreset.value = null
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
    date_format: undefined,
    date_before: undefined,
    date_after: undefined,
    custom_message: undefined,
    ...val,
  })
}, { immediate: true })

function handleSave() {
  const cleaned: ValidationRules = {}
  if (localRules.not_blank) cleaned.not_blank = true
  if (localRules.min_length != null) cleaned.min_length = localRules.min_length
  if (localRules.max_length != null) cleaned.max_length = localRules.max_length
  if (localRules.min != null) cleaned.min = localRules.min
  if (localRules.max != null) cleaned.max = localRules.max
  if (localRules.pattern) cleaned.pattern = localRules.pattern
  if (localRules.enum_values?.length) cleaned.enum_values = localRules.enum_values
  if (localRules.min_items != null) cleaned.min_items = localRules.min_items
  if (localRules.max_items != null) cleaned.max_items = localRules.max_items
  if (localRules.date_format) cleaned.date_format = localRules.date_format
  if (localRules.date_before) cleaned.date_before = localRules.date_before
  if (localRules.date_after) cleaned.date_after = localRules.date_after
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
.presets-section {
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.presets-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  display: block;
  margin-bottom: 8px;
}
.presets-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.preset-tag {
  cursor: pointer;
  transition: all 0.2s;
}
.preset-tag:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
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
.enum-tags-container {
  width: 100%;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.enum-tag {
  margin: 0;
}
.enum-input {
  margin-top: 4px;
  width: 100%;
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


