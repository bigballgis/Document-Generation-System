<template>
  <div class="permission-panel">
    <div class="toolbar" style="margin-bottom: 16px">
      <el-button type="primary" @click="grantDialogVisible = true">{{ t('workspace.settings.grantPermission') }}</el-button>
    </div>

    <el-empty v-if="store.permissions.length === 0" :description="t('workspace.settings.noPermissions')" />

    <el-table v-else :data="store.permissions" border stripe>
      <el-table-column prop="granteeName" :label="t('common.name')" min-width="140" />
      <el-table-column :label="t('workspace.settings.granteeType')" width="120">
        <template #default="{ row }">
          <el-tag :type="row.granteeType === 'USER' ? 'primary' : 'success'" size="small">{{ row.granteeType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('workspace.settings.permissionType')" width="120">
        <template #default="{ row }">
          <el-tag size="small">{{ row.permissionType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" :label="t('common.createdAt')" width="180" />
      <el-table-column :label="t('common.actions')" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            size="small"
            type="danger"
            :loading="revokingId === row.id"
            @click="handleRevoke(row)"
          >{{ t('common.revoke') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <GrantPermissionDialog
      v-model:visible="grantDialogVisible"
      :template-id="store.templateId"
      @saved="onGrantSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { revokePermission } from '@/api/admin'
import type { PermissionDTO } from '@/api/admin'
import GrantPermissionDialog from './GrantPermissionDialog.vue'

const store = useTemplateWorkspaceStore()
const { t } = useI18n()

const grantDialogVisible = ref(false)
const revokingId = ref<number | null>(null)

async function handleRevoke(permission: PermissionDTO) {
  try {
    await ElMessageBox.confirm(
      t('workspace.settings.revokeConfirm'),
      t('common.confirm'),
      { type: 'warning' },
    )
  } catch {
    return
  }

  revokingId.value = permission.id
  try {
    await revokePermission(store.templateId, permission.id)
    await store.refreshPermissions()
    ElMessage.success(t('message.deleteSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  } finally {
    revokingId.value = null
  }
}

async function onGrantSaved() {
  grantDialogVisible.value = false
  await store.refreshPermissions()
  ElMessage.success(t('message.saveSuccess'))
}
</script>

<style scoped>
.permission-panel {
  padding: 8px 0;
}
</style>
