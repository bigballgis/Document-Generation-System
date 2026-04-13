<template>
  <div class="transform-rules-editor">
    <div v-for="(rule, idx) in rules" :key="idx" class="rule-row">
      <el-select v-model="rule.type" style="width: 140px; margin-right: 8px" @change="emitUpdate">
        <el-option value="JSONPATH" :label="$t('dataSource.jsonPath')" />
        <el-option value="XPATH" :label="$t('dataSource.xPath')" />
        <el-option value="FLATTEN" :label="$t('dataSource.flatten')" />
        <el-option value="GROUP_BY" :label="$t('dataSource.groupBy')" />
        <el-option value="SORT_BY" :label="$t('dataSource.sortBy')" />
      </el-select>
      <el-input
        v-model="rule.expression"
        :placeholder="expressionPlaceholder(rule.type)"
        style="flex: 1; margin-right: 8px"
        @input="emitUpdate"
      />
      <el-input
        v-model="rule.targetField"
        placeholder="Target field"
        style="width: 140px; margin-right: 8px"
        @input="emitUpdate"
      />
      <el-button :icon="Delete" circle size="small" @click="removeRule(idx)" />
    </div>
    <el-button size="small" :icon="Plus" @click="addRule">{{ $t('common.create') }}</el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Plus, Delete } from '@element-plus/icons-vue'
import type { TransformRule } from '@/api/data-sources'

const props = defineProps<{
  modelValue?: TransformRule[]
}>()

const emit = defineEmits<{
  'update:modelValue': [val: TransformRule[]]
}>()

const rules = ref<TransformRule[]>([])

watch(
  () => props.modelValue,
  (val) => {
    if (val && JSON.stringify(val) !== JSON.stringify(rules.value)) {
      rules.value = val.map((r) => ({ ...r }))
    }
  },
  { immediate: true, deep: true },
)

function expressionPlaceholder(type: string) {
  const map: Record<string, string> = {
    JSONPATH: '$.data.items[*]',
    XPATH: '/root/items/item',
    FLATTEN: 'nestedField',
    GROUP_BY: 'category',
    SORT_BY: 'createdAt:desc',
  }
  return map[type] || ''
}

function emitUpdate() {
  emit('update:modelValue', rules.value.map((r) => ({ ...r })))
}

function addRule() {
  rules.value.push({ type: 'JSONPATH', expression: '', targetField: '' })
  emitUpdate()
}

function removeRule(idx: number) {
  rules.value.splice(idx, 1)
  emitUpdate()
}
</script>

<style scoped>
.transform-rules-editor {
  width: 100%;
}
.rule-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}
</style>
