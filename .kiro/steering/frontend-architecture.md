---
description: Vue 3 前端架构规范，包括路由设计、状态管理、组件设计、TypeScript 类型和 Axios 封装
inclusion: auto
fileMatchPattern: 'frontend/src/**/*.ts,frontend/src/**/*.vue'
---

# 前端架构规范

## 路由设计

### 路由结构

```typescript
// 公开路由（无需认证）
{ path: '/login', meta: { requiresAuth: false } }
{ path: '/register', meta: { requiresAuth: false } }

// 受保护路由（需要认证，嵌套在 MainLayout 下）
{
  path: '/',
  component: MainLayout,
  meta: { requiresAuth: true },
  children: [
    { path: 'module-name', name: 'ModuleName', component: () => import('@/views/module-name/Index.vue') },
    { path: 'module-name/:id', name: 'ModuleDetail', component: () => import('@/views/module-name/Detail.vue') },
  ]
}
```

### 路由 Meta 字段

- `requiresAuth: boolean` — 是否需要认证
- `roles?: UserRole[]` — 允许访问的角色列表（空表示所有已认证用户）
- `title?: string` — 页面标题

### 新增模块路由

新功能模块（如 segments）需要：
1. 在 `frontend/src/views/` 下创建对应目录（如 `segments/`）
2. 创建 `Index.vue`（列表页）和 `Detail.vue`（详情页）
3. 在 `router/index.ts` 中注册路由
4. 使用懒加载: `component: () => import('@/views/segments/Index.vue')`

## 状态管理 (Pinia)

### Store 规范

- 使用 Composition API 风格 (`defineStore('name', () => { ... })`)
- Store 文件路径: `frontend/src/stores/{storeName}.ts`
- 导出类型和 Store 函数
- 使用 `ref()` 定义状态，`computed()` 定义派生状态
- 异步操作直接在 Store 中定义为 async 函数

### 现有 Store

- `useUserStore` — 用户认证状态、JWT Token 管理、角色信息

## API 调用层

### Axios 实例 (`src/api/request.ts`)

- baseURL: `/api`
- timeout: 15000ms
- 自动附加 JWT Token（请求拦截器）
- 401 自动刷新 Token（响应拦截器）
- 错误消息通过 `ElMessage` 展示

### API 文件规范

每个功能模块一个 API 文件:
```typescript
// src/api/segments.ts
import request from './request'
import type { Segment, CreateSegmentRequest } from '@/types'

export function listSegments(params?: { keyword?: string }) {
  return request.get<PageResult<Segment>>('/segments', { params })
}

export function createSegment(data: CreateSegmentRequest) {
  return request.post<Segment>('/segments', data)
}
```

## TypeScript 类型

### 类型文件路径

- 通用类型: `frontend/src/types/index.ts`
- 模块专用类型可在 API 文件中定义或在 `types/` 下新建文件

### 通用类型

```typescript
interface ApiResponse<T>  // API 响应包装
interface PageResult<T>   // 分页结果
type TemplateStatus       // 模板状态枚举
type UserRole             // 用户角色枚举
```

### 新增类型规范

- 接口使用 `interface`，联合类型使用 `type`
- 与后端 DTO 对应的类型保持字段名一致（camelCase）
- 枚举值使用字符串字面量联合类型

## 组件设计

### 组件分类

- `components/` — 跨模块通用组件（如 MonacoEditor, OnlyOfficeEditor）
- `views/{module}/` — 模块页面组件
- 模块内部子组件放在对应 views 目录下

### 组件规范

- 使用 `<script setup lang="ts">`
- Props 使用 `defineProps<{ ... }>()`
- Emits 使用 `defineEmits<{ ... }>()`
- UI 组件使用 Element Plus（通过 unplugin-vue-components 自动导入）
- 图表使用 ECharts（通过 vue-echarts）
