# Requirements Document — 工作台测试与审核发布标签页 (Workspace Testing & Review-Publish)

## Introduction

本文档是模板工作台（Template Workspace）Phase 3 的需求规格，从父级 spec（`.kiro/specs/template-workspace/`）中提取以下需求：

- 父级 Requirement 8: 测试标签页
- 父级 Requirement 9: 审核与发布面板 — 提交审核
- 父级 Requirement 10: 审核与发布面板 — 自动激活与 API Key 生成
- 父级 Requirement 11: 审核与发布面板 — 手动激活与旧版本停用
- 父级 Requirement 17: 后端增强 — 自动激活服务

Phase 1（`.kiro/specs/workspace-foundation/`）已实现工作台骨架，包括：
- 工作台主页面 `Index.vue`（含 7 个标签页，其中 4 个为占位标签页）
- Pinia store `useTemplateWorkspaceStore`（已加载 template、assemblyConfig、dataSources、expressions、coverage、segments、availableTransitions）
- `WorkflowStepIndicator` 步骤指示器（含完成状态计算）
- `useWorkflowSteps` composable
- 路由配置与侧边栏导航

Phase 2（`.kiro/specs/workspace-data-segments/`）已将前 3 个占位标签页替换为真实实现：
1. Tab 1 "数据结构" → `DataStructureTab.vue`
2. Tab 2 "片段编排" → `SegmentArrangementTab.vue`
3. Tab 3 "编辑" → `VisualEditorTab.vue`

Phase 3 将 2 个占位标签页替换为真实实现：
1. Tab 4 "测试" → `TestingTab.vue`（测试数据管理、测试执行、变量覆盖率展示）
2. Tab 5 "审核与发布" → `ReviewPublishTab.vue`（提交审核、审核状态跟踪、自动/手动激活、API 端点信息）

同时包含后端变更：
- `AutoActivationService`：审核全部通过后自动执行两步状态转换（PENDING_REVIEW → REVIEWED → ACTIVE）+ 自动生成 API Key
- 在 `TemplateReviewService.approveReview()` 中集成 `AutoActivationService` 调用

## Glossary

- **Testing_Tab**: 测试标签页组件 (`TestingTab.vue`)，在模板工作台中统一管理测试数据、执行测试、查看变量覆盖率，替换 P1 占位组件
- **Review_Publish_Tab**: 审核与发布标签页组件 (`ReviewPublishTab.vue`)，提供提交审核、审核状态跟踪、自动/手动激活、API 端点信息展示，替换 P1 占位组件
- **Workspace_Store**: Pinia store (`useTemplateWorkspaceStore`)，P1 已实现并在 P2 扩展，管理工作台全部共享状态；P3 将扩展 `testCases`、`testReport`、`reviews` 状态
- **Auto_Activation_Service**: 后端自动激活服务 (`AutoActivationService.java`)，审核全部通过后自动将模板状态从 PENDING_REVIEW 经 REVIEWED 转为 ACTIVE，并自动生成 API Key
- **API_Endpoint_Info**: API 端点信息区域，展示已激活模板的调用 URL、API Key 前缀、cURL 示例、生成文档按钮
- **Template_State_Machine**: 模板状态机服务 (`TemplateStateMachineService.java`)，已实现，管理模板生命周期状态转换：DRAFT → PENDING_REVIEW → REVIEWED → ACTIVE → ARCHIVED
- **Template_Review_Service**: 模板审核服务 (`TemplateReviewService.java`)，已实现，提供 `submitForReview`、`approveReview`、`rejectReview`、`conditionalApprove` 方法；`approveReview` 内部调用 `checkAndTransitionTemplate` 检查是否所有审核已通过
- **Api_Key_Service**: API Key 服务 (`ApiKeyService.java`)，已实现，提供 `createApiKey`、`listApiKeys` 等方法
- **Template_Test_Service**: 模板测试服务 (`TemplateTestService.java`)，已实现，提供测试用例 CRUD、单个/批量执行、导入/导出功能
- **Composite_Coverage_Service**: 组合覆盖率服务 (`CompositeCoverageService.java`)，已实现，提供 `checkCoverage` 方法返回 `CompositeCoverageReport`
- **Coverage_Check_Service**: 覆盖率检查服务 (`CoverageCheckService.java`)，已实现，提供单模板级别的 `checkCoverage` 方法返回 `CoverageReport`（含 `totalTags`、`boundTags`、`unboundTagNames`、`unusedDataSourceFields`）
- **Review_Status**: 审核状态枚举 (`ReviewStatus`)，可选值为 PENDING、APPROVED、CONDITIONAL_APPROVED、REJECTED
- **Template_State**: 模板状态枚举 (`TemplateState`)，可选值为 DRAFT、PENDING_REVIEW、REVIEWED、ACTIVE、ARCHIVED
- **Composite_Test_Report**: 组合测试报告 DTO (`CompositeTestReportDTO`)，包含 `totalTests`、`passedTests`、`failedTests`、`segmentResults` 列表
- **Test_Case**: 测试用例 DTO (`TestCaseDTO`)，包含 `id`、`templateId`、`name`、`testDataJson`、`expectedResultJson`、`comparisonType`、`createdAt`、`updatedAt`

