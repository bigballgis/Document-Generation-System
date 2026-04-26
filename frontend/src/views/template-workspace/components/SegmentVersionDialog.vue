<template>
  <el-dialog
    v-model="visible"
    :title="t('workspace.segment.versionHistory') + ' — ' + segmentName"
    width="720px"
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <!-- Publish action -->
    <div class="publish-row">
      <el-input
        v-model="publishComment"
        :placeholder="t('workspace.segment.publishCommentPlaceholder')"
        size="default"
        style="flex: 1"
      />
      <el-button
        type="primary"
        :loading="publishing"
        @click="handlePublish"
      >
        {{ t('workspace.segment.publishVersion') }}
      </el-button>
    </div>

    <!-- Version list -->
    <el-table
      v-loading="loadingVersions"
      :data="versions"
      border
      stripe
      style="margin-top: 12px"
      max-height="320"
    >
      <el-table-column prop="versionNumber" :label="t('workspace.segment.versionNumber')" width="80" align="center">
        <template #default="{ row }">v{{ row.versionNumber }}</template>
      </el-table-column>
      <el-table-column prop="comment" :label="t('workspace.segment.publishComment')" min-width="160">
        <template #default="{ row }">{{ row.comment || t('common.emptyValue') }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" :label="t('common.createdAt')" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="160" align="center">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="selectForCompare(row)">
            {{ t('workspace.segment.selectCompare') }}
          </el-button>
          <el-popconfirm
            :title="t('workspace.segment.rollbackConfirm')"
            @confirm="handleRollback(row.versionNumber)"
          >
            <template #reference>
              <el-button size="small" text type="warning">
                {{ t('workspace.segment.rollback') }}
              </el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- Version comparison -->
    <div v-if="compareA != null || compareB != null" class="compare-section">
      <div class="compare-header">
        <span>{{ t('workspace.segment.compareTitle') }}:</span>
        <el-tag v-if="compareA != null" closable @close="compareA = null">
          v{{ compareA }}
        </el-tag>
        <span v-if="compareA != null && compareB != null">↔</span>
        <el-tag v-if="compareB != null" closable @close="compareB = null">
          v{{ compareB }}
        </el-tag>
        <el-button
          v-if="compareA != null && compareB != null"
          type="primary"
          size="small"
          :loading="comparing"
          @click="handleCompare"
        >
          {{ t('workspace.segment.compare') }}
        </el-button>
      </div>

      <!-- Diff result -->
      <template v-if="diffResult">
        <div class="diff-info">
          <el-tag v-if="diffResult.diffs.length === 0 && !diffResult.filePathChanged && !diffResult.contentChanged" type="success" size="small">
            {{ t('workspace.segment.noDifferences') }}
          </el-tag>
        </div>
        <!-- File path change -->
        <div v-if="diffResult.filePathChanged" class="diff-file-change">
          <div class="diff-file-label">{{ t('workspace.segment.fileChanged') }}</div>
          <div class="diff-file-paths">
            <div class="diff-old"><span class="diff-prefix">-</span> {{ diffResult.oldFilePath }}</div>
            <div class="diff-new"><span class="diff-prefix">+</span> {{ diffResult.newFilePath }}</div>
          </div>
        </div>
        <el-table
          v-if="diffResult.diffs.length > 0"
          :data="diffResult.diffs"
          border
          stripe
          size="small"
          style="margin-top: 8px"
        >
          <el-table-column prop="field" :label="t('workspace.segment.diffField')" width="160" />
          <el-table-column :label="t('workspace.segment.diffType')" width="100">
            <template #default="{ row }">
              <el-tag :type="diffTypeTag(row.changeType)" size="small">{{ row.changeType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('workspace.segment.diffOldValue')">
            <template #default="{ row }">
              <div class="diff-cell diff-cell-old">{{ row.oldValue ?? t('common.emptyValue') }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="t('workspace.segment.diffNewValue')">
            <template #default="{ row }">
              <div class="diff-cell diff-cell-new">{{ row.newValue ?? t('common.emptyValue') }}</div>
            </template>
          </el-table-column>
        </el-table>

        <!-- Content diff section -->
        <div v-if="diffResult.contentChanged" class="content-diff-section">
          <div class="content-diff-header">
            <el-tag size="small" type="info">{{ t('workspace.segment.contentDiff.title') }}</el-tag>
            <el-tag size="small">{{ t('workspace.segment.contentDiff.textDiffNote') }}</el-tag>
          </div>
          <el-alert
            v-if="diffResult.truncated"
            :title="t('workspace.segment.contentDiff.truncatedWarning', { max: 2000 })"
            type="warning"
            :closable="false"
            show-icon
            style="margin-bottom: 8px"
          />
          <div class="content-diff-view">
            <template v-for="(item, idx) in visibleDiffLines" :key="idx">
              <!-- Collapsed EQUAL lines -->
              <div v-if="item.collapsed" class="diff-line diff-line-collapsed" @click="expandGroup(item.groupId)">
                <span class="diff-line-num">&nbsp;</span>
                <span class="diff-line-num">&nbsp;</span>
                <span class="diff-line-prefix">&nbsp;</span>
                <span class="diff-line-text">
                  {{ t('workspace.segment.contentDiff.collapsedLines', { count: item.count }) }}
                  — {{ t('workspace.segment.contentDiff.expandLines') }}
                </span>
              </div>
              <!-- Normal lines -->
              <template v-else-if="item.line">
                <template v-if="item.line.type === 'MODIFIED'">
                  <div class="diff-line diff-line-removed">
                    <span class="diff-line-num">{{ item.line.oldLineNumber ?? '' }}</span>
                    <span class="diff-line-num">&nbsp;</span>
                    <span class="diff-line-prefix">-</span>
                    <span class="diff-line-text">{{ item.line.oldText ?? '' }}</span>
                  </div>
                  <div class="diff-line diff-line-added">
                    <span class="diff-line-num">&nbsp;</span>
                    <span class="diff-line-num">{{ item.line.newLineNumber ?? '' }}</span>
                    <span class="diff-line-prefix">+</span>
                    <span class="diff-line-text">{{ item.line.newText ?? '' }}</span>
                  </div>
                </template>
                <div v-else :class="['diff-line', diffLineClass(item.line.type)]">
                  <span class="diff-line-num">{{ item.line.oldLineNumber ?? '' }}</span>
                  <span class="diff-line-num">{{ item.line.newLineNumber ?? '' }}</span>
                  <span class="diff-line-prefix">{{ diffLinePrefix(item.line.type) }}</span>
                  <span class="diff-line-text">{{ diffLineText(item.line) }}</span>
                </div>
              </template>
            </template>
            <div v-if="hasMoreLines" class="diff-line diff-line-collapsed" @click="loadMoreLines">
              <span class="diff-line-num">&nbsp;</span>
              <span class="diff-line-num">&nbsp;</span>
              <span class="diff-line-prefix">&nbsp;</span>
              <span class="diff-line-text">
                ↓ {{ t('workspace.segment.contentDiff.expandLines') }} ({{ t('workspace.segment.contentDiff.moreLinesRemaining', { count: processedDiffLines.length - renderLimit }) }})
              </span>
            </div>
          </div>
        </div>
        <div v-else-if="!diffResult.contentChanged" class="content-diff-section">
          <el-tag type="success" size="small">{{ t('workspace.segment.contentDiff.noChanges') }}</el-tag>
        </div>
      </template>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  publishSegment,
  getSegmentVersions,
  compareSegmentVersions,
  rollbackSegmentVersion,
} from '@/api/composite-templates'
import type { SegmentVersionDTO, SegmentVersionDiffResult, ContentDiffLine } from '@/api/composite-templates'

const props = defineProps<{
  modelValue: boolean
  templateId: number
  segmentName: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'published'): void
  (e: 'rolledBack'): void
}>()

const { t } = useI18n()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (val) => { visible.value = val })
watch(visible, (val) => emit('update:modelValue', val))

