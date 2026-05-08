<template>
  <div class="parameter-table-design">
    <div class="table-header">
      <el-breadcrumb separator="/" class="table-breadcrumb">
        <el-breadcrumb-item
          v-for="(item, idx) in breadcrumbPath"
          :key="idx"
          @click="navigateToBreadcrumb(idx)"
        >
          <span class="breadcrumb-item" :class="{ clickable: idx < breadcrumbPath.length - 1 }">
            {{ item.name }}
            <el-tag v-if="item.tableType !== 'main'" size="small" type="info" class="breadcrumb-tag">
              {{ t(`workspace.design.table.${item.tableType}`) }}
            </el-tag>
          </span>
        </el-breadcrumb-item>
      </el-breadcrumb>
      <div class="header-actions">
        <el-button v-if="!readonly" size="small" type="primary" plain @click="addDialogVisible = true">
          <el-icon><Plus /></el-icon>
          {{ t('workspace.design.table.addField') }}
        </el-button>
      </div>
    </div>

    <ParameterTableView
      ref="tableViewRef"
      :parameters="currentLevelParameters"
      :parent-id="currentParentId"
      :readonly="readonly"
      @navigate="handleNavigate"
      @refresh="handleRefresh"
    />

    <el-dialog
      v-model="addDialogVisible"
      :title="t('workspace.design.table.addField')"
      width="400px"
      :close-on-click-modal="false"
    >
      <el-form :model="newField" label-position="top">
        <el-form-item :label="t('workspace.design.table.fieldName')">
          <el-input
            v-model="newField.name"
            :placeholder="t('workspace.design.table.fieldName')"
            autofocus
            @keyup.enter="submitNewField"
          />
          <div v-if="newFieldError" class="field-error">{{ newFieldError }}</div>
        </el-form-item>
        <el-form-item :label="t('workspace.design.table.fieldType')">
          <el-select v-model="newField.fieldType" style="width: 100%">
            <el-option
              v-for="dt in allFieldTypes"
              :key="dt"
              :label="dataTypeLabel(dt)"
              :value="dt"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!newField.name.trim() || !!newFieldError" @click="submitNewField">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { createParameter } from '@/api/parameters'
import type { ParameterBreadcrumbItem } from '@/types/workspace'
import type { ParameterDTO, DataType } from '@/types/parameter'
import { PARAMETER_NAME_REGEX } from '@/constants/parameterNamePattern'
import ParameterTableView from './ParameterTableView.vue'

const MAX_DEPTH = 5
// Extended field types: includes data types + virtual "FORMULA" type
type FieldType = DataType | 'FORMULA'
const allFieldTypes: FieldType[] = ['STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'ARRAY', 'OBJECT', 'FORMULA']

defineProps<{
  readonly: boolean
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()
const tableViewRef = ref()

const breadcrumbPath = ref<ParameterBreadcrumbItem[]>([
  { id: null, name: t('workspace.design.table.main'), tableType: 'main' },
])

const currentParentId = computed(() => {
  const last = breadcrumbPath.value[breadcrumbPath.value.length - 1]
  return last.id
})

const currentLevelParameters = computed(() => {
  const pid = currentParentId.value
  if (pid === null) {
    return store.parameters.filter(p => p.parentId === null)
  }
  const parent = findParameterById(store.parameters, pid)
  return parent?.children ?? []
})

function findParameterById(params: ParameterDTO[], id: number): ParameterDTO | null {
  for (const p of params) {
    if (p.id === id) return p
    if (p.children?.length) {
      const found = findParameterById(p.children, id)
      if (found) return found
    }
  }
  return null
}

function handleNavigate(param: ParameterDTO) {
  if (breadcrumbPath.value.length >= MAX_DEPTH) return
  const tableType = param.dataType === 'ARRAY' ? 'sub' : 'related'
  breadcrumbPath.value.push({ id: param.id, name: param.name, tableType })
}

function navigateToBreadcrumb(index: number) {
  if (index >= breadcrumbPath.value.length - 1) return
  breadcrumbPath.value = breadcrumbPath.value.slice(0, index + 1)
}

async function handleRefresh() {
  await store.refreshParameters()
}

const addDialogVisible = ref(false)
const newField = ref({ name: '', fieldType: 'STRING' as FieldType })
const newFieldError = ref('')

watch(() => newField.value.name, (val) => {
  if (!val.trim()) { newFieldError.value = ''; return }
  if (!PARAMETER_NAME_REGEX.test(val)) {
    newFieldError.value = t('workspace.design.table.nameInvalid')
    return
  }
  const dup = currentLevelParameters.value.find(p => p.name === val)
  if (dup) { newFieldError.value = t('workspace.design.table.nameDuplicate'); return }
  newFieldError.value = ''
})

function dataTypeLabel(dt: string): string {
  const key = `workspace.design.table.dataType.${dt}`
  const translated = t(key)
  return translated === key ? dt : translated
}

async function submitNewField() {
  const name = newField.value.name.trim()
  if (!name || newFieldError.value) return
  try {
    const isFormula = newField.value.fieldType === 'FORMULA'
    const dataType: DataType = isFormula ? 'STRING' : newField.value.fieldType as DataType
    await createParameter(store.templateId, {
      name,
      dataType,
      parameterType: isFormula ? 'DERIVED' : 'REQUEST',
      expressionText: isFormula ? '""' : undefined,
      expressionType: isFormula ? 'JAVASCRIPT' : undefined,
      parentId: currentParentId.value,
      sortOrder: currentLevelParameters.value.length,
    })
    addDialogVisible.value = false
    newField.value = { name: '', fieldType: 'STRING' }
    await handleRefresh()
    ElMessage.success(t('message.createSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  }
}
</script>

<style scoped>
.parameter-table-design {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  height: 100%;
  overflow: hidden;
}

.table-header {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-breadcrumb {
  flex-shrink: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.parameter-table-design > :deep(.parameter-table-view) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.breadcrumb-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.breadcrumb-item.clickable {
  cursor: pointer;
  color: var(--el-color-primary);
}
.breadcrumb-item.clickable:hover {
  text-decoration: underline;
}

.breadcrumb-tag {
  margin-left: 4px;
}

.field-error {
  color: var(--el-color-danger);
  font-size: 12px;
  margin-top: 4px;
}
</style>


