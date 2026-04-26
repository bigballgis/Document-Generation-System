# Preflight, Skills, and Operating Model

## Confirmed Scope

- Output directory: `docs/audits/full-project-review-2026-04-26/`
- Review scope: the complete current working tree, including uncommitted and untracked files.
- Working mode: establish the review baseline and documentation system first, then remediate in stages.
- Current phase: planning and documentation preparation.

## Language Policy

- Chat with the user must be in Simplified Chinese.
- All files must be written in English, including documentation, comments, task descriptions, commit messages drafted by the agent, test names where practical, and generated artifacts.
- Existing Chinese content encountered during edits must be treated as a remediation item.
- If the Chinese content is outside the current task scope, record it and create a focused follow-up task instead of silently expanding the change.

## Skills and Tooling

This review uses these capabilities:

- Parallel read-only exploration agents for backend security, backend business flows, frontend architecture, rendering service behavior, test/document governance, and infrastructure delivery.
- Local evidence indexing through file reads, file globbing, and targeted search.
- Markdown-based audit artifacts stored in the repository for durable handoff.
- Canvas skill was checked. Because the requested deliverable is a local document directory, this package uses Markdown as the source of truth.

## Role Split

GPT-5.5 is responsible for:

- System-level planning and sequencing.
- Risk classification.
- Task decomposition.
- Acceptance criteria.
- Reviewing implementation output from lower-tier models.
- Preventing scope drift and preserving architectural consistency.

Lower-tier implementation models may be used for:

- Narrow, single-workstream implementation tasks.
- Mechanical refactors with clear instructions.
- Adding tests from a defined test matrix.
- Documentation updates from a defined outline.

Lower-tier models must not be asked to:

- Make broad architectural decisions.
- Redesign security boundaries without a GPT-5.5-reviewed plan.
- Perform large cross-module refactors without a checklist.
- Interpret ambiguous requirements without escalation.
- Modify migration scripts without explicit database upgrade guidance.

## Task Design for Lower-Tier Models

Each implementation task must include:

- Goal.
- Exact files or modules in scope.
- Files explicitly out of scope.
- Required behavior.
- Security constraints.
- Data migration constraints.
- Test requirements.
- Validation commands.
- Rollback or failure handling notes.
- A final checklist.

Tasks should be small enough to finish independently. If a task touches more than one subsystem, split it unless the contract requires an atomic change.

## Review Principles

1. Evidence first: every P0/P1 conclusion must trace to specific files.
2. Stop incidents first: prioritize authorization bypasses, data corruption, SSRF, unsafe production startup, and migration failure risks.
3. Treat non-functional requirements as first-class work: reliability, performance, testing, documentation, CI/CD, deployment, operations, and maintainability.
4. Work in phases: P0 containment, P1 contract and test alignment, P2 system hardening, P3 long-term evolution.
5. Preserve user changes: the working tree is dirty; do not revert unknown changes.

## Remaining Preflight Items

- Build a full requirement-to-test traceability matrix.
- Define repeatable validation commands for backend, frontend, Docxtemplater, Docker, and migrations.
- Confirm the production deployment target: local Compose, internal server, Kubernetes, or hybrid.
- Decide how destructive or conflicting migrations may be changed, especially around `segment_versions`.
- Decide the OnlyOffice security model: JWT validation, callback allowlist, internal network policy, and MinIO presigned URL domain strategy.
