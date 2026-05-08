# Requirements Document — 工作台数据与片段标签页 (Workspace Data & Segments)

## Introduction

本文档是模板工作台（Template Workspace）Phase 2 的需求规格，从父级 spec（`.kiro/specs/template-workspace/`）中提取以下需求：

- 父级 Requirement 4: 数据结构标签页 — 数据源管理
- 父级 Requirement 5: 数据结构标签页 — 表达式管理
- 父级 Requirement 6: 片段编排标签页
- 父级 Requirement 7: 可视化编辑标签页

Phase 1（`.kiro/specs/workspace-foundation/`）已实现工作台骨架，包括：
- 工作台主页面 `Index.vue`（含 7 个占位标签页）
- Pinia store `useTemplateWorkspaceStore`（已加载 dataSources、expressions、assemblyConfig、coverage）
- `WorkflowStepIndicator` 步骤指示器（含完成状态计算）
- `useWorkflowSteps` composable
- 路由配置与侧边栏导航

Phase 2 将 3 个占位标签页替换为真实实现：
1. Tab 1 "数据结构" → `DataStructureTab.vue`（数据源 + 表达式管理）
2. Tab 2 "片段编排" → `SegmentArrangementTab.vue`（拖拽片段编排）
3. Tab 3 "编辑" → `VisualEditorTab.vue`（OnlyOffice 编辑器集成）

同时包含一项后端变更：`SegmentController.createSegment` 的 `file` 参数改为可选，`SegmentService.createSegment` 在 `file == null` 时创建空 .docx 模板。

## Glossary

- **Data_Structure_Tab**: 数据结构标签页组件 (`DataStructureTab.vue`)，在模板工作台中管理数据源和表达式的统一界面，替换 P1 占位组件
- **Segment_Arrangement_Tab**: 片段编排标签页组件 (`SegmentArrangementTab.vue`)，提供拖拽排序、内联创建片段、添加已有片段的界面，替换 P1 占位组件
- **Visual_Editor_Tab**: 可视化编辑标签页组件 (`VisualEditorTab.vue`)，展示片段列表并集成 OnlyOffice 编辑器入口，替换 P1 占位组件
- **Workspace_Store**: Pinia store (`useTemplateWorkspaceStore`)，P1 已实现，管理工作台全部共享状态，提供 `refreshDataSources`、`refreshExpressions`、`refreshAssemblyConfig`、`refreshCoverage` 等刷新方法
- **Segment_Details**: 片段详情数据，通过 `GET /api/composite-templates/{templateId}/segments` 获取，包含片段名称、类型、描述、更新时间等信息；由于 `AssemblySegmentEntry` 仅包含 `segmentId` 等配置字段，不包含片段名称和类型，因此需要额外加载片段详情用于 UI 展示
- **DataSourceFormDialog**: 已有数据源表单对话框组件 (`frontend/src/views/data-sources/DataSourceFormDialog.vue`)，支持 HTTP_API / DATABASE / INTERNAL_SYSTEM 三种类型的配置表单
- **KeyValueEditor**: 已有键值对编辑器组件 (`frontend/src/views/data-sources/KeyValueEditor.vue`)，接受 `Record<string, string>` 类型的 `v-model`，可复用于片段配置中的 data scope mapper
- **ExpressionFormDialog**: 已有表达式表单对话框组件 (`frontend/src/views/templates/components/ExpressionFormDialog.vue`)，支持 JAVASCRIPT / EXCEL 两种表达式类型的创建与编辑
- **ExpressionPanel**: 已有表达式面板组件 (`frontend/src/views/templates/components/ExpressionPanel.vue`)，包含表达式表格、增删改验证功能；注意：此组件在 `onMounted` 时自行调用 API 加载数据，不适合直接嵌入工作台（工作台从 store 读取数据），因此 Data_Structure_Tab 仅复用 `ExpressionFormDialog`，表达式表格部分需在 Data_Structure_Tab 中重新实现
- **Assembly_Config**: 组合模板的片段编排配置，包含 `segments` 数组，每个条目含 `segmentId`、`position`、`enabled`、`pageBreakBefore`、`lockedVersion`、`conditionExpression`、`dataScope`
- **useAssemblyConfig**: 已有组装配置 composable (`frontend/src/composables/useAssemblyConfig.ts`)，提供 `segments` 响应式状态、`addSegment`、`removeSegment`、`updateSegment`、`undo/redo`（最多 20 步历史）、`serialize/deserialize` 方法；Segment_Arrangement_Tab 应复用此 composable 管理本地编排状态
- **useSegmentDrag**: 已有拖拽排序 composable (`frontend/src/composables/useSegmentDrag.ts`)，提供 `reorder`、`moveUp`、`moveDown`、`onDrop` 方法，基于 HTML5 原生拖拽 API；Segment_Arrangement_Tab 应复用此 composable 实现拖拽排序
- **Segment_Type**: 片段类型枚举，可选值为 COVER、TOC、CHAPTER、TABLE、SIGNATURE、LEGAL、APPENDIX
- **SegmentController**: 后端片段控制器 (`SegmentController.java`)，提供片段 CRUD、克隆、版本管理、锁定等 REST 端点
- **SegmentService**: 后端片段服务 (`SegmentService.java`)，处理片段业务逻辑，包括文件上传至 MinIO、空 .docx 创建

