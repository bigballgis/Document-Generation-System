# Remediation and Optimization Roadmap

## Phase 0: Review Baseline and Preparation

Goal: turn review findings into durable engineering assets.

Tasks:

- Establish this directory as the review entry point.
- Maintain an evidence index, risk classification, and workstream breakdown.
- Build a requirement, design, task, implementation, test, and documentation traceability matrix.
- Define validation commands and release readiness checks.

Acceptance criteria:

- Every P0/P1 issue has file evidence, impact, remediation direction, and test requirements.
- Later implementation work can start from workstream tasks without reinterpreting the full context.

## Phase 1: P0 Security and Data Protection

Goal: block high-risk vulnerabilities and data corruption paths.

Tasks:

- Harden OnlyOffice callbacks.
  - Validate OnlyOffice JWT or request signatures.
  - Bind callbacks to server-side sessions or one-time tokens.
  - Restrict download URL protocol, host, port, and network ranges.
  - Enforce download size and Content-Type limits.
  - Prevent writes without a valid tenant context.
- Add SSRF protection.
  - OnlyOffice download URLs.
  - Webhook outbound URLs.
  - Other RestTemplate-based external calls.
- Tighten production defaults.
  - Fail production startup when default secrets are used.
  - Protect Swagger, Actuator, metrics, and Prometheus.
  - Stop exposing database, Redis, MinIO, and OnlyOffice management ports to untrusted networks.
- Harden ZIP imports.
  - Limit total ZIP size, entry count, per-entry size, and compression ratio.
  - Normalize file names and object storage keys.
  - Clean up uploaded temporary objects on import failure.

Acceptance criteria:

- Forged OnlyOffice callbacks cannot overwrite files.
- Callback URLs pointing to internal or metadata addresses are rejected.
- Malicious ZIP files cannot exhaust memory or create unsafe object keys.
- Production startup fails when placeholder secrets are used.

## Phase 2: Cross-Service Contract Repair

Goal: make Java backend calls match the real Docxtemplater service API.

Tasks:

- Align the document merge interface.
  - Standardize on either `/merge` or `/merge-segments`.
  - Standardize request and response payloads.
- Align watermark behavior.
  - Decide whether `/watermark` should exist.
  - Or route Java watermark behavior through `/render`.
- Align expression evaluation types.
  - Map Java `ExpressionType` values to Node `javascript` and `excel` values explicitly.
  - Reject unknown Node `type` values.
- Fix composite coverage scanning.
  - Add a variable scanning API, or parse templates in the backend.
  - Stop using `/evaluate` as a variable extraction endpoint.
- Add Java-to-Node contract tests.

Acceptance criteria:

- Merge, watermark, evaluate, and coverage paths pass against a real local service environment, not only mocks.
- Unknown expression types fail with explicit errors.
- Coverage scanning reads templates and returns variables through a defined contract.

## Phase 3: Business Consistency

Goal: align template state, versioning, composite templates, marketplace behavior, and test semantics.

Tasks:

- Centralize generation eligibility rules.
  - Use the same rule for synchronous, asynchronous, batch, and internal generation.
  - Define behavior for `DRAFT`, `ACTIVE`, `PENDING_REVIEW`, and other states.
- Make the `version` parameter real.
  - If historical-version generation is supported, render from the selected `TemplateVersion` file path.
  - If it is not supported, reject it explicitly and update API docs.
- Route composite template activation through the same state machine, or document a separate product rule.
- Support composite template marketplace copying, including assembly config and related MinIO objects.
- Clarify whether template test cases run the real generation pipeline.

Acceptance criteria:

- All generation entry points behave consistently for `DRAFT` and `ACTIVE` templates.
- `?version=n` renders from the selected version or fails explicitly.
- Copied composite templates can be generated.
- Test case behavior matches product documentation.

## Phase 4: Requirement, Documentation, and Test Closure

Goal: eliminate governance risks where tasks are checked without implementation or tests.

Tasks:

- Maintain the traceability matrix.
- Resolve composite template import/export R7.
  - Implement `render-config.json`, or defer it with a documented reason and target version.
- Add missing docx content diff backend and frontend tests.
- Update `segment-version-api.md`.
- Write the V30/V36/V39 migration runbook.
- Mark the old `template-segmentation` specification as superseded or update it.

Acceptance criteria:

- Every requirement has implementation, test, and documentation status.
- Every deferred requirement has a reason, impact, and target version.
- Existing-database upgrade paths are executable and reviewable.

## Phase 5: Frontend Experience and Maintainability

Goal: reduce user-facing state bugs and frontend maintenance cost.

Tasks:

- Reload the workspace when route parameters change.
- Reinitialize or remount `OnlyOfficeEditor` when `documentUrl`, `documentKey`, or `viewOnly` changes.
- Scope variable insertion to the correct OnlyOffice instance.
- Fix `VersionDiffPanel` so non-text-only diffs still display details.
- Fix `SegmentVersionDialog` reactivity around `Set` updates.
- Complete i18n and remove production debug logs.

Acceptance criteria:

- Switching workspace IDs does not show stale data.
- Multiple editor tabs do not insert content into the wrong document.
- All version diff categories are visible.
- Frontend tests cover the critical interactions.

## Phase 6: Infrastructure and Quality System

Goal: make delivery repeatable, observable, and recoverable.

Tasks:

- Add CI/CD.
  - Backend `mvn verify`.
  - Frontend `vue-tsc --noEmit`, lint, and Vitest.
  - Docxtemplater Jest tests.
  - Docker builds.
  - Dependency audits.
  - Flyway migration validation.
- Improve Docker runtime posture.
  - Pin image tags or digests.
  - Use non-root users.
  - Set CPU and memory limits.
  - Evaluate a supported Node LTS upgrade.
- Add operations documentation.
  - Backup, restore, migration, and rollback.
  - Logs, metrics, and alerts.
  - Production environment variables and secret management.

Acceptance criteria:

- Pull requests cannot merge without CI passing.
- Production deployment has a runbook.
- Critical services have health checks, resource limits, logs, and metrics.
