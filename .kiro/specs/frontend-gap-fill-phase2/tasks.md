# Implementation Plan: Frontend Gap Fill Phase 2

## Overview

本实施计划将前端补全第二期拆分为渐进式编码任务。从 API 调用层（新增文件 + 扩展现有文件）开始，逐步构建 Vue 组件，集成到现有页面，最后补全 i18n 翻译和单元测试。每个任务构建在前一个任务之上，确保增量可验证。所有代码使用 TypeScript + Vue 3 Composition API + Element Plus，遵循现有编码模式。

## Tasks

- [x] 1. API 调用层 — 新增文件
  - [x] 1.1 创建模板导入导出 API 文件
    - 创建 `frontend/src/api/import-export.ts`
    - 导入 `request` from `@/api/request`，导入 `TemplateDTO` from `@/api/templates`
    - 实现 `importDocx(file: File)` → multipart POST `/templates/import`，返回 `TemplateDTO`
    - 实现 `exportDocx(templateId: number)` → GET `/templates/{templateId}/export` with `responseType: 'blob'`
    - 实现 `exportConfig(templateId: number)` → GET `/templates/{templateId}/export-config` with `responseType: 'blob'`
    - 实现 `importConfig(file: File)` → multipart POST `/templates/import-config`，返回 `TemplateDTO`
    - 遵循 `templates.ts` 的编码模式
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 1.2 创建 Webhook API 文件
    - 创建 `frontend/src/api/webhooks.ts`
    - 定义接口: `WebhookConfigDTO`, `CreateWebhookRequest`, `UpdateWebhookRequest`, `WebhookLogDTO`
    - 实现 `createWebhook(templateId, data)` → POST `/templates/{templateId}/webhooks`
    - 实现 `listWebhooks(templateId)` → GET `/templates/{templateId}/webhooks`
    - 实现 `updateWebhook(webhookId, data)` → PUT `/webhooks/{webhookId}`
    - 实现 `deleteWebhook(webhookId)` → DELETE `/webhooks/{webhookId}`
    - 实现 `getWebhookLogs(webhookId, params)` → GET `/webhooks/{webhookId}/logs`，返回 `PageResult<WebhookLogDTO>`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x] 1.3 创建速率限制 API 文件
    - 创建 `frontend/src/api/rate-limits.ts`
    - 定义接口: `RateLimitStatusDTO`, `UsageStatsDTO`
    - 实现 `getRateLimits()` → GET `/rate-limits`，返回 `RateLimitStatusDTO[]`
    - 实现 `getUsageStats()` → GET `/usage-stats`，返回 `UsageStatsDTO`
    - _Requirements: 5.1, 5.2, 5.3_

- [x] 2. API 调用层 — 扩展现有文件
  - [x] 2.1 扩展 templates.ts — 新增 4 个函数
    - 修改 `frontend/src/api/templates.ts`
    - 新增 `submitReview(templateId: number)` → POST `/templates/{templateId}/submit-review`，返回 `TemplateDTO`
    - 新增 `getAvailableTransitions(templateId: number)` → GET `/templates/{templateId}/available-transitions`，返回 `string[]`
    - 新增 `scanVariables(templateId: number)` → POST `/templates/{templateId}/variables/scan`，返回 `VariableDTO[]`
    - 新增 `exportCoverageReport(templateId: number)` → GET `/templates/{templateId}/coverage/export` with `responseType: 'blob'`
    - _Requirements: 9.1, 9.2, 15.1, 17.1_

  - [x] 2.2 扩展 admin.ts — 新增 4 个函数
    - 修改 `frontend/src/api/admin.ts`
    - 新增 `submitForReview(templateId, data: { reviewerIds: number[]; reviewLevel?: number })` → POST `/templates/{templateId}/reviews`
    - 新增 `getTemplateReviews(templateId, params: { page; size })` → GET `/templates/{templateId}/reviews`，返回 `PageResult<ReviewDTO>`
    - 新增 `conditionalApproveReview(reviewId, data: { comment?; suggestions: string[] })` → PUT `/reviews/{reviewId}/conditional-approve`，返回 `ReviewDTO`
    - 新增 `getReviewEditorUrl(templateId)` → GET `/templates/{templateId}/reviews/editor-url`，返回 `string`
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [x] 2.3 扩展 market.ts — 新增 2 个函数
    - 修改 `frontend/src/api/market.ts`
    - 新增 `exportTestCases(templateId: number)` → GET `/templates/{templateId}/test-cases/export`，返回 `string`
    - 新增 `importTestCases(templateId: number, json: string)` → POST `/templates/{templateId}/test-cases/import`，返回 `TestCaseDTO[]`
    - _Requirements: 13.1, 13.2_

  - [x] 2.4 扩展 auth.ts — 新增 1 个函数
    - 修改 `frontend/src/api/auth.ts`
    - 新增 `resetPassword(email: string)` → POST `/auth/reset-password` with body `{ email }`
    - _Requirements: 11.1_

