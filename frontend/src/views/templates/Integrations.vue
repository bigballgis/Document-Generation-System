<template>
  <div class="template-integrations" v-loading="loading">
    <div class="page-header">
      <div class="header-left">
        <el-button @click="handleBack">
          <el-icon><ArrowLeft /></el-icon> {{ t('common.back') }}
        </el-button>
        <h2>{{ pageTitle }}</h2>
      </div>
      <el-button type="primary" plain @click="goWorkspace">
        {{ t('workspace.integrations.backToWorkspace') }}
      </el-button>
    </div>

    <el-card v-if="templateId > 0" shadow="never" class="tabs-card">
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane :label="t('webhook.title')" name="webhooks">
          <WebhookPanel :template-id="templateId" />
        </el-tab-pane>
        <el-tab-pane :label="t('schedule.title')" name="schedule">
          <ScheduledTaskManagement :template-id="templateId" />
        </el-tab-pane>
        <el-tab-pane :label="watermarkTabLabel" name="watermark">
          <WatermarkSecurityConfig :template-id="templateId" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getTemplate, type TemplateDTO } from '@/api/templates'
import WebhookPanel from './components/WebhookPanel.vue'
import ScheduledTaskManagement from './components/ScheduledTaskManagement.vue'
import WatermarkSecurityConfig from './components/WatermarkSecurityConfig.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const templateId = computed(() => Number(route.params.id))
const loading = ref(true)
const template = ref<TemplateDTO | null>(null)

const activeTab = ref<'webhooks' | 'schedule' | 'watermark'>('webhooks')

const pageTitle = computed(() => {
  if (template.value?.name) {
    return `${t('workspace.integrations.title')} — ${template.value.name}`
  }
  return t('workspace.integrations.title')
})

const watermarkTabLabel = computed(() => `${t('watermark.title')} & ${t('security.title')}`)

function applyTabFromRoute() {
  const raw = route.query.tab
  const v = typeof raw === 'string' ? raw : Array.isArray(raw) ? raw[0] : ''
  if (v === 'webhooks' || v === 'schedule' || v === 'watermark') {
    activeTab.value = v
  }
}

watch(
  () => route.query.tab,
  () => applyTabFromRoute(),
)

watch(activeTab, (v) => {
  const cur = typeof route.query.tab === 'string' ? route.query.tab : ''
  if (cur === v) return
  router.replace({ query: { ...route.query, tab: v } })
})

async function loadTemplate() {
  const id = templateId.value
  if (!Number.isFinite(id) || id <= 0) {
    template.value = null
    loading.value = false
    return
  }
  loading.value = true
  try {
    template.value = await getTemplate(id)
  } catch {
    template.value = null
  } finally {
    loading.value = false
  }
}

function handleBack() {
  router.push('/templates')
}

function goWorkspace() {
  router.push({ name: 'TemplateWorkspace', params: { id: String(templateId.value) } })
}

onMounted(async () => {
  applyTabFromRoute()
  await loadTemplate()
})
</script>

<style scoped>
.template-integrations {
  padding: 0;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.header-left h2 {
  margin: 0;
  font-size: 20px;
}
.tabs-card :deep(.el-tabs__content) {
  padding-top: 8px;
}
</style>
