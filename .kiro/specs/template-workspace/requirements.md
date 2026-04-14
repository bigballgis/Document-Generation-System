# Requirements Document — 模板工作台 (Template Workspace)

## Introduction

模板工作台（Template Workspace）是对现有模板编辑工作流的一次重大 UX 重构。当前系统中，模板管理分散在 5+ 个独立页面（Templates、Data Sources、Segments、Composite Templates、Template Detail），模板详情页包含 10+ 个标签页，用户缺乏引导式工作流。本功能将这些分散的操作整合为一个统一的、引导式的工作台界面，遵循"创建 → 定义数据 → 编排片段 → 编辑 → 测试 → 审核发布 → 跨环境导出 → 版本回滚"的自然工作流。

核心目标：
1. 将分散的模板管理页面整合为单一工作台页面
2. 提供可视化步骤指示器引导用户完成完整工作流
3. 将 10+ 标签页精简为 7 个聚焦标签页，高级功能收纳至"设置"区域
4. 实现审核通过后自动激活、自动生成 API Key、自动停用旧版本
5. 支持完整 ZIP 跨环境导出/导入（含片段、配置、数据源、表达式、测试数据、覆盖率报告）

## Glossary

- **Template_Workspace**: 模板工作台页面，替代当前 Template Detail 和 Composite Template Detail 页面，提供引导式模板编辑工作流的统一界面
- **Workflow_Step_Indicator**: 工作流步骤指示器组件，以水平步骤条形式展示 8 个工作流步骤的完成状态
- **Sidebar_Navigation**: 主布局侧边栏导航菜单，位于 MainLayout.vue 中的 el-menu 组件
- **Template_Creation_Wizard**: 模板创建向导对话框，引导用户通过分步表单创建新的组合模板
- **Data_Structure_Tab**: 数据结构标签页，在模板上下文中管理数据源和表达式的统一界面
- **Segment_Arrangement_Tab**: 片段编排标签页，提供拖拽排序、内联创建片段的界面
- **Visual_Editor_Tab**: 可视化编辑标签页，集成 OnlyOffice 编辑器用于编辑片段内容
- **Testing_Tab**: 测试标签页，统一管理测试数据、执行测试、查看变量覆盖率
- **Review_Publish_Panel**: 审核与发布面板，提供提交审核、审核状态跟踪、自动激活、API 端点展示的统一界面
- **Settings_Section**: 设置区域，收纳高级功能（Webhook、水印、定时任务、权限）
- **Auto_Activation_Service**: 自动激活服务，审核通过后自动将模板状态转为 ACTIVE，自动停用同模板的旧 ACTIVE 版本，自动生成或更新 API Key
- **Cross_Environment_Export**: 跨环境导出功能，将已发布模板打包为完整 ZIP 文件（含片段 .docx、assembly config、数据源配置、表达式、测试数据、覆盖率报告）
- **Cross_Environment_Import**: 跨环境导入功能，从 ZIP 文件导入完整模板配置，支持冲突检测和解决策略
- **Version_Rollback**: 版本回滚功能，将模板回滚到指定历史版本，基于历史版本内容创建新版本（版本号递增），模板状态不变，需重新走审核发布流程
- **Composite_Template**: 组合模板，由多个 Segment 片段按顺序组装而成的模板
- **API_Endpoint_Info**: API 端点信息组件，展示已激活模板的调用 URL、API Key、请求示例


## Requirements

### Requirement 1: 侧边栏导航重构

**User Story:** As a template author, I want a simplified sidebar navigation that groups template-related functions under a single "Template Management" entry, so that I can access the template workspace without navigating through multiple scattered menu items.

#### Acceptance Criteria

1. THE Sidebar_Navigation SHALL display a top-level menu item labeled "模板管理" (Template Management) that replaces the current separate "Templates", "Segments", "Components", and "Composite Templates" menu items
2. WHEN the user clicks the "模板管理" menu item, THE Sidebar_Navigation SHALL expand to show two sub-items: "模板列表" (Template List) linking to `/templates` and "片段库" (Segment Library) linking to `/segments`
3. THE Sidebar_Navigation SHALL remove the standalone "Data Sources" top-level menu item; data source management is primarily accessed within the Template_Workspace context, but a global "Data Sources" view SHALL remain accessible as a tab within the Admin page for cross-template data source auditing
4. THE Sidebar_Navigation SHALL retain the following top-level menu items unchanged: Dashboard, Documents, Tasks, Market, Admin, Audit
5. WHEN the user navigates to a Template_Workspace route (`/templates/:id/workspace`), THE Sidebar_Navigation SHALL highlight the "模板管理" parent menu item as active

### Requirement 2: 模板列表页增强

