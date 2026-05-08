# Requirements Document — 工作台导出/导入与设置标签页 (Workspace Export/Import & Settings)

## Introduction

本文档是模板工作台（Template Workspace）Phase 4（最终阶段）的需求规格，从父级 spec（`.kiro/specs/template-workspace/`）中提取以下需求：

- 父级 Requirement 12: 跨环境导出
- 父级 Requirement 13: 跨环境导入
- 父级 Requirement 14: 版本管理与回滚
- 父级 Requirement 15: 设置区域 — 高级功能整合
- 父级 Requirement 18: 后端增强 — 完整 ZIP 导出内容扩展

Phase 1（`.kiro/specs/workspace-foundation/`）已实现工作台骨架，包括：
- 工作台主页面 `Index.vue`（含 7 个标签页，其中 2 个为 P4 占位标签页）
- Pinia store `useTemplateWorkspaceStore`（已加载 template、assemblyConfig、dataSources、expressions、coverage、segments、availableTransitions、testCases、testReport、reviews）
- `WorkflowStepIndicator` 步骤指示器（含完成状态计算）
- `useWorkflowSteps` composable
- 路由配置与侧边栏导航

Phase 2（`.kiro/specs/workspace-data-segments/`）已将前 3 个占位标签页替换为真实实现：
1. Tab 1 "数据结构" → `DataStructureTab.vue`
2. Tab 2 "片段编排" → `SegmentArrangementTab.vue`
3. Tab 3 "编辑" → `VisualEditorTab.vue`

Phase 3（`.kiro/specs/workspace-test-publish/`）已将第 4-5 个占位标签页替换为真实实现：
1. Tab 4 "测试" → `TestingTab.vue`
2. Tab 5 "审核与发布" → `ReviewPublishTab.vue`
3. 后端 `AutoActivationService` 自动激活服务

Phase 4 将最后 2 个占位标签页替换为真实实现：
1. Tab 6 "导出/导入" → `ExportImportTab.vue`（跨环境 ZIP 导出、JSON 配置导出、ZIP 导入含冲突解决、JSON 配置导入）
2. Tab 7 "设置" → `SettingsTab.vue`（版本历史、版本对比、Webhook 配置、水印与安全、定时任务、权限管理 — 以可折叠面板形式整合）

同时包含后端变更：
- `CompositeImportExportService` 增强：扩展 ZIP 导出内容，新增 `data-sources.json`（含凭据脱敏）、`expressions.json`、`test-data.json`、`coverage-report.json`；导入时支持扩展文件解析及旧格式向后兼容

## Glossary

