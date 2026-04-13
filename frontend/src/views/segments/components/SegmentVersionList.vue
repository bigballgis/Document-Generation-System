<template>
  <div class="segment-version-list">
    <el-table :data="versions" v-loading="loading" stripe>
      <el-table-column prop="versionNumber" :label="$t('template.version')" width="120">
        <template #default="{ row }">v{{ row.versionNumber }}</template>
      </el-table-column>
      <el-table-column prop="createdBy" :label="$t('common.createdBy')" width="140" />
      <el-table-column prop="createdAt" :label="$t('common.createdAt')" min-width="180" />
      <el-table-column :label="$t('common.actions')" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleRollback(row)">
            {{ $t('template.versionRollback') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 16px">
      <h4>{{ $t('segment.compareVersions') }}</h4>
      <el-form :inline="true">
        <el-form-item :label="$t('segment.selectVersionA')">
          <el-select v-model="diffVersionA" style="width: 120px">
            <el-option v-for="v in versions" :key="v.id" :label="`v${v.versionNumber}`" :value="v.versionNumber" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('segment.selectVersionB')">
          <el-select v-model="diffVersionB" style="width: 120px">
            <el-option v-for="v in versions" :key="v.id" :label="`v${v.versionNumber}`" :value="v.versionNumber" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!diffVersionA || !diffVersionB" @click="handleCompare">
            {{ $t('template.compareVersions') }}
          </el-button>
        </el-form-item>
      </el-form>
      <el-card v-if="diffResult" shadow="never" style="margin-top: 8px">
        <p>{{ $t('template.diffSummary') }}: +{{ diffResult.summary.added }} / -{{ diffResult.summary.removed }} / ~{{ diffResult.summary.modified }}</p>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSegmentVersions, rollbackSegmentVersion, getSegmentVersionDiff } from '@/api/segments'
import type { SegmentVersion } from '@/types/segment'
import type { VersionDiffResult } from '@/api/templates'

const props = defineProps<{ segmentId: number }>()
const emit = defineEmits<{ updated: [] }>()
const { t } = useI18n()

const loading = ref(false)
const versions = ref<SegmentVersion[]>([])
const diffVersionA = ref<number | null>(null)
const diffVersionB = ref<number | null>(null)
const diffResult = ref<VersionDiffResult | null>(null)

async function fetchVersions() {
  loading.value = true
  try {
    versions.value = await getSegmentVersions(props.segmentId)
  } catch { /* handled */ } finally {
    loading.value = false
  }
}

async function handleRollback(row: SegmentVersion) {
  try {
    await ElMessageBox.confirm(
      t('segment.rollbackConfirm', { number: row.versionNumber }),
      t('common.warning'),
    )
    await rollbackSegmentVersion(props.segmentId, row.id)
    ElMessage.success(t('segment.rollbackSuccess'))
    fetchVersions()
    emit('updated')
  } catch { /* cancelled */ }
}

async function handleCompare() {
  if (!diffVersionA.value || !diffVersionB.value) return
  try {
    diffResult.value = await getSegmentVersionDiff(props.segmentId, diffVersionA.value, diffVersionB.value)
  } catch { /* handled */ }
}

onMounted(fetchVersions)
</script>
