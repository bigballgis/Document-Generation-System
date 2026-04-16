<template>
  <div class="approval-stage">
    <div class="approval-split">
      <!-- Left: Timeline -->
      <div class="timeline-panel">
        <!-- Submit review button (DRAFT + coverage 100%) -->
        <el-button
          v-if="canSubmitReview"
          type="primary"
          style="margin-bottom: 16px"
          @click="submitDialogVisible = true"
        >
          {{ t('workspace.approval.submitReview') }}
        </el-button>

        <!-- Timeline -->
        <el-empty v-if="store.reviews.length === 0" :description="t('workspace.approval.noReviews')" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="review in store.reviews"
            :key="review.id"
            :timestamp="review.updatedAt || review.createdAt"
            :type="timelineType(review.status)"
            placement="top"
          >
            <div class="timeline-content">
              <div class="timeline-header">
                <span class="reviewer-name">{{ review.reviewerName || `User #${review.reviewerId}` }}</span>
                <el-tag :type="statusTagType[review.status]" size="small">
                  {{ t(`workspace.approval.timeline.${statusKeyMap[review.status]}`) }}
                </el-tag>
              </div>
              <p v-if="review.comment" class="timeline-comment">{{ review.comment }}</p>
              <p v-if="review.reason" class="timeline-reason">{{ review.reason }}</p>
              <ul v-if="review.suggestions?.length" class="timeline-suggestions">
                <li v-for="(s, i) in review.suggestions" :key="i">{{ s }}</li>
              </ul>
            </div>
          </el-timeline-item>
        </el-timeline>

        <!-- Return to edit button (rejected) -->
        <el-button
          v-if="hasRejectedReview"
          type="warning"
          @click="handleReturnToEdit"
          :loading="returning"
        >
          {{ t('workspace.approval.returnToEdit') }}
        </el-button>
      </div>

      <!-- Right: Read-only preview -->
      <div class="preview-panel">
        <h4>{{ t('workspace.approval.previewTitle') }}</h4>
        <div v-if="previewUrl" class="preview-frame">
          <iframe :src="previewUrl" class="preview-iframe" />
        </div>
        <el-empty v-else :description="t('common.noData')" />
      </div>
    </div>

    <!-- Submit Review Dialog -->
    <SubmitReviewDialog
      v-model:visible="submitDialogVisible"
      @submit="handleSubmitReview"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { submitForReview } from '@/api/admin'
import { submitReview } from '@/api/templates'
import { previewCompositeTemplate } from '@/api/composite-templates'
import SubmitReviewDialog from './SubmitReviewDialog.vue'
import type { StageName } from '@/types/workspace'

defineProps<{
  readonly: boolean
}>()

const emit = defineEmits<{
  'stage-change': [stage: StageName]
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

const submitDialogVisible = ref(false)
const returning = ref(false)
const previewUrl = ref('')

type TagType = 'success' | 'warning' | 'danger' | 'info'
const statusTagType: Record<string, TagType> = {
  PENDING: 'info',
  APPROVED: 'success',
  CONDITIONAL_APPROVED: 'warning',
  REJECTED: 'danger',
}
const statusKeyMap: Record<string, string> = {
  PENDING: 'pending',
  APPROVED: 'approved',
  CONDITIONAL_APPROVED: 'conditional',
  REJECTED: 'rejected',
}

function timelineType(status: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
    PENDING: 'info',
    APPROVED: 'success',
    CONDITIONAL_APPROVED: 'warning',
    REJECTED: 'danger',
  }
  return map[status] || 'info'
}

const canSubmitReview = computed(() => {
  return store.templateStatus === 'DRAFT' && (store.coverage?.overallCoveragePercent ?? 0) >= 100
})

const hasRejectedReview = computed(() => {
  return store.reviews.some(r => r.status === 'REJECTED')
})

const allApproved = computed(() => {
  if (store.reviews.length === 0) return false
  return store.reviews.every(r => r.status === 'APPROVED' || r.status === 'CONDITIONAL_APPROVED')
})

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

async function handleReturnToEdit() {
  returning.value = true
  try {
    await Promise.all([store.refreshTemplate(), store.refreshReviews()])
    emit('stage-change', 'design')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  } finally {
    returning.value = false
  }
}

// Load preview
async function loadPreview() {
  try {
    const result = await previewCompositeTemplate(store.templateId)
    previewUrl.value = result.previewUrl
  } catch {
    // silent
  }
}

// Watch for status changes — auto-navigate on approval completion
watch(() => store.templateStatus, (newStatus) => {
  if ((newStatus === 'REVIEWED' || newStatus === 'ACTIVE') && allApproved.value) {
    emit('stage-change', 'publish')
  }
})

loadPreview()
</script>

<style scoped>
.approval-stage {
  padding: 16px 0;
}
.approval-split {
  display: flex;
  gap: 24px;
  min-height: 400px;
}
.timeline-panel {
  flex: 1;
  min-width: 0;
}
.preview-panel {
  width: 45%;
  flex-shrink: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
}
.preview-panel h4 {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}
.preview-frame {
  height: calc(100% - 40px);
}
.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
  min-height: 400px;
}
.timeline-content {
  padding: 4px 0;
}
.timeline-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.reviewer-name {
  font-weight: 600;
  font-size: 14px;
}
.timeline-comment {
  margin: 4px 0;
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.timeline-reason {
  margin: 4px 0;
  color: var(--el-color-danger);
  font-size: 13px;
}
.timeline-suggestions {
  margin: 4px 0;
  padding-left: 20px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