**User Story:** As a template author, I want the template list page to support creating composite templates via a guided wizard and to navigate directly to the workspace, so that I can start the guided workflow immediately after creation.

#### Acceptance Criteria

1. WHEN the user clicks the "Create" button on the template list page, THE Template_Creation_Wizard SHALL open as a dialog overlay
2. THE Template_Creation_Wizard SHALL collect the following fields in step 1: template name (required, max 100 characters), description (optional, max 500 characters), category (optional, tree-select from existing categories), tags (optional, multi-select from existing tags)
3. THE Template_Creation_Wizard SHALL collect the following field in step 2: output format (select from WORD/PDF, default WORD)
4. WHEN the user completes the wizard and clicks "Create", THE Template_Creation_Wizard SHALL call the `POST /api/composite-templates` endpoint with the collected data
5. WHEN the composite template is created successfully, THE Template_Creation_Wizard SHALL navigate the user to `/templates/{newTemplateId}/workspace`
6. IF the `POST /api/composite-templates` endpoint returns an error, THEN THE Template_Creation_Wizard SHALL display the error message from the response and remain on the current wizard step
7. WHEN the user clicks a template name in the template list table, THE Templates_List_Page SHALL navigate to `/templates/{id}/workspace` instead of the current `/templates/{id}` detail page
8. THE Templates_List_Page SHALL display a "Workspace" action button in the actions column for each template row, linking to `/templates/{id}/workspace`

### Requirement 3: 模板工作台页面 — 整体布局与步骤指示器

**User Story:** As a template author, I want a unified workspace page with a visual step indicator showing my progress through the template creation workflow, so that I know what step I am on and what to do next.

#### Acceptance Criteria

1. THE Template_Workspace SHALL be accessible at route `/templates/:id/workspace` and SHALL load the composite template data by calling `GET /api/templates/{id}`
2. IF the loaded template has `templateType = 'SINGLE'`, THEN THE Template_Workspace SHALL display a migration prompt: "This is a single-file template. Convert to composite template to use the full workspace." with a "Convert" button that calls `POST /api/templates/{id}/migrate-to-composite`; after successful migration, THE Template_Workspace SHALL reload with the new composite template data
3. THE Template_Workspace SHALL display a page header containing: the template name, the current template status as a colored tag, the current version number, and a "Back to List" button
4. THE Workflow_Step_Indicator SHALL display 8 steps in a horizontal bar: "创建模板" (Create), "定义数据" (Data Structure), "编排片段" (Arrange Segments), "编辑内容" (Edit Content), "测试验证" (Test), "审核发布" (Review & Publish), "跨环境导出" (Export), "版本管理" (Version Management)
5. THE Workflow_Step_Indicator SHALL calculate each step's completion status based on the following rules:
   - Step 1 "创建模板": complete when the template record exists (always complete on workspace page)
   - Step 2 "定义数据": complete when at least one data source or expression exists for the template
   - Step 3 "编排片段": complete when the assembly config contains at least one enabled segment
   - Step 4 "编辑内容": complete when all enabled segments have at least one version beyond the initial version (versionNumber > 1), indicating the segment has been edited after creation
   - Step 5 "测试验证": complete when at least one test has been executed and variable coverage rate is 100%
   - Step 6 "审核发布": complete when the template status is ACTIVE
   - Step 7 "跨环境导出": always shown as available (no completion tracking)
   - Step 8 "版本管理": always shown as available (no completion tracking)
6. WHEN the user clicks a step in the Workflow_Step_Indicator, THE Template_Workspace SHALL activate the corresponding tab below according to this mapping: Step 1 → no tab (already on workspace), Step 2 → "数据结构" tab, Step 3 → "片段编排" tab, Step 4 → "编辑" tab, Step 5 → "测试" tab, Step 6 → "审核与发布" tab, Step 7 → "导出/导入" tab, Step 8 → "设置" tab (Version History section)
7. THE Template_Workspace SHALL display a tab bar below the step indicator with 7 tabs: "数据结构" (Data Structure), "片段编排" (Segments), "编辑" (Editor), "测试" (Testing), "审核与发布" (Review & Publish), "导出/导入" (Export/Import), "设置" (Settings)
8. WHILE the template status is DRAFT, THE Workflow_Step_Indicator SHALL visually highlight the first incomplete step as the recommended next action using a pulsing or highlighted style
9. WHILE the template status is ACTIVE, THE Template_Workspace SHALL display a prominent banner: "This template is currently active and serving API requests. Editing will create a new draft version." with an "Edit as New Version" button; WHEN clicked, THE Template_Workspace SHALL call a new backend endpoint `POST /api/templates/{templateId}/create-draft-version` which creates a new version snapshot and transitions the template status to DRAFT, allowing the user to make changes and go through the review-publish cycle again; NOTE: this backend endpoint does not exist yet and must be implemented as part of this feature — it should create a new TemplateVersion record and set the template status to DRAFT via the state machine (ACTIVE → ARCHIVED → DRAFT is not a valid transition, so the endpoint should directly set status to DRAFT as a special "edit" operation, bypassing the normal state machine)

