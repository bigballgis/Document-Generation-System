<template>
  <div class="components-page">
    <div class="page-header">
      <h2>{{ $t('component.title') }}</h2>
    </div>
    <el-empty :description="$t('common.noData')">
      <template #description>
        <p>{{ $t('common.noData') }}</p>
      </template>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.components-page { padding: 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; }
</style>
