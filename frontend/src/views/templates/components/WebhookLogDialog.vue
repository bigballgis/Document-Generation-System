<template>
  <el-dialog
    :model-value="visible"
    :title="$t('webhook.logs')"
    width="900px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-table :data="logs" v-loading="loading" border stripe>
      <el-table-column prop="eventType" :label="$t('webhook.eventType')" width="150" />
      <el-table-column :label="$t('webhook.payloadTemplate')" min-width="200">
        <template #default="{ row }">
          <el-popover v-if="row.payload && row.payload.length > 50" trigger="hover" width="400">
            <template #reference>
              <span>{{ row.payload.substring(0, 50) + '...' }}</span>
            </template>
            <pre style="white-space: pre-wrap; word-break: break-all; max-height: 300px; overflow: auto;">{{ row.payload }}</pre>
          </el-popover>
          <span v-else>{{ row.payload }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('webhook.responseStatus')" width="140">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.responseStatus)">{{ row.responseStatus }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('webhook.responseBody')" min-width="200">
        <template #default="{ row }">
          {{ row.responseBody && row.responseBody.length > 50 ? row.responseBody.substring(0, 50) + '...' : row.responseBody }}
        </template>
      </el-table-column>
      <el-table-column prop="sentAt" :label="$t('webhook.sentAt')" width="170" />
    </el-table>

    <div class="pagination-wrapper" style="margin-top: 16px; display: flex; justify-content: flex-end;">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="fetchLogs"
        @current-change="fetchLogs"
      />
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import type { WebhookLogDTO } from '@/api/webhooks'
import { getWebhookLogs } from '@/api/webhooks'

const props = defineProps<{
  visible: boolean
  webhookId: number
}>()

defineEmits<{
  'update:visible': [val: boolean]
}>()

const loading = ref(false)
const logs = ref<WebhookLogDTO[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 10 })

type TagType = 'success' | 'warning' | 'danger' | 'info'

function statusTagType(status: number): TagType {
  if (status >= 200 && status < 300) return 'success'
  if (status >= 400 && status < 500) return 'warning'
  if (status >= 500) return 'danger'
  return 'info'
}

async function fetchLogs() {
  if (!props.webhookId) return
  loading.value = true
  try {
    const res = await getWebhookLogs(props.webhookId, { page: query.page, size: query.size })
    logs.value = res.content
    total.value = res.totalElements
  } catch { /* handled */ } finally {
    loading.value = false
  }
}

watch(() => props.visible, (val) => {
  if (val && props.webhookId) {
    query.page = 1
    fetchLogs()
  }
})
</script>
