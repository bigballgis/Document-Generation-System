<template>
  <el-drawer
    :model-value="visible"
    :title="t('workspace.design.parameterOverview')"
    direction="rtl"
    size="420px"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-if="!editingParam" class="view-toggle">
      <el-radio-group v-model="viewMode" size="small">
        <el-radio-button value="tree">{{ t('workspace.design.overview.treeView') }}</el-radio-button>
        <el-radio-button value="schema">{{ t('workspace.design.overview.jsonSchemaView') }}</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="editingParam" class="detail-view">
      <div class="detail-back" @click="editingParam = null">
        <el-icon><ArrowLeft /></el-icon>
        <span>{{ t('common.back') }}</span>
      </div>

      <el-form label-position="top" class="detail-form">
        <el-form-item :label="t('parameter.name')">
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item :label="t('parameter.dataType')">
          <el-select v-model="editForm.dataType" style="width: 100%">
            <el-option v-for="dt in leafDataTypes" :key="dt" :label="dt" :value="dt" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('parameter.required')">
          <el-switch v-model="editForm.required" />
        </el-form-item>
        <el-form-item :label="t('parameter.description')">
          <el-input v-model="editForm.description" type="textarea" :rows="2" />
        </el-form-item>

        <el-divider content-position="left">{{ t('parameter.validationRules') }}</el-divider>
        <ValidationRulesPopover
          :visible="rulesPopoverVisible"
          :data-type="editForm.dataType"
          :rules="editForm.validationRules"
          @update:visible="rulesPopoverVisible = $event"
          @save="handleRulesSave"
        >
          <div class="rules-trigger" @click="rulesPopoverVisible = true">
            <template v-if="ruleCount > 0">
              <el-tag v-for="tag in ruleTags" :key="tag.key" size="small" :type="tag.type" class="rule-tag">
                {{ tag.label }}
              </el-tag>
            </template>
            <el-button v-else size="small" type="primary" plain>
              <el-icon><Plus /></el-icon>
              {{ t('parameter.addRule') }}
            </el-button>
          </div>
        </ValidationRulesPopover>

        <div class="detail-actions">
          <el-button @click="editingParam = null">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="saveDetail">{{ t('common.save') }}</el-button>
        </div>
      </el-form>
    </div>

    <div v-else-if="viewMode === 'tree'" class="tree-view">
      <el-breadcrumb v-if="breadcrumb.length > 1" separator="/" class="overview-breadcrumb">
        <el-breadcrumb-item
          v-for="(item, idx) in breadcrumb"
          :key="idx"
          @click="navigateToBreadcrumb(idx)"
        >
          <span class="bc-item" :class="{ clickable: idx < breadcrumb.length - 1 }">
            {{ item.name }}
          </span>
        </el-breadcrumb-item>
      </el-breadcrumb>

      <div v-if="currentLevelParams.length > 0" class="param-list">
        <div
          v-for="p in currentLevelParams"
          :key="p.id"
          class="param-row"
          @click="handleParamClick(p)"
        >
          <span class="param-name">{{ p.name }}</span>
          <div class="param-meta">
            <el-tag size="small" :type="dataTypeTagType(p.dataType)">{{ p.dataType }}</el-tag>
            <el-tag v-if="p.required" size="small" type="danger">{{ t('parameter.required') }}</el-tag>
            <el-icon class="nav-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
      <el-empty v-else :description="t('parameter.empty')" />
    </div>

    <div v-else class="schema-view">
      <pre class="schema-code">{{ jsonSchemaText }}</pre>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowRight, ArrowLeft, Plus } from '@element-plus/icons-vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { updateParameter } from '@/api/parameters'
import type { ParameterDTO, DataType, ValidationRules } from '@/types/parameter'
import ValidationRulesPopover from './ValidationRulesPopover.vue'

defineProps<{ visible: boolean }>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'navigate-to-param': [paramId: number]
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

const viewMode = ref<'tree' | 'schema'>('tree')
const leafDataTypes: DataType[] = ['STRING', 'NUMBER', 'DATE', 'BOOLEAN']

interface BreadcrumbItem { id: number | null; name: string }
const breadcrumb = ref<BreadcrumbItem[]>([{ id: null, name: 'Root' }])

const currentLevelParams = computed(() => {
  const last = breadcrumb.value[breadcrumb.value.length - 1]
  if (last.id === null) return store.parameters.filter(p => p.parentId === null)
  const parent = findById(store.parameters, last.id)
  return parent?.children ?? []
})

function findById(params: ParameterDTO[], id: number): ParameterDTO | null {
  for (const p of params) {
    if (p.id === id) return p
    if (p.children?.length) { const f = findById(p.children, id); if (f) return f }
  }
  return null
}

function isNavigable(p: ParameterDTO): boolean {
  return p.dataType === 'ARRAY' || p.dataType === 'OBJECT'
}

function handleParamClick(p: ParameterDTO) {
  if (isNavigable(p)) {
    breadcrumb.value.push({ id: p.id, name: p.name })
  } else {
    openDetail(p)
  }
}

function navigateToBreadcrumb(index: number) {
  if (index >= breadcrumb.value.length - 1) return
  breadcrumb.value = breadcrumb.value.slice(0, index + 1)
}