- **Export_Import_Tab**: 导出/导入标签页组件 (`ExportImportTab.vue`)，在模板工作台中提供跨环境 ZIP 完整包导出、JSON 配置导出、ZIP 导入（含冲突解决）、JSON 配置导入功能，替换 P1 占位组件
- **Settings_Tab**: 设置标签页组件 (`SettingsTab.vue`)，以可折叠面板形式整合版本历史、版本对比、Webhook 配置、水印与安全、定时任务、权限管理等高级功能，替换 P1 占位组件
- **Workspace_Store**: Pinia store (`useTemplateWorkspaceStore`)，P1 已实现并在 P2/P3 扩展，管理工作台全部共享状态；P4 将扩展 `versions` 和 `permissions` 状态
- **Cross_Environment_Export**: 跨环境导出功能区域，位于 Export_Import_Tab 上半部分，提供 "Export Complete Package" 和 "Export Config Only" 两个导出按钮及导出摘要信息
- **Cross_Environment_Import**: 跨环境导入功能区域，位于 Export_Import_Tab 下半部分，提供 "Import Package" (.zip) 和 "Import Config Only" (.json) 两个导入入口
- **Composite_Import_Export_Service**: 后端组合模板导入导出服务 (`CompositeImportExportService.java`)，已实现基础 ZIP 导出（含 `config.json` + segment `.docx` 文件）和导入功能；P4 将扩展导出内容以包含 `data-sources.json`、`expressions.json`、`test-data.json`、`coverage-report.json`
- **Credential_Placeholder**: 凭据占位符字符串 `"__CREDENTIAL_PLACEHOLDER__"`，用于在导出的 `data-sources.json` 中替换敏感字段（数据库密码、API Key、OAuth 客户端密钥），导入时提示用户配置实际凭据
- **Version_History**: 版本历史子面板，位于 Settings_Tab 中，展示模板版本列表并提供回滚操作
- **Version_Diff**: 版本对比子面板，位于 Settings_Tab 中，允许用户选择两个版本号并查看差异
- **WebhookPanel**: 已有 Webhook 配置面板组件 (`frontend/src/views/templates/components/WebhookPanel.vue`)，接受 `templateId: number` prop，在 `onMounted` 时自行调用 `GET /api/templates/{templateId}/webhooks` 加载数据；Settings_Tab 直接嵌入此组件
- **WatermarkSecurityConfig**: 已有水印与安全配置组件 (`frontend/src/views/templates/components/WatermarkSecurityConfig.vue`)，接受 `templateId: number` prop；Settings_Tab 直接嵌入此组件
- **ScheduledTaskManagement**: 已有定时任务管理组件 (`frontend/src/views/templates/components/ScheduledTaskManagement.vue`)，接受 `templateId: number` prop，在 `onMounted` 时自行调用 API 加载数据；Settings_Tab 直接嵌入此组件
- **Permission_Panel**: 权限管理子面板，位于 Settings_Tab 中，展示模板级别权限列表并提供授权/撤销操作
- **Template_Version_DTO**: 模板版本 DTO (`TemplateVersionDTO`)，包含 `id`、`versionNumber`、`createdAt`、`createdBy`、`comment` 等字段
- **Version_Diff_Result**: 版本对比结果 DTO (`VersionDiffResult`)，包含 `versionA`、`versionB` 及差异详情
- **Permission_DTO**: 权限 DTO (`PermissionDTO`)，包含 `id`、`granteeId`、`granteeType`（USER/TEAM）、`permissionType`、`createdAt` 等字段
- **Grant_Permission_Request**: 授权请求 DTO (`GrantPermissionRequest`)，包含 `granteeId`、`granteeType`、`permissionType` 字段

## Requirements

### Requirement 1: 导出/导入标签页 — 跨环境导出

**User Story:** 作为模板作者，我希望将已发布的模板导出为完整 ZIP 包或仅导出 JSON 配置，以便将模板部署到其他环境。

**追溯:** 父级 spec Requirement 12

#### Acceptance Criteria

1. THE Export_Import_Tab SHALL display two sections separated by a visual divider: Cross_Environment_Export at the top and Cross_Environment_Import at the bottom
2. THE Cross_Environment_Export section SHALL display an "Export Complete Package" button (`el-button` type="primary"); WHEN clicked, THE Export_Import_Tab SHALL call `GET /api/composite-templates/{templateId}/export` (via the existing `exportCompositeAsZip` API function in `frontend/src/api/composite-templates.ts`) and trigger a browser file download of the returned ZIP blob with filename `composite-template-{templateId}.zip`
3. THE Cross_Environment_Export section SHALL display an "Export Config Only" button (`el-button`); WHEN clicked, THE Export_Import_Tab SHALL call `GET /api/composite-templates/{templateId}/export-config` (via the existing `exportCompositeConfig` API function) and trigger a browser file download of the returned JSON blob with filename `composite-config-{templateId}.json`
4. WHILE the template status is DRAFT, THE Export_Import_Tab SHALL disable the "Export Complete Package" button and display an `el-tooltip` with message using i18n key `workspace.exportImport.draftExportDisabled` explaining that the template must be in PENDING_REVIEW, REVIEWED, or ACTIVE status to export a complete package
5. THE Cross_Environment_Export section SHALL display an export summary card showing: segment count (from `store.assemblyConfig.segments.length`), data source count (from `store.dataSources.length`), expression count (from `store.expressions.length`), and test data count (from `store.testCases.length`); each count SHALL be displayed as a labeled statistic using `el-statistic` or a styled number-label pair
6. WHILE an export operation is in progress, THE Export_Import_Tab SHALL display a loading state on the clicked export button and disable both export buttons to prevent duplicate requests
7. IF the export API call fails, THEN THE Export_Import_Tab SHALL display an `ElMessage.error` notification with the error message and re-enable the export buttons