## Requirements

### Requirement 1: 测试标签页 — 整体布局与测试数据管理

**User Story:** 作为模板作者，我希望在工作台的测试标签页中统一管理测试数据，无需跳转到独立页面即可创建、编辑、导入和导出测试用例。

**追溯:** 父级 spec Requirement 8 (AC 1-3, 7-8)

#### Acceptance Criteria

1. THE Testing_Tab SHALL display three sub-sections arranged vertically and separated by visual dividers: "测试数据" (Test Data) at the top, "测试执行" (Test Execution) in the middle, and "变量覆盖率" (Variable Coverage) at the bottom
2. WHEN the Testing_Tab is activated, THE Testing_Tab SHALL load test cases by calling `GET /api/templates/{templateId}/test-cases` and store the result in the Workspace_Store (`store.testCases`); THE Testing_Tab SHALL also read the composite coverage from the Workspace_Store (`store.coverage`) which was loaded during workspace initialization
3. THE Testing_Tab SHALL display test cases in an `el-table` with columns: name, comparison type (VARIABLE_VALUES / TEXT_CONTENT / FILE_SNAPSHOT rendered as colored `el-tag`), test data preview (first 80 characters of `testDataJson` with `el-tooltip` showing full content on hover), last updated time (`updatedAt`), and action buttons (Edit, Run, Delete)
4. WHEN the user clicks "Add Test Case", THE Testing_Tab SHALL open a form dialog collecting: name (required, max 100 characters), test data JSON (required, using a JSON editor component with syntax validation), expected result JSON (optional, using a JSON editor), and comparison type (select from VARIABLE_VALUES / TEXT_CONTENT / FILE_SNAPSHOT, default VARIABLE_VALUES)
5. WHEN the test case form is submitted, THE Testing_Tab SHALL call `POST /api/templates/{templateId}/test-cases` with the form data; upon success, THE Testing_Tab SHALL call `store.refreshTestCases()` to update the shared state
6. WHEN the user clicks "Edit" on a test case row, THE Testing_Tab SHALL open the form dialog pre-populated with the selected test case data; upon save, THE Testing_Tab SHALL call `PUT /api/test-cases/{testCaseId}` and then call `store.refreshTestCases()`
7. WHEN the user clicks "Delete" on a test case row, THE Testing_Tab SHALL show an `ElMessageBox.confirm` confirmation dialog; upon confirmation, THE Testing_Tab SHALL disable the row's action buttons, call `DELETE /api/test-cases/{testCaseId}`, then call `store.refreshTestCases()`; IF the delete call fails, THE Testing_Tab SHALL re-enable the action buttons and display an `ElMessage.error` notification
8. WHEN the user clicks "Export Test Data", THE Testing_Tab SHALL call `GET /api/templates/{templateId}/test-cases/export` and trigger a browser file download of the JSON response as a `.json` file named `test-cases-{templateId}.json`
9. WHEN the user clicks "Import Test Data", THE Testing_Tab SHALL display a file upload area accepting `.json` files; WHEN a file is selected, THE Testing_Tab SHALL read the file content and call `POST /api/templates/{templateId}/test-cases/import` with the JSON string; upon success, THE Testing_Tab SHALL call `store.refreshTestCases()` and display an `ElMessage.success` notification showing the number of imported test cases
10. IF the test case list is empty, THEN THE Testing_Tab SHALL display an empty state card with an icon and a prompt message using i18n key `workspace.testing.emptyTestData`

