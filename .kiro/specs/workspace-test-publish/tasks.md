# Implementation Plan: 工作台测试与审核发布标签页 (Workspace Testing & Review-Publish)

## Overview

Phase 3 将 P1 骨架中的 2 个占位标签页替换为真实实现，按依赖顺序编排：后端变更（AutoActivationService + TemplateReviewService 集成）→ Store 扩展 → 前端组件（TestCaseFormDialog → TestingTab → SubmitReviewDialog → ApiEndpointInfo → ReviewPublishTab）→ Index.vue 集成 → i18n → Property-Based Tests → 单元测试。后端核心变更为新增 AutoActivationService 实现审核通过后自动激活 + API Key 生成，以及修改 TemplateReviewService 集成调用。前端核心工作为 TestingTab 和 ReviewPublishTab 两个标签页组件及其子组件的实现。

## Tasks

- [x] 1. 后端 — 新增 AutoActivationService + 修改 TemplateReviewService
  - [x] 1.1 创建 `AutoActivationService.java`
    - 创建文件: `backend/src/main/java/com/docgen/service/AutoActivationService.java`
    - 使用 `@Service` 注解，构造器注入 `TemplateStateMachineService`、`ApiKeyService`、`ApiKeyRepository`、`TemplateRepository`
    - 实现 `tryAutoActivate(Long templateId)` 方法（`@Transactional`）：
      - Step 1: 调用 `stateMachineService.transition(templateId, TemplateState.REVIEWED)` 将 PENDING_REVIEW → REVIEWED；失败时 WARN 日志并 return，不抛异常
      - Step 2: 调用 `stateMachineService.transition(templateId, TemplateState.ACTIVE)` 将 REVIEWED → ACTIVE；失败时 WARN 日志并 return，不抛异常
      - Step 3: 查询 `apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)` 检查是否存在 active（enabled 且未过期）API Key；若无则调用 `apiKeyService.createApiKey()` 创建 name 为 `auto-{sanitizedName}-{timestamp}` 的 key；失败时 WARN 日志，不抛异常
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_
  - [x] 1.2 修改 `TemplateReviewService.checkAndTransitionTemplate` 集成 AutoActivationService
    - 修改文件: `backend/src/main/java/com/docgen/service/TemplateReviewService.java`
    - 新增构造器参数 `AutoActivationService autoActivationService`
    - 在 `checkAndTransitionTemplate` 方法的 `!nextLevelPending` 分支中，将 `stateMachineService.transition(templateId, TemplateState.REVIEWED)` 替换为 `autoActivationService.tryAutoActivate(templateId)`
    - _Requirements: 7.7_
  - [x] 1.3 编写后端单元测试 — AutoActivationService
    - 创建文件: `backend/src/test/java/com/docgen/service/AutoActivationServiceTest.java`
    - 测试 tryAutoActivate 成功两步转换 + 无 active key → 创建 key
    - 测试 tryAutoActivate 成功两步转换 + 已有 active key → 不创建 key
    - 测试 Step 1 失败 → WARN 日志，不抛异常，不调用 Step 2
    - 测试 Step 2 失败 → WARN 日志，不抛异常，模板保持 REVIEWED
    - 测试 API Key 创建失败 → WARN 日志，不抛异常，模板保持 ACTIVE
    - 测试 API Key name 格式 `auto-{sanitizedName}-{timestamp}`
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_
  - [x] 1.4 编写后端单元测试 — TemplateReviewService.checkAndTransitionTemplate 集成
    - 扩展文件: `backend/src/test/java/com/docgen/service/TemplateReviewServiceTest.java`
    - 测试所有审核通过 + 无下一级 → 调用 `autoActivationService.tryAutoActivate`
    - 测试部分审核未完成 → 不调用 autoActivationService
    - 测试存在下一级审核 → 不调用 autoActivationService
    - _Requirements: 7.7_

- [x] 2. Checkpoint — 确保后端编译通过、单元测试通过
  - 运行 `mvn compile` 确认无编译错误
  - 运行后端单元测试确认通过
  - 确认 AutoActivationService 已创建且 TemplateReviewService 已修改

