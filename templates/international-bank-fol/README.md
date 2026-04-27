# International Bank Commercial Loan — Facility Offer Letter (FOL) (Full Demo)

This directory contains a **COMPOSITE** demo template designed for **enterprise-grade showcases**. The goal is to exercise as many platform capabilities as possible (composite assembly, conditional segments, parameter schema, derived parameters, validation rules, watermark, barcodes/qrcodes, OnlyOffice editing, and template test cases).

## What’s included

- `docs/`: authoring and demo docs
  - `docs/template-content.md`: authoritative tag specification to paste into the segment documents.
  - `DEMO-RUNBOOK.md`: runbook for design + test readiness.
  - `DEMO-SHOWCASE-SCRIPT.md`: enterprise talk track (what to say + click path).
- `spec/`: reference configuration and schemas
  - `spec/assembly-config.json`: reference assembly plan (segment ordering, conditional segments, data scopes).
  - `spec/parameters.json`: full parameter schema (REQUEST + DERIVED, validation rules, expressions).
  - `spec/parameters-flat.json`: flattened parameter view (quick inspection).
  - `spec/render-request-example.json`: example generate request including watermark + barcode + QR code.
- `data/`: demo datasets
  - `data/sample-data.json`: curated demo data (syndicated + secured + guarantor scenario).
  - `demo-data-variants/`: scenario toggles and stress variants.
- `scripts/`: bootstrap + API helpers
  - `scripts/demo-bootstrap.ps1`: creates the template, creates blank segments, imports parameters, imports test cases, runs tests, exports backup.
  - `scripts/fill-all-segments.ps1`: optional bulk author — reads `docs/template-content.md`, generates styled per-segment DOCX, uploads each segment, updates `assembly-config` paths (use after bootstrap for a full 18-segment demo).
  - `scripts/package-and-import.ps1`: produces a system-compatible ZIP via export, then imports it back (proves importability).
  - `scripts/reset-all-templates-and-import.ps1`: deletes **all** templates for the tenant, then imports one golden composite ZIP (use `-ExportGoldFromTemplateId` to export before wipe, or `-ZipPath` for a fixed file).
  - `scripts/demo-test-cases.json`: ready-to-import test cases for the template test runner.
  - `scripts/demo-api.http`: quick API calls (REST Client).

## Feature coverage (high-level)

- Variable substitution and deep object access: `{borrower.legal_name}`, `{bank.address.city}`
- Conditional blocks: `{#if has_guarantor}...{/if}`, `{#if !has_security}...{/if}`
- Loops + nested loops: `{#facilities}...{/facilities}` + repayment schedules
- Filters: `upper`, `currency`, `percent`, `dateFormat`, `default`
- Aggregations: `facilities.$sum_amount`, `fees.$sum_amount`, etc.
- Derived parameters: `total_facility_amount`, `weighted_avg_rate`, `overall_ltv` (JS + Excel formula examples)
- Composite assembly: segment ordering, `pageBreakBefore`, conditional segments, `dataScope`, headers/footers (workspace = per-segment DOCX; **tests and generation merge segments into one DOCX** in the backend)
- Generate-time features: watermark, barcode, qrcode
- Test runner: variable-value assertions against derived fields and scenario toggles

## Segment structure (reference)

| # | Segment | Condition |
|---|---------|-----------|
| 1 | Cover Page | always |
| 2 | Table of Contents | always |
| 3 | Part A — Definitions & Interpretation | always |
| 4 | Part B — Facility Details | always |
| 5 | Part C — Interest & Fees | always |
| 6 | Part D — Repayment Schedule | always |
| 7 | Part E — Conditions Precedent | always |
| 8 | Part F — Representations & Warranties | always |
| 9 | Part G — Covenants | always |
| 10 | Part H — Security & Collateral | `data.has_security === true` |
| 11 | Part I — Guarantee | `data.has_guarantor === true` |
| 12 | Part J — Events of Default | always |
| 13 | Part K — Governing Law & Jurisdiction | always |
| 14 | Part L — Miscellaneous | always |
| 15 | Appendix A — Compliance Certificate | always |
| 16 | Appendix B — Drawdown Notice | always |
| 17 | Appendix C — Fee Schedule | always |
| 18 | Signature Page | always |
