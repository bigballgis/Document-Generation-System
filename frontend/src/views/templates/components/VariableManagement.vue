<template>
  <div class="variable-management" v-loading="loading">
    <el-table :data="variables" stripe>
      <el-table-column prop="name" :label="$t('template.variableName')" min-width="160" />
      <el-table-column prop="type" :label="$t('template.variableType')" width="120">
        <template #default="{ row }">
          <el-tag size="small">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="defaultValue" :label="$t('template.variableDefault')" width="140" show-overflow-tooltip />
      <el-table-column prop="description" :label="$t('template.variableDescription')" min-width="180" show-overflow-tooltip />
      <el-table-column :label="$t('template.variableSource')" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.bound" type="success" size="small">
            {{ $t('template.variableBound') }}
          </el-tag>
          <el-tag v-else type="danger" size="small">
            {{ $t('template.variableUnbound') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="boundTo" label="Bound To" width="160" show-overflow-tooltip />
      <el-table-column :label="$t('common.actions')" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openBindDialog(row)">
            {{ $t('template.bindVariable') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="bindDialogVisible" :title="$t('template.bindVariable')" width="480px">
      <el-form label-width="120px">
        <el-form-item :label="$t('template.variableName')">
          <el-input :model-value="bindingVar?.name" disabled />
        </el-form-item>
        <el-form-item label="Data Source ID">
          <el-input-number v-model="bindForm.dataSourceId" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Field Path">
          <el-input v-model="bindForm.fieldPath" placeholder="e.g. response.data.name" />
        </el-form-item>
        <el-form-item label="Expression ID">
          <el-input-number v-model="bindForm.expressionId" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="bindSaving" @click="handleBind">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getTemplateVariables, bindVariable, type VariableDTO } from '@/api/templates'

const props = defineProps<{ templateId: number }>()
const { t } = useI18n()

const loading = ref(false)
const variables = ref<VariableDTO[]>([])
const bindDialogVisible = ref(false)
const bindSaving = ref(false)
const bindingVar = ref<VariableDTO | null>(null)

const bindForm = reactive({
  dataSourceId: undefined as number | undefined,
  fieldPath: '',
  expressionId: undefined as number | undefined,
})

async function fetchVariables() {
  loading.value = true
  try {
    variables.value = await getTemplateVariables(props.templateId)
  } catch {} finally {
    loading.value = false
  }
}

function openBindDialog(row: VariableDTO) {
  bindingVar.value = row
  bindForm.dataSourceId = undefined
  bindForm.fieldPath = ''
  bindForm.expressionId = undefined
  bindDialogVisible.value = true
}

async function handleBind() {
  if (!bindingVar.value) return
  bindSaving.value = true
  try {
    await bindVariable(props.templateId, bindingVar.value.id, {
      dataSourceId: bindForm.dataSourceId,
      fieldPath: bindForm.fieldPath || undefined,
      expressionId: bindForm.expressionId,
    })
    ElMessage.success(t('message.operationSuccess'))
    bindDialogVisible.value = false
    fetchVariables()
  } catch {} finally {
    bindSaving.value = false
  }
}

onMounted(fetchVariables)
</script>

