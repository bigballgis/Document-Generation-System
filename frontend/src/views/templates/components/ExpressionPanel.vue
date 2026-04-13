<template>
  <div class="expression-panel">
    <div class="toolbar">
      <el-button type="primary" @click="openCreateDialog">{{ $t('expression.create') }}</el-button>
    </div>

    <el-table :data="expressions" v-loading="loading" border stripe>
      <el-table-column prop="name" :label="$t('expression.name')" min-width="140" />
      <el-table-column :label="$t('expression.type')" width="130">
        <template #default="{ row }">
          {{ row.expressionType === 'JAVASCRIPT' ? $t('expression.typeJavaScript') : $t('expression.typeExcel') }}
        </template>
      </el-table-column>
      <el-table-column :label="$t('expression.content')" min-width="200">
        <template #default="{ row }">
          {{ row.expressionText.length > 50 ? row.expressionText.substring(0, 50) + '...' : row.expressionText }}
        </template>
      </el-table-column>
      <el-table-column prop="executionOrder" :label="$t('expression.executionOrder')" width="130" />
      <el-table-column prop="createdAt" :label="$t('common.createdAt')" width="170" />
      <el-table-column :label="$t('common.actions')" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEditDialog(row)">{{ $t('common.edit') }}</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
          <el-button size="small" :loading="validatingId === row.id" @click="handleValidate(row)">{{ $t('expression.validate') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <ExpressionFormDialog
      v-model:visible="dialogVisible"
      :template-id="templateId"
      :data="editingExpression"
      @saved="onSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ExpressionDTO } from '@/types/document'
import { getExpressions, deleteExpression, validateExpression } from '@/api/expressions'
import ExpressionFormDialog from './ExpressionFormDialog.vue'

const props = defineProps<{ templateId: number }>()
const { t } = useI18n()

const loading = ref(false)
const expressions = ref<ExpressionDTO[]>([])
const dialogVisible = ref(false)
const editingExpression = ref<ExpressionDTO | null>(null)
const validatingId = ref<number | null>(null)

async function fetchExpressions() {
  loading.value = true
  try {
    expressions.value = await getExpressions(props.templateId)
  } catch { /* handled */ } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingExpression.value = null
  dialogVisible.value = true
}

function openEditDialog(row: ExpressionDTO) {
  editingExpression.value = row
  dialogVisible.value = true
}

function onSaved() {
  fetchExpressions()
}

async function handleDelete(row: ExpressionDTO) {
  try {
    await ElMessageBox.confirm(
      t('expression.confirmDelete', { name: row.name }),
      t('confirm.deleteTitle'),
      { type: 'warning' },
    )
    await deleteExpression(row.id)
    ElMessage.success(t('message.deleteSuccess'))
    fetchExpressions()
  } catch { /* cancelled */ }
}

async function handleValidate(row: ExpressionDTO) {
  validatingId.value = row.id
  try {
    const result = await validateExpression({
      expression: row.expressionText,
      expressionType: row.expressionType,
    })
    if (result.valid) {
      ElMessage.success(t('expression.validationSuccess'))
    } else {
      let msg = t('expression.validationFailed')
      if (result.errorMessage) msg += `: ${result.errorMessage}`
      if (result.errorPosition != null) msg += ` ${t('expression.errorPosition', { pos: result.errorPosition })}`
      ElMessage.error(msg)
    }
  } catch { /* handled */ } finally {
    validatingId.value = null
  }
}

onMounted(fetchExpressions)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
</style>