## Requirements

### Requirement 1: 数据结构标签页 — 整体布局与数据源管理

**User Story:** 作为模板作者，我希望在工作台的数据结构标签页中直接管理数据源，无需跳转到独立的数据源页面并手动选择模板。

**追溯:** 父级 spec Requirement 4

#### Acceptance Criteria

1. THE Data_Structure_Tab SHALL display two sub-sections separated by a visual divider: "数据源" (Data Sources) at the top and "表达式" (Expressions) at the bottom
2. WHEN the Data_Structure_Tab is activated, THE Data_Structure_Tab SHALL read data sources from the Workspace_Store (`store.dataSources`) and expressions from the Workspace_Store (`store.expressions`), without making additional API calls since the store has already loaded the data during workspace initialization
3. THE Data_Structure_Tab SHALL display data sources in an `el-table` with columns: name, type (HTTP_API / DATABASE / INTERNAL_SYSTEM rendered as colored `el-tag`), priority, cache status (enabled/disabled icon), last updated time (`updatedAt`), and action buttons (Edit, Test Connection, Delete)
4. WHEN the user clicks "Add Data Source", THE Data_Structure_Tab SHALL open the existing DataSourceFormDialog component with `templateId` set to the current template ID from the Workspace_Store
5. WHEN the user clicks "Edit" on a data source row, THE Data_Structure_Tab SHALL open the DataSourceFormDialog with the selected data source passed as the `dataSource` prop
6. WHEN the DataSourceFormDialog emits a `saved` event (after successful create or update), THE Data_Structure_Tab SHALL call `store.refreshDataSources()` to update the shared state
7. WHEN the user clicks "Test Connection" on a data source row, THE Data_Structure_Tab SHALL call `POST /api/data-sources/{id}/test` and display the result (success or failure with response time) in an `ElMessage` notification or a result popover
8. WHEN the user clicks "Delete" on a data source row, THE Data_Structure_Tab SHALL show an `ElMessageBox.confirm` confirmation dialog; upon confirmation, THE Data_Structure_Tab SHALL disable the row's action buttons (to prevent double-click), call `DELETE /api/data-sources/{id}`, then call `store.refreshDataSources()` to update the shared state; IF the delete call fails, THE Data_Structure_Tab SHALL re-enable the action buttons and display an `ElMessage.error` notification
9. IF the data source list is empty, THEN THE Data_Structure_Tab SHALL display an empty state card with an icon and a prompt message using i18n key `workspace.dataSource.empty`
10. IF the `store.refreshDataSources()` call fails, THEN THE Data_Structure_Tab SHALL display an `ElMessage.error` notification with the error message and retain the previous data source list in the table

### Requirement 2: 数据结构标签页 — 表达式管理

**User Story:** 作为模板作者，我希望在同一个数据结构标签页中管理表达式（JavaScript / Excel 公式），以便在统一视图中定义计算字段。

**追溯:** 父级 spec Requirement 5

#### Acceptance Criteria

