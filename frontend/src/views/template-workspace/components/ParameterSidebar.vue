<template>
  <div class="parameter-sidebar" :class="{ 'is-collapsed': collapsed }">
    <!-- Collapsed -->
    <div v-if="collapsed" class="sidebar-collapsed" @click="emit('update:collapsed', false)">
      <el-icon :size="20"><List /></el-icon>
      <span class="collapsed-label">{{ t('workspace.design.sidebar.parameterList') }}</span>
    </div>

    <!-- Expanded -->
    <div v-else class="sidebar-expanded">
      <!-- Header -->
      <div class="sidebar-header">
        <el-input v-model="searchText" :placeholder="t('workspace.design.sidebar.search')" size="small" clearable :prefix-icon="Search" />
        <el-button size="small" circle @click="emit('update:collapsed', true)">
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>

      <!-- Breadcrumb navigation -->
      <div v-if="breadcrumb.length > 1" class="sidebar-breadcrumb">
        <span
          v-for="(item, idx) in breadcrumb"
          :key="idx"
          class="bc-item"
          :class="{ clickable: idx < breadcrumb.length - 1 }"
          @click="navigateToBreadcrumb(idx)"
        >
          {{ item.name }}
          <span v-if="idx < breadcrumb.length - 1" class="bc-sep">/</span>
        </span>
      </div>

      <!-- Parameter list for current level -->
      <div class="sidebar-content">
        <div v-if="filteredParams.length === 0" class="no-match">
          {{ t('workspace.design.sidebar.noMatch') }}
        </div>
        <div v-else class="param-list">
          <div
            v-for="p in filteredParams"
            :key="p.id"
            class="param-item"
            @click="handleParamClick(p)"
          >
            <div class="param-info">
              <span class="param-name">{{ p.name }}</span>
              <el-tag size="small" :type="dataTypeTagColor(p.dataType)">{{ p.dataType }}</el-tag>
            </div>
            <div class="param-actions">
              <!-- ARRAY: show loop insert button -->
              <el-tooltip v-if="p.dataType === 'ARRAY' && !readonly" :content="t('workspace.design.sidebar.insertLoop')" placement="top">
                <el-button size="small" circle type="warning" plain @click.stop="emit('insert-loop', p.name)">
                  <el-icon :size="12"><RefreshRight /></el-icon>
                </el-button>
              </el-tooltip>
              <!-- Leaf types: show variable insert button -->
              <el-tooltip v-if="isLeaf(p) && !readonly" :content="t('workspace.design.sidebar.insertVariable')" placement="top">
                <el-button size="small" circle type="primary" plain @click.stop="emit('insert-variable', p.parameterPath)">
                  <el-icon :size="12"><Plus /></el-icon>
                </el-button>
              </el-tooltip>
              <!-- Navigable arrow -->
              <el-icon v-if="isNavigable(p)" class="nav-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>

        <!-- Aggregation tags (only when inside an ARRAY) -->
        <div v-if="currentArrayAggregations.length > 0" class="aggregation-section">
          <div class="section-label">{{ t('workspace.design.sidebar.aggregationProperties') }}</div>
          <div class="agg-tags">
            <el-tag
              v-for="agg in currentArrayAggregations"
              :key="agg.path"
              size="small"
              type="primary"
              :class="{ clickable: !readonly }"
              @click="!readonly && emit('insert-variable', agg.path)"
            >{{ agg.name }}</el-tag>
          </div>
        </div>

        <!-- Condition block -->
        <div v-if="!readonly" class="condition-section">
          <div class="section-label">{{ t('workspace.design.sidebar.conditionBlock') }}</div>
          <div class="condition-input">
            <el-input v-model="conditionExpr" :placeholder="t('workspace.design.sidebar.conditionExpr')" size="small" @keyup.enter="handleAddCondition" />
            <el-button size="small" type="primary" :disabled="!conditionExpr.trim()" @click="handleAddCondition">
              {{ t('workspace.design.sidebar.addCondition') }}
            </el-button>
          </div>
        </div>
      </div>

      <!-- Inline creator -->
      <div v-if="!readonly" class="inline-creator">
        <el-button v-if="!showCreator" size="small" text type="primary" @click="showCreator = true">
          {{ t('workspace.design.sidebar.inlineCreate') }}
        </el-button>
        <div v-else class="creator-form">
          <el-input v-model="newParamName" :placeholder="t('parameter.name')" size="small" />
          <el-select v-model="newParamType" size="small" style="width: 100px">
            <el-option v-for="dt in allDataTypes" :key="dt" :label="dt" :value="dt" />
          </el-select>
          <el-button size="small" type="primary" :loading="creating" :disabled="!newParamName.trim()" @click="handleCreateParameter">OK</el-button>
          <el-button size="small" @click="showCreator = false">✕</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { List, ArrowRight, Search, Plus, RefreshRight } from '@element-plus/icons-vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { createParameter } from '@/api/parameters'
