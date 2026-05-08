---
inclusion: auto
name: frontend-patterns
description: Vue 3 UI patterns — list pages, dialogs, composables, Element Plus layout
---

# Frontend patterns

## Page layout

```
views/{module}/Index.vue    — list
views/{module}/Detail.vue   — detail
views/{module}/components/  — child components
```

Typical list page: header (title + actions) → filters (`el-form` inline) → table (`el-table` + `v-loading`) → pagination (`el-pagination`) → dialog (child component)

## List + table state

```typescript
const loading = ref(false)
const items = ref<ItemDTO[]>([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, size: 10 })

async function fetchData() {
  loading.value = true
  try {
    const res = await listItems(query)
    items.value = res.content
    total.value = res.totalElements
  } finally { loading.value = false }
}
function handleSearch() { query.page = 1; fetchData() }
onMounted(() => fetchData())
```

## Form dialog

- `<FormDialog v-model:visible="dialogVisible" :data="editingItem" @saved="onSaved" />`
- `data === null` → create; non-null → edit
- On success: `emit('saved')` + `emit('update:visible', false)`

## Destructive actions

`ElMessageBox.confirm` → action → `ElMessage.success` → `fetchData()`

## Composables

- Files: `composables/use{Feature}.ts`
- Examples: useAssemblyConfig, useParameterUtils, useSegmentDrag, useTaskPolling, useWorkflowSteps

## CSS

- `<style scoped>` + Element Plus CSS variables
- Classes: `.module-page`, `.page-header`, `.filter-card`, `.pagination-wrapper`
