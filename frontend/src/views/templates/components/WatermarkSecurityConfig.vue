<template>
  <div class="watermark-security-config" v-loading="loading">
    <el-alert
      v-if="templateType === 'SINGLE'"
      type="info"
      :closable="false"
      show-icon
      class="mb-16"
      :title="t('watermark.compositeOnlyNotice')"
      :description="t('watermark.singleTemplateEditingDisabled')"
    />

    <el-row :gutter="24">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>{{ t('watermark.title') }}</template>
          <el-form :model="watermarkForm" label-width="150px" size="default" :disabled="!watermarkEditable">
            <el-form-item :label="t('watermark.textWatermark')">
              <el-switch v-model="watermarkForm.textEnabled" />
            </el-form-item>
            <template v-if="watermarkForm.textEnabled">
              <el-form-item :label="t('watermark.text')">
                <el-input
                  v-model="watermarkForm.text"
                  type="textarea"
                  :rows="2"
                  :placeholder="'CONFIDENTIAL or {username}'"
                />
              </el-form-item>
              <el-form-item :label="t('watermark.fontSize')">
                <el-input-number v-model="watermarkForm.fontSize" :min="8" :max="72" />
              </el-form-item>
              <el-form-item :label="t('watermark.color')">
                <el-color-picker v-model="watermarkForm.colorHex" />
              </el-form-item>
              <el-form-item :label="t('watermark.opacity')">
                <el-slider v-model="watermarkForm.opacityPct" :min="0" :max="100" :step="5" show-input />
              </el-form-item>
              <el-form-item :label="t('watermark.rotation')">
                <el-input-number v-model="watermarkForm.rotation" :min="-90" :max="90" />
              </el-form-item>
            </template>

            <el-divider />

            <el-form-item :label="t('watermark.imageWatermark')">
              <el-switch v-model="watermarkForm.imageEnabled" />
            </el-form-item>
            <template v-if="watermarkForm.imageEnabled">
              <el-form-item :label="t('watermark.image')">
                <div class="image-field">
                  <el-input
                    v-model="watermarkForm.imageSource"
                    type="textarea"
                    :rows="4"
                    :placeholder="t('watermark.imageSourcePlaceholder')"
                  />
                  <div class="image-actions">
                    <input
                      ref="imageFileRef"
                      type="file"
                      accept="image/png,image/jpeg,image/webp,image/gif"
                      class="hidden-file-input"
                      :disabled="!watermarkEditable"
                      @change="onImageFileSelected"
                    />
                    <el-button size="small" @click="triggerImagePick">{{ t('watermark.chooseFile') }}</el-button>
                  </div>
                  <p class="hint">{{ t('watermark.imageSourceHint') }}</p>
                </div>
              </el-form-item>
              <el-form-item :label="t('watermark.position')">
                <el-select v-model="watermarkForm.imagePosition">
                  <el-option :label="'CENTER'" value="CENTER" />
                  <el-option :label="'TOP_LEFT'" value="TOP_LEFT" />
                  <el-option :label="'TOP_RIGHT'" value="TOP_RIGHT" />
                  <el-option :label="'BOTTOM_LEFT'" value="BOTTOM_LEFT" />
                  <el-option :label="'BOTTOM_RIGHT'" value="BOTTOM_RIGHT" />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('watermark.opacity')">
                <el-slider
                  v-model="watermarkForm.imageOpacityPct"
                  :min="0"
                  :max="100"
                  :step="5"
                  show-input
                />
              </el-form-item>
            </template>
          </el-form>
          <div class="watermark-actions">
            <el-button type="primary" :disabled="!watermarkEditable" :loading="saving" @click="handleSaveWatermark">
              {{ t('common.save') }}
            </el-button>
            <el-button :loading="saving" @click="handleClearWatermark">{{ t('watermark.clearWatermarkConfig') }}</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never">
          <template #header>{{ t('security.title') }}</template>
          <el-alert type="warning" :closable="false" show-icon class="mb-16">
            {{ t('security.notPersistedHint') }}
          </el-alert>
          <el-form :model="securityForm" label-width="140px" size="default">
            <el-form-item :label="t('security.pdfPassword')">
              <el-input v-model="securityForm.pdfPassword" type="password" show-password placeholder="Optional" disabled />
            </el-form-item>
            <el-form-item :label="t('security.disablePrint')">
              <el-switch v-model="securityForm.disablePrint" disabled />
            </el-form-item>
            <el-form-item :label="t('security.disableCopy')">
              <el-switch v-model="securityForm.disableCopy" disabled />
            </el-form-item>
            <el-form-item :label="t('security.disableEdit')">
              <el-switch v-model="securityForm.disableEdit" disabled />
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  getTemplate,
  updateTemplateRenderConfig,
  clearTemplateRenderConfig,
  type RenderConfigDocument,
} from '@/api/templates'

const props = defineProps<{ templateId: number }>()

const { t } = useI18n()
const loading = ref(false)
const saving = ref(false)
const templateType = ref<'SINGLE' | 'COMPOSITE'>('SINGLE')
const imageFileRef = ref<HTMLInputElement | null>(null)

/** Watermark in render_config is applied for composite generation only. */
const watermarkEditable = computed(() => templateType.value === 'COMPOSITE')

