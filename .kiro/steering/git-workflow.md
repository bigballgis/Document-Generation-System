---
description: Git 工作流和代码提交规范，包括分支策略、Commit Message 格式和 Code Review 要求
inclusion: manual
---

# Git 工作流规范

## 分支策略

| 分支 | 用途 | 命名 |
|------|------|------|
| `main` | 生产就绪代码 | — |
| `develop` | 开发集成分支 | — |
| `feature/*` | 新功能开发 | `feature/template-segmentation` |
| `bugfix/*` | Bug 修复 | `bugfix/segment-render-error` |
| `hotfix/*` | 紧急修复 | `hotfix/auth-token-leak` |
| `release/*` | 发布准备 | `release/v1.2.0` |

## Commit Message 格式

使用 Conventional Commits 规范:

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

### Type

| Type | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `refactor` | 重构（不改变功能） |
| `docs` | 文档变更 |
| `test` | 测试相关 |
| `chore` | 构建/工具变更 |
| `perf` | 性能优化 |
| `style` | 代码格式（不影响逻辑） |
| `ci` | CI/CD 配置 |
| `db` | 数据库迁移 |

### Scope

使用模块名: `backend`, `frontend`, `docxtemplater`, `k8s`, `spec`

### 示例

```
feat(backend): add Segment entity and repository

db(backend): add V29 migration for segments table

feat(frontend): add segment list view with drag-and-drop

test(backend): add SegmentAssemblyPropertyTest
```

## Code Review 要求

- 所有合并到 `develop` 和 `main` 的 PR 必须经过至少一人 Review
- PR 描述必须包含: 变更说明、关联的 Spec/Issue、测试覆盖情况
- CI 检查必须全部通过（编译、测试、Lint）
