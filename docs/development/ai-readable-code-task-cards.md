# AI-Readable Code Task Cards

These task cards translate `docs/development/ai-readable-code-remediation-plan.md` into low-tier-agent executable work. Each card is intentionally narrow, behavior-preserving, and independently reviewable.

## Execution Rules

- Execute one task card at a time.
- Use `.cursor/skills/ai-readable-code-governance/SKILL.md`.
- Treat the assigned task card as the execution authority. Master plans provide background only and must not expand scope.
- Read only the master-plan sections needed by the assigned card.
- Preserve behavior unless the card explicitly says otherwise.
- Do not add dependencies.
- Do not edit migrations, authentication behavior, tenant isolation, SSRF protections, or deployment configuration.
- Do not edit configuration comments; follow `docs/development/comment-cleanup-config-allowlist.md`.
- Stop instead of guessing when a comment may encode a security, migration, external-system, or persisted-data contract.
- Report insertions, deletions, net reduction, validation commands, and known failures.

## Card Template

```text
Task ID:
Goal:
Model tier:
Files in scope:
Files out of scope:
Required reading:
Allowed edits:
Forbidden edits:
Implementation steps:
Validation commands:
Stop conditions:
Expected handoff:
```

---

## AI-CODE-T01: Frontend Vue Template Comment Signal Pass

Goal: Remove obvious Vue template block comments that do not protect behavior or contracts.

Model tier: Low-tier implementation agent.

Files in scope:

- `frontend/src/**/*.vue`

Files out of scope:

- `frontend/vite.config.ts`
- `frontend/eslint.config.js`
- `frontend/src/i18n/**`
- Generated files and `dist/**`

Required reading:

- `docs/development/ai-readable-code-remediation-plan.md`
- `.cursor/skills/ai-readable-code-governance/SKILL.md`

Allowed edits:

- Remove single-line HTML comments such as `<!-- Filters -->`, `<!-- Table -->`, `<!-- Header -->`, `<!-- Pagination -->`, and similar visual section labels.
- Remove comments that only repeat the immediately following component or element.
- Leave surrounding markup unchanged.

Forbidden edits:

- Do not rename components.
- Do not change routes, API calls, store behavior, validation logic, or i18n keys.
- Do not remove comments that describe security, external integration, compatibility, or non-obvious state behavior.

Implementation steps:

1. Search `frontend/src/**/*.vue` for `<!--`.
2. Review each comment in local context.
3. Remove only comments that match the allowed edits.
4. Do not reformat unrelated markup.

Validation commands:

- From `frontend/`: `npm run type-check`
- From `frontend/`: `npx vitest run` when the touched files include tested components.

Stop conditions:

- A comment describes non-obvious behavior, compatibility, or external integration.
- Removing the comment requires changing code structure.

Expected handoff:

- Files changed.
- Comments removed count.
- Validation commands and results.
- Comments intentionally retained and why.

---

## AI-CODE-T02: Frontend TypeScript Comment Signal Pass

Goal: Remove redundant TypeScript comments that do not ground behavior.

Model tier: Low-tier implementation agent.

Files in scope:

- `frontend/src/**/*.ts`
- `<script setup lang="ts">` sections in `frontend/src/**/*.vue`

Files out of scope:

- `frontend/vite.config.ts`
- `frontend/eslint.config.js`
- `frontend/src/i18n/**`
- Generated files and `dist/**`

Required reading:

- `docs/development/ai-readable-code-remediation-plan.md`
- `.cursor/skills/ai-readable-code-governance/SKILL.md`

Allowed edits:

- Remove decorative `// ----` or `// ──` comments.
- Remove comments that repeat function names, API groups, obvious branches, or interceptor behavior.
- Remove empty or boilerplate JSDoc that duplicates a symbol name.

Forbidden edits:

- Do not change types, API paths, request payloads, store state, or test expectations.
- Do not remove comments describing state transitions, compatibility, or cross-service contracts.

Implementation steps:

1. Search for `//`, `/**`, and `*/` in files in scope.
2. Classify comments as must-keep or removable.
3. Remove only clearly removable comments.
4. Avoid broad regex deletion.

Validation commands:

- From `frontend/`: `npm run type-check`
- From `frontend/`: `npx vitest run`

Stop conditions:

