# Implementation Plan: 模板工作流阶段化 — Frontend Core

## Overview

前端核心骨架：新增 `StageName`/`StageStatus`/`StageDefinition` 类型，实现 `useStageAvailability` composable，创建 `StageIndicator` 组件，重构 `Index.vue` 从 el-tabs 切换为四阶段动态组件架构。此子 Spec 完成后，四个阶段组件暂用占位符，由 frontend-stages 子 Spec 填充。

## Tasks

- [x] 1. 类型定义和 Composable
  - [x] 1.1 修改 `frontend/src/types/workspace.ts`，新增阶段类型
    - 新增 `StageName = 'design' | 'test' | 'approval' | 'publish'`
    - 新增 `StageStatus = 'not_started' | 'in_progress' | 'completed' | 'readonly'`
    - 新增 `StageDefinition` 接口（name, label, status, clickable）
    - 保留现有 `WorkflowStep`、`StepKey`、`TabName` 接口（标记 `@deprecated`，frontend-cleanup 子 Spec 移除）
    - _Requirements: 1.1, 1.2_
  - [x] 1.2 创建 `frontend/src/composables/useStageAvailability.ts`
    - 实现设计文档中定义的 `useStageAvailability(store)` 函数
    - 返回 `stages`（computed StageDefinition[]）、`activeStage`（computed StageName）、`isReadonly`（computed boolean）
    - 阶段可用性逻辑严格按需求 2 的六条 AC 实现：DRAFT→{设计,测试}可用、DRAFT+100%覆盖→审批额外可用、PENDING_REVIEW→审批活跃+设计测试只读、REVIEWED→发布活跃、ACTIVE→发布已完成、ARCHIVED→全部只读
    - 阶段完成条件严格按需求 3 的四条 AC 实现
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1, 3.2, 3.3, 3.4_
  - [x] 1.3 编写属性测试：阶段可用性与完成状态一致性 (Property 1)
    - **Property 1: 阶段可用性与完成状态一致性**
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1, 3.2, 3.3, 3.4**
    - 路径: `frontend/src/__tests__/stageAvailability.property.test.ts`
    - 使用 fast-check 生成随机模板状态（5 种）、覆盖率百分比（0-100）、参数数量（0-N）、片段配置（enabled/filePath 组合）
    - 验证设计文档 Property 1 的全部 9 条子属性
    - 最小迭代 100 次

- [x] 2. StageIndicator 组件
  - [x] 2.1 创建 `frontend/src/views/template-workspace/components/StageIndicator.vue`
    - Props: `stages: StageDefinition[]`, `currentStage: StageName`
    - Emits: `stage-click(stage: StageName)`
    - 四个圆形图标从左到右排列，间距均匀
    - 状态样式：未开始→灰色圆圈、进行中→蓝色圆圈+CSS pulse 动画、已完成→绿色勾选、只读→绿色勾选（同已完成）、不可用→灰色+`pointer-events: none`
    - 相邻阶段连接线：已完成之间→绿色实线，未完成之间→灰色虚线
    - 每个阶段下方显示 i18n 标签（`workspace.stage.{name}`）
    - 不可点击阶段（`clickable=false`）不触发 `stage-click` 事件
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_
  - [x] 2.2 编写 StageIndicator 单元测试
    - 路径: `frontend/src/__tests__/StageIndicator.test.ts`
    - 测试：渲染四个阶段、各状态样式类名、点击可用阶段触发事件、点击不可用阶段不触发事件、连接线样式
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [x] 3. Checkpoint — 类型和组件编译验证
  - 确保 `npx vue-tsc --noEmit` 类型检查通过
  - 确保 `npx vitest --run src/__tests__/stageAvailability.property.test.ts src/__tests__/StageIndicator.test.ts` 测试通过
  - ask the user if questions arise.

- [x] 4. 重构 Index.vue 核心架构
  - [x] 4.1 重构 `frontend/src/views/template-workspace/Index.vue`
    - 移除 `el-tabs` 和所有 `el-tab-pane`
    - 移除 `WorkflowStepIndicator` 组件引用和 import
    - 移除 `useWorkflowSteps` composable 引用
    - 移除 `activeTab` ref 和 `handleBeforeLeave` 函数
    - 新增 `import StageIndicator` 和 `import { useStageAvailability }`
    - 新增 `currentStage` ref（StageName 类型，默认 'design'）
    - 新增 `stageAvailability = useStageAvailability(store)`
    - 新增 `stageDataLoaded` reactive 对象（test: false, approval: false）
    - 新增 `stageLoading` ref
    - 实现 `handleStageClick(stage)` 函数：按需加载阶段数据（test→refreshTestCases+refreshCoverage，approval→refreshReviews），加载时显示骨架屏
    - 模板区域结构改为：Header → ACTIVE banner → StageIndicator → 骨架屏/阶段组件
    - 使用 `<KeepAlive><component :is="currentStageComponent" /></KeepAlive>` 动态渲染阶段
    - `currentStageComponent` computed 根据 `currentStage` 返回对应组件
    - _Requirements: 1.3, 1.6, 15.1, 15.2, 15.3, 15.4_
  - [x] 4.2 创建四个阶段占位组件
    - `frontend/src/views/template-workspace/components/DesignStage.vue` — 占位，显示 "设计阶段（待实现）"
    - `frontend/src/views/template-workspace/components/TestStage.vue` — 占位，显示 "测试阶段（待实现）"
    - `frontend/src/views/template-workspace/components/ApprovalStage.vue` — 占位，显示 "审批阶段（待实现）"
    - `frontend/src/views/template-workspace/components/PublishStage.vue` — 占位，显示 "发布阶段（待实现）"
    - 每个组件接收 `readonly: boolean` prop
    - _Requirements: 1.3_
  - [x] 4.3 新增路由 `/templates/:id/api`
    - 在 `frontend/src/router/index.ts` 中添加 `TemplateApiManagement` 路由
    - 组件路径: `@/views/template-api/Index.vue`
    - 创建 `frontend/src/views/template-api/Index.vue` 占位组件（显示 "API 管理页面（待实现）"）
    - _Requirements: 13.1_

- [x] 5. Final checkpoint — 核心架构验证
  - 确保 `npx vue-tsc --noEmit` 类型检查通过
  - 确保 `npx vitest --run` 所有测试通过（包括现有测试无回归）
  - ask the user if questions arise.

## Notes

- 此子 Spec 完成后，Index.vue 已从 el-tabs 切换为四阶段架构，但阶段组件为占位符
- 现有 Tab 组件（ParameterTableTab、SegmentArrangementTab 等）暂不删除，由 frontend-cleanup 子 Spec 处理
- `useWorkflowSteps.ts` 和 `WorkflowStepIndicator.vue` 暂保留文件，由 frontend-cleanup 子 Spec 删除
