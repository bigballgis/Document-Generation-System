<template>
  <div class="version-history" v-loading="loading">
    <el-table :data="versions" stripe>
      <el-table-column :label="$t('template.version')" width="120">
        <template #default="{ row }">
          {{ $t('template.versionNumber', { number: row.versionNumber }) }}
        </template>
      </el-table-column>
      <el-table-column prop="createdBy" :label="$t('common.createdBy')" width="140" />
      <el-table-column prop="createdAt" :label="$t('common.createdAt')" width="180" />
      <el-table-column prop="comment" label="Comment" min-width="200" show-overflow-tooltip />
      <el-table-column :label="$t('common.actions')" width="120" fixed="right">
        <template #default="{ row }">
          <el-popconfirm
            :title="$t('template.rollbackConfirm', { number: row.versionNumber })"
            @confirm="handleRollback(row)"
          >
            <template #reference>
              <el-button link type="warning" size="small">
                {{ $t('template.versionRollback') }}
              </el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getTemplateVersions, rollbackVersion, type TemplateVersionDTO } from '@/api/templates'

const props = defineProps<{ templateId: number }>()
const { t } = useI18n()
const loading = ref(false)
const versions = ref<TemplateVersionDTO[]>([])

async function fetchVersions() {
  loading.value = true
  try {
    versions.value = await getTemplateVersions(props.templateId)
  } catch {} finally {
    loading.value = false
  }
}

async function handleRollback(row: TemplateVersionDTO) {
  try {
    await rollbackVersion(props.templateId, row.id)
    ElMessage.success(t('template.rollbackSuccess'))
    fetchVersions()
  } catch {}
}

onMounted(fetchVersions)
</script>
