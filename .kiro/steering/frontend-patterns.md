---
inclusion: auto
name: frontend-patterns
description: Vue 3 前端开发模式，包括页面组件模式、表格列表模式、表单对话框模式、Composable 模式和 OnlyOffice 集成模式
---

# 前端开发模式

## 页面组件结构

```
views/{module}/Index.vue    — 列表页
views/{module}/Detail.vue   — 详情页
views/{module}/Editor.vue   — 编辑器页
views/{module}/components/  — 子组件
```

统一结构: 页面头部(标题+操作) → 筛选区(el-form inline) → 数据表格(el-table+v-loading) → 分页(el-pagination) → 对话框(独立子组件)

## 表格列表状态模式

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

## 表单对话框模式

```vue
<FormDialog v-model:visible="dialogVisible" :data="editingItem" @saved="onSaved" />
```

- `data: null` = 创建模式，非 null = 编辑模式
- 提交后 `emit('saved')` + `emit('update:visible', false)`

## 危险操作

`ElMessageBox.confirm` → 执行 → `ElMessage.success` → `fetchData()`

## Composable

- 文件: `composables/use{Feature}.ts`，函数: `use{Feature}`
- 现有: `useLocale()` (locale, t, setLocale)

## OnlyOffice

- `<OnlyOfficeEditor :document-url :document-key :callback-url :view-only />`
- 模板变量插入: `TemplateTagToolbar` (insert-variable/insert-loop/insert-condition)

## CSS

- `<style scoped>` + Element Plus CSS 变量
- 类名: `.module-page`, `.page-header`, `.filter-card`, `.pagination-wrapper`
