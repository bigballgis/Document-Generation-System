# 实施计划：前端 Step 1 — 参数表设计

## 概述

实现设计阶段第一步"参数表设计"视图：ParameterTableDesign 主视图组件和 ParameterTableView 通用表视图组件，支持表/子表/关联表概念展示、面包屑导航、内联编辑、拖拽排序。

## Tasks

- [x] 1. 实现 ParameterTableView 通用表视图组件
  - [x] 1.1 创建 `frontend/src/views/template-workspace/components/ParameterTableView.vue`
    - Props: `parameters: ParameterDTO[]`、`parentId: number | null`、`readonly: boolean`
    - Emits: `navigate(param)`、`refresh()`
    - 将参数按 DataType 分类渲染：STRING/NUMBER/DATE/BOOLEAN → 字段行，ARRAY → 子表占位链接行（带一对多图标），OBJECT → 关联表占位链接行（带一对一图标）
    - 每个字段行显示：名称、类型、必填开关、默认值、描述、操作按钮
    - _Requirements: 2.1_
  - [x] 1.2 实现字段行内联编辑功能
    - 点击名称 → 可编辑输入框，blur 时调用 `updateParameter` API
    - 点击类型 → el-select 下拉选择 DataType
    - 点击必填 → el-switch 切换 required
    - 点击默认值 → 可编辑输入框
    - _Requirements: 2.7_
  - [x] 1.3 实现字段行删除功能
    - 点击删除按钮 → el-popconfirm 确认对话框
    - 如果被删除参数为 ARRAY/OBJECT 且有 children，确认对话框警告级联删除
    - 确认后调用 `deleteParameter` API 并 emit refresh
    - _Requirements: 2.8_
  - [x] 1.4 实现拖拽排序功能
    - 使用 sortablejs 实现字段行拖拽排序
    - 拖拽放置后更新所有同级参数的 sortOrder（调用 `batchUpdateParameters` API）
    - 统一拖拽视觉反馈：drag-source-active + drop-indicator 蓝色插入线
    - _Requirements: 2.9, 8.1, 8.2, 8.5_
  - [x] 1.5 实现"添加字段"按钮
    - 点击后弹出内联表单（参数名 + 数据类型下拉）
    - 提交后调用 `createParameter` API（parentId 自动设置为当前表的参数 ID）
    - _Requirements: 2.6_
  - [x] 1.6 实现只读模式
    - readonly 为 true 时隐藏所有编辑控件、添加字段按钮、删除按钮、拖拽手柄
    - _Requirements: 2.10_

- [x] 2. 实现 ParameterTableDesign 主视图组件
  - [x] 2.1 创建 `frontend/src/views/template-workspace/components/ParameterTableDesign.vue`
    - Props: `readonly: boolean`
    - 内部状态：`breadcrumbPath: ParameterBreadcrumbItem[]`、`currentParentId: number | null`
    - 从 `useTemplateWorkspaceStore` 获取参数数据，按 currentParentId 过滤当前层级参数
    - 渲染 TableBreadcrumb + ParameterTableView
    - _Requirements: 2.1, 2.4_
  - [x] 2.2 实现面包屑导航（TableBreadcrumb）
    - 顶部显示面包屑路径（如"主表 > items（子表）> address（关联表）"）
    - 每个层级可点击回到对应视图
    - 根级显示 i18n key `workspace.design.table.main`
    - 子表/关联表显示参数名 + 表类型标识
    - _Requirements: 2.4, 10.5_
  - [x] 2.3 实现钻入导航逻辑
    - 点击 ARRAY 占位链接 → 导航到 Sub_Table_View（更新 breadcrumbPath 和 currentParentId）
    - 点击 OBJECT 占位链接 → 导航到 Related_Table_View
    - 支持递归嵌套，最大 5 层
    - _Requirements: 2.2, 2.3, 2.5_

- [x] 3. Checkpoint — 确保参数表设计组件编译通过
  - 确保所有 tests pass，ask the user if questions arise.

## Notes

- ParameterTableView 是通用组件，主表/子表/关联表共用同一组件，通过 parentId 区分
- 参数 CRUD API 复用现有 `frontend/src/api/parameters.ts`
- 拖拽使用 sortablejs，配置统一参数（animation: 150, ghostClass: 'drag-source-active'）
