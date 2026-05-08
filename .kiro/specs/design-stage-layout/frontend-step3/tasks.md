# 实施计划：前端 Step 3 — 片段详细设计

## 概述

实现设计阶段第三步"片段详细设计"视图：SegmentDetailDesign 主视图、ParameterSidebar 参数侧边栏（含搜索过滤）、ParameterOverviewPanel 参数总览抽屉。

## Tasks

- [x] 1. 实现 ParameterSidebar 参数侧边栏
  - [x] 1.1 创建 `frontend/src/views/template-workspace/components/ParameterSidebar.vue`
    - Props: `collapsed`、`readonly`
    - Emits: `update:collapsed`、`insert-variable(paramPath)`、`insert-loop(arrayName)`、`insert-condition(expr)`
    - 三个可折叠区域：Parameter_List_Section、Loop_Block_Section、Condition_Block_Section
    - 顶部搜索输入框 + 收起/展开按钮
    - _Requirements: 5.3, 5.11_
  - [x] 1.2 实现 Parameter_List_Section
    - 以扁平列表展示所有参数（包括嵌套参数的完整路径，如 `company.name`）
    - 每个参数显示为可点击的标签卡片
    - 点击标签 → emit `insert-variable(param.parameterPath)`，插入 `{parameterPath}` 占位符
    - 支持按数据类型过滤：类型筛选标签（STRING、NUMBER、DATE、BOOLEAN、ARRAY、OBJECT）
    - _Requirements: 5.4, 5.5, 9.3_
  - [x] 1.3 实现 Loop_Block_Section
    - 列出所有 ARRAY 类型参数，显示为可点击的循环块标签
    - 点击标签 → emit `insert-loop(param.name)`，插入 `{#arrayName}\n\n{/arrayName}`
    - _Requirements: 5.6, 5.7_
  - [x] 1.4 实现 Condition_Block_Section
    - 提供条件表达式输入框和"添加条件"按钮
    - 用户输入条件表达式后生成可点击的条件块标签
    - 点击标签 → emit `insert-condition(expr)`，插入 `{#if expr}\n\n{/if}`
    - _Requirements: 5.8, 5.9_
  - [x] 1.5 实现搜索与过滤功能
    - 搜索框实时过滤三个区域中的参数（按名称模糊搜索，不区分大小写）
    - 搜索或过滤结果为空时显示"无匹配参数"提示
    - _Requirements: 9.1, 9.2, 9.4_
  - [x] 1.6 实现 Inline_Parameter_Creator
    - "+ 新增参数"按钮，点击展开内联表单（参数名 + 数据类型下拉）
    - 提交后调用 `createParameter` API 创建参数并刷新侧边栏列表
    - _Requirements: 5.10_
  - [x] 1.7 实现只读模式
    - readonly 为 true 时隐藏点击插入功能、新增参数入口
    - _Requirements: 5.12_

- [x] 2. 实现 ParameterOverviewPanel 参数总览抽屉
  - [x] 2.1 创建 `frontend/src/views/template-workspace/components/ParameterOverviewPanel.vue`
    - Props: `visible`
    - Emits: `update:visible`、`navigate-to-param(paramId)`
    - 使用 el-drawer 从右侧滑出
    - 提供两种展示模式切换：树形视图（缩进层级展示参数名和类型）和 JSON Schema 视图
    - 只读展示，不提供编辑功能
    - 点击参数名 → 关闭抽屉并 emit navigate-to-param
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 3. 实现 SegmentDetailDesign 主视图组件
  - [x] 3.1 创建 `frontend/src/views/template-workspace/components/SegmentDetailDesign.vue`
    - Props: `readonly: boolean`
    - 左右分栏布局：OnlyOffice_Editor 主区域（约 70%）+ ParameterSidebar（约 30%）
    - 编辑器上方显示片段选择器（el-select），列出所有已启用且有 filePath 的片段
    - 选择片段后调用 `getSegmentOnlyOfficeUrl` API 加载编辑器
    - 编辑器上方显示内容隔离提示条（`workspace.design.isolation.bodyHint`）
    - callbackUrl 指向片段级回调端点，附带 `contentType=body`
    - _Requirements: 5.1, 5.2, 6.1_
  - [x] 3.2 实现参数插入集成
    - 监听 ParameterSidebar 的 insert-variable/insert-loop/insert-condition 事件
    - 通过 OnlyOfficeEditor 组件的 expose 方法（insertVariable/insertLoop/insertCondition）在光标位置插入文本
    - 插入后短暂高亮显示已插入内容
    - _Requirements: 5.5, 5.7, 5.9, 8.4_
  - [x] 3.3 实现空状态和侧边栏折叠
    - 无已启用片段时显示空状态提示"请先在片段编排步骤中添加片段"
    - 侧边栏收起时编辑器扩展为 100% 宽度
    - _Requirements: 5.13, 5.11_

- [x] 4. Checkpoint — 确保片段详细设计组件编译通过
  - 确保所有 tests pass，ask the user if questions arise.

## Notes

- ParameterSidebar 从 useTemplateWorkspaceStore 获取参数数据
- 参数扁平化列表使用 parameterPath 字段（如 `company.address.city`）
- OnlyOffice 编辑器复用现有 `OnlyOfficeEditor.vue` 组件，通过 expose 方法插入文本
- 片段级 OnlyOffice URL 使用新增的 `getSegmentOnlyOfficeUrl` API
