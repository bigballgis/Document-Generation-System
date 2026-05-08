<template>
  <el-dialog :model-value="visible" @update:model-value="$emit('update:visible', $event)" :title="$t('migration.title')" width="500px">
    <el-form :model="form" label-width="120px">
      <el-form-item :label="$t('migration.sourceTemplate')">
        <el-input-number v-model="form.templateId" :min="1" :placeholder="$t('migration.sourceTemplatePlaceholder')" style="width: 100%" />
      </el-form-item>
    </el-form>
    <el-alert :title="$t('migration.warning')" type="warning" show-icon :closable="false" style="margin-top: 12px" />
    <template #footer>
      <el-button @click="$emit('update:visible', false)">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="migrating" @click="handleMigrate">{{ $t('migration.migrate') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { migrateToComposite } from '@/api/composite-templates'

defineProps<{ visible: boolean }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void; (e: 'migrated'): void }>()
const { t } = useI18n()

const migrating = ref(false)
const form = reactive({ templateId: undefined as number | undefined })

async function handleMigrate() {
  if (!form.templateId) return
  migrating.value = true
  try {
    await migrateToComposite(form.templateId)
    ElMessage.success(t('migration.success'))
    emit('update:visible', false)
    emit('migrated')
  } catch {} finally {
    migrating.value = false
  }
}
</script>
