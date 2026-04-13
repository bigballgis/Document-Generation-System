<template>
  <div class="template-detail" v-loading="loading">
    <div class="page-header">
      <div class="header-left">
        <el-button @click="router.push('/templates')">
          <el-icon><ArrowLeft /></el-icon> {{ $t('common.back') }}
        </el-button>
        <h2 v-if="template">{{ template.name }}</h2>
      </div>
      <div v-if="template" class="header-actions">
        <el-button type="primary" @click="openEditor">
          {{ $t('editor.onlyoffice') }}
        </el-button>
        <el-button @click="openEditDialog">{{ $t('common.edit') }}</el-button>
        <el-button @click="handleClone">{{ $t('common.clone') }}</el-button>
        <el-button
          v-if="template.status === 'DRAFT' || template.status === 'REVIEWED'"
          type="success"
          @click="handleActivate"
        >
          {{ $t('template.activate') }}
        </el-button>
        <el-button
          v-if="template.status === 'ACTIVE'"
          type="warning"
          @click="handleArchive"
        >
          {{ $t('template.archive') }}
        </el-button>
      </div>
    </div>

    <template v-if="template">
      <!-- Basic Info Card -->
      <el-card shadow="never" style="margin-bottom: 16px">
        <el-descriptions :column="3" border>
          <el-descriptions-item :label="$t('template.status')">
            <el-tag :type="statusTagType(template.status)" size="small">
              {{ $t(`template.status${statusLabel(template.status)}`) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('template.category')">
            {{ template.categoryName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('template.currentVersion')">
            v{{ template.version }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('template.outputFormat')">
            {{ template.outputFormat || 'WORD' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('common.createdAt')">
            {{ template.createdAt }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('common.updatedAt')">
            {{ template.updatedAt }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('template.description')" :span="3">
            {{ template.description || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('template.tags')" :span="3">
            <el-tag
              v-for="tag in template.tags"
              :key="tag.id"
              size="small"
              type="info"
              style="margin-right: 4px"
            >{{ tag.name }}</el-tag>
            <span v-if="!template.tags?.length">-</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- Tabs -->
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane :label="$t('template.versionHistory')" name="versions">
          <VersionHistory :template-id="template.id" />
        </el-tab-pane>
        <el-tab-pane :label="$t('template.versionCompare')" name="diff">
          <VersionDiff :template-id="template.id" />
        </el-tab-pane>
        <el-tab-pane :label="$t('template.variableManagement')" name="variables">
          <VariableManagement :template-id="template.id" />
        </el-tab-pane>
        <el-tab-pane :label="$t('template.coverage')" name="coverage">
          <CoveragePanel :template-id="template.id" />
        </el-tab-pane>
        <el-tab-pane :label="$t('test.title')" name="tests">
          <TestCaseManagement :template-id="template.id" />
        </el-tab-pane>
        <el-tab-pane :label="$t('schedule.title')" name="schedule">
          <ScheduledTaskManagement :template-id="template.id" />
        </el-tab-pane>
        <el-tab-pane :label="$t('watermark.title') + ' & ' + $t('security.title')" name="watermark">
          <WatermarkSecurityConfig :template-id="template.id" />
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- Edit Dialog -->
    <TemplateFormDialog
      v-model:visible="editDialogVisible"
      :template-data="template"
      :categories="categoryTree"
      :tags="tagList"
      @saved="onEditSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  getTemplate, cloneTemplate, activateTemplate, archiveTemplate,
  getCategories, getTags,
  type TemplateDTO, type CategoryDTO, type TagDTO,
} from '@/api/templates'
import TemplateFormDialog from './components/TemplateFormDialog.vue'
import VersionHistory from './components/VersionHistory.vue'
import VersionDiff from './components/VersionDiff.vue'
import VariableManagement from './components/VariableManagement.vue'
import CoveragePanel from './components/CoveragePanel.vue'
import TestCaseManagement from './components/TestCaseManagement.vue'
import ScheduledTaskManagement from './components/ScheduledTaskManagement.vue'
import WatermarkSecurityConfig from './components/WatermarkSecurityConfig.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const loading = ref(false)
const template = ref<TemplateDTO | null>(null)
const activeTab = ref('versions')
const editDialogVisible = ref(false)
const categoryTree = ref<CategoryDTO[]>([])
const tagList = ref<TagDTO[]>([])

const templateId = Number(route.params.id)

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

function statusTagType(status: string): TagType {
  const map: Record<string, TagType> = {
    DRAFT: 'info', PENDING_REVIEW: 'warning', REVIEWED: 'primary', ACTIVE: 'success', ARCHIVED: 'danger',
  }
  return map[status] || 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    DRAFT: 'Draft', PENDING_REVIEW: 'PendingReview', REVIEWED: 'Reviewed', ACTIVE: 'Active', ARCHIVED: 'Archived',
  }
  return map[status] || status
}

async function fetchTemplate() {
  loading.value = true
  try {
    template.value = await getTemplate(templateId)
  } catch { /* handled */ } finally {
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

function openEditDialog() {
  editDialogVisible.value = true
}

function openEditor() {
  router.push(`/templates/${templateId}/editor`)
}

function onEditSaved() {
  editDialogVisible.value = false
  fetchTemplate()
}

async function handleClone() {
  try {
    await cloneTemplate(templateId)
    ElMessage.success(t('template.cloneSuccess'))
  } catch { /* handled */ }
}

async function handleActivate() {
  try {
    await ElMessageBox.confirm(t('template.confirmActivate'), t('common.warning'))
    await activateTemplate(templateId)
    ElMessage.success(t('template.activateSuccess'))
    fetchTemplate()
  } catch { /* cancelled */ }
}

async function handleArchive() {
  try {
    await ElMessageBox.confirm(t('template.confirmArchive'), t('common.warning'))
    await archiveTemplate(templateId)
    ElMessage.success(t('template.archiveSuccess'))
    fetchTemplate()
  } catch { /* cancelled */ }
}

onMounted(() => {
  fetchTemplate()
  fetchFilters()
})
</script>

<style scoped>
.template-detail {
  padding: 0;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-left h2 {
  margin: 0;
}
.header-actions {
  display: flex;
  gap: 8px;
}
</style>
