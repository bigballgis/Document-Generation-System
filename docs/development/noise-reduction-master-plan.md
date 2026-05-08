# Noise Reduction Master Plan

This plan defines how to reduce repository noise without weakening correctness, security, or long-term maintainability.

## Source Principles

The plan is grounded in these current and durable engineering references:

- Google Engineering Practices, "The Standard of Code Review": approve changes that improve overall code health; do not chase perfection when the change clearly improves maintainability, readability, and understandability.
- Google Engineering Practices, "How to write code review comments": explain reasoning; ask authors to simplify code or add lasting code comments instead of explaining complexity only in review.
- Software Engineering at Google, Chapter 9, "Code Review": review should check correctness, comprehension, consistency, and maintainability; small changes should generally stay near 200 lines.
- SonarSource Cognitive Complexity guidance: optimize for code that humans can follow; nesting and branching add mental load, while linear, expressive control flow reduces it.
- SonarQube metric definitions: track cognitive complexity, duplication, maintainability debt, comment density, code smells, and new-code quality rather than relying on subjective cleanup.

References:

- <https://google.github.io/eng-practices/review/reviewer/standard.html>
- <https://google.github.io/eng-practices/review/reviewer/comments.html>
- <https://abseil.io/resources/swe-book/html/ch09.html>
- <https://www.sonarsource.com/blog/5-clean-code-tips-for-reducing-cognitive-complexity/>
- <https://docs.sonarsource.com/sonarqube-server/9.9/user-guide/metric-definitions/>

## Noise Taxonomy

Treat "noise" as anything that makes a future reader spend attention without learning behavior, constraints, or intent.

Priority 1: Safe Mechanical Noise

- Decorative section comments, banner comments, obvious HTML block labels, empty Javadoc, and redundant "what the code does" comments.
- Dead blank-line churn, repeated imports, trivial local variables that only rename the next expression.
- Generated artifacts, build outputs, and temporary scripts that should not be committed.

Priority 2: Structural Redundancy

- Repeated endpoint wrappers, duplicated DTO mapping, repeated validation branches, repeated test setup with no domain signal.
- Overgrown methods where helper extraction can reduce nesting or make invariants explicit.
- Copy-pasted frontend templates or API wrappers where a local component/composable already exists.

Priority 3: Behavioral Noise

- Multiple ways to do the same workflow.
- Compatibility shims for unshipped branch-only behavior.
- Fallbacks that hide invalid states instead of enforcing invariants.
- Tests that assert implementation details rather than user-facing or contract behavior.

Do not classify the following as removable noise by default:

- Security, tenant-isolation, SSRF, callback, migration, and external-system contract rationale.
- Public API documentation and OpenAPI annotations.
- Configuration comments in YAML, properties, env examples, Docker, Kubernetes, CI, and build/tool config files.
- Test comments that define a property, invariant, or data-generation rationale.

## Operating Model

Use a three-pass workflow.

### Pass 1: Inventory

Collect evidence before editing:

- `git diff --stat` and `git diff --numstat` for current working-tree scale.
- Search for comment and duplication candidates with `rg` or IDE search.
- Identify files with high churn or many touched modules before grouping changes.
- Decide whether this is a mechanical cleanup, structural refactor, or behavior cleanup.

Stop if cleanup would overlap security-sensitive logic, migrations, authentication behavior, or unrelated dirty files without an explicit task card.

### Pass 2: Reduce

Apply the least risky reduction first:

- Delete pure comment noise before refactoring code.
- Prefer local helper extraction over introducing shared abstractions.
- Prefer replacing duplicate branches with explicit data maps only when the resulting structure is easier to read.
- Keep public contracts and shipped behavior stable.
- Avoid compatibility layers for branch-only, unshipped changes.

Keep changes reviewable:

- Aim for about 200 changed lines per review unit when practical.
- Separate mechanical comment cleanup from behavior-preserving refactors.
- Separate refactors from behavior changes.
- Do not add dependencies solely for one-time cleanup.

### Pass 3: Verify

Validation should scale with risk:

- Mechanical comment cleanup: compile/type-check plus targeted tests when available.
- Structural refactor: targeted unit tests plus affected integration tests.
- Shared behavior cleanup: broader service/frontend/docxtemplater validation.

Always report:

- Files changed by area.
- Added/deleted line counts and net line reduction.
- Validation commands and results.
- Known failures that predate or are unrelated to the cleanup.
- Remaining risk and suggested next cleanup batch.

## Best Practices

### Comments

- Comments should explain why, constraints, trade-offs, external contracts, or non-obvious invariants.
- If a reviewer needs a comment to understand ordinary control flow, first try to simplify the code.
- Delete comments that restate method names, endpoint names, obvious template regions, or field assignments.
- Preserve comments that prevent future security, data isolation, migration, or integration mistakes.

### Duplication

- Remove duplication only after identifying the invariant being duplicated.
- Do not abstract two call sites solely because they look similar; abstract when the same policy must evolve together.
- Prefer named helpers inside the owning module before creating cross-module utilities.
- Keep tests expressive even if a small amount of setup duplication improves readability.

### Cognitive Load

- Favor linear flow, early returns, and named decisions.
- Reduce nesting before reducing line count.
- Replace nested conditionals with guard clauses when it clarifies failure modes.
- Keep functions narrow enough that a reader can hold the complete behavior in working memory.

### Review Quality

- Use code-health improvement as the acceptance bar, not subjective perfection.
- Label optional polish as optional in reviews.
- Use tooling for formatting/style; reserve human review for correctness, comprehension, maintainability, and safety.
- Keep every cleanup batch reversible and easy to inspect.

## Measurement

Use these metrics for each cleanup batch:

- `git diff --shortstat`: changed files, insertions, deletions.
- Net reduction: `deletions - insertions`.
- Deletion share: `deletions / (insertions + deletions)`.
- Directory contribution: net reduction by top-level directory.
- Validation result: pass/fail and known environmental failures.

Optional metrics when tools exist:

- Cognitive complexity hotspots.
- Duplicated line density.
- Comment-line density.
- Maintainability/code smell count on new code.

## Project-Specific Guardrails

- Chat remains Simplified Chinese; repository artifacts remain English.
- Preserve comments in configuration files listed in `docs/development/comment-cleanup-config-allowlist.md`.
- Follow `AGENTS.md` and task-card scope for remediation work.
- Do not change migrations, dependencies, authentication behavior, tenant isolation, or SSRF protections unless assigned.
- Do not revert unknown user changes.
- Create one commit per completed remediation task card unless the user opts out; for ad-hoc hygiene, ask before committing.