### Requirement 2: 导出/导入标签页 — 跨环境导入

**User Story:** 作为模板作者，我希望从其他环境导入模板 ZIP 包或 JSON 配置，支持冲突检测和解决，以便安全地跨环境部署模板。

**追溯:** 父级 spec Requirement 13

#### Acceptance Criteria

1. THE Cross_Environment_Import section SHALL display an "Import Package" area with an `el-upload` component accepting `.zip` files (accept=".zip"), configured with `drag` attribute to support drag-and-drop upload, and a file size limit hint
2. WHEN the user uploads a ZIP file, THE Export_Import_Tab SHALL call `POST /api/composite-templates/import` (via the existing `importCompositeFromZip` API function in `frontend/src/api/composite-templates.ts`) with the file; WHILE the import is in progress, THE Export_Import_Tab SHALL display a loading overlay on the import section
3. WHEN the import API returns a successful result (a `TemplateDTO`), THE Export_Import_Tab SHALL display a success summary dialog showing: imported template name, segment count, data source count, expression count, and a "Go to Workspace" button (`el-button` type="primary") linking to `/templates/{importedTemplateId}/workspace`
4. WHEN the import detects a naming conflict (the backend returns an error indicating a template with the same name already exists), THE Export_Import_Tab SHALL display a conflict resolution dialog with three options: "Rename" (append "-imported" suffix and retry the import with the modified name), "Overwrite" (retry the import with an overwrite flag), and "Cancel" (abort the import); the conflict resolution dialog SHALL use `el-dialog` with `el-radio-group` for option selection
5. IF the imported ZIP contains data sources with masked credentials (`__CREDENTIAL_PLACEHOLDER__` values), THEN THE Export_Import_Tab SHALL display a "Configure Credentials" dialog after successful import, listing each data source that has placeholder credentials with input fields for the user to enter actual credentials for the target environment; WHEN the user submits the credentials, THE Export_Import_Tab SHALL call `PUT /api/data-sources/{id}` for each data source to update the credentials
6. IF the ZIP file is malformed or missing required files (the backend returns a validation error), THEN THE Export_Import_Tab SHALL display an `ElMessage.error` notification with message using i18n key `workspace.exportImport.invalidPackage`
7. THE Cross_Environment_Import section SHALL also display an "Import Config Only" button with an `el-upload` component accepting `.json` files (accept=".json"); WHEN a JSON file is uploaded, THE Export_Import_Tab SHALL call `POST /api/templates/import-config` (via the existing `importConfig` API function in `frontend/src/api/import-export.ts`) with the file and display the result
8. IF the JSON config import succeeds, THEN THE Export_Import_Tab SHALL display an `ElMessage.success` notification and a "Go to Workspace" button linking to the imported template's workspace

### Requirement 3: 设置标签页 — 整体布局与版本历史

**User Story:** 作为模板作者，我希望在设置标签页中查看模板版本历史并能回滚到之前的版本，以便从有问题的发布中恢复。

**追溯:** 父级 spec Requirement 14, Requirement 15 (AC 1-2)

#### Acceptance Criteria

1. THE Settings_Tab SHALL contain the following sub-sections rendered as collapsible panels using `el-collapse` with `el-collapse-item`: "版本历史" (Version History), "版本对比" (Version Diff), "Webhook 配置" (Webhooks), "水印与安全" (Watermark & Security), "定时任务" (Scheduled Tasks), "权限管理" (Permissions)
2. WHEN the Settings_Tab is activated, THE Settings_Tab SHALL load template versions by calling `GET /api/templates/{templateId}/versions` (via the existing `getTemplateVersions` API function in `frontend/src/api/templates.ts`) and store the result in the Workspace_Store (`store.versions`)
3. THE "版本历史" panel SHALL display versions in an `el-table` with columns: version number, creation time (`createdAt`), created by (user name or ID), comment, and an action column with a "Rollback" button (`el-button` size="small")
4. WHEN the user clicks "Rollback" on a version row, THE Settings_Tab SHALL display an `ElMessageBox.confirm` confirmation dialog with message using i18n key `workspace.settings.rollbackConfirm` explaining: "Rolling back to version {versionNumber} will create a new version based on that version's content. You will need to go through the review process again to publish. Continue?"
5. WHEN the user confirms the rollback, THE Settings_Tab SHALL call `POST /api/templates/{templateId}/rollback/{versionId}` (via the existing `rollbackVersion` API function in `frontend/src/api/templates.ts`); upon success, THE Settings_Tab SHALL call `store.refreshTemplate()`, `store.refreshAssemblyConfig()`, `store.refreshSegments()`, `store.refreshCoverage()`, and `store.refreshVersions()` to refresh all workspace data, and display an `ElMessage.success` notification with message using i18n key `workspace.settings.rollbackSuccess`
6. IF the rollback API call fails, THEN THE Settings_Tab SHALL display an `ElMessage.error` notification with the error message
7. WHILE the rollback operation is in progress, THE Settings_Tab SHALL display a loading state on the clicked "Rollback" button and disable all other "Rollback" buttons to prevent concurrent rollback operations

