<template>
  <el-card class="api-endpoint-info">
    <template #header>{{ t('workspace.reviewPublish.apiEndpoint') }}</template>

    <div class="endpoint-row">
      <span class="label">URL:</span>
      <code>POST {{ apiUrl }}</code>
      <el-button size="small" @click="emit('copy', apiUrl)">{{ t('common.copy') }}</el-button>
    </div>

    <template v-if="activeKey">
      <div class="endpoint-row">
        <span class="label">API Key:</span>
        <code>{{ activeKey.keyPrefix }}</code>
        <el-button size="small" @click="emit('copy', activeKey.keyPrefix || '')">{{ t('common.copy') }}</el-button>
      </div>
    </template>
    <template v-else>
      <el-alert type="warning" :title="t('workspace.reviewPublish.noApiKey')" show-icon :closable="false" style="margin-bottom: 12px">
        <el-button size="small" :loading="creatingApiKey" @click="emit('create-api-key')">
          {{ t('workspace.reviewPublish.createApiKey') }}
        </el-button>
      </el-alert>
    </template>

    <div class="curl-section">
      <span class="label">cURL:</span>
      <pre class="curl-block">{{ curlExample }}</pre>
      <el-button size="small" @click="emit('copy', curlExample)">{{ t('common.copy') }}</el-button>
    </div>

    <el-alert type="info" :title="t('workspace.reviewPublish.apiNote')" show-icon :closable="false" style="margin-top: 12px" />
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ApiKeyDTO } from '@/api/admin'

const props = defineProps<{
  templateId: number
  apiKeys: ApiKeyDTO[]
  curlExample: string
  apiKeysLoading: boolean
  creatingApiKey: boolean
}>()

const emit = defineEmits<{
  copy: [text: string]
  'create-api-key': []
}>()

const { t } = useI18n()

const apiUrl = computed(() => `${window.location.origin}/api/generate/${props.templateId}`)
const activeKey = computed(() => props.apiKeys.find(k => k.enabled) ?? props.apiKeys[0] ?? null)
</script>

<style scoped>
.api-endpoint-info {
  margin-top: 16px;
}
.endpoint-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.endpoint-row .label {
  font-weight: bold;
  min-width: 60px;
}
.endpoint-row code {
  background: var(--el-fill-color-light);
  padding: 4px 8px;
  border-radius: 4px;
  font-family: monospace;
  flex: 1;
  word-break: break-all;
}
.curl-section {
  margin-top: 12px;
}
.curl-section .label {
  font-weight: bold;
  display: block;
  margin-bottom: 4px;
}
.curl-block {
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 4px 0;
}
</style>
