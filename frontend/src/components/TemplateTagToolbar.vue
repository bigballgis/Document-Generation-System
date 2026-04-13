<template>
  <div class="template-tag-toolbar">
    <el-button-group>
      <el-popover
        :visible="variablePopoverVisible"
        placement="bottom"
        :width="280"
        trigger="click"
      >
        <template #reference>
          <el-button size="small" @click="variablePopoverVisible = !variablePopoverVisible">
            <el-icon><EditPen /></el-icon>
            {{ $t('editor.insertVariable') }}
          </el-button>
        </template>
        <div class="popover-content">
          <el-input
            v-model="variableName"
            :placeholder="$t('editor.variablePlaceholder')"
            size="small"
            @keyup.enter="handleInsertVariable"
          />
          <el-button
            type="primary"
            size="small"
            :disabled="!variableName.trim()"
            style="margin-top: 8px; width: 100%"
            @click="handleInsertVariable"
          >
            {{ $t('common.confirm') }}
          </el-button>
          <div class="tag-preview">
            <code v-if="variableName.trim()">{{"{"}}{{ variableName.trim() }}{{"}"}}</code>
          </div>
        </div>
      </el-popover>

      <el-popover
        :visible="loopPopoverVisible"
        placement="bottom"
        :width="280"
        trigger="click"
      >
        <template #reference>
          <el-button size="small" @click="loopPopoverVisible = !loopPopoverVisible">
            <el-icon><Refresh /></el-icon>
            {{ $t('editor.insertLoop') }}
          </el-button>
        </template>
        <div class="popover-content">
          <el-input
            v-model="loopName"
            :placeholder="$t('editor.loopPlaceholder')"
            size="small"
            @keyup.enter="handleInsertLoop"
          />
          <el-button
            type="primary"
            size="small"
            :disabled="!loopName.trim()"
            style="margin-top: 8px; width: 100%"
            @click="handleInsertLoop"
          >
            {{ $t('common.confirm') }}
          </el-button>
          <div class="tag-preview">
            <code v-if="loopName.trim()">{{"{"}}#{{ loopName.trim() }}{{"}"}}...{{"{"}}/<span>{{ loopName.trim() }}</span>{{"}"}}</code>
          </div>
        </div>
      </el-popover>

      <el-popover
        :visible="conditionPopoverVisible"
        placement="bottom"
        :width="280"
        trigger="click"
      >
        <template #reference>
          <el-button size="small" @click="conditionPopoverVisible = !conditionPopoverVisible">
            <el-icon><Switch /></el-icon>
            {{ $t('editor.insertCondition') }}
          </el-button>
        </template>
        <div class="popover-content">
          <el-input
            v-model="conditionExpr"
            :placeholder="$t('editor.conditionPlaceholder')"
            size="small"
            @keyup.enter="handleInsertCondition"
          />
          <el-button
            type="primary"
            size="small"
            :disabled="!conditionExpr.trim()"
            style="margin-top: 8px; width: 100%"
            @click="handleInsertCondition"
          >
            {{ $t('common.confirm') }}
          </el-button>
          <div class="tag-preview">
            <code v-if="conditionExpr.trim()">{{"{"}}#if {{ conditionExpr.trim() }}{{"}"}}...{{"{"}}/if{{"}"}}</code>
          </div>
        </div>
      </el-popover>
    </el-button-group>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { EditPen, Refresh, Switch } from '@element-plus/icons-vue'

const emit = defineEmits<{
  (e: 'insert-variable', name: string): void
  (e: 'insert-loop', arrayName: string): void
  (e: 'insert-condition', expr: string): void
}>()

const variablePopoverVisible = ref(false)
const loopPopoverVisible = ref(false)
const conditionPopoverVisible = ref(false)

const variableName = ref('')
const loopName = ref('')
const conditionExpr = ref('')

function handleInsertVariable() {
  const name = variableName.value.trim()
  if (!name) return
  emit('insert-variable', name)
  variableName.value = ''
  variablePopoverVisible.value = false
}

function handleInsertLoop() {
  const name = loopName.value.trim()
  if (!name) return
  emit('insert-loop', name)
  loopName.value = ''
  loopPopoverVisible.value = false
}

function handleInsertCondition() {
  const expr = conditionExpr.value.trim()
  if (!expr) return
  emit('insert-condition', expr)
  conditionExpr.value = ''
  conditionPopoverVisible.value = false
}
</script>

<style scoped>
.template-tag-toolbar {
  padding: 8px 12px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  align-items: center;
  gap: 8px;
}

.popover-content {
  display: flex;
  flex-direction: column;
}

.tag-preview {
  margin-top: 8px;
  padding: 6px 8px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 12px;
}

.tag-preview code {
  color: var(--el-color-primary);
  word-break: break-all;
}
</style>
