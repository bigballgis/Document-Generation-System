<template>
  <div ref="containerRef" class="parameter-table-view">
    <!-- Unified table: all parameter types in one table -->
    <el-table
      ref="tableRef"
      :data="sortedParameters"
      border
      size="default"
      class="field-table"
      row-key="id"
      :max-height="tableMaxHeight"
      :empty-text="t('parameter.empty')"
    >
      <!-- Drag handle column -->
      <el-table-column v-if="!readonly" width="40" align="center">
        <template #default>
          <el-icon class="drag-handle" style="cursor: grab"><Rank /></el-icon>
        </template>
      </el-table-column>

      <!-- Name column (inline editable) + navigate for ARRAY/OBJECT -->
      <el-table-column :label="t('workspace.design.table.fieldName')" min-width="140">
        <template #default="{ row }">
          <div class="name-cell">
            <div v-if="editingCell?.id === row.id && editingCell?.field === 'name'" class="inline-edit">
              <el-tooltip
                :visible="!!nameValidationError"
                :content="nameValidationError"
                placement="top"
                effect="dark"
              >
                <el-input
                  v-model="editingCell.value"
                  size="small"
                  autofocus
                  :class="{ 'is-invalid': !!nameValidationError }"
                  @input="nameValidationError = validateParamName(editingCell!.value, row.id)"
                  @blur="commitEdit(row)"
                  @keyup.enter="commitEdit(row)"
                  @keyup.escape="cancelEdit"
                />
              </el-tooltip>
            </div>
            <template v-else>
              <span
                class="editable-cell"
                :class="{ clickable: !readonly }"
                @click.stop="!readonly && startEdit(row, 'name', row.name)"
              >{{ row.name }}</span>
              <!-- Navigate arrow for ARRAY/OBJECT -->
              <el-button
                v-if="isNavigable(row)"
                link
                type="primary"
                size="small"
                class="navigate-btn"
                @click.stop="emit('navigate', row)"
              >
                <span class="children-count">{{ row.children?.length ?? 0 }} {{ t('workspace.design.table.fieldName') }}</span>
                <el-icon><ArrowRight /></el-icon>
              </el-button>
            </template>
          </div>
        </template>
      </el-table-column>

      <!-- Type column (inline editable via select, with i18n labels) -->
      <el-table-column :label="t('workspace.design.table.fieldType')" width="110">
        <template #default="{ row }">
          <el-select
            v-if="editingCell?.id === row.id && editingCell?.field === 'dataType'"
            v-model="editingCell.value"
            size="small"
            @change="commitEdit(row)"
            @visible-change="(v: boolean) => !v && cancelEdit()"
          >
            <el-option
              v-for="dt in allDataTypes"
              :key="dt"
              :label="dataTypeLabel(dt)"
              :value="dt"
            />
          </el-select>
          <el-tag
            v-else
            size="small"
            :type="dataTypeTagType(row.dataType)"
            class="editable-cell"
            :class="{ clickable: !readonly }"
            @click="!readonly && startEdit(row, 'dataType', row.dataType)"
          >{{ dataTypeLabel(row.dataType) }}</el-tag>
        </template>
      </el-table-column>

      <!-- Required column (switch) -->
      <el-table-column :label="t('workspace.design.table.required')" width="70" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.required"
            :disabled="readonly"
            size="small"
            @change="(val: string | number | boolean) => handleToggleRequired(row, val as boolean)"
          />
        </template>
      </el-table-column>

      <!-- Validation Rules column (popover editor, hidden for ARRAY/OBJECT) -->
      <el-table-column :label="t('parameter.validationRules')" min-width="200">
        <template #default="{ row }">
          <template v-if="!isNavigable(row)">
            <ValidationRulesPopover
              :visible="validationPopoverRowId === row.id"
              :data-type="row.dataType"
              :rules="row.validationRules"
              @update:visible="(val: boolean) => { validationPopoverRowId = val ? row.id : null }"
              @save="(rules) => handleValidationSave(row, rules)"
            >
              <div class="validation-tags" @click="!readonly && (validationPopoverRowId = row.id)">
                <template v-if="countRules(row.validationRules) > 0">
                  <el-tag
                    v-for="tag in getRuleTags(row.validationRules)"
                    :key="tag.key"
                    size="small"
                    :type="tag.type"
                    class="rule-tag"
                    disable-transitions
                  >
                    {{ tag.label }}
                  </el-tag>
                </template>
                <span v-else class="editable-cell add-rule-hint" :class="{ clickable: !readonly }">
                  <el-icon :size="14"><Plus /></el-icon>
                  {{ t('parameter.addRule') }}
                </span>
              </div>
            </ValidationRulesPopover>
          </template>
          <span v-else class="default-value">—</span>
        </template>
      </el-table-column>

      <!-- Actions column -->
      <el-table-column v-if="!readonly" :label="t('common.actions')" width="60" align="center">
        <template #default="{ row }">
          <el-popconfirm
            :title="getDeleteWarning(row)"
            @confirm="handleDelete(row)"
          >
            <template #reference>
              <el-button link type="danger" size="small">
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Rank, Delete, ArrowRight, Plus } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import {
  updateParameter, deleteParameter, batchUpdateParameters,
} from '@/api/parameters'
import type { ParameterDTO, DataType, ValidationRules } from '@/types/parameter'
import ValidationRulesPopover from './ValidationRulesPopover.vue'

