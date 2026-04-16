---
inclusion: auto
name: frontend-architecture
description: Vue 3 前端架构规范，包括路由设计、状态管理、组件设计、TypeScript 类型和 Axios 封装
---

# 前端架构规范

## 路由

- 公开: `{ path: '/login', meta: { requiresAuth: false } }`
- 受保护: 嵌套在 MainLayout 下，`meta: { requiresAuth: true, roles?: UserRole[] }`
- 懒加载: `component: () => import('@/views/module/Index.vue')`
- 参考: #[[file:frontend/src/router/index.ts]]

## Pinia Store

- Composition API: `defineStore('name', () => { ... })` + `ref()` + `computed()` + async
- 现有: `useUserStore` (认证/JWT/角色), `useTemplateWorkspaceStore` (模板工作区)
- 参考: #[[file:frontend/src/stores/user.ts]]

## API 层

- 基础: #[[file:frontend/src/api/request.ts]] — baseURL `/api`, timeout 15s, 401 自动刷新
- 每模块一个文件: `src/api/{module}.ts`

## TypeScript 类型

- 通用: #[[file:frontend/src/types/index.ts]] (ApiResponse, PageResult, TemplateStatus, UserRole)
- 模块: `types/parameter.ts`, `types/workspace.ts`, `types/segment.ts`, `types/document.ts`
- 接口用 `interface`，联合类型用 `type`，与后端 DTO 字段名一致 (camelCase)

## 组件

- `<script setup lang="ts">` + `defineProps<{}>()` + `defineEmits<{}>()`
- 通用组件: `components/` — 现有: OnlyOfficeEditor, TemplateTagToolbar, KeyValueEditor, MonacoEditor
- 模块页面: `views/{module}/`
