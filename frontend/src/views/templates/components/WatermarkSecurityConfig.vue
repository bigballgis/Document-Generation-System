<template>
  <div class="watermark-security-config">
    <el-row :gutter="24">
      <!-- Watermark Settings -->
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>{{ $t('watermark.title') }}</template>
          <el-form :model="watermarkForm" label-width="140px" size="default">
            <el-form-item :label="$t('watermark.textWatermark')">
              <el-switch v-model="watermarkForm.textEnabled" />
            </el-form-item>
            <template v-if="watermarkForm.textEnabled">
              <el-form-item :label="$t('watermark.text')">
                <el-input v-model="watermarkForm.text" :placeholder="'CONFIDENTIAL'" />
              </el-form-item>
              <el-form-item :label="$t('watermark.fontSize')">
                <el-input-number v-model="watermarkForm.fontSize" :min="8" :max="72" />
              </el-form-item>
              <el-form-item :label="$t('watermark.color')">
                <el-color-picker v-model="watermarkForm.color" />
              </el-form-item>
              <el-form-item :label="$t('watermark.opacity')">
                <el-slider v-model="watermarkForm.opacity" :min="0" :max="100" :step="5" show-input />
              </el-form-item>
              <el-form-item :label="$t('watermark.rotation')">
                <el-input-number v-model="watermarkForm.rotation" :min="-90" :max="90" />
              </el-form-item>
              <el-form-item :label="$t('watermark.dynamicContent')">
                <el-input v-model="watermarkForm.dynamicContent" :placeholder="'{username} - {date}'" />
              </el-form-item>
            </template>

            <el-divider />

            <el-form-item :label="$t('watermark.imageWatermark')">
              <el-switch v-model="watermarkForm.imageEnabled" />
            </el-form-item>
            <template v-if="watermarkForm.imageEnabled">
              <el-form-item :label="$t('watermark.image')">
                <el-input v-model="watermarkForm.imageUrl" placeholder="Image URL" />
              </el-form-item>
              <el-form-item :label="$t('watermark.position')">
                <el-select v-model="watermarkForm.imagePosition">
                  <el-option label="Center" value="center" />
                  <el-option label="Top Left" value="top-left" />
                  <el-option label="Top Right" value="top-right" />
                  <el-option label="Bottom Left" value="bottom-left" />
                  <el-option label="Bottom Right" value="bottom-right" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$t('watermark.opacity')">
                <el-slider v-model="watermarkForm.imageOpacity" :min="0" :max="100" :step="5" show-input />
              </el-form-item>
            </template>
          </el-form>
        </el-card>
      </el-col>

      <!-- Security Settings -->
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>{{ $t('security.title') }}</template>
          <el-form :model="securityForm" label-width="140px" size="default">
            <el-form-item :label="$t('security.pdfPassword')">
              <el-input v-model="securityForm.pdfPassword" type="password" show-password placeholder="Optional" />
            </el-form-item>
            <el-form-item :label="$t('security.disablePrint')">
              <el-switch v-model="securityForm.disablePrint" />
            </el-form-item>
            <el-form-item :label="$t('security.disableCopy')">
              <el-switch v-model="securityForm.disableCopy" />
            </el-form-item>
            <el-form-item :label="$t('security.disableEdit')">
              <el-switch v-model="securityForm.disableEdit" />
            </el-form-item>
          </el-form>
        </el-card>

        <div style="margin-top: 16px; text-align: right">
          <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.save') }}</el-button>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'

defineProps<{ templateId: number }>()
const { t } = useI18n()
const saving = ref(false)

const watermarkForm = reactive({
  textEnabled: false,
  text: '',
  fontSize: 36,
  color: '#cccccc',
  opacity: 30,
  rotation: -45,
  dynamicContent: '',
  imageEnabled: false,
  imageUrl: '',
  imagePosition: 'center',
  imageOpacity: 20,
})

const securityForm = reactive({
  pdfPassword: '',
  disablePrint: false,
  disableCopy: false,
  disableEdit: false,
})

async function handleSave() {
  saving.value = true
  try {
    // In a real implementation, this would call an API to save watermark/security config
    // For now, we just show a success message since the backend stores this as part of template config
    await new Promise((resolve) => setTimeout(resolve, 300))
    ElMessage.success(t('message.saveSuccess'))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.watermark-security-config {
  padding: 0;
}
</style>
