<template>
  <div class="segment-detail" v-loading="loading">
    <div class="page-header">
      <div class="header-left">
        <el-button @click="router.push('/segments')">
          <el-icon><ArrowLeft /></el-icon> {{ $t('common.back') }}
        </el-button>
        <h2 v-if="segment">{{ segment.name }}</h2>
      </div>
      <div v-if="segment" class="header-actions">
        <el-button type="primary" @click="openEditor">
          {{ $t('segment.editor') }}
        </el-button>
        <el-button @click="openEditDialog">{{ $t('common.edit') }}</el-button>
        <el-button @click="handleClone">{{ $t('common.clone') }}</el-button>
        <el-button
          v-if="!segment.isComponent"
          type="success"
          @click="handlePromote"
        >
          {{ $t('segment.promote') }}
        </el-button>
        <el-button
          v-if="segment.isComponent"
          type="warning"
          @click="handleDemote"
        >
          {{ $t('segment.demote') }}
        </el-button>
        <el-button @click="permissionDialogVisible = true">
          {{ $t('segment.permissions') }}
        </el-button>
      </div>
    </div>

    <template v-if="segment">
      <!-- Basic Info Card -->
      <el-card shadow="never" style="margin-bottom: 16px">
        <el-descriptions :column="3" border>
          <el-descriptions-item :label="$t('segment.type')">
            <el-tag v-if="segment.segmentType" size="small" type="info">{{ segment.segmentType }}</el-tag>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('segment.isComponent')">
            <el-tag v-if="segment.isComponent" size="small" type="success">{{ $t('segment.componentSegment') }}</el-tag>
            <el-tag v-else size="small" type="info">{{ $t('segment.normalSegment') }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('segment.category')">
            {{ segment.categoryId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('common.createdAt')">
            {{ segment.createdAt }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('common.updatedAt')">
            {{ segment.updatedAt }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('common.createdBy')">
            {{ segment.createdBy }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('segment.description')" :span="3">
            {{ segment.description || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- References (for component templates) -->
      <el-card v-if="segment.isComponent" shadow="never" style="margin-bottom: 16px">
        <template #header>{{ $t('segment.references') }}</template>
        <p>{{ $t('segment.referenceCount') }}: {{ referenceCount }}</p>
        <p v-if="referenceCount === 0">{{ $t('segment.noReferences') }}</p>
        <p v-else>{{ $t('segment.referencedBy', { count: referenceCount }) }}</p>
      </el-card>

      <!-- Tabs -->
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane :label="$t('segment.versions')" name="versions">
          <SegmentVersionList :segment-id="segment.id" @updated="fetchSegment" />
        </el-tab-pane>
        <el-tab-pane :label="$t('segment.variables')" name="variables">
          <SegmentVariableList :segment-id="segment.id" />
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- Edit Dialog -->
    <SegmentFormDialog
      v-model:visible="editDialogVisible"
      :segment-data="segment"
      :categories="categoryTree"
      :tags="tagList"
      @saved="onEditSaved"
    />

    <!-- Permission Dialog -->
    <SegmentPermissionDialog
      v-if="segment"
      v-model:visible="permissionDialogVisible"
      :segment-id="segment.id"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  getSegment,
  cloneSegment,
  promoteToComponent,
  demoteFromComponent,
} from '@/api/segments'
import { getCategories, getTags, type CategoryDTO, type TagDTO } from '@/api/templates'
import type { Segment } from '@/types/segment'
import SegmentFormDialog from './components/SegmentFormDialog.vue'
import SegmentVersionList from './components/SegmentVersionList.vue'
import SegmentVariableList from './components/SegmentVariableList.vue'
import SegmentPermissionDialog from './components/SegmentPermissionDialog.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const loading = ref(false)
const segment = ref<Segment | null>(null)
const activeTab = ref('versions')
const editDialogVisible = ref(false)
const permissionDialogVisible = ref(false)
const categoryTree = ref<CategoryDTO[]>([])
const tagList = ref<TagDTO[]>([])
const referenceCount = ref(0)

const segmentId = Number(route.params.id)

async function fetchSegment() {
  loading.value = true
  try {
    segment.value = await getSegment(segmentId)
  } catch { /* handled */ } finally {
    loading.value = false
  }
}

async function fetchFilters() {
  try {
    const [cats, tags] = await Promise.all([getCategories(), getTags()])
    categoryTree.value = cats
    tagList.value = tags
  } catch { /* ignore */ }
}

function openEditDialog() {
  editDialogVisible.value = true
}

function openEditor() {
  router.push(`/segments/${segmentId}/editor`)
}

function onEditSaved() {
  editDialogVisible.value = false
  fetchSegment()
}

async function handleClone() {
  try {
    await cloneSegment(segmentId)
    ElMessage.success(t('segment.cloneSuccess'))
  } catch { /* handled */ }
}

async function handlePromote() {
  try {
    await promoteToComponent(segmentId)
    ElMessage.success(t('segment.promoteSuccess'))
    fetchSegment()
  } catch { /* handled */ }
}

async function handleDemote() {
  try {
    await demoteFromComponent(segmentId)
    ElMessage.success(t('segment.demoteSuccess'))
    fetchSegment()
  } catch { /* handled */ }
}

onMounted(() => {
  fetchSegment()
  fetchFilters()
})
</script>

<style scoped>
.segment-detail {
  padding: 0;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-left h2 {
  margin: 0;
}
.header-actions {
  display: flex;
  gap: 8px;
}
</style>
