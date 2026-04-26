# Composite R7: `render-config.json` Scope Decision (WS-03-T04)

**Date:** 2026-04-26  
**Decision:** **Defer** inclusion of `render-config.json` in composite template ZIP export/import for the current remediation batch.

## Context

Kiro requirement **R7** (see `.kiro/specs/composite-template-full-import/requirements.md`) calls for exporting watermark and barcode configuration in `render-config.json` and optionally importing it as template metadata.

The composite ZIP pipeline was hardened under **WS-03-T02** with strict entry allowlists, per-entry and aggregate size limits, and path rules. **WS-03-T03** added MinIO object-name sanitization for imported segment/header/footer files.

## Decision

**Defer** implementing `render-config.json` in the composite ZIP until all of the following are satisfied in a follow-up initiative:

1. **Published JSON schema** for `render-config.json` with explicit field typing, maximum sizes, and no ambiguous “fetch this URL” fields without the same outbound URL policy used elsewhere (SSRF-safe).
2. **Persistence model** for where imported render configuration lives on the `Template` (or a dedicated entity), including versioning, tenant isolation, and how it interacts with runtime generation (`WatermarkService`, barcode paths, etc.).
3. **ZIP allowlist update** coordinated with security review: add `render-config.json` only after schema validation and under the same `composite-import.zip` limits as other JSON entries.

## Rationale

- **Security:** Watermark and barcode configurations may embed image sources or indirect references. Without a strict schema and validation, `render-config.json` becomes a second uncontrolled JSON surface inside ZIP imports.
- **Contract drift:** Rendering behavior already spans Java services and the Docxtemplater Node service. A ZIP-only render config risks diverging from the canonical configuration used at generation time.
- **Scope control:** R1–R6 in the Kiro task list are implemented; R7 was never decomposed into concrete engineering tasks with tests. Implementing R7 “now” would expand the workstream across export, import, persistence, and likely UI without a locked contract.

## Impact

- Composite ZIP **export** will **not** emit `render-config.json`.
- Composite ZIP **import** will **not** read or store `render-config.json` (the entry is not on the import allowlist).
- Operators who rely on watermark/barcode settings must **reconfigure** them after import using existing product flows, or wait until R7 is implemented.

## Target

**Target version:** **Backlog** — no committed release train. Revisit after:

- A signed-off schema and persistence design, and  
- Explicit task cards replacing or extending **WS-03-T05** / **WS-03-T06** once WS-03-T04 is superseded by an “implement” decision.

## Follow-up Tasks (when scope reopens)

1. Add `render-config.json` to the documented composite ZIP contract and to `composite-import.zip` allowlists with parser limits.
2. Implement **WS-03-T05** (export) and **WS-03-T06** (import) against the approved schema.
3. Add regression tests for valid, oversized, and maliciously crafted `render-config.json` payloads.

## References

- `.kiro/specs/composite-template-full-import/requirements.md` — R7  
- `.kiro/specs/composite-template-full-import/tasks.md` — Task 6 note  
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md` — REQ-R7-001  
