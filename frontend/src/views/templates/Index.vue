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
        <el-button type="success" @click="importZipInput?.click()">
          {{ $t('template.importCompositePackage') }}
        </el-button>
        <input
          ref="importZipInput"
          type="file"
          accept=".zip"
          style="display: none"
          @change="handleImportZip"
        />
        <el-button type="primary" @click="wizardVisible = true">
          {{ $t('template.create') }}
        </el-button>
      </div>
    </div>

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
            <el-option label="In testing" value="IN_TEST" />
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
        <el-table-column prop="updatedAt" :label="$t('common.updatedAt')" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.actions')" width="380" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleClone(row)">
              {{ $t('common.clone') }}
            </el-button>
            <el-button link type="primary" size="small" @click="router.push(`/templates/${row.id}/api`)">
              {{ $t('workspace.api.apiButton') }}
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              link type="primary" size="small"
              @click="
                router.push({
                  name: 'TemplateIntegrations',
                  params: { id: String(row.id) },
                  query: { tab: 'webhooks' },
                })
              "
            >
              {{ $t('workspace.publish.openIntegrations') }}
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

    <TemplateCreationWizard
      v-model:visible="wizardVisible"
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
  getTags,
  type TemplateDTO, type TemplateQuery, type TagDTO,
} from '@/api/templates'
import TemplateCreationWizard from '@/views/template-workspace/components/TemplateCreationWizard.vue'
import { importDocx, importConfig } from '@/api/import-export'
import { importCompositeFromZip } from '@/api/composite-templates'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const templates = ref<TemplateDTO[]>([])
const total = ref(0)
const tagList = ref<TagDTO[]>([])
const wizardVisible = ref(false)

const importDocxInput = ref<HTMLInputElement | null>(null)
const importConfigInput = ref<HTMLInputElement | null>(null)
const importZipInput = ref<HTMLInputElement | null>(null)

const query = reactive<TemplateQuery>({
  keyword: '',
  tagId: null,
  status: '',
  page: 1,
  size: 10,
})

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

function formatDateTime(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  const date = value instanceof Date ? value : new Date(value as any)
  if (Number.isNaN(date.getTime())) return String(value)

  const parts = new Intl.DateTimeFormat(undefined, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(date)

  const map = Object.fromEntries(parts.map((p) => [p.type, p.value]))
  return `${map.year}-${map.month}-${map.day} ${map.hour}:${map.minute}:${map.second}`
}

function statusTagType(status: string): TagType {
  const map: Record<string, TagType> = {
    DRAFT: 'info',
    IN_TEST: 'warning',
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
    IN_TEST: 'InTest',
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
  } catch {} finally {
    loading.value = false
  }
}

async function fetchFilters() {
  try {
    tagList.value = await getTags()
  } catch {}
}

function handleSearch() {
  query.page = 1
  fetchTemplates()
}

function resetFilters() {
  query.keyword = ''
  query.tagId = null
  query.status = ''
  handleSearch()
}

function onWizardCreated(template: TemplateDTO) {
  router.push(`/templates/${template.id}/workspace`)
}

async function handleClone(row: TemplateDTO) {
  try {
    await cloneTemplate(row.id)
    ElMessage.success(t('template.cloneSuccess'))
    fetchTemplates()
  } catch {}
}

async function handleActivate(row: TemplateDTO) {
  try {
    await ElMessageBox.confirm(t('template.confirmActivate'), t('common.warning'))
    await activateTemplate(row.id)
    ElMessage.success(t('template.activateSuccess'))
    fetchTemplates()
  } catch {}
}

async function handleArchive(row: TemplateDTO) {
  try {
    await ElMessageBox.confirm(t('template.confirmArchive'), t('common.warning'))
    await archiveTemplate(row.id)
    ElMessage.success(t('template.archiveSuccess'))
    fetchTemplates()
  } catch {}
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
  } catch {}
}

async function handleImportDocx(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    await importDocx(file)
    ElMessage.success(t('message.importSuccess'))
    fetchTemplates()
  } catch {} finally {
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
  } catch {} finally {
    input.value = ''
  }
}

async function handleImportZip(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    await importCompositeFromZip(file)
    ElMessage.success(t('message.importSuccess'))
    fetchTemplates()
  } catch {} finally {
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

