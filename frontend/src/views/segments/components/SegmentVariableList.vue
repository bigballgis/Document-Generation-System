<template>
  <div class="segment-variable-list">
    <el-table :data="variables" v-loading="loading" stripe>
      <el-table-column prop="name" :label="$t('segment.variableName')" min-width="180" />
      <el-table-column prop="type" :label="$t('segment.variableType')" width="120" />
      <el-table-column :label="$t('segment.variableRequired')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.required ? 'danger' : 'info'" size="small">
            {{ row.required ? $t('common.yes') : $t('common.no') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="defaultValue" :label="$t('segment.variableDefault')" min-width="150">
        <template #default="{ row }">{{ row.defaultValue || '-' }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getSegmentVariables } from '@/api/segments'
import type { SegmentVariable } from '@/types/segment'

const props = defineProps<{ segmentId: number }>()

const loading = ref(false)
const variables = ref<SegmentVariable[]>([])

async function fetchVariables() {
  loading.value = true
  try {
    variables.value = await getSegmentVariables(props.segmentId)
  } catch { /* handled */ } finally {
    loading.value = false
  }
}

onMounted(fetchVariables)
</script>