1. THE Data_Structure_Tab SHALL display expressions in an `el-table` below the data sources section with columns: name, expression type (JAVASCRIPT / EXCEL rendered as colored `el-tag`), expression content (truncated to 80 characters with `el-tooltip` showing full content on hover), execution order, created time (`createdAt`), and action buttons (Edit, Validate, Delete)
2. WHEN the user clicks "Add Expression", THE Data_Structure_Tab SHALL open the existing ExpressionFormDialog component with `templateId` set to the current template ID from the Workspace_Store
3. WHEN the user clicks "Edit" on an expression row, THE Data_Structure_Tab SHALL open the ExpressionFormDialog with the selected expression passed as the `data` prop
4. WHEN the ExpressionFormDialog emits a `saved` event (after successful create or update), THE Data_Structure_Tab SHALL call `store.refreshExpressions()` to update the shared state
5. WHEN the user clicks "Validate" on an expression row, THE Data_Structure_Tab SHALL call `POST /api/expressions/validate` with the expression content and type, and display the validation result: success as `ElMessage.success`, failure as `ElMessage.error` with the error message and error position
6. WHEN the user clicks "Delete" on an expression row, THE Data_Structure_Tab SHALL show an `ElMessageBox.confirm` confirmation dialog; upon confirmation, THE Data_Structure_Tab SHALL disable the row's action buttons, call `DELETE /api/expressions/{id}`, then call `store.refreshExpressions()` to update the shared state; IF the delete call fails, THE Data_Structure_Tab SHALL re-enable the action buttons and display an `ElMessage.error` notification
7. IF the expression list is empty, THEN THE Data_Structure_Tab SHALL display an empty state card with an icon and a prompt message using i18n key `workspace.expression.empty`

### Requirement 3: 片段编排标签页 — 片段列表与拖拽排序

**User Story:** 作为模板作者，我希望在工作台中直接创建、编排和配置片段，无需跳转到独立页面即可构建文档结构。

**追溯:** 父级 spec Requirement 6

#### Acceptance Criteria

1. WHEN the Segment_Arrangement_Tab is activated, THE Segment_Arrangement_Tab SHALL read the assembly configuration from the Workspace_Store (`store.assemblyConfig`) and segment details from the Workspace_Store (`store.segments`) to obtain segment names, types, and other metadata (since `AssemblySegmentEntry` only contains `segmentId` and configuration fields); THE tab SHALL merge assembly config entries with segment details by matching `segmentId`, and render each segment as a draggable card showing: position number, segment name (from segment details, or "Unknown Segment #{segmentId}" if the segment detail is not found), segment type tag (from segment details), enabled status indicator, and action buttons (Expand Config, Remove)
2. WHEN the user drags a segment card to a new position, THE Segment_Arrangement_Tab SHALL use the existing `useSegmentDrag` composable to handle drag events and call `useAssemblyConfig.setSegments()` with the reordered result, recalculating all `position` fields to match array indices, and visually reflecting the new arrangement without calling the backend API
3. WHEN the user clicks "Save Arrangement", THE Segment_Arrangement_Tab SHALL call `PUT /api/composite-templates/{templateId}/assembly-config` with the `useAssemblyConfig.serialize()` result, then call `store.refreshAssemblyConfig()`, `store.refreshSegments()`, and `store.refreshCoverage()` to update the shared state, and finally call `useAssemblyConfig.deserialize(store.assemblyConfig)` to re-sync the local state and clear the undo/redo history
4. IF the `PUT /api/composite-templates/{templateId}/assembly-config` call fails, THEN THE Segment_Arrangement_Tab SHALL display an `ElMessage.error` notification with the error message and retain the local edited state so the user can retry
5. THE Segment_Arrangement_Tab SHALL support keyboard shortcuts using the existing `useSegmentDrag` composable: Alt+ArrowUp calls `moveUp()` to move the selected segment up one position, Alt+ArrowDown calls `moveDown()` to move the selected segment down one position
6. THE Segment_Arrangement_Tab SHALL use the existing `useAssemblyConfig` composable to maintain a local undo/redo history for all local changes (drag reorder, inline config modifications, segment removal); Ctrl+Z SHALL undo the last change, Ctrl+Shift+Z SHALL redo the last undone change; the undo/redo history SHALL be cleared when the arrangement is saved successfully (via `deserialize` which resets history)
7. IF the assembly configuration contains zero segments, THEN THE Segment_Arrangement_Tab SHALL display an empty state card with an icon and a prompt message using i18n key `workspace.segment.empty`
8. WHILE the Segment_Arrangement_Tab has unsaved local changes, THE tab SHALL display a visual indicator (e.g., a dot or badge on the "Save Arrangement" button) and THE tab SHALL track a `hasUnsavedChanges` flag; WHEN the user attempts to switch to another tab while `hasUnsavedChanges` is true, THE Template_Workspace SHALL show an `ElMessageBox.confirm` dialog: "You have unsaved arrangement changes. Discard changes?" with Confirm (discard) and Cancel (stay) buttons; additionally, WHEN the user attempts to navigate away from the workspace route (e.g., clicking "Back to List"), THE Template_Workspace SHALL use Vue Router's `onBeforeRouteLeave` guard to show the same confirmation dialog

