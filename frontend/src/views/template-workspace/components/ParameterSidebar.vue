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

      <!-- Scrollable content area -->
      <div class="sidebar-content">
        <!-- Parameter list -->
        <div v-if="filteredParams.length === 0" class="no-match">
          {{ t('workspace.design.sidebar.noMatch') }}
        </div>
        <div v-else class="param-list">
          <el-tooltip
            v-for="p in filteredParams"
            :key="p.id"
            :content="isLeaf(p) ? `${t('workspace.design.sidebar.clickToInsert')} {${p.parameterPath}}` : ''"
            :disabled="!isLeaf(p) || readonly"
            placement="left"
            :show-after="600"
          >
            <div
              class="param-item"
              :class="{ 'is-leaf': isLeaf(p), 'is-navigable': isNavigable(p) }"
              @click="handleParamClick(p)"
            >
              <div class="param-info">
                <span class="param-name">{{ p.name }}</span>
                <el-tag size="small" :type="dataTypeTagColor(p.dataType)">{{ p.dataType }}</el-tag>
              </div>
              <div class="param-actions">
                <el-tooltip v-if="p.dataType === 'ARRAY' && !readonly" :content="t('workspace.design.sidebar.insertLoop')" placement="top">
                  <el-button size="small" circle type="warning" plain class="action-btn" @click.stop="handleQuickLoop(p)">
                    <el-icon :size="12"><RefreshRight /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-icon v-if="isLeaf(p) && !readonly" class="insert-icon"><Plus /></el-icon>
                <el-icon v-if="isNavigable(p)" class="nav-arrow"><ArrowRight /></el-icon>
              </div>
            </div>
          </el-tooltip>
        </div>

        <!-- Aggregation tags (inside ARRAY context) -->
        <div v-if="currentArrayAggregations.length > 0" class="section-block">
          <div class="section-label" style="padding: 8px 12px 0">{{ t('workspace.design.sidebar.aggregationProperties') }}</div>
          <div class="agg-tags">
            <el-tag
              v-for="agg in currentArrayAggregations"
              :key="agg.path"
              size="small"
              :type="agg.group === 'built-in' ? 'info' : agg.group === 'number' ? 'success' : 'warning'"
              :class="{ clickable: !readonly }"
              @click="!readonly && emit('insert-variable', agg.path)"
            >{{ agg.name }}</el-tag>
          </div>
        </div>

        <!-- ═══ Collapsible: Expression Filters ═══ -->
        <div v-if="!readonly" class="section-block">
          <div class="section-header" @click="filtersExpanded = !filtersExpanded">
            <el-icon class="section-arrow" :class="{ expanded: filtersExpanded }"><ArrowRight /></el-icon>
            <span class="section-label" style="margin-bottom: 0">{{ t('workspace.design.sidebar.expressionFilters') }}</span>
          </div>
          <el-collapse-transition>
            <div v-show="filtersExpanded" class="section-body">
              <div class="filter-group">
                <div class="filter-group-label">{{ t('workspace.design.sidebar.filterGroupString') }}</div>
                <div class="filter-tags">
                  <el-tag v-for="f in stringFilters" :key="f.syntax" size="small" class="filter-tag" @click="copyFilter(f.syntax)">
                    <el-tooltip :content="f.example" placement="top" :show-after="300">
                      <span>{{ f.label }}</span>
                    </el-tooltip>
                  </el-tag>
                </div>
              </div>
              <div class="filter-group">
                <div class="filter-group-label">{{ t('workspace.design.sidebar.filterGroupNumber') }}</div>
                <div class="filter-tags">
                  <el-tag v-for="f in numberFilters" :key="f.syntax" size="small" type="success" class="filter-tag" @click="copyFilter(f.syntax)">
                    <el-tooltip :content="f.example" placement="top" :show-after="300">
                      <span>{{ f.label }}</span>
                    </el-tooltip>
                  </el-tag>
                </div>
              </div>
              <div class="filter-group">
                <div class="filter-group-label">{{ t('workspace.design.sidebar.filterGroupArray') }}</div>
                <div class="filter-tags">
                  <el-tag v-for="f in arrayFilters" :key="f.syntax" size="small" type="warning" class="filter-tag" @click="copyFilter(f.syntax)">
                    <el-tooltip :content="f.example" placement="top" :show-after="300">
                      <span>{{ f.label }}</span>
                    </el-tooltip>
                  </el-tag>
                </div>
              </div>
              <div class="filter-group">
                <div class="filter-group-label">{{ t('workspace.design.sidebar.filterGroupDate') }}</div>
                <div class="filter-tags">
                  <el-tag v-for="f in dateFilters" :key="f.syntax" size="small" type="danger" class="filter-tag" @click="copyFilter(f.syntax)">
                    <el-tooltip :content="f.example" placement="top" :show-after="300">
                      <span>{{ f.label }}</span>
                    </el-tooltip>
                  </el-tag>
                </div>
              </div>
              <div class="filter-hint">{{ t('workspace.design.sidebar.filterUsageHint') }}</div>
            </div>
          </el-collapse-transition>
        </div>

        <!-- ═══ Collapsible: Condition Block ═══ -->
        <div v-if="!readonly" class="section-block">
          <div class="section-header" @click="conditionExpanded = !conditionExpanded">
            <el-icon class="section-arrow" :class="{ expanded: conditionExpanded }"><ArrowRight /></el-icon>
            <span class="section-label" style="margin-bottom: 0">{{ t('workspace.design.sidebar.conditionBlock') }}</span>
          </div>
          <el-collapse-transition>
            <div v-show="conditionExpanded" class="section-body">
              <div class="condition-mode-toggle">
                <el-radio-group v-model="conditionMode" size="small">
                  <el-radio-button value="simple">{{ t('editor.conditionSimpleMode') }}</el-radio-button>
                  <el-radio-button value="advanced">{{ t('editor.conditionAdvancedMode') }}</el-radio-button>
                </el-radio-group>
              </div>
              <template v-if="conditionMode === 'simple'">
                <el-select
                  v-model="conditionVar" filterable allow-create default-first-option
                  size="small" style="margin-top: 6px; width: 100%"
                  :placeholder="t('workspace.design.sidebar.selectConditionVar')"
                >
                  <el-option v-for="opt in currentLevelConditionOptions" :key="opt.path" :label="opt.path" :value="opt.path">
                    <span>{{ opt.path }}</span>
                    <el-tag size="small" :type="dataTypeTagColor(opt.dataType as DataType)" style="margin-left: 6px; font-size: 10px">{{ opt.dataType }}</el-tag>
                  </el-option>
                </el-select>
                <el-select v-model="conditionOp" size="small" style="margin-top: 6px; width: 100%">
                  <el-option v-for="op in conditionOperators" :key="op.value" :label="op.label" :value="op.value" />
                </el-select>
                <el-input
                  v-if="conditionOp !== 'truthy' && conditionOp !== 'falsy'"
                  v-model="conditionValue"
                  :placeholder="t('editor.conditionValuePlaceholder')"
                  size="small" style="margin-top: 6px"
                  @keyup.enter="handleAddCondition"
                />
              </template>
              <template v-else>
                <el-input
                  v-model="conditionExpr"
                  :placeholder="t('workspace.design.sidebar.conditionExpr')"
                  size="small" style="margin-top: 6px"
                  @keyup.enter="handleAddCondition"
                />
              </template>
              <el-button
                size="small" type="primary" :disabled="!computedConditionExpr"
                style="margin-top: 6px; width: 100%"
                @click="handleAddCondition"
              >
                {{ t('workspace.design.sidebar.addCondition') }}
              </el-button>
              <div v-if="computedConditionExpr" class="tag-preview">
                <code>{{"{"}}#if {{ computedConditionExpr }}{{"}"}}...{{"{"}}/if{{"}"}}</code>
              </div>
            </div>
          </el-collapse-transition>
        </div>
      </div>

      <!-- Inline creator (pinned to bottom) -->
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

      <!-- Save draft (pinned to bottom). Template review is submitted from Test/Approval, not here. -->
      <div v-if="!readonly && store.isDraft" class="sidebar-actions">
        <el-button type="primary" :loading="saving" class="sidebar-save-btn" @click="handleSave">
          {{ t('workspace.design.sidebar.save') }}
        </el-button>
        <p class="sidebar-workflow-hint">{{ t('workspace.design.sidebar.reviewWorkflowHint') }}</p>
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
import { updateAssemblyConfig } from '@/api/composite-templates'
import type { ParameterDTO, DataType } from '@/types/parameter'