- [x] 3. Store 扩展 — 新增 testCases / testReport / reviews 状态与 actions
  - [x] 3.1 修改 `frontend/src/stores/templateWorkspace.ts`
    - 新增 import: `getTestCases` from `@/api/market`（或对应 API 文件），`getTemplateReviews` from `@/api/admin`
    - 新增 import 类型: `TestCaseDTO`、`CompositeTestReportDTO`（复用已有类型定义）、`TemplateReviewDTO`
    - 新增 state: `const testCases = ref<TestCaseDTO[]>([])`、`const testReport = ref<CompositeTestReportDTO | null>(null)`、`const reviews = ref<TemplateReviewDTO[]>([])`
    - 注意：testCases 和 reviews 不在 `initWorkspace` 中加载（按需加载，避免无效 API 调用）
    - 新增 `refreshTestCases()` action：调用 `getTestCases(templateId.value)`，成功时更新 `testCases.value` 并删除 `warnings.value.testCases`，失败时设置 `warnings.value.testCases`
    - 新增 `refreshReviews()` action：调用 `getTemplateReviews(templateId.value, { page: 0, size: 20 })`，成功时更新 `reviews.value`（取 `result.content`）并删除 `warnings.value.reviews`，失败时设置 `warnings.value.reviews`
    - 在 `$reset` 中新增 `testCases.value = []`、`testReport.value = null`、`reviews.value = []`
    - 在 return 中导出 `testCases, testReport, reviews, refreshTestCases, refreshReviews`
    - _Requirements: 8.4, 8.5, 8.6, 8.7_

- [x] 4. 前端组件 — TestCaseFormDialog.vue
  - [x] 4.1 创建 `frontend/src/views/template-workspace/components/TestCaseFormDialog.vue`
    - Props: `visible: boolean`、`templateId: number`、`testCase: TestCaseDTO | null`（null = 新建模式）
    - Emits: `update:visible`、`saved`
    - 表单字段: name（required, max 100）、testDataJson（required, JSON 编辑器组件 + 语法校验）、expectedResultJson（optional, JSON 编辑器）、comparisonType（select: VARIABLE_VALUES / TEXT_CONTENT / FILE_SNAPSHOT，默认 VARIABLE_VALUES）
    - watch `visible`：打开时根据 `testCase` prop 决定新建（重置表单）或编辑（填充表单）
    - JSON 校验函数 `validateJson`：`JSON.parse(value)` 失败时返回错误
    - 提交逻辑：新建调用 `createTestCase(templateId, data)`，编辑调用 `updateTestCase(testCase.id, data)`；成功 emit `saved`；失败 `ElMessage.error`
    - _Requirements: 1.4, 1.5, 1.6_

- [x] 5. 前端组件 — TestingTab.vue
  - [x] 5.1 创建 `frontend/src/views/template-workspace/components/TestingTab.vue`
    - 从 store 读取数据，无 props
    - 三个区域垂直排列，`el-divider` 分隔：测试数据 → 测试执行 → 变量覆盖率
    - **测试数据区域**：
      - section header（标题 + "Add Test Case" / "Export" / "Import" 按钮）
      - `el-table` 展示 testCases（columns: name, comparisonType el-tag, testDataJson truncated 80 chars + el-tooltip, updatedAt, actions: Edit/Run/Delete）
      - 空状态 `el-empty`（workspace.testing.emptyTestData）
      - 添加/编辑 → 打开 TestCaseFormDialog；saved 事件 → `store.refreshTestCases()`
      - 删除 → ElMessageBox.confirm → 禁用按钮（deletingIds Set）→ `deleteTestCase(id)` → `store.refreshTestCases()`；失败 → ElMessage.error + 重新启用
      - 导出 → `exportTestCases(templateId)` → Blob 下载 `test-cases-{id}.json`
      - 导入 → el-upload accept=".json" → 读取文件 → `importTestCases(templateId, text)` → `store.refreshTestCases()` → ElMessage.success 显示导入数量
    - **测试执行区域**：
      - "Run All Tests" 按钮 → `runAllCompositeTests(templateId)` → `store.testReport = report` → 渲染报告（total/passed/failed/executedAt + per-segment results el-table）
      - 运行中 loading 状态 + 按钮禁用
      - 单个 Run → `runTestCase(testCaseId)` → ElMessage.success/error
      - "Quick Test" el-dropdown → 选择测试用例 → `previewCompositeTemplate(templateId)` with testDataJson → 文件下载
      - "Generate Test Document" el-dropdown → 选择测试用例 → `previewCompositeTemplate(templateId)` → window.open(previewUrl)
    - **变量覆盖率区域**：
      - 从 `store.coverage`（CompositeCoverageReport）读取整体覆盖率 + per-segment 分解
      - 按需加载 `templateCoverage`（CoverageReport）获取 unboundTagNames 和 unusedDataSourceFields
      - 覆盖率百分比 + el-progress（green ≥100%, orange ≥50%, red <50%）
      - bound/total 变量计数
      - 覆盖率 <100% 且 totalVariables >0 → el-alert warning + unboundTagNames 红色 el-tag
      - totalVariables === 0 → el-empty（workspace.testing.noVariables）
      - per-segment el-table（segmentName, totalVariables, boundVariables, coveragePercent el-progress, status icon）
      - unusedDataSourceFields 可折叠区域（el-collapse）灰色 el-tag
      - "Refresh Coverage" 按钮 → `store.refreshCoverage()` + 重新加载 templateCoverage
    - 首次加载：onMounted → `loadTestCasesIfNeeded()`（检查 testCasesLoaded 标志避免重复加载）
    - _Requirements: 1.1, 1.2, 1.3, 1.7, 1.8, 1.9, 1.10, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 6. Checkpoint — 确保 TestingTab 和 TestCaseFormDialog 无 TypeScript 错误
  - 运行 TypeScript 类型检查确认无错误
  - 确认 TestingTab.vue 和 TestCaseFormDialog.vue 文件创建完成
  - 确认 store 扩展无类型错误

