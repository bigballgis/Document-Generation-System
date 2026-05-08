# Implementation Plan: 模板工作流阶段化 — Frontend Stages

## Overview

实现四个阶段组件的完整功能：DesignStage（编辑器+参数抽屉+片段弹出+设置弹出+工具栏）、TestStage（覆盖率条+测试数据表单+实时预览+智能引导）、ApprovalStage（审批时间线+只读预览+提交/返回按钮）、PublishStage（摘要卡片+激活/导出/新版本按钮）。同时实现 API 管理独立页面。

## Tasks

- [x] 1. DesignStage 组件实现
  - [x] 1.1 实现 `ParameterDrawer` 子组件
    - 路径: `frontend/src/views/template-workspace/components/ParameterDrawer.vue`
    - Props: `collapsed: boolean`, `readonly: boolean`
    - Emits: `update:collapsed`
    - 复用 `ParameterTableTab` 核心功能（参数 CRUD、树形表格、扫描占位符、JSON 导入），以抽屉面板形式呈现
    - 顶部显示"扫描占位符"按钮，调用扫描 API 并高亮新发现的参数
    - 收起时显示为窄条图标，编辑器扩展为 100%
    - `readonly=true` 时隐藏所有编辑操作按钮，仅显示参数列表
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_
  - [x] 1.2 实现 `SegmentPopover` 子组件
    - 路径: `frontend/src/views/template-workspace/components/SegmentPopover.vue`
    - 使用 `el-drawer` 实现弹出面板
    - 复用 `SegmentArrangementTab` 核心功能（拖拽排序、启用/禁用、条件表达式、数据作用域配置）
    - 关闭时检查未保存更改，有更改则显示 `ElMessageBox.confirm` 确认对话框
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_
  - [x] 1.3 实现 `SettingsPopover` 子组件
    - 路径: `frontend/src/views/template-workspace/components/SettingsPopover.vue`
    - 使用 `el-drawer` 实现弹出面板
    - 复用 `SettingsTab` 子组件（VersionHistoryPanel、PermissionPanel、WebhookConfig）
    - _Requirements: 6.3, 6.4_
  - [x] 1.4 实现 `DesignStage.vue` 完整功能（替换占位符）
    - 路径: `frontend/src/views/template-workspace/components/DesignStage.vue`
    - Props: `readonly: boolean`
    - 布局：顶部工具栏 + 主区域（编辑器 70% + ParameterDrawer 30%）
    - 工具栏按钮：片段编排（触发 SegmentPopover）、导入 ZIP（文件选择器→调用 `POST /api/composite-templates/import`）、齿轮设置（触发 SettingsPopover）
    - 编辑器主区域复用 `OnlyOfficeEditor` 组件实现内联编辑
    - 多片段模板时编辑器区域顶部显示片段选择器
    - `readonly=true` 时隐藏"导入 ZIP"按钮，ParameterDrawer 进入只读模式
    - _Requirements: 4.1, 4.2, 4.3, 5.1, 5.2, 6.1, 6.2, 6.3, 6.4, 6.5_

- [x] 2. Checkpoint — DesignStage 编译验证
  - 确保 `npx vue-tsc --noEmit` 类型检查通过
  - ask the user if questions arise.

- [x] 3. TestStage 组件实现
  - [x] 3.1 创建 `TestDataForm` 子组件和 `buildFormFields` 工具函数
    - 工具函数路径: `frontend/src/composables/useTestDataForm.ts`
    - 实现 `buildFormFields(parameters: ParameterDTO[]): FormField[]` 纯函数
    - 类型映射：STRING→el-input（无 enum）或 el-select（有 enum）、NUMBER→el-input-number（无 enum）或 el-select（有 enum）、BOOLEAN→el-switch、DATE→el-date-picker、OBJECT→可折叠嵌套表单组、ARRAY→动态增减列表
    - required=true 的参数显示必填标记，提交时验证非空
    - 有 defaultValue 的参数作为表单初始值
    - 新增 `FormField` 和 `TestDataFormState` 类型到 `frontend/src/types/testDataForm.ts`
    - _Requirements: 8.1, 8.2, 8.3, 8.4_
  - [x] 3.2 创建 `TestDataForm.vue` 组件
    - 路径: `frontend/src/views/template-workspace/components/TestDataForm.vue`
    - Props: `parameters: ParameterDTO[]`, `readonly: boolean`
    - Emits: `update:formData`, `submit`
    - 根据 `buildFormFields` 结果递归渲染表单控件
    - 底部提供"查看 JSON"切换按钮，在表单模式和 JSON 编辑模式之间切换
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_
  - [x] 3.3 编写属性测试：参数树到表单控件树同构映射 (Property 2)
    - **Property 2: 参数树到表单控件树同构映射**
    - **Validates: Requirements 8.1, 8.2, 8.3, 8.4**
    - 路径: `frontend/src/__tests__/testDataForm.property.test.ts`
    - 使用 fast-check 生成随机参数树（任意深度 OBJECT/ARRAY 嵌套、任意 DataType、任意 required/defaultValue/enum_values）
    - 验证 `buildFormFields` 输出与参数树同构、类型映射正确、required/defaultValue 传递正确
    - 最小迭代 100 次
  - [x] 3.4 创建 `CoverageBar.vue` 子组件
    - 路径: `frontend/src/views/template-workspace/components/CoverageBar.vue`
    - 调用 `GET /api/templates/{id}/coverage` 获取 `CoverageReport`（三维度）
    - 显示分支覆盖率（branchCoverage）、循环覆盖率（loopCoverage）、参数覆盖率（parameterCoverage）三个进度条
    - 三维度全部 100% 时显示绿色成功状态
    - _Requirements: 7.2, 7.3_
  - [x] 3.5 实现 `TestStage.vue` 完整功能（替换占位符）
    - 路径: `frontend/src/views/template-workspace/components/TestStage.vue`
    - Props: `readonly: boolean`
    - 布局：顶部 CoverageBar → 智能引导区域 → 左右分栏（TestDataForm 40% + 文档预览 60%）
    - 智能引导：列出未覆盖分支，点击条目高亮相关参数字段并显示提示文本
    - 预览区域：500ms 防抖后调用预览接口更新
    - 预览失败时显示错误信息+重试按钮，保留表单数据
    - _Requirements: 7.1, 7.2, 7.3, 9.1, 9.2, 9.3, 9.4_

