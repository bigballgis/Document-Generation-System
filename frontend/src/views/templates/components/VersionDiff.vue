<template>
  <div class="version-diff">
    <el-form :inline="true" style="margin-bottom: 16px">
      <el-form-item :label="$t('template.selectVersionA')">
        <el-select v-model="versionA" :placeholder="$t('template.selectVersionA')" style="width: 160px">
          <el-option
            v-for="v in versions"
            :key="v.versionNumber"
            :label="`v${v.versionNumber}`"
            :value="v.versionNumber"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('template.selectVersionB')">
        <el-select v-model="versionB" :placeholder="$t('template.selectVersionB')" style="width: 160px">
          <el-option
            v-for="v in versions"
            :key="v.versionNumber"
            :label="`v${v.versionNumber}`"
            :value="v.versionNumber"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :disabled="!canCompare" :loading="loading" @click="handleCompare">
          {{ $t('template.compareVersions') }}
        </el-button>
      </el-form-item>
    </el-form>

    <template v-if="diffResult">
      <el-card shadow="never" style="margin-bottom: 16px">
        <template #header>{{ $t('template.diffSummary') }}</template>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-statistic :title="$t('template.diffAdded')" :value="diffResult.summary.added">
              <template #prefix><span style="color: #67c23a">+</span></template>
            </el-statistic>
          </el-col>
          <el-col :span="8">
            <el-statistic :title="$t('template.diffRemoved')" :value="diffResult.summary.removed">
              <template #prefix><span style="color: #f56c6c">-</span></template>
            </el-statistic>
          </el-col>
          <el-col :span="8">
            <el-statistic :title="$t('template.diffModified')" :value="diffResult.summary.modified">
              <template #prefix><span style="color: #e6a23c">~</span></template>
            </el-statistic>
          </el-col>
        </el-row>
      </el-card>

      <el-card shadow="never">
        <el-table :data="allDiffs" stripe>
          <el-table-column prop="field" label="Field" min-width="180" />
          <el-table-column :label="$t('template.diffModified')" width="100">
            <template #default="{ row }">
              <el-tag :type="diffTagType(row.type)" size="small">{{ row.type }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="`v${versionA}`" min-width="250">
            <template #default="{ row }">
              <span :class="{ 'diff-removed': row.type === 'REMOVED' || row.type === 'MODIFIED' }">
                {{ row.oldValue || '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column :label="`v${versionB}`" min-width="250">
            <template #default="{ row }">
              <span :class="{ 'diff-added': row.type === 'ADDED' || row.type === 'MODIFIED' }">
                {{ row.newValue || '-' }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <el-empty v-else-if="!loading" :description="$t('common.noData')" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  getTemplateVersions, getVersionDiff,
  type TemplateVersionDTO, type VersionDiffResult, type DiffEntry,
} from '@/api/templates'

const props = defineProps<{ templateId: number }>()

const loading = ref(false)
const versions = ref<TemplateVersionDTO[]>([])
const versionA = ref<number | null>(null)
const versionB = ref<number | null>(null)
const diffResult = ref<VersionDiffResult | null>(null)

const canCompare = computed(() =>
  versionA.value != null && versionB.value != null && versionA.value !== versionB.value
)

const allDiffs = computed<DiffEntry[]>(() => {
  if (!diffResult.value) return []
  return [
    ...diffResult.value.textDiffs,
    ...diffResult.value.variableDiffs,
    ...diffResult.value.dataSourceDiffs,
    ...diffResult.value.expressionDiffs,
  ]
})

function diffTagType(type: string) {
  if (type === 'ADDED') return 'success'
  if (type === 'REMOVED') return 'danger'
  return 'warning'
}

async function fetchVersions() {
  try {
    versions.value = await getTemplateVersions(props.templateId)
  } catch {}
}

async function handleCompare() {
  if (!canCompare.value) return
  loading.value = true
  try {
    diffResult.value = await getVersionDiff(props.templateId, versionA.value!, versionB.value!)
  } catch {} finally {
    loading.value = false
  }
}

onMounted(fetchVersions)
</script>

<style scoped>
.diff-added {
  background-color: #e1f3d8;
  padding: 2px 4px;
  border-radius: 2px;
}
.diff-removed {
  background-color: #fde2e2;
  padding: 2px 4px;
  border-radius: 2px;
  text-decoration: line-through;
}
</style>

