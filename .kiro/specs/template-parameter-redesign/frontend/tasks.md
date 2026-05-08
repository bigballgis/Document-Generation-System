# Implementation Plan: Frontend — 参数表低代码管理界面 + 旧前端代码移除

## Overview

创建 ParameterTableTab（替代 DataStructureTab）及所有子组件（TreeTable、PreviewPanel、ValidationRulesPopover、DerivedExpressionEditor、VisualExpressionBuilder、ParameterTemplateMenu），创建 parameters API 模块，更新 Store/Router/i18n，移除旧 DataSource/Expression 前端代码。

## Tasks

- [x] 1. Frontend API module and TypeScript types
  - [x] 1.1 Create src/api/parameters.ts
    - getParameters, getParametersFlat, createParameter, updateParameter, deleteParameter, scanPlaceholders, autoCreateParameters, getParameterSchema
    - Use request.ts Axios instance
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.13, 3.1, 3.3, 7.1_
  - [x] 1.2 Create TypeScript types in src/types/parameter.ts
    - ParameterDTO, ValidationRules, ScanResultDTO, PlaceholderInfo, ParameterSchemaDTO, CoverageReport (new three-dimensional), CreateParameterRequest, UpdateParameterRequest
    - _Requirements: 1.1, 3.1, 7.1, 8.1_

- [x] 2. Update templateWorkspace store
  - [x] 2.1 Replace dataSources/expressions state with parameters state
    - Remove dataSources ref, expressions ref, refreshDataSources, refreshExpressions
    - Add parameters ref<ParameterDTO[]>, refreshParameters action
    - Update initWorkspace to load parameters instead of dataSources/expressions
    - _Requirements: 10.4, 10.25_
  - [x] 2.2 Update useWorkflowSteps composable
    - Replace `store.dataSources.length > 0 || store.expressions.length > 0` with `store.parameters.length > 0`
    - _Requirements: 10.20_

- [x] 3. ParameterTableTab — main container
  - [x] 3.1 Create ParameterTableTab.vue
    - Two-panel layout: left TreeTable, right PreviewPanel
    - Toolbar: "添加参数", "从模板扫描", "插入参数模板", "全部展开", "全部折叠", batch operations ("全选", "批量删除", "批量设置必填")
    - Coverage summary bar at top showing Branch/Loop/Parameter/Overall coverage
    - Replace DataStructureTab in TemplateWorkspace Index.vue
    - _Requirements: 4.1, 4.2, 4.8, 4.18, 4.32, 4.33, 4.43_

- [x] 4. ParameterTreeTable — tree table with inline editing
  - [x] 4.1 Create ParameterTreeTable.vue
    - Tree table with columns: name, parameter_type (tag badge), data_type (dropdown), required (toggle), default_value (inline), description (inline), validation_rules (summary badge), actions
    - Expand/collapse for OBJECT and ARRAY types with connector lines/indentation
    - Full Parameter_Path as tooltip on each row
    - Drag handle for sort_order reordering within same parent scope
    - _Requirements: 4.3, 4.4, 4.5, 4.6, 4.7, 4.26, 4.27_
  - [x] 4.2 Implement inline editing
    - Click name → inline text input (Enter confirm, Esc cancel)
    - Click data_type → inline dropdown (STRING/NUMBER/DATE/BOOLEAN/ARRAY/OBJECT)
    - Click required → inline toggle switch
    - Click default_value → type-appropriate input (text/number/date picker/toggle)
    - Click description → inline text input
    - Confirmation dialog when changing OBJECT/ARRAY to leaf type (warns about child deletion)
    - _Requirements: 4.9, 4.10, 4.11, 4.12, 4.13, 4.14_
  - [x] 4.3 Implement context menu and keyboard shortcuts
    - Right-click: 复制参数, 粘贴参数, 删除参数, 添加同级参数, 添加子参数 (only for OBJECT/ARRAY)
    - Tab → new sibling row, Enter → confirm and next field, Esc → cancel edit
    - "添加子参数" action button on OBJECT/ARRAY rows (auto-sets parent_id)
    - _Requirements: 4.17, 4.28, 4.29, 4.30, 4.31_

- [x] 5. Checkpoint — Ensure TreeTable renders and basic editing works
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. ParameterPreviewPanel — real-time preview
  - [x] 6.1 Create ParameterPreviewPanel.vue
    - Three tabs: "JSON Schema", "示例请求体", "占位符匹配"
    - JSON Schema tab: render parameter tree as JSON Schema (type, required, properties, items, description, constraints)
    - 示例请求体 tab: generate sample JSON with example values (STRING→"string", NUMBER→0, DATE→"2024-01-01", BOOLEAN→false, use default_value when defined)
    - 占位符匹配 tab: green checkmark (matched), red cross (unmatched), orange warning (unused)
    - Update all tabs within 500ms of any parameter change
    - _Requirements: 4.21, 4.22, 4.23, 4.24, 4.25_