const ALL_DATA_TYPES: DataType[] = ['STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'ARRAY', 'OBJECT']

const props = defineProps<{
  parameters: ParameterDTO[]
  parentId: number | null
  readonly: boolean
}>()

const emit = defineEmits<{
  (e: 'navigate', param: ParameterDTO): void
  (e: 'refresh'): void
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()
const tableRef = ref()
const containerRef = ref<HTMLElement>()
const tableMaxHeight = ref(400)

// ── Dynamic table height ──
let resizeObserver: ResizeObserver | null = null

function updateTableHeight() {
  if (!containerRef.value) return
  // Reserve no extra space — table fills the container
  const available = containerRef.value.clientHeight
  tableMaxHeight.value = Math.max(200, available)
}

onMounted(() => {
  nextTick(() => {
    updateTableHeight()
    initSortable()
    if (containerRef.value) {
      resizeObserver = new ResizeObserver(() => updateTableHeight())
      resizeObserver.observe(containerRef.value)
    }
  })
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
})

// ── All parameters in one sorted list ──
const sortedParameters = computed(() =>
  [...props.parameters].sort((a, b) => a.sortOrder - b.sortOrder),
)

const allDataTypes = ALL_DATA_TYPES

// ── Parameter name validation ──
// Must be a valid JSON key / docxtemplater placeholder: [a-zA-Z_][a-zA-Z0-9_]*
const PARAM_NAME_REGEX = /^[a-zA-Z_][a-zA-Z0-9_]*$/
const nameValidationError = ref('')

function validateParamName(value: string, excludeId?: number): string {
  if (!value || !value.trim()) {
    return t('workspace.design.table.nameRequired')
  }
  if (!PARAM_NAME_REGEX.test(value)) {
    return t('workspace.design.table.nameInvalid')
  }
  // Check duplicate among siblings
  const duplicate = props.parameters.find(
    p => p.name === value && p.id !== excludeId,
  )
  if (duplicate) {
    return t('workspace.design.table.nameDuplicate')
  }
  return ''
}

function isNavigable(row: ParameterDTO): boolean {
  return row.dataType === 'ARRAY' || row.dataType === 'OBJECT'
}

// ── i18n data type labels ──
function dataTypeLabel(dt: string): string {
  const key = `workspace.design.table.dataType.${dt}`
  const translated = t(key)
  // Fallback to raw value if no translation
  return translated === key ? dt : translated
}

function dataTypeTagType(dt: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' | undefined {
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

// ── Inline editing ──
interface EditingCell {
  id: number
  field: 'name' | 'dataType'
  value: string
}

const editingCell = ref<EditingCell | null>(null)

function startEdit(row: ParameterDTO, field: EditingCell['field'], value: string) {
  editingCell.value = { id: row.id, field, value }
}

function cancelEdit() {
  editingCell.value = null
  nameValidationError.value = ''
}

async function commitEdit(row: ParameterDTO) {
  if (!editingCell.value) return
  const { field, value } = editingCell.value

  // Block save if name validation fails
  if (field === 'name') {
    const error = validateParamName(value, row.id)
    if (error) {
      nameValidationError.value = error
      return
    }
  }

  const oldValue = (row as any)[field]
  editingCell.value = null
  nameValidationError.value = ''

  if (value === oldValue) return

  try {
    await updateParameter(row.id, { [field]: value, version: row.version })
    emit('refresh')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.saveFailed'))
  }
}

// ── Toggle required ──
async function handleToggleRequired(row: ParameterDTO, val: boolean) {
  try {
    await updateParameter(row.id, { required: val, version: row.version })
    emit('refresh')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.saveFailed'))
  }
}

// ── Delete ──
function getDeleteWarning(param: ParameterDTO): string {
  if (isNavigable(param) && param.children?.length > 0) {
    return t('workspace.design.table.deleteWarning')
  }
  return t('confirm.deleteMessage')
}

async function handleDelete(row: ParameterDTO) {
  try {
    await deleteParameter(row.id)
    emit('refresh')
    ElMessage.success(t('message.deleteSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.deleteFailed'))
  }
}

// ── Validation rules ──
const validationPopoverRowId = ref<number | null>(null)

interface RuleTag {
  key: string
  label: string
  type: 'primary' | 'success' | 'warning' | 'danger' | 'info'
}

function countRules(rules: ValidationRules | null): number {
  if (!rules) return 0
  return Object.keys(rules).filter(k => k !== 'custom_message' && (rules as any)[k] != null).length
}

function getRuleTags(rules: ValidationRules | null): RuleTag[] {
  if (!rules) return []
  const tags: RuleTag[] = []
  if (rules.not_blank) tags.push({ key: 'not_blank', label: t('parameter.validation.notBlank'), type: 'danger' })
  if (rules.min_length != null) tags.push({ key: 'min_length', label: `${t('parameter.validation.minLength')}: ${rules.min_length}`, type: 'warning' })
  if (rules.max_length != null) tags.push({ key: 'max_length', label: `${t('parameter.validation.maxLength')}: ${rules.max_length}`, type: 'warning' })
  if (rules.min != null) tags.push({ key: 'min', label: `${t('parameter.validation.min')}: ${rules.min}`, type: 'warning' })
  if (rules.max != null) tags.push({ key: 'max', label: `${t('parameter.validation.max')}: ${rules.max}`, type: 'warning' })
  if (rules.pattern) tags.push({ key: 'pattern', label: `${t('parameter.validation.pattern')}: ${rules.pattern}`, type: 'info' })
  if (rules.enum_values?.length) tags.push({ key: 'enum_values', label: `${t('parameter.validation.enumValues')}(${rules.enum_values.length})`, type: 'info' })
  if (rules.min_items != null) tags.push({ key: 'min_items', label: `${t('parameter.validation.minItems')}: ${rules.min_items}`, type: 'warning' })
  if (rules.max_items != null) tags.push({ key: 'max_items', label: `${t('parameter.validation.maxItems')}: ${rules.max_items}`, type: 'warning' })
  if (rules.date_format) tags.push({ key: 'date_format', label: `${t('parameter.validation.dateFormat')}: ${rules.date_format}`, type: 'info' })
  if (rules.date_before) tags.push({ key: 'date_before', label: `${t('parameter.validation.dateBefore')}: ${rules.date_before}`, type: 'warning' })
  if (rules.date_after) tags.push({ key: 'date_after', label: `${t('parameter.validation.dateAfter')}: ${rules.date_after}`, type: 'warning' })
  return tags
}

async function handleValidationSave(row: ParameterDTO, rules: ValidationRules) {
  try {
    await updateParameter(row.id, { validationRules: rules, version: row.version })
    validationPopoverRowId.value = null
    emit('refresh')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.saveFailed'))
  }
}

// ── Drag sort (all types participate) ──
let sortableInstance: Sortable | null = null

function initSortable() {
  if (props.readonly) return
  const el = tableRef.value?.$el?.querySelector('.el-table__body-wrapper tbody')
  if (!el) return
  sortableInstance?.destroy()
  sortableInstance = Sortable.create(el, {
    animation: 150,
    ghostClass: 'drag-source-active',
    handle: '.drag-handle',
    onEnd: async (evt: Sortable.SortableEvent) => {
      const { oldIndex, newIndex } = evt
      if (oldIndex == null || newIndex == null || oldIndex === newIndex) return
      const rows = [...sortedParameters.value]
      const [moved] = rows.splice(oldIndex, 1)
      rows.splice(newIndex, 0, moved)
      const items = rows.map((r, i) => ({
        id: r.id,
        version: r.version,
        sortOrder: i,
      }))
      try {
        await batchUpdateParameters(store.templateId, items)
        emit('refresh')
      } catch (e: any) {
        ElMessage.error(e.response?.data?.message || e.message || t('message.saveFailed'))
      }
    },
  })
}

watch(() => sortedParameters.value.length, () => {
  nextTick(() => initSortable())
})
</script>

<style scoped>
.parameter-table-view {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  overflow: hidden;
}

.field-table {
  width: 100%;
  flex: 1;
  min-height: 0;
}

.drag-handle {
  color: var(--el-text-color-placeholder);
  cursor: grab;
}
.drag-handle:active {
  cursor: grabbing;
}

.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.navigate-btn {
  flex-shrink: 0;
  font-size: 12px;
}

.children-count {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  margin-right: 2px;
}

.editable-cell {
  display: inline-block;
  padding: 2px 4px;
  border-radius: 4px;
  min-height: 22px;
  min-width: 40px;
}
.editable-cell.clickable {
  cursor: pointer;
}
.editable-cell.clickable:hover {
  background: var(--el-fill-color-light);
}

.default-value {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.inline-edit {
  display: flex;
  align-items: center;
}

:deep(.is-invalid .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

.rule-tag {
  margin: 2px 3px 2px 0;
  cursor: pointer;
}
.validation-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  min-height: 28px;
  cursor: pointer;
  padding: 2px 0;
  border-radius: 4px;
}
.validation-tags:hover {
  background: var(--el-fill-color);
}
.add-rule-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

:deep(.drag-source-active) {
  opacity: 0.5;
  transition: opacity 0.15s ease;
}
</style>
