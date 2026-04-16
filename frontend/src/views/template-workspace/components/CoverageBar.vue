<template>
  <div class="coverage-bar" :class="{ 'all-covered': allCovered }">
    <div class="coverage-item">
      <span class="coverage-label">{{ t('workspace.coverageBar.branch') }}</span>
      <el-progress
        :percentage="coverageData?.branchCoverage ?? 0"
        :stroke-width="8"
        :text-inside="true"
        :status="(coverageData?.branchCoverage ?? 0) >= 100 ? 'success' : ''"
        style="width: 140px"
      />
    </div>
    <div class="coverage-item">
      <span class="coverage-label">{{ t('workspace.coverageBar.loop') }}</span>
      <el-progress
        :percentage="coverageData?.loopCoverage ?? 0"
        :stroke-width="8"
        :text-inside="true"
        :status="(coverageData?.loopCoverage ?? 0) >= 100 ? 'success' : ''"
        style="width: 140px"
      />
    </div>
    <div class="coverage-item">
      <span class="coverage-label">{{ t('workspace.coverageBar.param') }}</span>
      <el-progress
        :percentage="coverageData?.parameterCoverage ?? 0"
        :stroke-width="8"
        :text-inside="true"
        :status="(coverageData?.parameterCoverage ?? 0) >= 100 ? 'success' : ''"
        style="width: 140px"
      />
    </div>
    <el-tag v-if="allCovered" type="success" size="small">
      {{ t('workspace.coverageBar.allCovered') }}
    </el-tag>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import request from '@/api/request'
import type { CoverageReport as ParameterCoverageReport } from '@/types/parameter'

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

const coverageData = ref<ParameterCoverageReport | null>(null)

const allCovered = computed(() => {
  if (!coverageData.value) return false
  return (
    coverageData.value.branchCoverage >= 100 &&
    coverageData.value.loopCoverage >= 100 &&
    coverageData.value.parameterCoverage >= 100
  )
})

onMounted(async () => {
  try {
    coverageData.value = await request.get<any, ParameterCoverageReport>(`/templates/${store.templateId}/coverage`)
  } catch {
    // silent — coverage data is optional
  }
})

defineExpose({ coverageData })
</script>

<style scoped>
.coverage-bar {
  display: flex;
  gap: 24px;
  align-items: center;
  padding: 12px 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}
.coverage-bar.all-covered {
  background: var(--el-color-success-light-9);
}
.coverage-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.coverage-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
</style>
