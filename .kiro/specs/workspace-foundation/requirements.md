# Requirements Document — 工作台骨架 (Workspace Foundation)

## Introduction

工作台骨架（Workspace Foundation）是模板工作台（Template Workspace）功能的 Phase 1 实现，从父级 spec（`.kiro/specs/template-workspace/`）中提取核心骨架需求。本阶段聚焦于：

1. 侧边栏导航重构 — 将分散的模板相关菜单整合为"模板管理"分组
2. 模板列表页增强 — 创建向导对话框 + 工作台导航入口
3. 工作台页面骨架 — 整体布局、步骤指示器、7 个标签页（占位组件）
4. 路由配置 — 新增工作台路由 + 旧路由重定向
5. Pinia Store — 并行加载全部工作台数据，为后续 Phase 共享状态
6. 国际化支持 — 所有新增 UI 文本的 i18n key

本阶段的 7 个标签页均为占位组件，显示"Coming in Phase N"提示信息。步骤指示器完整实现（基于真实数据计算完成状态）。Pinia store 加载全部数据（template、assembly config、data sources、expressions、coverage、transitions），确保 P2/P3/P4 可直接替换占位组件而无需修改 store。

## Glossary

- **Template_Workspace**: 模板工作台页面，位于 `/templates/:id/workspace`，提供引导式模板编辑工作流的统一界面
- **Workflow_Step_Indicator**: 工作流步骤指示器组件，以水平步骤条形式展示 8 个工作流步骤的完成状态
- **Sidebar_Navigation**: 主布局侧边栏导航菜单，位于 MainLayout.vue 中的 el-menu 组件
- **Template_Creation_Wizard**: 模板创建向导对话框，引导用户通过分步表单创建新的组合模板
- **Templates_List_Page**: 模板列表页面，位于 `/templates`，展示模板列表并提供创建、搜索、筛选功能
- **Workspace_Store**: Pinia store (`useTemplateWorkspaceStore`)，管理工作台页面的全部共享状态
- **Router**: Vue Router 路由配置，位于 `frontend/src/router/index.ts`
- **Placeholder_Tab**: 占位标签页组件，在 P1 阶段显示"Coming in Phase N"提示，后续阶段替换为真实实现

## Requirements

### Requirement 1: 侧边栏导航重构

**User Story:** As a template author, I want a simplified sidebar navigation that groups template-related functions under a single "Template Management" entry, so that I can access the template workspace without navigating through multiple scattered menu items.

#### Acceptance Criteria

1. THE Sidebar_Navigation SHALL display a top-level menu item labeled "模板管理" (Template Management) that replaces the current separate "Templates", "Segments", "Components", and "Composite Templates" menu items
2. WHEN the user clicks the "模板管理" menu item, THE Sidebar_Navigation SHALL expand to show two sub-items: "模板列表" (Template List) linking to `/templates` and "片段库" (Segment Library) linking to `/segments`
3. THE Sidebar_Navigation SHALL remove the standalone "Data Sources" top-level menu item; data source management is primarily accessed within the Template_Workspace context; the existing `/data-sources` route SHALL remain functional for direct URL access, and a global "Data Sources" view within the Admin page is planned for Phase 2
4. THE Sidebar_Navigation SHALL retain the following top-level menu items unchanged: Dashboard, Documents, Tasks, Market, Admin, Audit
5. WHEN the user navigates to a Template_Workspace route (`/templates/:id/workspace`), THE Sidebar_Navigation SHALL highlight the "模板管理" parent menu item as active

### Requirement 2: 模板列表页增强

**User Story:** As a template author, I want the template list page to support creating composite templates via a guided wizard and to navigate directly to the workspace, so that I can start the guided workflow immediately after creation.

#### Acceptance Criteria

1. WHEN the user clicks the "Create" button on the template list page, THE Template_Creation_Wizard SHALL open as a dialog overlay
2. THE Template_Creation_Wizard SHALL collect the following fields in step 1: template name (required, max 200 characters), description (optional, max 500 characters), category (optional, tree-select from existing categories), tags (optional, multi-select from existing tags; tags are associated after template creation via the Tag API `POST /api/tags/{tagId}/templates/{templateId}`)
3. THE Template_Creation_Wizard SHALL collect the following field in step 2: output format (select from WORD/PDF, default WORD)
4. WHEN the user completes the wizard and clicks "Create", THE Template_Creation_Wizard SHALL call the `POST /api/composite-templates` endpoint with the collected data
5. WHEN the composite template is created successfully, THE Template_Creation_Wizard SHALL navigate the user to `/templates/{newTemplateId}/workspace`
6. IF the `POST /api/composite-templates` endpoint returns an error, THEN THE Template_Creation_Wizard SHALL display the error message from the response and remain on the current wizard step
7. WHEN the user clicks a template name in the template list table, THE Templates_List_Page SHALL navigate to `/templates/{id}/workspace` instead of the current `/templates/{id}` detail page
8. THE Templates_List_Page SHALL display a "Workspace" action button in the actions column for each template row, linking to `/templates/{id}/workspace`

### Requirement 3: 工作台页面骨架 — 整体布局与步骤指示器

**User Story:** As a template author, I want a unified workspace page with a visual step indicator showing my progress through the template creation workflow, so that I know what step I am on and what to do next.

#### Acceptance Criteria

