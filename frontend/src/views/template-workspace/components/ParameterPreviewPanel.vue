<template>
  <div class="parameter-preview-panel">
    <el-tabs v-model="activeTab">
      <el-tab-pane :label="t('parameter.preview.jsonSchema')" name="jsonSchema">
        <pre class="preview-code" :class="{ 'highlight-flash': jsonSchemaFlash }">{{ jsonSchemaText }}</pre>
      </el-tab-pane>
      <el-tab-pane :label="t('parameter.preview.sampleBody')" name="sampleBody">
        <pre class="preview-code" :class="{ 'highlight-flash': sampleBodyFlash }">{{ sampleBodyText }}</pre>
      </el-tab-pane>
      <el-tab-pane :label="t('parameter.preview.placeholderMatch')" name="placeholderMatch">
        <div v-if="!scanResult" class="placeholder-stub">
          <el-button size="small" @click="handleScan">{{ t('parameter.scanTemplate') }}</el-button>
          <p>{{ t('parameter.preview.placeholderMatchHint') }}</p>
        </div>
        <div v-else class="placeholder-list">
          <div v-for="item in scanResult.matched" :key="'m-' + item.fullPath" class="placeholder-item matched">
            <el-icon color="var(--el-color-success)"><CircleCheckFilled /></el-icon>
            <span>{{ item.fullPath }}</span>
          </div>
          <div v-for="item in scanResult.unmatchedPlaceholders" :key="'u-' + item.fullPath" class="placeholder-item unmatched">
            <el-icon color="var(--el-color-danger)"><CircleCloseFilled /></el-icon>
            <span>{{ item.fullPath }}</span>
          </div>
          <div v-for="item in scanResult.unusedParameters" :key="'x-' + item.id" class="placeholder-item unused">
            <el-icon color="var(--el-color-warning)"><WarningFilled /></el-icon>
            <span>{{ item.parameterPath }}</span>
          </div>
          <div v-if="scanResult.unmatchedPlaceholders.length > 0" class="create-missing-btn">
            <el-button size="small" type="primary" @click="$emit('autoCreate')">
              {{ t('parameter.preview.createMissing') }}
            </el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { CircleCheckFilled, CircleCloseFilled, WarningFilled } from '@element-plus/icons-vue'
import { scanPlaceholders } from '@/api/parameters'
import type { ParameterDTO, ScanResultDTO } from '@/types/parameter'
import { generateJsonSchema, generateSampleBody } from '@/composables/useParameterUtils'

const props = defineProps<{
  parameters: ParameterDTO[]
  templateId: number
}>()

defineEmits<{
  autoCreate: []
}>()

const { t } = useI18n()
const activeTab = ref('jsonSchema')
const scanResult = ref<ScanResultDTO | null>(null)
const jsonSchemaFlash = ref(false)
const sampleBodyFlash = ref(false)

// Debounced scan
let scanTimer: ReturnType<typeof setTimeout> | null = null
let scanAbortController: AbortController | null = null

const jsonSchemaText = computed(() => {
  return JSON.stringify(generateJsonSchema(props.parameters), null, 2)
})

const sampleBodyText = computed(() => {
  return JSON.stringify(generateSampleBody(props.parameters), null, 2)
})

// Watch for parameter changes — trigger highlight flash and debounced scan
watch(() => props.parameters, () => {
  // Flash highlight
  jsonSchemaFlash.value = true
  sampleBodyFlash.value = true
  setTimeout(() => {
    jsonSchemaFlash.value = false
    sampleBodyFlash.value = false
  }, 600)

  // Debounced scan (2 seconds)
  if (scanTimer) clearTimeout(scanTimer)
  scanTimer = setTimeout(() => {
    debouncedScan()
  }, 2000)
}, { deep: true })

async function debouncedScan() {
  // Cancel previous request
  if (scanAbortController) {
    scanAbortController.abort()
  }
  scanAbortController = new AbortController()

  try {
    scanResult.value = await scanPlaceholders(props.templateId)
  } catch {
    // Silent failure — keep previous scan result
  }
}

async function handleScan() {
  try {
    scanResult.value = await scanPlaceholders(props.templateId)
  } catch {
    // Silent failure
  }
}
</script>

<style scoped>
.parameter-preview-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px;
  background: var(--el-bg-color);
  height: 100%;
}
.preview-code {
  font-family: monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 500px;
  overflow-y: auto;
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 4px;
  margin: 0;
  transition: background-color 0.3s ease;
}
.highlight-flash {
  background-color: var(--el-color-warning-light-9) !important;
}
.placeholder-stub {
  padding: 24px;
  text-align: center;
  color: var(--el-text-color-secondary);
}
.placeholder-stub p {
  margin-top: 8px;
}
.placeholder-list {
  max-height: 500px;
  overflow-y: auto;
}
.placeholder-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  font-family: monospace;
  font-size: 13px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}
.create-missing-btn {
  padding: 12px 8px;
  text-align: center;
}
</style>
