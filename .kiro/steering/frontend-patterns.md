---
description: Vue 3 前端开发模式，包括页面组件模式、表格列表模式、表单对话框模式、Composable 模式和 OnlyOffice 集成模式
inclusion: auto
fileMatchPattern: 'frontend/src/views/**/*.vue,frontend/src/components/**/*.vue,frontend/src/composables/**/*.ts'
---

# 前端开发模式

## 页面组件模式 (View Pattern)

每个功能模块的列表页遵循统一结构：

```vue
<template>
  <div class="module-page">
    <!-- 1. 页面头部：标题 + 操作按钮 -->
    <div class="page-header">
      <h2>{{ $t('module.title') }}</h2>
      <el-button type="primary" @click="openCreateDialog">
        {{ $t('module.create') }}
      </el-button>
    </div>

    <!-- 2. 筛选区域 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <!-- 筛选项 -->
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
          <el-button @click="resetFilters">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 3. 数据表格 -->
    <el-card shadow="never" style="margin-top: 16px">
      <el-table :data="items" v-loading="loading" stripe>
        <!-- 列定义 -->
        <el-table-column :label="$t('common.actions')" fixed="right">
          <template #default="{ row }">
            <!-- 操作按钮 -->
          </template>
        </el-table-column>
      </el-table>

      <!-- 4. 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- 5. 创建/编辑对话框（独立子组件） -->
    <ModuleFormDialog v-model:visible="dialogVisible" :data="editingItem" @saved="onSaved" />
  </div>
</template>
```

### 关键约定

- 页面组件放在 `views/{module}/Index.vue`
- 详情页放在 `views/{module}/Detail.vue`
- 编辑器页放在 `views/{module}/Editor.vue`
- 页面内子组件放在 `views/{module}/components/` 下
- 使用 `reactive` 管理查询参数，`ref` 管理列表数据和加载状态

## 表格列表模式 (Table Pattern)

```typescript
// 标准列表页状态
const loading = ref(false)
const items = ref<ItemDTO[]>([])
const total = ref(0)
const query = reactive<QueryParams>({
  keyword: '',
  page: 1,
  size: 10,
})

// 标准数据获取
async function fetchData() {
  loading.value = true
  try {
    const res = await listItems(query)
    items.value = res.content
    total.value = res.totalElements
  } catch { /* interceptor 处理 */ } finally {
    loading.value = false
  }
}

// 搜索重置页码
function handleSearch() {
  query.page = 1
  fetchData()
}

// 页面挂载时加载
onMounted(() => { fetchData() })
```

## 表单对话框模式 (Form Dialog Pattern)

表单对话框作为独立子组件，通过 `v-model:visible` 控制显隐：

```vue
<!-- 父组件调用 -->
<FormDialog v-model:visible="dialogVisible" :data="editingItem" @saved="onSaved" />
```

```vue
<!-- FormDialog.vue -->
<script setup lang="ts">
const props = defineProps<{
  visible: boolean
  data: ItemDTO | null  // null = 创建模式，非 null = 编辑模式
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'saved'): void
}>()

const isEdit = computed(() => !!props.data)
const title = computed(() => isEdit.value ? t('common.edit') : t('common.create'))

// 表单提交后
async function handleSubmit() {
  if (isEdit.value) {
    await updateItem(props.data!.id, form)
  } else {
    await createItem(form)
  }
  ElMessage.success(t('message.saveSuccess'))
  emit('saved')
  emit('update:visible', false)
}
</script>
```

## 危险操作确认模式 (Confirm Pattern)

删除、归档等危险操作使用 `ElMessageBox.confirm`：

```typescript
async function handleDelete(row: ItemDTO) {
  try {
    await ElMessageBox.confirm(
      t('module.confirmDelete', { name: row.name }),
      t('confirm.deleteTitle'),
      { type: 'warning' },
    )
    await deleteItem(row.id)
    ElMessage.success(t('message.deleteSuccess'))
    fetchData()
  } catch { /* 用户取消或请求失败 */ }
}
```

## Composable 模式

可复用逻辑提取为 composable 函数：

```typescript
// composables/useXxx.ts
export function useXxx() {
  const state = ref(...)
  const derived = computed(() => ...)

  function action() { ... }

  return { state, derived, action }
}
```

### 现有 Composable

- `useLocale()` — 语言切换，返回 `{ locale, t, setLocale }`

### 命名规范

- 文件名: `use{Feature}.ts`（如 `useSegmentDrag.ts`）
- 函数名: `use{Feature}`
- 放在 `frontend/src/composables/` 目录

## OnlyOffice 集成模式

### 编辑器组件使用

```vue
<OnlyOfficeEditor
  :document-url="docUrl"
  :document-key="docKey"
  :document-title="title"
  :callback-url="callbackUrl"
  :view-only="isPreview"
  @ready="onEditorReady"
  @error="onEditorError"
/>
```

### 关键 Props

| Prop | 类型 | 说明 |
|------|------|------|
| `documentUrl` | string | MinIO 文档 URL |
| `documentKey` | string | 文档版本唯一标识 |
| `callbackUrl` | string | 保存回调 URL |
| `viewOnly` | boolean | 预览模式 |

### 模板变量插入

通过 `TemplateTagToolbar` 组件提供三种插入操作：
- `insert-variable` → `{variableName}`
- `insert-loop` → `{#arrayName}...{/arrayName}`
- `insert-condition` → `{#if expr}...{/if}`

## 状态标签映射模式

模板状态使用 `el-tag` 展示，颜色映射：

```typescript
function statusTagType(status: string): TagType {
  const map: Record<string, TagType> = {
    DRAFT: 'info',
    PENDING_REVIEW: 'warning',
    REVIEWED: 'primary',
    ACTIVE: 'success',
    ARCHIVED: 'danger',
  }
  return map[status] || 'info'
}
```

## CSS 规范

- 使用 `<style scoped>` 避免样式污染
- 使用 Element Plus CSS 变量（`var(--el-color-primary)` 等）
- 页面布局类名: `.module-page`, `.page-header`, `.filter-card`, `.pagination-wrapper`
- 避免内联样式，除非是简单的 margin/width 调整
