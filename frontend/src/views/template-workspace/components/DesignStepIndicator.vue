<template>
  <div class="design-step-indicator">
    <div
      v-for="(step, idx) in steps"
      :key="step.name"
      class="step-item"
      :class="{
        active: step.name === currentStep,
        completed: stepStatuses[step.name] === 'completed',
      }"
      @click="emit('update:currentStep', step.name)"
    >
      <!-- Connector line (before each step except first) -->
      <div v-if="idx > 0" class="step-connector" />

      <!-- Step circle -->
      <div class="step-circle">
        <el-icon v-if="stepStatuses[step.name] === 'completed'" :size="14">
          <Check />
        </el-icon>
        <span v-else>{{ idx + 1 }}</span>
      </div>

      <!-- Step label -->
      <span class="step-label">{{ step.label }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Check } from '@element-plus/icons-vue'
import type { DesignStepName } from '@/types/workspace'

type StepStatus = 'not_started' | 'in_progress' | 'completed'

defineProps<{
  currentStep: DesignStepName
  stepStatuses: Record<DesignStepName, StepStatus>
}>()

const emit = defineEmits<{
  'update:currentStep': [step: DesignStepName]
}>()

const { t } = useI18n()

const steps = computed(() => [
  { name: 'parameter-table' as DesignStepName, label: t('workspace.design.step.parameterTable') },
  { name: 'segment-canvas' as DesignStepName, label: t('workspace.design.step.segmentCanvas') },
  { name: 'segment-detail' as DesignStepName, label: t('workspace.design.step.segmentDetail') },
])
</script>

<style scoped>
.design-step-indicator {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 12px 16px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.step-item {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
  transition: color 0.2s;
}

.step-item:hover .step-label {
  color: var(--el-color-primary);
}

.step-connector {
  width: 40px;
  height: 1px;
  background: var(--el-border-color);
  margin: 0 8px;
}

.step-circle {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  border: 1px solid var(--el-border-color);
  transition: all 0.2s;
}

.step-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  transition: color 0.2s;
}

/* Active step */
.step-item.active .step-circle {
  background: var(--el-color-primary);
  color: #fff;
  border-color: var(--el-color-primary);
}

.step-item.active .step-label {
  color: var(--el-color-primary);
  font-weight: 600;
}

/* Completed step */
.step-item.completed .step-circle {
  background: var(--el-color-success-light-3);
  color: var(--el-color-success);
  border-color: var(--el-color-success);
}

.step-item.completed .step-label {
  color: var(--el-color-success);
}

/* Active overrides completed styling */
.step-item.active.completed .step-circle {
  background: var(--el-color-primary);
  color: #fff;
  border-color: var(--el-color-primary);
}

.step-item.active.completed .step-label {
  color: var(--el-color-primary);
}
</style>
