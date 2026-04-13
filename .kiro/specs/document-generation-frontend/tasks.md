# Implementation Plan: Document Generation Frontend

## Overview

本实施计划将文档生成系统前端补全功能拆分为渐进式的编码任务。从 TypeScript 类型定义和 API 调用层开始，逐步构建 composable、页面组件、路由配置和 i18n 翻译。每个任务构建在前一个任务之上，确保增量可验证。所有代码使用 TypeScript + Vue 3 Composition API + Element Plus。

## Tasks

- [x] 1. TypeScript 类型定义
  - [x] 1.1 创建文档生成相关类型文件
    - 创建 `frontend/src/types/document.ts`
    - 定义所有接口和类型: `GenerateDocumentRequest`, `GenerateDocumentResponse`, `SegmentRenderStat`, `BatchGenerateRequest`, `AsyncTaskDTO`, `TaskStatus`, `TaskProgress`, `TaskQuery`, `GeneratedDocumentDTO`, `DocumentQuery`, `MergeDocumentsRequest`, `ExpressionDTO`, `ExpressionType`, `CreateExpressionRequest`, `UpdateExpressionRequest`, `ValidateExpressionRequest`, `ExpressionValidationResult`
    - 字段名与后端 DTO 一一对应（camelCase），时间字段使用 `string`
    - _Requirements: 1.1, 2.1, 3.1, 4.1_

- [x] 2. API 调用层
  - [x] 2.1 创建文档生成 API 文件
    - 创建 `frontend/src/api/generate.ts`
    - 导入 `request` from `@/api/request`，导入类型 from `@/types/document`
    - 实现 `generateDocument(templateId, data?, version?)` → POST `/generate/{templateId}`
    - 实现 `generateDocumentAsync(templateId, data?)` → POST `/generate/{templateId}/async`
    - 实现 `generateDocumentBatch(templateId, data)` → POST `/generate/{templateId}/batch`
    - 遵循 `templates.ts` / `segments.ts` 的编码模式
    - _Requirements: 1.2, 1.3, 1.4, 1.5_

  - [x] 2.2 创建异步任务 API 文件
    - 创建 `frontend/src/api/tasks.ts`
    - 实现 `getTasks(query)` → GET `/tasks`，`page` 转为 zero-based
    - 实现 `getTaskStatus(taskId)` → GET `/tasks/{taskId}`
    - 实现 `getTaskProgress(taskId)` → GET `/tasks/{taskId}/progress`
    - 实现 `downloadTaskResult(taskId)` → GET `/tasks/{taskId}/download` with `responseType: 'blob'`，触发浏览器文件下载
    - _Requirements: 2.3, 2.4, 2.5, 2.6_

  - [x] 2.3 创建文档历史 API 文件
    - 创建 `frontend/src/api/documents.ts`
    - 实现 `getDocuments(query)` → GET `/documents`，`page` 转为 zero-based
    - 实现 `getDocument(id)` → GET `/documents/{id}`
    - 实现 `downloadDocument(id)` → GET `/documents/{id}/download` with `responseType: 'blob'`
    - 实现 `mergeDocuments(data)` → POST `/documents/merge`
    - _Requirements: 3.2, 3.3, 3.4, 3.5_

  - [x] 2.4 创建表达式 API 文件
    - 创建 `frontend/src/api/expressions.ts`
    - 实现 `createExpression(templateId, data)` → POST `/templates/{templateId}/expressions`
    - 实现 `getExpressions(templateId)` → GET `/templates/{templateId}/expressions`
    - 实现 `updateExpression(id, data)` → PUT `/expressions/{id}`
    - 实现 `deleteExpression(id)` → DELETE `/expressions/{id}`
    - 实现 `validateExpression(data)` → POST `/expressions/validate`
    - _Requirements: 4.2, 4.3, 4.4, 4.5, 4.6_


- [x] 3. 后端 — 新增任务列表端点
  - [x] 3.1 在 TaskController 中新增 GET /api/tasks 列表端点
    - 修改 `backend/src/main/java/com/docgen/controller/TaskController.java`
    - 新增 `@GetMapping` 方法，接收可选查询参数: status (`String`)、templateId (`Long`)、`Pageable`
    - 通过 AsyncDocumentService 查询当前租户的异步任务列表，按 createdAt 降序排列
    - 返回 `Page<AsyncTaskDTO>`
    - _Requirements: 2.2_

  - [x] 3.2 在 AsyncDocumentService 中新增列表查询方法
    - 修改 `backend/src/main/java/com/docgen/service/AsyncDocumentService.java`
    - 新增 `listTasks(String status, Long templateId, Pageable pageable)` 方法
    - 从 AsyncTaskRepository 查询，支持按 status 和 templateId 可选过滤
    - _Requirements: 2.2_

