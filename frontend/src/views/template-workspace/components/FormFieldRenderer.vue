<template>
  <div class="form-field-renderer" :class="{ 'is-highlighted': isHighlighted }">
    <template v-if="field.controlType === 'collapse'">
      <el-collapse v-model="collapseActive">
        <el-collapse-item :title="field.name" :name="field.path">
          <FormFieldRenderer
            v-for="child in field.children"
            :key="child.parameterId"
            :field="child"
            :model-value="getChildValue(child)"
            :readonly="readonly"
            :highlighted-paths="highlightedPaths"
            @update:model-value="setChildValue(child, $event)"
          />
        </el-collapse-item>
      </el-collapse>
    </template>

    <template v-else-if="field.controlType === 'dynamic-list'">
      <div class="array-field">
        <div class="array-header">
          <span class="field-label">{{ field.name }}</span>
          <el-button v-if="!readonly" size="small" @click="addArrayItem">
            {{ t('workspace.testForm.addItem') }}
          </el-button>
        </div>
        <div v-for="(item, idx) in arrayItems" :key="idx" class="array-item">
          <span class="array-index">[{{ idx }}]</span>
          <el-input
            :model-value="String(item ?? '')"
            :disabled="readonly"
            @update:model-value="updateArrayItem(idx, $event)"
          />
          <el-button v-if="!readonly" size="small" type="danger" link @click="removeArrayItem(idx)">
            {{ t('workspace.testForm.removeItem') }}
          </el-button>
        </div>
      </div>
    </template>

    <template v-else>
      <el-form-item
        :label="field.name"
        :required="field.required"
        :class="{ 'is-highlighted': isHighlighted }"
      >
        <el-select
          v-if="field.controlType === 'select'"
          :model-value="modelValue as string"
          :disabled="readonly"
          clearable
          style="width: 100%"
          @update:model-value="emit('update:modelValue', $event)"
        >
          <el-option
            v-for="opt in field.enumValues"
            :key="opt"
            :label="opt"
            :value="opt"
          />
        </el-select>

        <el-switch
          v-else-if="field.controlType === 'switch'"
          :model-value="modelValue as boolean"
          :disabled="readonly"
          @update:model-value="emit('update:modelValue', $event)"
        />

        <el-date-picker
          v-else-if="field.controlType === 'date-picker'"
          :model-value="modelValue as string"
          type="date"
          value-format="YYYY-MM-DD"
          :disabled="readonly"
          style="width: 100%"
          @update:model-value="emit('update:modelValue', $event)"
        />

        <el-input-number
          v-else-if="field.controlType === 'number-input'"
          :model-value="modelValue as number"
          :disabled="readonly"
          controls-position="right"
          style="width: 100%"
          @update:model-value="emit('update:modelValue', $event)"
        />

        <el-input
          v-else
          :model-value="modelValue as string"
          :disabled="readonly"
          @update:model-value="emit('update:modelValue', $event)"
        />
      </el-form-item>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormField } from '@/types/testDataForm'

const props = defineProps<{
  field: FormField
  modelValue: unknown
  readonly: boolean
  highlightedPaths?: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: unknown]
}>()

const { t } = useI18n()

const collapseActive = ref<string[]>([props.field.path])

const isHighlighted = computed(() => {
  return props.highlightedPaths?.includes(props.field.path) ?? false
})

function getChildValue(child: FormField): unknown {
  const obj = (props.modelValue as Record<string, unknown>) ?? {}
  return obj[child.name]
}

function setChildValue(child: FormField, value: unknown) {
  const obj = { ...((props.modelValue as Record<string, unknown>) ?? {}) }
  obj[child.name] = value
  emit('update:modelValue', obj)
}

const arrayItems = computed<unknown[]>(() => {
  return Array.isArray(props.modelValue) ? props.modelValue : []
})

function addArrayItem() {
  emit('update:modelValue', [...arrayItems.value, ''])
}

function removeArrayItem(idx: number) {
  const items = [...arrayItems.value]
  items.splice(idx, 1)
  emit('update:modelValue', items)
}

function updateArrayItem(idx: number, value: unknown) {
  const items = [...arrayItems.value]
  items[idx] = value
  emit('update:modelValue', items)
}
</script>

<style scoped>
.form-field-renderer {
  margin-bottom: 4px;
}
.form-field-renderer.is-highlighted {
  background: var(--el-color-warning-light-9);
  border-radius: 4px;
  padding: 4px 8px;
}
.array-field {
  margin-bottom: 12px;
}
.array-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.field-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}
.array-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.array-index {
  font-family: monospace;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  min-width: 30px;
}
</style>

