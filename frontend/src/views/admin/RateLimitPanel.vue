<template>
  <div class="rate-limit-panel">
    <div class="toolbar" style="display: flex; justify-content: flex-end; margin-bottom: 16px;">
      <el-button :icon="Refresh" @click="fetchAll">{{ $t('common.refresh') }}</el-button>
    </div>

    <el-card shadow="never" style="margin-bottom: 16px;">
      <template #header>
        <span>{{ $t('admin.rateLimit.title') }}</span>
      </template>
      <el-table v-if="rateLimits.length > 0" :data="rateLimits" v-loading="loadingLimits" border stripe>
        <el-table-column prop="apiKeyName" :label="$t('admin.rateLimit.apiKeyName')" min-width="150" />
        <el-table-column prop="limitPerSecond" :label="$t('admin.rateLimit.limitPerSecond')" width="120" />
        <el-table-column prop="remainingPerSecond" :label="$t('admin.rateLimit.remainingPerSecond')" width="140" />
        <el-table-column prop="limitPerMinute" :label="$t('admin.rateLimit.limitPerMinute')" width="120" />
        <el-table-column prop="remainingPerMinute" :label="$t('admin.rateLimit.remainingPerMinute')" width="140" />
        <el-table-column prop="limitPerHour" :label="$t('admin.rateLimit.limitPerHour')" width="120" />
        <el-table-column prop="remainingPerHour" :label="$t('admin.rateLimit.remainingPerHour')" width="140" />
      </el-table>
      <el-empty v-else-if="!loadingLimits" description="No API Keys" />
    </el-card>

    <el-card shadow="never">
      <template #header>
        <span>{{ $t('admin.rateLimit.usageStats') }}</span>
      </template>
      <el-descriptions v-if="usageStats" :column="2" border>
        <el-descriptions-item :label="$t('admin.rateLimit.monthlyQuota')">{{ usageStats.monthlyQuota }}</el-descriptions-item>
        <el-descriptions-item :label="$t('admin.rateLimit.currentMonthUsage')">{{ usageStats.currentMonthUsage }}</el-descriptions-item>
        <el-descriptions-item :label="$t('admin.rateLimit.remainingQuota')">{{ usageStats.remainingQuota }}</el-descriptions-item>
        <el-descriptions-item :label="$t('admin.rateLimit.resetAt')">{{ usageStats.resetAt }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else-if="!loadingStats" :description="$t('common.noData')" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import type { RateLimitStatusDTO, UsageStatsDTO } from '@/api/rate-limits'
import { getRateLimits, getUsageStats } from '@/api/rate-limits'

const loadingLimits = ref(false)
const loadingStats = ref(false)
const rateLimits = ref<RateLimitStatusDTO[]>([])
const usageStats = ref<UsageStatsDTO | null>(null)

async function fetchRateLimits() {
  loadingLimits.value = true
  try {
    rateLimits.value = await getRateLimits()
  } catch {} finally {
    loadingLimits.value = false
  }
}

async function fetchUsageStats() {
  loadingStats.value = true
  try {
    usageStats.value = await getUsageStats()
  } catch {} finally {
    loadingStats.value = false
  }
}

function fetchAll() {
  fetchRateLimits()
  fetchUsageStats()
}

onMounted(fetchAll)
</script>
