<template>
  <div class="coverage-bar" :class="{ 'all-covered': allCovered }">
    <div class="coverage-item">
      <div class="coverage-item-header">
        <span class="coverage-label">{{ t('workspace.coverageBar.branch') }}</span>
        <span class="coverage-value">{{ fmt(branchCoverage) }}</span>
      </div>
      <el-progress
        :percentage="branchCoverage"
        :stroke-width="10"
        :text-inside="false"
        :status="branchCoverage >= 100 ? 'success' : ''"
      />
    </div>
    <div class="coverage-item">
      <div class="coverage-item-header">
        <span class="coverage-label">{{ t('workspace.coverageBar.loop') }}</span>
        <span class="coverage-value">{{ fmt(loopCoverage) }}</span>
      </div>
      <el-progress
        :percentage="loopCoverage"
        :stroke-width="10"
        :text-inside="false"
        :status="loopCoverage >= 100 ? 'success' : ''"
      />
    </div>
    <div class="coverage-item">
      <div class="coverage-item-header">
        <span class="coverage-label">{{ t('workspace.coverageBar.param') }}</span>
        <span class="coverage-value">{{ fmt(parameterCoverage) }}</span>
      </div>
      <el-progress
        :percentage="parameterCoverage"
        :stroke-width="10"
        :text-inside="false"
        :status="parameterCoverage >= 100 ? 'success' : ''"
      />
    </div>
    <el-tag v-if="allCovered" type="success" size="small" class="all-covered-tag">
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

function fmt(v: number) {
  return `${Math.round(v * 100) / 100}%`
}

const allCovered = computed(
  () =>
    branchCoverage.value >= 100 &&
    loopCoverage.value >= 100 &&
    parameterCoverage.value >= 100,
)
</script>

<style scoped>
.coverage-bar {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  padding: 12px 12px 10px;
  background: linear-gradient(180deg, var(--el-fill-color-light), var(--el-fill-color-lighter));
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
}
.coverage-bar.all-covered {
  background: linear-gradient(180deg, var(--el-color-success-light-9), var(--el-fill-color-lighter));
}
.coverage-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.coverage-item-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.coverage-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
  font-weight: 600;
}
.coverage-value {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}
.all-covered-tag {
  width: fit-content;
  justify-self: end;
  margin-top: 2px;
}
</style>
