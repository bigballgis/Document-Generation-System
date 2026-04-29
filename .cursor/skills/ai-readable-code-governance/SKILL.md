---
name: ai-readable-code-governance
description: Governs AI-readable code structure, necessary comments, deduplication, abstraction control, and class/method explosion prevention. Use when the user asks which comments are needed, how to make code easier for AI to read, reduce repeated code, prevent abstraction bloat, or plan structural code cleanup.
---

# AI-Readable Code Governance

## Required Context

Read before planning or editing:

- `AGENTS.md`
- `docs/development/ai-governance-index.md`
- `docs/development/ai-readable-code-remediation-plan.md`
- `docs/development/ai-readable-code-task-cards.md`
- `docs/development/comment-cleanup-config-allowlist.md`

Read only if relevant:

- `docs/development/ai-context-noise-reduction-master-plan.md` for prompt/context noise.
- `docs/development/noise-reduction-master-plan.md` for general code noise cleanup.
- Active audit task card for remediation work.

Use `validation-test-runner` after implementation. Use `git-change-management` before handoff or commit preparation.

Low-tier agents must execute one task card from `docs/development/ai-readable-code-task-cards.md` at a time. Do not let the master plan expand task scope. For low-tier execution, the assigned task card is authoritative over broad strategy language.

## Comment Policy

Treat comments as AI grounding signals, not decoration.

Keep comments only when they explain:

- Security, auth, tenant isolation, SSRF, encryption, or callback trust boundaries.
- Persisted data, migration, backward compatibility, or shipped public API constraints.
- External-system contracts such as OnlyOffice, Docxtemplater, MinIO, LibreOffice/UNO, or webhooks.
- Non-obvious invariants: ordering, retries, idempotency, locking, lifecycle, rate limits, or state transitions.
- Property-test intent or generated-data invariants.
- Intentional deviations from local patterns.

Remove or rewrite comments that:

- Repeat names, syntax, or visible control flow.
- Mark obvious sections such as CRUD, Filters, Validation, or Getters and Setters.
- Serve as decorative banners.
- Explain complexity that should instead be simplified.
- Say "silent", "handled by interceptor", "fallback", or "todo" without an actionable contract.

Before keeping a comment, ask:

1. Would an AI agent likely make a wrong edit without it?
2. Is it still true?
3. Can naming, structure, or tests express it better?
4. Does it explain why, contract, or invariant?

## AI-Readable Structure Rules

- Prefer guard clauses and linear flow.
- Reduce nesting before reducing line count.
- Use named predicates for multi-condition decisions.
- Keep side effects close to the triggering decision.
- Keep entry-point methods easy to find and read.
- Prefer stable domain names over comments.
- Avoid generic `Manager`, `Helper`, `Util`, `Processor`, `Handler`, `Common`, and `Base` names unless already established and truly generic.

## Deduplication Rules

Remove duplicate policy, not every similar-looking line.

Deduplicate when:

- The same rule must evolve together.
- Validation, mapping, state transition, or API contract logic is copied.
- Backend/frontend/docxtemplater encode the same contract differently.
- Repeated test setup hides the property under test.

Do not deduplicate when:

- Domains only look similar temporarily.
- A shared helper needs mode flags or optional callbacks.
- A local test is clearer with explicit setup.
- Extraction creates a vague abstraction.

Preferred order:

1. Normalize naming.
2. Extract local private method/function.
3. Extract package/module-private helper.
4. Extract shared abstraction only after stable repeated call sites share the same invariant.
5. Add or update tests around the shared policy.

## Explosion Control

Avoid class explosion:

- Do not create one-method services without independent responsibility.
- Do not introduce interfaces with one implementation unless framework, testing, or extension needs justify them.
- Do not create strategy classes before real strategies exist.
- Do not create base classes just to share a few lines.

Avoid method explosion:

- Do not split methods only to reduce line count.
- Do not create private methods that only rename the next expression.
- Do not hide preconditions in distant helpers.
- Prefer a readable 40-line cohesive workflow over ten trivial methods that require jumping around.

## Workflow

1. Inventory:
   - Identify comments, duplication, class/method explosion, and AI navigation pain.
   - Mark must-keep comments before deleting anything.
   - Identify files in and out of scope.

2. Plan:
   - Choose one pass: comment signal, AI navigation, duplicate policy, or explosion control.
   - Define behavior-preservation assumptions.
   - Define validation.

3. Edit:
   - Remove obvious comment noise first.
   - Convert comments into names/tests/structure where possible.
   - Prefer local simplification over new abstractions.
   - Keep public contracts stable.

4. Validate:
   - Run compile/type-check for touched language area.
   - Run targeted tests for refactored behavior.
   - Report known unrelated failures separately.

5. Report:
   - Files changed.
   - Comments removed/retained/rewritten.
   - Duplicate policy removed.
   - Classes/methods merged or avoided.
   - Validation commands and results.

## Stop Conditions

Stop and ask before changing when:

- A comment may encode security, migration, or external contract behavior.
- A deduplication crosses backend/frontend/docxtemplater boundaries without tests.
- Removing a class/interface could change Spring injection, serialization, transaction, or framework discovery.
- A proposed abstraction needs mode flags.
- Behavior preservation cannot be validated.

