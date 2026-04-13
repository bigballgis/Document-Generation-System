<template>
  <div class="data-sources-page">
    <!-- Header -->
    <div class="page-header">
      <h2>{{ $t('dataSource.title') }}</h2>
      <div class="header-actions">
        <el-select
          v-model="selectedTemplateId"
          :placeholder="$t('template.name')"
          filterable
          style="width: 240px; margin-right: 12px"
          @change="loadDataSources"
        >
          <el-option
            v-for="tpl in templates"
            :key="tpl.id"
            :label="tpl.name"
            :value="tpl.id"
          />
        </el-select>
        <el-button
          type="primary"
          :icon="Plus"
          :disabled="!selectedTemplateId"
          @click="openCreateDialog"
        >
          {{ $t('dataSource.create') }}
        </el-button>
      </div>
    </div>

    <!-- Data Source Table -->
    <el-table
      v-loading="loading"
      :data="dataSources"
      stripe
      style="width: 100%"
    >
      <el-table-column prop="name" :label="$t('dataSource.name')" min-width="160" />
      <el-table-column :label="$t('dataSource.type')" width="150">
        <template #default="{ row }">
          <el-tag :type="typeTagColor(row.type)" effect="plain">
            {{ typeLabel(row.type) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('dataSource.priority')" width="100" prop="priority" />
      <el-table-column :label="$t('dataSource.cacheEnabled')" width="120">
        <template #default="{ row }">
          <el-tag :type="row.cacheEnabled ? 'success' : 'info'" size="small">
            {{ row.cacheEnabled ? $t('common.yes') : $t('common.no') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.updatedAt')" width="180">
        <template #default="{ row }">
          {{ formatDate(row.updatedAt) }}
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="handleTestConnection(row)">
            {{ $t('dataSource.testConnection') }}
          </el-button>
          <el-button size="small" type="primary" link @click="openEditDialog(row)">
            {{ $t('common.edit') }}
          </el-button>
          <el-popconfirm
            :title="$t('confirm.deleteMessage')"
            @confirm="handleDelete(row.id)"
          >
            <template #reference>
              <el-button size="small" type="danger" link>
                {{ $t('common.delete') }}
              </el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create/Edit Dialog -->
    <DataSourceFormDialog
      v-model:visible="dialogVisible"
      :data-source="editingDataSource"
      :template-id="selectedTemplateId!"
      @saved="onSaved"
    />

    <!-- Test Connection Result Dialog -->
    <el-dialog
      v-model="testResultVisible"
      :title="$t('dataSource.testConnection')"
      width="420px"
      destroy-on-close
    >
      <el-result
        :icon="testResult?.success ? 'success' : 'error'"
        :title="testResult?.success ? $t('dataSource.testSuccess') : $t('dataSource.testFailed')"
        :sub-title="testResult?.message"
      >
        <template #extra v-if="testResult?.responseTime">
          <el-text type="info">{{ testResult.responseTime }}ms</el-text>
        </template>
      </el-result>
    </el-dialog>

    <!-- Pipeline Visualization Section -->
    <div v-if="dataSources.length > 0" class="pipeline-section">
      <h3>{{ $t('dataSource.pipeline') }}</h3>
      <PipelineVisualization :data-sources="dataSources" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  getDataSources,
  deleteDataSource,
  testConnection,
  type DataSourceDTO,
  type TestConnectionResult,
} from '@/api/data-sources'
import { getTemplates, type TemplateDTO } from '@/api/templates'
import DataSourceFormDialog from './DataSourceFormDialog.vue'
import PipelineVisualization from './PipelineVisualization.vue'

const { t } = useI18n()

const loading = ref(false)
const templates = ref<TemplateDTO[]>([])
const selectedTemplateId = ref<number | null>(null)
const dataSources = ref<DataSourceDTO[]>([])

// Dialog state
const dialogVisible = ref(false)
const editingDataSource = ref<DataSourceDTO | null>(null)

// Test connection
const testResultVisible = ref(false)
const testResult = ref<TestConnectionResult | null>(null)

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

function typeTagColor(type: string): TagType {
  const map: Record<string, TagType> = {
    HTTP_API: 'primary',
    DATABASE: 'success',
    INTERNAL_SYSTEM: 'warning',
  }
  return map[type] || 'info'
}

function typeLabel(type: string) {
  const map: Record<string, string> = {
    HTTP_API: t('dataSource.typeHttpApi'),
    DATABASE: t('dataSource.typeDatabase'),
    INTERNAL_SYSTEM: t('dataSource.typeInternal'),
  }
  return map[type] || type
}

function formatDate(iso: string) {
  if (!iso) return ''
  return new Date(iso).toLocaleString()
}

async function loadTemplates() {
  try {
    const res = await getTemplates({ page: 0, size: 200 })
    templates.value = res.content || []
  } catch {
    templates.value = []
  }
}

async function loadDataSources() {
  if (!selectedTemplateId.value) {
    dataSources.value = []
    return
  }
  loading.value = true
  try {
    dataSources.value = await getDataSources(selectedTemplateId.value)
  } catch {
    dataSources.value = []
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingDataSource.value = null
  dialogVisible.value = true
}

function openEditDialog(ds: DataSourceDTO) {
  editingDataSource.value = ds
  dialogVisible.value = true
}

async function handleDelete(id: number) {
  try {
    await deleteDataSource(id)
    ElMessage.success(t('message.deleteSuccess'))
    loadDataSources()
  } catch {
    // error handled by interceptor
  }
}

async function handleTestConnection(ds: DataSourceDTO) {
  testResult.value = null
  testResultVisible.value = true
  try {
    testResult.value = await testConnection(ds.id)
  } catch (e: any) {
    testResult.value = {
      success: false,
      message: e?.response?.data?.message || e.message || t('dataSource.testFailed'),
    }
  }
}

function onSaved() {
  loadDataSources()
}

onMounted(() => {
  loadTemplates()
})
</script>

<style scoped>
.data-sources-page {
  padding: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0;
}
.header-actions {
  display: flex;
  align-items: center;
}
.pipeline-section {
  margin-top: 32px;
}
.pipeline-section h3 {
  margin-bottom: 16px;
}
</style>