const versions = ref<SegmentVersionDTO[]>([])
const loadingVersions = ref(false)
const publishing = ref(false)
const publishComment = ref('')

const compareA = ref<number | null>(null)
const compareB = ref<number | null>(null)
const comparing = ref(false)
const diffResult = ref<SegmentVersionDiffResult | null>(null)

watch(
  () => props.modelValue,
  async (val) => {
    if (val && props.segmentName) {
      await loadVersions()
    }
  },
  { immediate: true },
)

async function loadVersions() {
  loadingVersions.value = true
  try {
    versions.value = await getSegmentVersions(props.templateId, props.segmentName)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message)
  } finally {
    loadingVersions.value = false
  }
}

async function handlePublish() {
  publishing.value = true
  try {
    await publishSegment(props.templateId, props.segmentName, publishComment.value || undefined)
    ElMessage.success(t('workspace.segment.publishSuccess'))
    publishComment.value = ''
    await loadVersions()
    emit('published')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message)
  } finally {
    publishing.value = false
  }
}

function selectForCompare(row: SegmentVersionDTO) {
  if (compareA.value == null) {
    compareA.value = row.versionNumber
  } else if (compareB.value == null) {
    compareB.value = row.versionNumber
  } else {
    compareA.value = compareB.value
    compareB.value = row.versionNumber
  }
  diffResult.value = null
}

