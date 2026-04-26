---
name: project-remediation-runner
description: Executes staged remediation task cards for this document generation system. Use when implementing tasks from docs/audits/full-project-review-2026-04-26/09-task-cards.md, following execution order, scope limits, validation commands, and stop conditions.
---

# Project Remediation Runner

## Purpose

Use this skill when implementing any task from:

`docs/audits/full-project-review-2026-04-26/09-task-cards.md`

The task cards were prepared by GPT-5.5 for lower-tier implementation models. Follow them exactly.

## Required Reading

Before editing files, read:

1. `AGENTS.md`
2. `CLAUDE.md`
3. `docs/audits/full-project-review-2026-04-26/09-task-cards.md`
4. `docs/audits/full-project-review-2026-04-26/10-implementation-review-checklist.md`
5. `docs/audits/full-project-review-2026-04-26/11-execution-sequence.md`

Read only the task card being executed plus directly relevant checklist sections.

## Execution Workflow

1. Identify the task ID.
2. Read the task card.
3. Restate:
   - goal,
   - files in scope,
   - files out of scope,
   - stop conditions,
   - validation commands.
4. Inspect only files needed for the task.
5. Add or update required tests first when the task is a characterization, contract, or security task.
6. Make the smallest implementation change.
7. Run the validation commands if possible.
8. Update audit status files only when the task card asks for it.
9. Stop and report if any stop condition is reached.

## Hard Rules

- Work on one task card only.
- Do not expand scope across workstreams.
- Do not add dependencies without approval.
- Do not modify Flyway migrations unless the task explicitly says so.
- Do not change public API behavior without documentation.
- Do not introduce Chinese text into repository files.
- Do not revert unknown user changes.
- Do not commit unless explicitly requested.

## Final Response Format

```text
Task ID:
Summary:
Files changed:
Tests added or updated:
Validation commands:
Validation results:
Not run:
Remaining risks:
Stop conditions:
```