### Requirement 4: 数据结构标签页 — 数据源管理

**User Story:** As a template author, I want to manage data sources directly within the template workspace context, so that I do not need to navigate to a separate Data Sources page and manually select the template.

#### Acceptance Criteria

1. THE Data_Structure_Tab SHALL display two sub-sections: "数据源" (Data Sources) at the top and "表达式" (Expressions) at the bottom, separated by a visual divider
2. WHEN the Data_Structure_Tab is activated, THE Data_Structure_Tab SHALL load data sources by calling `GET /api/templates/{templateId}/data-sources` and expressions by calling `GET /api/templates/{templateId}/expressions`
3. THE Data_Structure_Tab SHALL display data sources in a table with columns: name, type (HTTP_API/DATABASE/INTERNAL_SYSTEM as colored tags), priority, cache status, last updated time, and action buttons (Edit, Test Connection, Delete)
4. WHEN the user clicks "Add Data Source", THE Data_Structure_Tab SHALL open a form dialog pre-bound to the current template ID, collecting: name (required), type (required, select from HTTP_API/DATABASE/INTERNAL_SYSTEM); the form SHALL dynamically display type-specific configuration fields: for HTTP_API — URL, method, headers, params, auth type, timeout, retry config; for DATABASE — db type, host, port, database name, username, password, SQL query; for INTERNAL_SYSTEM — service name, service URL, auth config. Additionally: cache enabled (toggle), cache TTL (number input, visible when cache enabled), priority (number input, default 0). This reuses the existing DataSourceFormDialog component pattern.
5. WHEN the user clicks "Test Connection" on a data source row, THE Data_Structure_Tab SHALL call `POST /api/data-sources/{id}/test` and display the result (success/failure, response time) in a result dialog
6. WHEN the user clicks "Delete" on a data source row, THE Data_Structure_Tab SHALL show a confirmation dialog; upon confirmation, THE Data_Structure_Tab SHALL call `DELETE /api/data-sources/{id}` and refresh the data source list
7. IF the data source list is empty, THEN THE Data_Structure_Tab SHALL display an empty state with a prompt: "No data sources configured. Add a data source to provide dynamic data for your template."

### Requirement 5: 数据结构标签页 — 表达式管理

**User Story:** As a template author, I want to manage expressions (JavaScript/Excel formulas) within the same data structure tab, so that I can define computed fields alongside data sources in a unified parameter table view.

#### Acceptance Criteria

1. THE Data_Structure_Tab SHALL display expressions in a table below the data sources section with columns: name, expression type (JS/EXCEL as colored tags), expression content (truncated to 80 characters with tooltip for full content), last updated time, and action buttons (Edit, Validate, Delete)
2. WHEN the user clicks "Add Expression", THE Data_Structure_Tab SHALL open a form dialog collecting: name (required, max 100 characters), type (required, select from JS/EXCEL), expression content (required, using a code editor with syntax highlighting), description (optional)
3. WHEN the user clicks "Validate" on an expression row, THE Data_Structure_Tab SHALL call `POST /api/expressions/validate` with the expression content and display the validation result (valid/invalid with error details)
4. WHEN the user clicks "Delete" on an expression row, THE Data_Structure_Tab SHALL show a confirmation dialog; upon confirmation, THE Data_Structure_Tab SHALL call `DELETE /api/expressions/{id}` and refresh the expression list
5. IF the expression list is empty, THEN THE Data_Structure_Tab SHALL display an empty state with a prompt: "No expressions defined. Add expressions to create computed fields from your data sources."

### Requirement 6: 片段编排标签页

**User Story:** As a template author, I want to create, arrange, and configure segments directly within the workspace, so that I can build the document structure without navigating to separate pages.

#### Acceptance Criteria

