<template>
  <el-card shadow="never" style="margin-bottom: 16px">
    <template #header>{{ $t('composite.coverage') }}</template>
    <div v-loading="loading">
      <template v-if="report">
        <el-progress
          :percentage="Math.round(report.overallCoveragePercent)"
          :status="report.overallCoveragePercent >= 100 ? 'success' : report.overallCoveragePercent >= 80 ? '' : 'warning'"
          :stroke-width="20"
          style="margin-bottom: 12px"
        />
        <el-table :data="report.segmentCoverages" size="small" stripe>
          <el-table-column prop="segmentName" :label="$t('segment.name')" min-width="150" />
          <el-table-column prop="totalVariables" :label="$t('composite.totalVars')" width="100" align="center" />
          <el-table-column prop="boundVariables" :label="$t('composite.boundVars')" width="100" align="center" />
          <el-table-column :label="$t('template.coverageRate')" width="120" align="center">
            <template #default="{ row }">
              {{ Math.round(row.coveragePercent) }}%
            </template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else-if="!loading" :description="$t('common.noData')" />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getCompositeCoverage } from '@/api/composite-templates'
import type { CompositeCoverageReport } from '@/types/segment'

const props = defineProps<{ templateId: number }>()

const loading = ref(false)
const report = ref<CompositeCoverageReport | null>(null)

async function fetchCoverage() {
  loading.value = true
  try {
    report.value = await getCompositeCoverage(props.templateId)
  } catch { /* handled */ } finally {
    loading.value = false
  }
}

onMounted(fetchCoverage)
</script>