async function handleCompare() {
  if (compareA.value == null || compareB.value == null) return
  if (compareA.value === compareB.value) {
    ElMessage.warning(t('workspace.segment.sameVersionWarning'))
    return
  }
  if (comparing.value) return // prevent double-click
  comparing.value = true
  try {
    diffResult.value = await compareSegmentVersions(
      props.templateId, props.segmentName, compareA.value, compareB.value, true,
    )
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message)
  } finally {
    comparing.value = false
  }
}

async function handleRollback(targetVersion: number) {
  try {
    await rollbackSegmentVersion(props.templateId, props.segmentName, targetVersion)
    ElMessage.success(t('workspace.segment.rollbackSuccess'))
    emit('rolledBack')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message)
  }
}

function handleClosed() {
  compareA.value = null
  compareB.value = null
  diffResult.value = null
  publishComment.value = ''
  expandedGroups.value.clear()
  renderLimit.value = 200
}

function formatTime(iso: string) {
  if (!iso) return t('common.emptyValue')
  return new Date(iso).toLocaleString()
}

function diffTypeTag(type: string) {
  const map: Record<string, string> = { ADDED: 'success', REMOVED: 'danger', MODIFIED: 'warning' }
  return (map[type] || 'info') as any
}

// ── Content diff helpers ──

interface ProcessedDiffItem {
  collapsed?: boolean
  count?: number
  groupId?: number
  line?: ContentDiffLine
}

const expandedGroups = ref<Set<number>>(new Set())
const renderLimit = ref(200) // Progressive rendering: start with 200 items

const processedDiffLines = computed<ProcessedDiffItem[]>(() => {
  if (!diffResult.value?.contentDiffs) return []
  const lines = diffResult.value.contentDiffs
  const result: ProcessedDiffItem[] = []
  let i = 0
  let groupId = 0

  while (i < lines.length) {
    if (lines[i].type === 'EQUAL') {
      // Count consecutive EQUAL lines
      let start = i
      while (i < lines.length && lines[i].type === 'EQUAL') {
        i++
      }
      let count = i - start
      if (count > 7 && !expandedGroups.value.has(groupId)) {
        // Show first 3 and last 3, collapse the middle (only when saving ≥4 lines)
        result.push({ line: lines[start] })
        result.push({ line: lines[start + 1] })
        result.push({ line: lines[start + 2] })
        result.push({ collapsed: true, count: count - 6, groupId })
        result.push({ line: lines[i - 3] })
        result.push({ line: lines[i - 2] })
        result.push({ line: lines[i - 1] })
      } else {
        for (let j = start; j < i; j++) {
          result.push({ line: lines[j] })
        }
      }
      groupId++
    } else {
      result.push({ line: lines[i] })
      i++
    }
  }
  return result
})

