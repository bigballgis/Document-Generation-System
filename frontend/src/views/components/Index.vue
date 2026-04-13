<template>
  <div class="components-page">
    <div class="page-header">
      <h2>{{ $t('component.title') }}</h2>
    </div>

    <!-- Filters -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item :label="$t('common.search')">
          <el-input
            v-model="query.keyword"
            :placeholder="$t('component.searchPlaceholder')"
            clearable
            style="width: 240px"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('segment.tags')">
          <el-select
            v-model="query.tagId"
            :placeholder="$t('common.all')"
            clearable
            style="width: 160px"
            @change="handleSearch"
          >
            <el-option v-for="tag in tagList" :key="tag.id" :label="tag.name" :value="tag.id" />
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
      <el-table :data="components" v-loading="loading" stripe>
        <el-table-column prop="name" :label="$t('segment.name')" min-width="200">
          <template #default="{ row }">
            <router-link :to="`/segments/${row.id}`" class="component-link">
              {{ row.name }}
            </router-link>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="$t('segment.description')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="$t('segment.type')" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.segmentType" size="small" type="info">{{ row.segmentType }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" :label="$t('common.updatedAt')" width="170" />
        <el-table-column :label="$t('common.actions')" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="router.push(`/segments/${row.id}`)">
              {{ $t('common.detail') }}
            </el-button>
            <el-button link type="warning" size="small" @click="handleDemote(row)">
              {{ $t('segment.demote') }}
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
          @size-change="fetchComponents"
          @current-change="fetchComponents"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getSegments, demoteFromComponent } from '@/api/segments'
import { getTags, type TagDTO } from '@/api/templates'
import type { Segment, SegmentQuery } from '@/types/segment'

const router = useRouter()
const { t } = useI18n()

const loading = ref(false)
const components = ref<Segment[]>([])
const total = ref(0)
const tagList = ref<TagDTO[]>([])

const query = reactive<SegmentQuery>({
  keyword: '',
  tagId: null,
  isComponent: true,
  page: 1,
  size: 10,
})

async function fetchComponents() {
  loading.value = true
  try {
    const res = await getSegments(query)
    components.value = res.content
    total.value = res.totalElements
  } catch { /* handled */ } finally {
    loading.value = false
  }
}

async function fetchFilters() {
  try {
    tagList.value = await getTags()
  } catch { /* ignore */ }
}

function handleSearch() {
  query.page = 1
  fetchComponents()
}

function resetFilters() {
  query.keyword = ''
  query.tagId = null
  handleSearch()
}

async function handleDemote(row: Segment) {
  try {
    await demoteFromComponent(row.id)
    ElMessage.success(t('segment.demoteSuccess'))
    fetchComponents()
  } catch { /* handled */ }
}

onMounted(() => {
  fetchComponents()
  fetchFilters()
})
</script>

<style scoped>
.components-page {
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
.component-link {
  color: var(--el-color-primary);
  text-decoration: none;
  font-weight: 500;
}
.component-link:hover {
  text-decoration: underline;
}
</style>
