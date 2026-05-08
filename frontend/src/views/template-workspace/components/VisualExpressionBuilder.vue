<template>
  <div class="visual-expression-builder">
    <div v-for="(step, index) in steps" :key="index" class="step-row">
      <span class="step-label">{{ index === 0 ? t('parameter.expression.selectParameter') : '' }}</span>
      <el-select v-model="step.source" size="small" :placeholder="t('parameter.expression.selectParameter')" filterable>
        <el-option v-for="p in availableParameters" :key="p.id" :label="p.name" :value="p.name" />
      </el-select>
      <el-select v-model="step.operator" size="small" :placeholder="t('parameter.expression.selectOperator')">
        <el-option-group v-for="group in operatorGroups" :key="group.label" :label="t('parameter.expression.' + group.label)">
          <el-option v-for="op in group.operators" :key="op.value" :label="op.label" :value="op.value" />
        </el-option-group>
      </el-select>
      <el-select v-model="step.operand" size="small" :placeholder="t('parameter.expression.selectParameter')" filterable allow-create>
        <el-option v-for="p in availableParameters" :key="p.id" :label="p.name" :value="p.name" />
      </el-select>
    </div>

    <el-button size="small" @click="addStep">{{ t('parameter.expression.addStep') }}</el-button>

    <div v-if="generatedExpression" class="expression-preview">
      <span class="preview-label">{{ t('parameter.expression.preview') }}:</span>
      <code>{{ generatedExpression }}</code>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ParameterDTO } from '@/types/parameter'

interface ExpressionStep {
  source: string
  operator: string
  operand: string
}

defineProps<{
  availableParameters: ParameterDTO[]
  expressionType: string
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [val: string]
}>()

const { t } = useI18n()

const steps = ref<ExpressionStep[]>([{ source: '', operator: '', operand: '' }])

const operatorGroups = [
  {
    label: 'arithmetic',
    operators: [
      { label: '+', value: '+' },
      { label: '-', value: '-' },
      { label: '×', value: '*' },
      { label: '÷', value: '/' },
    ],
  },
  {
    label: 'comparison',
    operators: [
      { label: '>', value: '>' },
      { label: '<', value: '<' },
      { label: '==', value: '==' },
      { label: '!=', value: '!=' },
      { label: '>=', value: '>=' },
      { label: '<=', value: '<=' },
    ],
  },
  {
    label: 'string',
    operators: [
      { label: 'concat', value: 'concat' },
      { label: 'toUpperCase', value: 'toUpperCase' },
      { label: 'toLowerCase', value: 'toLowerCase' },
    ],
  },
  {
    label: 'logical',
    operators: [
      { label: 'AND', value: '&&' },
      { label: 'OR', value: '||' },
    ],
  },
]

const generatedExpression = computed(() => {
  const parts: string[] = []
  for (const step of steps.value) {
    if (!step.source) continue
    if (parts.length === 0) {
      if (step.operator && step.operand) {
        if (['concat', 'toUpperCase', 'toLowerCase'].includes(step.operator)) {
          parts.push(buildStringOp(step.source, step.operator, step.operand))
        } else {
          parts.push(`${step.source} ${step.operator} ${step.operand}`)
        }
      } else {
        parts.push(step.source)
      }
    } else {
      if (step.operator && step.operand) {
        parts.push(`${step.operator} ${step.operand}`)
      }
    }
  }
  return parts.join(' ')
})

watch(generatedExpression, (val) => {
  emit('update:modelValue', val)
})

function buildStringOp(source: string, op: string, operand: string): string {
  switch (op) {
    case 'concat': return `${source} + ${operand}`
    case 'toUpperCase': return `${source}.toUpperCase()`
    case 'toLowerCase': return `${source}.toLowerCase()`
    default: return `${source} ${op} ${operand}`
  }
}

function addStep() {
  steps.value.push({ source: '', operator: '', operand: '' })
}
</script>

<style scoped>
.visual-expression-builder {
  padding: 8px 0;
}
.step-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.step-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  min-width: 60px;
}
.expression-preview {
  margin-top: 12px;
  padding: 8px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 13px;
}
.preview-label {
  color: var(--el-text-color-secondary);
  margin-right: 8px;
}
.expression-preview code {
  font-family: monospace;
}
</style>
