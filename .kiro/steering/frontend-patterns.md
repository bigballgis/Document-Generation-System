---
inclusion: auto
name: frontend-patterns
description: Vue 3 前端开发模式，包括页面组件模式、表格列表模式、表单对话框模式、Composable 模式和 OnlyOffice 集成模式
---

# 前端开发模式

## 页面结构

```
views/{module}/Index.vue    — 列表页
views/{module}/Detail.vue   — 详情页
views/{module}/components/  — 子组件
```

页面布局: 头部(标题+操作) → 筛选区(el-form inline) → 表格(el-table+v-loading) → 分页(el-pagination) → 对话框(子组件)

## 表格列表状态

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

## 表单对话框

- `<FormDialog v-model:visible="dialogVisible" :data="editingItem" @saved="onSaved" />`
- `data: null` = 创建，非 null = 编辑
- 提交后 `emit('saved')` + `emit('update:visible', false)`

## 危险操作

`ElMessageBox.confirm` → 执行 → `ElMessage.success` → `fetchData()`

## Composable

- 文件: `composables/use{Feature}.ts`
- 现有: useAssemblyConfig, useParameterUtils, useSegmentDrag, useTaskPolling, useWorkflowSteps

## CSS

- `<style scoped>` + Element Plus CSS 变量
- 类名: `.module-page`, `.page-header`, `.filter-card`, `.pagination-wrapper`
