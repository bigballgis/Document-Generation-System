<template>
  <el-drawer
    :model-value="visible"
    :title="t('workspace.segment.title')"
    direction="ttb"
    size="80%"
    :before-close="handleBeforeClose"
    @update:model-value="handleVisibleChange"
  >
    <SegmentArrangementTab ref="segmentTabRef" />
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import SegmentArrangementTab from './SegmentArrangementTab.vue'

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { t } = useI18n()
const segmentTabRef = ref<InstanceType<typeof SegmentArrangementTab> | null>(null)

async function handleBeforeClose(done: () => void) {
  if (segmentTabRef.value?.hasUnsavedChanges) {
    try {
      await ElMessageBox.confirm(
        t('workspace.segment.unsavedConfirm'),
        t('common.warning'),
        { type: 'warning' },
      )
      done()
    } catch {
      // User cancelled — stay open
    }
  } else {
    done()
  }
}

function handleVisibleChange(val: boolean) {
  if (!val && segmentTabRef.value?.hasUnsavedChanges) {
    ElMessageBox.confirm(
      t('workspace.segment.unsavedConfirm'),
      t('common.warning'),
      { type: 'warning' },
    ).then(() => {
      emit('update:visible', false)
    }).catch(() => {
      // stay open
    })
  } else {
    emit('update:visible', val)
  }
}
</script>