1. THE Template_Workspace SHALL be accessible at route `/templates/:id/workspace` and SHALL load the composite template data by calling `GET /api/templates/{id}`
2. IF the loaded template has `templateType = 'SINGLE'`, THEN THE Template_Workspace SHALL display a migration prompt: "This is a single-file template. Convert to composite template to use the full workspace." with a "Convert" button that calls `POST /api/templates/{id}/migrate-to-composite`; after successful migration, THE Template_Workspace SHALL navigate to `/templates/{compositeTemplateId}/workspace` using the `compositeTemplateId` from the migration result (since migration creates a new composite template with a different ID and archives the original)
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


#### P1 Placeholder Tab Specification

10. THE Template_Workspace SHALL render each of the 7 tabs as a placeholder component in Phase 1, displaying the following messages:
    - Tab 1 "数据结构": "Data structure management coming in Phase 2"
    - Tab 2 "片段编排": "Segment arrangement coming in Phase 2"
    - Tab 3 "编辑": "Visual editor coming in Phase 2"
    - Tab 4 "测试": "Testing coming in Phase 3"
    - Tab 5 "审核与发布": "Review & publish coming in Phase 3"
    - Tab 6 "导出/导入": "Export/import coming in Phase 4"
    - Tab 7 "设置": "Settings coming in Phase 4"
11. EACH Placeholder_Tab SHALL render as a centered card with an icon, the phase message, and a brief description of the planned functionality, using i18n keys for all text

### Requirement 4: 路由配置更新

**User Story:** As a developer, I want the router configuration updated to support the new workspace route and redirect legacy detail routes, so that existing bookmarks and links continue to work.

#### Acceptance Criteria

1. THE Router SHALL register a new route: `{ path: 'templates/:id/workspace', name: 'TemplateWorkspace', component: () => import('@/views/template-workspace/Index.vue') }`
2. WHEN a user navigates to `/templates/:id` (the legacy detail route), THE Router SHALL redirect to `/templates/:id/workspace`
3. WHEN a user navigates to `/composite-templates/:id` (the legacy composite detail route), THE Router SHALL redirect to `/templates/:id/workspace`
4. THE Router SHALL retain the existing `/segments/:id/editor` route for the OnlyOffice segment editor, as the Visual_Editor_Tab opens segment editors in new tabs
5. THE Router SHALL retain the existing `/templates/:id/editor` route for backward compatibility

### Requirement 5: 工作台数据加载与状态管理

**User Story:** As a template author, I want the workspace to efficiently load all required data on mount and keep it synchronized across tabs, so that switching between tabs is fast and data is always current.

#### Acceptance Criteria

1. WHEN the Template_Workspace page is mounted, THE Workspace_Store SHALL load the following data in parallel: template details (`GET /api/templates/{id}`) and assembly config (`GET /api/composite-templates/{id}/assembly-config`) as critical requests using `Promise.all`, and data sources (`GET /api/templates/{id}/data-sources`), expressions (`GET /api/templates/{id}/expressions`), coverage report (`GET /api/composite-templates/{id}/coverage`), and available state transitions (`GET /api/templates/{id}/available-transitions`) as non-critical requests using `Promise.allSettled`
2. WHILE any of the parallel data loading requests are in progress, THE Template_Workspace SHALL display a full-page loading skeleton
3. IF any critical data loading request fails (template details or assembly config), THEN THE Template_Workspace SHALL display an error page with a "Retry" button and the error message
4. IF any non-critical data loading request fails (data sources, expressions, coverage, or transitions), THEN THE Template_Workspace SHALL still render the workspace but display a warning banner for the failed section with a "Retry" button for that specific section
5. WHEN the user performs a mutation action in any tab (e.g., adding a data source, saving assembly config, running tests), THE Workspace_Store SHALL refresh only the affected data sections rather than reloading all data
6. THE Workspace_Store SHALL use a Pinia store (`useTemplateWorkspaceStore`) to manage shared state across tabs, including: template details, assembly config, data sources, expressions, coverage data, available transitions, and workflow step completion status
7. THE Workspace_Store SHALL expose individual refresh actions (`refreshTemplate`, `refreshAssemblyConfig`, `refreshDataSources`, `refreshExpressions`, `refreshCoverage`, `refreshTransitions`) for granular data updates from tab components in future phases

### Requirement 6: 国际化支持

**User Story:** As a user of any supported language, I want all new workspace UI text to be properly internationalized, so that the workspace is usable in English, Simplified Chinese, and Traditional Chinese.

#### Acceptance Criteria

1. THE Template_Workspace SHALL use vue-i18n for all user-visible text, with keys prefixed by `workspace.` (e.g., `workspace.stepCreate`, `workspace.stepDataStructure`)
2. THE Template_Workspace SHALL add translations for all new i18n keys in three locale files: `en-US.json`, `zh-CN.json`, and `zh-TW.json`
3. THE Workflow_Step_Indicator step labels SHALL use i18n keys: `workspace.step1` through `workspace.step8`
4. THE Template_Workspace tab labels SHALL use i18n keys: `workspace.tabDataStructure`, `workspace.tabSegments`, `workspace.tabEditor`, `workspace.tabTesting`, `workspace.tabReviewPublish`, `workspace.tabExportImport`, `workspace.tabSettings`
5. THE Placeholder_Tab messages SHALL use i18n keys with the `workspace.placeholder.*` prefix (e.g., `workspace.placeholder.dataStructure`, `workspace.placeholder.segments`)
6. THE Sidebar_Navigation new labels SHALL use i18n keys: `nav.templateManagement` for the parent menu, `nav.templateList` for the template list sub-item, `nav.segmentLibrary` for the segment library sub-item
7. THE Template_Creation_Wizard labels SHALL use i18n keys with the `workspace.wizard.*` prefix (e.g., `workspace.wizard.title`, `workspace.wizard.step1Title`, `workspace.wizard.step2Title`)
