<template>
  <div class="review-publish-tab">
    <!-- Status Badge -->
    <div class="status-section">
      <el-tag :type="statusTagType[store.templateStatus]" size="large">
        {{ t(`workspace.reviewPublish.status.${statusKeyMap[store.templateStatus]}`) }}
      </el-tag>
    </div>

    <!-- Submit for Review -->
    <el-button v-if="canSubmitReview" type="primary" @click="submitDialogVisible = true" style="margin-bottom: 16px">
      {{ t('workspace.reviewPublish.submitForReview') }}
    </el-button>

    <!-- Auto-activation failed alert -->
    <el-alert
      v-if="autoActivationFailed"
      type="error"
      :title="t('workspace.reviewPublish.autoActivationFailed')"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
    />

    <!-- Ready to Publish -->
    <el-alert
      v-if="allReviewsApproved && store.templateStatus === 'PENDING_REVIEW'"
      type="success"
      :title="t('workspace.reviewPublish.readyToPublish')"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
    />

    <!-- Review Rejected -->
    <el-alert
      v-if="hasRejectedReview"
      type="error"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
    >
      <template #title>{{ t('workspace.reviewPublish.reviewRejected') }}</template>
      <p v-if="rejectedReview?.reason">{{ rejectedReview.reason }}</p>
      <p v-if="rejectedReview?.comment">{{ rejectedReview.comment }}</p>
      <el-button size="small" @click="handleReviseResubmit">{{ t('workspace.reviewPublish.reviseResubmit') }}</el-button>
    </el-alert>

    <!-- Review Table -->
    <template v-if="showReviewTable">
      <el-table :data="paginatedReviews" stripe style="margin-bottom: 12px">
        <el-table-column :label="t('workspace.reviewPublish.reviewer')" min-width="120">
          <template #default="{ row }">{{ row.reviewerName || `User #${row.reviewerId}` }}</template>
        </el-table-column>
        <el-table-column :label="t('workspace.testing.status')" width="160">
          <template #default="{ row }">
            <el-tag :type="reviewStatusTagType[row.status]" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="level" :label="t('workspace.reviewPublish.reviewLevel')" width="120" />
        <el-table-column prop="comment" :label="t('workspace.reviewPublish.comment')" min-width="200" />
        <el-table-column :label="t('workspace.reviewPublish.suggestions')" min-width="200">
          <template #default="{ row }">
            <ul v-if="row.suggestions?.length" style="margin: 0; padding-left: 16px">
              <li v-for="(s, i) in row.suggestions" :key="i">{{ s }}</li>
            </ul>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('common.createdAt')" width="170" />
        <el-table-column prop="updatedAt" :label="t('workspace.reviewPublish.completedAt')" width="170" />
      </el-table>
      <el-pagination
        v-model:current-page="reviewPage"
        :page-size="reviewPageSize"
        :total="store.reviews.length"
        layout="prev, pager, next"
        style="justify-content: center"
      />
    </template>

    <!-- Manual Activate -->
    <el-button
      v-if="showActivateButton"
      type="success"
      :loading="activating"
      @click="handleActivate"
      style="margin-top: 16px"
    >
      {{ t('workspace.reviewPublish.activate') }}
    </el-button>

    <!-- API Endpoint Info -->
    <ApiEndpointInfo
      v-if="showApiEndpointInfo"
      :template-id="store.templateId"
      :api-keys="apiKeys"
      :curl-example="curlExample"
      :api-keys-loading="apiKeysLoading"
      :creating-api-key="creatingApiKey"
      @copy="copyToClipboard"
      @create-api-key="handleCreateApiKey"
    />

    <!-- Submit Review Dialog -->
    <SubmitReviewDialog
      v-model:visible="submitDialogVisible"
      @submit="handleSubmitReview"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { submitForReview, getApiKeys, createApiKey } from '@/api/admin'
import { activateTemplate, submitReview } from '@/api/templates'
import type { ApiKeyDTO } from '@/api/admin'
import ApiEndpointInfo from './ApiEndpointInfo.vue'
import SubmitReviewDialog from './SubmitReviewDialog.vue'

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

const reviewsLoaded = ref(false)
const submitDialogVisible = ref(false)
const activating = ref(false)
const apiKeys = ref<ApiKeyDTO[]>([])
const apiKeysLoading = ref(false)
const creatingApiKey = ref(false)
const reviewPage = ref(1)
const reviewPageSize = ref(10)

