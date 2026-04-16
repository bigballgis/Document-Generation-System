<template>
  <div class="stage-indicator">
    <template v-for="(stage, index) in stages" :key="stage.name">
      <!-- Connecting line (before each stage except the first) -->
      <div
        v-if="index > 0"
        class="stage-line"
        :class="lineClass(index)"
        data-testid="stage-line"
      />
      <!-- Stage circle + label -->
      <div
        class="stage-item"
        :class="stageItemClass(stage)"
        :data-testid="`stage-${stage.name}`"
        @click="handleClick(stage)"
      >
        <div class="stage-circle" :class="stageCircleClass(stage)">
          <el-icon v-if="isCompleted(stage)" :size="16"><Check /></el-icon>
          <span v-else class="stage-number">{{ index + 1 }}</span>
        </div>
        <span class="stage-label">{{ stage.label }}</span>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { Check } from '@element-plus/icons-vue'
import type { StageName, StageDefinition } from '@/types/workspace'

const props = defineProps<{
  stages: StageDefinition[]
  currentStage: StageName
}>()

const emit = defineEmits<{
  (e: 'stage-click', stage: StageName): void
}>()

function isCompleted(stage: StageDefinition): boolean {
  return stage.status === 'completed' || stage.status === 'readonly'
}

function handleClick(stage: StageDefinition) {
  if (stage.clickable) {
    emit('stage-click', stage.name)
  }
}

function stageItemClass(stage: StageDefinition) {
  return {
    'is-active': stage.name === props.currentStage,
    'is-disabled': !stage.clickable,
  }
}

function stageCircleClass(stage: StageDefinition) {
  return {
    'circle-not-started': stage.status === 'not_started',
    'circle-in-progress': stage.status === 'in_progress',
    'circle-completed': stage.status === 'completed' || stage.status === 'readonly',
  }
}

function lineClass(index: number) {
  const prev = props.stages[index - 1]
  const curr = props.stages[index]
  const bothDone =
    (prev.status === 'completed' || prev.status === 'readonly') &&
    (curr.status === 'completed' || curr.status === 'readonly')
  return bothDone ? 'line-completed' : 'line-pending'
}
</script>

<style scoped>
.stage-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 0;
  gap: 0;
}

.stage-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  min-width: 80px;
}

.stage-item.is-disabled {
  cursor: default;
  pointer-events: none;
}

.stage-circle {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s;
}

.circle-not-started {
  background-color: var(--el-fill-color-light, #f0f2f5);
  color: var(--el-text-color-placeholder, #a8abb2);
  border: 2px solid var(--el-border-color-lighter, #e4e7ed);
}

.circle-in-progress {
  background-color: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
  border: 2px solid var(--el-color-primary, #409eff);
  animation: pulse 2s infinite;
}

.circle-completed {
  background-color: var(--el-color-success, #67c23a);
  color: #fff;
  border: 2px solid var(--el-color-success, #67c23a);
}

.stage-label {
  margin-top: 8px;
  font-size: 13px;
  color: var(--el-text-color-regular, #606266);
  white-space: nowrap;
}

.stage-item.is-active .stage-label {
  color: var(--el-color-primary, #409eff);
  font-weight: 600;
}

.stage-line {
  width: 60px;
  height: 2px;
  margin: 0 4px;
  margin-bottom: 28px; /* align with circle center */
}

.line-completed {
  background-color: var(--el-color-success, #67c23a);
}

.line-pending {
  background: repeating-linear-gradient(
    90deg,
    var(--el-border-color, #dcdfe6) 0,
    var(--el-border-color, #dcdfe6) 6px,
    transparent 6px,
    transparent 10px
  );
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(64, 158, 255, 0.4); }
  50% { box-shadow: 0 0 0 8px rgba(64, 158, 255, 0); }
}
</style>
