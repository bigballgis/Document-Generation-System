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
      <el-button type="primary" @click="openDialog(null)">{{ $t('admin.tenant.create') }}</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="name" :label="$t('admin.tenant.name')" min-width="160" />
      <el-table-column :label="$t('admin.tenant.status')" width="120">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">
            {{ row.enabled ? $t('admin.tenant.statusActive') : $t('admin.tenant.statusDisabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('admin.tenant.maxTemplates')" width="140" prop="maxTemplates" />
      <el-table-column :label="$t('admin.tenant.maxApiCalls')" width="180" prop="maxApiCallsPerMonth" />
      <el-table-column :label="$t('admin.tenant.maxStorage')" width="140">
        <template #default="{ row }">{{ row.maxStorageGb }} GB</template>
      </el-table-column>
      <el-table-column :label="$t('common.createdAt')" width="180">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="openDialog(row)">{{ $t('common.edit') }}</el-button>
          <el-button
            size="small"
            :type="row.enabled ? 'warning' : 'success'"
            link
            @click="toggleEnabled(row)"
          >
            {{ row.enabled ? $t('common.disable') : $t('common.enable') }}
          </el-button>
          <el-button size="small" link @click="viewUsage(row)">{{ $t('admin.tenant.usage') }}</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editing ? $t('admin.tenant.edit') : $t('admin.tenant.create')" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="160px">
        <el-form-item :label="$t('admin.tenant.name')" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="$t('admin.tenant.maxTemplates')" prop="maxTemplates">
          <el-input-number v-model="form.maxTemplates" :min="1" :max="10000" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('admin.tenant.maxApiCalls')" prop="maxApiCallsPerMonth">
          <el-input-number v-model="form.maxApiCallsPerMonth" :min="100" :max="10000000" :step="1000" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('admin.tenant.maxStorage')" prop="maxStorageGb">
          <el-input-number v-model="form.maxStorageGb" :min="1" :max="1000" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="usageVisible" :title="$t('admin.tenant.usage')" width="450px" destroy-on-close>
      <div v-if="usage" class="usage-list">
        <div class="usage-item">
          <span>{{ $t('admin.tenant.usedTemplates') }}</span>
          <el-progress :percentage="Math.round((usage.usedTemplates / usage.maxTemplates) * 100)" />
          <span class="usage-text">{{ usage.usedTemplates }} / {{ usage.maxTemplates }}</span>
        </div>
        <div class="usage-item">
          <span>{{ $t('admin.tenant.usedApiCalls') }}</span>
          <el-progress :percentage="Math.round((usage.usedApiCalls / usage.maxApiCallsPerMonth) * 100)" />
          <span class="usage-text">{{ usage.usedApiCalls }} / {{ usage.maxApiCallsPerMonth }}</span>
        </div>
        <div class="usage-item">
          <span>{{ $t('admin.tenant.usedStorage') }}</span>
          <el-progress :percentage="Math.round((usage.usedStorageGb / usage.maxStorageGb) * 100)" />
          <span class="usage-text">{{ usage.usedStorageGb }} GB / {{ usage.maxStorageGb }} GB</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getTenants,
  createTenant,
  updateTenant,
  enableTenant,
  disableTenant,
  getTenantUsage,
  type TenantDTO,
  type TenantUsageDTO,
} from '@/api/admin'

const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const list = ref<TenantDTO[]>([])

const dialogVisible = ref(false)
const editing = ref<TenantDTO | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  maxTemplates: 100,
  maxApiCallsPerMonth: 10000,
  maxStorageGb: 10,
})

const rules: FormRules = {
  name: [{ required: true, message: () => t('validation.required', { field: t('admin.tenant.name') }), trigger: 'blur' }],
  maxTemplates: [{ required: true, message: () => t('validation.required', { field: t('admin.tenant.maxTemplates') }), trigger: 'blur' }],
  maxApiCallsPerMonth: [{ required: true, message: () => t('validation.required', { field: t('admin.tenant.maxApiCalls') }), trigger: 'blur' }],
  maxStorageGb: [{ required: true, message: () => t('validation.required', { field: t('admin.tenant.maxStorage') }), trigger: 'blur' }],
}

const usageVisible = ref(false)
const usage = ref<TenantUsageDTO | null>(null)

function formatDate(iso: string) {
  return iso ? new Date(iso).toLocaleString() : ''
}

async function loadData() {
  loading.value = true
  try {
    const res = await getTenants({ page: page.value - 1, size: pageSize.value, keyword: keyword.value || undefined })
    list.value = res.content || []
    total.value = res.totalElements || 0
  } catch {} finally {
    loading.value = false
  }
}

function openDialog(tenant: TenantDTO | null) {
  editing.value = tenant
  if (tenant) {
    form.name = tenant.name
    form.maxTemplates = tenant.maxTemplates
    form.maxApiCallsPerMonth = tenant.maxApiCallsPerMonth
    form.maxStorageGb = tenant.maxStorageGb
  } else {
    form.name = ''
    form.maxTemplates = 100
    form.maxApiCallsPerMonth = 10000
    form.maxStorageGb = 10
  }
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (editing.value) {
      await updateTenant(editing.value.id, { ...form })
      ElMessage.success(t('message.updateSuccess'))
    } else {
      await createTenant({ ...form })
      ElMessage.success(t('message.createSuccess'))
    }
    dialogVisible.value = false
    loadData()
  } catch {} finally {
    saving.value = false
  }
}

async function toggleEnabled(tenant: TenantDTO) {
  const msg = tenant.enabled ? t('admin.tenant.disableConfirm') : t('admin.tenant.enableConfirm')
  const title = tenant.enabled ? t('confirm.disableTitle') : t('confirm.enableTitle')
  try {
    await ElMessageBox.confirm(msg, title, { type: 'warning' })
    if (tenant.enabled) {
      await disableTenant(tenant.id)
    } else {
      await enableTenant(tenant.id)
    }
    ElMessage.success(t('message.operationSuccess'))
    loadData()
  } catch {}
}

async function viewUsage(tenant: TenantDTO) {
  usage.value = null
  usageVisible.value = true
  try {
    usage.value = await getTenantUsage(tenant.id)
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
.usage-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.usage-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.usage-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: right;
}
</style>