- [x] 7. 前端组件 — SubmitReviewDialog.vue
  - [x] 7.1 创建 `frontend/src/views/template-workspace/components/SubmitReviewDialog.vue`
    - Props: `visible: boolean`
    - Emits: `update:visible`、`submit: [reviewerIds: number[], reviewLevel: number]`
    - 表单字段: reviewerIds（required, el-select filterable multiple，打开时调用 `GET /api/users` 加载用户列表）、reviewLevel（select: 1=初审 / 2=终审，默认 1）
    - watch `visible`：打开时加载用户列表（仅首次）
    - 提交校验：未选择审核人 → ElMessage.warning
    - 提交 → emit `submit` 事件
    - _Requirements: 4.3_

- [x] 8. 前端组件 — ApiEndpointInfo.vue
  - [x] 8.1 创建 `frontend/src/views/template-workspace/components/ApiEndpointInfo.vue`
    - Props: `templateId: number`、`apiKeys: ApiKeyDTO[]`、`curlExample: string`、`apiKeysLoading: boolean`、`creatingApiKey: boolean`
    - Emits: `copy: [text: string]`、`create-api-key`
    - el-card 展示：
      - API URL: `POST {origin}/api/generate/{templateId}` + Copy 按钮
      - API Key: 第一个 active key 的 keyPrefix + Copy 按钮；无 key → el-alert warning + "Create API Key" 按钮
      - cURL 示例: `<pre>` 代码块 + Copy 按钮
      - "Generate Document" 按钮
      - API 版本固定说明 el-alert（workspace.reviewPublish.apiNote）
    - _Requirements: 5.1, 5.2, 5.4_

- [x] 9. 前端组件 — ReviewPublishTab.vue
  - [x] 9.1 创建 `frontend/src/views/template-workspace/components/ReviewPublishTab.vue`
    - 从 store 读取数据，无 props
    - **状态徽章区域**：
      - el-tag 显示当前模板状态（DRAFT=info, PENDING_REVIEW=warning, REVIEWED=primary, ACTIVE=success, ARCHIVED=danger）
    - **提交审核**：
      - DRAFT 或 REVIEWED 状态 → 显示 "Submit for Review" 按钮
      - 点击 → 打开 SubmitReviewDialog
      - 提交流程：`POST /api/templates/{id}/reviews` → `POST /api/templates/{id}/submit-review`（状态转换）→ `store.refreshTemplate()` + `store.refreshReviews()` → ElMessage.success
      - 失败 → ElMessage.error + 保持对话框打开
    - **审核状态表格**：
      - PENDING_REVIEW / REVIEWED / ACTIVE 状态 → 显示审核表格
      - el-table columns: reviewerName（从 userMap 解析）、status（colored el-tag）、reviewLevel、comment、suggestions、createdAt、completedAt
      - el-pagination（page size 10）
    - **审核结果指示器**：
      - 所有审核通过 → el-alert success "Ready to Publish"
      - 有审核被拒 → el-alert error + 拒绝原因 + "Revise & Resubmit" 按钮
      - 自动激活失败（REVIEWED + 所有通过）→ el-alert error 提示 + 手动激活按钮
    - **手动激活**：
      - REVIEWED 状态 → 显示 "Activate" 按钮
      - 点击 → 检查 store.coverage 覆盖率 <100% → ElMessageBox.confirm 警告 → 确认后 `POST /api/templates/{id}/activate` → `store.refreshTemplate()` + `store.refreshTransitions()` → ElMessage.success → 加载 API Keys
      - 失败 → ElMessage.error
    - **API 端点信息**：
      - ACTIVE 状态 → 显示 ApiEndpointInfo 组件
      - 加载 API Keys: `GET /api/api-keys?page=0&size=10`
      - 无 API Key → 显示 Create API Key 按钮 → `createApiKey({ name: 'manual-{name}-{timestamp}' })`
      - cURL 示例 computed
      - Copy 功能 → `navigator.clipboard.writeText`
    - 首次加载：onMounted → `loadReviewsIfNeeded()`（检查 reviewsLoaded 标志）+ ACTIVE 状态时加载 API Keys
    - _Requirements: 4.1, 4.2, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 6.1, 6.2, 6.3, 6.4_