- [x] 3. 检查点 — 确保 API 层正确
  - 确保所有测试通过，ask the user if questions arise.

- [x] 4. 前端 — Webhook 管理组件
  - [x] 4.1 创建 WebhookFormDialog 组件
    - 创建 `frontend/src/views/templates/components/WebhookFormDialog.vue`
    - Props: `visible: boolean`, `data: WebhookConfigDTO | null`, `templateId: number`
    - Emits: `update:visible`, `saved`
    - 遵循项目 Form Dialog Pattern（`v-model:visible`，computed `isEdit`）
    - 表单字段: URL（required, URL 校验）、Secret（required, type=password）、Payload Template（optional textarea）、Enabled（el-switch, 仅编辑模式）
    - 创建模式调用 `createWebhook`，编辑模式调用 `updateWebhook`
    - 所有文本使用 `webhook.*` 和 `common.*` i18n key
    - _Requirements: 4.3, 4.4, 4.5, 4.8_

  - [x] 4.2 创建 WebhookLogDialog 组件
    - 创建 `frontend/src/views/templates/components/WebhookLogDialog.vue`
    - Props: `visible: boolean`, `webhookId: number`
    - Emits: `update:visible`
    - 分页表格展示日志: Event Type、Payload（truncated, el-popover 展示完整内容）、Response Status（tag 颜色区分 2xx/4xx/5xx）、Response Body（truncated）、Sent At
    - 调用 `getWebhookLogs` 加载数据，支持分页
    - 所有文本使用 `webhook.*` 和 `common.*` i18n key
    - _Requirements: 4.7, 4.8_

  - [x] 4.3 创建 WebhookPanel 组件
    - 创建 `frontend/src/views/templates/components/WebhookPanel.vue`
    - Props: `templateId: number`
    - 加载并展示 Webhook 列表（el-table）: URL、Status（el-tag）、Payload Template（truncated）、Created At、Actions（Edit / Delete / Logs）
    - "Create Webhook" 按钮打开 WebhookFormDialog 创建模式
    - Edit 打开 WebhookFormDialog 编辑模式，Delete 使用 ElMessageBox.confirm 确认后调用 `deleteWebhook`，Logs 打开 WebhookLogDialog
    - 空列表显示 `el-empty`
    - 所有文本使用 `webhook.*` 和 `common.*` i18n key
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8_

- [x] 5. 前端 — 速率限制面板
  - [x] 5.1 创建 RateLimitPanel 组件
    - 创建 `frontend/src/views/admin/RateLimitPanel.vue`
    - 速率限制表格（el-table）: API Key Name、Limit/Second、Remaining/Second、Limit/Minute、Remaining/Minute、Limit/Hour、Remaining/Hour
    - 用量统计卡片（el-descriptions 或 el-statistic）: Monthly Quota、Current Month Usage、Remaining Quota、Reset Date
    - Refresh 按钮重新加载数据
    - 调用 `getRateLimits` 和 `getUsageStats` 加载数据
    - 空列表显示 "No API Keys" 提示
    - 所有文本使用 `admin.rateLimit.*` 和 `common.*` i18n key
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [x] 6. 前端 — 密码重置对话框
  - [x] 6.1 创建 ResetPasswordDialog 组件
    - 创建 `frontend/src/views/auth/ResetPasswordDialog.vue`
    - Props: `visible: boolean`
    - Emits: `update:visible`
    - 表单字段: Email（required, email 校验）
    - Submit 调用 `resetPassword` API，成功后 `ElMessage.success` 提示并关闭对话框
    - 失败时 `ElMessage.error` 显示错误，不关闭对话框
    - 所有文本使用 `auth.*` i18n key
    - _Requirements: 12.2, 12.3, 12.4, 12.5, 12.6_

