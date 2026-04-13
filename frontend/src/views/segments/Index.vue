<template>
  <div class="segments-page">
    <div class="page-header">
      <h2>{{ $t('segment.title') }}</h2>
      <el-button type="primary" @click="openCreateDialog">
        {{ $t('segment.create') }}
      </el-button>
    </div>

    <!-- Filters -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item :label="$t('common.search')">
          <el-input
            v-model="query.keyword"
            :placeholder="$t('segment.searchPlaceholder')"
            clearable
            style="width: 220px"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('segment.category')">
          <el-tree-select
            v-model="query.categoryId"
            :data="categoryTree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            :placeholder="$t('common.all')"
            clearable
            check-strictly
            style="width: 160px"
            @change="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('segment.tags')">
          <el-select
            v-model="query.tagId"
            :placeholder="$t('common.all')"
            clearable
            style="width: 150px"
            @change="handleSearch"
          >
            <el-option v-for="tag in tagList" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('segment.type')">
          <el-select
            v-model="query.segmentType"
            :placeholder="$t('segment.allTypes')"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option v-for="st in segmentTypes" :key="st.value" :label="st.label" :value="st.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
          <el-button @click="resetFilters">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-card shadow="never" style="margin-top: 16px">
      <el-table :data="segments" v-loading="loading" stripe>
        <el-table-column prop="name" :label="$t('segment.name')" min-width="180">
          <template #default="{ row }">
            <router-link :to="`/segments/${row.id}`" class="segment-link">
              {{ row.name }}
            </router-link>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="$t('segment.description')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="$t('segment.type')" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.segmentType" size="small" type="info">{{ row.segmentType }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('segment.isComponent')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isComponent" size="small" type="success">{{ $t('segment.componentSegment') }}</el-tag>
            <el-tag v-else size="small" type="info">{{ $t('segment.normalSegment') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" :label="$t('common.updatedAt')" width="170" />
        <el-table-column :label="$t('common.actions')" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEditDialog(row)">
              {{ $t('common.edit') }}
            </el-button>
            <el-button link type="primary" size="small" @click="handleClone(row)">
              {{ $t('common.clone') }}
            </el-button>
            <el-button
              v-if="!row.isComponent"
              link type="success" size="small"
              @click="handlePromote(row)"
            >
              {{ $t('segment.promote') }}
            </el-button>
            <el-button
              v-if="row.isComponent"
              link type="warning" size="small"
              @click="handleDemote(row)"
            >
              {{ $t('segment.demote') }}
            </el-button>
            <el-button link size="small" @click="toggleFavorite(row)">
              {{ $t('segment.favorite') }}
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">
              {{ $t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchSegments"
          @current-change="fetchSegments"
        />
      </div>
    </el-card>

    <!-- Create/Edit Dialog -->
    <SegmentFormDialog
      v-model:visible="formDialogVisible"
      :segment-data="editingSegment"
      :categories="categoryTree"
      :tags="tagList"
      @saved="onFormSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getSegments,
  deleteSegment,
  cloneSegment,
  promoteToComponent,
  demoteFromComponent,
  addSegmentFavorite,
  removeSegmentFavorite,
} from '@/api/segments'
import { getCategories, getTags, type CategoryDTO, type TagDTO } from '@/api/templates'
import type { Segment, SegmentQuery } from '@/types/segment'
import SegmentFormDialog from './components/SegmentFormDialog.vue'

const { t } = useI18n()

const loading = ref(false)
const segments = ref<Segment[]>([])
const total = ref(0)
const categoryTree = ref<CategoryDTO[]>([])
const tagList = ref<TagDTO[]>([])
const formDialogVisible = ref(false)
const editingSegment = ref<Segment | null>(null)

const query = reactive<SegmentQuery>({
  keyword: '',
  categoryId: null,
  tagId: null,
  segmentType: '',
  page: 1,
  size: 10,
})

const segmentTypes = computed(() => [
  { label: 'COVER', value: 'COVER' },
  { label: 'TOC', value: 'TOC' },
  { label: 'CHAPTER', value: 'CHAPTER' },
  { label: 'TABLE', value: 'TABLE' },
  { label: 'SIGNATURE', value: 'SIGNATURE' },
  { label: 'LEGAL', value: 'LEGAL' },
  { label: 'APPENDIX', value: 'APPENDIX' },
])

async function fetchSegments() {
  loading.value = true
  try {
    const res = await getSegments(query)
    segments.value = res.content
    total.value = res.totalElements
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

function handleSearch() {
  query.page = 1
  fetchSegments()
}

function resetFilters() {
  query.keyword = ''
  query.categoryId = null
  query.tagId = null
  query.segmentType = ''
  handleSearch()
}

function openCreateDialog() {
  editingSegment.value = null
  formDialogVisible.value = true
}

function openEditDialog(row: Segment) {
  editingSegment.value = { ...row }
  formDialogVisible.value = true
}

function onFormSaved() {
  formDialogVisible.value = false
  fetchSegments()
}

async function handleClone(row: Segment) {
  try {
    await cloneSegment(row.id)
    ElMessage.success(t('segment.cloneSuccess'))
    fetchSegments()
  } catch { /* handled */ }
}

async function handlePromote(row: Segment) {
  try {
    await promoteToComponent(row.id)
    ElMessage.success(t('segment.promoteSuccess'))
    fetchSegments()
  } catch { /* handled */ }
}

async function handleDemote(row: Segment) {
  try {
    await demoteFromComponent(row.id)
    ElMessage.success(t('segment.demoteSuccess'))
    fetchSegments()
  } catch { /* handled */ }
}

async function toggleFavorite(row: Segment) {
  try {
    // Simple toggle — in a real app we'd track favorite state per row
    await addSegmentFavorite(row.id)
    ElMessage.success(t('segment.favoriteSuccess'))
  } catch { /* handled */ }
}

async function handleDelete(row: Segment) {
  try {
    await ElMessageBox.confirm(
      t('segment.confirmDelete', { name: row.name }),
      t('confirm.deleteTitle'),
      { type: 'warning' },
    )
    await deleteSegment(row.id)
    ElMessage.success(t('segment.deleteSuccess'))
    fetchSegments()
  } catch { /* cancelled or error */ }
}

onMounted(() => {
  fetchSegments()
  fetchFilters()
})
</script>

<style scoped>
.segments-page {
  padding: 0;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
}
.filter-card :deep(.el-form-item) {
  margin-bottom: 0;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.segment-link {
  color: var(--el-color-primary);
  text-decoration: none;
  font-weight: 500;
}
.segment-link:hover {
  text-decoration: underline;
}
</style>
