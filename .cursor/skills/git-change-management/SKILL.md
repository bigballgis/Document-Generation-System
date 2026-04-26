---
name: git-change-management
description: Manage git status, change grouping, commits, and review-ready handoff for this repository. Use when preparing commits, classifying dirty working tree changes, checking diffs, or handing off completed remediation task cards.
---

# Git Change Management

## Required Context

Read:

- `AGENTS.md`
- Active task card in `docs/audits/full-project-review-2026-04-26/09-task-cards.md`
- `docs/audits/full-project-review-2026-04-26/10-implementation-review-checklist.md`

## Core Rules

- **Default commit cadence:** The repository owner prefers a **commit after each completed remediation task card** (see `AGENTS.md` → Git and File Safety). Skip only when the user opts out or when there is nothing safe to commit. One task card per commit when practical.
- Do not push unless the user explicitly asks.
- Do not amend commits unless the user explicitly asks and it is safe.
- Do not run destructive git commands.
- Do not revert unknown user changes.
- Do not stage unrelated files.
- Do not include secrets, local credentials, generated binaries, or temporary files.

## Before Any Commit

Inspect:

- `git status`
- staged and unstaged diff
- recent commit style

Confirm:

- The changed files match one task card.
- Tests or validation commands were run or documented as not run.
- Audit files were updated when required.
- No Chinese text was introduced outside intentional localized UI resources.

## Commit Grouping

Prefer small commits grouped by task card:

- One task card per commit.
- Separate test-only characterization commits from behavior-changing commits when practical.
- Separate documentation decisions from production code changes.
- Keep migration changes isolated.

## Commit Message Style

Use concise English messages focused on intent.

Examples:

```text
Harden OnlyOffice callback URL validation
```

```text
Document Java-to-Node rendering contract gaps
```

```text
Add content diff service characterization tests
```

## Handoff Without Commit

If the user does not ask for a commit, provide:

- Task ID.
- Files changed.
- Tests run.
- Tests not run.
- Remaining risks.
- Suggested commit grouping.

## Stop Conditions

Stop and report if:

- Unrelated staged files are present.
- Possible secrets are staged.
- Generated binaries are staged.
- The working tree contains conflicting user changes in the same files.
- The change spans multiple task cards without approval.
