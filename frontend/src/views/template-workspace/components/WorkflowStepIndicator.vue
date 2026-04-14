<template>
  <div class="workflow-step-indicator">
    <el-steps :active="activeStepIndex" finish-status="success" align-center>
      <el-step
        v-for="step in steps"
        :key="step.key"
        :title="step.label"
        :status="getStepStatus(step)"
        :class="{ 'step-pulse': step.active }"
        @click="handleStepClick(step.key)"
      />
    </el-steps>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowStep } from '@/types/workspace'

const props = defineProps<{
  steps: WorkflowStep[]
  currentTab: string
}>()

const emit = defineEmits<{
  (e: 'step-click', stepKey: string): void
}>()

const activeStepIndex = computed(() => {
  const idx = props.steps.findIndex(s => s.active)
  return idx >= 0 ? idx : props.steps.filter(s => s.completed).length
})

function getStepStatus(step: WorkflowStep): '' | 'wait' | 'process' | 'finish' | 'success' | 'error' {
  if (step.completed) return 'success'
  if (step.active) return 'process'
  if (step.alwaysAvailable) return 'process'
  return 'wait'
}

function handleStepClick(stepKey: string) {
  emit('step-click', stepKey)
}
</script>

<style scoped>
.workflow-step-indicator {
  padding: 16px 0;
  cursor: pointer;
}
.step-pulse :deep(.el-step__icon) {
  animation: pulse 2s infinite;
}
@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(64, 158, 255, 0.4); }
  50% { box-shadow: 0 0 0 8px rgba(64, 158, 255, 0); }
}
</style>