import type { ParameterDTO, DataType } from '@/types/parameter'

const props = defineProps<{ collapsed: boolean; readonly: boolean }>()

const emit = defineEmits<{
  'update:collapsed': [value: boolean]
  'insert-variable': [paramPath: string]
  'insert-loop': [arrayName: string]
  'insert-condition': [expr: string]
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

const searchText = ref('')
const conditionExpr = ref('')
const allDataTypes: DataType[] = ['STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'ARRAY', 'OBJECT']

// ── Breadcrumb navigation ──
interface BreadcrumbItem { id: number | null; name: string }
const breadcrumb = ref<BreadcrumbItem[]>([{ id: null, name: 'Root' }])

const currentLevelParams = computed(() => {
  const last = breadcrumb.value[breadcrumb.value.length - 1]
  if (last.id === null) return store.parameters.filter(p => p.parentId === null)
  const parent = findById(store.parameters, last.id)
  return parent?.children ?? []
})

const filteredParams = computed(() => {
  const q = searchText.value.trim().toLowerCase()
  if (!q) return currentLevelParams.value
  return currentLevelParams.value.filter(p => p.name.toLowerCase().includes(q))
})

// ── Aggregation tags for current ARRAY context ──
const currentArrayAggregations = computed(() => {
  const last = breadcrumb.value[breadcrumb.value.length - 1]
  if (last.id === null) return []
  const parent = findById(store.parameters, last.id)
  if (!parent || parent.dataType !== 'ARRAY') return []

  const path = parent.parameterPath || parent.name
  const tags: { name: string; path: string }[] = [
    { name: '$count', path: `${path}.$count` },
    { name: '$first', path: `${path}.$first` },
    { name: '$last', path: `${path}.$last` },
  ]
  for (const child of parent.children ?? []) {
    if (child.dataType === 'NUMBER') {
      tags.push({ name: `$sum_${child.name}`, path: `${path}.$sum_${child.name}` })
      tags.push({ name: `$avg_${child.name}`, path: `${path}.$avg_${child.name}` })
    }
  }
  return tags
})

function findById(params: ParameterDTO[], id: number): ParameterDTO | null {
  for (const p of params) {
    if (p.id === id) return p
    if (p.children?.length) { const f = findById(p.children, id); if (f) return f }
  }
  return null
}

function isNavigable(p: ParameterDTO): boolean { return p.dataType === 'ARRAY' || p.dataType === 'OBJECT' }
function isLeaf(p: ParameterDTO): boolean { return !isNavigable(p) }

function handleParamClick(p: ParameterDTO) {
  console.log('[ParameterSidebar] handleParamClick:', p.name, 'navigable:', isNavigable(p), 'readonly:', props.readonly)
  if (isNavigable(p)) {
    breadcrumb.value.push({ id: p.id, name: p.name })
  } else if (!props.readonly) {
    console.log('[ParameterSidebar] Emitting insert-variable:', p.parameterPath)
    emit('insert-variable', p.parameterPath)
  }
}

function navigateToBreadcrumb(index: number) {
  if (index >= breadcrumb.value.length - 1) return
  breadcrumb.value = breadcrumb.value.slice(0, index + 1)
}

function dataTypeTagColor(dt: DataType): 'success' | 'warning' | 'danger' | 'info' | undefined {
  switch (dt) {
    case 'NUMBER': return 'success'; case 'DATE': return 'warning'; case 'BOOLEAN': return 'danger'
    case 'ARRAY': return 'warning'; case 'OBJECT': return 'info'; default: return undefined
  }
}

function handleAddCondition() {
  const expr = conditionExpr.value.trim()
  if (!expr) return
  emit('insert-condition', expr)
  conditionExpr.value = ''
}

// ── Inline creator ──
const showCreator = ref(false)
const newParamName = ref('')
const newParamType = ref<DataType>('STRING')
const creating = ref(false)

async function handleCreateParameter() {
  const name = newParamName.value.trim()
  if (!name) return
  creating.value = true
  try {
    const parentId = breadcrumb.value[breadcrumb.value.length - 1].id
    await createParameter(store.templateId, { name, dataType: newParamType.value, parentId })
    await store.refreshParameters()
    newParamName.value = ''; showCreator.value = false
    ElMessage.success(t('message.createSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  } finally { creating.value = false }
}
</script>

<style scoped>
.parameter-sidebar { height: 100%; border-right: 1px solid var(--el-border-color-lighter); background: var(--el-bg-color); display: flex; flex-direction: column; transition: width 0.3s; }
.parameter-sidebar.is-collapsed { width: 36px; min-width: 36px; }

.sidebar-collapsed { display: flex; flex-direction: column; align-items: center; padding-top: 12px; gap: 6px; cursor: pointer; height: 100%; }
.sidebar-collapsed:hover { background: var(--el-fill-color-light); }
.collapsed-label { writing-mode: vertical-rl; font-size: 11px; color: var(--el-text-color-secondary); }

.sidebar-expanded { display: flex; flex-direction: column; height: 100%; width: 260px; }

.sidebar-header { display: flex; align-items: center; gap: 6px; padding: 8px 10px; border-bottom: 1px solid var(--el-border-color-lighter); }
.sidebar-header .el-input { flex: 1; }

.sidebar-breadcrumb { padding: 6px 10px; border-bottom: 1px solid var(--el-border-color-lighter); font-size: 12px; display: flex; flex-wrap: wrap; gap: 2px; }
.bc-item { color: var(--el-text-color-secondary); }
.bc-item.clickable { color: var(--el-color-primary); cursor: pointer; }
.bc-item.clickable:hover { text-decoration: underline; }
.bc-sep { margin: 0 2px; color: var(--el-border-color); }

.sidebar-content { flex: 1; overflow-y: auto; padding: 0; }

.param-list { display: flex; flex-direction: column; }
.param-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 12px; border-bottom: 1px solid var(--el-border-color-extra-light);
  cursor: pointer; transition: background 0.1s;
}
.param-item:hover { background: var(--el-fill-color-light); }
.param-info { display: flex; align-items: center; gap: 8px; min-width: 0; }
.param-name { font-size: 13px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.param-actions { display: flex; align-items: center; gap: 4px; flex-shrink: 0; }
.nav-arrow { color: var(--el-text-color-placeholder); font-size: 12px; }

.no-match { padding: 20px; text-align: center; color: var(--el-text-color-secondary); font-size: 13px; }

.aggregation-section, .condition-section { padding: 8px 12px; border-top: 1px solid var(--el-border-color-lighter); }
.section-label { font-size: 11px; font-weight: 600; color: var(--el-text-color-secondary); margin-bottom: 6px; text-transform: uppercase; }
.agg-tags { display: flex; flex-wrap: wrap; gap: 4px; }
.agg-tags .el-tag.clickable { cursor: pointer; }
.agg-tags .el-tag.clickable:hover { opacity: 0.8; }

.condition-input { display: flex; gap: 6px; }
.condition-input .el-input { flex: 1; }

.inline-creator { padding: 6px 10px; border-top: 1px solid var(--el-border-color-lighter); flex-shrink: 0; }
.creator-form { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.creator-form .el-input { flex: 1; min-width: 80px; }
</style>