- [x] 10. Checkpoint — 确保 ReviewPublishTab 及子组件无 TypeScript 错误
  - 运行 TypeScript 类型检查确认无错误
  - 确认 SubmitReviewDialog.vue、ApiEndpointInfo.vue、ReviewPublishTab.vue 文件创建完成
  - 确认所有组件间 props/emits 类型匹配

- [x] 11. Index.vue 集成 — 替换 2 个占位标签页
  - [x] 11.1 修改 `frontend/src/views/template-workspace/Index.vue`
    - 新增 import: `TestingTab` from `./components/TestingTab.vue`、`ReviewPublishTab` from `./components/ReviewPublishTab.vue`
    - 将 `placeholderTabs` 数组中 `testing` 和 `reviewPublish` 两项移除，仅保留 `exportImport` 和 `settings` 两个 P4 占位
    - 在 `<el-tabs>` 中 P2 真实标签页之后、P4 占位之前，新增两个 `<el-tab-pane>`：
      - `<el-tab-pane :label="$t('workspace.tabTesting')" name="testing"><TestingTab /></el-tab-pane>`
      - `<el-tab-pane :label="$t('workspace.tabReviewPublish')" name="reviewPublish"><ReviewPublishTab /></el-tab-pane>`
    - _Requirements: 8.1, 8.2, 8.3, 8.8_

- [x] 12. 国际化 — 添加 P3 新增 i18n key
  - [x] 12.1 扩展 `frontend/src/i18n/en-US.json`
    - 添加 `workspace.testing.*` 前缀 key（testData, addTestCase, emptyTestData, export, import, importSuccess, deleteConfirm, nameRequired, nameMaxLength, testDataRequired, invalidJson, testExecution, runAll, quickTest, generateTestDoc, total, passed, failed, executedAt, testPassed, testFailed, runAllFailed, runFailed, quickTestFailed, generateFailed, variableCoverage, refreshCoverage, noVariables, variablesBound, coverageWarning, unusedFields）
    - 添加 `workspace.reviewPublish.*` 前缀 key（submitForReview, submitSuccess, submitFailed, selectReviewers, reviewLevel, initialReview, finalReview, readyToPublish, reviewRejected, reviseResubmit, activate, activateSuccess, activateFailed, autoActivationFailed, lowCoverageWarning, apiEndpoint, noApiKey, createApiKey, apiKeyCreated, apiKeyFailed, apiNote, generateDocument, status.draft, status.pendingReview, status.reviewed, status.active, status.archived）
    - _Requirements: 9.1, 9.3, 9.4, 9.5, 9.6_
  - [x] 12.2 扩展 `frontend/src/i18n/zh-CN.json`
    - 添加与 en-US 对应的所有中文简体翻译
    - _Requirements: 9.2_
  - [x] 12.3 扩展 `frontend/src/i18n/zh-TW.json`
    - 添加与 en-US 对应的所有中文繁体翻译
    - _Requirements: 9.2_

- [x] 13. Checkpoint — 确保前端编译通过、i18n 完整
  - 运行 TypeScript 类型检查确认无错误
  - 确认 en-US、zh-CN、zh-TW 三个语言文件中所有 P3 新增 key 完整
  - 确认 TestingTab 和 ReviewPublishTab 已替换占位组件
  - 确认 store 扩展（testCases, testReport, reviews）已正确导出

