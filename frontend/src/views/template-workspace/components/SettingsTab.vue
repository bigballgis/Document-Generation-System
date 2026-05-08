<template>
  <div class="settings-tab">
    <el-collapse v-model="activeCollapseNames">
      <el-collapse-item :title="t('workspace.settings.versionHistory')" name="versionHistory">
        <VersionHistoryPanel />
      </el-collapse-item>

      <el-collapse-item :title="t('workspace.settings.versionDiff')" name="versionDiff">
        <VersionDiffPanel />
      </el-collapse-item>

      <el-collapse-item :title="t('workspace.settings.webhooks')" name="webhooks">
        <WebhookPanel :template-id="store.templateId" />
      </el-collapse-item>

      <el-collapse-item :title="t('workspace.settings.watermarkSecurity')" name="watermarkSecurity">
        <WatermarkSecurityConfig :template-id="store.templateId" />
      </el-collapse-item>

      <el-collapse-item :title="t('workspace.settings.scheduledTasks')" name="scheduledTasks">
        <ScheduledTaskManagement :template-id="store.templateId" />
      </el-collapse-item>

      <el-collapse-item :title="t('workspace.settings.permissions')" name="permissions">
        <PermissionPanel />
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import VersionHistoryPanel from './VersionHistoryPanel.vue'
import VersionDiffPanel from './VersionDiffPanel.vue'
import PermissionPanel from './PermissionPanel.vue'
import WebhookPanel from '@/views/templates/components/WebhookPanel.vue'
import WatermarkSecurityConfig from '@/views/templates/components/WatermarkSecurityConfig.vue'
import ScheduledTaskManagement from '@/views/templates/components/ScheduledTaskManagement.vue'

const store = useTemplateWorkspaceStore()
const { t } = useI18n()

const activeCollapseNames = ref<string[]>(['versionHistory'])
const dataLoaded = ref(false)

async function loadSettingsData() {
  if (!dataLoaded.value) {
    await Promise.allSettled([
      store.refreshVersions(),
      store.refreshPermissions(),
    ])
    dataLoaded.value = true
  }
}

onMounted(() => {
  loadSettingsData()
})
</script>

<style scoped>
.settings-tab {
  padding: 16px 0;
}
</style>
