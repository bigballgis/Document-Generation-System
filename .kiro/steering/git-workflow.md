---
inclusion: manual
name: git-workflow
description: Git 工作流规范，包括分支策略和 Code Review 要求
---

# Git 工作流

## 分支策略

| 分支 | 用途 | 命名 |
|------|------|------|
| main | 生产 | — |
| develop | 开发集成 | — |
| feature/* | 新功能 | feature/template-segmentation |
| bugfix/* | Bug 修复 | bugfix/segment-render-error |
| hotfix/* | 紧急修复 | hotfix/auth-token-leak |
| release/* | 发布准备 | release/v1.2.0 |

## Code Review

- 合并到 develop/main 必须至少一人 Review
- PR 包含: 变更说明、关联 Spec/Issue、测试覆盖
- CI 全部通过 (编译+测试+Lint)
