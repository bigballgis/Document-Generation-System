<template>
  <div class="version-diff-panel">
    <el-row :gutter="16" align="middle">
      <el-col :span="8">
        <el-select v-model="versionA" :placeholder="t('workspace.settings.versionA')" clearable>
          <el-option v-for="v in store.versions" :key="v.id" :label="`v${v.versionNumber}`" :value="v.versionNumber" />
        </el-select>
      </el-col>
      <el-col :span="8">
        <el-select v-model="versionB" :placeholder="t('workspace.settings.versionB')" clearable>
          <el-option v-for="v in store.versions" :key="v.id" :label="`v${v.versionNumber}`" :value="v.versionNumber" />
        </el-select>
      </el-col>
      <el-col :span="8">
        <el-button type="primary" :loading="comparing" :disabled="isCompareDisabled" @click="handleCompare">
          {{ t('workspace.settings.compareVersions') }}
        </el-button>
      </el-col>
    </el-row>

    <template v-if="diffResult">
      <div class="diff-summary" style="margin-top: 16px">
        <el-tag type="success">+{{ diffResult.summary.added }}</el-tag>
        <el-tag type="danger" style="margin-left: 8px">-{{ diffResult.summary.removed }}</el-tag>
        <el-tag type="warning" style="margin-left: 8px">~{{ diffResult.summary.modified }}</el-tag>
      </div>

      <el-table v-if="diffResult.textDiffs.length > 0" :data="diffResult.textDiffs" border stripe style="margin-top: 12px">
        <el-table-column prop="field" label="Field" width="200" />
        <el-table-column label="Type" width="120">
          <template #default="{ row }">
            <el-tag :type="diffTypeTag(row.type)" size="small">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="oldValue" label="Old Value" />
        <el-table-column prop="newValue" label="New Value" />
      </el-table>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { getVersionDiff } from '@/api/templates'
import type { VersionDiffResult } from '@/api/templates'

const store = useTemplateWorkspaceStore()
const { t } = useI18n()

const versionA = ref<number | null>(null)
const versionB = ref<number | null>(null)
const diffResult = ref<VersionDiffResult | null>(null)
const comparing = ref(false)

const isCompareDisabled = computed(() =>
  versionA.value === null || versionB.value === null || versionA.value === versionB.value,
)

async function handleCompare() {
  if (versionA.value === null || versionB.value === null) return
  comparing.value = true
  try {
    diffResult.value = await getVersionDiff(store.templateId, versionA.value, versionB.value)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  } finally {
    comparing.value = false
  }
}

function diffTypeTag(type: string): 'primary' | 'success' | 'danger' | 'warning' | 'info' {
  switch (type) {
    case 'ADDED': return 'success'
    case 'REMOVED': return 'danger'
    case 'MODIFIED': return 'warning'
    default: return 'info'
  }
}
</script>

<style scoped>
.version-diff-panel {
  padding: 8px 0;
}
</style>
