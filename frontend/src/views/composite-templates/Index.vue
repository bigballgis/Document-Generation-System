<template>
  <div class="composite-templates-page">
    <div class="page-header">
      <h2>{{ $t('composite.title') }}</h2>
      <el-button type="primary" @click="createDialogVisible = true">
        {{ $t('composite.create') }}
      </el-button>
    </div>

    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item :label="$t('common.search')">
          <el-input
            v-model="query.keyword"
            :placeholder="$t('composite.searchPlaceholder')"
            clearable
            style="width: 280px"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
          <el-button @click="resetFilters">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <el-table :data="templates" v-loading="loading" stripe>
        <el-table-column prop="name" :label="$t('composite.name')" min-width="200">
          <template #default="{ row }">
            <router-link :to="`/composite-templates/${row.id}`" class="template-link">
              {{ row.name }}
            </router-link>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="$t('common.description')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" :label="$t('common.status')" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('composite.segmentCount')" width="120" align="center">
          <template #default="{ row }">
            {{ row.version || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" :label="$t('common.updatedAt')" width="170" />
        <el-table-column :label="$t('common.actions')" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="$router.push(`/composite-templates/${row.id}`)">
              {{ $t('common.detail') }}
            </el-button>
            <el-button link type="primary" size="small" @click="$router.push(`/composite-templates/${row.id}/editor`)">
              {{ $t('composite.assemblyEditor') }}
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">
              {{ $t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchTemplates"
          @current-change="fetchTemplates"
        />
      </div>
    </el-card>

    <el-dialog v-model="createDialogVisible" :title="$t('composite.create')" width="500px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item :label="$t('composite.name')" prop="name">
          <el-input v-model="createForm.name" :placeholder="$t('composite.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('common.description')" prop="description">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getTemplates, deleteTemplate, type TemplateDTO, type TemplateQuery } from '@/api/templates'
import { createCompositeTemplate } from '@/api/composite-templates'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const templates = ref<TemplateDTO[]>([])
const total = ref(0)
const createDialogVisible = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()

const query = reactive<TemplateQuery>({
  keyword: '',
  status: '',
  page: 1,
  size: 10,
})

const createForm = reactive({
  name: '',
  description: '',
})

const createRules = {
  name: [{ required: true, message: () => t('validation.required', { field: t('composite.name') }), trigger: 'blur' }],
}

type ElTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

function statusTagType(status: string): ElTagType {
  const map: Record<string, ElTagType> = {
    DRAFT: 'info',
    PENDING_REVIEW: 'warning',
    REVIEWED: 'primary',
    ACTIVE: 'success',
    ARCHIVED: 'danger',
  }
  return map[status] || 'info'
}

async function fetchTemplates() {
  loading.value = true
  try {
    const res = await getTemplates({ ...query, page: query.page })
    templates.value = res.content
    total.value = res.totalElements
  } catch {} finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  fetchTemplates()
}

function resetFilters() {
  query.keyword = ''
  handleSearch()
}

async function handleCreate() {
  const form = createFormRef.value
  if (!form) return
  try {
    await form.validate()
  } catch { return }

  creating.value = true
  try {
    const result = await createCompositeTemplate({
      name: createForm.name,
      description: createForm.description || undefined,
    })
    ElMessage.success(t('message.createSuccess'))
    createDialogVisible.value = false
    createForm.name = ''
    createForm.description = ''
    router.push(`/composite-templates/${result.id}/editor`)
  } catch {} finally {
    creating.value = false
  }
}

async function handleDelete(row: TemplateDTO) {
  try {
    await ElMessageBox.confirm(
      t('composite.confirmDelete', { name: row.name }),
      t('confirm.deleteTitle'),
      { type: 'warning' },
    )
    await deleteTemplate(row.id)
    ElMessage.success(t('message.deleteSuccess'))
    fetchTemplates()
  } catch {}
}

onMounted(() => {
  fetchTemplates()
})
</script>

<style scoped>
.composite-templates-page { padding: 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; }
.filter-card :deep(.el-form-item) { margin-bottom: 0; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
.template-link { color: var(--el-color-primary); text-decoration: none; font-weight: 500; }
.template-link:hover { text-decoration: underline; }
</style>

