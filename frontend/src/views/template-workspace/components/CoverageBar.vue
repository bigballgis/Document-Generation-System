<template>
  <div class="coverage-bar" :class="{ 'all-covered': allCovered }">
    <div class="coverage-item">
      <span class="coverage-label">{{ t('workspace.coverageBar.branch') }}</span>
      <el-progress
        :percentage="branchCoverage"
        :stroke-width="8"
        :text-inside="true"
        :status="branchCoverage >= 100 ? 'success' : ''"
        style="width: 140px"
      />
    </div>
    <div class="coverage-item">
      <span class="coverage-label">{{ t('workspace.coverageBar.loop') }}</span>
      <el-progress
        :percentage="loopCoverage"
        :stroke-width="8"
        :text-inside="true"
        :status="loopCoverage >= 100 ? 'success' : ''"
        style="width: 140px"
      />
    </div>
    <div class="coverage-item">
      <span class="coverage-label">{{ t('workspace.coverageBar.param') }}</span>
      <el-progress
        :percentage="parameterCoverage"
        :stroke-width="8"
        :text-inside="true"
        :status="parameterCoverage >= 100 ? 'success' : ''"
        style="width: 140px"
      />
    </div>
    <el-tag v-if="allCovered" type="success" size="small">
      {{ t('workspace.coverageBar.allCovered') }}
    </el-tag>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = withDefaults(
  defineProps<{
    branchCoverage?: number
    loopCoverage?: number
    parameterCoverage?: number
  }>(),
  {
    branchCoverage: 0,
    loopCoverage: 0,
    parameterCoverage: 0,
  },
)

const { t } = useI18n()

const branchCoverage = computed(() => Math.round(props.branchCoverage * 100) / 100)
const loopCoverage = computed(() => Math.round(props.loopCoverage * 100) / 100)
const parameterCoverage = computed(() => Math.round(props.parameterCoverage * 100) / 100)

const allCovered = computed(
  () =>
    branchCoverage.value >= 100 &&
    loopCoverage.value >= 100 &&
    parameterCoverage.value >= 100,
)
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