- [x] 4. Composable — 异步任务轮询
  - [x] 4.1 创建 useTaskPolling composable
    - 创建 `frontend/src/composables/useTaskPolling.ts`
    - 接收 `taskId: Ref<string | null>` 和 `intervalMs = 3000` 参数
    - 当 `taskId` 变化时自动开始轮询 `getTaskProgress`
    - 当 task status 变为 COMPLETED 或 FAILED 时自动停止轮询
    - 单次轮询失败允许最多 3 次连续失败后停止并设置 error
    - 通过 `onBeforeUnmount` 自动清理 `setInterval`
    - 返回 `{ task, progress, isPolling, error, start, stop }`
    - _Requirements: 6.3, 6.4_

- [x] 5. 检查点 — 确保类型、API 层和后端端点正确
  - 确保所有测试通过，ask the user if questions arise.

- [x] 6. 前端 — 文档生成对话框
  - [x] 6.1 创建 GenerateDialog 组件
    - 创建 `frontend/src/views/templates/components/GenerateDialog.vue`
    - Props: `visible: boolean`, `templateId: number`
    - Emits: `update:visible`, `generated`
    - 实现生成模式选择器（sync / async / batch）
    - 实现 JSON 参数编辑器（textarea）
    - 实现输出格式选择器（Word / PDF / Both）和存储策略选择器（Temporary / Persistent）
    - Batch 模式: 数据集编辑器（多行 JSON，最多 1000 条）、失败策略选择器、失败阈值输入
    - Sync 模式提交调用 `generateDocument`，显示结果和下载链接
    - Async 模式提交调用 `generateDocumentAsync`，显示 taskId 并提供跳转到 `/tasks` 的链接
    - Batch 模式提交调用 `generateDocumentBatch`，跳转到 `/tasks` 页面
    - 错误时使用 `ElMessage.error` 显示错误消息
    - 所有文本使用 `document.*` 和 `common.*` i18n key
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_

  - [x] 6.2 集成 GenerateDialog 到模板详情页
    - 修改 `frontend/src/views/templates/Detail.vue`
    - 在 header-actions 区域新增 "Generate" 按钮
    - 引入 GenerateDialog 组件，通过 `v-model:visible` 控制显隐
    - _Requirements: 5.1_

- [x] 7. 前端 — 表达式管理面板
  - [x] 7.1 创建 ExpressionFormDialog 组件
    - 创建 `frontend/src/views/templates/components/ExpressionFormDialog.vue`
    - Props: `visible: boolean`, `templateId: number`, `data: ExpressionDTO | null`
    - Emits: `update:visible`, `saved`
    - 表单字段: Name（required, max 100）、Type（dropdown: JavaScript / Excel Formula）、Expression Text（textarea）、Description（optional）、Execution Order（number, default 0）
    - 创建模式调用 `createExpression`，编辑模式调用 `updateExpression`
    - 内置 "Validate" 按钮调用 `validateExpression`，显示验证结果（成功指示器或错误消息 + 错误位置）
    - 所有文本使用 `expression.*` 和 `common.*` i18n key
    - _Requirements: 8.3, 8.4, 8.5, 8.7, 8.8_

  - [x] 7.2 创建 ExpressionPanel 组件
    - 创建 `frontend/src/views/templates/components/ExpressionPanel.vue`
    - Props: `templateId: number`
    - 加载并展示表达式列表（Name, Type, Expression Text truncated, Execution Order, Created At, Actions）
    - Actions: Edit（打开 ExpressionFormDialog 编辑模式）、Delete（ElMessageBox.confirm 确认后调用 `deleteExpression`）、Validate（调用 `validateExpression` 显示结果）
    - "Create Expression" 按钮打开 ExpressionFormDialog 创建模式
    - 所有文本使用 `expression.*` 和 `common.*` i18n key
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8_

  - [x] 7.3 集成 ExpressionPanel 到模板详情页
    - 修改 `frontend/src/views/templates/Detail.vue`
    - 在 `<el-tabs>` 中新增 `<el-tab-pane>` 标签页，label 使用 `$t('expression.title')`
    - 引入 ExpressionPanel 组件，传入 `templateId`
    - _Requirements: 8.1_


