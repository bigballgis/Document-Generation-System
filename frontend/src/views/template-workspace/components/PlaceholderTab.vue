<template>
  <div class="placeholder-tab">
    <el-card class="placeholder-card" shadow="hover">
      <div class="placeholder-content">
        <el-icon :size="48" class="placeholder-icon">
          <component :is="icon" />
        </el-icon>
        <h3 class="placeholder-phase">Coming in Phase {{ phase }}</h3>
        <p class="placeholder-message">{{ $t(messageKey) }}</p>
        <p class="placeholder-description">{{ $t(descriptionKey) }}</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import {
  DataLine, Grid, Edit, Checked, Promotion, Switch, Setting,
} from '@element-plus/icons-vue'

// Map icon names to components for dynamic <component :is="icon" /> resolution
const _icons = { DataLine, Grid, Edit, Checked, Promotion, Switch, Setting }
void _icons

defineProps<{
  icon: string
  phase: number
  messageKey: string
  descriptionKey: string
}>()
</script>

<style scoped>
.placeholder-tab {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
  padding: 40px;
}
.placeholder-card {
  max-width: 480px;
  width: 100%;
}
.placeholder-content {
  text-align: center;
  padding: 32px 16px;
}
.placeholder-icon {
  color: var(--el-color-info);
  margin-bottom: 16px;
}
.placeholder-phase {
  font-size: 18px;
  color: var(--el-text-color-primary);
  margin: 0 0 8px;
}
.placeholder-message {
  font-size: 14px;
  color: var(--el-text-color-regular);
  margin: 0 0 4px;
}
.placeholder-description {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0;
}
</style>
