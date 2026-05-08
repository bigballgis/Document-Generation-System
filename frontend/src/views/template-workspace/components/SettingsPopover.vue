<template>
  <el-drawer
    :model-value="visible"
    :title="t('workspace.tabSettings')"
    direction="rtl"
    size="520px"
    @update:model-value="emit('update:visible', $event)"
  >
    <SettingsTab />
  </el-drawer>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SettingsTab from './SettingsTab.vue'

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { t } = useI18n()
</script>
