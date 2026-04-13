<template>
  <div class="recommend-panel">
    <h4>{{ $t('assembly.recommendations') }}</h4>
    <div v-loading="loading">
      <ul v-if="recommendations.length > 0" class="recommend-list">
        <li v-for="rec in recommendations" :key="rec.segmentId" class="recommend-item" @click="$emit('add', { id: rec.segmentId, name: rec.segmentName })">
          <div class="rec-info">
            <span class="rec-name">{{ rec.segmentName }}</span>
            <span class="rec-reason">{{ rec.reason }}</span>
          </div>
          <el-icon class="add-icon"><Plus /></el-icon>
        </li>
      </ul>
      <p v-else-if="!loading" class="no-data">{{ $t('common.noData') }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { getSegmentRecommendations } from '@/api/composite-templates'
import type { SegmentRecommendation } from '@/types/segment'

const props = defineProps<{ templateId: number }>()
defineEmits<{ (e: 'add', segment: { id: number; name: string }): void }>()

const loading = ref(false)
const recommendations = ref<SegmentRecommendation[]>([])

async function fetchRecommendations() {
  loading.value = true
  try {
    recommendations.value = await getSegmentRecommendations(props.templateId)
  } catch { /* handled */ } finally {
    loading.value = false
  }
}

onMounted(fetchRecommendations)
</script>

<style scoped>
.recommend-panel { margin-top: 16px; }
.recommend-panel h4 { margin: 0 0 8px; font-size: 14px; }
.recommend-list { list-style: none; padding: 0; margin: 0; }
.recommend-item { display: flex; align-items: center; gap: 8px; padding: 8px; cursor: pointer; border-radius: 4px; border: 1px solid var(--el-border-color-lighter); margin-bottom: 6px; }
.recommend-item:hover { border-color: var(--el-color-primary-light-5); background: var(--el-fill-color-lighter); }
.rec-info { flex: 1; }
.rec-name { font-weight: 500; font-size: 13px; display: block; }
.rec-reason { font-size: 12px; color: var(--el-text-color-secondary); }
.add-icon { color: var(--el-color-primary); font-size: 16px; }
.no-data { font-size: 13px; color: var(--el-text-color-secondary); }
</style>