### Requirement 2: 测试标签页 — 测试执行与结果展示

**User Story:** 作为模板作者，我希望能在测试标签页中执行所有测试并查看详细结果，以便在发布前验证模板的正确性。

**追溯:** 父级 spec Requirement 8 (AC 4, 9-10)

#### Acceptance Criteria

1. THE Testing_Tab SHALL display a "Run All Tests" button in the "测试执行" section; WHEN clicked, THE Testing_Tab SHALL call `POST /api/composite-templates/{templateId}/tests/run` and display the returned `CompositeTestReportDTO` showing: total tests count, passed tests count (green), failed tests count (red), execution time (`executedAt`), and a per-segment results breakdown table
2. WHILE the test execution is in progress, THE Testing_Tab SHALL display a loading state on the "Run All Tests" button and disable the button to prevent duplicate submissions
3. THE per-segment results breakdown table SHALL display columns: segment name, total tests, passed tests, failed tests, and a status indicator (all-pass = green check icon, has-failures = red cross icon)
4. WHEN the user clicks "Run" on an individual test case row (in the Test Data section), THE Testing_Tab SHALL call `POST /api/test-cases/{testCaseId}/run` and display the result in an `ElMessage` notification: success as `ElMessage.success` with "Test passed", failure as `ElMessage.error` with the failure details
5. THE Testing_Tab SHALL display a "Quick Test" button; WHEN clicked, THE Testing_Tab SHALL show a dropdown of available test cases; WHEN the user selects a test case, THE Testing_Tab SHALL call `POST /api/composite-templates/{templateId}/preview` with the test case's `testDataJson` as parameters and trigger a browser file download of the generated document
6. THE Testing_Tab SHALL display a "Generate Test Document" button; WHEN clicked, THE Testing_Tab SHALL allow the user to select a test case from a dropdown, then call `POST /api/composite-templates/{templateId}/preview` with the selected test data as parameters and open the returned `previewUrl` in a new browser tab or trigger a file download
7. IF the "Run All Tests" API call fails, THEN THE Testing_Tab SHALL display an `ElMessage.error` notification with the error message and re-enable the "Run All Tests" button
8. WHEN the test report is loaded, THE Testing_Tab SHALL store the report in the Workspace_Store (`store.testReport`) so that the Workflow_Step_Indicator can use it to calculate Step 5 completion status

### Requirement 3: 测试标签页 — 变量覆盖率展示

**User Story:** 作为模板作者，我希望在测试标签页中查看变量覆盖率详情，以便了解哪些变量尚未绑定数据源并在发布前修复。

**追溯:** 父级 spec Requirement 8 (AC 5-7)

#### Acceptance Criteria

1. THE Testing_Tab SHALL display the "变量覆盖率" section showing: overall coverage percentage as a prominent number with an `el-progress` bar (color: green when 100%, orange when 50%-99%, red when below 50%), total variables count (`totalTags` from `CoverageReport` or sum of `totalVariables` from `CompositeCoverageReport.segmentCoverages`), bound variables count, and unbound variables count
2. THE Testing_Tab SHALL display a per-segment coverage breakdown table using `CompositeCoverageReport.segmentCoverages` with columns: segment name, total variables, bound variables, coverage percentage (rendered as an inline `el-progress` bar), and a status indicator (100% = green check, below 100% = orange warning)
3. WHEN the overall coverage rate is below 100% and total variables count is greater than zero, THE Testing_Tab SHALL display a warning alert using `el-alert` type "warning" with message using i18n key `workspace.testing.coverageWarning`, and SHALL display the list of unbound variable names (`unboundTagNames` from `CoverageReport`) as red-colored `el-tag` elements
4. THE Testing_Tab SHALL display a list of unused data source fields (`unusedDataSourceFields` from `CoverageReport`) in a collapsible section labeled with i18n key `workspace.testing.unusedFields`, rendered as gray `el-tag` elements
5. IF the total variable count is zero (no segments or no variables detected), THEN THE Testing_Tab SHALL display the coverage section with a message using i18n key `workspace.testing.noVariables`
6. THE Testing_Tab SHALL display a "Refresh Coverage" button; WHEN clicked, THE Testing_Tab SHALL call `store.refreshCoverage()` to reload the coverage data from the backend

