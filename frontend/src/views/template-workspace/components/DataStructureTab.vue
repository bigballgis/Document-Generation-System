<template>
  <div class="data-structure-tab">
    <!-- Data Sources Section -->
    <div class="section-header">
      <h3>{{ t('workspace.dataSource.title') }}</h3>
      <el-button type="primary" @click="openAddDataSource">
        {{ t('workspace.dataSource.add') }}
      </el-button>
    </div>

    <el-empty
      v-if="store.dataSources.length === 0"
      :description="t('workspace.dataSource.empty')"
    />
    <el-table v-else :data="store.dataSources" stripe>
      <el-table-column prop="name" :label="t('dataSource.name')" min-width="150" />
      <el-table-column :label="t('dataSource.type')" width="160">
        <template #default="{ row }">
          <el-tag :type="dataSourceTagType(row.type)">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="priority" :label="t('dataSource.priority')" width="100" />
      <el-table-column :label="t('dataSource.cacheEnabled')" width="100" align="center">
        <template #default="{ row }">
          <el-icon v-if="row.cacheEnabled" color="var(--el-color-success)"><Check /></el-icon>
          <el-icon v-else color="var(--el-color-info)"><Close /></el-icon>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" :label="t('common.updatedAt')" width="180" />
      <el-table-column :label="t('common.actions')" width="240" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :disabled="deletingIds.has(row.id)"
            @click="openEditDataSource(row)"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            link
            type="primary"
            :disabled="deletingIds.has(row.id)"
            @click="handleTestConnection(row.id)"
          >
            {{ t('dataSource.testConnection') }}
          </el-button>
          <el-button
            link
            type="danger"
            :loading="deletingIds.has(row.id)"
            @click="handleDeleteDataSource(row.id)"
          >
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-divider />

    <!-- Expressions Section -->
    <div class="section-header">
      <h3>{{ t('workspace.expression.title') }}</h3>
      <el-button type="primary" @click="openAddExpression">
        {{ t('workspace.expression.add') }}
      </el-button>
    </div>

    <el-empty
      v-if="store.expressions.length === 0"
      :description="t('workspace.expression.empty')"
    />
    <el-table v-else :data="store.expressions" stripe>
      <el-table-column prop="name" :label="t('expression.name')" min-width="150" />
      <el-table-column :label="t('expression.type')" width="140">
        <template #default="{ row }">
          <el-tag :type="expressionTagType(row.expressionType)">{{ row.expressionType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('expression.content')" min-width="200">
        <template #default="{ row }">
          <el-tooltip
            :content="row.expressionText"
            placement="top"
            :disabled="row.expressionText.length <= 80"
          >
            <span class="expression-text">{{ truncate(row.expressionText, 80) }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="executionOrder" :label="t('expression.executionOrder')" width="120" />
      <el-table-column prop="createdAt" :label="t('common.createdAt')" width="180" />
      <el-table-column :label="t('common.actions')" width="240" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :disabled="deletingIds.has(row.id)"
            @click="openEditExpression(row)"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            link
            type="primary"
            :disabled="deletingIds.has(row.id)"
            @click="handleValidateExpression(row)"
          >
            {{ t('expression.validate') }}
          </el-button>
          <el-button
            link
            type="danger"
            :loading="deletingIds.has(row.id)"
            @click="handleDeleteExpression(row.id)"
          >
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Dialogs -->
    <DataSourceFormDialog
      v-model:visible="dsDialogVisible"
      :data-source="editingDataSource"
      :template-id="store.templateId"
      @saved="onDataSourceSaved"
    />
    <ExpressionFormDialog
      v-model:visible="exprDialogVisible"
      :template-id="store.templateId"
      :data="editingExpression"
      @saved="onExpressionSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close } from '@element-plus/icons-vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { testConnection, deleteDataSource } from '@/api/data-sources'
import type { DataSourceDTO } from '@/api/data-sources'
import { deleteExpression, validateExpression } from '@/api/expressions'
import type { ExpressionDTO } from '@/types/document'
import DataSourceFormDialog from '@/views/data-sources/DataSourceFormDialog.vue'
import ExpressionFormDialog from '@/views/templates/components/ExpressionFormDialog.vue'

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

// ── Dialog state ──
const dsDialogVisible = ref(false)
const editingDataSource = ref<DataSourceDTO | null>(null)
const exprDialogVisible = ref(false)
const editingExpression = ref<ExpressionDTO | null>(null)

// ── Deleting guard ──
const deletingIds = reactive(new Set<number>())

// ── Helpers ──
function truncate(text: string, max: number): string {
  return text.length > max ? text.slice(0, max) + '...' : text
}

function dataSourceTagType(type: string) {
  switch (type) {
    case 'HTTP_API': return 'primary'
    case 'DATABASE': return 'success'
    case 'INTERNAL_SYSTEM': return 'warning'
    default: return 'info'
  }
}

function expressionTagType(type: string) {
  return type === 'JAVASCRIPT' ? 'primary' : 'success'
}

// ── Data Source operations ──
function openAddDataSource() {
  editingDataSource.value = null
  dsDialogVisible.value = true
}

function openEditDataSource(row: DataSourceDTO) {
  editingDataSource.value = row
  dsDialogVisible.value = true
}

async function onDataSourceSaved() {
  await store.refreshDataSources()
}

async function handleTestConnection(id: number) {
  try {
    const result = await testConnection(id)
    if (result.success) {
      ElMessage.success(
        result.responseTime != null
          ? `${t('dataSource.testSuccess')} (${result.responseTime}ms)`
          : t('dataSource.testSuccess'),
      )
    } else {
      ElMessage.error(result.message || t('dataSource.testFailed'))
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('dataSource.testFailed'))
  }
}

async function handleDeleteDataSource(id: number) {
  try {
    await ElMessageBox.confirm(
      t('confirm.deleteMessage'),
      t('confirm.deleteTitle'),
      { type: 'warning' },
    )
  } catch {
    return
  }

  deletingIds.add(id)
  try {
    await deleteDataSource(id)
    await store.refreshDataSources()
    ElMessage.success(t('message.deleteSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  } finally {
    deletingIds.delete(id)
  }
}

// ── Expression operations ──
function openAddExpression() {
  editingExpression.value = null
  exprDialogVisible.value = true
}

function openEditExpression(row: ExpressionDTO) {
  editingExpression.value = row
  exprDialogVisible.value = true
}

async function onExpressionSaved() {
  await store.refreshExpressions()
}

async function handleValidateExpression(row: ExpressionDTO) {
  try {
    const result = await validateExpression({
      expression: row.expressionText,
      expressionType: row.expressionType,
    })
    if (result.valid) {
      ElMessage.success(t('expression.validationSuccess'))
    } else {
      const msg = result.errorMessage || t('expression.validationFailed')
      const detail = result.errorPosition != null
        ? `${msg} (position: ${result.errorPosition})`
        : msg
      ElMessage.error(detail)
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('expression.validationFailed'))
  }
}

async function handleDeleteExpression(id: number) {
  try {
    await ElMessageBox.confirm(
      t('confirm.deleteMessage'),
      t('confirm.deleteTitle'),
      { type: 'warning' },
    )
  } catch {
    return
  }

  deletingIds.add(id)
  try {
    await deleteExpression(id)
    await store.refreshExpressions()
    ElMessage.success(t('message.deleteSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  } finally {
    deletingIds.delete(id)
  }
}
</script>

<style scoped>
.data-structure-tab {
  padding: 16px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--el-text-color-primary);
}

.expression-text {
  font-family: monospace;
  font-size: 13px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: inline-block;
  max-width: 100%;
}
</style>
