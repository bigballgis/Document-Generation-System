<template>
  <div class="pipeline-viz">
    <div class="pipeline-stages">
      <!-- Stage: Fetch -->
      <div class="stage-column">
        <div class="stage-header fetch">{{ $t('dataSource.pipelineFetch') }}</div>
        <div
          v-for="ds in dataSources"
          :key="'fetch-' + ds.id"
          class="pipeline-node fetch"
        >
          <el-tag :type="typeTagColor(ds.type)" size="small" style="margin-bottom: 4px">
            {{ typeLabel(ds.type) }}
          </el-tag>
          <div class="node-name">{{ ds.name }}</div>
          <div class="node-meta">P{{ ds.priority }}</div>
        </div>
      </div>

      <!-- Arrow -->
      <div class="stage-arrow">
        <el-icon :size="24"><Right /></el-icon>
      </div>

      <!-- Stage: Transform -->
      <div class="stage-column">
        <div class="stage-header transform">{{ $t('dataSource.pipelineTransform') }}</div>
        <div
          v-for="ds in dataSourcesWithTransform"
          :key="'transform-' + ds.id"
          class="pipeline-node transform"
        >
          <div class="node-name">{{ ds.name }}</div>
          <div class="node-meta">{{ transformRuleCount(ds) }} {{ $t('dataSource.responseTransform') }}</div>
        </div>
        <div v-if="dataSourcesWithTransform.length === 0" class="empty-stage">—</div>
      </div>

      <!-- Arrow -->
      <div class="stage-arrow">
        <el-icon :size="24"><Right /></el-icon>
      </div>

      <!-- Stage: Compute -->
      <div class="stage-column">
        <div class="stage-header compute">{{ $t('dataSource.pipelineCompute') }}</div>
        <div class="pipeline-node compute">
          <div class="node-name">{{ $t('expression.title') }}</div>
          <div class="node-meta">{{ dataSources.length }} {{ $t('dataSource.list') }}</div>
        </div>
      </div>

      <!-- Arrow -->
      <div class="stage-arrow">
        <el-icon :size="24"><Right /></el-icon>
      </div>

      <!-- Stage: Validate -->
      <div class="stage-column">
        <div class="stage-header validate">{{ $t('dataSource.pipelineValidate') }}</div>
        <div class="pipeline-node validate">
          <div class="node-name">{{ $t('dataSource.pipelineValidate') }}</div>
          <div class="node-meta">{{ dataSources.length }} {{ $t('dataSource.list') }}</div>
        </div>
      </div>
    </div>

    <!-- Dependency info -->
    <div v-if="dependencyInfo.length > 0" class="dependency-section">
      <el-text type="info" size="small">{{ $t('dataSource.dependsOn') }}:</el-text>
      <div v-for="dep in dependencyInfo" :key="dep.name" class="dep-item">
        <el-text size="small">{{ dep.name }} → {{ dep.dependsOn }}</el-text>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Right } from '@element-plus/icons-vue'
import type { DataSourceDTO } from '@/api/data-sources'

const props = defineProps<{
  dataSources: DataSourceDTO[]
}>()

const { t } = useI18n()

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

function typeTagColor(type: string): TagType {
  const map: Record<string, TagType> = { HTTP_API: 'primary', DATABASE: 'success', INTERNAL_SYSTEM: 'warning' }
  return map[type] || 'info'
}

function typeLabel(type: string) {
  const map: Record<string, string> = {
    HTTP_API: t('dataSource.typeHttpApi'),
    DATABASE: t('dataSource.typeDatabase'),
    INTERNAL_SYSTEM: t('dataSource.typeInternal'),
  }
  return map[type] || type
}

function parseConfig(ds: DataSourceDTO) {
  try {
    return JSON.parse(ds.configJson)
  } catch {
    return {}
  }
}

const dataSourcesWithTransform = computed(() =>
  props.dataSources.filter((ds) => {
    const config = parseConfig(ds)
    return config.responseTransformRules && config.responseTransformRules.length > 0
  }),
)

function transformRuleCount(ds: DataSourceDTO): number {
  const config = parseConfig(ds)
  return config.responseTransformRules?.length ?? 0
}

const dependencyInfo = computed(() => {
  const deps: { name: string; dependsOn: string }[] = []
  for (const ds of props.dataSources) {
    const config = parseConfig(ds)
    if (config.dependsOn && Array.isArray(config.dependsOn) && config.dependsOn.length > 0) {
      deps.push({ name: ds.name, dependsOn: config.dependsOn.join(', ') })
    }
  }
  return deps
})
</script>

<style scoped>
.pipeline-viz {
  background: var(--el-bg-color-page, #f5f7fa);
  border-radius: 8px;
  padding: 20px;
}
.pipeline-stages {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.stage-column {
  flex: 1;
  min-width: 140px;
}
.stage-header {
  text-align: center;
  font-weight: 600;
  padding: 8px 12px;
  border-radius: 6px;
  margin-bottom: 12px;
  color: #fff;
}
.stage-header.fetch { background: #409eff; }
.stage-header.transform { background: #e6a23c; }
.stage-header.compute { background: #67c23a; }
.stage-header.validate { background: #909399; }
.pipeline-node {
  background: #fff;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 8px;
  border-left: 3px solid #dcdfe6;
}
.pipeline-node.fetch { border-left-color: #409eff; }
.pipeline-node.transform { border-left-color: #e6a23c; }
.pipeline-node.compute { border-left-color: #67c23a; }
.pipeline-node.validate { border-left-color: #909399; }
.node-name {
  font-weight: 500;
  font-size: 13px;
}
.node-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.stage-arrow {
  display: flex;
  align-items: center;
  padding-top: 40px;
  color: #c0c4cc;
}
.empty-stage {
  text-align: center;
  color: #c0c4cc;
  padding: 12px;
}
.dependency-section {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}
.dep-item {
  margin-top: 4px;
}
</style>
