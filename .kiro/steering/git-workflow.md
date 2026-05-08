---
inclusion: manual
name: git-workflow
description: Git workflow — branch strategy and code review expectations
---

# Git workflow

## Branch strategy

| Branch | Purpose | Naming |
|--------|---------|--------|
| main | Production | — |
| develop | Integration | — |
| feature/* | Features | `feature/template-parameters` |
| bugfix/* | Bug fixes | `bugfix/parameter-validation` |
| hotfix/* | Emergency fixes | `hotfix/auth-token-leak` |
| release/* | Release prep | `release/v1.2.0` |

## Code review

- Merges to `develop` / `main` require at least one reviewer
- PRs include: summary, linked spec/issue, test coverage
- CI green: build + tests + lint