### Requirement 4: 片段编排标签页 — 新建与添加片段

**User Story:** 作为模板作者，我希望能在片段编排标签页中直接创建新片段或添加已有片段到编排配置中。

**追溯:** 父级 spec Requirement 6 (AC 4-7)

#### Acceptance Criteria

1. WHEN the user clicks "Add Segment", THE Segment_Arrangement_Tab SHALL open a panel (drawer or popover) with two options: "Create New Segment" and "Add Existing Segment"
2. WHEN the user selects "Create New Segment", THE Segment_Arrangement_Tab SHALL display an inline form collecting: segment name (required, max 200 characters), segment type (select from COVER / TOC / CHAPTER / TABLE / SIGNATURE / LEGAL / APPENDIX), description (optional), and a file upload for the .docx file (optional — labeled with i18n hint that an empty template will be created if no file is provided)
3. WHEN the new segment form is submitted, THE Segment_Arrangement_Tab SHALL call `POST /api/segments` with the form data and file (if provided); upon success, THE Segment_Arrangement_Tab SHALL add the created segment to the local assembly configuration at the end position with `enabled = true` and `pageBreakBefore = false`, then save the updated configuration by calling `PUT /api/composite-templates/{templateId}/assembly-config`, call `store.refreshAssemblyConfig()` and `store.refreshSegments()` to sync the shared state, and finally call `useAssemblyConfig.deserialize(store.assemblyConfig)` to re-sync the local state; IF the segment creation succeeds but the assembly config save fails, THE Segment_Arrangement_Tab SHALL display an `ElMessage.warning` notification explaining that the segment was created but not yet added to the arrangement, and the user can add it via "Add Existing Segment"
4. WHEN the user selects "Add Existing Segment", THE Segment_Arrangement_Tab SHALL display a searchable list by calling `GET /api/segments` with search parameters (keyword, segmentType, page size 20), filtering out segments that are already present in the current assembly configuration (by `segmentId`), and allowing the user to select one or more segments via checkboxes; the list SHALL support pagination if results exceed the page size
5. WHEN the user confirms the existing segment selection, THE Segment_Arrangement_Tab SHALL add each selected segment to the local assembly configuration at the end position with `enabled = true` and `pageBreakBefore = false`, then save the updated configuration by calling `PUT /api/composite-templates/{templateId}/assembly-config`, call `store.refreshAssemblyConfig()` and `store.refreshSegments()` to sync the shared state, and finally call `useAssemblyConfig.deserialize(store.assemblyConfig)` to re-sync the local state

### Requirement 5: 片段编排标签页 — 片段配置与移除

**User Story:** 作为模板作者，我希望能展开每个片段卡片查看和修改其配置选项，以及从编排中移除片段。

**追溯:** 父级 spec Requirement 6 (AC 8-9)

#### Acceptance Criteria

