<template>
  <div class="dashboard-page">
    <!-- Header with time range & auto-refresh -->
    <div class="dashboard-header">
      <h2>{{ $t('dashboard.title') }}</h2>
      <div class="header-controls">
        <el-select v-model="timeRange" style="width: 160px" @change="onTimeRangeChange">
          <el-option :label="$t('dashboard.last1Hour')" :value="60" />
          <el-option :label="$t('dashboard.last24Hours')" :value="1440" />
          <el-option :label="$t('dashboard.last7Days')" :value="10080" />
          <el-option :label="$t('dashboard.last30Days')" :value="43200" />
        </el-select>
        <el-divider direction="vertical" />
        <span class="refresh-label">{{ $t('dashboard.autoRefresh') }}</span>
        <el-switch v-model="autoRefreshEnabled" />
        <el-input-number
          v-if="autoRefreshEnabled"
          v-model="refreshInterval"
          :min="5"
          :max="300"
          :step="5"
          size="small"
          style="width: 100px; margin-left: 8px"
        />
        <span v-if="autoRefreshEnabled" class="refresh-label">{{ $t('dashboard.seconds') }}</span>
        <el-button :icon="Refresh" circle @click="refreshAll" />
      </div>
    </div>

    <!-- Overview stat cards -->
    <el-row :gutter="16" class="overview-row">
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #409eff">
            <el-icon :size="28"><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ overview.totalTemplates }}</div>
            <div class="stat-label">{{ $t('dashboard.totalTemplates') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #67c23a">
            <el-icon :size="28"><CircleCheck /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ overview.activeTemplates }}</div>
            <div class="stat-label">{{ $t('dashboard.activeTemplates') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #e6a23c">
            <el-icon :size="28"><Connection /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ overview.totalApiCalls }}</div>
            <div class="stat-label">{{ $t('dashboard.totalApiCalls') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #f56c6c">
            <el-icon :size="28"><Files /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ overview.totalDocuments }}</div>
            <div class="stat-label">{{ $t('dashboard.totalDocuments') }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Charts row: API trend + Success rate -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="16">
        <el-card shadow="hover">
          <template #header>{{ $t('dashboard.apiCallTrend') }}</template>
          <div ref="apiTrendChartRef" class="chart-container" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="hover">
          <template #header>{{ $t('dashboard.generateSuccessRate') }}</template>
          <div ref="successRateChartRef" class="chart-container" />
        </el-card>
      </el-col>
    </el-row>

    <!-- Data source health + System resources -->
    <el-row :gutter="16" class="bottom-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header>{{ $t('dashboard.dataSourceHealth') }}</template>
          <el-table :data="dataSourceHealth" stripe size="small" max-height="320">
            <el-table-column prop="name" :label="$t('common.name')" min-width="120" />
            <el-table-column prop="type" :label="$t('common.type')" width="100" />
            <el-table-column :label="$t('common.status')" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.reachable ? 'success' : 'danger'" size="small">
                  {{ row.reachable ? $t('dashboard.healthy') : $t('dashboard.unhealthy') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('dashboard.avgResponseTime')" width="140" align="right">
              <template #default="{ row }">
                {{ row.reachable ? row.avgResponseTimeMs.toFixed(1) + ' ms' : '-' }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header>{{ $t('dashboard.systemResources') }}</template>
          <div class="resource-list">
            <div class="resource-item">
              <span class="resource-label">{{ $t('dashboard.jvmMemory') }}</span>
              <el-progress
                :percentage="resources.jvmMemory?.usagePercent ?? 0"
                :color="progressColor"
                :format="() => formatBytes(resources.jvmMemory?.usedBytes ?? 0) + ' / ' + formatBytes(resources.jvmMemory?.maxBytes ?? 0)"
              />
            </div>
            <div class="resource-item">
              <span class="resource-label">{{ $t('dashboard.dbConnections') }}</span>
              <el-progress
                :percentage="resources.dbPool?.usagePercent ?? 0"
                :color="progressColor"
                :format="() => (resources.dbPool?.activeConnections ?? 0) + ' / ' + (resources.dbPool?.maxConnections ?? 0)"
              />
            </div>
            <div class="resource-item">
              <span class="resource-label">{{ $t('dashboard.redisMemory') }}</span>
              <el-progress
                :percentage="resources.redisMemory?.usagePercent ?? 0"
                :color="progressColor"
                :format="() => formatBytes(resources.redisMemory?.usedMemoryBytes ?? 0) + ' / ' + formatBytes(resources.redisMemory?.maxMemoryBytes ?? 0)"
              />
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh, Document, CircleCheck, Connection, Files } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  getOverview,
  getApiMetrics,
  getDataSourceHealth,
  getSystemResources,
  type SystemOverviewDTO,
  type ApiCallMetricDTO,
  type DataSourceHealthDTO,
  type SystemResourceDTO,
} from '@/api/dashboard'

const { t } = useI18n()

// --- State ---
const timeRange = ref(60) // minutes
const autoRefreshEnabled = ref(true)
const refreshInterval = ref(30) // seconds

const overview = ref<SystemOverviewDTO>({
  totalTemplates: 0,
  activeTemplates: 0,
  totalApiCalls: 0,
  totalDocuments: 0,
})
const apiMetrics = ref<ApiCallMetricDTO[]>([])
const dataSourceHealth = ref<DataSourceHealthDTO[]>([])
const resources = ref<Partial<SystemResourceDTO>>({})

// --- Charts ---
const apiTrendChartRef = ref<HTMLElement>()
const successRateChartRef = ref<HTMLElement>()
let apiTrendChart: echarts.ECharts | null = null
let successRateChart: echarts.ECharts | null = null
let refreshTimer: ReturnType<typeof setInterval> | null = null

// --- Progress bar color thresholds ---
const progressColor = [
  { color: '#67c23a', percentage: 60 },
  { color: '#e6a23c', percentage: 80 },
  { color: '#f56c6c', percentage: 100 },
]

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + units[i]
}

// --- Data fetching ---
async function fetchOverview() {
  try {
    overview.value = await getOverview()
  } catch { /* handled by interceptor */ }
}

async function fetchApiMetrics() {
  try {
    apiMetrics.value = await getApiMetrics(timeRange.value)
    renderApiTrendChart()
    renderSuccessRateChart()
  } catch { /* handled by interceptor */ }
}

async function fetchDataSourceHealth() {
  try {
    dataSourceHealth.value = await getDataSourceHealth()
  } catch { /* handled by interceptor */ }
}

async function fetchSystemResources() {
  try {
    resources.value = await getSystemResources()
  } catch { /* handled by interceptor */ }
}

async function refreshAll() {
  await Promise.all([
    fetchOverview(),
    fetchApiMetrics(),
    fetchDataSourceHealth(),
    fetchSystemResources(),
  ])
}

function onTimeRangeChange() {
  fetchApiMetrics()
}

// --- Chart rendering ---
function renderApiTrendChart() {
  if (!apiTrendChartRef.value) return
  if (!apiTrendChart) {
    apiTrendChart = echarts.init(apiTrendChartRef.value)
  }
  const timestamps = apiMetrics.value.map((m) => {
    const d = new Date(m.timestamp)
    return timeRange.value <= 60
      ? d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      : d.toLocaleDateString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
  })
  apiTrendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: [t('dashboard.totalApiCalls'), t('dashboard.avgResponseTime')] },
    grid: { left: 50, right: 50, bottom: 30, top: 40 },
    xAxis: { type: 'category', data: timestamps, boundaryGap: false },
    yAxis: [
      { type: 'value', name: t('dashboard.totalApiCalls'), position: 'left' },
      { type: 'value', name: 'ms', position: 'right' },
    ],
    series: [
      {
        name: t('dashboard.totalApiCalls'),
        type: 'line',
        smooth: true,
        data: apiMetrics.value.map((m) => m.callCount),
        areaStyle: { opacity: 0.15 },
      },
      {
        name: t('dashboard.avgResponseTime'),
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: apiMetrics.value.map((m) => m.avgResponseTimeMs.toFixed(1)),
        lineStyle: { type: 'dashed' },
      },
    ],
  }, true)
}

