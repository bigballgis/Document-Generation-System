---
inclusion: auto
name: frontend-architecture
description: Vue 3 前端架构规范，包括路由设计、状态管理、组件设计、TypeScript 类型和 Axios 封装
---

# 前端架构规范

## 路由设计

- 公开路由: `{ path: '/login', meta: { requiresAuth: false } }`
- 受保护路由嵌套在 MainLayout 下，`meta: { requiresAuth: true, roles?: UserRole[] }`
- 懒加载: `component: () => import('@/views/module/Index.vue')`
- 新模块: `views/{module}/Index.vue` + `Detail.vue` → `router/index.ts` 注册

## Pinia Store

- Composition API 风格: `defineStore('name', () => { ... })`
- `ref()` 状态 + `computed()` 派生 + async 函数
- 路径: `frontend/src/stores/{storeName}.ts`
- 现有: `useUserStore` (认证/JWT/角色)

## API 层 (`src/api/request.ts`)

- baseURL `/api`, timeout 15000ms
- 请求拦截器自动附加 JWT，响应拦截器 401 自动刷新
- 每模块一个文件: `src/api/segments.ts`

```typescript
export function listSegments(params?: { keyword?: string }) {
  return request.get<PageResult<Segment>>('/segments', { params })
}
```

## TypeScript 类型

- 通用: `frontend/src/types/index.ts` (ApiResponse, PageResult, TemplateStatus, UserRole)
- 接口用 `interface`，联合类型用 `type`，与后端 DTO 字段名一致 (camelCase)

## 组件规范

- `<script setup lang="ts">` + `defineProps<{}>()` + `defineEmits<{}>()`
- UI: Element Plus (自动导入) / 图表: vue-echarts
- 通用组件 `components/`，模块页面 `views/{module}/`
