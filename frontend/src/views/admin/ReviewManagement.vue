<template>
  <div>
    <div class="toolbar">
      <el-radio-group v-model="statusFilter" @change="loadData">
        <el-radio-button value="">{{ $t('common.all') }}</el-radio-button>
        <el-radio-button value="PENDING">{{ $t('review.statusPending') }}</el-radio-button>
        <el-radio-button value="APPROVED">{{ $t('review.statusApproved') }}</el-radio-button>
        <el-radio-button value="CONDITIONAL_APPROVED">{{ $t('review.statusConditional') }}</el-radio-button>
        <el-radio-button value="REJECTED">{{ $t('review.statusRejected') }}</el-radio-button>
      </el-radio-group>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="templateName" :label="$t('admin.permission.template')" min-width="160" />
      <el-table-column prop="reviewerName" :label="$t('review.reviewer')" width="140" />
      <el-table-column :label="$t('common.status')" width="160">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('review.level')" width="120">
        <template #default="{ row }">
          {{ row.level === 'INITIAL' ? $t('review.levelInitial') : $t('review.levelFinal') }}
        </template>
      </el-table-column>
      <el-table-column prop="comment" :label="$t('review.comment')" min-width="200" show-overflow-tooltip />
      <el-table-column :label="$t('common.updatedAt')" width="180">
        <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="200" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button size="small" type="success" link @click="openActionDialog(row, 'approve')">
              {{ $t('review.approve') }}
            </el-button>
            <el-button size="small" type="danger" link @click="openActionDialog(row, 'reject')">
              {{ $t('review.reject') }}
            </el-button>
          </template>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50]"
        @current-change="loadData"
        @size-change="loadData"
      />
    </div>

    <!-- Approve/Reject Dialog -->
    <el-dialog v-model="actionDialogVisible" :title="actionType === 'approve' ? $t('review.approve') : $t('review.reject')" width="450px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item :label="actionType === 'approve' ? $t('review.comment') : $t('review.rejectReason')">
          <el-input v-model="actionText" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button :type="actionType === 'approve' ? 'success' : 'danger'" :loading="saving" @click="handleAction">
          {{ $t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getReviews, approveReview, rejectReview, type ReviewDTO } from '@/api/admin'

const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const statusFilter = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const list = ref<ReviewDTO[]>([])

const actionDialogVisible = ref(false)
const actionType = ref<'approve' | 'reject'>('approve')
const actionReview = ref<ReviewDTO | null>(null)
const actionText = ref('')

type TagType = 'warning' | 'success' | 'primary' | 'danger' | 'info'

function statusTagType(status: string): TagType {
  const map: Record<string, TagType> = { PENDING: 'warning', APPROVED: 'success', CONDITIONAL_APPROVED: 'primary', REJECTED: 'danger' }
  return map[status] || 'info'
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    PENDING: t('review.statusPending'),
    APPROVED: t('review.statusApproved'),
    CONDITIONAL_APPROVED: t('review.statusConditional'),
    REJECTED: t('review.statusRejected'),
  }
  return map[status] || status
}

function formatDate(iso: string) {
  return iso ? new Date(iso).toLocaleString() : ''
}

async function loadData() {
  loading.value = true
  try {
    const res = await getReviews({ page: page.value - 1, size: pageSize.value, status: statusFilter.value || undefined })
    list.value = res.content || []
    total.value = res.totalElements || 0
  } catch { /* interceptor */ } finally {
    loading.value = false
  }
}

function openActionDialog(review: ReviewDTO, type: 'approve' | 'reject') {
  actionReview.value = review
  actionType.value = type
  actionText.value = ''
  actionDialogVisible.value = true
}

async function handleAction() {
  if (!actionReview.value) return
  saving.value = true
  try {
    if (actionType.value === 'approve') {
      await approveReview(actionReview.value.id, actionText.value)
      ElMessage.success(t('review.approveSuccess'))
    } else {
      await rejectReview(actionReview.value.id, actionText.value)
      ElMessage.success(t('review.rejectSuccess'))
    }
    actionDialogVisible.value = false
    loadData()
  } catch { /* interceptor */ } finally {
    saving.value = false
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.text-muted {
  color: var(--el-text-color-secondary);
}
</style>
