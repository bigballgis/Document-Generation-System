<template>
  <div class="stage-indicator-mini">
    <template v-for="(stage, index) in stages" :key="stage.name">
      <div v-if="index > 0" class="mini-line" :class="lineClass(index)" />
      <div
        class="mini-stage"
        :class="stageItemClass(stage)"
        :title="stage.label"
        @click="handleClick(stage)"
      >
        <div class="mini-circle" :class="stageCircleClass(stage)">
          <el-icon v-if="isCompleted(stage)" :size="10"><Check /></el-icon>
          <span v-else class="mini-number">{{ index + 1 }}</span>
        </div>
        <span class="mini-label">{{ stage.label }}</span>
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
  if (stage.clickable) emit('stage-click', stage.name)
}

function stageItemClass(stage: StageDefinition) {
  return { 'is-active': stage.name === props.currentStage, 'is-disabled': !stage.clickable }
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
  const bothDone = (prev.status === 'completed' || prev.status === 'readonly') && (curr.status === 'completed' || curr.status === 'readonly')
  return bothDone ? 'line-done' : 'line-pending'
}
</script>

<style scoped>
.stage-indicator-mini { display: flex; align-items: center; gap: 0; }

.mini-stage {
  display: flex; align-items: center; gap: 5px;
  cursor: pointer; padding: 4px 8px; border-radius: 4px;
  transition: background 0.15s;
}
.mini-stage:hover { background: var(--el-fill-color-light); }
.mini-stage.is-disabled { cursor: default; opacity: 0.5; }

.mini-circle {
  width: 20px; height: 20px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 10px; font-weight: 600; flex-shrink: 0;
}

.circle-not-started { background: var(--el-fill-color-light); color: var(--el-text-color-placeholder); border: 1.5px solid var(--el-border-color); }
.circle-in-progress { background: var(--el-color-primary-light-9); color: var(--el-color-primary); border: 1.5px solid var(--el-color-primary); }
.circle-completed { background: var(--el-color-success); color: #fff; border: 1.5px solid var(--el-color-success); }

.mini-label { font-size: 12px; color: var(--el-text-color-secondary); white-space: nowrap; }
.mini-stage.is-active .mini-label { color: var(--el-color-primary); font-weight: 600; }

.mini-line { width: 20px; height: 1.5px; }
.line-done { background: var(--el-color-success); }
.line-pending { background: var(--el-border-color-lighter); }

.mini-number { font-size: 10px; }
</style>
