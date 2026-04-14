import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { getTemplate, getAvailableTransitions, getTemplateVersions } from '@/api/templates'
import { getAssemblyConfig, getCompositeCoverage } from '@/api/composite-templates'
import { getDataSources } from '@/api/data-sources'
import { getExpressions } from '@/api/expressions'
import { getTestCases } from '@/api/market'
import { getTemplateReviews, getTemplatePermissions } from '@/api/admin'
import type { TemplateDTO, TemplateVersionDTO } from '@/api/templates'
import type { AssemblyConfig, CompositeCoverageReport } from '@/types/segment'
import type { DataSourceDTO } from '@/api/data-sources'
import type { ExpressionDTO } from '@/types/document'
import type { TestCaseDTO } from '@/api/market'
import type { ReviewDTO, PermissionDTO } from '@/api/admin'

export const useTemplateWorkspaceStore = defineStore('templateWorkspace', () => {
  // ── State ──
  const templateId = ref<number>(0)
  const template = ref<TemplateDTO | null>(null)
  const assemblyConfig = ref<AssemblyConfig | null>(null)
  const dataSources = ref<DataSourceDTO[]>([])
  const expressions = ref<ExpressionDTO[]>([])
  const coverage = ref<CompositeCoverageReport | null>(null)
  const availableTransitions = ref<string[]>([])

  // P3 — on-demand loaded state (NOT in initWorkspace)
  const testCases = ref<TestCaseDTO[]>([])
  const testReport = ref<any | null>(null)
  const reviews = ref<ReviewDTO[]>([])

  // P4 — on-demand loaded state (NOT in initWorkspace)
  const versions = ref<TemplateVersionDTO[]>([])
  const permissions = ref<PermissionDTO[]>([])

  const loading = ref(false)
  const criticalError = ref<string | null>(null)
  const warnings = ref<Record<string, string>>({})

  // ── Derived ──
  const templateStatus = computed(() => template.value?.status ?? 'DRAFT')
  const isActive = computed(() => templateStatus.value === 'ACTIVE')
  const isDraft = computed(() => templateStatus.value === 'DRAFT')

  // ── Actions ──
  async function initWorkspace(id: number): Promise<void> {
    if (templateId.value !== 0 && templateId.value !== id) {
      $reset()
    }
    templateId.value = id
    loading.value = true
    criticalError.value = null
    warnings.value = {}

    // Critical requests — failure shows error page
    const criticalPromises = Promise.all([
      getTemplate(id),
      getAssemblyConfig(id),
    ])

    // Non-critical requests — failure shows warning banner
    const nonCriticalResults = Promise.allSettled([
      getDataSources(id),
      getExpressions(id),
      getCompositeCoverage(id),
      getAvailableTransitions(id),
    ])

    try {
      const [tmpl, config] = await criticalPromises
      template.value = tmpl
      assemblyConfig.value = config
    } catch (e: any) {
      criticalError.value = e.message || 'Failed to load workspace'
      loading.value = false
      return
    }

    const settled = await nonCriticalResults
    const sections = ['dataSources', 'expressions', 'coverage', 'transitions'] as const
    settled.forEach((result, index) => {
      if (result.status === 'fulfilled') {
        switch (index) {
          case 0: dataSources.value = result.value as DataSourceDTO[]; break
          case 1: expressions.value = result.value as ExpressionDTO[]; break
          case 2: coverage.value = result.value as CompositeCoverageReport; break
          case 3: availableTransitions.value = result.value as string[]; break
        }
      } else {
        warnings.value[sections[index]] = result.reason?.message || 'Load failed'
      }
    })

    loading.value = false
  }

  async function refreshTemplate(): Promise<void> {
    if (!templateId.value) return
    try {
      template.value = await getTemplate(templateId.value)
    } catch (e: any) {
      warnings.value.template = e.message || 'Refresh failed'
    }
  }

  async function refreshAssemblyConfig(): Promise<void> {
    if (!templateId.value) return
    try {
      assemblyConfig.value = await getAssemblyConfig(templateId.value)
    } catch (e: any) {
      warnings.value.assemblyConfig = e.message || 'Refresh failed'
    }
  }

  async function refreshDataSources(): Promise<void> {
    if (!templateId.value) return
    try {
      dataSources.value = await getDataSources(templateId.value)
      delete warnings.value.dataSources
    } catch (e: any) {
      warnings.value.dataSources = e.message || 'Refresh failed'
    }
  }

  async function refreshExpressions(): Promise<void> {
    if (!templateId.value) return
    try {
      expressions.value = await getExpressions(templateId.value)
      delete warnings.value.expressions
    } catch (e: any) {
      warnings.value.expressions = e.message || 'Refresh failed'
    }
  }

  async function refreshCoverage(): Promise<void> {
    if (!templateId.value) return
    try {
      coverage.value = await getCompositeCoverage(templateId.value)
      delete warnings.value.coverage
    } catch (e: any) {
      warnings.value.coverage = e.message || 'Refresh failed'
    }
  }

  async function refreshTransitions(): Promise<void> {
    if (!templateId.value) return
    try {
      availableTransitions.value = await getAvailableTransitions(templateId.value)
      delete warnings.value.transitions
    } catch (e: any) {
      warnings.value.transitions = e.message || 'Refresh failed'
    }
  }

  async function refreshTestCases(): Promise<void> {
    if (!templateId.value) return
    try {
      testCases.value = await getTestCases(templateId.value)
      delete warnings.value.testCases
    } catch (e: any) {
      warnings.value.testCases = e.message || 'Refresh failed'
    }
  }

  async function refreshReviews(): Promise<void> {
    if (!templateId.value) return
    try {
      const result = await getTemplateReviews(templateId.value, { page: 0, size: 20 })
      reviews.value = result.content
      delete warnings.value.reviews
    } catch (e: any) {
      warnings.value.reviews = e.message || 'Refresh failed'
    }
  }

  async function refreshVersions(): Promise<void> {
    if (!templateId.value) return
    try {
      versions.value = await getTemplateVersions(templateId.value)
      delete warnings.value.versions
    } catch (e: any) {
      warnings.value.versions = e.message || 'Refresh failed'
    }
  }

  async function refreshPermissions(): Promise<void> {
    if (!templateId.value) return
    try {
      permissions.value = await getTemplatePermissions(templateId.value)
      delete warnings.value.permissions
    } catch (e: any) {
      warnings.value.permissions = e.message || 'Refresh failed'
    }
  }

  function $reset() {
    templateId.value = 0
    template.value = null
    assemblyConfig.value = null
    dataSources.value = []
    expressions.value = []
    coverage.value = null
    availableTransitions.value = []
    testCases.value = []
    testReport.value = null
    reviews.value = []
    versions.value = []
    permissions.value = []
    loading.value = false
    criticalError.value = null
    warnings.value = {}
  }

  return {
    templateId, template, assemblyConfig, dataSources, expressions,
    coverage, availableTransitions, loading, criticalError, warnings,
    testCases, testReport, reviews, versions, permissions,
    templateStatus, isActive, isDraft,
    initWorkspace, refreshTemplate, refreshAssemblyConfig,
    refreshDataSources, refreshExpressions, refreshCoverage,
    refreshTransitions, refreshTestCases, refreshReviews,
    refreshVersions, refreshPermissions, $reset,
  }
})