- [x] 8. 前端 — 异步任务监控页
  - [x] 8.1 创建任务监控页面
    - 创建 `frontend/src/views/tasks/Index.vue`
    - 遵循标准列表页模式（page-header + filter-card + table + pagination）
    - 筛选区域: Status 下拉框（Pending / Running / Completed / Failed）、Template ID 输入框
    - 表格列: Task ID, Task Type, Template ID, Status（el-tag 颜色映射）, Progress（el-progress）, Created At, Completed At, Actions
    - 页面加载时调用 `getTasks` 获取任务列表，筛选变更时重新查询
    - RUNNING 状态的任务使用 `useTaskPolling` composable 实时更新进度条和计数器（completed/total, success/fail）
    - COMPLETED 状态显示 "Download" 按钮，调用 `downloadTaskResult`
    - FAILED 状态在 tooltip 或展开行中显示 errorMessage
    - 所有文本使用 `task.*` 和 `common.*` i18n key
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_

- [x] 9. 前端 — 生成文档历史页
  - [x] 9.1 创建文档历史页面
    - 创建 `frontend/src/views/documents/Index.vue`
    - 遵循标准列表页模式（page-header + filter-card + table + pagination）
    - 筛选区域: Template ID 输入框、Status 下拉框、Date Range 日期范围选择器
    - 表格列: ID, Template ID, Format, Status, File Size, Page Count, Generated At, Expires At, Actions（Download）
    - 筛选变更时调用 `getDocuments` 刷新表格，page 重置为 1
    - Download 按钮调用 `downloadDocument` 触发浏览器下载
    - 支持多选（checkbox），选中后显示 "Merge" 按钮
    - 所有文本使用 `document.*` 和 `common.*` i18n key
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.7_

  - [x] 9.2 创建 MergeDialog 组件
    - 创建 `frontend/src/views/documents/components/MergeDialog.vue`
    - Props: `visible: boolean`, `documentIds: number[]`
    - Emits: `update:visible`, `merged`
    - 表单字段: Insert Page Breaks（switch）、Generate TOC（switch）、Output Format（dropdown: DOCX / PDF）
    - 提交调用 `mergeDocuments`，成功后 emit `merged` 刷新列表
    - 所有文本使用 `document.*` 和 `common.*` i18n key
    - _Requirements: 7.6_

- [x] 10. 前端 — 路由注册与导航菜单
  - [x] 10.1 扩展路由配置
    - 修改 `frontend/src/router/index.ts`
    - 在 main layout children 中新增 `/tasks` 路由 → `views/tasks/Index.vue`，meta: `{ title: 'Tasks', requiresAuth: true }`
    - 在 main layout children 中新增 `/documents` 路由 → `views/documents/Index.vue`，meta: `{ title: 'Documents', requiresAuth: true }`
    - _Requirements: 9.1, 9.2_

  - [x] 10.2 扩展导航菜单
    - 修改 `frontend/src/layouts/MainLayout.vue`
    - 在 Templates 菜单项之后新增 Documents 菜单项（index="/documents"，icon: Files，title: `$t('nav.documents')`）
    - 新增 Tasks 菜单项（index="/tasks"，icon: Clock，title: `$t('nav.tasks')`）
    - 在 `navTitleMap` 中添加 `/tasks: 'nav.tasks'` 和 `/documents: 'nav.documents'` 映射
    - 导入 `Files` 和 `Clock` 图标 from `@element-plus/icons-vue`
    - _Requirements: 9.3, 9.4_

- [x] 11. 前端 — i18n 国际化翻译
  - [x] 11.1 扩展 i18n 翻译文件
    - 修改 `frontend/src/i18n/en-US.json`: 新增 `document.*`（generateMode, modeSync, modeAsync, modeBatch, parameters, parametersHint, dataSets, dataSetsHint, failureStrategy, strategyContinue, strategyThreshold, failureThreshold, history, storageMode, storageTemporary, storagePersistent, generateSuccess, batchProgress, batchComplete, downloadZip, expiresAt, generatedAt, fileSize, merge, mergeOrder, insertPageBreak, generateToc, outputFormat, formatWord, formatPdf, formatBoth）和 `task.*`（title, taskId, status, statusPending, statusRunning, statusCompleted, statusFailed, progress, createdAt, completedAt, errorMessage, downloadResult, viewProgress, pollingActive）key；补全 `expression.executionOrder`, `expression.description`, `expression.confirmDelete`, `expression.errorPosition` key；新增 `nav.tasks`, `nav.documents` key
    - 修改 `frontend/src/i18n/zh-CN.json`: 添加对应的简体中文翻译（已有的 expression.* key 保持不变）
    - 修改 `frontend/src/i18n/zh-TW.json`: 添加对应的繁体中文翻译（已有的 expression.* key 保持不变）
    - 复用已有 common key（`common.confirm`, `common.cancel`, `common.delete`, `common.download` 等）
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

