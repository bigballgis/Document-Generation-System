# Readiness Review

## Readiness Status

The preparation package is ready for staged planning and handoff, but not yet ready for broad implementation by lower-tier models without additional task cards. The current documents provide the strategic baseline, risk map, workstreams, evidence index, traceability seed, validation command list, and iteration log.

## Ready

- Local audit directory exists and is structured.
- All audit package files are written in English.
- The previous generated Cursor plan file has also been converted to English.
- Project-level `AGENTS.md` and `CLAUDE.md` entry files exist.
- Project skills exist under `.cursor/skills/` for remediation execution, backend security, Docxtemplater service hardening, frontend workspace work, delivery governance, migration safety, git change management, local deployment operations, and validation test running.
- A skill registry exists at `12-skill-registry.md`.
- P0/P1/P2 findings are identified and mapped to evidence files.
- Workstreams are split by subsystem and risk type.
- Lower-tier model constraints are documented.
- Validation command placeholders are documented.
- The rule is explicit: chat with the user is Simplified Chinese, repository files are English.

## Not Ready Yet

- The full line-level traceability matrix is not complete.
- No build, test, Docker, or migration validation command has been executed in this planning pass.
- No implementation task cards have been expanded into low-level step-by-step instructions yet.
- The dirty working tree has not been classified into feature code, documentation, sample assets, local debug files, binaries, and generated artifacts.
- Production deployment assumptions are not yet confirmed.
- Database migration strategy for existing deployments is not yet decided.
- OnlyOffice callback security design is not yet finalized.
- A Cursor always-apply rule now exists at `.cursor/rules/project-agent-constraints.mdc`.

## Gate Before Lower-Tier Implementation

Before assigning a task to a lower-tier model, GPT-5.5 should produce a task card that includes:

- Workstream ID.
- Goal.
- Files in scope.
- Files out of scope.
- Required behavior.
- Security constraints.
- Data migration constraints.
- Tests to add or update.
- Validation commands.
- Expected output.
- Stop conditions and escalation triggers.

## Suggested First Task Card

Workstream: WS-01  
Task: characterize and harden the main template OnlyOffice callback path.

Minimum scope:

- `backend/src/main/java/com/docgen/config/SecurityConfig.java`
- `backend/src/main/java/com/docgen/controller/OnlyOfficeController.java`
- `backend/src/main/java/com/docgen/service/OnlyOfficeService.java`
- Related tests only.

Out of scope:

- Segment callback hardening.
- Webhook SSRF hardening.
- Docxtemplater service contract repair.
- Frontend changes.
- Database migrations.

Acceptance criteria:

- A forged callback without a valid token or source proof cannot overwrite a template file.
- Download URLs outside the allowed host or network policy are rejected.
- Empty, oversized, or invalid content is rejected before MinIO overwrite.
- Tests cover rejection and successful callback behavior.

## Execution Guidance for Lower-Tier Models

- Do not solve multiple P0 issues in one task.
- Prefer characterization tests before behavior changes when existing behavior is unclear.
- Avoid broad refactors.
- Do not change public API contracts without updating documentation.
- Do not modify Flyway migrations unless the task is explicitly a migration task.
- Do not introduce new dependencies without approval.
- Stop and escalate if security design choices are ambiguous.

## GPT-5.5 Review Checklist

Before accepting lower-tier implementation output:

- Verify the implementation stayed within scope.
- Verify no unrelated files were reformatted or rewritten.
- Verify tests fail before the fix when practical and pass after the fix.
- Verify error handling does not hide security failures as success.
- Verify documentation and traceability rows were updated.
- Verify all new file content is English.
