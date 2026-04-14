<template>
  <el-dialog
    :model-value="visible"
    :title="t('workspace.reviewPublish.submitForReview')"
    width="500px"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form label-width="120px">
      <el-form-item :label="t('workspace.reviewPublish.selectReviewers')" required>
        <el-select
          v-model="form.reviewerIds"
          multiple
          filterable
          :loading="loadingUsers"
          :placeholder="t('workspace.reviewPublish.selectReviewers')"
          style="width: 100%"
        >
          <el-option
            v-for="user in users"
            :key="user.id"
            :label="user.username"
            :value="user.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('workspace.reviewPublish.reviewLevel')">
        <el-select v-model="form.reviewLevel" style="width: 100%">
          <el-option :label="t('workspace.reviewPublish.initialReview')" :value="1" />
          <el-option :label="t('workspace.reviewPublish.finalReview')" :value="2" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ t('common.submit') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getUsers } from '@/api/admin'
import type { UserDTO } from '@/api/admin'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  submit: [reviewerIds: number[], reviewLevel: number]
}>()

const { t } = useI18n()

const form = reactive({
  reviewerIds: [] as number[],
  reviewLevel: 1,
})

const users = ref<UserDTO[]>([])
const loadingUsers = ref(false)
const submitting = ref(false)

watch(() => props.visible, async (val) => {
  if (val && users.value.length === 0) {
    loadingUsers.value = true
    try {
      const result = await getUsers({ page: 0, size: 100 })
      users.value = result.content
    } catch { /* silent */ }
    finally { loadingUsers.value = false }
  }
  if (val) {
    form.reviewerIds = []
    form.reviewLevel = 1
  }
})

function handleSubmit() {
  if (form.reviewerIds.length === 0) {
    ElMessage.warning(t('workspace.reviewPublish.selectReviewers'))
    return
  }
  emit('submit', form.reviewerIds, form.reviewLevel)
}
</script>