### Requirement 4: 审核与发布标签页 — 状态展示与提交审核

**User Story:** 作为模板作者，我希望在审核与发布标签页中查看当前模板状态并提交审核请求，以便在工作台内完成审核流程。

**追溯:** 父级 spec Requirement 9

#### Acceptance Criteria

1. THE Review_Publish_Tab SHALL display the current template status prominently at the top with a colored status badge: DRAFT as gray `el-tag`, PENDING_REVIEW as orange `el-tag`, REVIEWED as blue `el-tag`, ACTIVE as green `el-tag`, ARCHIVED as red `el-tag`
2. WHILE the template status is DRAFT or REVIEWED, THE Review_Publish_Tab SHALL display a "Submit for Review" button
3. WHEN the user clicks "Submit for Review", THE Review_Publish_Tab SHALL open a dialog collecting: reviewers (required, searchable user selector using `el-select` with `filterable` and `multiple` attributes, loading users by calling `GET /api/users` with the current tenant context), and review level (select from 1 = Initial Review / 2 = Final Review, default 1)
4. WHEN the submit review form is confirmed, THE Review_Publish_Tab SHALL call `POST /api/templates/{templateId}/reviews` with the `SubmitReviewRequest` containing `reviewerIds` and `reviewLevel`; upon success, THE Review_Publish_Tab SHALL call `store.refreshTemplate()` and `store.refreshReviews()` to update the shared state, and display an `ElMessage.success` notification
5. IF the review submission API call fails, THEN THE Review_Publish_Tab SHALL display an `ElMessage.error` notification with the error message and keep the dialog open so the user can retry
6. WHILE the template status is PENDING_REVIEW, REVIEWED, or ACTIVE, THE Review_Publish_Tab SHALL display a review status table by loading reviews from the Workspace_Store (`store.reviews`); the table SHALL show columns: reviewer name (resolved by calling `GET /api/users/{reviewerId}` or by maintaining a local user map loaded from `GET /api/users`), review status (PENDING / APPROVED / CONDITIONAL_APPROVED / REJECTED rendered as colored `el-tag`), review level, comment, suggestions (as a list), creation time (`createdAt`), and completion time (`completedAt`)
7. THE Review_Publish_Tab SHALL support pagination for the review list using `el-pagination` with page size 10
8. IF all review records have status APPROVED or CONDITIONAL_APPROVED, THEN THE Review_Publish_Tab SHALL display a "Ready to Publish" indicator using a green `el-alert` with type "success"
9. IF any review record has status REJECTED, THEN THE Review_Publish_Tab SHALL display a "Review Rejected" indicator using a red `el-alert` with type "error" showing the rejection reason (comment from the rejected review), and a "Revise & Resubmit" button
10. WHEN the user clicks "Revise & Resubmit", THE Review_Publish_Tab SHALL display the rejected reviewer's comments and suggestions prominently in a highlighted card; since the backend `TemplateReviewService.rejectReview()` already transitions the template from PENDING_REVIEW to DRAFT upon rejection, THE Review_Publish_Tab SHALL call `store.refreshTemplate()` and `store.refreshReviews()` to reflect the current DRAFT status, and then enable the "Submit for Review" button so the user can resubmit after addressing the feedback

### Requirement 5: 审核与发布标签页 — 自动激活与 API Key 生成

**User Story:** 作为模板作者，我希望模板在审核全部通过后自动激活并生成 API Key，无需手动操作即可开始提供 API 服务。

**追溯:** 父级 spec Requirement 10

#### Acceptance Criteria