### Requirement 4: 设置标签页 — 版本对比

**User Story:** 作为模板作者，我希望能选择两个版本进行对比查看差异，以便了解版本之间的变更内容。

**追溯:** 父级 spec Requirement 14 (AC 6), Requirement 15 (AC 2)

#### Acceptance Criteria

1. THE "版本对比" panel SHALL display two version selectors (`el-select`) labeled "Version A" and "Version B", populated with version numbers from the loaded version list (`store.versions`), and a "Compare" button (`el-button` type="primary")
2. WHEN the user selects two different version numbers and clicks "Compare", THE Settings_Tab SHALL call `GET /api/templates/{templateId}/versions/diff?versionA={a}&versionB={b}` (via the existing `getVersionDiff` API function in `frontend/src/api/templates.ts`) and display the returned `VersionDiffResult`
3. THE version diff result SHALL be displayed in a structured format showing the differences between the two versions; the display format SHALL depend on the `VersionDiffResult` structure returned by the backend
4. IF the user selects the same version number for both selectors, THEN THE "Compare" button SHALL be disabled
5. IF the version diff API call fails, THEN THE Settings_Tab SHALL display an `ElMessage.error` notification with the error message
6. WHILE the version diff operation is in progress, THE Settings_Tab SHALL display a loading state on the "Compare" button

### Requirement 5: 设置标签页 — 嵌入已有组件（Webhook、水印、定时任务）

**User Story:** 作为模板作者，我希望在设置标签页中直接管理 Webhook 配置、水印与安全设置、定时任务，无需跳转到其他页面。

**追溯:** 父级 spec Requirement 15 (AC 3-5)

#### Acceptance Criteria

1. THE "Webhook 配置" collapsible panel SHALL embed the existing `WebhookPanel` component (`frontend/src/views/templates/components/WebhookPanel.vue`) with `templateId` prop set to the current template ID from the Workspace_Store; the WebhookPanel component loads its own data via `GET /api/templates/{templateId}/webhooks` on mount, so no additional data loading is needed in the Settings_Tab
2. THE "水印与安全" collapsible panel SHALL embed the existing `WatermarkSecurityConfig` component (`frontend/src/views/templates/components/WatermarkSecurityConfig.vue`) with `templateId` prop set to the current template ID from the Workspace_Store
3. THE "定时任务" collapsible panel SHALL embed the existing `ScheduledTaskManagement` component (`frontend/src/views/templates/components/ScheduledTaskManagement.vue`) with `templateId` prop set to the current template ID from the Workspace_Store; the ScheduledTaskManagement component loads its own data via API on mount, so no additional data loading is needed in the Settings_Tab
4. WHEN any embedded component performs a mutation (create/update/delete webhook, save watermark config, create/update/delete scheduled task), THE embedded component SHALL handle its own data refresh internally (each component already implements this pattern)

### Requirement 6: 设置标签页 — 权限管理

**User Story:** 作为模板作者，我希望在设置标签页中管理模板级别的权限，以便控制哪些用户或团队可以访问和操作此模板。

**追溯:** 父级 spec Requirement 15 (AC 6)

#### Acceptance Criteria

