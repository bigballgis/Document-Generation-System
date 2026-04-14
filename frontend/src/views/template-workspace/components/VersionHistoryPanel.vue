<template>
  <el-table :data="store.versions" border stripe>
    <el-table-column prop="versionNumber" :label="t('common.version')" width="100" />
    <el-table-column prop="createdAt" :label="t('common.createdAt')" width="180" />
    <el-table-column prop="createdBy" :label="t('common.createdBy')" width="140" />
    <el-table-column prop="comment" :label="t('common.comment')" />
    <el-table-column :label="t('common.actions')" width="120" fixed="right">
      <template #default="{ row }">
        <el-button
          size="small"
          :loading="rollingBackId === row.id"
          :disabled="rollingBackId !== null && rollingBackId !== row.id"
          @click="handleRollback(row)"
        >{{ t('workspace.settings.rollback') }}</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { rollbackVersion } from '@/api/templates'
import type { TemplateVersionDTO } from '@/api/templates'

const store = useTemplateWorkspaceStore()
const { t } = useI18n()

const rollingBackId = ref<number | null>(null)

async function handleRollback(version: TemplateVersionDTO) {
  try {
    await ElMessageBox.confirm(
      t('workspace.settings.rollbackConfirm', { versionNumber: version.versionNumber }),
      t('common.confirm'),
      { type: 'warning' },
    )
  } catch {
    return
  }

  rollingBackId.value = version.id
  try {
    await rollbackVersion(store.templateId, version.id)
    await Promise.all([
      store.refreshTemplate(),
      store.refreshAssemblyConfig(),
      store.refreshCoverage(),
      store.refreshVersions(),
    ])
    ElMessage.success(t('workspace.settings.rollbackSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  } finally {
    rollingBackId.value = null
  }
}
</script>