1. WHEN the template is activated (either automatically via Auto_Activation_Service or manually), THE Review_Publish_Tab SHALL display the API_Endpoint_Info section containing: the API endpoint URL (`POST /api/generate/{templateId}`), the API Key prefix (masked, showing first 8 characters followed by "..."), a "Copy URL" button, a "Copy API Key" button (copies the full key prefix display), a sample cURL request block (using `<pre>` with copy button), and a "Generate Document" button
2. THE API_Endpoint_Info section SHALL include a note using i18n key `workspace.reviewPublish.apiNote` explaining that the API endpoint always serves the latest active version and callers can use `?version={versionNumber}` to pin to a specific version
3. WHEN the Review_Publish_Tab is activated and the template status is ACTIVE, THE Review_Publish_Tab SHALL load API keys by calling `GET /api/api-keys` (which returns keys for the current tenant) and display the first active key's prefix in the API_Endpoint_Info section
4. IF no API Key exists for the current tenant, THEN THE API_Endpoint_Info section SHALL display a warning message using i18n key `workspace.reviewPublish.noApiKey` and a "Create API Key" button; WHEN clicked, THE Review_Publish_Tab SHALL call the API Key creation endpoint to generate a new key
5. IF the auto-activation process fails (detected by the template remaining in REVIEWED status after all reviews are approved), THEN THE Review_Publish_Tab SHALL display an error message using i18n key `workspace.reviewPublish.autoActivationFailed` and a manual "Activate" button as fallback
6. WHILE the template status is REVIEWED and auto-activation has not occurred, THE Review_Publish_Tab SHALL display a manual "Activate" button as a fallback

### Requirement 6: 审核与发布标签页 — 手动激活

**User Story:** 作为模板作者，我希望在自动激活未触发时能手动激活已审核的模板，并在激活前收到覆盖率不足的警告。

**追溯:** 父级 spec Requirement 11

#### Acceptance Criteria

1. WHEN the user clicks the manual "Activate" button, THE Review_Publish_Tab SHALL first check the current coverage from the Workspace_Store (`store.coverage`); IF the overall coverage percentage is below 100%, THE Review_Publish_Tab SHALL display a warning dialog using `ElMessageBox.confirm` with message using i18n key `workspace.reviewPublish.lowCoverageWarning` and Confirm/Cancel buttons
2. WHEN the user confirms activation (or coverage is 100%), THE Review_Publish_Tab SHALL call `POST /api/templates/{templateId}/activate` (using the existing `activateTemplate` endpoint in `TemplateController` which performs the state machine transition REVIEWED → ACTIVE) to transition the template to ACTIVE status
3. WHEN the activation is successful, THE Review_Publish_Tab SHALL call `store.refreshTemplate()` and `store.refreshTransitions()` to update the shared state, update the status badge to ACTIVE (green), and display the API_Endpoint_Info section
4. IF the activation API call fails, THEN THE Review_Publish_Tab SHALL display an `ElMessage.error` notification with the error message (e.g., "Cannot activate composite template without assembly config", "Cannot activate: segments not found")

### Requirement 7: 后端 — 自动激活服务 (AutoActivationService)

**User Story:** 作为系统，我需要一个后端服务在审核全部通过后自动激活模板并生成 API Key，以实现发布流程的完全自动化。

**追溯:** 父级 spec Requirement 17

#### Acceptance Criteria

1. THE Auto_Activation_Service SHALL be a Spring `@Service` class named `AutoActivationService` with constructor-injected dependencies: `TemplateStateMachineService`, `ApiKeyService`, `ApiKeyRepository`, `TemplateRepository`
2. THE Auto_Activation_Service SHALL expose a method `tryAutoActivate(Long templateId)` that performs the following two-step state transition: first call `TemplateStateMachineService.transition(templateId, TemplateState.REVIEWED)` to transition from PENDING_REVIEW to REVIEWED, then call `TemplateStateMachineService.transition(templateId, TemplateState.ACTIVE)` to transition from REVIEWED to ACTIVE
3. WHEN the Auto_Activation_Service successfully transitions the template to ACTIVE, THE Auto_Activation_Service SHALL check if any active (enabled and non-expired) API Key exists for the template's tenant by querying the `ApiKeyRepository`; IF no active API Key exists, THE Auto_Activation_Service SHALL call `ApiKeyService.createApiKey()` with an auto-generated name in the format `"auto-{templateName}-{timestamp}"` (where timestamp is `Instant.now().toEpochMilli()`)
4. IF the first state transition (PENDING_REVIEW → REVIEWED) fails, THEN THE Auto_Activation_Service SHALL log the error at WARN level with template ID and the exception message, and return without throwing an exception to the caller
5. IF the second state transition (REVIEWED → ACTIVE) fails, THEN THE Auto_Activation_Service SHALL log the error at WARN level with template ID and the exception message, and leave the template in REVIEWED status without throwing an exception to the caller
6. IF the API Key creation fails, THEN THE Auto_Activation_Service SHALL log the error at WARN level with template ID and the exception message, and leave the template in ACTIVE status (activation succeeded, only key generation failed) without throwing an exception to the caller
7. THE Auto_Activation_Service SHALL be triggered from within `TemplateReviewService.checkAndTransitionTemplate()` method: after the existing logic determines that all review levels are completed (the `!nextLevelPending` branch), instead of directly calling `stateMachineService.transition(templateId, TemplateState.REVIEWED)`, THE method SHALL call `autoActivationService.tryAutoActivate(templateId)` which performs both the REVIEWED transition and the ACTIVE transition