1. THE "权限管理" collapsible panel SHALL display template-level permissions by calling `GET /api/templates/{templateId}/permissions` (via the existing `getTemplatePermissions` API function in `frontend/src/api/admin.ts`) and store the result in the Workspace_Store (`store.permissions`)
2. THE Permission_Panel SHALL display permissions in an `el-table` with columns: grantee name (user name or team name, resolved from `granteeId` and `granteeType`), grantee type (USER / TEAM rendered as colored `el-tag`), permission type (rendered as `el-tag`), granted time (`createdAt`), and an action column with a "Revoke" button (`el-button` size="small" type="danger")
3. WHEN the user clicks "Grant Permission", THE Permission_Panel SHALL open a form dialog collecting: grantee type (select from USER / TEAM), grantee (searchable `el-select` with `filterable` attribute — loading users via `GET /api/users` when type is USER, or loading teams via `GET /api/teams` when type is TEAM), and permission type (select from available permission types)
4. WHEN the grant permission form is submitted, THE Permission_Panel SHALL call `POST /api/templates/{templateId}/permissions` (via the existing `grantPermission` API function in `frontend/src/api/admin.ts`) with the `GrantPermissionRequest` data; upon success, THE Permission_Panel SHALL call `store.refreshPermissions()` to update the shared state and display an `ElMessage.success` notification
5. WHEN the user clicks "Revoke" on a permission row, THE Permission_Panel SHALL show an `ElMessageBox.confirm` confirmation dialog; upon confirmation, THE Permission_Panel SHALL call `DELETE /api/templates/{templateId}/permissions/{permissionId}` (via the existing `revokePermission` API function in `frontend/src/api/admin.ts`); upon success, THE Permission_Panel SHALL call `store.refreshPermissions()` to update the shared state
6. IF the permission list is empty, THEN THE Permission_Panel SHALL display an empty state with a prompt message using i18n key `workspace.settings.noPermissions`

### Requirement 7: 后端增强 — 完整 ZIP 导出内容扩展

**User Story:** 作为系统，我需要扩展组合模板 ZIP 导出内容以包含数据源、表达式、测试数据和覆盖率报告，使跨环境导入完整且自包含。

**追溯:** 父级 spec Requirement 18

#### Acceptance Criteria

