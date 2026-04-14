---
inclusion: manual
name: git-commit
description: Git 提交规范，包括 Conventional Commits 格式和提交粒度
---

# Git 提交规范

## 格式

```
<type>(<scope>): <subject>

<body>
```

type: feat|fix|refactor|docs|style|test|chore|perf
scope (可选): frontend|backend|docxtemplater|i18n|spec
subject: 英文、首字母小写、祈使语气、≤50字符、无句号

## 提交前

1. `git add -A` → `git status --short` 确认
2. 确认无 node_modules、.env 等不应提交的文件
3. 一个 spec 完成后统一提交，紧急修复可单独提交

## 示例

```
feat(frontend): add document generation UI and task monitoring

- Add generate.ts, tasks.ts, documents.ts API layers
- Add Task Monitor page with real-time polling
- Add GenerateDialog on template detail
```
