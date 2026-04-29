<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        :placeholder="$t('common.search')"
        clearable
        style="width: 240px"
        @keyup.enter="loadData"
      />
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="username" :label="$t('admin.user.username')" min-width="120" />
      <el-table-column prop="email" :label="$t('admin.user.email')" min-width="180" />
      <el-table-column :label="$t('admin.user.role')" width="150">
        <template #default="{ row }">
          <el-tag size="small">{{ roleLabel(row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="tenantName" :label="$t('admin.user.tenant')" width="140" />
      <el-table-column prop="teamId" :label="$t('admin.user.team')" width="100" />
      <el-table-column :label="$t('admin.user.reviewLane')" width="110">
        <template #default="{ row }">
          {{ row.teamReviewLane || '—' }}
        </template>
      </el-table-column>
      <el-table-column :label="$t('admin.user.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
            {{ row.status === 'ACTIVE' ? $t('admin.user.statusActive') : $t('admin.user.statusLocked') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('admin.user.lastLogin')" width="180">
        <template #default="{ row }">{{ formatDate(row.lastLogin) }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="openRoleDialog(row)">
            {{ $t('admin.user.assignRole') }}
          </el-button>
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

    <el-dialog v-model="roleDialogVisible" :title="$t('admin.user.assignRole')" width="440px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item :label="$t('admin.user.role')">
          <el-select v-model="selectedRole" style="width: 100%">
            <el-option label="Super Admin" value="SUPER_ADMIN" />
            <el-option label="Tenant Admin" value="TENANT_ADMIN" />
            <el-option label="Team Admin" value="TEAM_ADMIN" />
            <el-option label="User" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('admin.user.team')">
          <el-input-number v-model="assignTeamId" :min="1" style="width: 100%" :controls="true" />
        </el-form-item>
        <el-form-item :label="$t('admin.user.reviewLane')">
          <el-select v-model="selectedLane" clearable style="width: 100%" :placeholder="$t('admin.user.reviewLaneHint')">
            <el-option :label="$t('admin.user.laneNone')" value="" />
            <el-option label="MAKER" value="MAKER" />
            <el-option label="CHECKER" value="CHECKER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleAssignRole">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getUsers, updateUser, type UserDTO } from '@/api/admin'

const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const list = ref<UserDTO[]>([])

const roleDialogVisible = ref(false)
const editingUser = ref<UserDTO | null>(null)
const selectedRole = ref('USER')
const assignTeamId = ref<number | undefined>(undefined)
const selectedLane = ref<string>('')

const roleLabels: Record<string, string> = {
  SUPER_ADMIN: 'Super Admin',
  TENANT_ADMIN: 'Tenant Admin',
  TEAM_ADMIN: 'Team Admin',
  USER: 'User',
}

function roleLabel(role: string) {
  return roleLabels[role] || role
}

function formatDate(iso?: string) {
  return iso ? new Date(iso).toLocaleString() : '-'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getUsers({ page: page.value - 1, size: pageSize.value, keyword: keyword.value || undefined })
    list.value = res.content || []
    total.value = res.totalElements || 0
  } catch {} finally {
    loading.value = false
  }
}

function openRoleDialog(user: UserDTO) {
  editingUser.value = user
  selectedRole.value = user.role
  assignTeamId.value = user.teamId ?? undefined
  selectedLane.value = user.teamReviewLane ?? ''
  roleDialogVisible.value = true
}

async function handleAssignRole() {
  if (!editingUser.value) return
  saving.value = true
  try {
    await updateUser(editingUser.value.id, {
      role: selectedRole.value,
      teamId: assignTeamId.value ?? null,
      teamReviewLane: selectedLane.value ? selectedLane.value : null,
    })
    ElMessage.success(t('message.updateSuccess'))
    roleDialogVisible.value = false
    loadData()
  } catch {} finally {
    saving.value = false
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