- [x] 12. 检查点 — 确保所有组件和路由正确
  - 确保所有测试通过，ask the user if questions arise.


- [x] 13. 前端测试 — Property-Based Tests 与单元测试
  - [x] 13.1 编写 Property Test — Document query parameter mapping
    - 创建 `frontend/src/__tests__/documents.property.test.ts`
    - **Property 1: Document query parameter mapping preserves all filter fields**
    - 使用 fast-check 生成随机 `DocumentQuery`（optional 字段随机 present/absent）
    - Mock Axios，调用 `getDocuments(query)`，验证传递的 params 与输入一致，`page` 转为 zero-based
    - `fc.assert(fc.property(...), { numRuns: 100 })`
    - // Feature: document-generation-frontend, Property 1: Document query parameter mapping preserves all filter fields
    - **Validates: Requirements 3.2**

  - [x] 13.2 编写 Property Test — Document history filter-to-API mapping
    - 创建 `frontend/src/__tests__/documentHistory.property.test.ts`
    - **Property 2: Document history filter-to-API mapping**
    - 使用 fast-check 生成随机筛选组合（templateId present/absent, status selected/empty, date range set/unset）
    - Mount Document History 组件，触发搜索，验证 `getDocuments` 调用参数与筛选值一致
    - `fc.assert(fc.property(...), { numRuns: 100 })`
    - // Feature: document-generation-frontend, Property 2: Document history filter-to-API mapping
    - **Validates: Requirements 7.4**

  - [x] 13.3 编写 Property Test — Expression validation result rendering
    - 创建 `frontend/src/__tests__/expressionPanel.property.test.ts`
    - **Property 3: Expression validation result rendering**
    - 使用 fast-check 生成随机 `ExpressionValidationResult`（valid=true 无 error，或 valid=false 有 errorMessage 和可选 errorPosition）
    - Mount ExpressionPanel 组件，验证 valid=true 时渲染成功指示器，valid=false 时渲染错误消息和错误位置
    - `fc.assert(fc.property(...), { numRuns: 100 })`
    - // Feature: document-generation-frontend, Property 3: Expression validation result rendering
    - **Validates: Requirements 8.7**

  - [x] 13.4 编写 API 层单元测试
    - 创建 `frontend/src/__tests__/api/generate.test.ts`: 验证 generateDocument, generateDocumentAsync, generateDocumentBatch 发送正确的 HTTP method、URL、payload
    - 创建 `frontend/src/__tests__/api/tasks.test.ts`: 验证 getTasks, getTaskStatus, getTaskProgress, downloadTaskResult
    - 创建 `frontend/src/__tests__/api/documents.test.ts`: 验证 getDocuments, getDocument, downloadDocument, mergeDocuments
    - 创建 `frontend/src/__tests__/api/expressions.test.ts`: 验证 createExpression, getExpressions, updateExpression, deleteExpression, validateExpression
    - _Requirements: 1.2-1.5, 2.2-2.4, 3.2-3.5, 4.2-4.6_

  - [x] 13.5 编写 useTaskPolling composable 单元测试
    - 创建 `frontend/src/__tests__/composables/useTaskPolling.test.ts`
    - 验证轮询启动/停止逻辑
    - 验证 task status 变为 COMPLETED/FAILED 时自动停止
    - 验证连续 3 次失败后停止并设置 error
    - 验证组件卸载时清理 interval
    - _Requirements: 6.3, 6.4_

- [x] 14. 最终检查点 — 确保所有测试通过
  - 确保所有测试通过，ask the user if questions arise.

## Notes

- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties defined in the design document
- Unit tests validate specific examples and edge cases
- 所有前端代码使用 TypeScript + Vue 3 Composition API + Element Plus
- API 文件遵循现有 `templates.ts` / `segments.ts` 的编码模式
- i18n key 遵循 `{module}.{page}.{element}` 命名规范
