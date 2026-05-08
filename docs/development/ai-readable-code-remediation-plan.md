# AI-Readable Code Remediation Plan

This plan optimizes the repository for AI-assisted programming: fewer misleading comments, clearer structure, less duplication, lower context cost, and fewer opportunities for agents to infer behavior incorrectly.

## Goal

Make the codebase easy for AI agents to navigate, verify, and change with minimal context.

The target state is:

- Code expresses normal behavior directly.
- Comments exist only when they prevent wrong AI inference or preserve critical context.
- Public workflows have one obvious path.
- Duplicate policies are centralized without creating abstraction bloat.
- Classes and methods remain cohesive, not fragmented into needless wrappers.
- Tests describe contracts and invariants, not implementation trivia.

## Comment Necessity Policy

For AI-first development, comments are not "for humans" by default. They are either grounding signals or noise.

### Must Keep Comments

Keep comments that prevent an AI agent from making an unsafe or incorrect edit:

- Security rationale: authentication, authorization, tenant isolation, SSRF, encryption, callback trust boundaries, token handling.
- Migration and data compatibility rationale: why a field, enum, schema, or fallback exists for persisted or shipped data.
- External-system contracts: OnlyOffice callback requirements, Docxtemplater tag compatibility, MinIO object contracts, LibreOffice/UNO constraints, webhook delivery semantics.
- Non-obvious invariants: ordering, idempotency, retries, rate limiting, task state transitions, locking, lifecycle constraints.
- Test property definitions: what invariant a property-based test is proving, especially when generated inputs make the test hard to infer.
- Intentional deviations from a local pattern: only when the reason cannot be expressed through naming or structure.

### Usually Remove Comments

Remove comments that add tokens without grounding behavior:

- Decorative banners and section labels.
- Comments that repeat method, endpoint, component, or variable names.
- Comments that describe obvious syntax or one-line assignments.
- Empty Javadoc and "CRUD operation" labels.
- Vue template block labels such as `<!-- Filters -->` when the markup is self-evident.
- "Handled by interceptor", "silent", "fallback", or "validation" comments when the surrounding code already says the same thing.
- Historical explanations that are not connected to a shipped compatibility requirement.

### Convert Comments Into Code

When a comment explains ordinary control flow, prefer:

- Rename a method or variable.
- Extract a small named predicate.
- Replace nested branches with guard clauses.
- Move a magic literal into a named constant.
- Add a contract test if the behavior matters.

### Comment Review Checklist

For every retained comment, answer:

1. Would an AI agent likely make a wrong edit without this comment?
2. Is the comment still true in the current code?
3. Can naming, structure, or a test express this better?
4. Is this about why/contract/invariant rather than what/syntax?

If the answer to all four is not favorable, remove or rewrite the comment.

## AI-Readable Code Style

### Prefer Explicit Linear Flow

- Use guard clauses for invalid states.
- Keep success paths easy to scan.
- Avoid deep nesting and long `else` chains.
- Prefer named booleans for multi-condition decisions.
- Keep side effects close to the decision that triggers them.

### Prefer Locality

- Keep policy close to its owning workflow until duplication proves it must be shared.
- Avoid broad utility classes for one or two call sites.
- Avoid cross-module helpers that hide domain ownership.
- Keep DTO mapping, validation, and orchestration boundaries visible.

### Prefer One Obvious Path

- Do not keep multiple equivalent APIs, stores, routes, helpers, or services unless compatibility requires them.
- If legacy behavior must stay, mark the compatibility reason and test it.
- Remove branch-only compatibility shims once the branch behavior is replaced.

### Prefer Stable Names Over Comments

Good names should encode:

- Domain object.
- Operation.
- Boundary or side effect.
- State transition or invariant.

Avoid generic names:

- `Manager`, `Helper`, `Util`, `Processor`, `Handler`, `Common`, `Base` unless the role is truly generic and established locally.

## Duplication Reduction Policy

Duplicate code is not always bad. Duplicate policy is dangerous.

### Remove Duplication When

- The same rule must evolve together.
- The same validation is copied across endpoints/components.
- The same API contract is encoded in Java, TypeScript, and Node in divergent ways.
- The same test setup obscures the property under test.
- A repeated branch has the same state transition or side effect.

### Keep Duplication When

