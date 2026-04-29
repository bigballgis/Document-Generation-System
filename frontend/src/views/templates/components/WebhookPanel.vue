<template>
  <div class="webhook-panel">
    <div class="toolbar">
      <el-button type="primary" @click="openCreateDialog">{{ $t('webhook.create') }}</el-button>
    </div>

    <el-table v-if="webhooks.length > 0" :data="webhooks" v-loading="loading" border stripe>
      <el-table-column prop="url" :label="$t('webhook.url')" min-width="200" />
      <el-table-column :label="$t('webhook.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">
            {{ row.enabled ? $t('common.enable') : $t('common.disable') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('webhook.payloadTemplate')" min-width="180">
        <template #default="{ row }">
          {{ row.payloadTemplate && row.payloadTemplate.length > 50 ? row.payloadTemplate.substring(0, 50) + '...' : row.payloadTemplate }}
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" :label="$t('common.createdAt')" width="170" />
      <el-table-column :label="$t('common.actions')" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEditDialog(row)">{{ $t('common.edit') }}</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
          <el-button size="small" @click="openLogDialog(row)">{{ $t('webhook.logs') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-else-if="!loading" :description="$t('common.noData')" />

    <WebhookFormDialog
      v-model:visible="formDialogVisible"
      :template-id="templateId"
      :data="editingWebhook"
      @saved="onSaved"
    />

    <WebhookLogDialog
      v-model:visible="logDialogVisible"
      :webhook-id="logWebhookId"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { WebhookConfigDTO } from '@/api/webhooks'
import { listWebhooks, deleteWebhook } from '@/api/webhooks'
import WebhookFormDialog from './WebhookFormDialog.vue'
import WebhookLogDialog from './WebhookLogDialog.vue'

const props = defineProps<{ templateId: number }>()
const { t } = useI18n()

const loading = ref(false)
const webhooks = ref<WebhookConfigDTO[]>([])
const formDialogVisible = ref(false)
const editingWebhook = ref<WebhookConfigDTO | null>(null)
const logDialogVisible = ref(false)
const logWebhookId = ref(0)

async function fetchWebhooks() {
  loading.value = true
  try {
    webhooks.value = await listWebhooks(props.templateId)
  } catch {} finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingWebhook.value = null
  formDialogVisible.value = true
}

function openEditDialog(row: WebhookConfigDTO) {
  editingWebhook.value = row
  formDialogVisible.value = true
}

function openLogDialog(row: WebhookConfigDTO) {
  logWebhookId.value = row.id
  logDialogVisible.value = true
}

function onSaved() {
  fetchWebhooks()
}

async function handleDelete(row: WebhookConfigDTO) {
  try {
    await ElMessageBox.confirm(
      t('webhook.confirmDelete'),
      t('confirm.deleteTitle'),
      { type: 'warning' },
    )
    await deleteWebhook(row.id)
    ElMessage.success(t('message.deleteSuccess'))
    fetchWebhooks()
  } catch {}
}

onMounted(fetchWebhooks)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
</style>
