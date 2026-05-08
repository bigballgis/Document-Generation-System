# 实施计划：设计阶段版面与交互重设计

## 概述

本功能按功能边界拆分为 7 个子 spec，串行执行。每个子 spec 包含独立的 tasks.md 文件。

## 子 Spec 执行顺序

| 序号 | 子 Spec | 路径 | 说明 |
|------|---------|------|------|
| 1 | backend | `.kiro/specs/design-stage-layout/backend/tasks.md` | 后端 API + 服务 + DTO 扩展 + 预置模板 + SecurityConfig |
| 2 | frontend-core | `.kiro/specs/design-stage-layout/frontend-core/tasks.md` | 类型扩展 + composables + API 层 + i18n |
| 3 | frontend-step1 | `.kiro/specs/design-stage-layout/frontend-step1/tasks.md` | 参数表设计（ParameterTableDesign + ParameterTableView） |
| 4 | frontend-step2 | `.kiro/specs/design-stage-layout/frontend-step2/tasks.md` | 片段编排（SegmentCanvas + ComponentPanel + CanvasArea + ControlNodeEditor） |
| 5 | frontend-step3 | `.kiro/specs/design-stage-layout/frontend-step3/tasks.md` | 片段详细设计（SegmentDetailDesign + ParameterSidebar + ParameterOverviewPanel） |
| 6 | frontend-integration | `.kiro/specs/design-stage-layout/frontend-integration/tasks.md` | DesignStage.vue 重写 + DesignStepIndicator + 工具栏 + 废弃组件清理 |
| 7 | testing | `.kiro/specs/design-stage-layout/testing/tasks.md` | PBT 属性测试 + 单元测试 |

## 依赖关系

```
backend ──→ frontend-core ──→ frontend-step1 ──→ frontend-step2 ──→ frontend-step3 ──→ frontend-integration ──→ testing
```

## 注意事项

- 每个子 spec 完成后执行 checkpoint 确认编译通过
- 前端子 spec 依赖 frontend-core 中的类型和 composable
- testing 子 spec 在所有实现完成后执行
- 所有子 spec 共享同一份 requirements.md 和 design.md
