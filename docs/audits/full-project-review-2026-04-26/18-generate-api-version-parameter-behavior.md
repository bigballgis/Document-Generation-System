# Generate API `version` Query Parameter — Observed Behavior (WS-05-T03)

This note records **current** behavior of `POST /api/generate/{templateId}?version=...` as implemented in `DynamicApiService` and downstream `DocumentGeneratorService`, without prescribing future contract changes (see WS-05-T04).

## Where `version` is handled

Only **`DynamicApiService.generateViaApi`** reads `version`. **`DocumentGeneratorService.generateDocument`** has **no** `version` parameter and **`GenerateDocumentRequest`** has **no** version field.

## When `version` is null

- No calls to `TemplateVersionRepository` for version resolution.
- Generation proceeds with `documentGeneratorService.generateDocument(templateId, request)` immediately after `ACTIVE` eligibility.

## When `version` is non-null

1. **`findMaxVersionNumber(templateId)`** determines the latest stored version number.
2. If **`allowHistoryVersions`** is **false** and the requested **`version` is not equal** to that maximum → **`GENERATE_VERSION_NOT_ALLOWED`** (HTTP 400). No existence lookup runs in that branch.
3. Otherwise, **`findByTemplateIdOrderByVersionNumberDesc`** loads rows; the requested number must match at least one row or **`TEMPLATE_VERSION_NOT_FOUND`** (HTTP 404).
4. On success, **`generateDocument(templateId, request, versionOrNull)`** is invoked (WS-05-T05): the third argument is the validated version number when `version` was present, or `null` when omitted. The render file path is still taken from the current `Template` row (see normative contract).

## Effect on rendered template file path

`TemplateVersion.templateFilePath` is **loaded from the database for validation only**. It is **not** passed into `DocumentGeneratorService`. Rendering still uses **`Template.templateFilePath`** from the current `templates` row (see `DocumentGeneratorService.generateDocument` and `renderDocument`).

Therefore, today, **`?version=n` does not switch the DOCX path used for render**; it only enforces policy and existence.

## Evidence

- Implementation: `DynamicApiService.java`, `DocumentGeneratorService.java`, `GenerateDocumentRequest.java`.
- Characterization tests: `DynamicApiServiceVersionParameterTest.java`.

## Normative contract (WS-05-T04)

Product and API rules (including explicit “validation-only” semantics for `version`) are defined in:

- [`../../versioned-template-generation-contract.md`](../../versioned-template-generation-contract.md)