- Two workflows are similar now but owned by different domains.
- Extraction would create a vague abstraction.
- A test is clearer with local setup.
- The shared helper would require flags, optional callbacks, or mode parameters to satisfy unrelated callers.

### Preferred Deduplication Order

1. Rename repeated concepts consistently.
2. Extract a local private method or local function.
3. Extract a package/module-private helper in the owning area.
4. Extract a shared abstraction only after three or more stable call sites share the same invariant.
5. Add a contract test around the shared policy.

## Avoiding Class and Method Explosion

### Class Explosion Smells

- One-method service classes with no independent lifecycle.
- `*Helper` / `*Util` wrappers around single framework calls.
- Interfaces with one implementation and no test or extension seam.
- Strategy classes created before there are real strategies.
- "Base" classes used to share a few lines of code.

### Method Explosion Smells

- Many tiny methods that only rename the next line.
- Private methods that require reading the whole class to understand their preconditions.
- Helpers with ambiguous names like `process`, `handle`, `doValidate`, or `prepare`.
- Methods split only to reduce line count, not cognitive load.

### Preferred Shape

- A class should own a coherent domain responsibility.
- A public method should map to a real operation or contract.
- A private method should hide a meaningful sub-step with a clear name and stable preconditions.
- A module should be navigable from top-level workflow to detail without bouncing across many trivial files.

## Structural Remediation Roadmap

### Phase 1: Comment Signal Pass

Scope:

- Source and test code only.
- Preserve configuration comments.
- Preserve public contract annotations.

Actions:

- Remove decorative and restatement comments.
- Rewrite retained comments into short invariant/contract statements.
- Move repeated comment knowledge into tests or named code.

Validation:

- Compile/type-check.
- Targeted tests when comments are converted into code structure.

### Phase 2: AI Navigation Pass

Scope:

- High-churn services, controllers, frontend views, and docxtemplater routes.

Actions:

- Add consistent naming for workflows and state transitions.
- Split files only when it improves ownership and searchability.
- Reduce "middle of file" hidden behavior by moving key workflows near entry points.
- Keep important entry points short enough for focused snippet reads.

Validation:

- Existing tests for touched modules.
- Manual diff review for behavior preservation.

### Phase 3: Duplicate Policy Pass

Scope:

- Repeated validation, mapping, API wrapper, and state transition logic.

Actions:

- Identify repeated rules.
- Extract local or module-private helpers.
- Avoid global abstractions unless the policy is truly shared.
- Add tests for extracted policy.

Validation:

- Targeted unit tests for extracted helpers.
- Integration tests where endpoints or contracts are affected.

### Phase 4: Explosion Control Pass

Scope:

- One-method classes, vague helpers, thin interfaces, and tiny wrappers.

Actions:

- Inline trivial wrappers.
- Merge classes that do not own independent responsibilities.
- Remove interfaces with one implementation unless framework, testing, or extension needs justify them.
- Replace mode-flag abstractions with explicit workflows when that is clearer.

Validation:

- Full compile/type-check for affected language area.
- Tests covering merged workflows.

### Phase 5: Preventive Governance

Actions:

- Use `.cursor/skills/ai-readable-code-governance/SKILL.md` for future cleanup and review.
- Use `docs/development/ai-readable-code-task-cards.md` as the execution layer for low-tier agents. Low-tier agents should execute exactly one task card at a time and must not infer extra scope from this master plan.
- Add review questions to task handoffs:
  - Does this comment prevent wrong AI edits?
  - Did this abstraction reduce policy duplication or just move code?
  - Did we create a class/method only to satisfy aesthetics?
  - Can an AI agent find the contract with one search and one focused read?

## Measurement

For each remediation batch, report:

- Changed files.
- Insertions and deletions.
- Net line reduction.
- Comments removed vs comments retained/rewritten.
- Duplicate policy eliminated.
- Classes/methods merged or avoided.
- Validation commands and results.
- Remaining high-noise hotspots.

## Stop Conditions

Stop instead of guessing when:

- A comment appears to encode a security, migration, or external contract.
- A duplicate rule crosses backend/frontend/docxtemplater contract boundaries.
- Removing a class/interface could change Spring injection, serialization, transaction, or test behavior.
- A helper extraction requires multiple mode flags.
- Tests are insufficient to prove behavior preservation.