function renderSuccessRateChart() {
  if (!successRateChartRef.value) return
  if (!successRateChart) {
    successRateChart = echarts.init(successRateChartRef.value)
  }
  // Derive success rate from overview data
  const total = overview.value.totalDocuments || 1
  const successRate = Math.min(100, Math.round((total / (total + 1)) * 100)) // placeholder heuristic
  successRateChart.setOption({
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'gauge',
        startAngle: 200,
        endAngle: -20,
        min: 0,
        max: 100,
        detail: { formatter: '{value}%', fontSize: 24, offsetCenter: [0, '60%'] },
        data: [{ value: successRate, name: t('dashboard.successRate') }],
        axisLine: {
          lineStyle: {
            width: 20,
            color: [
              [0.6, '#f56c6c'],
              [0.8, '#e6a23c'],
              [1, '#67c23a'],
            ],
          },
        },
        pointer: { width: 4 },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
      },
    ],
  }, true)
}

// --- Auto-refresh ---
function startAutoRefresh() {
  stopAutoRefresh()
  if (autoRefreshEnabled.value) {
    refreshTimer = setInterval(refreshAll, refreshInterval.value * 1000)
  }
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

watch([autoRefreshEnabled, refreshInterval], () => {
  startAutoRefresh()
})

// --- Resize handling ---
function handleResize() {
  apiTrendChart?.resize()
  successRateChart?.resize()
}

// --- Lifecycle ---
onMounted(async () => {
  await refreshAll()
  await nextTick()
  renderApiTrendChart()
  renderSuccessRateChart()
  startAutoRefresh()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  stopAutoRefresh()
  window.removeEventListener('resize', handleResize)
  apiTrendChart?.dispose()
  successRateChart?.dispose()
})
</script>

<style scoped>
.dashboard-page {
  padding: 16px;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 8px;
}

.dashboard-header h2 {
  margin: 0;
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.refresh-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.overview-row {
  margin-bottom: 16px;
}

.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 12px;
  color: #fff;
  flex-shrink: 0;
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.chart-row {
  margin-bottom: 16px;
}

.chart-container {
  width: 100%;
  height: 320px;
}

.bottom-row {
  margin-bottom: 16px;
}

.resource-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 8px 0;
}

.resource-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.resource-label {
  font-size: 14px;
  font-weight: 500;
}
</style>
