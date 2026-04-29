<template>
  <div class="api-management-page">
    <div class="page-header">
      <div class="header-left">
        <h2>{{ t('workspace.api.title') }} — {{ template?.name ?? '' }}</h2>
      </div>
      <el-button @click="router.push('/templates')">{{ t('workspace.api.backToList') }}</el-button>
    </div>

    <el-skeleton v-if="loading" :rows="8" animated />

    <template v-else-if="template">
      <el-card shadow="never" style="margin-bottom: 16px">
        <template #header>{{ t('workspace.api.versions') }}</template>
        <el-empty v-if="versions.length === 0" :description="t('workspace.api.noActiveVersions')" />
        <el-table v-else :data="versions" stripe>
          <el-table-column prop="versionNumber" :label="t('common.version')" width="100">
            <template #default="{ row }">v{{ row.versionNumber }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" :label="t('common.createdAt')" width="180" />
          <el-table-column prop="createdBy" :label="t('common.createdBy')" />
          <el-table-column prop="comment" :label="t('common.comment')" />
        </el-table>
      </el-card>

      <ApiEndpointInfo
        :template-id="templateId"
        :api-keys="apiKeys"
        :curl-example="curlExample"
        :api-keys-loading="apiKeysLoading"
        :creating-api-key="creatingApiKey"
        @copy="copyToClipboard"
        @create-api-key="handleCreateApiKey"
      />

      <el-card shadow="never" style="margin-top: 16px">
        <template #header>{{ t('workspace.api.keys') }}</template>
        <el-table :data="apiKeys" stripe v-loading="apiKeysLoading">
          <el-table-column prop="name" :label="t('common.name')" />
          <el-table-column prop="keyPrefix" label="Key Prefix" width="200" />
          <el-table-column :label="t('common.status')" width="100">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                {{ row.enabled ? t('common.enable') : t('common.disable') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" :label="t('common.createdAt')" width="180" />
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getTemplate, getTemplateVersions, type TemplateDTO, type TemplateVersionDTO } from '@/api/templates'
import { getApiKeys, createApiKey, type ApiKeyDTO } from '@/api/admin'
import ApiEndpointInfo from '@/views/template-workspace/components/ApiEndpointInfo.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const templateId = computed(() => Number(route.params.id))
const loading = ref(false)
const template = ref<TemplateDTO | null>(null)
const versions = ref<TemplateVersionDTO[]>([])
const apiKeys = ref<ApiKeyDTO[]>([])
const apiKeysLoading = ref(false)
const creatingApiKey = ref(false)

const curlExample = computed(() => {
  const keyPrefix = apiKeys.value.find(k => k.enabled)?.keyPrefix ?? 'YOUR_API_KEY'
  return `curl -X POST \\
  ${window.location.origin}/api/generate/${templateId.value} \\
  -H "X-API-Key: ${keyPrefix}" \\
  -H "Content-Type: application/json" \\
  -d '{"data": {}}'`
})

async function loadData() {
  loading.value = true
  try {
    const [tmpl, vers] = await Promise.all([
      getTemplate(templateId.value),
      getTemplateVersions(templateId.value),
    ])
    template.value = tmpl
    versions.value = vers
    await loadApiKeys()
  } catch {
  } finally {
    loading.value = false
  }
}

async function loadApiKeys() {
  apiKeysLoading.value = true
  try {
    const result = await getApiKeys({ page: 0, size: 20 })
    apiKeys.value = result.content
  } catch {
  } finally {
    apiKeysLoading.value = false
  }
}

async function handleCreateApiKey() {
  creatingApiKey.value = true
  try {
    await createApiKey({ name: `api-${template.value?.name ?? 'template'}-${Date.now()}` })
    await loadApiKeys()
    ElMessage.success(t('workspace.reviewPublish.apiKeyCreated'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('workspace.reviewPublish.apiKeyFailed'))
  } finally {
    creatingApiKey.value = false
  }
}

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text)
  ElMessage.success(t('common.copied'))
}

onMounted(() => loadData())
</script>

<style scoped>
.api-management-page {
  padding: 0;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
}
</style>

