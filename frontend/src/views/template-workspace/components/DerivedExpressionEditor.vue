<template>
  <div class="derived-expression-editor">
    <el-tag
      v-if="scopeLevel === 'row'"
      type="warning"
      size="small"
      class="scope-indicator"
    >
      {{ t('workspace.design.sidebar.rowExpression') }}
    </el-tag>
    <el-tag
      v-else-if="scopeLevel === 'object'"
      type="info"
      size="small"
      class="scope-indicator"
    >
      {{ t('workspace.design.sidebar.objectExpression') }}
    </el-tag>

    <el-tabs v-model="activeMode" type="border-card" size="small">
      <el-tab-pane :label="t('parameter.expression.visualMode')" name="visual">
        <VisualExpressionBuilder
          :available-parameters="availableParameters"
          :expression-type="expressionType"
          :model-value="expressionText"
          @update:model-value="handleExpressionChange"
        />
      </el-tab-pane>
      <el-tab-pane :label="t('parameter.expression.advancedMode')" name="advanced">
        <el-input
          v-model="localExpression"
          type="textarea"
          :rows="4"
          :placeholder="expressionType === 'JAVASCRIPT' ? 'e.g. price * quantity' : 'e.g. =A1*B1'"
          @input="handleExpressionChange"
        />
      </el-tab-pane>
    </el-tabs>

    <div class="editor-footer">
      <div class="expression-type-select">
        <el-radio-group :model-value="expressionType" size="small" @change="(val: string | number | boolean | undefined) => emit('update:expressionType', val as string)">
          <el-radio-button value="JAVASCRIPT">JavaScript</el-radio-button>
          <el-radio-button value="EXCEL_FORMULA">Excel</el-radio-button>
        </el-radio-group>
      </div>
      <el-button size="small" @click="handleTest">{{ t('parameter.expression.test') }}</el-button>
    </div>

    <div v-if="testResult !== null" class="test-result">
      <span class="test-label">{{ t('parameter.expression.testResult') }}:</span>
      <code>{{ testResult }}</code>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ParameterDTO } from '@/types/parameter'
import VisualExpressionBuilder from './VisualExpressionBuilder.vue'

const props = defineProps<{
  expressionText: string
  expressionType: string
  availableParameters: ParameterDTO[]
  scopeLevel?: 'root' | 'row' | 'object'
}>()

const emit = defineEmits<{
  'update:expressionText': [val: string]
  'update:expressionType': [val: string]
}>()

const { t } = useI18n()
const activeMode = ref('visual')
const localExpression = ref(props.expressionText)
const testResult = ref<string | null>(null)

watch(() => props.expressionText, (val) => {
  localExpression.value = val
})

function handleExpressionChange(val: string | undefined) {
  const text = val ?? ''
  localExpression.value = text
  emit('update:expressionText', text)
}

function handleTest() {
  try {
    // Simple evaluation for testing purposes
    if (props.expressionType === 'JAVASCRIPT') {
      // eslint-disable-next-line no-new-func
      const fn = new Function('return ' + localExpression.value)
      testResult.value = String(fn())
    } else {
      testResult.value = localExpression.value
    }
  } catch (e: any) {
    testResult.value = `Error: ${e.message}`
  }
}
</script>

<style scoped>
.derived-expression-editor {
  margin-top: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px;
  background: var(--el-fill-color-blank);
}
.scope-indicator {
  margin-bottom: 8px;
}
.editor-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.test-result {
  margin-top: 8px;
  padding: 8px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 13px;
}
.test-label {
  color: var(--el-text-color-secondary);
  margin-right: 8px;
}
.test-result code {
  font-family: monospace;
}
</style>
