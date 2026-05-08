# 实施计划：前端集成 — DesignStage 重写 + 步骤指示器 + 工具栏 + 清理

## 概述

重写 DesignStage.vue 为三步容器，实现 DesignStepIndicator 步骤指示器，整合统一工具栏，清理废弃组件。这是将前面三个步骤组件串联起来的集成层。

## Tasks

- [x] 1. 实现 DesignStepIndicator 步骤指示器
  - [x] 1.1 创建 `frontend/src/views/template-workspace/components/DesignStepIndicator.vue`
    - Props: `currentStep: DesignStepName`、`stepStatuses: Record<DesignStepName, StepStatus>`
    - Emits: `update:currentStep(step)`
    - 显示三个子步骤：参数表设计、片段编排、片段详细设计（使用 i18n key `workspace.design.step.*`）
    - 每个步骤显示状态：未开始（灰色）、进行中（蓝色高亮）、已完成（绿色勾选）
    - 点击步骤切换视图
    - _Requirements: 1.1, 1.2, 1.4_

- [x] 2. 重写 DesignStage.vue
  - [x] 2.1 完全重写 `frontend/src/views/template-workspace/components/DesignStage.vue`
    - Props: `readonly: boolean`（接口不变）
    - 移除当前的"编辑器 + ParameterDrawer + SegmentPopover + SettingsPopover"布局
    - 新布局：统一工具栏 → DesignStepIndicator → 子步骤视图区域 → 上一步/下一步按钮
    - 使用 useDesignStep composable 管理步骤状态
    - 根据 currentStep 动态渲染 ParameterTableDesign / SegmentCanvas / SegmentDetailDesign
    - _Requirements: 1.1, 1.4, 1.5, 1.7_
  - [x] 2.2 实现统一工具栏
    - 位于 DesignStepIndicator 上方
    - 左侧："导入 ZIP"按钮（仅 DRAFT 状态可见，复用现有 importCompositeFromZip 逻辑）
    - 右侧："参数总览"按钮（三步均可见，点击打开 ParameterOverviewPanel）+ 片段选择器（仅第三步可见）+ 齿轮设置按钮（复用 SettingsPopover）
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_
  - [x] 2.3 实现只读模式
    - 模板状态不为 DRAFT 时隐藏所有编辑操作按钮
    - 三个子步骤均以只读模式展示（传递 readonly prop）
    - _Requirements: 1.6_
  - [x] 2.4 实现"上一步"/"下一步"导航按钮
    - 子步骤视图区域下方显示导航按钮
    - 使用 i18n key `workspace.design.prev` / `workspace.design.next`
    - _Requirements: 1.5_
  - [x] 2.5 添加全局拖拽样式
    - 在 DesignStage.vue 中定义统一拖拽 CSS 类
    - `.drag-source-active`（opacity: 0.5）、`.drop-indicator`（蓝色插入线）、`.drop-forbidden`（禁止光标）
    - _Requirements: 8.1_

- [x] 3. 清理废弃组件
  - [x] 3.1 删除或标记废弃组件
    - 删除 `SegmentPopover.vue`（被 SegmentCanvas 替代）
    - 删除 `SegmentArrangementTab.vue`（被 SegmentCanvas + CanvasArea 替代）
    - 删除 `ParameterDrawer.vue`（被 ParameterSidebar 替代，确认仅在 DesignStage.vue 中引用）
    - 更新相关 import 引用
    - _Requirements: 1.7_

- [x] 4. Checkpoint — 确保集成后编译通过且功能完整
  - 确保所有 tests pass，ask the user if questions arise.

## Notes

- DesignStage.vue 的 Props 接口不变（仅 readonly: boolean），对外兼容
- 统一工具栏复用现有的 importCompositeFromZip API 和 SettingsPopover 组件
- 废弃组件删除前需确认无其他引用
- ParameterOverviewPanel 的 navigate-to-param 事件需要联动 useDesignStep 切换到参数表设计步骤并钻入对应层级
