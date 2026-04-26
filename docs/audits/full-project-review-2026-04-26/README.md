# Full Project Review and Optimization Package

Review date: 2026-04-26  
Planning model: GPT-5.5  
Scope: the complete current working tree, including uncommitted and untracked files

## Purpose

This directory is the durable working package for a staged, full-project review and optimization effort. It is not a one-time summary. It covers functional correctness, security, data consistency, reliability, performance, maintainability, testing, documentation, release governance, and long-term optimization.

## Operating Constraints

- User-facing chat must be in Simplified Chinese.
- All repository files, documentation, code comments, task descriptions, and generated artifacts must be written in English.
- If a future implementation pass encounters Chinese text in files, create a focused remediation task to convert it to English before expanding the scope.
- GPT-5.5 owns planning, orchestration, review quality, task decomposition, and acceptance criteria.
- Lower-tier implementation models may execute individual tasks, so every task must be small, explicit, evidence-based, and verifiable.

## Documents

- [../../../../AGENTS.md](../../../../AGENTS.md): project-level AI agent operating guide.
- [../../../../CLAUDE.md](../../../../CLAUDE.md): Claude-specific implementation instructions.
- [../../../../.cursor/skills/project-remediation-runner/SKILL.md](../../../../.cursor/skills/project-remediation-runner/SKILL.md): project skill for executing remediation task cards.
- [00-preflight-and-skills.md](00-preflight-and-skills.md): preflight checks, skills, operating model, and execution constraints.
- [01-executive-findings.md](01-executive-findings.md): cross-module high-priority findings.
- [02-remediation-and-optimization-roadmap.md](02-remediation-and-optimization-roadmap.md): staged remediation and optimization roadmap.
- [03-workstreams.md](03-workstreams.md): workstream breakdown for staged execution.
- [04-evidence-index.md](04-evidence-index.md): evidence index mapping issues to files.
- [05-traceability-matrix.md](05-traceability-matrix.md): requirement, implementation, test, and documentation tracking.
- [06-validation-commands.md](06-validation-commands.md): validation commands for later implementation phases.
- [07-iteration-log.md](07-iteration-log.md): iteration log for staged remediation.
- [08-readiness-review.md](08-readiness-review.md): readiness assessment and lower-tier model handoff gates.
- [09-task-cards.md](09-task-cards.md): first batch of low-level implementation task cards.
- [10-implementation-review-checklist.md](10-implementation-review-checklist.md): GPT-5.5 checklist for reviewing implementation output.
- [11-execution-sequence.md](11-execution-sequence.md): recommended execution order and dependency rules.
- [12-skill-registry.md](12-skill-registry.md): project skill registry and task-to-skill mapping.
- [13-dirty-working-tree-classification.md](13-dirty-working-tree-classification.md): classification of current uncommitted and untracked artifacts.
- [14-java-node-contract-mismatch-inventory.md](14-java-node-contract-mismatch-inventory.md): inventory of Java-to-Node contract mismatches.
- [16-template-test-execution-semantics.md](16-template-test-execution-semantics.md): characterized behavior of `TemplateTestService` (real render pipeline vs JSON-only).
- [17-composite-r7-render-config-scope.md](17-composite-r7-render-config-scope.md): WS-03-T04 decision to defer `render-config.json` in composite ZIP (R7).
- [18-generate-api-version-parameter-behavior.md](18-generate-api-version-parameter-behavior.md): WS-05-T03 observed behavior of `?version=` on document generation API.
- [Normative contract: versioned generation](../../versioned-template-generation-contract.md) (WS-05-T04; file under repository `docs/`).
- [19-docx-identical-diff-contract.md](19-docx-identical-diff-contract.md): WS-04-T03 canonical contract for identical DOCX body text (`contentDiffs` empty, `contentChanged=false`).
- [20-composite-coverage-variable-scan-contract.md](20-composite-coverage-variable-scan-contract.md): WS-02-T06 normative contract for composite segment variable scanning (`POST /scan-variables`; Java must not use `/evaluate` for this).
- [20-composite-coverage-variable-scan-contract.md](20-composite-coverage-variable-scan-contract.md): WS-02-T06 normative contract for composite coverage variable scanning (`POST /scan-variables`); implementation WS-02-T07.

## Current Top Priorities

1. Harden OnlyOffice callback authentication, source validation, and download URL restrictions.
2. Add timeouts, resource limits, and SSRF protections to outbound HTTP paths.
3. Fix backend-to-Docxtemplater service contract mismatches.
4. Add real tests for docx content diff and segment version features.
5. Resolve Flyway V30/V36/V39 upgrade risks around the `segment_versions` model.

## Execution Model

Each implementation phase must follow this sequence:

1. Define the scope and acceptance criteria.
2. Update or confirm the contract documentation.
3. Make the smallest code and configuration changes needed.
4. Add or update tests.
5. Run validation commands.
6. Update this audit package with evidence and status.