- [x] 7. ValidationRulesPopover and DerivedExpressionEditor
  - [x] 7.1 Create ValidationRulesPopover.vue
    - Popover panel anchored to parameter row
    - Dynamic form controls based on data_type: toggles for not_null/not_blank, number inputs for min_length/max_length/min/max/min_items/max_items, text input for pattern, tag input for enum_values, text input for custom_message
    - Show only applicable rules per data_type compatibility matrix
    - Summary badge showing active rule count (e.g., "3 条规则")
    - _Requirements: 4.34, 4.35, 4.36_
  - [x] 7.2 Create DerivedExpressionEditor.vue
    - Expandable panel below DERIVED parameter row
    - Two modes: "可视化模式" (VisualExpressionBuilder) and "高级模式" (code editor with syntax highlighting)
    - "测试" button to evaluate expression with sample values
    - _Requirements: 4.37, 4.42_
  - [x] 7.3 Create VisualExpressionBuilder.vue
    - Step-by-step: select source parameter → select operator → select second operand
    - Operator categories: arithmetic, comparison, string, date, logical
    - "添加步骤" button for chaining operations as visual pipeline
    - Generate expression_text (JS or Excel) and show in read-only preview
    - _Requirements: 4.38, 4.39, 4.40_
  - [x] 7.4 Implement autocomplete in advanced code editor
    - Autocomplete dropdown listing available parameter names filtered by typed prefix
    - _Requirements: 4.41_

- [x] 8. ParameterTemplateMenu and scan dialog
  - [x] 8.1 Create ParameterTemplateMenu.vue
    - Dropdown menu with predefined templates: "地址" (OBJECT: province, city, district, street, zipCode), "联系人" (OBJECT: name, phone, email), "商品列表" (ARRAY: name, price, quantity, subtotal as DERIVED)
    - Insert complete tree structure at current scope
    - _Requirements: 4.19, 4.20_
  - [x] 8.2 Implement scan dialog in ParameterTableTab
    - "从模板扫描" button → call scan API → show dialog with matched/unmatched/unused in tree view
    - Option to auto-create missing parameter hierarchy
    - _Requirements: 4.15, 4.16_

- [x] 9. Remove old frontend code
  - [x] 9.1 Relocate KeyValueEditor.vue to shared components
    - Move frontend/src/views/data-sources/KeyValueEditor.vue → frontend/src/components/KeyValueEditor.vue
    - Update SegmentArrangementTab.vue import path
    - _Requirements: 10.19_
  - [x] 9.2 Delete data-sources frontend files
    - Delete: src/api/data-sources.ts, src/views/data-sources/ directory (after KeyValueEditor relocation)
    - _Requirements: 10.3_
  - [x] 9.3 Delete expressions frontend files
    - Delete: src/api/expressions.ts, src/views/templates/components/ExpressionFormDialog.vue, src/views/templates/components/ExpressionPanel.vue
    - _Requirements: 10.24_
  - [x] 9.4 Delete DataStructureTab.vue
    - _Requirements: 10.5_
  - [x] 9.5 Update router/index.ts — remove data-sources route
    - _Requirements: 10.16_
  - [x] 9.6 Update MainLayout.vue — remove data-sources navigation entry and navTitleMap entry
    - _Requirements: 10.17_
  - [x] 9.7 Update ExportImportTab.vue — remove updateDataSource import and credential config logic
    - _Requirements: 10.18_
  - [x] 9.8 Update Detail.vue — remove ExpressionPanel import and "expressions" tab pane
    - _Requirements: 10.27_
  - [x] 9.9 Update i18n locale files — remove dataSource/expression keys, add parameter keys
    - Update en-US.json, zh-CN.json, zh-TW.json
    - _Requirements: 10.22, 10.28_
  - [x] 9.10 Remove old frontend test files and mocks
    - Delete DataSourceFormDialog.test.ts, DataStructureTab.test.ts, expressionPanel.property.test.ts
    - Update any test files that mock data-sources or expressions API
    - _Requirements: 10.21_

- [x] 10. Frontend property tests
  - [x] 10.1 Write parameterPath.property.test.ts
    - **Property 6: Parameter path computation** — fast-check test for frontend path utility
    - **Validates: Requirements 1.15**
  - [x] 10.2 Write dataTypeRecommendation.property.test.ts
    - **Property 15: Data type recommendation from placeholder name** — fast-check test for keyword matching
    - **Validates: Requirements 4.16**
  - [x] 10.3 Write jsonSchemaGeneration.property.test.ts
    - **Property 24: JSON Schema generation** — fast-check test for OBJECT→properties, ARRAY→items
    - **Validates: Requirements 4.23**
  - [x] 10.4 Write sampleBodyGeneration.property.test.ts
    - **Property 26: Sample request body generation** — fast-check test for nested JSON with example values
    - **Validates: Requirements 4.24**

- [x] 11. Frontend component tests
  - [x] 11.1 Write ParameterTableTab.test.ts
    - Test two-panel layout, toolbar buttons, coverage summary bar
    - _Requirements: 4.1, 4.2, 4.43_
  - [x] 11.2 Write ParameterTreeTable.test.ts
    - Test tree rendering, inline editing, expand/collapse, drag-and-drop
    - _Requirements: 4.3, 4.9, 4.10, 4.26_
  - [x] 11.3 Write ParameterPreviewPanel.test.ts
    - Test three preview tabs rendering
    - _Requirements: 4.21, 4.23, 4.24, 4.25_
  - [x] 11.4 Write ValidationRulesPopover.test.ts
    - Test dynamic form controls per data_type
    - _Requirements: 4.34, 4.35_
  - [x] 11.5 Write DerivedExpressionEditor.test.ts
    - Test two modes, expression preview
    - _Requirements: 4.37, 4.40_

- [x] 12. Final checkpoint — Ensure all frontend tests pass
  - Ensure all tests pass, ask the user if questions arise.