1. THE Segment_Arrangement_Tab SHALL display the current assembly configuration by calling `GET /api/composite-templates/{templateId}/assembly-config` and render each segment as a draggable card
2. WHEN the user drags a segment card to a new position, THE Segment_Arrangement_Tab SHALL update the local segment order and visually reflect the new arrangement
3. WHEN the user clicks "Save Arrangement", THE Segment_Arrangement_Tab SHALL call `PUT /api/composite-templates/{templateId}/assembly-config` with the updated segment list
4. WHEN the user clicks "Add Segment", THE Segment_Arrangement_Tab SHALL open a panel with two options: "Create New Segment" (opens a segment creation form) and "Add Existing Segment" (opens a searchable segment list)
5. WHEN the user selects "Create New Segment", THE Segment_Arrangement_Tab SHALL display an inline form collecting: segment name (required), segment type (select from COVER/TOC/CHAPTER/TABLE/SIGNATURE/LEGAL/APPENDIX), description (optional), and a file upload for the .docx file (optional — if not provided, an empty .docx template will be created server-side)
6. WHEN the new segment form is submitted, THE Segment_Arrangement_Tab SHALL call `POST /api/segments` with the form data and file (if provided), then automatically add the created segment to the assembly configuration; NOTE: the backend `SegmentController.createSegment` endpoint's `@RequestPart("file")` must be changed to `required = false` to support creating segments without an initial .docx file
7. WHEN the user selects "Add Existing Segment", THE Segment_Arrangement_Tab SHALL display a searchable list of segments (calling `GET /api/segments` with search parameters) and allow the user to select one or more segments to add
8. WHEN the user clicks the expand arrow on a segment card, THE Segment_Arrangement_Tab SHALL show inline configuration options: enabled toggle, page break before toggle, locked version number input, condition expression input, and data scope mapper
9. WHEN the user clicks "Remove" on a segment card, THE Segment_Arrangement_Tab SHALL remove the segment from the assembly configuration (not delete the segment itself) after confirmation
10. THE Segment_Arrangement_Tab SHALL support keyboard shortcuts: Alt+ArrowUp to move selected segment up, Alt+ArrowDown to move selected segment down, Ctrl+Z to undo, Ctrl+Shift+Z to redo
11. IF the assembly configuration contains zero segments, THEN THE Segment_Arrangement_Tab SHALL display an empty state with a prompt: "No segments added. Add segments to build your document structure."

### Requirement 7: 可视化编辑标签页

**User Story:** As a template author, I want to open the OnlyOffice editor for any segment directly from the workspace, so that I can edit segment content without navigating away.

#### Acceptance Criteria

1. THE Visual_Editor_Tab SHALL display a list of all segments in the current assembly configuration with columns: position number, segment name, segment type, last edited time, and an "Open Editor" button
2. WHEN the user clicks "Open Editor" on a segment row, THE Visual_Editor_Tab SHALL open the OnlyOffice editor for that segment in a new browser tab at `/segments/{segmentId}/editor`; alternatively, if the workspace viewport is wide enough (≥1440px), THE Visual_Editor_Tab MAY embed the editor in an iframe panel within the workspace
3. THE Visual_Editor_Tab SHALL display a "Preview Composite" button; WHEN clicked, THE Visual_Editor_Tab SHALL call `POST /api/composite-templates/{templateId}/preview` and display the preview result (download link or inline preview)
4. THE Visual_Editor_Tab SHALL display a "Selective Preview" button; WHEN clicked, THE Visual_Editor_Tab SHALL allow the user to select specific segments via checkboxes, then call `POST /api/composite-templates/{templateId}/preview/selective` with the selected segment IDs
5. WHILE a segment is being edited by another user (lock exists), THE Visual_Editor_Tab SHALL display a lock icon and the editing user's name next to that segment row; lock status SHALL be checked by calling `GET /api/segments/{segmentId}/lock` for each segment when the tab is activated

### Requirement 8: 测试标签页

**User Story:** As a template author, I want a unified testing interface where I can create test data, run tests, and check variable coverage, so that I can validate my template before publishing.

#### Acceptance Criteria

1. THE Testing_Tab SHALL display three sub-sections arranged vertically: "Test Data" at the top, "Test Execution" in the middle, and "Variable Coverage" at the bottom
2. THE Testing_Tab SHALL load test data by calling `GET /api/segments/{segmentId}/test-data` for each segment in the assembly, and composite test results by calling `GET /api/composite-templates/{templateId}/coverage`
3. WHEN the user clicks "Add Test Data", THE Testing_Tab SHALL open a form dialog with a JSON editor for entering test data, a name field (required, max 100 characters), and a segment selector (to associate test data with a specific segment)
4. WHEN the user clicks "Run All Tests", THE Testing_Tab SHALL call `POST /api/composite-templates/{templateId}/tests/run` and display the composite test report showing: total tests, passed tests, failed tests, and per-segment results
5. THE Testing_Tab SHALL display the variable coverage section showing: total variables count, bound variables count, unbound variables list, coverage percentage as a progress bar, and a list of unused data source fields
6. IF the total variable count is zero (no segments or no variables detected), THEN THE Testing_Tab SHALL display the coverage section with a message: "No variables detected. Add segments and edit them to define template variables."
7. WHEN the coverage rate is below 100% and total variables > 0, THE Testing_Tab SHALL highlight unbound variables in red and display a warning message: "Some variables are not bound to data sources. Bind all variables before publishing."
7. WHEN the user clicks "Export Test Data", THE Testing_Tab SHALL export all test data as a JSON file download
8. WHEN the user clicks "Import Test Data", THE Testing_Tab SHALL accept a JSON file upload and import the test data entries
9. WHEN the user clicks "Generate Test Document", THE Testing_Tab SHALL call `POST /api/composite-templates/{templateId}/preview` with the selected test data as parameters (since the template may not be ACTIVE yet, preview is used instead of generate) and display the result with a download link for the rendered document
10. THE Testing_Tab SHALL display a "Quick Test" button that combines test data selection and document generation in a single action: the user selects a test data entry from a dropdown, clicks "Quick Test", and the system calls `POST /api/composite-templates/{templateId}/preview` with the test data, then opens the generated document as a download (the browser triggers a file save dialog)