- [x] 7. 前端 — 集成 Webhook 到模板详情页
  - [x] 7.1 在 Detail.vue 新增 Webhooks 标签页
    - 修改 `frontend/src/views/templates/Detail.vue`
    - 在 `<el-tabs>` 中新增 `<el-tab-pane>` 标签页，label 使用 `$t('webhook.title')`
    - 引入 WebhookPanel 组件，传入 `templateId`
    - _Requirements: 4.1_

- [x] 8. 前端 — 集成审核功能到模板详情页
  - [x] 8.1 在 Detail.vue 新增 Reviews 标签页和审核操作
    - 修改 `frontend/src/views/templates/Detail.vue`
    - 在 `<el-tabs>` 中新增 "Reviews" 标签页，展示模板审核列表（调用 `getTemplateReviews`），表格列: Reviewer、Status、Level、Comment、Suggestions、Created At
    - 新增 "Submit for Review" 对话框（选择 reviewers 和 review level），调用 `submitForReview`
    - 新增 "Conditional Approve" 对话框（comment + suggestions 列表），调用 `conditionalApproveReview`
    - 在 header-actions 新增 "Review in Editor" 按钮，调用 `getReviewEditorUrl` 并在新标签页打开
    - 所有文本使用 `review.*` 和 `common.*` i18n key
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [x] 9. 前端 — 集成导入导出和状态转换到模板详情页
  - [x] 9.1 在 Detail.vue 新增导出按钮和状态转换按钮
    - 修改 `frontend/src/views/templates/Detail.vue`
    - 在 header-actions 新增 "Export as .docx" 按钮（调用 `exportDocx`，触发浏览器下载）
    - 在 header-actions 新增 "Export Configuration" 按钮（调用 `exportConfig`，触发浏览器下载）
    - 页面加载时调用 `getAvailableTransitions`，根据返回值动态显示状态转换按钮（Submit for Review / Activate / Archive），替换现有硬编码的状态判断逻辑
    - 点击状态转换按钮调用对应 API（`submitReview` / `activateTemplate` / `archiveTemplate`），成功后刷新模板数据
    - 无可用转换时隐藏转换按钮区域
    - _Requirements: 2.3, 2.4, 10.1, 10.2, 10.3, 10.4_

  - [x] 9.2 在 Detail.vue 集成变量扫描、覆盖率导出、测试用例导入导出
    - 修改 `frontend/src/views/templates/Detail.vue`
    - 在 Variables 标签页中新增 "Scan Variables" 按钮，调用 `scanVariables`，扫描中显示 loading 状态，完成后刷新变量列表并显示成功消息（含变量数量）
    - 在 Coverage 标签页中新增 "Export Coverage Report" 按钮，调用 `exportCoverageReport` 触发浏览器下载
    - 在 Tests 标签页中新增 "Export Test Cases" 按钮（调用 `exportTestCases`，触发 JSON 下载）和 "Import Test Cases" 按钮（上传 .json 文件，调用 `importTestCases`，刷新列表）
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 16.1, 16.2, 16.3, 18.1, 18.2, 18.3, 18.4, 18.5_

- [x] 10. 前端 — 集成导入按钮到模板列表页
  - [x] 10.1 在 Index.vue 新增导入按钮
    - 修改 `frontend/src/views/templates/Index.vue`
    - 在 page-header 区域新增 "Import Template" 按钮（上传 .docx，调用 `importDocx`，成功后刷新列表）
    - 新增 "Import Configuration" 按钮（上传 .json，调用 `importConfig`，成功后刷新列表）
    - 使用隐藏 `<input type="file">` 或 `el-upload` 的 `before-upload` 实现文件选择
    - 文件类型校验: .docx 和 .json
    - 错误时 `ElMessage.error` 显示错误消息
    - _Requirements: 2.1, 2.2, 2.5, 2.6_

- [x] 11. 前端 — 集成密码重置到登录页
  - [x] 11.1 在 LoginView.vue 新增 "Forgot Password?" 链接
    - 修改 `frontend/src/views/auth/LoginView.vue`
    - 在 `form-footer` 区域新增 "Forgot Password?" 链接（使用 `$t('auth.forgotPassword')` i18n key）
    - 点击打开 ResetPasswordDialog
    - 引入 ResetPasswordDialog 组件
    - _Requirements: 12.1, 12.2_