- [x] 14. 前端测试 — Property-Based Tests
  - [x] 14.1 编写 Property Test — Coverage display state consistency (Property 1)
    - 创建测试文件: `frontend/src/__tests__/workspace-test-publish-property.test.ts`
    - **Property 1: Coverage display state consistency**
    - 使用 fast-check 生成随机 `CompositeCoverageReport`（segmentCoverages 长度 0-20，每个 entry 的 totalVariables 0-100，boundVariables 0 到 totalVariables，overallCoveragePercent 0-100）
    - 断言：totalVariables = sum(segment.totalVariables)；boundVariables = sum(segment.boundVariables)；warning 可见性 = (pct < 100 && total > 0)；empty state 可见性 = (total === 0)；color = green(≥100)/orange(≥50)/red(<50)
    - **Validates: Requirements 3.1, 3.3, 3.5**
  - [x] 14.2 编写 Property Test — Review status indicator correctness (Property 2)
    - **Property 2: Review status indicator correctness**
    - 使用 fast-check 生成随机 `TemplateReviewDTO[]`（长度 1-20），每个 review 的 status 从 [PENDING, APPROVED, CONDITIONAL_APPROVED, REJECTED] 随机选择
    - 断言：allReviewsApproved = every(status in [APPROVED, CONDITIONAL_APPROVED])；hasRejectedReview = some(status === REJECTED)；互斥性：allReviewsApproved 和 hasRejectedReview 不同时为 true
    - **Validates: Requirements 4.8, 4.9**
  - [x] 14.3 编写后端 Property Test — AutoActivationService two-step transition (Property 3)
    - 创建测试文件: `backend/src/test/java/com/docgen/property/AutoActivationPropertyTest.java`
    - **Property 3: AutoActivationService two-step transition**
    - 使用 jqwik 生成随机 templateId，mock TemplateStateMachineService（可配置成功/失败），mock TemplateRepository
    - 断言：成功时模板状态为 ACTIVE；Step 1 失败时不调用 Step 2；Step 2 失败时模板保持 REVIEWED；所有情况均不抛异常
    - **Validates: Requirements 7.2, 7.4, 7.5**
  - [x] 14.4 编写后端 Property Test — AutoActivationService API Key auto-creation (Property 4)
    - **Property 4: AutoActivationService API Key auto-creation**
    - 使用 jqwik 生成随机 templateId、tenantId、existingKeys 列表（0-5 个，enabled/disabled/expired 随机），mock ApiKeyRepository 和 ApiKeyService
    - 断言：无 active key 时 createApiKey 被调用一次且 name 匹配 `auto-*-*` 格式；有 active key 时 createApiKey 不被调用；创建失败时不抛异常
    - **Validates: Requirements 7.3, 7.6**
  - [x] 14.5 编写 Property Test — Workflow Step 5 completion calculation (Property 5)
    - **Property 5: Workflow Step 5 completion calculation**
    - 使用 fast-check 生成随机 testReport（null 或 CompositeTestReportDTO）和随机 coverage（null 或 CompositeCoverageReport，overallCoveragePercent 0-100，segmentCoverages 长度 0-10）
    - 断言：Step 5 completed = (coverage != null && coverage.overallCoveragePercent >= 100 && coverage.segmentCoverages.length > 0)
    - **Validates: Requirements 8.8**

