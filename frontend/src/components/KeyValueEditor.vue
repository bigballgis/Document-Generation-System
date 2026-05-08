<template>
  <div class="kv-editor">
    <div v-for="(_, idx) in entries" :key="idx" class="kv-row">
      <el-input
        v-model="entries[idx].key"
        placeholder="Key"
        style="width: 40%; margin-right: 8px"
        @input="emitUpdate"
      />
      <el-input
        v-model="entries[idx].value"
        placeholder="Value"
        style="width: 40%; margin-right: 8px"
        @input="emitUpdate"
      />
      <el-button :icon="Delete" circle size="small" @click="removeEntry(idx)" />
    </div>
    <el-button size="small" :icon="Plus" @click="addEntry">{{ $t('common.create') }}</el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Plus, Delete } from '@element-plus/icons-vue'

const props = defineProps<{
  modelValue: Record<string, string>
}>()

const emit = defineEmits<{
  'update:modelValue': [val: Record<string, string>]
}>()

interface KVEntry {
  key: string
  value: string
}

const entries = ref<KVEntry[]>([])

watch(
  () => props.modelValue,
  (val) => {
    if (!val || typeof val !== 'object') {
      entries.value = []
      return
    }
    const newEntries = Object.entries(val).map(([key, value]) => ({ key, value }))
    // Only reset if structurally different to avoid cursor jumps
    if (JSON.stringify(newEntries) !== JSON.stringify(entries.value)) {
      entries.value = newEntries
    }
  },
  { immediate: true, deep: true },
)

function emitUpdate() {
  const result: Record<string, string> = {}
  for (const e of entries.value) {
    if (e.key.trim()) result[e.key.trim()] = e.value
  }
  emit('update:modelValue', result)
}

function addEntry() {
  entries.value.push({ key: '', value: '' })
}

function removeEntry(idx: number) {
  entries.value.splice(idx, 1)
  emitUpdate()
}
</script>

<style scoped>
.kv-editor {
  width: 100%;
}
.kv-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}
</style>
