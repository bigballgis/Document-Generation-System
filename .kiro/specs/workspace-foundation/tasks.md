# Implementation Plan: 工作台骨架 (Workspace Foundation)

## Overview

Phase 1 实现模板工作台骨架，包含后端新端点与 DTO 扩展、前端类型定义、Pinia store、composable、4 个 Vue 组件（Index.vue、WorkflowStepIndicator.vue、PlaceholderTab.vue、TemplateCreationWizard.vue）、路由变更、导航变更、模板列表页变更和 i18n 支持。任务按依赖顺序编排：后端 → 类型 → API 层 → Store → Composable → 组件 → 路由 → 导航 → 列表页 → i18n → 测试。

## Tasks

- [x] 1. 后端 — 实现 `POST /api/templates/{id}/create-draft-version` 端点与 TemplateDTO 扩展
  - [x] 1.1 扩展 `TemplateDTO.java` 新增 `templateType` 和 `version` 字段
  - [x] 1.2 在 `TemplateService.java` 中新增 `createDraftVersion(Long templateId, Long userId)` 方法
  - [x] 1.3 在 `TemplateController.java` 中新增 `@PostMapping("/{id}/create-draft-version")` 端点
  - [x] 1.4 编写后端单元测试 — `createDraftVersion` 方法

- [x] 2. 前端类型定义与 API 层
  - [x] 2.1 创建 `frontend/src/types/workspace.ts` 类型文件
  - [x] 2.2 在 `frontend/src/api/templates.ts` 中新增 `createDraftVersion` API 函数并扩展 `TemplateDTO` 类型

- [x] 3. Pinia Store — `useTemplateWorkspaceStore`
  - [x] 3.1 创建 `frontend/src/stores/templateWorkspace.ts`

- [x] 4. Composable — `useWorkflowSteps`
  - [x] 4.1 创建 `frontend/src/composables/useWorkflowSteps.ts`

- [x] 5. Checkpoint — 确保后端编译通过、前端类型/store/composable 无 TypeScript 错误

- [x] 6. 前端组件 — 工作台页面与子组件
  - [x] 6.1 创建 `PlaceholderTab.vue` 占位标签页组件
  - [x] 6.2 创建 `WorkflowStepIndicator.vue` 步骤指示器组件
  - [x] 6.3 创建 `TemplateCreationWizard.vue` 模板创建向导组件
  - [x] 6.4 创建 `Index.vue` 工作台主页面

- [x] 7. 路由配置更新
  - [x] 7.1 修改 `frontend/src/router/index.ts`

- [x] 8. 侧边栏导航变更
  - [x] 8.1 修改 `frontend/src/layouts/MainLayout.vue`

- [x] 9. 模板列表页变更
  - [x] 9.1 修改 `frontend/src/views/templates/Index.vue`

- [x] 10. Checkpoint — 确保前端编译通过、路由正确、导航菜单渲染正常

- [x] 11. 国际化 — 添加所有新增 i18n key
  - [x] 11.1 扩展 `frontend/src/i18n/en-US.json`
  - [x] 11.2 扩展 `frontend/src/i18n/zh-CN.json`
  - [x] 11.3 扩展 `frontend/src/i18n/zh-TW.json`

- [x] 12. 前端测试 — Property-Based Tests 与单元测试
  - [x] 12.1 编写 Property Test — 工作流步骤完成计算 (Property 1)
  - [x] 12.2 编写 Property Test — 向导表单验证 (Property 2)
  - [x] 12.3 编写单元测试 — `WorkflowStepIndicator.vue`
  - [x] 12.4 编写单元测试 — `PlaceholderTab.vue`
  - [x] 12.5 编写单元测试 — `TemplateCreationWizard.vue`
  - [x] 12.6 编写单元测试 — `Index.vue` 工作台主页面
  - [x] 12.7 编写集成测试 — 工作台初始化与错误处理

- [x] 13. Final checkpoint — 确保所有测试通过、i18n 完整、路由重定向正确

## Notes

- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- 后端新增一个端点 (`create-draft-version`) 和 TemplateDTO 扩展（`templateType` + `version`），其余均为前端变更
- P1 的 7 个标签页均为 `PlaceholderTab` 占位组件，后续 Phase 逐步替换
