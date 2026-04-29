---
name: noise-reduction-governance
description: Reduces repository noise, redundancy, comment clutter, duplication, and cognitive complexity while preserving behavior and project safety rules. Use when the user asks for code slimming, cleanup, deduplication, reducing noise, maintainability improvement, comment cleanup, dead-code review, or code-health refactoring.
---

# Noise Reduction Governance

## Required Context

Read before planning or editing:

- `AGENTS.md`
- `docs/development/noise-reduction-master-plan.md`
- `docs/development/comment-cleanup-config-allowlist.md`
- Relevant task card if this is remediation work from `docs/audits/full-project-review-2026-04-26/09-task-cards.md`

Use `validation-test-runner` after implementation. Use `git-change-management` before commit or handoff.

## Core Rules

- Preserve behavior unless the user explicitly asks for a behavior change.
- Preserve configuration comments in YAML, properties, env examples, Docker, Kubernetes, CI, and build/tool config files.
- Preserve comments explaining security, tenant isolation, SSRF, migrations, external contracts, timing, invariants, or non-obvious test properties.
- Do not add dependencies just to perform cleanup.
- Do not broaden across workstreams, authentication, migrations, or tenant isolation unless the active task card allows it.
- Do not revert unknown user changes.

## Noise Classes

Remove first:

- Decorative section banners and obvious region comments.
- Empty Javadoc and comments that only restate names or visible control flow.
- Repeated blank-line churn and temporary scripts/artifacts.

Refactor only with evidence:

- Duplicated validation branches.
- Repeated DTO mapping or API wrappers.
- Over-nested methods where guard clauses or local helpers reduce cognitive load.
- Frontend templates that repeat an established component/composable pattern.

Do not remove by default:

- OpenAPI annotations and public contract metadata.
- Security and external-system rationale.
- Test property explanations that define invariants.
- Configuration comments covered by the allowlist.

## Workflow

1. Inventory:
   - Inspect `git status`, current dirty tree, and relevant diffs.
   - Identify whether the work is mechanical cleanup, structural refactor, or behavior cleanup.
   - Define files in scope and files out of scope.

2. Reduce:
   - Delete mechanical comment noise before refactoring code.
   - Prefer local simplification over new shared abstractions.
   - Prefer reducing nesting and cognitive load over maximizing line deletion.
   - Keep batches reviewable; separate mechanical cleanup from behavior-preserving refactors.

3. Validate:
   - Mechanical cleanup: compile/type-check plus targeted tests when relevant.
   - Structural refactor: targeted unit tests plus affected integration tests.
   - Shared behavior: broader backend/frontend/docxtemplater validation.

4. Report:
   - Files changed by area.
   - Insertions, deletions, net reduction, and deletion share from `git diff --shortstat` / `git diff --numstat`.
   - Validation commands and results.
   - Known limitations and next cleanup candidate.

## Acceptance Bar

Accept a cleanup batch when it improves code health, readability, or maintainability without reducing correctness, test signal, security posture, or reviewability.

Do not chase perfect minimalism. A smaller codebase is only better when future readers need less attention to understand the same behavior.

