<template>
  <div class="segment-editor-page" v-loading="pageLoading">
    <div class="editor-header">
      <div class="header-left">
        <el-button @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          {{ $t('common.back') }}
        </el-button>
        <h3 v-if="segment" class="editor-title">
          {{ segment.name }} — {{ $t('segment.editor') }}
        </h3>
        <el-tag v-if="locked" type="success" size="small" style="margin-left: 8px">
          {{ $t('segment.lockAcquired') }}
        </el-tag>
      </div>
    </div>

    <!-- Lock warning -->
    <el-alert
      v-if="otherUserLock && !locked"
      type="warning"
      :closable="false"
      style="margin-bottom: 12px"
    >
      <template #title>
        {{ $t('segment.lockWarning', { username: otherUserLock.lockedByUsername }) }}
      </template>
      <template #default>
        <el-button size="small" type="primary" @click="forceAcquire" style="margin-right: 8px">
          {{ $t('segment.editAnyway') }}
        </el-button>
        <el-button size="small" @click="viewReadOnly = true">
          {{ $t('segment.viewReadOnly') }}
        </el-button>
      </template>
    </el-alert>

    <!-- Lock expired warning -->
    <el-alert
      v-if="lockError === 'lock_expired'"
      type="error"
      :closable="false"
      style="margin-bottom: 12px"
    >
      {{ $t('segment.lockExpired') }}
    </el-alert>

    <div v-if="editorReady" class="editor-body">
      <OnlyOfficeEditor
        :document-url="documentUrl"
        :document-key="documentKey"
        :document-title="documentTitle"
        :callback-url="callbackUrl"
        :view-only="viewReadOnly"
        @ready="onEditorReady"
        @error="onEditorError"
        @close="goBack"
      />
    </div>

    <div v-else-if="!pageLoading" class="editor-placeholder">
      <el-empty :description="errorMessage || $t('editor.title')">
        <el-button v-if="errorMessage" type="primary" @click="initEditor">
          {{ $t('common.refresh') }}
        </el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getSegment } from '@/api/segments'
import type { Segment } from '@/types/segment'
import OnlyOfficeEditor from '@/components/OnlyOfficeEditor.vue'
import { useSegmentLock } from '@/composables/useSegmentLock'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const pageLoading = ref(false)
const segment = ref<Segment | null>(null)
const editorReady = ref(false)
const viewReadOnly = ref(false)
const errorMessage = ref('')

const segmentId = computed(() => Number(route.params.id))

const { locked, otherUserLock, lockError, acquire, release } = useSegmentLock()

const documentUrl = computed(() => {
  if (!segment.value) return ''
  // The backend should provide a presigned URL for the segment file
  const backendUrl = import.meta.env.VITE_API_BASE_URL || ''
  return `${backendUrl}/api/segments/${segmentId.value}/onlyoffice-url`
})

const documentKey = computed(() => {
  if (!segment.value) return ''
  return `segment-${segment.value.id}-${Date.now()}`
})

const documentTitle = computed(() => {
  return segment.value ? `${segment.value.name}.docx` : 'Segment.docx'
})

const callbackUrl = computed(() => {
  const backendUrl = import.meta.env.VITE_BACKEND_INTERNAL_URL || 'http://app:8080'
  return `${backendUrl}/api/segments/${segmentId.value}/onlyoffice-callback`
})

function goBack() {
  release()
  router.push(`/segments/${segmentId.value}`)
}

function onEditorReady() {
  // Editor loaded
}

function onEditorError(message: string) {
  errorMessage.value = message
  ElMessage.error(message)
}

async function forceAcquire() {
  const success = await acquire(segmentId.value)
  if (success) {
    viewReadOnly.value = false
    ElMessage.success(t('segment.lockAcquired'))
  } else {
    ElMessage.error(t('segment.lockFailed'))
  }
}

async function initEditor() {
  pageLoading.value = true
  errorMessage.value = ''
  try {
    segment.value = await getSegment(segmentId.value)

    // Try to acquire lock
    const lockSuccess = await acquire(segmentId.value)
    if (!lockSuccess && otherUserLock.value) {
      // Another user has the lock — show warning, default to read-only
      viewReadOnly.value = true
    }

    editorReady.value = true
  } catch {
    errorMessage.value = t('message.serverError')
  } finally {
    pageLoading.value = false
  }
}

onMounted(() => {
  initEditor()
})

onBeforeUnmount(() => {
  release()
})
</script>

<style scoped>
.segment-editor-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
}
.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  margin-bottom: 8px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.editor-title {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
}
.editor-body {
  flex: 1;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  overflow: hidden;
}
.editor-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
