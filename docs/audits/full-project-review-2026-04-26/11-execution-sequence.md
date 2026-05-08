# Execution Sequence

This file defines a safe order for assigning task cards to lower-tier implementation models. The goal is to reduce ambiguity, avoid cross-workstream collisions, and ensure each task has enough context to complete without GPT-5.5 during implementation.

## Batch 0: Governance Preparation

Run these before production code changes if possible.

1. `WS-07-T01`: Classify Dirty Working Tree Artifacts
2. `WS-02-T01`: Document Java-to-Node Contract Mismatches
3. `WS-02-T06`: Define Composite Coverage Variable Scan Contract
4. `WS-03-T04`: Decide Composite R7 Render Config Scope
5. `WS-04-T03`: Decide Identical Diff Contract
6. `WS-05-T04`: Decide Versioned Rendering Contract
7. `WS-07-T05`: Create Migration Runbook
8. `WS-07-T06`: Create Release Runbook

Notes:

- These are documentation or decision tasks.
- They reduce implementation ambiguity.
- If product decisions are unavailable, mark the item blocked and continue with independent characterization tests.

## Batch 1: P0 Characterization

Run these before security behavior changes.

1. `WS-01-T01`: Characterize Main Template OnlyOffice Callback
2. `WS-03-T01`: Add ZIP Import Boundary Tests
3. `WS-05-T01`: Add Generation Eligibility Characterization Tests
4. `WS-05-T03`: Characterize Version Parameter Behavior
5. `WS-06-T01`: Add Workspace Route Reuse Test
6. `WS-06-T03`: Add OnlyOfficeEditor Prop Change Tests

Notes:

- These tasks should avoid production behavior changes.
- They create a safety net before fixes.

## Batch 2: Core P0 Security Containment

Recommended order:

1. `WS-01-T02`: Add RestTemplate Timeouts
2. `WS-01-T05`: Add SSRF URL Policy Helper
3. `WS-01-T06`: Apply URL Policy to Main OnlyOffice Callback
4. `WS-01-T09`: Enforce Callback Download Size Limit
5. `WS-01-T03`: Harden Main Template OnlyOffice Callback
6. `WS-01-T07`: Apply URL Policy to Segment OnlyOffice Callback
7. `WS-01-T04`: Harden Segment OnlyOffice Callback
8. `WS-01-T08`: Apply URL Policy to Webhook Delivery
9. `WS-01-T10`: Enforce Production Secret Startup Checks

Notes:

- Do not run callback hardening tasks in parallel unless shared helpers are already merged.
- `WS-01-T05` is a dependency for `WS-01-T06`, `WS-01-T07`, and `WS-01-T08`.
- `WS-01-T03` should use the characterization from `WS-01-T01`.

## Batch 3: Java-to-Node Contract Fixes

Recommended order:

1. `WS-02-T03`: Add Node Unknown Evaluate Type Tests
2. `WS-02-T02`: Align Expression Evaluation Type Mapping
3. `WS-02-T04`: Align Document Merge Route
4. `WS-02-T05`: Align Watermark Contract
5. `WS-02-T07`: Implement Composite Coverage Variable Scan Contract

Notes:

- `WS-02-T07` depends on `WS-02-T06`.
- Keep each endpoint contract separate.
- Do not combine merge, watermark, expression, and coverage fixes in one implementation pass.

## Batch 4: ZIP Import and Composite Package Work

Recommended order:

1. `WS-03-T02`: Add ZIP Import Limits
2. `WS-03-T03`: Normalize Imported Object Names
3. `WS-03-T05`: Implement Composite R7 Export
4. `WS-03-T06`: Implement Composite R7 Import

Notes:

- `WS-03-T05` and `WS-03-T06` depend on `WS-03-T04`.
- Security hardening should happen before feature expansion.

## Batch 5: Segment Version and Content Diff

Recommended order:

1. `WS-04-T01`: Add ContentDiffService Unit Tests
2. `WS-04-T02`: Add DocxTextExtractor Unit Tests
3. `WS-04-T04`: Implement Identical Diff Contract
4. `WS-04-T05`: Fix Docx XML Parse Failure Semantics
5. `WS-04-T06`: Handle Concurrent Segment Publish Conflicts
6. `WS-04-T07`: Update Segment Version API Documentation
7. `WS-04-T08`: Add SegmentVersionDialog Tests
8. `WS-04-T09`: Fix SegmentVersionDialog Collapsed Diff Reactivity

Notes:

- `WS-04-T04` and `WS-04-T07` depend on `WS-04-T03`.
- Backend contract should be settled before frontend UI assertions are finalized.

## Batch 6: Business Consistency

Recommended order:

1. `WS-05-T02`: Centralize Generation Eligibility Rule
2. `WS-05-T05`: Implement Versioned Rendering Contract
3. `WS-05-T06`: Align Composite Activation with State Machine
4. `WS-05-T07`: Characterize Template Test Service Semantics

Notes:

- `WS-05-T02` should use the characterization from `WS-05-T01`.
- `WS-05-T05` depends on `WS-05-T04`.
- If lifecycle rules are ambiguous, stop before changing state machine behavior.

## Batch 7: Frontend Experience

Recommended order:

1. `WS-06-T02`: Fix Workspace Reload on Route Change
2. `WS-06-T04`: Reinitialize OnlyOfficeEditor on Document Change
3. `WS-06-T05`: Remove DesignStage Debug Logging
4. `WS-06-T06`: Fix VersionDiffPanel Non-Text Diff Display
5. `WS-06-T07`: Complete Frontend i18n for Reviewed Components

Notes:

- `WS-06-T02` depends on `WS-06-T01`.
- `WS-06-T04` depends on `WS-06-T03`.
- `WS-06-T07` may conflict with the repository language policy because localized i18n files intentionally contain non-English UI strings. Treat that as a product policy decision, not an implementation guess.

## Batch 8: CI and Infrastructure

Recommended order:

1. `WS-07-T02`: Add Backend CI Stage
2. `WS-07-T03`: Add Frontend CI Stage
3. `WS-07-T04`: Add Docxtemplater CI Stage
4. `WS-08-T03`: Improve Frontend Docker Reproducibility
5. `WS-08-T01`: Pin Floating Docker Image Tags
6. `WS-08-T02`: Add Compose Resource Limits
7. `WS-08-T04`: Add Non-Root Runtime Users

Notes:

- CI provider must be known before CI implementation tasks start.
- Docker hardening should be validated with `docker compose config` or image builds.
- Use `delivery-governance` for CI and Docker hardening tasks.
- Use `local-deployment-operations` for local Compose validation and smoke checks.

## Batch 9: Validation, Handoff, and Commit Preparation

Recommended order after each completed implementation task:

1. Use `validation-test-runner` to run scoped validation commands from the task card.
2. Update `07-iteration-log.md` with command results and remaining risks.
3. Update `05-traceability-matrix.md` when a tracked item changes status.
4. Use `git-change-management` to review `git status`, changed files, staged files, and suggested commit grouping.

Notes:

- Do not commit unless the user explicitly asks.
- Keep one task card per commit when commits are requested.
- Do not stage unrelated files.
- Do not include generated binaries, temporary files, local secrets, or unrelated dirty-tree changes.

## Parallelization Rules

Safe to run in parallel:

- Documentation decision tasks that touch different files.
- Backend test-only tasks and frontend test-only tasks.
- CI stage tasks after the CI provider is confirmed.

Avoid parallel execution:

- Tasks that modify shared security helpers.
- Tasks that modify `CompositeImportExportService`.
- Tasks that modify `OnlyOfficeEditor.vue`.
- Tasks that modify migration-related behavior.
- Tasks that update the same i18n JSON files.

## Global Stop Conditions

Any lower-tier implementation should stop and report if:

- A required product decision is missing.
- A migration change appears necessary but was not in scope.
- A new dependency appears necessary.
- The task requires touching files outside its scope.
- Tests require unavailable services or credentials.
- The implementation would introduce Chinese text outside existing localized resource files.
