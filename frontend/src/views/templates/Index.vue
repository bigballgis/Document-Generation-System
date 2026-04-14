<template>
  <div class="templates-page">
    <div class="page-header">
      <h2>{{ $t('template.title') }}</h2>
      <div style="display: flex; gap: 8px;">
        <el-button @click="importDocxInput?.click()">
          {{ $t('template.importTemplate') }}
        </el-button>
        <input
          ref="importDocxInput"
          type="file"
          accept=".docx"
          style="display: none"
          @change="handleImportDocx"
        />
        <el-button @click="importConfigInput?.click()">
          {{ $t('template.importConfiguration') }}
        </el-button>
        <input
          ref="importConfigInput"
          type="file"
          accept=".json"
          style="display: none"
          @change="handleImportConfig"
        />
        <el-button type="primary" @click="wizardVisible = true">
          {{ $t('template.create') }}
        </el-button>
      </div>
    </div>

    <!-- Filters -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item :label="$t('common.search')">
          <el-input
            v-model="query.keyword"
            :placeholder="$t('template.searchPlaceholder')"
            clearable
            style="width: 240px"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('template.category')">
          <el-tree-select
            v-model="query.categoryId"
            :data="categoryTree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            :placeholder="$t('common.all')"
            clearable
            check-strictly
            style="width: 180px"
            @change="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('template.tags')">
          <el-select
            v-model="query.tagId"
            :placeholder="$t('common.all')"
            clearable
            style="width: 160px"
            @change="handleSearch"
          >
            <el-option
              v-for="tag in tagList"
              :key="tag.id"
              :label="tag.name"
              :value="tag.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('template.status')">
          <el-select
            v-model="query.status"
            :placeholder="$t('common.all')"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option label="Draft" value="DRAFT" />
            <el-option label="Pending Review" value="PENDING_REVIEW" />
            <el-option label="Reviewed" value="REVIEWED" />
            <el-option label="Active" value="ACTIVE" />
            <el-option label="Archived" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
          <el-button @click="resetFilters">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-card shadow="never" style="margin-top: 16px">
      <el-table :data="templates" v-loading="loading" stripe>
        <el-table-column prop="name" :label="$t('template.name')" min-width="180">
          <template #default="{ row }">
            <router-link :to="`/templates/${row.id}/workspace`" class="template-link">
              {{ row.name }}
            </router-link>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="$t('template.description')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" :label="$t('template.status')" width="140">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ $t(`template.status${statusLabel(row.status)}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" :label="$t('template.category')" width="130" />
        <el-table-column :label="$t('template.tags')" width="200">
          <template #default="{ row }">
            <el-tag
              v-for="tag in row.tags"
              :key="tag.id"
              size="small"
              type="info"
              style="margin-right: 4px; margin-bottom: 2px"
            >
              {{ tag.name }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" :label="$t('template.version')" width="90" align="center">
          <template #default="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" :label="$t('common.updatedAt')" width="170" />
        <el-table-column :label="$t('common.actions')" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="router.push(`/templates/${row.id}/workspace`)">
              {{ $t('workspace.workspaceAction') }}
            </el-button>
            <el-button link type="primary" size="small" @click="openEditDialog(row)">
              {{ $t('common.edit') }}
            </el-button>
            <el-button link type="primary" size="small" @click="handleClone(row)">
              {{ $t('common.clone') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'REVIEWED'"
              link type="success" size="small"
              @click="handleActivate(row)"
            >
              {{ $t('template.activate') }}
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              link type="warning" size="small"
              @click="handleArchive(row)"
            >
              {{ $t('template.archive') }}
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

    <!-- Create/Edit Dialog -->
    <TemplateFormDialog
      v-model:visible="formDialogVisible"
      :template-data="editingTemplate"
      :categories="categoryTree"
      :tags="tagList"
      @saved="onFormSaved"
    />

    <!-- Creation Wizard -->
    <TemplateCreationWizard
      v-model:visible="wizardVisible"
      :categories="categoryTree"
      :tags="tagList"
      @created="onWizardCreated"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTemplates, deleteTemplate, cloneTemplate, activateTemplate, archiveTemplate,
  getCategories, getTags,
  type TemplateDTO, type TemplateQuery, type CategoryDTO, type TagDTO,
} from '@/api/templates'
import TemplateFormDialog from './components/TemplateFormDialog.vue'
import TemplateCreationWizard from '@/views/template-workspace/components/TemplateCreationWizard.vue'
import { importDocx, importConfig } from '@/api/import-export'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const templates = ref<TemplateDTO[]>([])
const total = ref(0)
const categoryTree = ref<CategoryDTO[]>([])
const tagList = ref<TagDTO[]>([])
const formDialogVisible = ref(false)
const editingTemplate = ref<TemplateDTO | null>(null)
const wizardVisible = ref(false)

const importDocxInput = ref<HTMLInputElement | null>(null)
const importConfigInput = ref<HTMLInputElement | null>(null)

const query = reactive<TemplateQuery>({
  keyword: '',
  categoryId: null,
  tagId: null,
  status: '',
  page: 1,
  size: 10,
})

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

function statusTagType(status: string): TagType {
  const map: Record<string, TagType> = {
    DRAFT: 'info',
    PENDING_REVIEW: 'warning',
    REVIEWED: 'primary',
    ACTIVE: 'success',
    ARCHIVED: 'danger',
  }
  return map[status] || 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    DRAFT: 'Draft',
    PENDING_REVIEW: 'PendingReview',
    REVIEWED: 'Reviewed',
    ACTIVE: 'Active',
    ARCHIVED: 'Archived',
  }
  return map[status] || status
}

async function fetchTemplates() {
  loading.value = true
  try {
    const res = await getTemplates(query)
    templates.value = res.content
    total.value = res.totalElements
  } catch { /* handled by interceptor */ } finally {
    loading.value = false
  }
}

async function fetchFilters() {
  try {
    const [cats, tags] = await Promise.all([getCategories(), getTags()])
    categoryTree.value = cats
    tagList.value = tags
  } catch { /* ignore */ }
}

function handleSearch() {
  query.page = 1
  fetchTemplates()
}

function resetFilters() {
  query.keyword = ''
  query.categoryId = null
  query.tagId = null
  query.status = ''
  handleSearch()
}

function openEditDialog(row: TemplateDTO) {
  editingTemplate.value = { ...row }
  formDialogVisible.value = true
}

function onFormSaved() {
  formDialogVisible.value = false
  fetchTemplates()
}

function onWizardCreated(template: TemplateDTO) {
  router.push(`/templates/${template.id}/workspace`)
}

async function handleClone(row: TemplateDTO) {
  try {
    await cloneTemplate(row.id)
    ElMessage.success(t('template.cloneSuccess'))
    fetchTemplates()
  } catch { /* handled */ }
}

async function handleActivate(row: TemplateDTO) {
  try {
    await ElMessageBox.confirm(t('template.confirmActivate'), t('common.warning'))
    await activateTemplate(row.id)
    ElMessage.success(t('template.activateSuccess'))
    fetchTemplates()
  } catch { /* cancelled or error */ }
}

async function handleArchive(row: TemplateDTO) {
  try {
    await ElMessageBox.confirm(t('template.confirmArchive'), t('common.warning'))
    await archiveTemplate(row.id)
    ElMessage.success(t('template.archiveSuccess'))
    fetchTemplates()
  } catch { /* cancelled or error */ }
}

async function handleDelete(row: TemplateDTO) {
  try {
    await ElMessageBox.confirm(
      t('template.confirmDelete', { name: row.name }),
      t('confirm.deleteTitle'),
      { type: 'warning' },
    )
    await deleteTemplate(row.id)
    ElMessage.success(t('message.deleteSuccess'))
    fetchTemplates()
  } catch { /* cancelled or error */ }
}

async function handleImportDocx(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    await importDocx(file)
    ElMessage.success(t('message.importSuccess'))
    fetchTemplates()
  } catch { /* handled */ } finally {
    input.value = ''
  }
}

async function handleImportConfig(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    await importConfig(file)
    ElMessage.success(t('message.importSuccess'))
    fetchTemplates()
  } catch { /* handled */ } finally {
    input.value = ''
  }
}

onMounted(() => {
  fetchTemplates()
  fetchFilters()
})
</script>

<style scoped>
.templates-page {
  padding: 0;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
}
.filter-card :deep(.el-form-item) {
  margin-bottom: 0;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.template-link {
  color: var(--el-color-primary);
  text-decoration: none;
  font-weight: 500;
}
.template-link:hover {
  text-decoration: underline;
}
</style>