1. WHEN the user clicks the expand arrow on a segment card, THE Segment_Arrangement_Tab SHALL show inline configuration options: enabled toggle (`el-switch`), page break before toggle (`el-switch`), locked version number input (`el-input-number` with a "Use Latest" checkbox — when checked, `lockedVersion` is set to `null` meaning always use the latest version; when unchecked, the user can enter a specific version number), condition expression input (`el-input` with placeholder), and data scope mapper (reusing the existing `KeyValueEditor` component with `v-model` bound to the segment's `dataScope: Record<string, string>`)
2. WHEN the user modifies any inline configuration option, THE Segment_Arrangement_Tab SHALL update the local assembly configuration state; the changes SHALL be persisted only when the user clicks "Save Arrangement"
3. WHEN the user clicks "Remove" on a segment card, THE Segment_Arrangement_Tab SHALL show an `ElMessageBox.confirm` confirmation dialog; upon confirmation, THE Segment_Arrangement_Tab SHALL remove the segment from the local assembly configuration (the segment entity itself is not deleted), recalculate all `position` fields, and mark the arrangement as having unsaved changes

### Requirement 6: 可视化编辑标签页

**User Story:** 作为模板作者，我希望从工作台直接打开 OnlyOffice 编辑器编辑任意片段，并能预览组合文档效果。

**追溯:** 父级 spec Requirement 7

#### Acceptance Criteria

1. WHEN the Visual_Editor_Tab is activated, THE Visual_Editor_Tab SHALL read the assembly configuration from the Workspace_Store (`store.assemblyConfig`) and segment details from the Workspace_Store (`store.segments`) to obtain segment names, types, and update times (since `AssemblySegmentEntry` only contains `segmentId` and configuration fields); THE tab SHALL merge assembly config entries with segment details by matching `segmentId`, and display a list of all segments with columns: position number, segment name (or "Unknown Segment" if detail not found), segment type tag, last updated time (`updatedAt` from segment details), lock status icon, and an "Open Editor" button
2. WHEN the user clicks "Open Editor" on a segment row, THE Visual_Editor_Tab SHALL open the OnlyOffice editor for that segment in a new browser tab by calling `window.open('/segments/{segmentId}/editor', '_blank')`
3. THE Visual_Editor_Tab SHALL display a "Preview Composite" button; WHEN clicked, THE Visual_Editor_Tab SHALL call `POST /api/composite-templates/{templateId}/preview` (via `previewCompositeTemplate` API function) and open the returned `previewUrl` in a new browser tab; IF the API call fails, THE Visual_Editor_Tab SHALL display an `ElMessage.error` notification
4. THE Visual_Editor_Tab SHALL display a "Selective Preview" button; WHEN clicked, THE Visual_Editor_Tab SHALL allow the user to select specific segments via checkboxes, then call `POST /api/composite-templates/{templateId}/preview/selective` with the selected segment IDs; IF no segments are selected, THE "Selective Preview" submit button SHALL be disabled
5. WHEN the Visual_Editor_Tab is activated, THE Visual_Editor_Tab SHALL check the lock status of each segment by calling `GET /api/segments/{segmentId}/lock` for each segment in the assembly configuration, using `Promise.allSettled` for parallel execution; IF any lock check fails, THE Visual_Editor_Tab SHALL treat that segment as unlocked (no lock icon displayed)
6. WHILE a segment has an active lock held by another user, THE Visual_Editor_Tab SHALL display a lock icon and the locking user's name (`lockedByUsername`) next to that segment row; the "Open Editor" button SHALL remain enabled (the editor page itself handles lock acquisition)
7. IF the assembly configuration contains zero segments, THEN THE Visual_Editor_Tab SHALL display an empty state card with an icon and a prompt message using i18n key `workspace.editor.empty`, and a link to switch to the "片段编排" tab

### Requirement 7: 后端变更 — SegmentController file 参数改为可选

**User Story:** 作为系统，我需要支持在不上传 .docx 文件的情况下创建片段，以便工作台中的内联创建功能可以创建空片段。

**追溯:** 父级 spec 设计文档 Section 4.3

#### Acceptance Criteria

1. THE SegmentController `createSegment` endpoint SHALL change the `@RequestPart("file")` annotation to `@RequestPart(value = "file", required = false)` to accept requests without a file part
2. WHEN the `file` parameter is `null`, THE SegmentService `createSegment` method SHALL create an empty .docx template file by implementing the same `generateEmptyDocx` logic as `TemplateService` (the minimal Open XML ZIP structure with `[Content_Types].xml`, `_rels/.rels`, and `word/document.xml`); the implementation MAY either extract the shared logic into a utility class or duplicate it in `SegmentService`; the generated file SHALL be uploaded to MinIO at path `segments/{tenantId}/{uuid}_{sanitizedSegmentName}.docx` (where `sanitizedSegmentName` replaces non-alphanumeric characters with underscores), and the resulting file path SHALL be used as the segment's `filePath`
3. WHEN the `file` parameter is provided and non-empty, THE SegmentService `createSegment` method SHALL upload the provided file to MinIO using the existing `uploadSegmentFile` logic (no behavior change)
4. IF the empty .docx creation or upload fails, THEN THE SegmentService SHALL throw a `BusinessException` with error code `INTERNAL_ERROR` and HTTP status 500
5. THE frontend `createSegment` API function in `frontend/src/api/segments.ts` SHALL be updated to accept `file` as an optional parameter (`file?: File`); WHEN `file` is undefined, THE function SHALL send the `FormData` without the `file` part

### Requirement 8: 工作台 Index.vue 集成 — 替换占位标签页与 Store 扩展

**User Story:** 作为模板作者，我希望工作台的前三个标签页显示真实功能而非占位提示。

**追溯:** 父级 spec Requirement 3 (AC 7, 10)

#### Acceptance Criteria

1. THE Template_Workspace `Index.vue` SHALL replace the placeholder `PlaceholderTab` for tab "数据结构" (`dataStructure`) with the `DataStructureTab` component
2. THE Template_Workspace `Index.vue` SHALL replace the placeholder `PlaceholderTab` for tab "片段编排" (`segments`) with the `SegmentArrangementTab` component
3. THE Template_Workspace `Index.vue` SHALL replace the placeholder `PlaceholderTab` for tab "编辑" (`editor`) with the `VisualEditorTab` component
4. THE Template_Workspace `Index.vue` SHALL retain the `PlaceholderTab` for the remaining 4 tabs: "测试" (Phase 3), "审核与发布" (Phase 3), "导出/导入" (Phase 4), "设置" (Phase 4)
5. WHEN the user performs a mutation in any of the three new tabs (add/edit/delete data source, add/edit/delete expression, save arrangement), THE Workspace_Store step completion status SHALL automatically recalculate via the existing `useWorkflowSteps` composable, and the Workflow_Step_Indicator SHALL reflect the updated completion state
6. THE Workspace_Store SHALL be extended to include a `segments` state (`Segment[]`) loaded by calling `GET /api/composite-templates/{templateId}/segments` during `initWorkspace`, and a `refreshSegments()` action; this data provides segment names, types, and update times needed by the Segment_Arrangement_Tab and Visual_Editor_Tab (since `AssemblySegmentEntry` only contains `segmentId` and configuration fields)
7. THE `segments` API call SHALL be treated as a non-critical request (using `Promise.allSettled`); IF it fails, THE Workspace_Store SHALL set `warnings.segments` with the error message

### Requirement 9: 国际化 — P2 新增 i18n key

**User Story:** 作为任何支持语言的用户，我希望 P2 新增的所有 UI 文本都已正确国际化。

**追溯:** 父级 spec Requirement 20

#### Acceptance Criteria

1. THE Data_Structure_Tab, Segment_Arrangement_Tab, and Visual_Editor_Tab SHALL use vue-i18n for all user-visible text
2. THE Template_Workspace SHALL add translations for all P2 new i18n keys in three locale files: `en-US.json`, `zh-CN.json`, and `zh-TW.json`
3. THE new i18n keys SHALL use the following prefixes: `workspace.dataSource.*` for data source section labels, `workspace.expression.*` for expression section labels, `workspace.segment.*` for segment arrangement labels, `workspace.editor.*` for visual editor labels
4. THE empty state messages SHALL use i18n keys: `workspace.dataSource.empty`, `workspace.expression.empty`, `workspace.segment.empty`, `workspace.editor.empty`
5. THE action button labels SHALL reuse existing i18n keys where available (e.g., `common.edit`, `common.delete`, `common.save`) and define new keys only for P2-specific labels
6. THE Workspace_Store warning key `workspace.warning.segments` SHALL be added for the case when segment details loading fails