/** Visible slice of processedDiffLines for progressive rendering */
const visibleDiffLines = computed(() => processedDiffLines.value.slice(0, renderLimit.value))
const hasMoreLines = computed(() => processedDiffLines.value.length > renderLimit.value)

function loadMoreLines() {
  renderLimit.value += 200
}

function expandGroup(gid: number | undefined) {
  if (gid == null) return
  // Replace Set so Vue tracks the dependency (mutating Set in-place does not trigger computed refresh).
  const next = new Set(expandedGroups.value)
  next.add(gid)
  expandedGroups.value = next
}

function diffLineClass(type: string) {
  const map: Record<string, string> = {
    EQUAL: 'diff-line-equal',
    ADDED: 'diff-line-added',
    REMOVED: 'diff-line-removed',
    MODIFIED: 'diff-line-modified',
  }
  return map[type] || ''
}

function diffLinePrefix(type: string) {
  const map: Record<string, string> = { ADDED: '+', REMOVED: '-', EQUAL: ' ' }
  return map[type] || ' '
}

function diffLineText(line: ContentDiffLine) {
  if (line.type === 'ADDED') return line.newText ?? ''
  if (line.type === 'REMOVED') return line.oldText ?? ''
  return line.oldText ?? line.newText ?? ''
}
</script>

<style scoped>
.publish-row {
  display: flex;
  gap: 10px;
  align-items: center;
}
.compare-section {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-light);
}
.compare-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.diff-info {
  margin-top: 8px;
}
.diff-file-change {
  margin-top: 8px;
  padding: 8px 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
  font-family: monospace;
  font-size: 12px;
}
.diff-file-label {
  font-weight: 600;
  color: var(--el-color-warning);
  margin-bottom: 4px;
  font-family: inherit;
}
.diff-file-paths { display: flex; flex-direction: column; gap: 2px; }
.diff-old { color: var(--el-color-danger); }
.diff-new { color: var(--el-color-success); }
.diff-prefix { font-weight: 700; margin-right: 4px; }
.diff-cell { font-size: 12px; white-space: pre-wrap; word-break: break-all; max-height: 120px; overflow-y: auto; }
.diff-cell-old { background: var(--el-color-danger-light-9); padding: 4px 6px; border-radius: 2px; }
.diff-cell-new { background: var(--el-color-success-light-9); padding: 4px 6px; border-radius: 2px; }

/* Content diff styles */
.content-diff-section {
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px dashed var(--el-border-color-light);
}
.content-diff-header {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.content-diff-view {
  max-height: 500px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  font-family: 'Courier New', Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
}
.diff-line {
  display: flex;
  align-items: stretch;
  min-height: 22px;
}
.diff-line-num {
  display: inline-block;
  width: 40px;
  min-width: 40px;
  text-align: right;
  padding: 0 6px;
  color: var(--el-text-color-secondary);
  border-right: 1px solid var(--el-border-color-lighter);
  user-select: none;
  flex-shrink: 0;
}
.diff-line-prefix {
  display: inline-block;
  width: 16px;
  min-width: 16px;
  text-align: center;
  font-weight: 700;
  flex-shrink: 0;
}
.diff-line-text {
  flex: 1;
  padding: 0 8px;
  white-space: pre-wrap;
  word-break: break-all;
}
.diff-line-equal {
  background: var(--el-fill-color-lighter);
}
.diff-line-added {
  background: var(--el-color-success-light-9);
}
.diff-line-added .diff-line-prefix {
  color: var(--el-color-success);
}
.diff-line-removed {
  background: var(--el-color-danger-light-9);
}
.diff-line-removed .diff-line-prefix {
  color: var(--el-color-danger);
}
.diff-line-collapsed {
  background: var(--el-fill-color);
  cursor: pointer;
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-style: italic;
  border-top: 1px dashed var(--el-border-color-lighter);
  border-bottom: 1px dashed var(--el-border-color-lighter);
}
.diff-line-collapsed:hover {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
</style>