- [x] 4. Checkpoint — TestStage 编译验证
  - 确保 `npx vue-tsc --noEmit` 类型检查通过
  - 确保 `npx vitest --run src/__tests__/testDataForm.property.test.ts` 测试通过
  - ask the user if questions arise.

- [x] 5. ApprovalStage 和 PublishStage 组件实现
  - [x] 5.1 实现 `ApprovalStage.vue` 完整功能（替换占位符）
    - 路径: `frontend/src/views/template-workspace/components/ApprovalStage.vue`
    - Props: `readonly: boolean`
    - 布局：左侧 ApprovalTimeline + 右侧只读模板快照预览
    - ApprovalTimeline 使用 `el-timeline` + `el-timeline-item`，每条记录显示审批人、状态标签、评论、时间戳
    - 状态标签颜色：通过→绿色、有条件通过→橙色、拒绝→红色、待审核→灰色
    - DRAFT + 覆盖率 100% 时显示"提交审核"按钮（调用 `submitReview` API）
    - 审核被拒绝时显示"返回修改"按钮，点击后刷新模板数据并 emit `stage-change('design')` 切换到设计阶段
    - 审核全部通过后（检测到状态变为 REVIEWED/ACTIVE）自动刷新并 emit `stage-change('publish')`
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 14.2_
  - [x] 5.2 实现 `PublishStage.vue` 完整功能（替换占位符）
    - 路径: `frontend/src/views/template-workspace/components/PublishStage.vue`
    - Props: `readonly: boolean`
    - 显示 PublishSummaryCard：版本号、参数数量、覆盖率百分比、审核状态
    - REVIEWED 状态：摘要卡片下方居中显示大尺寸"激活"按钮（调用 `POST /api/templates/{id}/activate`）
    - ACTIVE 状态：隐藏"激活"按钮，显示"导出 ZIP 包"按钮（调用 `GET /api/composite-templates/{id}/export`）和"编辑为新版本"按钮（调用 `createDraftVersion` → 刷新 → emit `stage-change('design')`）
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 14.1_
  - [x] 5.3 更新 `Index.vue` 处理阶段组件的 `stage-change` 事件
    - 监听 DesignStage/TestStage/ApprovalStage/PublishStage 的 `stage-change` 事件
    - 收到事件后调用 `handleStageClick(newStage)` 切换阶段
    - _Requirements: 10.6, 10.7, 11.6, 14.1, 14.2_

- [x] 6. API 管理独立页面
  - [x] 6.1 实现 `frontend/src/views/template-api/Index.vue` 完整功能（替换占位符）
    - 显示模板名称 + "返回模板列表"按钮
    - ACTIVE 版本列表（调用 `getTemplateVersions`）
    - API 端点信息（复用 `ApiEndpointInfo` 组件）
    - API Key 管理区域
    - 调用统计区域
    - _Requirements: 13.1, 13.2, 13.3, 13.5_
  - [x] 6.2 在模板列表页添加"API"图标按钮
    - 修改 `frontend/src/views/templates/Index.vue`，在模板行操作列添加 API 图标按钮
    - 点击导航到 `/templates/:id/api`
    - _Requirements: 13.4_

- [x] 7. Final checkpoint — 全部阶段组件验证
  - 确保 `npx vue-tsc --noEmit` 类型检查通过
  - 确保 `npx vitest --run` 所有测试通过
  - ask the user if questions arise.

## Notes

- 依赖 frontend-core 子 Spec 完成后执行（需要 StageIndicator、useStageAvailability、Index.vue 新架构就绪）
- 依赖 backend 子 Spec 完成后执行（导出约束需要后端先就绪）
- 阶段组件的 `stage-change` 事件用于跨阶段导航（审核拒绝→设计、审核通过→发布、新版本→设计）
- OnlyOffice 编辑器集成需手动测试，自动测试不覆盖
