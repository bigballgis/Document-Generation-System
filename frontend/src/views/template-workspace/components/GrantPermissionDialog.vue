<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    :title="t('workspace.settings.grantPermission')"
    width="480px"
  >
    <el-form :model="form" label-width="120px">
      <el-form-item :label="t('workspace.settings.granteeType')">
        <el-radio-group v-model="form.granteeType">
          <el-radio value="USER">USER</el-radio>
          <el-radio value="TEAM">TEAM</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="t('common.name')">
        <el-select v-model="form.granteeId" filterable :loading="loadingGrantees" :placeholder="t('common.select')">
          <template v-if="form.granteeType === 'USER'">
            <el-option v-for="u in users" :key="u.id" :label="u.username" :value="u.id" />
          </template>
          <template v-else>
            <el-option v-for="team in teams" :key="team.id" :label="team.name" :value="team.id" />
          </template>
        </el-select>
      </el-form-item>
      <el-form-item :label="t('workspace.settings.permissionType')">
        <el-select v-model="form.permissionType">
          <el-option label="VIEW" value="VIEW" />
          <el-option label="EDIT" value="EDIT" />
          <el-option label="DELETE" value="DELETE" />
          <el-option label="CALL_API" value="CALL_API" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ t('common.confirm') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { getUsers, grantPermission } from '@/api/admin'
import { listTeams } from '@/api/teams'
import type { UserDTO } from '@/api/admin'
import type { TeamDTO } from '@/api/teams'

const props = defineProps<{
  visible: boolean
  templateId: number
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  saved: []
}>()

const { t } = useI18n()
const workspaceStore = useTemplateWorkspaceStore()

const form = reactive({
  granteeType: 'USER' as 'USER' | 'TEAM',
  granteeId: null as number | null,
  permissionType: 'VIEW' as 'VIEW' | 'EDIT' | 'DELETE' | 'CALL_API',
})

const users = ref<UserDTO[]>([])
const teams = ref<TeamDTO[]>([])
const loadingGrantees = ref(false)
const submitting = ref(false)

watch(() => form.granteeType, async (type) => {
  form.granteeId = null
  loadingGrantees.value = true
  try {
    if (type === 'USER') {
      const result = await getUsers({ page: 0, size: 100 })
      users.value = result.content
    } else {
      const tenantId = workspaceStore.template?.tenantId
      if (tenantId) {
        teams.value = await listTeams(tenantId)
      }
    }
  } catch {
    /* silent */
  } finally {
    loadingGrantees.value = false
  }
}, { immediate: true })

watch(() => props.visible, (val) => {
  if (val) {
    form.granteeType = 'USER'
    form.granteeId = null
    form.permissionType = 'VIEW'
  }
})

async function handleSubmit() {
  if (!form.granteeId) {
    ElMessage.warning(t('workspace.settings.selectGrantee'))
    return
  }
  submitting.value = true
  try {
    await grantPermission(props.templateId, {
      granteeId: form.granteeId,
      granteeType: form.granteeType,
      permissionType: form.permissionType,
    })
    emit('saved')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.saveFailed'))
  } finally {
    submitting.value = false
  }
}
</script>
