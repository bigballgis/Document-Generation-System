# Versioned Template Generation — API Contract (WS-05-T04)

This document defines the **product and API contract** for template version numbers on document generation, as decided for the 2026-04-26 remediation baseline. It builds on observed behavior (WS-05-T03, audit note `docs/audits/full-project-review-2026-04-26/18-generate-api-version-parameter-behavior.md`).

## Scope

- **In scope:** `POST /api/generate/{templateId}` synchronous generation and the optional query parameter `version`.
- **Out of scope for this contract:** Async (`…/async`) and batch (`…/batch`) endpoints — they **do not** accept `version` today.

## Decision summary

| Topic | Contract |
| --- | --- |
| Historical file rendering | **Not supported** (including after WS-05-T05). Generated documents **always** use the template binary referenced by the current `templates.template_file_path` row (after normal pipeline resolution). |
| Meaning of `?version=n` when present | **Supported** for **validation and policy only**: the server checks that the version exists in `template_versions`, enforces `allowHistoryVersions` vs latest-version rules, then runs the same render path as when `version` is omitted. |
| Meaning of omitted `version` | Same render path; no `template_versions` reads for version resolution. |

## Normative rules (`?version`)

1. **Eligibility:** The template must be **`ACTIVE`** (`TemplateGenerationEligibilityService`) before any version logic runs.
2. **Latest version number:** When `version` is not `null`, the server reads `findMaxVersionNumber(templateId)` from `template_versions`.
3. **History policy:** If `allowHistoryVersions` is **false** and the requested `version` is **not** equal to that maximum → respond with **`GENERATE_VERSION_NOT_ALLOWED`** (HTTP **400**). No full version list fetch is required for this branch.
4. **Existence:** Otherwise the requested `version` must match a row in `template_versions` for that template; else **`TEMPLATE_VERSION_NOT_FOUND`** (HTTP **404**).
5. **Render source:** Regardless of `version`, **`DocumentGeneratorService`** MUST continue to resolve the DOCX (or composite assembly) from the **current** template entity and related live configuration — **not** from `template_versions.template_file_path` for output generation, unless a **future** task explicitly supersedes this contract with true versioned rendering.

## Rationale

- Keeps the **documented contract** aligned with **current implementation**, avoiding a mismatch where clients assume historical bytes.
- Preserves **useful** semantics of `version`: gatekeeping, auditing, and future extension without breaking existing validation.
- **WS-05-T05** implemented explicit passing of the validated sync API version into `DocumentGeneratorService` without changing the render source; a later task may still add true versioned rendering if the product requires it.

## OpenAPI and UI disclaimer

Controller / OpenAPI text that suggests `version` “selects” a template file for rendering should be treated as **misleading** relative to this contract until copy and implementation are updated under a dedicated documentation or implementation task.

## Implementation status (WS-05-T05)

- The synchronous API passes the validated `version` number into `DocumentGeneratorService.generateDocument(..., syncValidatedTemplateVersion)` for explicit logging and future extension.
- **Render source is unchanged:** SINGLE templates still call `renderDocument(template.getTemplateFilePath(), ...)`. Async, batch, and scheduled jobs call the two-argument overload (third parameter `null`).

## References

- Implementation: `DynamicApiService`, `DocumentGeneratorService`, `GenerateController`.
- Characterization tests: `DynamicApiServiceVersionParameterTest`, `DocumentGeneratorServiceSyncVersionRenderPathTest`.
