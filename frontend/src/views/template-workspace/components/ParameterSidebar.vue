<template>
  <div class="parameter-sidebar" :class="{ 'is-collapsed': collapsed }">
    <!-- Collapsed state -->
    <div v-if="collapsed" class="sidebar-collapsed" @click="emit('update:collapsed', false)">
      <el-icon :size="20"><List /></el-icon>
      <span class="collapsed-label">{{ t('workspace.design.sidebar.parameterList') }}</span>
    </div>

    <!-- Expanded state -->
    <div v-else class="sidebar-expanded">
      <!-- Header with search + collapse -->
      <div class="sidebar-header">
        <el-input
          v-model="searchText"
          :placeholder="t('workspace.design.sidebar.search')"
          size="small"
          clearable
          :prefix-icon="Search"
        />
        <el-button size="small" circle @click="emit('update:collapsed', true)">
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>

      <!-- Type filter tags -->
      <div class="type-filter">
        <span class="filter-label">{{ t('workspace.design.sidebar.filterByType') }}</span>
        <div class="filter-tags">
          <el-tag
            v-for="dt in allDataTypes"
            :key="dt"
            :type="activeTypeFilter === dt ? undefined : 'info'"
            :effect="activeTypeFilter === dt ? 'dark' : 'plain'"
            size="small"
            class="filter-tag"
            @click="toggleTypeFilter(dt)"
          >
            {{ dt }}
          </el-tag>
        </div>
      </div>

      <!-- Scrollable content -->
      <div class="sidebar-content">
        <!-- Parameter List Section -->
        <el-collapse v-model="activeSections">
          <el-collapse-item name="params" :title="t('workspace.design.sidebar.parameterList')">
            <div v-if="filteredFlatParams.length === 0" class="no-match">
              {{ t('workspace.design.sidebar.noMatch') }}
            </div>
            <div v-else class="param-tags">
              <el-tag
                v-for="p in filteredFlatParams"
                :key="p.id"
                :class="{ clickable: !readonly }"
                size="default"
                :type="dataTypeTagColor(p.dataType)"
                :effect="readonly ? 'plain' : 'light'"
                @click="handleInsertVariable(p)"
              >
                {{ p.parameterPath }}
              </el-tag>
            </div>
          </el-collapse-item>

          <!-- Loop Block Section -->
          <el-collapse-item name="loops" :title="t('workspace.design.sidebar.loopBlock')">
            <div v-if="filteredArrayParams.length === 0" class="no-match">
              {{ t('workspace.design.sidebar.noMatch') }}
            </div>
            <div v-else class="param-tags">
              <el-tag
                v-for="p in filteredArrayParams"
                :key="p.id"
                :class="{ clickable: !readonly }"
                size="default"
                type="warning"
                :effect="readonly ? 'plain' : 'light'"
                @click="handleInsertLoop(p)"
              >
                {{ p.name }}
              </el-tag>
            </div>
          </el-collapse-item>

          <!-- Aggregation Properties Section -->
          <el-collapse-item name="aggregations" :title="t('workspace.design.sidebar.aggregationProperties')">
            <div v-if="filteredAggregationGroups.length === 0" class="no-match">
              {{ t('workspace.design.sidebar.noMatch') }}
            </div>
            <div v-else>
              <div v-for="group in filteredAggregationGroups" :key="group.arrayName" class="aggregation-group">
                <div class="aggregation-group-label">{{ group.arrayName }}</div>
                <div class="param-tags">
                  <el-tag
                    v-for="tag in group.tags"
                    :key="tag.placeholderPath"
                    :class="{ clickable: !readonly }"
                    size="default"
                    type="primary"
                    :effect="readonly ? 'plain' : 'light'"
                    @click="handleInsertAggregation(tag.placeholderPath)"
                  >
                    {{ tag.name }}
                  </el-tag>
                </div>
              </div>
            </div>
          </el-collapse-item>

          <!-- Condition Block Section -->
          <el-collapse-item name="conditions" :title="t('workspace.design.sidebar.conditionBlock')">
            <div v-if="!readonly" class="condition-input">
              <el-input
                v-model="conditionExpr"
                :placeholder="t('workspace.design.sidebar.conditionExpr')"
                size="small"
                @keyup.enter="handleAddCondition"
              />
              <el-button size="small" type="primary" :disabled="!conditionExpr.trim()" @click="handleAddCondition">
                {{ t('workspace.design.sidebar.addCondition') }}
              </el-button>
            </div>
            <div v-if="conditionTags.length === 0 && readonly" class="no-match">
              {{ t('workspace.design.sidebar.noMatch') }}
            </div>
            <div v-else class="param-tags">
              <el-tag
                v-for="(expr, idx) in conditionTags"
                :key="idx"
                :class="{ clickable: !readonly }"
                size="default"
                type="success"
                :effect="readonly ? 'plain' : 'light'"
                closable
                @click="handleInsertCondition(expr)"
                @close="conditionTags.splice(idx, 1)"
              >
                {{ expr }}
              </el-tag>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>

      <!-- Inline Parameter Creator -->
      <div v-if="!readonly" class="inline-creator">
        <el-button v-if="!showCreator" size="small" text type="primary" @click="showCreator = true">
          {{ t('workspace.design.sidebar.inlineCreate') }}
        </el-button>
        <div v-else class="creator-form">
          <el-input
            v-model="newParamName"
            :placeholder="t('parameter.name')"
            size="small"
          />
          <el-select v-model="newParamType" size="small" style="width: 120px">
            <el-option v-for="dt in allDataTypes" :key="dt" :label="dt" :value="dt" />
          </el-select>
          <el-button size="small" type="primary" :loading="creating" :disabled="!newParamName.trim()" @click="handleCreateParameter">
            {{ t('common.confirm') }}
          </el-button>
          <el-button size="small" @click="showCreator = false">
            {{ t('common.cancel') }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { List, ArrowRight, Search } from '@element-plus/icons-vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { createParameter } from '@/api/parameters'
import type { ParameterDTO, DataType } from '@/types/parameter'

const props = defineProps<{
  collapsed: boolean
  readonly: boolean
}>()

const emit = defineEmits<{
  'update:collapsed': [value: boolean]
  'insert-variable': [paramPath: string]
  'insert-loop': [arrayName: string]
  'insert-condition': [expr: string]
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

const searchText = ref('')
const activeTypeFilter = ref<DataType | null>(null)
const activeSections = ref(['params', 'loops', 'aggregations', 'conditions'])
const conditionExpr = ref('')
const conditionTags = ref<string[]>([])

// Inline creator state
const showCreator = ref(false)
const newParamName = ref('')
const newParamType = ref<DataType>('STRING')
const creating = ref(false)

const allDataTypes: DataType[] = ['STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'ARRAY', 'OBJECT']

// Flatten parameter tree into a flat list with parameterPath
function flattenParams(params: ParameterDTO[]): ParameterDTO[] {
  const result: ParameterDTO[] = []
  function walk(list: ParameterDTO[]) {
    for (const p of list) {
      result.push(p)
      if (p.children?.length) walk(p.children)
    }
  }
  walk(params)
  return result
}

const flatParams = computed(() => flattenParams(store.parameters))

const filteredFlatParams = computed(() => {
  let list = flatParams.value
  const q = searchText.value.trim().toLowerCase()
  if (q) {
    list = list.filter(p => p.parameterPath.toLowerCase().includes(q) || p.name.toLowerCase().includes(q))
  }
  if (activeTypeFilter.value) {
    list = list.filter(p => p.dataType === activeTypeFilter.value)
  }
  return list
})

const filteredArrayParams = computed(() => {
  let list = flatParams.value.filter(p => p.dataType === 'ARRAY')
  const q = searchText.value.trim().toLowerCase()
  if (q) {
    list = list.filter(p => p.parameterPath.toLowerCase().includes(q) || p.name.toLowerCase().includes(q))
  }
  return list
})

// ── Aggregation properties (client-side generated) ──

interface AggregationTag {
  name: string
  placeholderPath: string
}

interface AggregationGroup {
  arrayName: string
  tags: AggregationTag[]
}

const aggregationGroups = computed<AggregationGroup[]>(() => {
  const arrayParams = flatParams.value.filter(p => p.dataType === 'ARRAY')
  return arrayParams.map(arr => {
    const tags: AggregationTag[] = []
    const path = arr.parameterPath

    // Always: $count, $first, $last
    tags.push({ name: '$count', placeholderPath: `${path}.$count` })
    tags.push({ name: '$first', placeholderPath: `${path}.$first` })
    tags.push({ name: '$last', placeholderPath: `${path}.$last` })

    // Generate per-child aggregation tags
    const children = arr.children ?? []
    for (const child of children) {
      if (child.dataType === 'NUMBER') {
        tags.push({ name: `$sum_${child.name}`, placeholderPath: `${path}.$sum_${child.name}` })
        tags.push({ name: `$avg_${child.name}`, placeholderPath: `${path}.$avg_${child.name}` })
        tags.push({ name: `$min_${child.name}`, placeholderPath: `${path}.$min_${child.name}` })
        tags.push({ name: `$max_${child.name}`, placeholderPath: `${path}.$max_${child.name}` })
      } else if (child.dataType === 'STRING') {
        tags.push({ name: `$join_${child.name}`, placeholderPath: `${path}.$join_${child.name}` })
      }
    }

    return { arrayName: arr.name, tags }
  })
})

const filteredAggregationGroups = computed<AggregationGroup[]>(() => {
  const q = searchText.value.trim().toLowerCase()
  if (!q) return aggregationGroups.value
  return aggregationGroups.value
    .map(group => ({
      ...group,
      tags: group.tags.filter(
        tag => tag.name.toLowerCase().includes(q) || tag.placeholderPath.toLowerCase().includes(q),
      ),
    }))
    .filter(group => group.tags.length > 0)
})

function toggleTypeFilter(dt: DataType) {
  activeTypeFilter.value = activeTypeFilter.value === dt ? null : dt
}

function dataTypeTagColor(dt: DataType): 'success' | 'warning' | 'danger' | 'info' | undefined {
  switch (dt) {
    case 'STRING': return undefined
    case 'NUMBER': return 'success'
    case 'DATE': return 'warning'
    case 'BOOLEAN': return 'danger'
    case 'ARRAY': return 'warning'
    case 'OBJECT': return 'info'
    default: return 'info'
  }
}

function handleInsertVariable(p: ParameterDTO) {
  if (props.readonly) return
  emit('insert-variable', p.parameterPath)
}

function handleInsertLoop(p: ParameterDTO) {
  if (props.readonly) return
  emit('insert-loop', p.name)
}

function handleInsertAggregation(placeholderPath: string) {
  if (props.readonly) return
  emit('insert-variable', placeholderPath)
}

function handleInsertCondition(expr: string) {
  if (props.readonly) return
  emit('insert-condition', expr)
}

function handleAddCondition() {
  const expr = conditionExpr.value.trim()
  if (!expr) return
  conditionTags.value.push(expr)
  conditionExpr.value = ''
}

async function handleCreateParameter() {
  const name = newParamName.value.trim()
  if (!name) return
  creating.value = true
  try {
    await createParameter(store.templateId, { name, dataType: newParamType.value })
    await store.refreshParameters()
    newParamName.value = ''
    newParamType.value = 'STRING'
    showCreator.value = false
    ElMessage.success(t('message.createSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.parameter-sidebar {
  height: 100%;
  border-left: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
}
.parameter-sidebar.is-collapsed {
  width: 40px;
  min-width: 40px;
}
.sidebar-collapsed {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 16px;
  gap: 8px;
  cursor: pointer;
  height: 100%;
}
.sidebar-collapsed:hover {
  background: var(--el-fill-color-light);
}
.collapsed-label {
  writing-mode: vertical-rl;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.sidebar-expanded {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 280px;
  max-width: 360px;
}
.sidebar-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 12px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.sidebar-header .el-input {
  flex: 1;
}
.type-filter {
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.filter-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
  display: block;
}
.filter-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}
.filter-tag {
  cursor: pointer;
}
.sidebar-content {
  flex: 1;
  overflow-y: auto;
  padding: 0 4px;
}
.param-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 4px 0;
}
.param-tags .el-tag.clickable {
  cursor: pointer;
}
.param-tags .el-tag.clickable:hover {
  opacity: 0.8;
  transform: scale(1.02);
}
.no-match {
  padding: 12px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.condition-input {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.condition-input .el-input {
  flex: 1;
}
.inline-creator {
  padding: 8px 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.aggregation-group {
  margin-bottom: 8px;
}
.aggregation-group-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
  font-weight: 500;
}
.creator-form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.creator-form .el-input {
  flex: 1;
  min-width: 100px;
}
</style>