- A comment appears to encode a product or integration contract.
- Removing a comment would require renaming or refactoring code.

Expected handoff:

- Files changed.
- Comments removed count.
- Comments retained due to contract/invariant risk.
- Validation commands and results.

---

## AI-CODE-T03: Backend DTO and Entity Javadoc Signal Pass

Goal: Remove empty or redundant Javadoc from backend DTO and entity classes.

Model tier: Low-tier implementation agent.

Files in scope:

- `backend/src/main/java/com/docgen/dto/**/*.java`
- `backend/src/main/java/com/docgen/entity/**/*.java`

Files out of scope:

- `backend/src/main/java/com/docgen/config/**`
- `backend/src/main/java/com/docgen/controller/**`
- `backend/src/main/java/com/docgen/service/**`
- Flyway migrations and resources

Required reading:

- `docs/development/ai-readable-code-remediation-plan.md`
- `.cursor/skills/ai-readable-code-governance/SKILL.md`

Allowed edits:

- Remove empty Javadoc blocks.
- Remove class or field comments that only repeat the class or field name.
- Remove decorative getter/setter section comments.

Forbidden edits:

- Do not change annotations, field names, enum values, serialization behavior, validation constraints, or database mapping.
- Do not remove comments explaining persisted data compatibility or enum state semantics.

Implementation steps:

1. Search DTO/entity files for Javadoc and `//`.
2. Remove only comments covered by allowed edits.
3. Verify no annotations or code statements changed.

Validation commands:

- From `backend/`: `mvn -q -DskipTests compile`
- Run targeted tests only if a non-comment code change becomes necessary; otherwise stop and ask first.

Stop conditions:

- A comment is tied to JPA mapping, JSON serialization, enum compatibility, or persisted data.
- Any non-comment code edit appears necessary.

Expected handoff:

- Files changed.
- Comments removed count.
- Validation commands and results.

---

## AI-CODE-T04: Backend Controller and Service Comment Signal Pass

Goal: Remove obvious section/restatement comments in backend controllers and services without altering behavior.

Model tier: Low-tier implementation agent.

Files in scope:

- `backend/src/main/java/com/docgen/controller/**/*.java`
- `backend/src/main/java/com/docgen/service/**/*.java`

Files out of scope:

- `backend/src/main/java/com/docgen/config/**`
- `backend/src/main/java/com/docgen/security/**`
- `backend/src/main/resources/**`
- Flyway migrations

Required reading:

- `docs/development/ai-readable-code-remediation-plan.md`
- `.cursor/skills/ai-readable-code-governance/SKILL.md`

Allowed edits:

- Remove decorative section comments.
- Remove comments that repeat endpoint names, method names, or visible branches.
- Remove empty Javadoc blocks.

Forbidden edits:

- Do not remove OpenAPI annotations.
- Do not remove comments describing security, tenant isolation, SSRF, external callbacks, state-machine rules, retries, or persistence compatibility.
- Do not change method bodies unless the task is stopped and escalated.

Implementation steps:

1. Search files in scope for `//`, `/**`, and `*/`.
2. Review each comment with 10-20 lines of surrounding context.
3. Remove only comments that are clearly removable.
4. Do not reorder methods or reformat unrelated code.

Validation commands:

- From `backend/`: `mvn -q -DskipTests compile`
- Targeted tests for touched services if comments were converted into names/constants; otherwise compile is sufficient.

Stop conditions:

- A comment mentions security, tenant, SSRF, callback, migration, state machine, retry, or external service behavior.
- A code change beyond comment deletion appears useful.

Expected handoff:

- Files changed.
- Comments removed count.
- Comments retained and why.
- Validation commands and results.

---

## AI-CODE-T05: Docxtemplater Comment Signal Pass

Goal: Remove redundant comments in the Node rendering service while preserving Java-to-Node contract clarity.

Model tier: Low-tier implementation agent.

Files in scope:

- `docxtemplater-service/src/**/*.js`
- `docxtemplater-service/server.js`
- `docxtemplater-service/*.mjs`

Files out of scope:

- `docxtemplater-service/jest.config.js`
- `docxtemplater-service/package.json`
- `docxtemplater-service/package-lock.json`
- Generated output directories

Required reading:

