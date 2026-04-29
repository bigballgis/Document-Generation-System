<template>
  <div class="market-page">
    <h2>{{ $t('market.title') }}</h2>

    <el-card shadow="never" style="margin-bottom: 16px">
      <el-row :gutter="16" align="middle">
        <el-col :span="8">
          <el-input
            v-model="query.keyword"
            :placeholder="$t('market.search')"
            clearable
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-col>
        <el-col :span="5">
          <el-select v-model="query.category" :placeholder="$t('market.categories')" clearable @change="handleSearch">
            <el-option :label="$t('market.allTemplates')" value="" />
            <el-option :label="$t('market.presetContract')" value="contract" />
            <el-option :label="$t('market.presetReport')" value="report" />
            <el-option :label="$t('market.presetInvoice')" value="invoice" />
            <el-option :label="$t('market.presetCertificate')" value="certificate" />
          </el-select>
        </el-col>
        <el-col :span="5">
          <el-select v-model="query.sort" :placeholder="$t('common.sort')" @change="handleSearch">
            <el-option :label="$t('market.popular')" value="popular" />
            <el-option :label="$t('market.recent')" value="recent" />
          </el-select>
        </el-col>
        <el-col :span="6" style="text-align: right">
          <el-button type="primary" @click="shareDialogVisible = true">
            <el-icon><Share /></el-icon> {{ $t('market.share') }}
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-row :gutter="16" v-loading="loading">
      <el-col v-for="item in templates" :key="item.id" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card shadow="hover" class="market-card">
          <div class="card-header">
            <el-tag size="small" type="info">{{ item.category }}</el-tag>
            <el-tag v-if="item.shareScope === 'GLOBAL'" size="small" type="success">{{ $t('market.scopeGlobal') }}</el-tag>
            <el-tag v-else size="small">{{ $t('market.scopeTenant') }}</el-tag>
          </div>
          <h3 class="card-title">{{ item.name }}</h3>
          <p class="card-desc">{{ item.description || '-' }}</p>
          <div class="card-tags">
            <el-tag v-for="tag in item.tags" :key="tag" size="small" type="info" style="margin-right: 4px">
              {{ tag }}
            </el-tag>
          </div>
          <div class="card-meta">
            <span>{{ $t('market.author') }}: {{ item.author }}</span>
            <span>{{ $t('market.usageCount') }}: {{ item.usageCount }}</span>
          </div>
          <div class="card-actions">
            <el-button type="primary" size="small" @click="handleCopy(item)">
              <el-icon><CopyDocument /></el-icon> {{ $t('market.copyToWorkspace') }}
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div v-if="!loading && templates.length === 0" style="text-align: center; padding: 40px 0; color: #999">
      {{ $t('common.noData') }}
    </div>

    <div v-if="total > 0" class="pagination-wrapper">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSearch"
        @current-change="handleSearch"
      />
    </div>

    <el-dialog v-model="shareDialogVisible" :title="$t('market.share')" width="480px">
      <el-form :model="shareForm" label-width="120px">
        <el-form-item :label="$t('template.name')">
          <el-input v-model="shareForm.templateId" :placeholder="'Template ID'" type="number" />
        </el-form-item>
        <el-form-item :label="$t('market.shareScope')">
          <el-radio-group v-model="shareForm.scope">
            <el-radio value="TENANT_INTERNAL">{{ $t('market.scopeTenant') }}</el-radio>
            <el-radio value="GLOBAL">{{ $t('market.scopeGlobal') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shareDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="shareLoading" @click="handleShare">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, CopyDocument, Share } from '@element-plus/icons-vue'
import {
  searchMarketTemplates, copyFromMarket, shareToMarket,
  type MarketTemplateDTO, type MarketQuery,
} from '@/api/market'

const { t } = useI18n()

const loading = ref(false)
const templates = ref<MarketTemplateDTO[]>([])
const total = ref(0)

const query = reactive<MarketQuery>({
  keyword: '',
  category: '',
  sort: 'popular',
  page: 0,
  size: 12,
})

const currentPage = computed({
  get: () => query.page + 1,
  set: (val: number) => { query.page = val - 1 },
})

const shareDialogVisible = ref(false)
const shareLoading = ref(false)
const shareForm = reactive({ templateId: '' as string | number, scope: 'TENANT_INTERNAL' as const })

async function fetchTemplates() {
  loading.value = true
  try {
    const result = await searchMarketTemplates(query)
    templates.value = result.content
    total.value = result.totalElements
  } catch {} finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 0
  fetchTemplates()
}

async function handleCopy(item: MarketTemplateDTO) {
  try {
    await ElMessageBox.confirm(
      t('market.copyToWorkspace') + `: ${item.name}?`,
      t('common.confirm'),
    )
    await copyFromMarket(item.id)
    ElMessage.success(t('market.copySuccess'))
  } catch {}
}

async function handleShare() {
  if (!shareForm.templateId) return
  shareLoading.value = true
  try {
    await shareToMarket(Number(shareForm.templateId), { scope: shareForm.scope })
    ElMessage.success(t('market.shareSuccess'))
    shareDialogVisible.value = false
    fetchTemplates()
  } catch {} finally {
    shareLoading.value = false
  }
}

onMounted(fetchTemplates)
</script>

<style scoped>
.market-page {
  padding: 20px;
}
.market-page h2 {
  margin: 0 0 16px;
}
.market-card {
  margin-bottom: 16px;
}
.card-header {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}
.card-title {
  margin: 0 0 8px;
  font-size: 16px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-desc {
  color: #666;
  font-size: 13px;
  margin: 0 0 8px;
  height: 40px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.card-tags {
  margin-bottom: 8px;
  min-height: 24px;
}
.card-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #999;
  margin-bottom: 12px;
}
.card-actions {
  text-align: center;
}
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>

