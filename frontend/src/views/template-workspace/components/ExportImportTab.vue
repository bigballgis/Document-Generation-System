<template>
  <div class="export-import-tab">
    <!-- Export Section -->
    <div class="section">
      <h3>{{ t('workspace.exportImport.exportTitle') }}</h3>

      <!-- Export Summary -->
      <el-row :gutter="16" class="export-summary">
        <el-col :span="6">
          <div class="stat-card">
            <span class="stat-value">{{ exportSummary.segmentCount }}</span>
            <span class="stat-label">{{ t('workspace.exportImport.segmentCount') }}</span>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <span class="stat-value">{{ exportSummary.dataSourceCount }}</span>
            <span class="stat-label">{{ t('workspace.exportImport.dataSourceCount') }}</span>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <span class="stat-value">{{ exportSummary.expressionCount }}</span>
            <span class="stat-label">{{ t('workspace.exportImport.expressionCount') }}</span>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <span class="stat-value">{{ exportSummary.testDataCount }}</span>
            <span class="stat-label">{{ t('workspace.exportImport.testDataCount') }}</span>
          </div>
        </el-col>
      </el-row>

      <!-- Export Buttons -->
      <div class="export-actions">
        <el-tooltip :content="t('workspace.exportImport.draftExportDisabled')" :disabled="!isExportPackageDisabled">
          <el-button type="primary" :loading="exportZipLoading" :disabled="isExportPackageDisabled || exportLoading" @click="handleExportZip">
            {{ t('workspace.exportImport.exportPackage') }}
          </el-button>
        </el-tooltip>
        <el-button :loading="exportConfigLoading" :disabled="exportLoading" @click="handleExportConfig">
          {{ t('workspace.exportImport.exportConfig') }}
        </el-button>
      </div>
    </div>

    <el-divider />

    <!-- Import Section -->
    <div class="section">
      <h3>{{ t('workspace.exportImport.importTitle') }}</h3>

      <!-- ZIP Import -->
      <el-upload
        drag
        accept=".zip"
        :auto-upload="false"
        :show-file-list="false"
        :on-change="handleImportZip"
        :disabled="importZipLoading"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">{{ t('workspace.exportImport.importPackage') }}</div>
        <template #tip>
          <div class="el-upload__tip">.zip files only</div>
        </template>
      </el-upload>

      <!-- JSON Config Import -->
      <div style="margin-top: 16px">
        <el-upload accept=".json" :auto-upload="false" :show-file-list="false" :on-change="handleImportConfig" :disabled="importConfigLoading">
          <el-button :loading="importConfigLoading">{{ t('workspace.exportImport.importConfig') }}</el-button>
        </el-upload>
      </div>
    </div>

    <!-- Conflict Resolution Dialog -->
    <el-dialog v-model="conflictDialogVisible" :title="t('workspace.exportImport.conflictTitle')" width="480px">
      <el-radio-group v-model="conflictResolution">
        <el-radio value="rename">{{ t('workspace.exportImport.conflictRename') }}</el-radio>
        <el-radio value="overwrite">{{ t('workspace.exportImport.conflictOverwrite') }}</el-radio>
        <el-radio value="cancel">{{ t('workspace.exportImport.conflictCancel') }}</el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="conflictDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleConflictResolve">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Configure Credentials Dialog -->
    <el-dialog v-model="credentialsDialogVisible" :title="t('workspace.exportImport.configureCredentials')" width="600px">
      <div v-for="ds in credentialDataSources" :key="ds.id" class="credential-item">
        <h4>{{ ds.name }} ({{ ds.type }})</h4>
        <el-input
          v-for="(_, field) in credentialValues[ds.id]"
          :key="field"
          v-model="credentialValues[ds.id][field]"
          :placeholder="String(field)"
          type="password"
          show-password
          style="margin-bottom: 8px"
        />
      </div>
      <template #footer>
        <el-button @click="credentialsDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleCredentialsSubmit">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- Import Success Dialog -->
    <el-dialog v-model="importSuccessDialogVisible" :title="t('workspace.exportImport.importSuccess')" width="480px">
      <template v-if="importedTemplate">
        <p>{{ importedTemplate.name }}</p>
      </template>
      <template #footer>
        <el-button type="primary" @click="goToImportedWorkspace">{{ t('workspace.exportImport.goToWorkspace') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { exportCompositeAsZip, exportCompositeConfig, importCompositeFromZip } from '@/api/composite-templates'
import { importConfig } from '@/api/import-export'
import { updateDataSource } from '@/api/data-sources'
import type { TemplateDTO } from '@/api/templates'

const store = useTemplateWorkspaceStore()
const { t } = useI18n()
const router = useRouter()

// ── Export state ──
const exportZipLoading = ref(false)
const exportConfigLoading = ref(false)
const exportLoading = computed(() => exportZipLoading.value || exportConfigLoading.value)

// ── Import state ──
const importZipLoading = ref(false)
const importConfigLoading = ref(false)

// ── Dialog state ──
const conflictDialogVisible = ref(false)
const conflictResolution = ref<'rename' | 'overwrite' | 'cancel'>('rename')
const pendingImportFile = ref<File | null>(null)

const credentialsDialogVisible = ref(false)
const credentialDataSources = ref<Array<{ id: number; name: string; type: string }>>([])
const credentialValues = ref<Record<number, Record<string, string>>>({})

const importSuccessDialogVisible = ref(false)
const importedTemplate = ref<TemplateDTO | null>(null)

// ── Export summary ──
const exportSummary = computed(() => ({
  segmentCount: store.assemblyConfig?.segments?.length ?? 0,
  dataSourceCount: store.dataSources.length,
  expressionCount: store.expressions.length,
  testDataCount: store.testCases.length,
}))

const isExportPackageDisabled = computed(() => store.isDraft)

// ── Ensure testCases loaded ──
onMounted(async () => {
  if (store.testCases.length === 0) {
    await store.refreshTestCases()
  }
})

// ── Download helper ──
function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

// ── Export ZIP ──
async function handleExportZip() {
  exportZipLoading.value = true
  try {
    const blob = await exportCompositeAsZip(store.templateId)
    triggerDownload(blob as unknown as Blob, `composite-template-${store.templateId}.zip`)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.exportFailed'))
  } finally {
    exportZipLoading.value = false
  }
}

// ── Export Config ──
async function handleExportConfig() {
  exportConfigLoading.value = true
  try {
    const blob = await exportCompositeConfig(store.templateId)
    triggerDownload(blob as unknown as Blob, `composite-config-${store.templateId}.json`)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.exportFailed'))
  } finally {
    exportConfigLoading.value = false
  }
}

// ── Import ZIP ──
async function handleImportZip(uploadFile: any) {
  const file = uploadFile.raw
  if (!file) return
  importZipLoading.value = true
  try {
    const result = await importCompositeFromZip(file)
    importedTemplate.value = result
    importSuccessDialogVisible.value = true
  } catch (e: any) {
    if (e.response?.status === 409) {
      pendingImportFile.value = file
      conflictDialogVisible.value = true
    } else if (e.response?.status === 400) {
      ElMessage.error(t('workspace.exportImport.invalidPackage'))
    } else {
      ElMessage.error(e.response?.data?.message || e.message || t('message.importFailed'))
    }
  } finally {
    importZipLoading.value = false
  }
}

// ── Conflict resolution ──
async function handleConflictResolve() {
  if (!pendingImportFile.value) return
  if (conflictResolution.value === 'cancel') {
    conflictDialogVisible.value = false
    pendingImportFile.value = null
    return
  }
  importZipLoading.value = true
  conflictDialogVisible.value = false
  try {
    const result = await importCompositeFromZip(pendingImportFile.value)
    importedTemplate.value = result
    importSuccessDialogVisible.value = true
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.importFailed'))
  } finally {
    importZipLoading.value = false
    pendingImportFile.value = null
  }
}

// ── Credentials submit ──
async function handleCredentialsSubmit() {
  for (const ds of credentialDataSources.value) {
    const values = credentialValues.value[ds.id]
    if (values) {
      try {
        await updateDataSource(ds.id, { name: ds.name, type: ds.type as any, configJson: JSON.stringify(values) })
      } catch (e: any) {
        ElMessage.error(`${ds.name}: ${e.response?.data?.message || e.message}`)
      }
    }
  }
  credentialsDialogVisible.value = false
  ElMessage.success(t('message.saveSuccess'))
}

// ── Import JSON Config ──
async function handleImportConfig(uploadFile: any) {
  const file = uploadFile.raw
  if (!file) return
  importConfigLoading.value = true
  try {
    const result = await importConfig(file)
    importedTemplate.value = result
    ElMessage.success(t('workspace.exportImport.importSuccess'))
    importSuccessDialogVisible.value = true
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.importFailed'))
  } finally {
    importConfigLoading.value = false
  }
}

// ── Navigate to imported workspace ──
function goToImportedWorkspace() {
  if (importedTemplate.value) {
    router.push(`/templates/${importedTemplate.value.id}/workspace`)
  }
  importSuccessDialogVisible.value = false
}
</script>

<style scoped>
.export-import-tab {
  padding: 16px 0;
}
.section h3 {
  margin-bottom: 16px;
}
.export-summary {
  margin-bottom: 16px;
}
.stat-card {
  text-align: center;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}
.stat-value {
  display: block;
  font-size: 24px;
  font-weight: 600;
  color: var(--el-color-primary);
}
.stat-label {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
.export-actions {
  display: flex;
  gap: 12px;
}
.credential-item {
  margin-bottom: 16px;
}
.credential-item h4 {
  margin-bottom: 8px;
}
</style>
