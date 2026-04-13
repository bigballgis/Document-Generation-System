---
inclusion: manual
---

# Git 提交规范

## Commit Message 格式

使用 Conventional Commits 规范：

```
<type>(<scope>): <subject>

<body>
```

### Type（必选）

| Type | 说明 |
|------|------|
| feat | 新功能 |
| fix | 修复 bug |
| refactor | 重构（不改变功能） |
| docs | 文档变更 |
| style | 代码格式（不影响逻辑） |
| test | 测试相关 |
| chore | 构建/工具/依赖变更 |
| perf | 性能优化 |

### Scope（可选）

模块名称，如 `frontend`、`backend`、`docxtemplater`、`i18n`、`spec`。多模块变更可省略。

### Subject（必选）

- 使用英文，首字母小写，不加句号
- 祈使语气（add, fix, update, remove）
- 50 字符以内

### Body（推荐）

- 说明变更内容和原因
- 按模块分组列出主要变更
- 每行 72 字符以内

## 提交前检查

1. `git add -A` 暂存所有变更
2. `git status --short` 确认变更文件列表
3. 确认没有遗漏的文件或不应提交的文件（如 node_modules、.env）
4. 编写 commit message 后执行 `git commit`

## 提交粒度

- 一个 spec 的所有任务完成后统一提交一次
- 紧急修复可单独提交
- 不要把不相关的变更混在一个 commit 里

## 示例

```
feat(frontend): add document generation UI and task monitoring

- Add generate.ts, tasks.ts, documents.ts, expressions.ts API layers
- Add Task Monitor page with real-time polling
- Add Document History page with filters and merge
- Add GenerateDialog and ExpressionPanel on template detail
- Add 8 test files (32 tests)
```

```
fix(backend): add missing GET /api/tasks list endpoint

- Add listTasks method to AsyncDocumentService
- Add findByFilters query to AsyncTaskRepository
- Add @GetMapping to TaskController
```
