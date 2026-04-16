<template>
  <div class="parameter-tree-table">
    <el-empty v-if="parameters.length === 0" :description="t('parameter.empty')" />
    <el-table
      v-else
      ref="tableRef"
      :data="parameters"
      row-key="id"
      :tree-props="{ children: 'children' }"
      :default-expand-all="false"
      border
      @row-contextmenu="handleContextMenu"
    >
      <!-- Drag handle + checkbox column -->
      <el-table-column width="60" align="center">
        <template #default="{ row }">
          <div class="cell-drag-check">
            <span class="drag-handle" :data-row-id="row.id">⠿</span>
            <el-checkbox
              :model-value="selectedIds.has(row.id)"
              @change="(val: boolean | string | number) => emit('toggleSelect', row.id, !!val)"
            />
          </div>
        </template>
      </el-table-column>

      <!-- Name column — inline edit -->
      <el-table-column :label="t('parameter.name')" min-width="180">
        <template #default="{ row }">
          <div v-if="isEditingCell(row.id, 'name')" class="inline-edit-cell">
            <el-input
              ref="inlineInputRef"
              v-model="inlineEdit.value"
              size="small"
              @keyup.enter="confirmInlineEdit(row)"
              @keyup.esc="cancelInlineEdit"
              @blur="confirmInlineEdit(row)"
            />
          </div>
          <el-tooltip v-else :content="row.parameterPath" placement="top">
            <span class="editable-cell" @click="startInlineEdit(row, 'name')">
              {{ row.name }}
            </span>
          </el-tooltip>
        </template>
      </el-table-column>

      <!-- Parameter type column -->
      <el-table-column :label="t('parameter.parameterType')" width="110">
        <template #default="{ row }">
          <el-select
            :model-value="row.parameterType"
            size="small"
            @change="(val: string) => handleParameterTypeChange(row, val)"
          >
            <el-option label="REQUEST" value="REQUEST" />
            <el-option label="DERIVED" value="DERIVED" />
          </el-select>
        </template>
      </el-table-column>

      <!-- Data type column -->
      <el-table-column :label="t('parameter.dataType')" width="130">
        <template #default="{ row }">
          <el-select
            :model-value="row.dataType"
            size="small"
            @change="(val: string) => handleDataTypeChange(row, val)"
          >
            <el-option v-for="dt in dataTypes" :key="dt" :label="dt" :value="dt" />
          </el-select>
        </template>
      </el-table-column>

      <!-- Required column -->
      <el-table-column :label="t('parameter.required')" width="80" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.required"
            size="small"
            @change="(val: boolean | string | number) => emit('update', row.id, { required: !!val, version: row.version })"
          />
        </template>
      </el-table-column>

      <!-- Default value column — inline edit with type-appropriate controls -->
      <el-table-column :label="t('parameter.defaultValue')" min-width="120">
        <template #default="{ row }">
          <div v-if="isEditingCell(row.id, 'defaultValue')" class="inline-edit-cell">
            <el-input-number
              v-if="row.dataType === 'NUMBER'"
              v-model="inlineEditNumber"
              size="small"
              controls-position="right"
              @keyup.enter="confirmInlineEdit(row)"
              @keyup.esc="cancelInlineEdit"
              @blur="confirmInlineEdit(row)"
            />
            <el-date-picker
              v-else-if="row.dataType === 'DATE'"
              v-model="inlineEdit.value"
              type="date"
              size="small"
              value-format="YYYY-MM-DD"
              @change="() => confirmInlineEdit(row)"
            />
            <el-switch
              v-else-if="row.dataType === 'BOOLEAN'"
              :model-value="inlineEdit.value === 'true'"
              size="small"
              @change="(val: boolean | string | number) => { inlineEdit.value = String(!!val); confirmInlineEdit(row) }"
            />
            <el-input
              v-else
              ref="inlineInputRef"
              v-model="inlineEdit.value"
              size="small"
              @keyup.enter="confirmInlineEdit(row)"
              @keyup.esc="cancelInlineEdit"
              @blur="confirmInlineEdit(row)"
            />
          </div>
          <span v-else class="editable-cell" @click="startInlineEdit(row, 'defaultValue')">
            {{ row.defaultValue ?? '—' }}
          </span>
        </template>
      </el-table-column>

      <!-- Description column — inline edit -->
      <el-table-column :label="t('parameter.description')" min-width="140">
        <template #default="{ row }">
          <div v-if="isEditingCell(row.id, 'description')" class="inline-edit-cell">
            <el-input
              ref="inlineInputRef"
              v-model="inlineEdit.value"
              size="small"
              @keyup.enter="confirmInlineEdit(row)"
              @keyup.esc="cancelInlineEdit"
              @blur="confirmInlineEdit(row)"
            />
          </div>
          <span v-else class="editable-cell" @click="startInlineEdit(row, 'description')">
            {{ row.description ?? '—' }}
          </span>
        </template>
      </el-table-column>

      <!-- Validation rules column — integrated popover -->
      <el-table-column :label="t('parameter.validationRules')" width="110" align="center">
        <template #default="{ row }">
          <ValidationRulesPopover
            :visible="validationPopoverRowId === row.id"
            :data-type="row.dataType"
            :rules="row.validationRules"
            @update:visible="(val: boolean) => { validationPopoverRowId = val ? row.id : null }"
            @save="(rules) => handleValidationSave(row, rules)"
          >
            <el-badge
              v-if="countRules(row.validationRules) > 0"
              :value="t('parameter.ruleCount', { count: countRules(row.validationRules) })"
              type="info"
              class="rule-badge"
              @click="validationPopoverRowId = row.id"
            />
            <span v-else class="editable-cell" @click="validationPopoverRowId = row.id">—</span>
          </ValidationRulesPopover>
        </template>
      </el-table-column>

      <!-- Actions column -->
      <el-table-column :label="t('common.actions')" width="160" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.dataType === 'OBJECT' || row.dataType === 'ARRAY'"
            link
            type="primary"
            size="small"
            @click="emit('addChild', row.id)"
          >
            {{ t('parameter.addChild') }}
          </el-button>
          <el-button link type="danger" size="small" @click="emit('delete', row.id)">
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- DERIVED expression editor (expandable below row) -->
    <template v-for="param in flatDerivedParams" :key="'expr-' + param.id">
      <div v-if="expandedExpressionIds.has(param.id)" class="derived-expression-row">
        <div class="expression-label">{{ param.name }} — Expression:</div>
        <DerivedExpressionEditor
          :expression-text="param.expressionText ?? ''"
          :expression-type="param.expressionType ?? 'JAVASCRIPT'"
          :available-parameters="getAvailableParamsForExpression(param.id)"
          @update:expression-text="(val) => emit('update', param.id, { expressionText: val, version: param.version })"
          @update:expression-type="(val) => emit('update', param.id, { expressionType: val as any, version: param.version })"
        />
      </div>
    </template>

    <!-- Context Menu -->
    <div
      v-show="contextMenuVisible"
      ref="contextMenuRef"
      class="context-menu"
      :style="{ left: contextMenuPos.x + 'px', top: contextMenuPos.y + 'px' }"
    >
      <div class="context-menu-item" @click="handleContextAction('addSibling')">{{ t('parameter.contextMenu.addSibling') }}</div>
      <div
        v-if="contextRow && (contextRow.dataType === 'OBJECT' || contextRow.dataType === 'ARRAY')"
        class="context-menu-item"
        @click="handleContextAction('addChild')"
      >{{ t('parameter.contextMenu.addChild') }}</div>
      <div class="context-menu-item" @click="handleContextAction('copy')">{{ t('parameter.contextMenu.copy') }}</div>
      <div
        class="context-menu-item"
        :class="{ 'context-menu-disabled': !copiedParameter }"
        @click="handleContextAction('paste')"
      >{{ t('parameter.contextMenu.paste') }}</div>
      <div class="context-menu-item" @click="handleContextAction('duplicate')">{{ t('parameter.contextMenu.duplicate') }}</div>
      <div class="context-menu-item context-menu-danger" @click="handleContextAction('delete')">{{ t('parameter.contextMenu.delete') }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import type { ParameterDTO, DataType, ValidationRules, UpdateParameterRequest } from '@/types/parameter'
import ValidationRulesPopover from './ValidationRulesPopover.vue'
import DerivedExpressionEditor from './DerivedExpressionEditor.vue'

type EditableField = 'name' | 'defaultValue' | 'description'

const props = defineProps<{
  parameters: ParameterDTO[]
  selectedIds: Set<number>
}>()

const emit = defineEmits<{
  update: [id: number, data: UpdateParameterRequest]
  delete: [id: number]
  addChild: [parentId: number]
  addSibling: [parentId: number | null]
  toggleSelect: [id: number, selected: boolean]
  copy: [param: ParameterDTO]
  paste: [parentId: number | null]
  duplicate: [id: number]
}>()

const { t } = useI18n()

const dataTypes: DataType[] = ['STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'ARRAY', 'OBJECT']
const tableRef = ref<any>(null)
const inlineInputRef = ref<any>(null)

// ── Inline editing state ──
const inlineEdit = reactive({
  rowId: null as number | null,
  field: null as EditableField | null,
  value: '',
  originalValue: '',
})
const inlineEditNumber = ref<number>(0)

function isEditingCell(rowId: number, field: EditableField): boolean {
  return inlineEdit.rowId === rowId && inlineEdit.field === field
}

function startInlineEdit(row: ParameterDTO, field: EditableField) {
  // If already editing another cell, confirm it first
  if (inlineEdit.rowId !== null && (inlineEdit.rowId !== row.id || inlineEdit.field !== field)) {
    const prevRow = findParameterById(props.parameters, inlineEdit.rowId)
    if (prevRow) confirmInlineEdit(prevRow)
  }

  inlineEdit.rowId = row.id
  inlineEdit.field = field
  const rawValue = (row as any)[field] ?? ''
  inlineEdit.value = String(rawValue)
  inlineEdit.originalValue = String(rawValue)

  if (field === 'defaultValue' && row.dataType === 'NUMBER') {
    inlineEditNumber.value = Number(rawValue) || 0
  }

  nextTick(() => {
    inlineInputRef.value?.focus?.()
    inlineInputRef.value?.select?.()
  })
}

function confirmInlineEdit(row: ParameterDTO) {
  if (inlineEdit.rowId === null || inlineEdit.field === null) return

  let finalValue = inlineEdit.value
  if (inlineEdit.field === 'defaultValue' && row.dataType === 'NUMBER') {
    finalValue = String(inlineEditNumber.value)
  }

  // Only emit update if value changed
  if (finalValue !== inlineEdit.originalValue) {
    const data: UpdateParameterRequest = { version: row.version }
    ;(data as any)[inlineEdit.field] = finalValue || null
    emit('update', row.id, data)
  }

  inlineEdit.rowId = null
  inlineEdit.field = null
}

function cancelInlineEdit() {
  inlineEdit.value = inlineEdit.originalValue
  inlineEdit.rowId = null
  inlineEdit.field = null
}

/** Activate inline edit on a specific row/field (called from parent) */
function activateInlineEdit(rowId: number, field: EditableField) {
  const row = findParameterById(props.parameters, rowId)
  if (row) startInlineEdit(row, field)
}

// ── Parameter type change ──
function handleParameterTypeChange(row: ParameterDTO, newType: string) {
  emit('update', row.id, { parameterType: newType as any, version: row.version })
  if (newType === 'DERIVED') {
    expandedExpressionIds.value.add(row.id)
  }
}

// ── Data type change with child deletion warning ──
async function handleDataTypeChange(row: ParameterDTO, newType: string) {
  if ((row.dataType === 'OBJECT' || row.dataType === 'ARRAY') && row.children?.length > 0) {
    if (newType !== 'OBJECT' && newType !== 'ARRAY') {
      try {
        await ElMessageBox.confirm(
          t('parameter.changeTypeWarning'),
          t('common.warning'),
          { type: 'warning' },
        )
      } catch { return }
    }
  }
  emit('update', row.id, { dataType: newType as DataType, version: row.version })
}

// ── Validation rules popover ──
const validationPopoverRowId = ref<number | null>(null)

function handleValidationSave(row: ParameterDTO, rules: ValidationRules) {
  emit('update', row.id, { validationRules: rules, version: row.version })
  validationPopoverRowId.value = null
}

// ── Derived expression editor ──
const expandedExpressionIds = ref(new Set<number>())

const flatDerivedParams = computed(() => {
  return flattenAll(props.parameters).filter(p => p.parameterType === 'DERIVED')
})

function getAvailableParamsForExpression(currentId: number): ParameterDTO[] {
  return flattenAll(props.parameters).filter(p => p.id !== currentId)
}

// ── Context menu ──
const contextMenuVisible = ref(false)
const contextMenuPos = ref({ x: 0, y: 0 })
const contextRow = ref<ParameterDTO | null>(null)
const contextMenuRef = ref<HTMLElement | null>(null)
const copiedParameter = ref<ParameterDTO | null>(null)

function handleContextMenu(row: ParameterDTO, _column: any, event: MouseEvent) {
  event.preventDefault()
  contextRow.value = row
  contextMenuPos.value = { x: event.clientX, y: event.clientY }
  contextMenuVisible.value = true
}

function handleContextAction(action: string) {
  contextMenuVisible.value = false
  if (!contextRow.value) return
  switch (action) {
    case 'addSibling':
      emit('addSibling', contextRow.value.parentId)
      break
    case 'addChild':
      emit('addChild', contextRow.value.id)
      break
    case 'copy':
      copiedParameter.value = contextRow.value
      emit('copy', contextRow.value)
      break
    case 'paste':
      if (copiedParameter.value) {
        emit('paste', contextRow.value.parentId)
      }
      break
    case 'duplicate':
      emit('duplicate', contextRow.value.id)
      break
    case 'delete':
      emit('delete', contextRow.value.id)
      break
  }
}

function setCopiedParameter(param: ParameterDTO | null) {
  copiedParameter.value = param
}

function hideContextMenu() {
  contextMenuVisible.value = false
}

onMounted(() => {
  document.addEventListener('click', hideContextMenu)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', hideContextMenu)
})

// ── Expand/Collapse ──
function expandAll() {
  if (tableRef.value) {
    toggleAllRows(props.parameters, true)
  }
}

function collapseAll() {
  if (tableRef.value) {
    toggleAllRows(props.parameters, false)
  }
}

function toggleAllRows(rows: ParameterDTO[], expand: boolean) {
  for (const row of rows) {
    tableRef.value?.toggleRowExpansion(row, expand)
    if (row.children?.length) {
      toggleAllRows(row.children, expand)
    }
  }
}

// ── Helpers ──
function countRules(rules: ValidationRules | null): number {
  if (!rules) return 0
  return Object.keys(rules).filter(k => k !== 'custom_message' && (rules as any)[k] != null).length
}

function flattenAll(params: ParameterDTO[]): ParameterDTO[] {
  const result: ParameterDTO[] = []
  for (const p of params) {
    result.push(p)
    if (p.children?.length) result.push(...flattenAll(p.children))
  }
  return result
}

function findParameterById(params: ParameterDTO[], id: number): ParameterDTO | undefined {
  for (const p of params) {
    if (p.id === id) return p
    if (p.children?.length) {
      const found = findParameterById(p.children, id)
      if (found) return found
    }
  }
  return undefined
}

function getTableRef() {
  return tableRef.value
}

defineExpose({ expandAll, collapseAll, activateInlineEdit, setCopiedParameter, getTableRef })
</script>

<style scoped>
.parameter-tree-table {
  position: relative;
}
.cell-drag-check {
  display: flex;
  align-items: center;
  gap: 4px;
}
.drag-handle {
  cursor: grab;
  color: var(--el-text-color-placeholder);
  font-size: 14px;
  opacity: 0;
  transition: opacity 0.15s;
  user-select: none;
}
.el-table__row:hover .drag-handle {
  opacity: 1;
}
.editable-cell {
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
}
.editable-cell:hover {
  background: var(--el-fill-color);
}
.inline-edit-cell {
  width: 100%;
}
.rule-badge {
  cursor: pointer;
}
.derived-expression-row {
  padding: 8px 16px 8px 60px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-blank);
}
.expression-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.context-menu {
  position: fixed;
  z-index: 3000;
  background: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  padding: 4px 0;
  min-width: 160px;
}
.context-menu-item {
  padding: 8px 16px;
  cursor: pointer;
  font-size: 13px;
}
.context-menu-item:hover {
  background: var(--el-fill-color-light);
}
.context-menu-disabled {
  color: var(--el-text-color-placeholder);
  cursor: not-allowed;
}
.context-menu-danger {
  color: var(--el-color-danger);
}
</style>
