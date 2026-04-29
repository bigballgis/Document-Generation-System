<template>
  <div>
    <div class="toolbar">
      <span />
      <el-button type="primary" @click="openCreateDialog">{{ $t('admin.apiKey.create') }}</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="name" :label="$t('admin.apiKey.name')" min-width="160" />
      <el-table-column :label="$t('admin.apiKey.key')" width="160">
        <template #default="{ row }">{{ row.keyPrefix ? row.keyPrefix + '...' : '***' }}</template>
      </el-table-column>
      <el-table-column :label="$t('admin.apiKey.status')" width="120">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">
            {{ row.enabled ? $t('admin.apiKey.statusActive') : $t('admin.apiKey.statusDisabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('admin.apiKey.expiresAt')" width="180">
        <template #default="{ row }">{{ formatDate(row.expiresAt) }}</template>
      </el-table-column>
      <el-table-column :label="$t('admin.apiKey.rateLimit')" width="200">
        <template #default="{ row }">
          <span v-if="row.rateLimitPerSecond">{{ row.rateLimitPerSecond }}/s</span>
          <span v-if="row.rateLimitPerMinute"> {{ row.rateLimitPerMinute }}/m</span>
          <span v-if="row.rateLimitPerHour"> {{ row.rateLimitPerHour }}/h</span>
          <span v-if="!row.rateLimitPerSecond && !row.rateLimitPerMinute && !row.rateLimitPerHour">-</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="240" fixed="right">
        <template #default="{ row }">
          <el-button
            size="small"
            :type="row.enabled ? 'warning' : 'success'"
            link
            @click="toggleEnabled(row)"
          >
            {{ row.enabled ? $t('admin.apiKey.disable') : $t('admin.apiKey.enable') }}
          </el-button>
          <el-popconfirm :title="$t('confirm.deleteMessage')" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button size="small" type="danger" link>{{ $t('common.delete') }}</el-button>
            </template>
          </el-popconfirm>
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

    <el-dialog v-model="createDialogVisible" :title="$t('admin.apiKey.create')" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="140px">
        <el-form-item :label="$t('admin.apiKey.name')" prop="name" :rules="[{ required: true, message: 'Required' }]">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="$t('admin.apiKey.expiresAt')">
          <el-date-picker v-model="form.expiresAt" type="datetime" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('admin.apiKey.perSecond')">
          <el-input-number v-model="form.rateLimitPerSecond" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('admin.apiKey.perMinute')">
          <el-input-number v-model="form.rateLimitPerMinute" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('admin.apiKey.perHour')">
          <el-input-number v-model="form.rateLimitPerHour" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="keyDialogVisible" :title="$t('admin.apiKey.key')" width="500px" :close-on-click-modal="false">
      <el-alert :title="$t('admin.apiKey.keyHint')" type="warning" show-icon :closable="false" style="margin-bottom: 16px" />
      <el-input :model-value="createdKey" readonly>
        <template #append>
          <el-button @click="copyKey">{{ $t('admin.apiKey.copyKey') }}</el-button>
        </template>
      </el-input>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getApiKeys,
  createApiKey,
  deleteApiKey,
  enableApiKey,
  disableApiKey,
  type ApiKeyDTO,
} from '@/api/admin'

const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const list = ref<ApiKeyDTO[]>([])

const createDialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  expiresAt: '' as string | Date,
  rateLimitPerSecond: 0,
  rateLimitPerMinute: 0,
  rateLimitPerHour: 0,
})

const keyDialogVisible = ref(false)
const createdKey = ref('')

function formatDate(iso?: string) {
  return iso ? new Date(iso).toLocaleString() : '-'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getApiKeys({ page: page.value - 1, size: pageSize.value })
    list.value = res.content || []
    total.value = res.totalElements || 0
  } catch {} finally {
    loading.value = false
  }
}

function openCreateDialog() {
  form.name = ''
  form.expiresAt = ''
  form.rateLimitPerSecond = 0
  form.rateLimitPerMinute = 0
  form.rateLimitPerHour = 0
  createDialogVisible.value = true
}

async function handleCreate() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const expiresAt = form.expiresAt ? new Date(form.expiresAt).toISOString() : undefined
    const res = await createApiKey({
      name: form.name,
      expiresAt,
      rateLimitPerSecond: form.rateLimitPerSecond || undefined,
      rateLimitPerMinute: form.rateLimitPerMinute || undefined,
      rateLimitPerHour: form.rateLimitPerHour || undefined,
    })
    createDialogVisible.value = false
    createdKey.value = res.key
    keyDialogVisible.value = true
    loadData()
  } catch {} finally {
    saving.value = false
  }
}

function copyKey() {
  navigator.clipboard.writeText(createdKey.value)
  ElMessage.success(t('admin.apiKey.keyCopied'))
}

async function toggleEnabled(apiKey: ApiKeyDTO) {
  const msg = apiKey.enabled ? t('confirm.disableMessage') : t('confirm.enableMessage')
  const title = apiKey.enabled ? t('confirm.disableTitle') : t('confirm.enableTitle')
  try {
    await ElMessageBox.confirm(msg, title, { type: 'warning' })
    if (apiKey.enabled) {
      await disableApiKey(apiKey.id)
    } else {
      await enableApiKey(apiKey.id)
    }
    ElMessage.success(t('message.operationSuccess'))
    loadData()
  } catch {}
}

async function handleDelete(id: number) {
  try {
    await deleteApiKey(id)
    ElMessage.success(t('message.deleteSuccess'))
    loadData()
  } catch {}
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