### Requirement 9: 审核与发布面板 — 提交审核

**User Story:** As a template author, I want to submit my template for review directly from the workspace and track the review status, so that I can manage the entire review-to-publish lifecycle in one place.

#### Acceptance Criteria

1. THE Review_Publish_Panel SHALL display the current template status prominently at the top with a colored status badge (DRAFT=gray, PENDING_REVIEW=orange, REVIEWED=blue, ACTIVE=green, ARCHIVED=red)
2. WHILE the template status is DRAFT or REVIEWED, THE Review_Publish_Panel SHALL display a "Submit for Review" button
3. WHEN the user clicks "Submit for Review", THE Review_Publish_Panel SHALL open a dialog collecting: reviewers (required, searchable user selector allowing multiple selection from the current tenant's user list), review level (select: Initial Review / Final Review)
4. WHEN the submit review form is confirmed, THE Review_Publish_Panel SHALL first call `POST /api/templates/{templateId}/reviews` with the reviewer IDs and review level to create the review records, then call `POST /api/templates/{templateId}/submit-review` to transition the template state to PENDING_REVIEW; IF the review creation succeeds but the state transition fails, THE Review_Publish_Panel SHALL display an error and the created reviews SHALL remain (the user can retry the state transition)
5. WHILE the template status is PENDING_REVIEW, THE Review_Publish_Panel SHALL display a review status table showing: reviewer name, review status (PENDING/APPROVED/CONDITIONAL_APPROVED/REJECTED as colored tags), review level, comment, suggestions, and creation time
6. THE Review_Publish_Panel SHALL load reviews by calling `GET /api/templates/{templateId}/reviews` with pagination parameters
7. IF all reviewers have approved the template (all review records have status APPROVED or CONDITIONAL_APPROVED), THEN THE Review_Publish_Panel SHALL display a "Ready to Publish" indicator
8. IF any reviewer rejects the template (any review record has status REJECTED), THEN THE Review_Publish_Panel SHALL display a "Review Rejected" indicator with the rejection reason, and a "Revise & Resubmit" button that transitions the template back to DRAFT status (calling `POST /api/templates/{templateId}/submit-review` is not needed — the backend TemplateReviewService already handles the PENDING_REVIEW → DRAFT transition on rejection)
9. WHEN the user clicks "Revise & Resubmit", THE Review_Publish_Panel SHALL display the rejected reviewer's comments and suggestions prominently, allowing the user to address the feedback before resubmitting

### Requirement 10: 审核与发布面板 — 自动激活与 API Key 生成

**User Story:** As a template author, I want the template to be automatically activated after review approval, with an API key auto-generated and the old version auto-deactivated, so that I do not need to perform these steps manually.

#### Acceptance Criteria

1. WHEN all reviewers approve a template (all review records reach APPROVED or CONDITIONAL_APPROVED status), THE Auto_Activation_Service SHALL automatically transition the template status through the state machine: first from PENDING_REVIEW to REVIEWED, then from REVIEWED to ACTIVE
2. WHEN the Auto_Activation_Service activates a template, THE system SHALL simply update the template's status from REVIEWED to ACTIVE; since each template is a single database record with a version history, the API endpoint `POST /api/generate/{templateId}` automatically serves the latest content — no separate "old version deactivation" is needed
3. WHEN the Auto_Activation_Service activates a template, THE Auto_Activation_Service SHALL check if an API Key exists for the current tenant; IF no API Key exists, THEN THE Auto_Activation_Service SHALL automatically create a new API Key with the name format "auto-{templateName}-{timestamp}" by calling the ApiKeyService
4. WHEN the template is activated (either automatically or manually), THE Review_Publish_Panel SHALL display the API_Endpoint_Info section showing: the API endpoint URL (`POST /api/generate/{templateId}`), the API Key prefix (masked, showing first 8 characters), a "Copy URL" button, a "Copy API Key" button, a sample cURL request, and a "Generate Document" button that opens the GenerateDialog for manual document generation from the workspace
5. THE API_Endpoint_Info section SHALL include a note explaining that the API endpoint always serves the latest active version of the template, and that callers can use `?version={versionNumber}` to pin to a specific version
6. IF the auto-activation process fails (state transition error or API Key creation error), THEN THE Auto_Activation_Service SHALL log the error, keep the template in REVIEWED status, and THE Review_Publish_Panel SHALL display an error message: "Auto-activation failed. Please activate manually."
7. WHILE the template status is REVIEWED and auto-activation has not occurred, THE Review_Publish_Panel SHALL display a manual "Activate" button as a fallback

### Requirement 11: 审核与发布面板 — 手动激活与旧版本停用

**User Story:** As a template author, I want to manually activate a reviewed template when auto-activation is not triggered, and I want the system to automatically deactivate the old active version, so that only one version is active at a time.

#### Acceptance Criteria

1. WHEN the user clicks the manual "Activate" button, THE Review_Publish_Panel SHALL call `POST /api/templates/{templateId}/activate` to transition the template to ACTIVE status
2. WHEN the activation endpoint is called, THE system SHALL transition the template from REVIEWED to ACTIVE; since the template is a single database record, activation simply updates the status field — the API endpoint `POST /api/generate/{templateId}` always serves the latest active version, so no separate "old version deactivation" is needed
3. WHEN the template is successfully activated, THE Review_Publish_Panel SHALL refresh the template data, update the status badge to ACTIVE (green), and display the API_Endpoint_Info section
4. IF the template has variable coverage below 100% at activation time, THEN THE Review_Publish_Panel SHALL display a warning dialog: "Variable coverage is below 100%. Some template variables may not render correctly. Continue activation?" with Confirm and Cancel buttons
5. WHEN the user confirms activation despite low coverage, THE Review_Publish_Panel SHALL proceed with the activation call

### Requirement 12: 跨环境导出

**User Story:** As a template author, I want to export a published template as a complete ZIP package containing all related resources, so that I can import the template into a production environment without manual reconfiguration.

#### Acceptance Criteria

1. THE Cross_Environment_Export section in the "导出/导入" tab SHALL display an "Export Complete Package" button; WHEN clicked, THE Cross_Environment_Export SHALL call `GET /api/composite-templates/{templateId}/export` and trigger a browser file download of the ZIP file
2. THE exported ZIP file SHALL contain the following items: all segment .docx files, assembly configuration JSON (segment order, conditions, page breaks), data source configurations (with sensitive credentials masked as placeholders), expression definitions, test data JSON files, and a coverage report JSON
3. THE Cross_Environment_Export section SHALL also display an "Export Config Only" button; WHEN clicked, THE Cross_Environment_Export SHALL call `GET /api/composite-templates/{templateId}/export-config` and trigger a download of the JSON configuration file only (without .docx files)
4. WHILE the template status is DRAFT, THE Cross_Environment_Export SHALL disable the "Export Complete Package" button and display a tooltip: "Template must be in PENDING_REVIEW, REVIEWED, or ACTIVE status to export"
5. THE Cross_Environment_Export section SHALL display a summary of what will be exported: segment count, data source count, expression count, test data count, and total estimated file size

### Requirement 13: 跨环境导入

**User Story:** As a template author, I want to import a template package from another environment, with conflict detection and resolution options, so that I can deploy templates across environments safely.

#### Acceptance Criteria

1. THE Cross_Environment_Import section in the "导出/导入" tab SHALL display an "Import Package" button with a file upload area accepting .zip files
2. WHEN the user uploads a ZIP file, THE Cross_Environment_Import SHALL call `POST /api/composite-templates/import` with the file and display the import result
3. WHEN the import detects naming conflicts (a template with the same name already exists), THE Cross_Environment_Import SHALL display a conflict resolution dialog with three options: "Rename" (append a suffix like "-imported"), "Overwrite" (replace the existing template), and "Cancel" (abort the import)
4. WHEN the import completes successfully, THE Cross_Environment_Import SHALL display a success summary showing: template name, segment count imported, data source count imported, expression count imported, and a "Go to Workspace" button linking to the imported template's workspace
5. IF the imported ZIP contains `data-sources.json` with masked credentials (`__CREDENTIAL_PLACEHOLDER__`), THEN THE Cross_Environment_Import SHALL display a "Configure Credentials" dialog after import, listing each data source that has placeholder credentials and prompting the user to enter the actual credentials for the target environment
6. IF the ZIP file is malformed or missing required files (assembly config JSON), THEN THE Cross_Environment_Import SHALL display an error message: "Invalid package format. The ZIP file must contain a valid assembly configuration."
7. THE Cross_Environment_Import section SHALL also display an "Import Config Only" button accepting .json files, calling the existing `POST /api/templates/import-config` endpoint

### Requirement 14: 版本管理与回滚

**User Story:** As a template author, I want to view version history and rollback to a previous version directly from the workspace, with the system automatically managing activation states, so that I can recover from problematic releases.

#### Acceptance Criteria

1. THE "设置" (Settings) tab SHALL include a "Version History" sub-section displaying template versions by calling `GET /api/templates/{templateId}/versions`, showing: version number, creation time, created by, and comment
2. WHEN the user clicks "Rollback" on a version row, THE Template_Workspace SHALL display a confirmation dialog: "Rolling back to version {versionNumber} will create a new version based on that version's content. The template status will remain unchanged — you will need to go through the review process again to publish. Continue?"
3. WHEN the user confirms the rollback, THE Template_Workspace SHALL call `POST /api/templates/{templateId}/rollback/{versionId}` to perform the rollback
4. WHEN the rollback is successful, THE Template_Workspace SHALL note that rollback creates a new version (version number increments) based on the historical version's content; the template status remains unchanged — if the user wants to re-activate the rolled-back version, they must go through the review-and-publish workflow again
5. WHEN the rollback completes, THE Template_Workspace SHALL refresh all workspace data (template info, assembly config, segments, coverage) and update the Workflow_Step_Indicator
6. THE "设置" tab SHALL include a "Version Diff" sub-section allowing the user to select two version numbers and view differences by calling `GET /api/templates/{templateId}/versions/diff`

### Requirement 15: 设置区域 — 高级功能整合

**User Story:** As a template author, I want advanced features like webhooks, watermarks, scheduled tasks, and permissions grouped in a Settings section, so that the main workflow tabs remain focused and uncluttered.

#### Acceptance Criteria

1. THE Settings_Section SHALL be the 7th tab in the Template_Workspace tab bar, labeled "设置" (Settings)
2. THE Settings_Section SHALL contain the following sub-sections rendered as collapsible panels: "Version History" (版本历史), "Version Diff" (版本对比), "Webhooks" (Webhook 配置), "Watermark & Security" (水印与安全), "Scheduled Tasks" (定时任务), "Permissions" (权限管理)
3. THE Settings_Section "Webhooks" panel SHALL embed the existing WebhookPanel component, loading webhooks by calling `GET /api/templates/{templateId}/webhooks`
4. THE Settings_Section "Watermark & Security" panel SHALL embed the existing WatermarkSecurityConfig component
5. THE Settings_Section "Scheduled Tasks" panel SHALL embed the existing ScheduledTaskManagement component
6. THE Settings_Section "Permissions" panel SHALL display template-level permissions by calling `GET /api/templates/{templateId}/permissions` and provide grant/revoke actions

### Requirement 16: 路由配置更新

**User Story:** As a developer, I want the router configuration updated to support the new workspace route and redirect legacy detail routes, so that existing bookmarks and links continue to work.

#### Acceptance Criteria

1. THE Router SHALL register a new route: `{ path: 'templates/:id/workspace', name: 'TemplateWorkspace', component: () => import('@/views/template-workspace/Index.vue') }`
2. WHEN a user navigates to `/templates/:id` (the legacy detail route), THE Router SHALL redirect to `/templates/:id/workspace`
3. WHEN a user navigates to `/composite-templates/:id` (the legacy composite detail route), THE Router SHALL redirect to `/templates/:id/workspace`
4. THE Router SHALL retain the existing `/segments/:id/editor` route for the OnlyOffice segment editor, as the Visual_Editor_Tab opens segment editors in new tabs
5. THE Router SHALL retain the existing `/templates/:id/editor` route for backward compatibility

### Requirement 17: 后端增强 — 自动激活服务

**User Story:** As a system, I want a backend service that automatically activates templates after review approval, generates API keys, and deactivates old versions, so that the publish workflow is fully automated.

#### Acceptance Criteria

1. WHEN all TemplateReview records for a template reach APPROVED or CONDITIONAL_APPROVED status, THE Auto_Activation_Service SHALL automatically perform a two-step state transition: first call `TemplateStateMachineService.transition(templateId, REVIEWED)`, then call `TemplateStateMachineService.transition(templateId, ACTIVE)`
2. WHEN the Auto_Activation_Service activates a template, THE Auto_Activation_Service SHALL ensure the template's status is set to ACTIVE; since each template is a single database record with a version history (not multiple records), there is no need to deactivate "other records" — the single template record transitions from REVIEWED to ACTIVE
3. WHEN the Auto_Activation_Service activates a template, THE Auto_Activation_Service SHALL call `ApiKeyService.createApiKey()` with an auto-generated name if no active API Key exists for the tenant
4. IF the state transition fails during auto-activation, THEN THE Auto_Activation_Service SHALL log the error with template ID and review IDs, and leave the template in its current state without throwing an exception to the review approval caller
5. THE Auto_Activation_Service SHALL be triggered from within the `TemplateReviewService.approveReview()` method after the review status is updated, by checking if all reviews for the template are now approved

### Requirement 18: 后端增强 — 完整 ZIP 导出内容扩展

**User Story:** As a system, I want the composite template ZIP export to include data sources, expressions, test data, and coverage reports in addition to segments and config, so that cross-environment imports are complete and self-contained.

#### Acceptance Criteria

1. WHEN the `GET /api/composite-templates/{templateId}/export` endpoint is called, THE CompositeImportExportService SHALL include the following additional files in the ZIP: `data-sources.json` (array of data source configurations with sensitive fields replaced by placeholder `"__CREDENTIAL_PLACEHOLDER__"`), `expressions.json` (array of expression definitions), `test-data.json` (array of all segment test data entries), and `coverage-report.json` (the coverage report from CoverageCheckService)
2. THE exported `data-sources.json` SHALL mask the following sensitive fields: database passwords, API keys in auth configs, and OAuth client secrets, replacing each with the string `"__CREDENTIAL_PLACEHOLDER__"`
3. WHEN the `POST /api/composite-templates/import` endpoint receives a ZIP containing the extended files, THE CompositeImportExportService SHALL import data sources (prompting for credential replacement), expressions, and test data in addition to segments and assembly config
4. IF the imported ZIP does not contain `data-sources.json` or `expressions.json` (older format), THEN THE CompositeImportExportService SHALL proceed with importing only the available files without error

### Requirement 19: 工作台数据加载与状态管理

**User Story:** As a template author, I want the workspace to efficiently load all required data on mount and keep it synchronized across tabs, so that switching between tabs is fast and data is always current.

#### Acceptance Criteria

1. WHEN the Template_Workspace page is mounted, THE Template_Workspace SHALL load the following data in parallel: template details (`GET /api/templates/{id}`), assembly config (`GET /api/composite-templates/{id}/assembly-config`), data sources (`GET /api/templates/{id}/data-sources`), expressions (`GET /api/templates/{id}/expressions`), coverage report (`GET /api/composite-templates/{id}/coverage`), and available state transitions (`GET /api/templates/{id}/available-transitions`)
2. WHILE any of the parallel data loading requests are in progress, THE Template_Workspace SHALL display a full-page loading skeleton
3. IF any critical data loading request fails (template details or assembly config), THEN THE Template_Workspace SHALL display an error page with a "Retry" button and the error message
4. IF any non-critical data loading request fails (data sources, expressions, coverage, or transitions), THEN THE Template_Workspace SHALL still render the workspace but display a warning banner for the failed section with a "Retry" button for that specific section
4. WHEN the user performs a mutation action in any tab (e.g., adding a data source, saving assembly config, running tests), THE Template_Workspace SHALL refresh only the affected data sections rather than reloading all data
5. THE Template_Workspace SHALL use a Pinia store (`useTemplateWorkspaceStore`) to manage shared state across tabs, including: template details, assembly config, data sources, expressions, coverage data, reviews, and workflow step completion status

### Requirement 20: 国际化支持

**User Story:** As a user of any supported language, I want all new workspace UI text to be properly internationalized, so that the workspace is usable in English, Simplified Chinese, and Traditional Chinese.

#### Acceptance Criteria

1. THE Template_Workspace SHALL use vue-i18n for all user-visible text, with keys prefixed by `workspace.` (e.g., `workspace.stepCreate`, `workspace.stepDataStructure`)
2. THE Template_Workspace SHALL add translations for all new i18n keys in three locale files: `en-US.json`, `zh-CN.json`, and `zh-TW.json`
3. THE Workflow_Step_Indicator step labels SHALL use i18n keys: `workspace.step1` through `workspace.step8`
4. THE Template_Workspace tab labels SHALL use i18n keys: `workspace.tabDataStructure`, `workspace.tabSegments`, `workspace.tabEditor`, `workspace.tabTesting`, `workspace.tabReviewPublish`, `workspace.tabExportImport`, `workspace.tabSettings`
5. THE API_Endpoint_Info section labels and the Cross_Environment_Export/Import section labels SHALL use i18n keys with the `workspace.` prefix
