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
          :loading="loadingCandidates"
          :placeholder="t('workspace.reviewPublish.selectReviewers')"
          style="width: 100%"
        >
          <el-option
            v-for="c in candidates"
            :key="c.id"
            :label="candidateLabel(c)"
            :value="c.id"
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
      <el-button type="primary" :loading="props.isSubmitting" :disabled="props.isSubmitting" @click="handleSubmit">
        {{ t('common.submit') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getReviewerCandidates, type ReviewerCandidateDTO } from '@/api/templates'

const props = withDefaults(
  defineProps<{
    visible: boolean
    /** Composite template id; candidates are same-tenant, same-team, excluding the author. */
    templateId: number
    /** When true, disables the submit button (e.g. parent is awaiting submitForReview). */
    isSubmitting?: boolean
  }>(),
  { isSubmitting: false },
)

const emit = defineEmits<{
  'update:visible': [value: boolean]
  submit: [reviewerIds: number[], reviewLevel: number]
}>()

const { t } = useI18n()

const form = reactive({
  reviewerIds: [] as number[],
  reviewLevel: 1,
})

const candidates = ref<ReviewerCandidateDTO[]>([])
const loadingCandidates = ref(false)

function candidateLabel(c: ReviewerCandidateDTO): string {
  const lane = c.teamReviewLane ? ` [${c.teamReviewLane}]` : ''
  if (c.email) return `${c.username} (${c.email})${lane}`
  return `${c.username}${lane}`
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      form.reviewerIds = []
      form.reviewLevel = 1
    }
  },
)

watch(
  () => [props.visible, props.templateId, form.reviewLevel] as const,
  async ([visible, templateId, reviewLevel]) => {
    if (!visible) return
    if (!templateId || templateId <= 0) {
      candidates.value = []
      return
    }
    form.reviewerIds = []
    loadingCandidates.value = true
    try {
      candidates.value = await getReviewerCandidates(templateId, reviewLevel)
    } catch (e: any) {
      candidates.value = []
      ElMessage.error(
        e.response?.data?.message || e.message || t('workspace.reviewPublish.reviewerCandidatesLoadFailed'),
      )
    } finally {
      loadingCandidates.value = false
    }
  },
)

function handleSubmit() {
  if (form.reviewerIds.length === 0) {
    ElMessage.warning(t('workspace.reviewPublish.selectReviewers'))
    return
  }
  emit('submit', form.reviewerIds, form.reviewLevel)
}
</script>
