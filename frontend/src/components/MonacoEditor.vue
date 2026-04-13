<template>
  <div ref="editorContainer" class="monaco-editor-wrapper" />
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as monaco from 'monaco-editor'

const LANG_ID = 'docxtemplater'

export interface MonacoEditorProps {
  modelValue?: string
  readOnly?: boolean
  theme?: string
}

const props = withDefaults(defineProps<MonacoEditorProps>(), {
  modelValue: '',
  readOnly: false,
  theme: 'vs',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'change', value: string): void
}>()

const editorContainer = ref<HTMLElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null
let isUpdatingFromProp = false

/** Register the custom Docxtemplater language once */
function registerDocxtemplaterLanguage() {
  if (monaco.languages.getLanguages().some((l) => l.id === LANG_ID)) return

  monaco.languages.register({ id: LANG_ID })

  monaco.languages.setMonarchTokensProvider(LANG_ID, {
    tokenizer: {
      root: [
        // Loop open tags: {#items}, {#each list}
        [/\{#\w[\w.]*\}/, 'tag.loop'],
        // Loop close tags: {/items}
        [/\{\/\w[\w.]*\}/, 'tag.loop'],
        // Conditional open: {#if condition}
        [/\{#if\b[^}]*\}/, 'tag.condition'],
        // Conditional close: {/if}
        [/\{\/if\}/, 'tag.condition'],
        // Else tag
        [/\{#else\}/, 'tag.condition'],
        // Variable tags: {variableName}, {object.property}
        [/\{[\w][\w.]*\}/, 'tag.variable'],
      ],
    },
  })

  monaco.editor.defineTheme('docxtemplater-theme', {
    base: 'vs',
    inherit: true,
    rules: [
      { token: 'tag.variable', foreground: '1976D2', fontStyle: 'bold' },
      { token: 'tag.loop', foreground: '388E3C', fontStyle: 'bold' },
      { token: 'tag.condition', foreground: 'F57C00', fontStyle: 'bold' },
    ],
    colors: {},
  })

  // Auto-completion provider
  monaco.languages.registerCompletionItemProvider(LANG_ID, {
    triggerCharacters: ['{', '#', '/'],
    provideCompletionItems(model, position) {
      const word = model.getWordUntilPosition(position)
      const range: monaco.IRange = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      }

      // Check what character precedes the cursor to provide context-aware completions
      const lineContent = model.getLineContent(position.lineNumber)
      const charBefore = lineContent.charAt(position.column - 2)

      const suggestions: monaco.languages.CompletionItem[] = []

      if (charBefore === '{') {
        // After opening brace — suggest variable, loop, condition patterns
        suggestions.push(
          {
            label: '{variable}',
            kind: monaco.languages.CompletionItemKind.Variable,
            insertText: 'variable}',
            detail: 'Insert a template variable',
            range,
          },
          {
            label: '{#loop}...{/loop}',
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: '#${1:items}}\n  $0\n{/${1:items}}',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            detail: 'Insert a loop block',
            range,
          },
          {
            label: '{#if}...{/if}',
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: '#if ${1:condition}}\n  $0\n{/if}',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            detail: 'Insert a conditional block',
            range,
          },
          {
            label: '{#if}...{#else}...{/if}',
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: '#if ${1:condition}}\n  $2\n{#else}\n  $0\n{/if}',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            detail: 'Insert a conditional block with else',
            range,
          },
        )
      } else {
        // General suggestions
        suggestions.push(
          {
            label: '{variable}',
            kind: monaco.languages.CompletionItemKind.Variable,
            insertText: '{${1:variable}}',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            detail: 'Insert a template variable',
            range,
          },
          {
            label: '{#loop}...{/loop}',
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: '{#${1:items}}\n  $0\n{/${1:items}}',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            detail: 'Insert a loop block',
            range,
          },
          {
            label: '{#if}...{/if}',
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: '{#if ${1:condition}}\n  $0\n{/if}',
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            detail: 'Insert a conditional block',
            range,
          },
        )
      }

      return { suggestions }
    },
  })
}

function createEditor() {
  if (!editorContainer.value) return

  registerDocxtemplaterLanguage()

  editor = monaco.editor.create(editorContainer.value, {
    value: props.modelValue,
    language: LANG_ID,
    theme: 'docxtemplater-theme',
    readOnly: props.readOnly,
    minimap: { enabled: false },
    automaticLayout: true,
    wordWrap: 'on',
    fontSize: 14,
    lineNumbers: 'on',
    scrollBeyondLastLine: false,
    tabSize: 2,
  })

  editor.onDidChangeModelContent(() => {
    if (isUpdatingFromProp) return
    const value = editor!.getValue()
    emit('update:modelValue', value)
    emit('change', value)
  })
}

watch(
  () => props.modelValue,
  (newVal) => {
    if (editor && newVal !== editor.getValue()) {
      isUpdatingFromProp = true
      editor.setValue(newVal)
      isUpdatingFromProp = false
    }
  },
)

watch(
  () => props.readOnly,
  (val) => {
    editor?.updateOptions({ readOnly: val })
  },
)

onMounted(() => {
  createEditor()
})

onBeforeUnmount(() => {
  editor?.dispose()
  editor = null
})

defineExpose({
  getEditor: () => editor,
})
</script>

<style scoped>
.monaco-editor-wrapper {
  width: 100%;
  height: 100%;
  min-height: 400px;
}
</style>