- [x] 12. 前端 — 集成速率限制到管理页
  - [x] 12.1 在 admin/Index.vue 新增 Rate Limits 标签页
    - 修改 `frontend/src/views/admin/Index.vue`
    - 在 `<el-tabs>` 中新增 `<el-tab-pane>` 标签页，label 使用 `$t('admin.rateLimits')`
    - 引入 RateLimitPanel 组件
    - _Requirements: 6.1_

- [x] 13. 检查点 — 确保所有组件和页面集成正确
  - 确保所有测试通过，ask the user if questions arise.

- [x] 14. 前端 — i18n 国际化翻译
  - [x] 14.1 扩展 i18n 翻译文件
    - 修改 `frontend/src/i18n/en-US.json`: 新增 `auth.forgotPassword`、`template.importTemplate`、`template.importConfiguration`、`template.scanVariables`、`template.scanVariablesSuccess`、`template.availableTransitions`、`webhook.payloadTemplate`、`webhook.confirmDelete`、`webhook.eventType`、`webhook.responseStatus`、`webhook.responseBody`、`webhook.sentAt`、`webhook.noLogs`、`admin.rateLimit.apiKeyName`、`admin.rateLimit.limitPerSecond`、`admin.rateLimit.remainingPerSecond`、`admin.rateLimit.limitPerMinute`、`admin.rateLimit.remainingPerMinute`、`admin.rateLimit.limitPerHour`、`admin.rateLimit.remainingPerHour`、`admin.rateLimit.monthlyQuota`、`admin.rateLimit.currentMonthUsage`、`admin.rateLimit.remainingQuota`、`review.reviewInEditor`、`review.selectReviewers`、`review.conditionalApproveDialog` 等 key
    - 修改 `frontend/src/i18n/zh-CN.json`: 添加对应的简体中文翻译
    - 修改 `frontend/src/i18n/zh-TW.json`: 添加对应的繁体中文翻译
    - 复用已有 common key（`common.confirm`, `common.cancel`, `common.delete`, `common.export`, `common.import` 等）
    - 确保三个语言文件的新增 key 集合一致
    - _Requirements: 19.1, 19.2, 19.3, 19.4_

- [x] 15. 检查点 — 确保 i18n 完整性
  - 确保所有测试通过，ask the user if questions arise.

- [x] 16. 前端测试 — API 层单元测试
  - [x] 16.1 编写 import-export API 单元测试
    - 创建 `frontend/src/__tests__/api/import-export.test.ts`
    - 验证 `importDocx` 发送 multipart POST 到正确 URL，Content-Type 为 `multipart/form-data`
    - 验证 `exportDocx` 发送 GET 到正确 URL，`responseType` 为 `blob`
    - 验证 `exportConfig` 发送 GET 到正确 URL，`responseType` 为 `blob`
    - 验证 `importConfig` 发送 multipart POST 到正确 URL
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [x] 16.2 编写 webhooks API 单元测试
    - 创建 `frontend/src/__tests__/api/webhooks.test.ts`
    - 验证 `createWebhook` 发送 POST 到 `/templates/{templateId}/webhooks`
    - 验证 `listWebhooks` 发送 GET 到 `/templates/{templateId}/webhooks`
    - 验证 `updateWebhook` 发送 PUT 到 `/webhooks/{webhookId}`
    - 验证 `deleteWebhook` 发送 DELETE 到 `/webhooks/{webhookId}`
    - 验证 `getWebhookLogs` 发送 GET 到 `/webhooks/{webhookId}/logs` 并传递分页参数
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x] 16.3 编写 rate-limits API 单元测试
    - 创建 `frontend/src/__tests__/api/rate-limits.test.ts`
    - 验证 `getRateLimits` 发送 GET 到 `/rate-limits`
    - 验证 `getUsageStats` 发送 GET 到 `/usage-stats`
    - _Requirements: 5.2, 5.3_

  - [x] 16.4 编写 templates.ts 扩展函数单元测试
    - 创建 `frontend/src/__tests__/api/templates-extensions.test.ts`
    - 验证 `submitReview` 发送 POST 到 `/templates/{templateId}/submit-review`
    - 验证 `getAvailableTransitions` 发送 GET 到 `/templates/{templateId}/available-transitions`
    - 验证 `scanVariables` 发送 POST 到 `/templates/{templateId}/variables/scan`
    - 验证 `exportCoverageReport` 发送 GET 到正确 URL，`responseType` 为 `blob`
    - _Requirements: 9.1, 9.2, 15.1, 17.1_

  - [x] 16.5 编写 admin.ts 扩展函数单元测试
    - 创建 `frontend/src/__tests__/api/admin-extensions.test.ts`
    - 验证 `submitForReview` 发送 POST 到 `/templates/{templateId}/reviews`
    - 验证 `getTemplateReviews` 发送 GET 到 `/templates/{templateId}/reviews` 并传递分页参数
    - 验证 `conditionalApproveReview` 发送 PUT 到 `/reviews/{reviewId}/conditional-approve`
    - 验证 `getReviewEditorUrl` 发送 GET 到 `/templates/{templateId}/reviews/editor-url`
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [x] 16.6 编写 market.ts 和 auth.ts 扩展函数单元测试
    - 创建 `frontend/src/__tests__/api/market-extensions.test.ts`
    - 验证 `exportTestCases` 发送 GET 到 `/templates/{templateId}/test-cases/export`
    - 验证 `importTestCases` 发送 POST 到 `/templates/{templateId}/test-cases/import`
    - 创建 `frontend/src/__tests__/api/auth-extensions.test.ts`
    - 验证 `resetPassword` 发送 POST 到 `/auth/reset-password` with body `{ email }`
    - _Requirements: 13.1, 13.2, 11.1_

