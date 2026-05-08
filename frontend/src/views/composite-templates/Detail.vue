<template>
  <div class="composite-detail" v-loading="loading">
    <div class="page-header">
      <div class="header-left">
        <el-button @click="router.push('/composite-templates')">
          <el-icon><ArrowLeft /></el-icon> {{ $t('common.back') }}
        </el-button>
        <h2 v-if="template">{{ template.name }}</h2>
      </div>
      <div v-if="template" class="header-actions">
        <el-button type="primary" @click="router.push(`/composite-templates/${templateId}/editor`)">
          {{ $t('composite.assemblyEditor') }}
        </el-button>
        <el-button @click="handlePreview">{{ $t('common.preview') }}</el-button>
        <el-button @click="migrationDialogVisible = true">{{ $t('migration.title') }}</el-button>
      </div>
    </div>

    <template v-if="template">
      <el-card shadow="never" style="margin-bottom: 16px">
        <el-descriptions :column="3" border>
          <el-descriptions-item :label="$t('common.status')">
            <el-tag :type="statusTagType(template.status)" size="small">{{ template.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('template.version')">
            {{ template.version }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('common.createdAt')">
            {{ template.createdAt }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('common.updatedAt')">
            {{ template.updatedAt }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('common.description')" :span="3">
            {{ template.description || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <CoverageIndicator :template-id="templateId" />

      <el-tabs v-model="activeTab" type="border-card" style="margin-top: 16px">
        <el-tab-pane :label="$t('composite.segments')" name="segments">
          <el-table :data="assemblySegments" v-loading="configLoading" stripe>
            <el-table-column prop="name" :label="$t('common.name')" min-width="180" />
            <el-table-column :label="$t('common.type')" width="120">
              <template #default="{ row }">
                <el-tag v-if="row.segmentType" size="small" type="info">{{ row.segmentType }}</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column :label="$t('common.status')" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.enabled" size="small" type="success">{{ $t('common.enable') }}</el-tag>
                <el-tag v-else size="small" type="info">{{ $t('common.disable') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('workspace.editor.position')" width="80" align="center">
              <template #default="{ row }">{{ row.position + 1 }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </template>

    <MigrationDialog v-model:visible="migrationDialogVisible" @migrated="fetchTemplate" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getTemplate, type TemplateDTO } from '@/api/templates'
import { getAssemblyConfig, previewCompositeTemplate } from '@/api/composite-templates'
import type { AssemblySegmentEntry } from '@/types/segment'
import CoverageIndicator from './components/CoverageIndicator.vue'
import MigrationDialog from './components/MigrationDialog.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const templateId = Number(route.params.id)
const loading = ref(false)
const template = ref<TemplateDTO | null>(null)
const activeTab = ref('segments')
const assemblySegments = ref<AssemblySegmentEntry[]>([])
const configLoading = ref(false)
const migrationDialogVisible = ref(false)

type ElTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

function statusTagType(status: string): ElTagType {
  const map: Record<string, ElTagType> = { DRAFT: 'info', PENDING_REVIEW: 'warning', REVIEWED: 'primary', ACTIVE: 'success', ARCHIVED: 'danger' }
  return map[status] || 'info'
}

async function fetchTemplate() {
  loading.value = true
  try {
    template.value = await getTemplate(templateId)
  } catch {} finally {
    loading.value = false
  }
}

async function fetchAssemblyConfig() {
  configLoading.value = true
  try {
    const config = await getAssemblyConfig(templateId)
    assemblySegments.value = config.segments ?? []
  } catch {} finally {
    configLoading.value = false
  }
}

async function handlePreview() {
  try {
    await previewCompositeTemplate(templateId)
    ElMessage.success(t('common.preview'))
  } catch {}
}

onMounted(() => {
  fetchTemplate()
  fetchAssemblyConfig()
})
</script>

<style scoped>
.composite-detail { padding: 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.header-left { display: flex; align-items: center; gap: 12px; }
.header-left h2 { margin: 0; }
.header-actions { display: flex; gap: 8px; }
</style>

