---
inclusion: manual
name: spec-quality
description: Spec 文档质量标准，包括前后端一致性检查、EARS 需求模式和正确性属性
---

# Spec 质量标准

## 前端任务粒度

- 每个前端页面 (API层+视图+组件+i18n) 为独立子任务
- 每个子任务明确列出: API 文件、视图文件、组件文件、路由配置、i18n key

## 前端任务拆分模式

```
- [ ] N. 前端 — {模块名}
  - [ ] N.1 创建 API 调用层 (frontend/src/api/xxx.ts)
  - [ ] N.2 创建组件 (FormDialog/List/Validator)
  - [ ] N.3 集成到页面
  - [ ] N.4 扩展 i18n (三语言文件)
```

## 检查点验收

1. API 覆盖率: 每个 Controller 端点在 `frontend/src/api/` 有对应函数
2. 路由完整性: 每个页面在 `router/index.ts` 注册
3. i18n 完整性: 所有文本有 key，三语言文件同步

## Controller → API 映射

`XxxController.java` → `frontend/src/api/xxx.ts`，仅后端使用的端点需标注。

## EARS 需求模式

- Ubiquitous: THE [system] SHALL [action]
- Event-driven: WHEN [trigger], THE [system] SHALL [action]
- State-driven: WHILE [state], THE [system] SHALL [action]
- Unwanted: IF [condition], THEN THE [system] SHALL [action]

## 正确性属性

每个 spec 定义可执行属性，后端 jqwik + 前端 fast-check，引用对应需求编号。
