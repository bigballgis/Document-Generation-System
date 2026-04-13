<template>
  <div class="data-scope-mapper">
    <div class="mapper-header">
      <span>{{ $t('assembly.dataScope') }}</span>
      <el-button size="small" link type="primary" @click="addMapping">{{ $t('assembly.addMapping') }}</el-button>
    </div>
    <div v-if="mappings.length > 0" class="mapping-list">
      <div v-for="(m, i) in mappings" :key="i" class="mapping-row">
        <el-input v-model="m.local" size="small" :placeholder="$t('assembly.localVar')" style="width: 140px" @change="emitUpdate" />
        <span class="arrow">→</span>
        <el-input v-model="m.global" size="small" :placeholder="$t('assembly.globalVar')" style="width: 200px" @change="emitUpdate" />
        <el-button size="small" link type="danger" @click="removeMapping(i)">
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>
    </div>
    <p v-else class="no-scope">{{ $t('assembly.noDataScope') }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Delete } from '@element-plus/icons-vue'

const props = defineProps<{ dataScope: Record<string, string> | null }>()
const emit = defineEmits<{ (e: 'update', scope: Record<string, string> | null): void }>()

interface MappingEntry { local: string; global: string }

const mappings = ref<MappingEntry[]>([])

watch(() => props.dataScope, (ds) => {
  if (ds) {
    mappings.value = Object.entries(ds).map(([local, global]) => ({ local, global }))
  } else {
    mappings.value = []
  }
}, { immediate: true })

function addMapping() {
  mappings.value.push({ local: '', global: '' })
}

function removeMapping(index: number) {
  mappings.value.splice(index, 1)
  emitUpdate()
}

function emitUpdate() {
  if (mappings.value.length === 0) {
    emit('update', null)
    return
  }
  const scope: Record<string, string> = {}
  for (const m of mappings.value) {
    if (m.local.trim()) {
      scope[m.local.trim()] = m.global.trim()
    }
  }
  emit('update', Object.keys(scope).length > 0 ? scope : null)
}
</script>

<style scoped>
.data-scope-mapper { margin-top: 8px; }
.mapper-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; font-size: 13px; font-weight: 500; }
.mapping-list { display: flex; flex-direction: column; gap: 4px; }
.mapping-row { display: flex; align-items: center; gap: 6px; }
.arrow { color: var(--el-text-color-secondary); font-size: 14px; }
.no-scope { font-size: 12px; color: var(--el-text-color-secondary); margin: 0; }
</style>
