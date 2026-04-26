# Claude Project Instructions

This file provides Claude-specific project instructions. It mirrors `AGENTS.md` so Claude-based lower-tier implementation sessions start with the same constraints.

## Mandatory Context

Before implementing, read:

- `AGENTS.md`
- `docs/audits/full-project-review-2026-04-26/README.md`
- `docs/audits/full-project-review-2026-04-26/08-readiness-review.md`
- `docs/audits/full-project-review-2026-04-26/09-task-cards.md`
- `docs/audits/full-project-review-2026-04-26/10-implementation-review-checklist.md`
- `docs/audits/full-project-review-2026-04-26/11-execution-sequence.md`
- `docs/audits/full-project-review-2026-04-26/12-skill-registry.md`

## Language Policy

- User-facing conversation should be Simplified Chinese.
- All repository files and generated artifacts must be English.
- Comments, documentation, tests, task notes, and commit message drafts must be English.
- Localized UI resource files may contain localized strings only when they are intentionally part of product i18n.

## Task Execution Policy

- Execute one task card at a time.
- Do not combine workstreams.
- Do not change files outside the task card scope.
- Do not make product decisions not stated in the task card.
- Stop and report if a stop condition is reached.
- Prefer tests before behavior changes when the task is characterization or security-sensitive.

## Project Skills

Use `.cursor/skills/project-remediation-runner/SKILL.md` for every remediation task card.

Use one additional domain skill when relevant:

- `.cursor/skills/backend-security-hardening/SKILL.md`
- `.cursor/skills/docxtemplater-service-hardening/SKILL.md`
- `.cursor/skills/frontend-vue-workspace/SKILL.md`
- `.cursor/skills/delivery-governance/SKILL.md`
- `.cursor/skills/migration-safety/SKILL.md`
- `.cursor/skills/git-change-management/SKILL.md`
- `.cursor/skills/local-deployment-operations/SKILL.md`
- `.cursor/skills/validation-test-runner/SKILL.md`

## Review Package

The active review package is:

`docs/audits/full-project-review-2026-04-26/`

Use it as the source of truth for:

- Risks.
- Workstreams.
- Task cards.
- Execution order.
- Review checklist.
- Validation commands.

## Safety Constraints

- Do not revert unknown user changes.
- Do not run destructive Git commands.
- Do not add dependencies without approval.
- Do not change Flyway migrations unless explicitly assigned.
- Do not introduce or expose secrets.
- Do not weaken authentication, tenant isolation, or SSRF protections.
- Follow `AGENTS.md` **Git and File Safety** for commit cadence (default: commit after each completed task card with task ID in the English message; never push unless asked).
- Do not run local deployment commands against production services.

## Required Final Summary

Every implementation response must include:

- Task ID.
- Summary of changes.
- Files changed.
- Tests added or updated.
- Validation commands and results.
- Known limitations.
- Follow-up items.