const editingParam = ref<ParameterDTO | null>(null)
const saving = ref(false)
const rulesPopoverVisible = ref(false)
const editForm = reactive({
  name: '',
  dataType: 'STRING' as DataType,
  required: false,
  description: '',
  validationRules: null as ValidationRules | null,
})

function openDetail(p: ParameterDTO) {
  editingParam.value = p
  editForm.name = p.name
  editForm.dataType = p.dataType as DataType
  editForm.required = p.required
  editForm.description = p.description ?? ''
  editForm.validationRules = p.validationRules ? { ...p.validationRules } : null
}

function handleRulesSave(rules: ValidationRules) {
  editForm.validationRules = Object.keys(rules).length > 0 ? rules : null
  rulesPopoverVisible.value = false
}

interface RuleTag { key: string; label: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info' }

const ruleCount = computed(() => {
  if (!editForm.validationRules) return 0
  return Object.keys(editForm.validationRules).filter(k => k !== 'custom_message' && (editForm.validationRules as any)[k] != null).length
})

const ruleTags = computed<RuleTag[]>(() => {
  const r = editForm.validationRules
  if (!r) return []
  const tags: RuleTag[] = []
  if (r.not_blank) tags.push({ key: 'not_blank', label: t('parameter.validation.notBlank'), type: 'danger' })
  if (r.min_length != null) tags.push({ key: 'min_length', label: `${t('parameter.validation.minLength')}: ${r.min_length}`, type: 'warning' })
  if (r.max_length != null) tags.push({ key: 'max_length', label: `${t('parameter.validation.maxLength')}: ${r.max_length}`, type: 'warning' })
  if (r.min != null) tags.push({ key: 'min', label: `${t('parameter.validation.min')}: ${r.min}`, type: 'warning' })
  if (r.max != null) tags.push({ key: 'max', label: `${t('parameter.validation.max')}: ${r.max}`, type: 'warning' })
  if (r.pattern) tags.push({ key: 'pattern', label: `${t('parameter.validation.pattern')}`, type: 'info' })
  if (r.enum_values?.length) tags.push({ key: 'enum', label: `${t('parameter.validation.enumValues')}(${r.enum_values.length})`, type: 'info' })
  return tags
})

async function saveDetail() {
  if (!editingParam.value) return
  saving.value = true
  try {
    await updateParameter(editingParam.value.id, {
      name: editForm.name,
      dataType: editForm.dataType,
      required: editForm.required,
      description: editForm.description || undefined,
      validationRules: editForm.validationRules ?? undefined,
      version: editingParam.value.version,
    })
    await store.refreshParameters()
    ElMessage.success(t('message.updateSuccess'))
    editingParam.value = null
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.saveFailed'))
  } finally {
    saving.value = false
  }
}

function dataTypeTagType(dt: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' | undefined {
  switch (dt) {
    case 'STRING': return undefined; case 'NUMBER': return 'success'; case 'DATE': return 'warning'
    case 'BOOLEAN': return 'danger'; case 'ARRAY': return 'warning'; case 'OBJECT': return 'info'
    default: return 'info'
  }
}

function toJsonSchema(params: ParameterDTO[]): Record<string, unknown> {
  const properties: Record<string, unknown> = {}
  const required: string[] = []
  for (const p of params) {
    if (p.required) required.push(p.name)
    if (p.dataType === 'OBJECT' && p.children?.length) properties[p.name] = { type: 'object', ...toJsonSchema(p.children) }
    else if (p.dataType === 'ARRAY' && p.children?.length) properties[p.name] = { type: 'array', items: { type: 'object', ...toJsonSchema(p.children) } }
    else properties[p.name] = { type: p.dataType.toLowerCase() }
  }
  const schema: Record<string, unknown> = { properties }
  if (required.length) schema.required = required
  return schema
}

const jsonSchemaText = computed(() => JSON.stringify({ type: 'object', ...toJsonSchema(store.parameters) }, null, 2))
</script>

<style scoped>
.view-toggle { margin-bottom: 16px; }

/* ── Detail View ── */
.detail-back {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: var(--el-color-primary);
  font-size: 14px;
  margin-bottom: 16px;
}
.detail-back:hover { text-decoration: underline; }

.detail-form { padding: 0 4px; }

.rules-trigger {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  cursor: pointer;
  padding: 4px 0;
}
.rule-tag { cursor: pointer; }

.detail-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

/* ── Tree View ── */
.overview-breadcrumb {
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.bc-item { font-size: 13px; }
.bc-item.clickable { cursor: pointer; color: var(--el-color-primary); }
.bc-item.clickable:hover { text-decoration: underline; }

.tree-view { overflow-y: auto; }

.param-list { display: flex; flex-direction: column; }

.param-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.15s;
}
.param-row:hover { background: var(--el-fill-color-light); }
.param-row:last-child { border-bottom: none; }

.param-name { font-size: 14px; font-weight: 500; color: var(--el-text-color-primary); }
.param-meta { display: flex; align-items: center; gap: 6px; }
.nav-arrow { color: var(--el-text-color-placeholder); font-size: 14px; }

/* ── Schema View ── */
.schema-view { overflow: auto; }
.schema-code {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.6;
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
</style>