type TagType = 'info' | 'warning' | 'primary' | 'success' | 'danger'
const statusTagType: Record<string, TagType> = {
  DRAFT: 'info', PENDING_REVIEW: 'warning', REVIEWED: 'primary', ACTIVE: 'success', ARCHIVED: 'danger',
}
const statusKeyMap: Record<string, string> = {
  DRAFT: 'draft', PENDING_REVIEW: 'pendingReview', REVIEWED: 'reviewed', ACTIVE: 'active', ARCHIVED: 'archived',
}
const reviewStatusTagType: Record<string, TagType> = {
  PENDING: 'info', APPROVED: 'success', CONDITIONAL_APPROVED: 'warning', REJECTED: 'danger',
}

const canSubmitReview = computed(() => store.templateStatus === 'DRAFT' || store.templateStatus === 'REVIEWED')
const showReviewTable = computed(() => ['PENDING_REVIEW', 'REVIEWED', 'ACTIVE'].includes(store.templateStatus))
const allReviewsApproved = computed(() => {
  if (store.reviews.length === 0) return false
  return store.reviews.every(r => r.status === 'APPROVED' || r.status === 'CONDITIONAL_APPROVED')
})
const hasRejectedReview = computed(() => store.reviews.some(r => r.status === 'REJECTED'))
const rejectedReview = computed(() => store.reviews.find(r => r.status === 'REJECTED') ?? null)
const showActivateButton = computed(() => store.templateStatus === 'REVIEWED')
const showApiEndpointInfo = computed(() => store.templateStatus === 'ACTIVE')
const autoActivationFailed = computed(() => store.templateStatus === 'REVIEWED' && allReviewsApproved.value)
const paginatedReviews = computed(() => {
  const start = (reviewPage.value - 1) * reviewPageSize.value
  return store.reviews.slice(start, start + reviewPageSize.value)
})

async function loadReviewsIfNeeded() {
  if (!reviewsLoaded.value) {
    await store.refreshReviews()
    reviewsLoaded.value = true
  }
  if (store.templateStatus === 'ACTIVE') {
    await loadApiKeys()
  }
}

onMounted(() => { loadReviewsIfNeeded() })

async function handleSubmitReview(reviewerIds: number[], reviewLevel: number) {
  try {
    await submitForReview(store.templateId, { reviewerIds, reviewLevel })
    await submitReview(store.templateId)
    await Promise.all([store.refreshTemplate(), store.refreshReviews()])
    submitDialogVisible.value = false
    ElMessage.success(t('workspace.reviewPublish.submitSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('workspace.reviewPublish.submitFailed'))
  }
}

async function handleActivate() {
  if (store.coverage && store.coverage.overallCoveragePercent < 100) {
    try {
      await ElMessageBox.confirm(t('workspace.reviewPublish.lowCoverageWarning'), t('common.confirm'), { type: 'warning' })
    } catch { return }
  }
  activating.value = true
  try {
    await activateTemplate(store.templateId)
    await Promise.all([store.refreshTemplate(), store.refreshTransitions()])
    ElMessage.success(t('workspace.reviewPublish.activateSuccess'))
    await loadApiKeys()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('workspace.reviewPublish.activateFailed'))
  } finally {
    activating.value = false
  }
}

async function handleReviseResubmit() {
  await Promise.all([store.refreshTemplate(), store.refreshReviews()])
}

async function loadApiKeys() {
  apiKeysLoading.value = true
  try {
    const result = await getApiKeys({ page: 0, size: 10 })
    apiKeys.value = result.content
  } catch { /* silent */ }
  finally { apiKeysLoading.value = false }
}

async function handleCreateApiKey() {
  creatingApiKey.value = true
  try {
    await createApiKey({ name: `manual-${store.template?.name ?? 'template'}-${Date.now()}` })
    await loadApiKeys()
    ElMessage.success(t('workspace.reviewPublish.apiKeyCreated'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('workspace.reviewPublish.apiKeyFailed'))
  } finally {
    creatingApiKey.value = false
  }
}

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text)
  ElMessage.success(t('common.copied'))
}

const curlExample = computed(() => {
  const keyPrefix = apiKeys.value.find(k => k.enabled)?.keyPrefix ?? apiKeys.value[0]?.keyPrefix ?? 'YOUR_API_KEY'
  return `curl -X POST \\
  ${window.location.origin}/api/generate/${store.templateId} \\
  -H "X-API-Key: ${keyPrefix}" \\
  -H "Content-Type: application/json" \\
  -d '{"data": {}}'`
})
</script>

<style scoped>
.review-publish-tab { padding: 0; }
.status-section { margin-bottom: 16px; }
</style>