const props = defineProps<{ collapsed: boolean; readonly: boolean }>()

const emit = defineEmits<{
  'update:collapsed': [value: boolean]
  'insert-variable': [paramPath: string]
  'insert-loop': [loopText: string]
  'insert-condition': [expr: string]
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

// ── Shared helpers ──
function findParamById(params: ParameterDTO[], id: number): ParameterDTO | null {
  for (const p of params) {
    if (p.id === id) return p
    if (p.children?.length) { const f = findParamById(p.children, id); if (f) return f }
  }
  return null
}

function isNavigable(p: ParameterDTO): boolean { return p.dataType === 'ARRAY' || p.dataType === 'OBJECT' }
function isLeaf(p: ParameterDTO): boolean { return !isNavigable(p) }

function dataTypeTagColor(dt: DataType): 'success' | 'warning' | 'danger' | 'info' | undefined {
  switch (dt) {
    case 'NUMBER': return 'success'; case 'DATE': return 'warning'; case 'BOOLEAN': return 'danger'
    case 'ARRAY': return 'warning'; case 'OBJECT': return 'info'; default: return undefined
  }
}

// ── Search ──
const searchText = ref('')

// ── Breadcrumb navigation ──
interface BreadcrumbItem { id: number | null; name: string }
const breadcrumb = ref<BreadcrumbItem[]>([{ id: null, name: 'Root' }])

const currentLevelParams = computed(() => {
  const last = breadcrumb.value[breadcrumb.value.length - 1]
  if (last.id === null) return store.parameters.filter(p => p.parentId === null)
  const parent = findParamById(store.parameters, last.id)
  return parent?.children ?? []
})

const filteredParams = computed(() => {
  const q = searchText.value.trim().toLowerCase()
  if (!q) return currentLevelParams.value
  return currentLevelParams.value.filter(p => p.name.toLowerCase().includes(q))
})

function handleParamClick(p: ParameterDTO) {
  if (isNavigable(p)) {
    breadcrumb.value.push({ id: p.id, name: p.name })
  } else if (!props.readonly) {
    emit('insert-variable', p.parameterPath)
  }
}

function navigateToBreadcrumb(index: number) {
  if (index >= breadcrumb.value.length - 1) return
  breadcrumb.value = breadcrumb.value.slice(0, index + 1)
}

function handleQuickLoop(p: ParameterDTO) {
  emit('insert-loop', `{#${p.name}}\n\n{/${p.name}}`)
}

// ── Aggregation tags ──
const currentArrayAggregations = computed(() => {
  const last = breadcrumb.value[breadcrumb.value.length - 1]
  if (last.id === null) return []
  const parent = findParamById(store.parameters, last.id)
  if (!parent || parent.dataType !== 'ARRAY') return []
  const path = parent.parameterPath || parent.name
  const tags: { name: string; path: string; group?: string }[] = [
    { name: '$count', path: `${path}.$count`, group: 'built-in' },
    { name: '$first', path: `${path}.$first`, group: 'built-in' },
    { name: '$last', path: `${path}.$last`, group: 'built-in' },
  ]
  for (const child of parent.children ?? []) {
    if (child.dataType === 'NUMBER') {
      tags.push({ name: `$sum_${child.name}`, path: `${path}.$sum_${child.name}`, group: 'number' })
      tags.push({ name: `$avg_${child.name}`, path: `${path}.$avg_${child.name}`, group: 'number' })
      tags.push({ name: `$min_${child.name}`, path: `${path}.$min_${child.name}`, group: 'number' })
      tags.push({ name: `$max_${child.name}`, path: `${path}.$max_${child.name}`, group: 'number' })
    } else if (child.dataType === 'STRING') {
      tags.push({ name: `$join_${child.name}`, path: `${path}.$join_${child.name}`, group: 'string' })
      tags.push({ name: `$join(;)_${child.name}`, path: `${path}.$join(;)_${child.name}`, group: 'string' })
      tags.push({ name: `$join(、)_${child.name}`, path: `${path}.$join(、)_${child.name}`, group: 'string' })
      tags.push({ name: `$join(|)_${child.name}`, path: `${path}.$join(|)_${child.name}`, group: 'string' })
      tags.push({ name: `$join(/)_${child.name}`, path: `${path}.$join(/)_${child.name}`, group: 'string' })
      tags.push({ name: `$join(\\n)_${child.name}`, path: `${path}.$join(\\n)_${child.name}`, group: 'string' })
    }
  }
  return tags
})

// ── Condition variable options (level-aware) ──
/** Only show params at the current breadcrumb level + aggregation virtuals if inside ARRAY */
const currentLevelConditionOptions = computed(() => {
  const result: { path: string; dataType: DataType | string }[] = []
  const last = breadcrumb.value[breadcrumb.value.length - 1]

  // Get the current parent param (null = root)
  const parentParam = last.id !== null ? findParamById(store.parameters, last.id) : null
  const children = parentParam ? (parentParam.children ?? []) : store.parameters.filter(p => p.parentId === null)

  // Add all children at this level
  for (const p of children) {
    result.push({ path: p.parameterPath || p.name, dataType: p.dataType })
  }

  // If inside an ARRAY, add aggregation virtual properties
  if (parentParam?.dataType === 'ARRAY') {
    const parentPath = parentParam.parameterPath || parentParam.name
    result.push({ path: `${parentPath}.$count`, dataType: 'NUMBER' })
    result.push({ path: `${parentPath}.length`, dataType: 'NUMBER' })
    result.push({ path: `${parentPath}.$first`, dataType: 'OBJECT' })
    result.push({ path: `${parentPath}.$last`, dataType: 'OBJECT' })
    for (const child of parentParam.children ?? []) {
      if (child.dataType === 'NUMBER') {
        result.push({ path: `${parentPath}.$sum_${child.name}`, dataType: 'NUMBER' })
        result.push({ path: `${parentPath}.$avg_${child.name}`, dataType: 'NUMBER' })
        result.push({ path: `${parentPath}.$min_${child.name}`, dataType: 'NUMBER' })
        result.push({ path: `${parentPath}.$max_${child.name}`, dataType: 'NUMBER' })
      } else if (child.dataType === 'STRING') {
        result.push({ path: `${parentPath}.$join_${child.name}`, dataType: 'STRING' })
      }
    }
  }

  return result
})

// ── Condition block ──
const conditionExpanded = ref(true)
const filtersExpanded = ref(false)
const conditionMode = ref<'simple' | 'advanced'>('simple')
const conditionVar = ref('')
const conditionOp = ref('truthy')
const conditionValue = ref('')
const conditionExpr = ref('')

const conditionOperators = computed(() => [
  { value: 'truthy', label: t('editor.conditionOpTruthy') },
  { value: 'falsy', label: t('editor.conditionOpFalsy') },
  { value: '===', label: `=== (${t('editor.conditionOpEqual')})` },
  { value: '!==', label: `!== (${t('editor.conditionOpNotEqual')})` },
  { value: '>', label: `> (${t('editor.conditionOpGt')})` },
  { value: '>=', label: `>= (${t('editor.conditionOpGte')})` },
  { value: '<', label: `< (${t('editor.conditionOpLt')})` },
  { value: '<=', label: `<= (${t('editor.conditionOpLte')})` },
])

const computedConditionExpr = computed(() => {
  if (conditionMode.value === 'advanced') return conditionExpr.value.trim()
  const v = conditionVar.value.trim()
  if (!v) return ''
  if (conditionOp.value === 'truthy') return v
  if (conditionOp.value === 'falsy') return `!${v}`
  const val = conditionValue.value.trim()
  if (!val) return ''
  return `${v} ${conditionOp.value} ${val}`
})

function handleAddCondition() {
  const expr = computedConditionExpr.value
  if (!expr) return
  emit('insert-condition', expr)
  conditionExpr.value = ''
  conditionVar.value = ''
  conditionOp.value = 'truthy'
  conditionValue.value = ''
}

// ── Expression filter definitions ──
const stringFilters = [
  { label: 'upper', syntax: '| upper', example: '{name | upper} → "JOHN"' },
  { label: 'lower', syntax: '| lower', example: '{name | lower} → "john"' },
  { label: 'trim', syntax: '| trim', example: '{name | trim}' },
  { label: 'default', syntax: "| default:'N/A'", example: "{name | default:'N/A'} → \"N/A\" if empty" },
  { label: 'replace', syntax: "| replace:' ':'-'", example: "{name | replace:' ':'-'} → \"John-Doe\"" },
  { label: 'substr', syntax: '| substr:0:10', example: '{text | substr:0:10} → first 10 chars' },
]
const numberFilters = [
  { label: 'toFixed', syntax: '| toFixed:2', example: '{price | toFixed:2} → "100.00"' },
  { label: 'round', syntax: '| round:1', example: '{score | round:1} → 3.1' },
  { label: 'currency', syntax: "| currency:'¥':2", example: "{price | currency:'¥':2} → \"¥100.00\"" },
  { label: 'percent', syntax: '| percent:1', example: '{rate | percent:1} → "85.5%"' },
  { label: 'abs', syntax: '| abs', example: '{diff | abs} → 42' },
]
const arrayFilters = [
  { label: 'joinBy', syntax: "| joinBy:'name':','", example: "{items | joinBy:'name':','} → \"A,B,C\"" },
  { label: 'sumBy', syntax: "| sumBy:'price'", example: "{items | sumBy:'price'} → 1150" },
  { label: 'avgBy', syntax: "| avgBy:'price'", example: "{items | avgBy:'price'} → 383" },
  { label: 'count', syntax: '| count', example: '{items | count} → 3' },
  { label: 'sortBy', syntax: "| sortBy:'name'", example: "{#items | sortBy:'name'}...{/}" },
  { label: 'where', syntax: "| where:'age > 18'", example: "{#users | where:'age > 18'}...{/}" },
  { label: 'first', syntax: '| first', example: '{items | first}' },
  { label: 'last', syntax: '| last', example: '{items | last}' },
  { label: 'reverse', syntax: '| reverse', example: '{#items | reverse}...{/}' },
  { label: 'unique', syntax: "| unique:'type'", example: "{#items | unique:'type'}...{/}" },
  { label: 'slice', syntax: '| slice:0:5', example: '{#items | slice:0:5}...{/} → first 5' },
  { label: 'groupBy', syntax: "| groupBy:'category'", example: "{#items | groupBy:'category'}...{/}" },
]
const dateFilters = [
  { label: 'dateFormat', syntax: "| dateFormat:'YYYY-MM-DD'", example: "{date | dateFormat:'YYYY-MM-DD'} → \"2025-01-15\"" },
  { label: 'dateFormat (CN)', syntax: "| dateFormat:'YYYY年MM月DD日'", example: "{date | dateFormat:'YYYY年MM月DD日'}" },
]

function copyFilter(syntax: string) {
  navigator.clipboard.writeText(syntax).then(() => {
    ElMessage.success({ message: `${syntax}`, duration: 1500 })
  }).catch(() => {
    ElMessage.info(syntax)
  })
}

// ── Inline creator ──
const allDataTypes: DataType[] = ['STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'ARRAY', 'OBJECT']
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

// ── Save draft (assembly) ──
const saving = ref(false)

async function handleSave() {
  saving.value = true
  try {
    if (store.assemblyConfig) {
      await updateAssemblyConfig(store.templateId, store.assemblyConfig)
    }
    await store.refreshTemplate()
    ElMessage.success(t('message.saveSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  } finally {
    saving.value = false
  }
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

/* ── Parameter list ── */
.param-list { display: flex; flex-direction: column; }
.param-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 7px 12px; border-bottom: 1px solid var(--el-border-color-extra-light);
  cursor: pointer; transition: background 0.15s;
}
.param-item:hover { background: var(--el-fill-color-light); }
.param-item.is-leaf:hover { background: var(--el-color-primary-light-9); }
.param-info { display: flex; align-items: center; gap: 8px; min-width: 0; }
.param-name { font-size: 13px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.param-actions { display: flex; align-items: center; gap: 4px; flex-shrink: 0; }
.action-btn { opacity: 0.6; transition: opacity 0.15s; }
.param-item:hover .action-btn { opacity: 1; }
.insert-icon { color: var(--el-color-primary); font-size: 12px; opacity: 0; transition: opacity 0.15s; }
.param-item:hover .insert-icon { opacity: 0.7; }
.nav-arrow { color: var(--el-text-color-placeholder); font-size: 12px; }
.no-match { padding: 20px; text-align: center; color: var(--el-text-color-secondary); font-size: 13px; }

/* ── Section blocks (aggregation / loop / condition) ── */
.section-block { border-top: 1px solid var(--el-border-color-lighter); }
.section-header {
  display: flex; align-items: center; gap: 4px; padding: 8px 12px;
  cursor: pointer; user-select: none; transition: background 0.1s;
}
.section-header:hover { background: var(--el-fill-color-light); }
.section-arrow { font-size: 12px; color: var(--el-text-color-secondary); transition: transform 0.2s; }
.section-arrow.expanded { transform: rotate(90deg); }
.section-label { font-size: 11px; font-weight: 600; color: var(--el-text-color-secondary); text-transform: uppercase; margin-bottom: 6px; }
.section-body { padding: 0 12px 10px; }

.agg-tags { display: flex; flex-wrap: wrap; gap: 4px; padding: 0 12px 8px; }
.agg-tags .el-tag.clickable { cursor: pointer; }
.agg-tags .el-tag.clickable:hover { opacity: 0.8; }

/* ── Condition ── */
.condition-mode-toggle { display: flex; justify-content: center; }

/* ── Filter tags ── */
.filter-group { margin-bottom: 8px; }
.filter-group-label { font-size: 11px; color: var(--el-text-color-secondary); margin-bottom: 4px; font-weight: 500; }
.filter-tags { display: flex; flex-wrap: wrap; gap: 4px; }
.filter-tag { cursor: pointer; }
.filter-tag:hover { opacity: 0.8; }
.filter-hint { font-size: 11px; color: var(--el-text-color-placeholder); margin-top: 4px; line-height: 1.4; }

/* ── Shared preview ── */
.tag-preview { margin-top: 6px; padding: 4px 8px; background: var(--el-fill-color-light); border-radius: 4px; font-size: 11px; }
.tag-preview code { color: var(--el-color-primary); word-break: break-all; }

/* ── Inline creator ── */
.inline-creator { padding: 6px 10px; border-top: 1px solid var(--el-border-color-lighter); flex-shrink: 0; }
.creator-form { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.creator-form .el-input { flex: 1; min-width: 80px; }

/* ── Save & Publish ── */
.sidebar-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
  background: var(--el-bg-color);
}
.sidebar-save-btn { width: 100%; }
.sidebar-workflow-hint {
  margin: 0;
  font-size: 11px;
  line-height: 1.35;
  color: var(--el-text-color-secondary);
}
</style>
