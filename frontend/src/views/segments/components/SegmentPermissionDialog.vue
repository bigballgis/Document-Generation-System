<template>
  <el-dialog
    :model-value="visible"
    :title="$t('segment.permissions')"
    width="650px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-table :data="permissions" v-loading="loading" stripe style="margin-bottom: 16px">
      <el-table-column :label="$t('segment.permissionUser')" min-width="140">
        <template #default="{ row }">
          {{ row.userId ? `User #${row.userId}` : `Team #${row.teamId}` }}
        </template>
      </el-table-column>
      <el-table-column prop="permissionType" :label="$t('segment.permissionType')" width="120">
        <template #default="{ row }">
          <el-tag size="small">{{ row.permissionType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="grantedAt" :label="$t('common.createdAt')" width="170" />
      <el-table-column :label="$t('common.actions')" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" size="small" @click="handleRevoke(row)">
            {{ $t('segment.revokePermission') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-divider />
    <h4>{{ $t('segment.grantPermission') }}</h4>
    <el-form :inline="true">
      <el-form-item :label="$t('segment.permissionUser')">
        <el-input-number v-model="grantForm.userId" :min="1" :placeholder="$t('segment.permissionUser')" />
      </el-form-item>
      <el-form-item :label="$t('segment.permissionType')">
        <el-select v-model="grantForm.permissionType" style="width: 120px">
          <el-option :label="$t('segment.permView')" value="VIEW" />
          <el-option :label="$t('segment.permEdit')" value="EDIT" />
          <el-option :label="$t('segment.permAdmin')" value="ADMIN" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleGrant">{{ $t('segment.grantPermission') }}</el-button>
      </el-form-item>
    </el-form>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSegmentPermissions, grantSegmentPermission, revokeSegmentPermission } from '@/api/segments'
import type { SegmentPermission, PermissionType } from '@/types/segment'

const props = defineProps<{ visible: boolean; segmentId: number }>()
defineEmits<{ 'update:visible': [val: boolean] }>()
const { t } = useI18n()

const loading = ref(false)
const permissions = ref<SegmentPermission[]>([])
const grantForm = reactive({
  userId: null as number | null,
  permissionType: 'VIEW' as PermissionType,
})

async function fetchPermissions() {
  loading.value = true
  try {
    permissions.value = await getSegmentPermissions(props.segmentId)
  } catch { /* handled */ } finally {
    loading.value = false
  }
}

async function handleGrant() {
  if (!grantForm.userId) return
  try {
    await grantSegmentPermission(props.segmentId, {
      userId: grantForm.userId,
      permissionType: grantForm.permissionType,
    })
    ElMessage.success(t('segment.grantSuccess'))
    fetchPermissions()
  } catch { /* handled */ }
}

async function handleRevoke(row: SegmentPermission) {
  try {
    await ElMessageBox.confirm(t('segment.revokeConfirm'), t('common.warning'))
    await revokeSegmentPermission(props.segmentId, row.id)
    ElMessage.success(t('segment.revokeSuccess'))
    fetchPermissions()
  } catch { /* cancelled */ }
}

watch(() => props.visible, (val) => {
  if (val) fetchPermissions()
})
</script>
