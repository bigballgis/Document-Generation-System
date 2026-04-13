<template>
  <div class="segment-search-panel">
    <el-input
      v-model="keyword"
      :placeholder="$t('segment.searchPlaceholder')"
      clearable
      size="small"
      @input="handleSearch"
    />
    <ul v-if="results.length > 0" class="search-results">
      <li v-for="seg in results" :key="seg.id" class="search-item" @click="$emit('add', { id: seg.id, name: seg.name })">
        <span>{{ seg.name }}</span>
        <el-tag v-if="seg.isComponent" size="small" type="success">{{ $t('segment.componentSegment') }}</el-tag>
        <el-icon class="add-icon"><Plus /></el-icon>
      </li>
    </ul>
    <p v-else-if="keyword && !loading" class="no-results">{{ $t('common.noData') }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { getSegments } from '@/api/segments'
import type { Segment } from '@/types/segment'

defineEmits<{ (e: 'add', segment: { id: number; name: string }): void }>()

const keyword = ref('')
const results = ref<Segment[]>([])
const loading = ref(false)
let debounceTimer: ReturnType<typeof setTimeout> | null = null

function handleSearch() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(async () => {
    if (!keyword.value.trim()) {
      results.value = []
      return
    }
    loading.value = true
    try {
      const res = await getSegments({ keyword: keyword.value, page: 1, size: 10 })
      results.value = res.content
    } catch { results.value = [] } finally {
      loading.value = false
    }
  }, 300)
}
</script>

<style scoped>
.segment-search-panel { margin-bottom: 16px; }
.search-results { list-style: none; padding: 0; margin: 8px 0 0; max-height: 300px; overflow-y: auto; }
.search-item { display: flex; align-items: center; gap: 8px; padding: 6px 8px; cursor: pointer; border-radius: 4px; font-size: 13px; }
.search-item:hover { background: var(--el-fill-color-light); }
.search-item span:first-child { flex: 1; }
.add-icon { color: var(--el-color-primary); }
.no-results { font-size: 13px; color: var(--el-text-color-secondary); margin-top: 8px; }
</style>
