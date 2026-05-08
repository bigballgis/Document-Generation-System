---
inclusion: manual
name: git-commit
description: Git commit conventions — Conventional Commits format and commit granularity
---

# Git commit conventions

## Format

```
<type>(<scope>): <subject>

<body>
```

- type: `feat|fix|refactor|docs|style|test|chore|perf`
- scope (optional): `frontend|backend|docxtemplater|i18n|spec`
- subject: English, sentence case / imperative, ≤50 chars, no trailing period

## Before commit

1. `git add -A` → `git status --short` to verify
2. Ensure `node_modules`, `.env`, and other non-repo files are not staged
3. Prefer one commit when a spec batch is done; hotfixes may commit alone
