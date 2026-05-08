# WS-04-T03: Canonical identical-text contract for DOCX content diff

**Status:** Adopted (documentation only; behavior matches current backend).  
**Date:** 2026-04-26

## Decision

For **segment version DOCX content comparison** exposed through `ContentDiffService.computeContentDiff` (and therefore `SegmentVersionDiffResult` when content diff is requested), **identical extracted plain text** is represented as:

| Field | Value when extracted texts are equal |
| --- | --- |
| `contentChanged` | `false` |
| `contentDiffs` | **Empty list** (no rows of type `EQUAL`) |
| `truncated` | `true` if either side reported extraction truncation (or future line-cap truncation on the diff list); otherwise `false`. The diff algorithm is **not** invoked when texts are equal. |

**Rationale:** Skip `java-diff-utils` when there is nothing to compare; keep a single client rule—use `contentChanged` for “no body text change,” not the presence of `EQUAL` lines in `contentDiffs`.

## Internal helper (not the public API contract)

`ContentDiffService.computeLineDiff` (package-private) is the line-level primitive:

- **Both strings empty** → **empty list** (not “all EQUAL”).
- **Identical non-empty strings** that split into the same lines → **only `EQUAL` rows** (e.g. one physical line → one `EQUAL` line).

Today, `computeContentDiff` only calls `computeLineDiff` when `!oldText.equals(newText)`.

## Compatibility impact

- **Clients and UI** must **not** assume that identical bodies yield `contentDiffs` full of `EQUAL` entries. They **must** treat **`contentChanged == false`** (with optional empty `contentDiffs` when `includeContentDiff=true`) as **no textual body change**.
- **Breaking change risk:** Any consumer that inferred equality from `contentDiffs` being exclusively `EQUAL` rows would already disagree with shipped `computeContentDiff` behavior; this document formalizes the supported contract.

## Evidence

- `backend/src/main/java/com/docgen/service/ContentDiffService.java` — short-circuit before `computeLineDiff` when texts are equal.
- `backend/src/test/java/com/docgen/service/ContentDiffServiceTest.java` — `computeContentDiff_identicalTexts_returnsEmptyLinesAndNoContentChange`, `computeLineDiff_identicalSingleLine_returnsOneEqualLine`, `computeLineDiff_bothEmpty_returnsEmptyList`.

## Related work

- **WS-04-T04:** Change implementation only if this contract is revised; current code matches this document.
- **WS-04-T07:** Further API field documentation in `docs/segment-version-api.md`.
