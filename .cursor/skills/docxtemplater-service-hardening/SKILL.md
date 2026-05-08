---
name: docxtemplater-service-hardening
description: Harden and repair the Node.js Docxtemplater rendering service. Use for Express route contracts, evaluate sandbox behavior, request body limits, merge-segments behavior, PDF conversion, LibreOffice/UNO resource isolation, MinIO use, and Java-to-Node contract tasks.
---

# Docxtemplater Service Hardening

## Required Context

Read the active task card in:

`docs/audits/full-project-review-2026-04-26/09-task-cards.md`

Also read:

- `AGENTS.md`
- `docs/audits/full-project-review-2026-04-26/04-evidence-index.md`
- `docs/audits/full-project-review-2026-04-26/10-implementation-review-checklist.md`

## Service Rules

- Keep Java backend and Node service contracts explicit.
- Do not let unknown `/evaluate` types fall through to JavaScript execution.
- Do not rely on Node `vm` as a trusted sandbox for untrusted code.
- Prefer `isolated-vm` for untrusted JavaScript execution when available.
- Enforce endpoint-specific body limits.
- Validate request shape before rendering, merging, converting, or evaluating.
- Avoid unbounded buffering for large documents where practical.
- Do not expose stack traces or internal errors in production responses.

## Document Processing Rules

- Treat `.docx` input as untrusted.
- Do not assume XML string manipulation preserves relationships, media, comments, footnotes, or section metadata.
- Add tests for realistic documents before expanding merge behavior.
- Keep PDF conversion isolated and resource-bounded.
- Do not silently claim PDF encryption succeeded if the security tool failed.

## Stop Conditions

Stop and report if:

- A route contract is ambiguous.
- A Java caller and Node route expect different payload semantics.
- A fix requires a new dependency.
- A task requires LibreOffice integration tests but the environment lacks LibreOffice.
- A merge change would require full OpenXML relationship rewriting.

## Useful References

- Express production security best practices: `https://expressjs.com/en/advanced/best-practice-security.html`
- OWASP Node.js Security Cheat Sheet: `https://cheatsheetseries.owasp.org/cheatsheets/Nodejs_Security_Cheat_Sheet.html`
- Node.js `vm` documentation: `https://nodejs.org/api/vm.html`
- isolated-vm project: `https://github.com/laverdet/isolated-vm`
