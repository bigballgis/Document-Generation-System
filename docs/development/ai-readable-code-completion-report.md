# AI-Readable Code Task Cards — Completion Report

Date: 2026-04-28  
Authority: `docs/development/ai-readable-code-task-cards.md`, `.cursor/skills/ai-readable-code-governance/SKILL.md`

This report closes the **AI-CODE-T01** through **AI-CODE-T10** execution cycle for this repository snapshot. Mechanical passes (T01–T05), read-only inventories (T06–T08), and approved implementation cards (T09–T10) are summarized below.

## Execution status

| Task ID | Status | Summary |
| --- | --- | --- |
| AI-CODE-T01 | Completed | Vue template block-comment signal pass: no `<!--` comments under `frontend/src/**/*.vue` (repo search). |
| AI-CODE-T02 | Completed | Redundant TS / `<script setup>` comment cleanup; `catch {}` normalization where safe. |
| AI-CODE-T03 | Completed | DTO/entity Javadoc noise reduction (empty/redundant blocks). |
| AI-CODE-T04 | Completed | Controller/service decorative comment pass (preserve security/contract comments). |
| AI-CODE-T05 | Completed | Docxtemplater service redundant comment pass; `npm test` green. |
| AI-CODE-T06 | Completed (inventory) | Comment-to-code candidates listed in **§ T06 inventory** (read-only deliverable). |
| AI-CODE-T07 | Completed (inventory) | Duplicate validation policy candidates in **§ T07 inventory**; high-value alignments implemented under T09 scope (see below). |
| AI-CODE-T08 | Completed (inventory) | Thin wrapper / one-method patterns in **§ T08 inventory** (read-only deliverable). |
| AI-CODE-T09 | Completed | Approved extractions: shared Node `badRequest` helper (`docxtemplater-service/src/utils/http-errors.js`); shared frontend `PARAMETER_NAME_REGEX` (`frontend/src/constants/parameterNamePattern.ts`). |
| AI-CODE-T10 | Completed | Approved inline: removed `useLocale` composable; `OnlyOfficeEditor.vue` uses `useI18n()` directly (single consumer). |

## T06 inventory — comment-to-code candidates (representative)

Goal: identify comments that could become names, predicates, constants, or tests **without** requiring immediate edits. Risk levels are indicative.

| # | Location | Suggested conversion | Risk |
| --- | --- | --- | --- |
| 1 | `ParameterTableView.vue` — sibling duplicate check | Named predicate `isDuplicateSiblingName(...)` | Low |
| 2 | `QuickAddBar.vue` — root name duplication | Same pattern as (1) if unified | Medium |
| 3 | `MonacoEditor.vue` — cursor context comment | Extract small function `charBeforeCursor(...)` | Low |
| 4 | `useJsonImport.ts` — “array contains objects” | Predicate `isArrayOfObjects` | Low |
| 5 | Property tests — “Verify …” comments | Often intentional property anchors; keep unless test names suffice | Low–Medium |
| 6 | `UserService.java` — `TODO` reset token | Product backlog; not a naming refactor | High (behavior) |
| 7 | i18n-backed user strings | No conversion; intentional localization | N/A |

Further candidates: prefer incremental refactors **only** when paired with a narrow task card.

## T07 inventory — duplicate validation policy (representative)

| # | Area | Observation | Follow-up |
| --- | --- | --- | --- |
| 1 | Frontend parameter names | Centralized on `PARAMETER_NAME_REGEX` (aligned with `ParameterService` pattern). | Done |
| 2 | Docxtemplater `400` JSON body | Centralized `badRequest(res, code, message)` in `http-errors.js`. | Done |
| 3 | API layer (`request.ts`) error mapping | Repeated status handling; consolidation needs explicit contract card. | Deferred |
| 4 | Backend null/blank DTO guards | Often intentional per-endpoint; cross-cutting extraction risks hidden coupling. | Deferred |

## T08 inventory — thin wrappers and boundaries (representative)

| Symbol | Classification | Notes |
| --- | --- | --- |
| `CallbackDocumentDownloadHelper` | **Keep** | Shared bounded download; security-sensitive; tests exist. |
| `TemplateGenerationEligibilityService` | **Keep** | Central business rule; multiple callers. |
| `useLocale` (removed) | **Inlined** | Trivial; single consumer — completed under T10. |
| Single-implementation interfaces used for mocking | **Keep** | Test and Spring extension contracts. |

Do **not** merge Spring `@Service` beans or transactional boundaries without a dedicated remediation task.

## Validation commands (regression baseline)

- Backend: `mvn -q -DskipTests compile` from `backend/`
- Frontend: `npm run type-check`; `npm run test:ci` (Vitest with `--pool=threads`) from `frontend/`
- Docxtemplater: `npm test` from `docxtemplater-service/`

## Follow-up (outside this card series)

- Optional: align `Detail.vue` reviewer UX with `getReviewerCandidates` (workflow backlog).
- Dependency/supply-chain upgrades per `docs/audits/full-project-review-2026-04-26/26-dependency-audit-snapshot-2026-04-26.md`.
- Further T09 extractions require a **new** approved candidate and a **single** scoped task execution.