const watermarkForm = reactive({
  textEnabled: false,
  text: '',
  fontSize: 36,
  colorHex: '#cccccc',
  opacityPct: 30,
  rotation: -45,
  imageEnabled: false,
  imageSource: '',
  imagePosition: 'CENTER' as 'CENTER' | 'TOP_LEFT' | 'TOP_RIGHT' | 'BOTTOM_LEFT' | 'BOTTOM_RIGHT',
  imageOpacityPct: 30,
})

const securityForm = reactive({
  pdfPassword: '',
  disablePrint: false,
  disableCopy: false,
  disableEdit: false,
})

function resetWatermarkFormFromDoc(doc: RenderConfigDocument | null) {
  const tw = doc?.textWatermark
  const iw = doc?.imageWatermark
  watermarkForm.textEnabled = !!tw && !!(tw.text && String(tw.text).trim())
  watermarkForm.text = tw?.text ?? ''
  watermarkForm.fontSize = tw?.fontSize ?? 36
  watermarkForm.colorHex = tw?.color ?? '#cccccc'
  watermarkForm.opacityPct = Math.round(((tw?.opacity ?? 0.3) as number) * 100)
  watermarkForm.rotation = tw?.rotation ?? -45

  watermarkForm.imageEnabled = !!iw && !!(iw.imageSource && String(iw.imageSource).trim())
  watermarkForm.imageSource = iw?.imageSource ?? ''
  const pos = iw?.position ?? 'CENTER'
  watermarkForm.imagePosition = (['CENTER', 'TOP_LEFT', 'TOP_RIGHT', 'BOTTOM_LEFT', 'BOTTOM_RIGHT'].includes(pos)
    ? pos
    : 'CENTER') as typeof watermarkForm.imagePosition
  watermarkForm.imageOpacityPct = Math.round(((iw?.opacity ?? 0.3) as number) * 100)
}

async function loadFromServer() {
  if (!props.templateId || props.templateId <= 0) return
  loading.value = true
  try {
    const tpl = await getTemplate(props.templateId)
    templateType.value = tpl.templateType ?? 'SINGLE'
    const raw = tpl.renderConfig
    if (raw && raw.trim()) {
      try {
        const doc = JSON.parse(raw) as RenderConfigDocument
        resetWatermarkFormFromDoc(doc)
      } catch {
        resetWatermarkFormFromDoc(null)
      }
    } else {
      resetWatermarkFormFromDoc(null)
    }
  } catch {
    resetWatermarkFormFromDoc(null)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.templateId,
  () => {
    void loadFromServer()
  },
  { immediate: true },
)

function triggerImagePick() {
  if (!watermarkEditable.value) return
  imageFileRef.value?.click()
}

function onImageFileSelected(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    const r = reader.result
    if (typeof r === 'string') {
      watermarkForm.imageSource = r
      watermarkForm.imageEnabled = true
    }
    input.value = ''
  }
  reader.readAsDataURL(file)
}

function buildPayload(): RenderConfigDocument | null {
  const doc: RenderConfigDocument = { schemaVersion: 1 }
  if (watermarkForm.textEnabled) {
    const text = watermarkForm.text?.trim() ?? ''
    if (!text) {
      ElMessage.error(t('watermark.validationTextRequired'))
      return null
    }
    doc.textWatermark = {
      text,
      fontSize: watermarkForm.fontSize,
      color: watermarkForm.colorHex,
      opacity: watermarkForm.opacityPct / 100,
      rotation: watermarkForm.rotation,
    }
  }
  if (watermarkForm.imageEnabled) {
    const src = watermarkForm.imageSource?.trim() ?? ''
    if (!src) {
      ElMessage.error(t('watermark.validationImageRequired'))
      return null
    }
    const lower = src.toLowerCase()
    if (lower.startsWith('http://') || lower.startsWith('https://')) {
      ElMessage.error(t('watermark.validationNoRemoteImageUrl'))
      return null
    }
    doc.imageWatermark = {
      imageSource: src,
      position: watermarkForm.imagePosition,
      opacity: watermarkForm.imageOpacityPct / 100,
    }
  }
  return doc
}

async function handleSaveWatermark() {
  if (!props.templateId || !watermarkEditable.value) return
  const doc = buildPayload()
  if (doc === null) return
  const hasText = !!doc.textWatermark
  const hasImg = !!doc.imageWatermark
  saving.value = true
  try {
    if (!hasText && !hasImg) {
      await clearTemplateRenderConfig(props.templateId)
      resetWatermarkFormFromDoc(null)
      ElMessage.success(t('message.saveSuccess'))
      return
    }
    await updateTemplateRenderConfig(props.templateId, doc)
    ElMessage.success(t('message.saveSuccess'))
    await loadFromServer()
  } catch (e: unknown) {
    const msg = e && typeof e === 'object' && 'message' in e ? String((e as { message?: string }).message) : ''
    ElMessage.error(msg || t('message.operationFailed'))
  } finally {
    saving.value = false
  }
}

async function handleClearWatermark() {
  if (!props.templateId) return
  saving.value = true
  try {
    await clearTemplateRenderConfig(props.templateId)
    resetWatermarkFormFromDoc(null)
    ElMessage.success(t('message.saveSuccess'))
  } catch {
    ElMessage.error(t('message.operationFailed'))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.watermark-security-config {
  padding: 0;
}
.mb-16 {
  margin-bottom: 16px;
}
.hidden-file-input {
  display: none;
}
.image-field {
  width: 100%;
}
.image-actions {
  margin-top: 8px;
}
.hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.watermark-actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