### Requirement 8: 工作台 Index.vue 集成 — 替换占位标签页与 Store 扩展

**User Story:** 作为模板作者，我希望工作台的测试和审核发布标签页显示真实功能而非占位提示。

**追溯:** 父级 spec Requirement 3 (AC 7), Requirement 19 (AC 5)

#### Acceptance Criteria

1. THE Template_Workspace `Index.vue` SHALL replace the placeholder `PlaceholderTab` for tab "测试" (`testing`) with the `TestingTab` component
2. THE Template_Workspace `Index.vue` SHALL replace the placeholder `PlaceholderTab` for tab "审核与发布" (`reviewPublish`) with the `ReviewPublishTab` component
3. THE Template_Workspace `Index.vue` SHALL retain the `PlaceholderTab` for the remaining 2 tabs: "导出/导入" (Phase 4), "设置" (Phase 4)
4. THE Workspace_Store SHALL be extended to include a `testCases` state (`TestCaseDTO[]`) with a `refreshTestCases()` action that calls `GET /api/templates/{templateId}/test-cases`
5. THE Workspace_Store SHALL be extended to include a `testReport` state (`CompositeTestReportDTO | null`) that is set after running all tests
6. THE Workspace_Store SHALL be extended to include a `reviews` state (`TemplateReviewDTO[]`) with a `refreshReviews()` action that calls `GET /api/templates/{templateId}/reviews` (first page, size 20)
7. THE `testCases` and `reviews` SHALL NOT be loaded during `initWorkspace` (they are loaded on-demand when the respective tabs are activated), to avoid unnecessary API calls for users who do not visit those tabs
8. WHEN the user performs a mutation in the Testing_Tab or Review_Publish_Tab (run tests, submit review, activate), THE Workspace_Store step completion status SHALL automatically recalculate via the existing `useWorkflowSteps` composable, and the Workflow_Step_Indicator SHALL reflect the updated completion state

### Requirement 9: 国际化 — P3 新增 i18n key

**User Story:** 作为任何支持语言的用户，我希望 P3 新增的所有 UI 文本都已正确国际化。

**追溯:** 父级 spec Requirement 20

#### Acceptance Criteria

1. THE Testing_Tab and Review_Publish_Tab SHALL use vue-i18n for all user-visible text
2. THE Template_Workspace SHALL add translations for all P3 new i18n keys in three locale files: `en-US.json`, `zh-CN.json`, and `zh-TW.json`
3. THE new i18n keys SHALL use the following prefixes: `workspace.testing.*` for testing tab labels, `workspace.reviewPublish.*` for review and publish tab labels
4. THE empty state and warning messages SHALL use i18n keys: `workspace.testing.emptyTestData`, `workspace.testing.noVariables`, `workspace.testing.coverageWarning`, `workspace.testing.unusedFields`, `workspace.reviewPublish.autoActivationFailed`, `workspace.reviewPublish.noApiKey`, `workspace.reviewPublish.lowCoverageWarning`, `workspace.reviewPublish.apiNote`
5. THE action button labels SHALL reuse existing i18n keys where available (e.g., `common.edit`, `common.delete`, `common.run`) and define new keys only for P3-specific labels
6. THE status badge labels SHALL use i18n keys: `workspace.reviewPublish.status.draft`, `workspace.reviewPublish.status.pendingReview`, `workspace.reviewPublish.status.reviewed`, `workspace.reviewPublish.status.active`, `workspace.reviewPublish.status.archived`