- [x] 17. 前端测试 — 组件单元测试
  - [x] 17.1 编写 WebhookPanel 组件测试
    - 创建 `frontend/src/__tests__/views/templates/WebhookPanel.test.ts`
    - 验证组件正确渲染表格列（URL、Status、Payload Template、Created At、Actions）
    - 验证 "Create Webhook" 按钮打开 WebhookFormDialog
    - 验证 Delete 操作触发确认对话框并调用 `deleteWebhook`
    - 验证空列表显示 `el-empty`
    - _Requirements: 4.1, 4.2, 4.6_

  - [x] 17.2 编写 WebhookFormDialog 组件测试
    - 创建 `frontend/src/__tests__/views/templates/WebhookFormDialog.test.ts`
    - 验证创建模式渲染空表单，编辑模式预填数据
    - 验证表单校验（URL required, Secret required）
    - 验证提交调用正确的 API（create vs update）
    - _Requirements: 4.3, 4.4, 4.5_

  - [x] 17.3 编写 WebhookLogDialog 组件测试
    - 创建 `frontend/src/__tests__/views/templates/WebhookLogDialog.test.ts`
    - 验证组件正确渲染日志表格列
    - 验证分页功能
    - _Requirements: 4.7_

  - [x] 17.4 编写 RateLimitPanel 组件测试
    - 创建 `frontend/src/__tests__/views/admin/RateLimitPanel.test.ts`
    - 验证速率限制表格正确渲染
    - 验证用量统计卡片正确渲染
    - 验证 Refresh 按钮重新加载数据
    - _Requirements: 6.2, 6.3, 6.4_

  - [x] 17.5 编写 ResetPasswordDialog 组件测试
    - 创建 `frontend/src/__tests__/views/auth/ResetPasswordDialog.test.ts`
    - 验证 Email 表单字段和校验
    - 验证提交成功后显示成功消息并关闭对话框
    - 验证提交失败后显示错误消息且不关闭对话框
    - _Requirements: 12.3, 12.4, 12.5_

- [x] 18. 最终检查点 — 确保所有测试通过
  - 确保所有测试通过，ask the user if questions arise.

## Notes

- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- 本功能不使用 Property-Based Testing（设计文档已说明原因：API 薄封装 + UI 组件，example-based 测试更合适）
- 所有前端代码使用 TypeScript + Vue 3 Composition API + Element Plus
- API 文件遵循现有 `templates.ts` / `admin.ts` 的编码模式
- i18n key 遵循 `{module}.{element}` 命名规范，复用已有 common key