- [x] 15. 前端测试 — 单元测试
  - [x] 15.1 编写单元测试 — TestingTab.vue
    - 创建测试文件: `frontend/src/__tests__/TestingTab.test.ts`
    - 测试：三个区域渲染 + 分隔线、首次挂载调用 store.refreshTestCases()、测试用例表格列渲染（name, comparisonType tag, testDataJson truncated+tooltip, updatedAt, actions）、空状态卡片、添加/编辑对话框打开、saved 事件刷新 store、删除确认流程、删除失败恢复、导出触发文件下载、导入流程 + ElMessage.success、Run All Tests → 报告渲染、Run All 失败 → ElMessage.error + 按钮重新启用、单个 Run → ElMessage.success/error、Quick Test 下拉 + preview、Generate Test Document 下拉 + preview、覆盖率 100% → 绿色无警告、覆盖率 50% → 橙色 + 警告 + unbound tags、0 变量 → noVariables 提示、Refresh Coverage 调用
    - _Requirements: 1.1–1.10, 2.1–2.8, 3.1–3.6_
  - [x] 15.2 编写单元测试 — TestCaseFormDialog.vue
    - 创建测试文件: `frontend/src/__tests__/TestCaseFormDialog.test.ts`
    - 测试：新建模式空表单、编辑模式预填充、JSON 校验无效 → 错误提示、提交新建调用 createTestCase、提交编辑调用 updateTestCase、提交失败 → ElMessage.error
    - _Requirements: 1.4, 1.5, 1.6_
  - [x] 15.3 编写单元测试 — ReviewPublishTab.vue
    - 创建测试文件: `frontend/src/__tests__/ReviewPublishTab.test.ts`
    - 测试：状态徽章渲染（5 种状态对应 5 种 tag type）、DRAFT → Submit for Review 按钮、PENDING_REVIEW → 审核表格、REVIEWED → Activate 按钮、ACTIVE → API Endpoint Info、所有审核通过 → Ready to Publish、有审核被拒 → Review Rejected + Revise & Resubmit、自动激活失败提示、提交审核流程（对话框 → API → store 刷新）、提交失败 → 对话框保持打开、手动激活 100% 覆盖率 → 直接激活、手动激活 <100% → 警告对话框、激活失败 → ElMessage.error、API Key 存在 → 显示 prefix + cURL、API Key 不存在 → 警告 + Create 按钮、审核表格分页
    - _Requirements: 4.1–4.10, 5.1–5.6, 6.1–6.4_
  - [x] 15.4 编写单元测试 — ApiEndpointInfo.vue
    - 创建测试文件: `frontend/src/__tests__/ApiEndpointInfo.test.ts`
    - 测试：API URL 渲染 + Copy 按钮、API Key prefix 渲染 + Copy 按钮、无 API Key → 警告 + Create 按钮、cURL 示例渲染 + Copy 按钮、API 版本固定说明文本
    - _Requirements: 5.1, 5.2, 5.4_
  - [x] 15.5 编写单元测试 — SubmitReviewDialog.vue
    - 创建测试文件: `frontend/src/__tests__/SubmitReviewDialog.test.ts`
    - 测试：打开时加载用户列表、审核人多选、审核级别选择、未选择审核人 → 警告、提交 → emit submit 事件
    - _Requirements: 4.3_
  - [x] 15.6 编写单元测试 — Index.vue 集成（P3 部分）
    - 扩展测试文件: `frontend/src/__tests__/views/TemplateWorkspaceIndex.test.ts`
    - 测试：testing 标签页渲染 TestingTab（非 PlaceholderTab）、reviewPublish 标签页渲染 ReviewPublishTab（非 PlaceholderTab）、剩余 2 个标签页仍为 PlaceholderTab
    - _Requirements: 8.1, 8.2, 8.3_

- [x] 16. Final checkpoint — 确保所有测试通过、i18n 完整、组件集成正确
  - 运行前端测试 `npx vitest --run` 确认所有测试通过
  - 运行后端测试确认通过
  - 确认 en-US、zh-CN、zh-TW 三个语言文件中所有 P3 新增 key 完整
  - 确认 TestingTab 和 ReviewPublishTab 已替换占位组件
  - 确认 store 扩展（testCases, testReport, reviews, refreshTestCases, refreshReviews）已集成
  - 确认 AutoActivationService 已创建且 TemplateReviewService 已集成调用
  - 确认 WorkflowStepIndicator 能正确反映 Step 5 完成状态

## Notes

- 所有任务均为必需任务，无可选标记
- 后端变更范围：新增 AutoActivationService + 修改 TemplateReviewService（无数据库迁移）
- 前端核心工作：TestingTab（含 TestCaseFormDialog）+ ReviewPublishTab（含 SubmitReviewDialog + ApiEndpointInfo）
- testCases 和 reviews 按需加载（不在 initWorkspace 中），testReport 存储在 store 供 WorkflowStepIndicator 使用
- Property tests 覆盖设计文档中定义的 5 个正确性属性（3 个前端 fast-check + 2 个后端 jqwik）
- 每个任务引用具体需求编号以确保可追溯性
- 任务顺序遵循 P2 模式：后端 → Store → 前端组件 → Index 集成 → i18n → PBT → 单元测试
