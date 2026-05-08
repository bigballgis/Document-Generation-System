<template>
  <div>
    <div class="toolbar">
      <el-select
        v-model="selectedTemplateId"
        :placeholder="$t('admin.permission.template')"
        filterable
        clearable
        style="width: 280px"
        @change="loadPermissions"
      >
        <el-option v-for="tpl in templates" :key="tpl.id" :label="tpl.name" :value="tpl.id" />
      </el-select>
      <el-button type="primary" :disabled="!selectedTemplateId" @click="grantDialogVisible = true">
        {{ $t('admin.permission.grant') }}
      </el-button>
    </div>

    <el-table v-loading="loading" :data="permissions" stripe>
      <el-table-column prop="granteeName" :label="$t('admin.permission.grantee')" min-width="140" />
      <el-table-column :label="$t('admin.permission.granteeType')" width="120">
        <template #default="{ row }">
          <el-tag :type="row.granteeType === 'USER' ? 'primary' : 'success'" size="small">
            {{ row.granteeType === 'USER' ? $t('admin.permission.granteeUser') : $t('admin.permission.granteeTeam') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('admin.permission.permissionType')" width="140">
        <template #default="{ row }">
          <el-tag size="small">{{ permLabel(row.permissionType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.createdAt')" width="180">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="120" fixed="right">
        <template #default="{ row }">
          <el-popconfirm :title="$t('confirm.deleteMessage')" @confirm="handleRevoke(row.id)">
            <template #reference>
              <el-button size="small" type="danger" link>{{ $t('admin.permission.revoke') }}</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="grantDialogVisible" :title="$t('admin.permission.grant')" width="450px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item :label="$t('admin.permission.granteeType')">
          <el-radio-group v-model="grantForm.granteeType">
            <el-radio value="USER">{{ $t('admin.permission.granteeUser') }}</el-radio>
            <el-radio value="TEAM">{{ $t('admin.permission.granteeTeam') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('admin.permission.grantee')">
          <el-input-number v-model="grantForm.granteeId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('admin.permission.permissionType')">
          <el-select v-model="grantForm.permissionType" style="width: 100%">
            <el-option :label="$t('admin.permission.permView')" value="VIEW" />
            <el-option :label="$t('admin.permission.permEdit')" value="EDIT" />
            <el-option :label="$t('admin.permission.permDelete')" value="DELETE" />
            <el-option :label="$t('admin.permission.permCallApi')" value="CALL_API" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="grantDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleGrant">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getTemplatePermissions, grantPermission, revokePermission, type PermissionDTO } from '@/api/admin'
import { getTemplates, type TemplateDTO } from '@/api/templates'

const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const templates = ref<TemplateDTO[]>([])
const selectedTemplateId = ref<number | null>(null)
const permissions = ref<PermissionDTO[]>([])

const grantDialogVisible = ref(false)
const grantForm = reactive({
  granteeId: 1,
  granteeType: 'USER' as 'USER' | 'TEAM',
  permissionType: 'VIEW' as 'VIEW' | 'EDIT' | 'DELETE' | 'CALL_API',
})

const permLabels: Record<string, string> = {
  VIEW: 'View',
  EDIT: 'Edit',
  DELETE: 'Delete',
  CALL_API: 'Call API',
}

function permLabel(type: string) {
  return permLabels[type] || type
}

function formatDate(iso: string) {
  return iso ? new Date(iso).toLocaleString() : ''
}

async function loadTemplates() {
  try {
    const res = await getTemplates({ page: 0, size: 200 })
    templates.value = res.content || []
  } catch { templates.value = [] }
}

async function loadPermissions() {
  if (!selectedTemplateId.value) {
    permissions.value = []
    return
  }
  loading.value = true
  try {
    permissions.value = await getTemplatePermissions(selectedTemplateId.value)
  } catch { permissions.value = [] } finally {
    loading.value = false
  }
}

async function handleGrant() {
  if (!selectedTemplateId.value) return
  saving.value = true
  try {
    await grantPermission(selectedTemplateId.value, { ...grantForm })
    ElMessage.success(t('admin.permission.grantSuccess'))
    grantDialogVisible.value = false
    loadPermissions()
  } catch {} finally {
    saving.value = false
  }
}

async function handleRevoke(permId: number) {
  if (!selectedTemplateId.value) return
  try {
    await revokePermission(selectedTemplateId.value, permId)
    ElMessage.success(t('admin.permission.revokeSuccess'))
    loadPermissions()
  } catch {}
}

onMounted(() => loadTemplates())
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}
</style>

