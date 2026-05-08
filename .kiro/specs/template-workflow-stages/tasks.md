# Implementation Plan: 模板工作流阶段化重构

## Overview

将模板工作区从 7 个平铺 Tab + 8 步指示器重构为四阶段工作流（设计 → 测试 → 审批 → 发布）。按功能边界拆分为 4 个子 Spec，串行执行。

## 子 Spec 执行顺序

| 序号 | 子 Spec | 路径 | 说明 |
|------|---------|------|------|
| 1 | backend | `backend/tasks.md` | ErrorCode + exportAsZip 状态检查（极小） |
| 2 | frontend-core | `frontend-core/tasks.md` | 类型 + useStageAvailability + StageIndicator + Index.vue 重构 |
| 3 | frontend-stages | `frontend-stages/tasks.md` | 四个阶段组件 + API 管理页面 |
| 4 | frontend-cleanup | `frontend-cleanup/tasks.md` | 移除旧组件 + i18n + 测试更新 |

## 依赖关系

```
backend ──┐
           ├──→ frontend-stages ──→ frontend-cleanup
frontend-core ─┘
```

- `backend` 和 `frontend-core` 可并行执行
- `frontend-stages` 依赖 `backend` + `frontend-core` 完成
- `frontend-cleanup` 依赖 `frontend-stages` 完成
