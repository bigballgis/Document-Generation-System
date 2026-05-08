# Implementation Plan: Template Parameter Redesign

## Overview

本功能因超过 30 个子任务，已按功能边界拆分为 4 个子 Spec，串行执行。详见 [execution-order.md](./execution-order.md)。

## Sub-Specs

- [x] 1. Backend Core — 数据库迁移 + Entity + DTO + Repository + ParameterService + TemplateScanService + ParameterController
  - 详见 [backend-core/tasks.md](./backend-core/tasks.md)
  - 覆盖需求: Req 1, 2, 3, 9

- [x] 2. Backend Validation & Coverage — ParameterValidationService + 覆盖率重构 + DocumentGeneratorService 简化 + Parameter Schema
  - 详见 [backend-validation-coverage/tasks.md](./backend-validation-coverage/tasks.md)
  - 覆盖需求: Req 5, 6, 7, 8

- [x] 3. Backend Cleanup — 移除旧代码 (DataSource/Expression/TemplateVariable) + 更新依赖服务 + 配置清理
  - 详见 [backend-cleanup/tasks.md](./backend-cleanup/tasks.md)
  - 覆盖需求: Req 10

- [x] 4. Frontend — ParameterTableTab + 前端组件 + Store/Router/i18n 更新 + 旧前端代码移除
  - 详见 [frontend/tasks.md](./frontend/tasks.md)
  - 覆盖需求: Req 4

## Notes

- 子 Spec 按顺序串行执行，前一个完成后才开始下一个
- 每个子 Spec 完成后验证编译和测试通过
- 所有任务标记为必须（无可选任务）
- Property-based tests 覆盖设计文档中的 32 个 Correctness Properties
