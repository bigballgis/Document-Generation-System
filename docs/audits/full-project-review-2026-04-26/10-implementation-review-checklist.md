# Implementation Review Checklist

GPT-5.5 should use this checklist before accepting any lower-tier implementation output.

## Scope Control

- The implementation only touches files listed in the task card.
- Files explicitly marked out of scope were not changed.
- No unrelated formatting churn was introduced.
- No unrelated refactors were added.
- No user changes were reverted.

## Language Policy

- All new or modified file content is English.
- New comments are English.
- New test names and descriptions are English where practical.
- No Chinese text was introduced into repository files.

Recommended check:

```powershell
rg "[\p{Han}]" <changed-files-or-directory>
```

## Security Review

- Security failures are rejected explicitly.
- Security failures are not logged as successful operations.
- Error handling does not hide data corruption or authorization failures.
- No new SSRF path was introduced.
- No new unauthenticated write path was introduced.
- No default secret was added.
- No sensitive value was logged.
- No external URL is trusted without validation.

## Data Integrity Review

- Existing persisted data remains compatible unless the task explicitly includes migration work.
- No Flyway migration was modified unless the task allowed it.
- File storage writes are atomic or have clear failure handling where practical.
- Partial failure behavior is tested or documented.
- Tenant boundaries are preserved.

## Contract Review

- API request and response shapes are documented or unchanged.
- Frontend and backend types remain aligned.
- Java-to-Node service contracts are tested or documented.
- Public endpoint behavior is not silently changed.
- Backward compatibility impact is stated.

## Test Review

- Tests cover the main success path.
- Tests cover the main failure path.
- Security tasks include negative tests.
- Contract tasks include cross-boundary expectations.
- Tests are focused and do not require unrelated infrastructure unless justified.
- If tests were not run, the reason is documented.

## Validation Review

- The task card validation commands were run, or a clear reason is given.
- Failures are summarized with actionable next steps.
- The final response includes command results.
- New linter or type errors are not introduced.

## Documentation Review

- Relevant audit package files are updated if the task changes status or evidence.
- `05-traceability-matrix.md` is updated when a tracked item moves state.
- `07-iteration-log.md` is updated after meaningful implementation or validation.
- Public API docs are updated when public behavior changes.

## Stop and Escalate

Reject or pause the implementation if any of these occur:

- The implementation changes multiple workstreams without approval.
- The implementation introduces a new dependency without approval.
- The implementation changes migration behavior without a migration task.
- The implementation changes authentication semantics without tests.
- The implementation solves ambiguity by guessing product behavior.
- The implementation contains unexplained broad rewrites.
