---
inclusion: auto
name: frontend-architecture
description: Vue 3 frontend architecture — routing, Pinia, API layer, TypeScript, components
---

# Frontend architecture

## Routing

- Public: `{ path: '/login', meta: { requiresAuth: false } }`
- Protected: nested under MainLayout, `meta: { requiresAuth: true, roles?: UserRole[] }`
- Lazy: `component: () => import('@/views/module/Index.vue')`
- Reference: #[[file:frontend/src/router/index.ts]]

## Pinia

- Composition API: `defineStore('name', () => { ... })` with `ref()` / `computed()` / async actions
- Existing: `useUserStore` (auth/JWT/roles), `useTemplateWorkspaceStore` (workspace)
- Reference: #[[file:frontend/src/stores/user.ts]]

## API layer

- Base client: #[[file:frontend/src/api/request.ts]] — baseURL `/api`, timeout 15s, 401 refresh
- One module file: `src/api/{module}.ts`

## TypeScript

- Shared: #[[file:frontend/src/types/index.ts]] (`ApiResponse`, `PageResult`, `TemplateStatus`, `UserRole`)
- Domain: `types/parameter.ts`, `types/workspace.ts`, `types/segment.ts`, `types/document.ts`
- Prefer `interface` for objects, `type` for unions; field names match backend DTOs (camelCase)

## Components

- `<script setup lang="ts">` + `defineProps<{}>()` + `defineEmits<{}>()`
- Shared: `components/` — e.g. OnlyOfficeEditor, TemplateTagToolbar, KeyValueEditor, MonacoEditor
- Features: `views/{module}/`
