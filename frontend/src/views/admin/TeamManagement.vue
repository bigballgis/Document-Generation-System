<template>
  <div>
    <div class="toolbar">
      <el-select
        v-if="isSuperAdmin"
        v-model="selectedTenantId"
        :placeholder="$t('admin.team.selectTenant')"
        filterable
        style="width: 260px; margin-right: 12px"
        @change="loadTeams"
      >
        <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <el-button type="primary" :disabled="!effectiveTenantId" @click="openDialog(null)">
        {{ $t('admin.team.create') }}
      </el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="name" :label="$t('admin.team.name')" min-width="140" />
      <el-table-column :label="$t('admin.team.approvalMode')" width="160">
        <template #default="{ row }">
          <el-tag size="small">{{ approvalModeLabel(row.approvalMode) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('admin.team.adMembershipGroup')" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.adGroupObjectId || '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('admin.team.adMakerGroup')" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.adMakerGroupObjectId || '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('admin.team.adCheckerGroup')" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.adCheckerGroupObjectId || '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.createdAt')" width="180">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="openDialog(row)">{{ $t('common.edit') }}</el-button>
          <el-popconfirm :title="$t('confirm.deleteMessage')" @confirm="handleDelete(row)">
            <template #reference>
              <el-button size="small" type="danger" link>{{ $t('common.delete') }}</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? $t('admin.team.edit') : $t('admin.team.create')"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="200px">
        <el-form-item :label="$t('admin.team.name')" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="$t('admin.team.description')" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="$t('admin.team.approvalMode')" prop="approvalMode">
          <el-select v-model="form.approvalMode" style="width: 100%" @change="onApprovalModeChange">
            <el-option :label="$t('admin.team.modeCrossReview')" value="CROSS_REVIEW" />
            <el-option :label="$t('admin.team.modeMakerChecker')" value="MAKER_CHECKER" />
          </el-select>
        </el-form-item>
        <template v-if="form.approvalMode === 'CROSS_REVIEW'">
          <el-form-item :label="$t('admin.team.adMembershipGroup')" prop="adGroupObjectId">
            <el-input
              v-model="form.adGroupObjectId"
              :placeholder="$t('admin.team.adGroupPlaceholder')"
            />
            <div class="hint">{{ $t('admin.team.hintCrossReviewAd') }}</div>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item :label="$t('admin.team.adMakerGroup')" prop="adMakerGroupObjectId">
            <el-input v-model="form.adMakerGroupObjectId" :placeholder="$t('admin.team.adGroupPlaceholder')" />
          </el-form-item>
          <el-form-item :label="$t('admin.team.adCheckerGroup')" prop="adCheckerGroupObjectId">
            <el-input v-model="form.adCheckerGroupObjectId" :placeholder="$t('admin.team.adGroupPlaceholder')" />
          </el-form-item>
          <div class="hint">{{ $t('admin.team.hintMakerCheckerAd') }}</div>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getTenants, type TenantDTO } from '@/api/admin'
import {
  listTeams,
  createTeam,
  updateTeam,
  deleteTeam,
  type TeamDTO,
  type CreateTeamRequest,
  type UpdateTeamRequest,
} from '@/api/teams'

const { t } = useI18n()
const userStore = useUserStore()

const isSuperAdmin = computed(() => userStore.userRole === 'SUPER_ADMIN')
const effectiveTenantId = computed(() =>
  isSuperAdmin.value ? selectedTenantId.value : userStore.userInfo?.tenantId ?? null,
)

const loading = ref(false)
const saving = ref(false)
const list = ref<TeamDTO[]>([])
const tenants = ref<TenantDTO[]>([])
const selectedTenantId = ref<number | null>(null)

const dialogVisible = ref(false)
const editing = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  description: '',
  approvalMode: 'CROSS_REVIEW' as 'CROSS_REVIEW' | 'MAKER_CHECKER',
  adGroupObjectId: '',
  adMakerGroupObjectId: '',
  adCheckerGroupObjectId: '',
})

