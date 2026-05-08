<template>
  <div class="coverage-panel" v-loading="loading">
    <template v-if="report">
      <el-row :gutter="20" style="margin-bottom: 20px">
        <el-col :span="6">
          <el-card shadow="never" class="coverage-stat">
            <el-progress
              type="dashboard"
              :percentage="report.coverageRate"
              :color="coverageColor"
              :width="120"
            />
            <div class="stat-label">{{ $t('template.coverageRate') }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="coverage-stat">
            <div class="stat-value">{{ report.totalVariables }}</div>
            <div class="stat-label">{{ $t('template.variables') }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="coverage-stat">
            <div class="stat-value" style="color: #67c23a">{{ report.boundVariables }}</div>
            <div class="stat-label">{{ $t('template.variableBound') }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="coverage-stat">
            <div class="stat-value" style="color: #f56c6c">{{ report.unboundVariables }}</div>
            <div class="stat-label">{{ $t('template.variableUnbound') }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-alert
        v-if="report.coverageRate < 100"
        :title="$t('template.coverageWarning', { threshold: 100 })"
        type="warning"
        show-icon
        style="margin-bottom: 16px"
      />

      <el-card shadow="never" style="margin-bottom: 16px">
        <template #header>{{ $t('template.boundTags') }}</template>
        <div v-if="report.boundTags.length">
          <el-tag
            v-for="tag in report.boundTags"
            :key="tag"
            type="success"
            size="small"
            style="margin: 0 6px 6px 0"
          >{{ tag }}</el-tag>
        </div>
        <el-empty v-else :image-size="60" :description="$t('common.noData')" />
      </el-card>

      <el-card shadow="never" style="margin-bottom: 16px">
        <template #header>{{ $t('template.unboundTags') }}</template>
        <div v-if="report.unboundTags.length">
          <el-tag
            v-for="tag in report.unboundTags"
            :key="tag"
            type="danger"
            size="small"
            style="margin: 0 6px 6px 0"
          >{{ tag }}</el-tag>
        </div>
        <el-empty v-else :image-size="60" :description="$t('common.noData')" />
      </el-card>

      <el-card shadow="never">
        <template #header>{{ $t('template.unusedFields') }}</template>
        <div v-if="report.unusedFields.length">
          <el-tag
            v-for="field in report.unusedFields"
            :key="field"
            type="info"
            size="small"
            style="margin: 0 6px 6px 0"
          >{{ field }}</el-tag>
        </div>
        <el-empty v-else :image-size="60" :description="$t('common.noData')" />
      </el-card>
    </template>

    <el-empty v-else-if="!loading" :description="$t('common.noData')" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getTemplateCoverage, type CoverageReport } from '@/api/templates'

const props = defineProps<{ templateId: number }>()

const loading = ref(false)
const report = ref<CoverageReport | null>(null)

const coverageColor = computed(() => {
  if (!report.value) return '#909399'
  const rate = report.value.coverageRate
  if (rate >= 100) return '#67c23a'
  if (rate >= 80) return '#e6a23c'
  return '#f56c6c'
})

async function fetchCoverage() {
  loading.value = true
  try {
    report.value = await getTemplateCoverage(props.templateId)
  } catch {} finally {
    loading.value = false
  }
}

onMounted(fetchCoverage)
</script>

<style scoped>
.coverage-stat {
  text-align: center;
  padding: 16px 0;
}
.stat-value {
  font-size: 32px;
  font-weight: bold;
  line-height: 1.2;
}
.stat-label {
  margin-top: 8px;
  color: #909399;
  font-size: 14px;
}
</style>

