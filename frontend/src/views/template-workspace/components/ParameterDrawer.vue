<template>
  <div class="parameter-drawer" :class="{ 'is-collapsed': collapsed }">
    <!-- Collapsed state: narrow icon strip -->
    <div v-if="collapsed" class="drawer-collapsed" @click="emit('update:collapsed', false)">
      <el-icon :size="20"><List /></el-icon>
      <span class="collapsed-label">{{ t('workspace.design.parameters') }}</span>
    </div>

    <!-- Expanded state: full parameter panel -->
    <div v-else class="drawer-expanded">
      <div class="drawer-header">
        <span class="drawer-title">{{ t('workspace.design.parameters') }}</span>
        <div class="drawer-actions">
          <el-button
            v-if="!readonly"
            size="small"
            @click="handleScanPlaceholders"
            :loading="scanning"
          >
            {{ t('parameter.scanTemplate') }}
          </el-button>
          <el-button size="small" circle @click="emit('update:collapsed', true)">
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- Toolbar (edit mode only) -->
      <div v-if="!readonly" class="drawer-toolbar">
        <el-button size="small" type="primary" @click="handleAddParameter">
          {{ t('parameter.addParameter') }}
        </el-button>
        <el-button size="small" @click="jsonImportVisible = true">
          {{ t('parameter.jsonImport') }}
        </el-button>
      </div>

      <!-- Parameter tree -->
      <div class="drawer-content">
        <el-empty v-if="store.parameters.length === 0" :description="t('parameter.empty')" />
        <el-tree
          v-else
          :data="treeData"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
        >
          <template #default="{ data }">
            <div class="tree-node" :class="{ 'is-new': newParamIds.has(data.id) }">
              <span class="node-name">{{ data.name }}</span>
              <el-tag size="small" type="info">{{ data.dataType }}</el-tag>
              <el-tag v-if="data.required" size="small" type="danger">{{ t('parameter.required') }}</el-tag>
              <div v-if="!readonly" class="node-actions">
                <el-button link size="small" @click.stop="handleDeleteParameter(data.id)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
          </template>
        </el-tree>
      </div>

      <!-- Scan result dialog -->
      <el-dialog v-model="scanDialogVisible" :title="t('parameter.scanDialog.title')" width="580px" append-to-body>
        <div v-if="scanResult" class="scan-result">
          <div class="scan-section">
            <h4>{{ t('parameter.scanDialog.unmatched') }} ({{ scanResult.unmatchedPlaceholders.length }})</h4>
            <ul v-if="scanResult.unmatchedPlaceholders.length">
              <li v-for="p in scanResult.unmatchedPlaceholders" :key="p.fullPath">{{ p.fullPath }}</li>
            </ul>
          </div>
        </div>
        <template #footer>
          <el-button @click="scanDialogVisible = false">{{ t('common.close') }}</el-button>
          <el-button
            v-if="scanResult && scanResult.unmatchedPlaceholders.length > 0"
            type="primary"
            :loading="autoCreating"
            @click="handleAutoCreate"
          >
            {{ t('parameter.scanDialog.autoCreate') }}
          </el-button>
        </template>
      </el-dialog>

      <!-- JSON Import Dialog -->
      <JsonImportDialog
        v-model:visible="jsonImportVisible"
        :template-id="store.templateId"
        @imported="handleJsonImported"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { List, ArrowRight, Delete } from '@element-plus/icons-vue'
import { useTemplateWorkspaceStore } from '@/stores/templateWorkspace'
import { createParameter, deleteParameter, scanPlaceholders, autoCreateParameters } from '@/api/parameters'
import type { ParameterDTO, ScanResultDTO } from '@/types/parameter'
import JsonImportDialog from './JsonImportDialog.vue'

defineProps<{
  collapsed: boolean
  readonly: boolean
}>()

const emit = defineEmits<{
  'update:collapsed': [value: boolean]
}>()

const { t } = useI18n()
const store = useTemplateWorkspaceStore()

const scanning = ref(false)
const scanDialogVisible = ref(false)
const scanResult = ref<ScanResultDTO | null>(null)
const autoCreating = ref(false)
const jsonImportVisible = ref(false)
const newParamIds = ref(new Set<number>())

interface TreeNode {
  id: number
  name: string
  dataType: string
  required: boolean
  children: TreeNode[]
}

function toTreeNodes(params: ParameterDTO[]): TreeNode[] {
  return params.map(p => ({
    id: p.id,
    name: p.name,
    dataType: p.dataType,
    required: p.required,
    children: p.children?.length ? toTreeNodes(p.children) : [],
  }))
}

const treeData = computed(() => toTreeNodes(store.parameters))

async function handleAddParameter() {
  try {
    const param = await createParameter(store.templateId, { name: `param_${Date.now() % 10000}` })
    await store.refreshParameters()
    newParamIds.value.add(param.id)
    setTimeout(() => newParamIds.value.delete(param.id), 3000)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  }
}

async function handleDeleteParameter(id: number) {
  try {
    await ElMessageBox.confirm(t('confirm.deleteMessage'), t('confirm.deleteTitle'), { type: 'warning' })
  } catch { return }
  try {
    await deleteParameter(id)
    await store.refreshParameters()
    ElMessage.success(t('message.deleteSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.deleteFailed'))
  }
}

async function handleScanPlaceholders() {
  scanning.value = true
  try {
    scanResult.value = await scanPlaceholders(store.templateId)
    if (scanResult.value.unmatchedPlaceholders.length > 0) {
      scanDialogVisible.value = true
    } else {
      ElMessage.success(t('message.operationSuccess'))
    }
    // Highlight newly discovered params
    newParamIds.value.clear()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  } finally {
    scanning.value = false
  }
}

async function handleAutoCreate() {
  autoCreating.value = true
  try {
    await autoCreateParameters(store.templateId)
    await store.refreshParameters()
    scanDialogVisible.value = false
    ElMessage.success(t('message.createSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || t('message.operationFailed'))
  } finally {
    autoCreating.value = false
  }
}

async function handleJsonImported() {
  await store.refreshParameters()
}
</script>

<style scoped>
.parameter-drawer {
  height: 100%;
  border-left: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
}
.parameter-drawer.is-collapsed {
  width: 40px;
  min-width: 40px;
}
.drawer-collapsed {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 16px;
  gap: 8px;
  cursor: pointer;
  height: 100%;
}
.drawer-collapsed:hover {
  background: var(--el-fill-color-light);
}
.collapsed-label {
  writing-mode: vertical-rl;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.drawer-expanded {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 280px;
}
.drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 12px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.drawer-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.drawer-actions {
  display: flex;
  gap: 4px;
  align-items: center;
}
.drawer-toolbar {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.drawer-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 2px 0;
}
.tree-node.is-new {
  background: var(--el-color-success-light-9);
  border-radius: 4px;
}
.node-name {
  font-size: 13px;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node-actions {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.2s;
}
.tree-node:hover .node-actions {
  opacity: 1;
}
.scan-result {
  max-height: 300px;
  overflow-y: auto;
}
.scan-section h4 {
  margin-bottom: 8px;
}
.scan-section ul {
  padding-left: 20px;
  margin: 0;
}
.scan-section li {
  font-family: monospace;
  font-size: 13px;
  line-height: 1.8;
}
</style>