const rules: FormRules = {
  name: [{ required: true, message: t('validation.required'), trigger: 'blur' }],
  approvalMode: [{ required: true, message: t('validation.required'), trigger: 'change' }],
}

function approvalModeLabel(mode?: string) {
  if (mode === 'MAKER_CHECKER') return t('admin.team.modeMakerChecker')
  return t('admin.team.modeCrossReview')
}

function formatDate(iso?: string) {
  return iso ? new Date(iso).toLocaleString() : '—'
}

function onApprovalModeChange() {
  if (form.approvalMode === 'CROSS_REVIEW') {
    form.adMakerGroupObjectId = ''
    form.adCheckerGroupObjectId = ''
  } else {
    form.adGroupObjectId = ''
  }
}

function openDialog(row: TeamDTO | null) {
  editing.value = !!row
  editingId.value = row?.id ?? null
  if (row) {
    form.name = row.name
    form.description = row.description || ''
    form.approvalMode = (row.approvalMode as typeof form.approvalMode) || 'CROSS_REVIEW'
    form.adGroupObjectId = row.adGroupObjectId || ''
    form.adMakerGroupObjectId = row.adMakerGroupObjectId || ''
    form.adCheckerGroupObjectId = row.adCheckerGroupObjectId || ''
  } else {
    form.name = ''
    form.description = ''
    form.approvalMode = 'CROSS_REVIEW'
    form.adGroupObjectId = ''
    form.adMakerGroupObjectId = ''
    form.adCheckerGroupObjectId = ''
  }
  dialogVisible.value = true
}

async function loadTenants() {
  if (!isSuperAdmin.value) return
  try {
    const res = await getTenants({ page: 0, size: 200 })
    tenants.value = res.content || []
    if (tenants.value.length && selectedTenantId.value == null) {
      selectedTenantId.value = tenants.value[0].id
    }
  } catch {
    tenants.value = []
  }
}

async function loadTeams() {
  const tid = effectiveTenantId.value
  if (!tid) {
    list.value = []
    return
  }
  loading.value = true
  try {
    list.value = await listTeams(tid)
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  const tid = effectiveTenantId.value
  if (!tid) return
  await formRef.value?.validate().catch(() => Promise.reject())
  saving.value = true
  try {
    if (editing.value && editingId.value != null) {
      const body: UpdateTeamRequest = {
        name: form.name,
        description: form.description,
        approvalMode: form.approvalMode,
        adGroupObjectId: form.approvalMode === 'CROSS_REVIEW' ? form.adGroupObjectId || undefined : '',
        adMakerGroupObjectId: form.approvalMode === 'MAKER_CHECKER' ? form.adMakerGroupObjectId || undefined : '',
        adCheckerGroupObjectId: form.approvalMode === 'MAKER_CHECKER' ? form.adCheckerGroupObjectId || undefined : '',
      }
      await updateTeam(tid, editingId.value, body)
    } else {
      const body: CreateTeamRequest = {
        name: form.name,
        description: form.description || undefined,
        approvalMode: form.approvalMode,
        adGroupObjectId: form.approvalMode === 'CROSS_REVIEW' ? form.adGroupObjectId || undefined : undefined,
        adMakerGroupObjectId:
          form.approvalMode === 'MAKER_CHECKER' ? form.adMakerGroupObjectId || undefined : undefined,
        adCheckerGroupObjectId:
          form.approvalMode === 'MAKER_CHECKER' ? form.adCheckerGroupObjectId || undefined : undefined,
      }
      await createTeam(tid, body)
    }
    ElMessage.success(t('message.updateSuccess'))
    dialogVisible.value = false
    loadTeams()
  } catch {
    /* error surfaced by interceptor */
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: TeamDTO) {
  const tid = effectiveTenantId.value
  if (!tid) return
  try {
    await deleteTeam(tid, row.id)
    ElMessage.success(t('message.deleteSuccess'))
    loadTeams()
  } catch {
    /* */
  }
}

watch(effectiveTenantId, () => loadTeams())

onMounted(async () => {
  await loadTenants()
  await loadTeams()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
  margin-top: 4px;
}
</style>