1. WHEN the `GET /api/composite-templates/{templateId}/export` endpoint is called, THE Composite_Import_Export_Service SHALL include the following additional files in the ZIP alongside the existing `config.json` and `segments/*.docx` files: `data-sources.json` (array of data source configurations), `expressions.json` (array of expression definitions), `test-data.json` (array of all test data entries for the template), and `coverage-report.json` (the coverage report)
2. THE exported `data-sources.json` SHALL mask the following sensitive fields by replacing each with the string `"__CREDENTIAL_PLACEHOLDER__"`: database passwords (the `password` field in DATABASE type data sources), API keys in authentication configurations (the `apiKey` field), and OAuth client secrets (the `clientSecret` field); all other data source configuration fields SHALL be exported as-is
3. WHEN the `POST /api/composite-templates/import` endpoint receives a ZIP containing the extended files (`data-sources.json`, `expressions.json`, `test-data.json`), THE Composite_Import_Export_Service SHALL import data sources (creating new data source records associated with the imported template), expressions (creating new expression records), and test data (creating new test data records) in addition to the existing segment and assembly config import logic
4. IF the imported ZIP does not contain `data-sources.json`, `expressions.json`, `test-data.json`, or `coverage-report.json` (older ZIP format without extended files), THEN THE Composite_Import_Export_Service SHALL proceed with importing only the available files (`config.json` and `segments/*.docx`) without returning an error, maintaining backward compatibility with older export formats
5. THE Composite_Import_Export_Service SHALL load data sources by calling `DataSourceRepository.findByTemplateId(templateId)`, expressions by calling `ExpressionRepository.findByTemplateId(templateId)`, and test data by calling the test case repository for the template; the coverage report SHALL be obtained by calling `CoverageCheckService.checkCoverage(templateId)` or `CompositeCoverageService.checkCoverage(templateId)`
6. THE `coverage-report.json` file SHALL be included in the export for informational purposes only; it SHALL NOT be imported during the import process (coverage is recalculated from the imported template's actual state)

### Requirement 8: 工作台 Index.vue 集成 — 替换占位标签页与 Store 扩展

**User Story:** 作为模板作者，我希望工作台的导出/导入和设置标签页显示真实功能而非占位提示。

**追溯:** 父级 spec Requirement 3 (AC 7), Requirement 19 (AC 5)

#### Acceptance Criteria

1. THE Template_Workspace `Index.vue` SHALL replace the placeholder `PlaceholderTab` for tab "导出/导入" (`exportImport`) with the `ExportImportTab` component
2. THE Template_Workspace `Index.vue` SHALL replace the placeholder `PlaceholderTab` for tab "设置" (`settings`) with the `SettingsTab` component
3. THE Template_Workspace `Index.vue` SHALL remove the `placeholderTabs` array and the `PlaceholderTab` component import since all 7 tabs are now real implementations; the `PlaceholderTab` component file MAY be retained for potential future use but SHALL NOT be rendered
4. THE Workspace_Store SHALL be extended to include a `versions` state (`TemplateVersionDTO[]`) with a `refreshVersions()` action that calls `GET /api/templates/{templateId}/versions`
5. THE Workspace_Store SHALL be extended to include a `permissions` state (`PermissionDTO[]`) with a `refreshPermissions()` action that calls `GET /api/templates/{templateId}/permissions`
6. THE `versions` and `permissions` SHALL NOT be loaded during `initWorkspace` (they are loaded on-demand when the Settings_Tab is activated), to avoid unnecessary API calls for users who do not visit the settings tab
7. WHEN the user performs a mutation in the Export_Import_Tab or Settings_Tab (import template, rollback version, grant/revoke permission), THE Workspace_Store step completion status SHALL automatically recalculate via the existing `useWorkflowSteps` composable, and the Workflow_Step_Indicator SHALL reflect the updated completion state
8. WHEN the Export_Import_Tab is activated, THE Export_Import_Tab SHALL ensure `store.testCases` is loaded (calling `store.refreshTestCases()` if not yet loaded) to display the test data count in the export summary

### Requirement 9: 国际化 — P4 新增 i18n key

**User Story:** 作为任何支持语言的用户，我希望 P4 新增的所有 UI 文本都已正确国际化。

**追溯:** 父级 spec Requirement 20

#### Acceptance Criteria

1. THE Export_Import_Tab and Settings_Tab SHALL use vue-i18n for all user-visible text
2. THE Template_Workspace SHALL add translations for all P4 new i18n keys in three locale files: `en-US.json`, `zh-CN.json`, and `zh-TW.json`
3. THE new i18n keys SHALL use the following prefixes: `workspace.exportImport.*` for export/import tab labels, `workspace.settings.*` for settings tab labels
4. THE export/import section labels SHALL use i18n keys: `workspace.exportImport.exportTitle`, `workspace.exportImport.importTitle`, `workspace.exportImport.exportPackage`, `workspace.exportImport.exportConfig`, `workspace.exportImport.importPackage`, `workspace.exportImport.importConfig`, `workspace.exportImport.draftExportDisabled`, `workspace.exportImport.invalidPackage`, `workspace.exportImport.importSuccess`, `workspace.exportImport.goToWorkspace`, `workspace.exportImport.conflictTitle`, `workspace.exportImport.conflictRename`, `workspace.exportImport.conflictOverwrite`, `workspace.exportImport.conflictCancel`, `workspace.exportImport.configureCredentials`, `workspace.exportImport.segmentCount`, `workspace.exportImport.dataSourceCount`, `workspace.exportImport.expressionCount`, `workspace.exportImport.testDataCount`
5. THE settings section labels SHALL use i18n keys: `workspace.settings.versionHistory`, `workspace.settings.versionDiff`, `workspace.settings.webhooks`, `workspace.settings.watermarkSecurity`, `workspace.settings.scheduledTasks`, `workspace.settings.permissions`, `workspace.settings.rollbackConfirm`, `workspace.settings.rollbackSuccess`, `workspace.settings.compareVersions`, `workspace.settings.grantPermission`, `workspace.settings.revokeConfirm`, `workspace.settings.noPermissions`, `workspace.settings.versionA`, `workspace.settings.versionB`
6. THE action button labels SHALL reuse existing i18n keys where available (e.g., `common.export`, `common.import`, `common.compare`, `common.grant`, `common.revoke`) and define new keys only for P4-specific labels
