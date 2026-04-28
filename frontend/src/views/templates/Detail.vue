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
        <el-button @click="handleExportDocx">{{ $t('template.exportDocx') }}</el-button>
        <el-button @click="handleExportConfig">{{ $t('template.exportConfig') }}</el-button>
        <el-button
          v-if="availableTransitions.includes('IN_TEST')"
          type="warning"
          @click="handleSubmitToTest"
        >
          {{ $t('template.submitToTest') }}
        </el-button>
        <el-button
          v-if="availableTransitions.includes('ACTIVE')"
          type="success"
          @click="handleActivate"
        >
          {{ $t('template.activate') }}
        </el-button>
        <el-button
          v-if="template.status === 'ACTIVE'"
          type="success"
          @click="generateDialogVisible = true"
        >
          {{ $t('document.generate') }}
        </el-button>
        <el-button
          v-if="availableTransitions.includes('ARCHIVED')"
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
          <div style="margin-bottom: 12px;">
            <el-button :loading="scanLoading" @click="handleScanVariables">
              {{ $t('template.scanVariables') }}
            </el-button>
          </div>
          <VariableManagement :template-id="template.id" />
        </el-tab-pane>
        <el-tab-pane :label="$t('template.coverage')" name="coverage">
          <div style="margin-bottom: 12px;">
            <el-button @click="handleExportCoverageReport">
              {{ $t('template.exportCoverageReport') }}
            </el-button>
          </div>
          <CoveragePanel :template-id="template.id" />
        </el-tab-pane>
        <el-tab-pane :label="$t('test.title')" name="tests">
          <div style="margin-bottom: 12px; display: flex; gap: 8px;">
            <el-button @click="handleExportTestCases">{{ $t('test.exportCases') }}</el-button>
            <el-button @click="testCaseFileInput?.click()">{{ $t('test.importCases') }}</el-button>
            <input
              ref="testCaseFileInput"
              type="file"
              accept=".json"
              style="display: none"
              @change="handleImportTestCases"
            />
          </div>
          <TestCaseManagement ref="testCaseMgmtRef" :template-id="template.id" />
        </el-tab-pane>
        <el-tab-pane :label="$t('schedule.title')" name="schedule">
          <ScheduledTaskManagement :template-id="template.id" />
        </el-tab-pane>
        <el-tab-pane :label="$t('watermark.title') + ' & ' + $t('security.title')" name="watermark">
          <WatermarkSecurityConfig :template-id="template.id" />
        </el-tab-pane>
        <el-tab-pane :label="$t('webhook.title')" name="webhooks">
          <WebhookPanel :template-id="template.id" />
        </el-tab-pane>
        <el-tab-pane :label="$t('review.title')" name="reviews">
          <div style="margin-bottom: 12px; display: flex; gap: 8px;">
            <el-button type="primary" @click="submitReviewDialogVisible = true">
              {{ $t('review.submit') }}
            </el-button>
            <el-button @click="handleReviewInEditor">
              {{ $t('review.reviewInEditor') }}
            </el-button>
          </div>
          <el-table :data="reviews" v-loading="reviewsLoading" stripe>
            <el-table-column prop="reviewerName" :label="$t('review.reviewer')" min-width="120" />
            <el-table-column prop="status" :label="$t('common.status')" width="140">
              <template #default="{ row }">
                <el-tag :type="reviewStatusTagType(row.status)" size="small">
                  {{ $t(`review.status${reviewStatusLabel(row.status)}`) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="level" :label="$t('review.level')" width="120">
              <template #default="{ row }">
                {{ row.level === 'INITIAL' ? $t('review.levelInitial') : $t('review.levelFinal') }}
              </template>
            </el-table-column>
            <el-table-column prop="comment" :label="$t('review.comment')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="suggestions" :label="$t('review.suggestions')" min-width="160">
              <template #default="{ row }">
                <span v-if="row.suggestions?.length">{{ row.suggestions.join(', ') }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" :label="$t('common.createdAt')" width="170" />
            <el-table-column :label="$t('common.actions')" width="160" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'PENDING'"
                  link type="primary" size="small"
                  @click="openConditionalApproveDialog(row)"
                >
                  {{ $t('review.conditionalApprove') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="display: flex; justify-content: flex-end; margin-top: 12px;">
            <el-pagination
              v-model:current-page="reviewPage"
              v-model:page-size="reviewSize"
              :total="reviewTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="fetchReviews"
              @current-change="fetchReviews"
            />
          </div>
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

    <!-- Generate Dialog -->
    <GenerateDialog
      v-model:visible="generateDialogVisible"
      :template-id="templateId"
      @generated="fetchTemplate"
    />

    <!-- Submit for Review Dialog -->
    <el-dialog
      v-model="submitReviewDialogVisible"
      :title="$t('review.submit')"
      width="480px"
    >
      <el-form label-width="140px">
        <el-form-item :label="$t('review.selectReviewers')">
          <el-input
            v-model="reviewerIdsInput"
            :placeholder="$t('review.selectReviewers')"
          />
        </el-form-item>
        <el-form-item :label="$t('review.level')">
          <el-select v-model="reviewLevel" style="width: 100%">
            <el-option :label="$t('review.levelInitial')" :value="1" />
            <el-option :label="$t('review.levelFinal')" :value="2" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitReviewDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitReviewLoading" @click="handleSubmitForReview">
          {{ $t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Conditional Approve Dialog -->
    <el-dialog
      v-model="conditionalApproveDialogVisible"
      :title="$t('review.conditionalApproveDialog')"
      width="480px"
    >
      <el-form label-width="120px">
        <el-form-item :label="$t('review.comment')">
          <el-input v-model="conditionalApproveComment" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item :label="$t('review.suggestions')">
          <div style="width: 100%">
            <div v-for="(_s, idx) in conditionalApproveSuggestions" :key="idx" style="display: flex; gap: 8px; margin-bottom: 8px;">
              <el-input v-model="conditionalApproveSuggestions[idx]" />
              <el-button type="danger" link @click="conditionalApproveSuggestions.splice(idx, 1)">{{ $t('common.delete') }}</el-button>
            </div>
            <el-button type="primary" link @click="conditionalApproveSuggestions.push('')">{{ $t('review.addSuggestion') }}</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="conditionalApproveDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="conditionalApproveLoading" @click="handleConditionalApprove">
          {{ $t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, type ComponentPublicInstance } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  getTemplate, cloneTemplate, activateTemplate, archiveTemplate,
  getCategories, getTags, submitTemplateToTest, getAvailableTransitions,
  scanVariables, exportCoverageReport,
  type TemplateDTO, type CategoryDTO, type TagDTO,
} from '@/api/templates'
import { exportDocx, exportConfig } from '@/api/import-export'
import { exportTestCases, importTestCases } from '@/api/market'
import TemplateFormDialog from './components/TemplateFormDialog.vue'
import VersionHistory from './components/VersionHistory.vue'
import VersionDiff from './components/VersionDiff.vue'
import VariableManagement from './components/VariableManagement.vue'
import CoveragePanel from './components/CoveragePanel.vue'
import TestCaseManagement from './components/TestCaseManagement.vue'
import ScheduledTaskManagement from './components/ScheduledTaskManagement.vue'
import WatermarkSecurityConfig from './components/WatermarkSecurityConfig.vue'
import GenerateDialog from './components/GenerateDialog.vue'
import WebhookPanel from './components/WebhookPanel.vue'
import {
  submitForReview, getTemplateReviews, conditionalApproveReview, getReviewEditorUrl,
  type ReviewDTO,
} from '@/api/admin'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const loading = ref(false)
const template = ref<TemplateDTO | null>(null)
const activeTab = ref('versions')
const editDialogVisible = ref(false)
const generateDialogVisible = ref(false)
const categoryTree = ref<CategoryDTO[]>([])
const tagList = ref<TagDTO[]>([])

const templateId = Number(route.params.id)

// Review state
const reviews = ref<ReviewDTO[]>([])
const reviewsLoading = ref(false)
const reviewPage = ref(1)
const reviewSize = ref(10)
const reviewTotal = ref(0)
const submitReviewDialogVisible = ref(false)
const submitReviewLoading = ref(false)
const reviewerIdsInput = ref('')
const reviewLevel = ref(1)
const conditionalApproveDialogVisible = ref(false)
const conditionalApproveLoading = ref(false)
const conditionalApproveComment = ref('')
const conditionalApproveSuggestions = ref<string[]>([])
const currentReviewId = ref<number>(0)

// State transitions
const availableTransitions = ref<string[]>([])

// Scan variables & test case import
const scanLoading = ref(false)
const testCaseFileInput = ref<HTMLInputElement | null>(null)
const testCaseMgmtRef = ref<ComponentPublicInstance<{ refreshTestCases: () => Promise<void> }> | null>(null)

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

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

async function fetchTemplate() {
  loading.value = true
  try {
    template.value = await getTemplate(templateId)
    fetchTransitions()
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

// Review helpers
type ReviewTagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

function reviewStatusTagType(status: string): ReviewTagType {
  const map: Record<string, ReviewTagType> = {
    PENDING: 'warning', APPROVED: 'success', CONDITIONAL_APPROVED: 'primary', REJECTED: 'danger',
  }
  return map[status] || 'info'
}

function reviewStatusLabel(status: string) {
  const map: Record<string, string> = {
    PENDING: 'Pending', APPROVED: 'Approved', CONDITIONAL_APPROVED: 'Conditional', REJECTED: 'Rejected',
  }
  return map[status] || status
}

async function fetchReviews() {
  reviewsLoading.value = true
  try {
    const res = await getTemplateReviews(templateId, { page: reviewPage.value - 1, size: reviewSize.value })
    reviews.value = res.content
    reviewTotal.value = res.totalElements
  } catch { /* handled */ } finally {
    reviewsLoading.value = false
  }
}

async function handleSubmitForReview() {
  const ids = reviewerIdsInput.value.split(',').map(s => Number(s.trim())).filter(n => !isNaN(n) && n > 0)
  if (!ids.length) {
    ElMessage.warning(t('review.selectReviewers'))
    return
  }
  submitReviewLoading.value = true
  try {
    await submitForReview(templateId, { reviewerIds: ids, reviewLevel: reviewLevel.value })
    ElMessage.success(t('review.submitSuccess'))
    submitReviewDialogVisible.value = false
    reviewerIdsInput.value = ''
    fetchReviews()
    fetchTemplate()
  } catch { /* handled */ } finally {
    submitReviewLoading.value = false
  }
}

function openConditionalApproveDialog(row: ReviewDTO) {
  currentReviewId.value = row.id
  conditionalApproveComment.value = ''
  conditionalApproveSuggestions.value = ['']
  conditionalApproveDialogVisible.value = true
}

async function handleConditionalApprove() {
  conditionalApproveLoading.value = true
  try {
    await conditionalApproveReview(currentReviewId.value, {
      comment: conditionalApproveComment.value || undefined,
      suggestions: conditionalApproveSuggestions.value.filter(s => s.trim()),
    })
    ElMessage.success(t('review.approveSuccess'))
    conditionalApproveDialogVisible.value = false
    fetchReviews()
  } catch { /* handled */ } finally {
    conditionalApproveLoading.value = false
  }
}

async function handleReviewInEditor() {
  try {
    const url = await getReviewEditorUrl(templateId)
    window.open(url, '_blank')
  } catch { /* handled */ }
}

async function fetchTransitions() {
  try {
    availableTransitions.value = await getAvailableTransitions(templateId)
  } catch { /* handled */ }
}

async function handleSubmitToTest() {
  try {
    await submitTemplateToTest(templateId)
    ElMessage.success(t('workspace.design.submitToTestSuccess'))
    fetchTemplate()
    fetchTransitions()
  } catch { /* handled */ }
}

async function handleExportDocx() {
  try {
    const blob = await exportDocx(templateId)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${template.value?.name || 'template'}.docx`
    a.click()
    URL.revokeObjectURL(url)
  } catch { /* handled */ }
}

async function handleExportConfig() {
  try {
    const blob = await exportConfig(templateId)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${template.value?.name || 'template'}-config.json`
    a.click()
    URL.revokeObjectURL(url)
  } catch { /* handled */ }
}

async function handleScanVariables() {
  scanLoading.value = true
  try {
    const vars = await scanVariables(templateId)
    ElMessage.success(t('template.scanVariablesSuccess', { count: vars.length }))
  } catch { /* handled */ } finally {
    scanLoading.value = false
  }
}

async function handleExportCoverageReport() {
  try {
    const blob = await exportCoverageReport(templateId) as unknown as Blob
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${template.value?.name || 'template'}-coverage.json`
    a.click()
    URL.revokeObjectURL(url)
  } catch { /* handled */ }
}

async function handleExportTestCases() {
  try {
    const json = await exportTestCases(templateId)
    const blob = new Blob([json], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${template.value?.name || 'template'}-test-cases.json`
    a.click()
    URL.revokeObjectURL(url)
  } catch { /* handled */ }
}

async function handleImportTestCases(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    const text = await file.text()
    await importTestCases(templateId, text)
    ElMessage.success(t('message.importSuccess'))
    await testCaseMgmtRef.value?.refreshTestCases?.()
  } catch { /* handled */ } finally {
    input.value = ''
  }
}

onMounted(() => {
  fetchTemplate()
  fetchFilters()
  fetchReviews()
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
