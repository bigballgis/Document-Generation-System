<template>
  <div v-loading="loading">
    <div style="margin-bottom: 12px">
      <el-button type="primary" size="small" @click="submitDialogVisible = true">
        {{ $t('review.submit') }}
      </el-button>
    </div>
    <el-table :data="reviews" stripe size="small">
      <el-table-column prop="segmentId" :label="$t('segment.name')" width="120" />
      <el-table-column prop="reviewerId" :label="$t('review.reviewer')" width="120" />
      <el-table-column prop="status" :label="$t('common.status')" width="120">
        <template #default="{ row }">
          <el-tag :type="reviewStatusType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="comment" :label="$t('review.comment')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="createdAt" :label="$t('common.createdAt')" width="170" />
      <el-table-column :label="$t('common.actions')" width="180">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING'" link type="success" size="small" @click="handleApprove(row)">
            {{ $t('review.approve') }}
          </el-button>
          <el-button v-if="row.status === 'PENDING'" link type="danger" size="small" @click="handleReject(row)">
            {{ $t('review.reject') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="submitDialogVisible" :title="$t('review.submit')" width="400px">
      <p>{{ $t('composite.submitReviewHint') }}</p>
      <template #footer>
        <el-button @click="submitDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitDialogVisible = false">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { approveSegmentReview, rejectSegmentReview } from '@/api/composite-templates'
import type { SegmentReview } from '@/types/segment'

defineProps<{ templateId: number }>()
const { t } = useI18n()

const loading = ref(false)
const reviews = ref<SegmentReview[]>([])
const submitDialogVisible = ref(false)

type ElTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

function reviewStatusType(status: string): ElTagType {
  const map: Record<string, ElTagType> = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }
  return map[status] || 'info'
}

async function handleApprove(row: SegmentReview) {
  try {
    await approveSegmentReview(row.id)
    ElMessage.success(t('review.approveSuccess'))
    row.status = 'APPROVED'
  } catch { /* handled */ }
}

async function handleReject(row: SegmentReview) {
  try {
    await rejectSegmentReview(row.id, { comment: '' })
    ElMessage.success(t('review.rejectSuccess'))
    row.status = 'REJECTED'
  } catch { /* handled */ }
}

onMounted(() => {
  // Reviews are loaded when a review exists; placeholder for now
  loading.value = false
})
</script>
