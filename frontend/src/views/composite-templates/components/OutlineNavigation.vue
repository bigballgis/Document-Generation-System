<template>
  <div class="outline-nav">
    <h4>{{ $t('assembly.outline') }}</h4>
    <ul class="outline-list">
      <li
        v-for="(entry, index) in segments"
        :key="entry.filePath + '-' + index"
        class="outline-item"
        :class="{ active: selectedIndex === index, disabled: !entry.enabled }"
        @click="$emit('select', index)"
      >
        <span class="outline-pos">{{ index + 1 }}.</span>
        <span class="outline-name">{{ entry.name }}</span>
      </li>
    </ul>
    <p v-if="segments.length === 0" class="outline-empty">{{ $t('common.noData') }}</p>
  </div>
</template>

<script setup lang="ts">
import type { AssemblySegmentEntry } from '@/types/segment'

defineProps<{
  segments: AssemblySegmentEntry[]
  selectedIndex: number
}>()

defineEmits<{ (e: 'select', index: number): void }>()
</script>

<style scoped>
.outline-nav h4 { margin: 0 0 8px; font-size: 14px; }
.outline-list { list-style: none; padding: 0; margin: 0; }
.outline-item { padding: 6px 8px; cursor: pointer; border-radius: 4px; display: flex; gap: 6px; font-size: 13px; }
.outline-item:hover { background: var(--el-fill-color-light); }
.outline-item.active { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.outline-item.disabled { opacity: 0.5; }
.outline-pos { font-weight: 600; min-width: 20px; }
.outline-empty { font-size: 13px; color: var(--el-text-color-secondary); }
</style>