- `docs/development/ai-readable-code-remediation-plan.md`
- `.cursor/skills/ai-readable-code-governance/SKILL.md`
- `.cursor/skills/docxtemplater-service-hardening/SKILL.md` only if comments mention sandbox, rendering contracts, PDF conversion, MinIO, or Java-to-Node behavior.

Allowed edits:

- Remove decorative section comments and redundant route body examples.
- Remove comments that restate adjacent code.
- Keep or shorten contract comments that prevent wrong edits.

Forbidden edits:

- Do not change request/response contracts.
- Do not change sandbox restrictions, PDF conversion behavior, MinIO behavior, watermark behavior, or legacy tag rewriting.
- Do not remove comments explaining SSRF-safe contracts or external service limitations.

Implementation steps:

1. Search files in scope for `//` and JSDoc blocks.
2. Classify comments as contract, invariant, or noise.
3. Remove only noise.
4. Preserve contract comments in shorter form if needed.

Validation commands:

- From `docxtemplater-service/`: `npm test`

Stop conditions:

- A comment explains Java service expectations, sandbox behavior, legacy template compatibility, external binary constraints, or storage contracts.

Expected handoff:

- Files changed.
- Comments removed/shortened count.
- Validation command and result.

---

## AI-CODE-T06: Comment-to-Code Conversion Candidates Inventory

Goal: Identify comments that should be converted into clearer names, constants, predicates, or tests without editing code.

Model tier: Low-tier exploration agent.

Files in scope:

- `backend/src/main/java/**`
- `frontend/src/**`
- `docxtemplater-service/src/**`

Files out of scope:

- Configuration files.
- Migrations.
- Generated artifacts.

Required reading:

- `docs/development/ai-readable-code-remediation-plan.md`
- `.cursor/skills/ai-readable-code-governance/SKILL.md`

Allowed edits:

- None. Read-only inventory only.

Forbidden edits:

- Do not change files.
- Do not propose broad rewrites.

Implementation steps:

1. Search for comments that explain ordinary control flow.
2. For each candidate, record file path, current comment, surrounding symbol, and suggested conversion type.
3. Group candidates by risk: low, medium, high.

Validation commands:

- None. Read-only task.

Stop conditions:

- More than 30 candidates are found; stop and return the top 15 by likely value.

Expected handoff:

- Candidate list with file paths.
- Suggested conversion type: rename, predicate, constant, guard clause, or test.
- Risk level.
- No file changes.

---

## AI-CODE-T07: Duplicate Validation Policy Inventory

Goal: Identify duplicated validation logic that may deserve local or module-private extraction.

Model tier: Low-tier exploration agent.

Files in scope:

- `backend/src/main/java/com/docgen/controller/**`
- `backend/src/main/java/com/docgen/service/**`
- `frontend/src/api/**`
- `frontend/src/views/**`
- `docxtemplater-service/src/routes/**`

Files out of scope:

- Migrations.
- Configuration files.
- Generated artifacts.

Required reading:

- `docs/development/ai-readable-code-remediation-plan.md`
- `.cursor/skills/ai-readable-code-governance/SKILL.md`

Allowed edits:

- None. Read-only inventory only.

Forbidden edits:

- Do not extract helpers.
- Do not change validation behavior.

Implementation steps:

1. Search for repeated validation patterns (`required`, blank string, type checks, state checks, permission checks).
2. Group repeated logic by owning module.
3. Identify whether duplication is policy duplication or harmless local clarity.
4. Recommend at most 10 extraction candidates.

Validation commands:

- None. Read-only task.

Stop conditions:

- A repeated rule crosses security, tenant isolation, migration, or Java-to-Node contract boundaries.

Expected handoff:

- Candidate list.
- Owning module.
- Why extraction is or is not recommended.
- Suggested tests if extraction is later approved.

---

## AI-CODE-T08: One-Method Class and Thin Interface Inventory

Goal: Identify class/interface explosion candidates without making structural changes.

Model tier: Low-tier exploration agent.

Files in scope:

- `backend/src/main/java/com/docgen/**`
- `frontend/src/**`
- `docxtemplater-service/src/**`

Files out of scope:

- Configuration classes unless explicitly requested.
- Framework-required interfaces.
- Generated files.

Required reading:

- `docs/development/ai-readable-code-remediation-plan.md`
- `.cursor/skills/ai-readable-code-governance/SKILL.md`

