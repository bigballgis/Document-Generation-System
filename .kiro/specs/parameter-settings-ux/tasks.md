# Implementation Plan: Parameter Settings UX

## Overview

本功能按功能边界拆分为两个子 Spec，串行执行：

1. **backend-api** — 后端批量操作 API、JSON 导入端点、ErrorCode、AuditLog（Req 9, Req 3 后端部分）
2. **frontend-ux** — 前端全部 UX 增强：内联编辑、智能命名、JSON 导入对话框、拖拽排序、校验规则集成、衍生表达式集成、预览面板自动同步、键盘导航/Undo-Redo、快速添加栏、右键菜单完整功能、移除 ParameterTemplateMenu（Req 1-8, 10-11, Req 3 前端部分）

## 子 Spec

- [x] 1. 执行子 Spec: backend-api
  - 路径: `.kiro/specs/parameter-settings-ux/backend-api/tasks.md`
  - 覆盖: Req 9 (批量操作 API), Req 3 后端 (JSON 导入端点)

- [x] 2. 执行子 Spec: frontend-ux
  - 路径: `.kiro/specs/parameter-settings-ux/frontend-ux/tasks.md`
  - 覆盖: Req 1-8, 10-11, Req 3 前端部分
  - 依赖: backend-api 子 Spec 完成后执行