Allowed edits:

- None. Read-only inventory only.

Forbidden edits:

- Do not delete, merge, or rename classes.
- Do not propose removing interfaces required by Spring, serialization, test mocking, or extension contracts without evidence.

Implementation steps:

1. Identify one-method classes, thin wrapper services, one-implementation interfaces, and vague helper/util/base classes.
2. For each candidate, inspect usages.
3. Classify as keep, possible merge, possible inline, or needs senior review.

Validation commands:

- None. Read-only task.

Stop conditions:

- A candidate participates in dependency injection, transaction boundaries, serialization, security, scheduling, or framework discovery.

Expected handoff:

- Candidate list with usage evidence.
- Classification.
- Risk notes.
- No file changes.

---

## AI-CODE-T09: Low-Risk Local Duplicate Extraction

Goal: Refactor exactly one approved local duplicate policy into a local helper with tests.

Model tier: Low-tier implementation agent after senior approval of a candidate.

Files in scope:

- Only files named in the approved candidate.
- Directly related test file.

Files out of scope:

- Any file not named in the approved candidate.
- Cross-module helpers.

Required reading:

- Approved candidate note from `AI-CODE-T07`.
- `docs/development/ai-readable-code-remediation-plan.md`
- `.cursor/skills/ai-readable-code-governance/SKILL.md`
- Relevant domain skill if backend/frontend/docxtemplater-specific.

Allowed edits:

- Extract a local private method/function or module-private helper.
- Update tests for the extracted policy.
- Preserve behavior and public API.

Forbidden edits:

- Do not create shared global utilities.
- Do not add mode flags to make callers fit.
- Do not change validation semantics.
- Do not touch unrelated duplicate candidates.

Implementation steps:

1. Read the approved candidate and relevant files.
2. Add or update a targeted test first when practical.
3. Extract the smallest local helper.
4. Run validation.

Validation commands:

- Run targeted tests for the touched module.
- Run compile/type-check for the language area.

Stop conditions:

- Extraction requires changing public behavior.
- Helper needs mode flags, optional callbacks, or unrelated caller branches.
- Tests do not exist and behavior cannot be validated quickly.

Expected handoff:

- Candidate ID/source.
- Files changed.
- Tests added/updated.
- Validation commands and results.
- Net line reduction or explanation if neutral.

---

## AI-CODE-T10: Low-Risk Trivial Wrapper Inline

Goal: Inline exactly one approved trivial wrapper or helper after senior approval.

Model tier: Low-tier implementation agent after senior approval of a candidate.

Files in scope:

- Only files named in the approved candidate.
- Directly related tests.

Files out of scope:

- Any file not named in the approved candidate.
- Framework-required beans, interfaces, or configuration.

Required reading:

- Approved candidate note from `AI-CODE-T08`.
- `docs/development/ai-readable-code-remediation-plan.md`
- `.cursor/skills/ai-readable-code-governance/SKILL.md`

Allowed edits:

- Inline a trivial wrapper with one or two call sites.
- Delete unused wrapper only if all references are removed.
- Update imports and tests.

Forbidden edits:

- Do not inline Spring services, transactional boundaries, scheduled jobs, listeners, serializers, or security components.
- Do not merge classes without explicit approval.
- Do not touch more than one wrapper candidate.

Implementation steps:

1. Verify usages.
2. Inline the wrapper.
3. Remove dead imports.
4. Run validation.

Validation commands:

- Compile/type-check for the affected language area.
- Targeted tests for the touched module.

Stop conditions:

- The wrapper is a DI boundary, transaction boundary, framework hook, or public API.
- More than two call sites are found.
- Behavior validation is not available.

Expected handoff:

- Approved candidate source.
- Files changed.
- Wrapper removed or retained.
- Validation commands and results.

---

## Recommended Execution Order

1. `AI-CODE-T01`
2. `AI-CODE-T02`
3. `AI-CODE-T03`
4. `AI-CODE-T04`
5. `AI-CODE-T05`
6. `AI-CODE-T06`
7. `AI-CODE-T07`
8. `AI-CODE-T08`
9. Senior review of inventory outputs.
10. `AI-CODE-T09` for one approved candidate at a time.
11. `AI-CODE-T10` for one approved candidate at a time.

