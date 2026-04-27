# International Bank FOL — Full Demo Runbook (Design + Test)

This runbook turns the `international-bank-fol` folder into a **repeatable, customer-ready demo**.

## Goals

- A composite template that visibly demonstrates platform features (assembly, conditions, data scope, parameters, derived fields, watermark/barcodes, OnlyOffice editing, and automated test cases).
- A stable “happy path” scenario with curated data that looks realistic for enterprise stakeholders.
- A pre-flight test suite that catches broken derived parameters and common regressions before the live demo.

## Phase 1 — Bootstrap (one-time per environment)

1. Ensure local stack is running:
   - Frontend: `http://localhost/`
   - Backend: `http://localhost:8080/actuator/health/ping`
2. Run the bootstrap:

```powershell
cd "D:\working\Document Generation System\templates\international-bank-fol"
.\demo-bootstrap.ps1
```

Environment variables (optional):

- `DOCGEN_BACKEND_URL` (default `http://localhost:8080`)
- `DOCGEN_USERNAME` (default `sunsun`)
- `DOCGEN_PASSWORD` (if unset, the script prompts)

Outputs:
- A newly created COMPOSITE template
- Blank segment/header/footer DOCX objects in MinIO
- Parameter table imported from `parameters.json`
- Test cases imported from `demo-test-cases.json`

## Phase 2 — Design / Authoring (OnlyOffice)

The bootstrap creates **blank** DOCX files. Authoring is done via OnlyOffice:

1. Open the template workspace in the UI.
2. For each segment:
   - Open the segment in OnlyOffice editor.
   - Copy/paste the corresponding tag spec section from `template-content.md`.
   - Save (the callback writes the updated DOCX back to MinIO).
3. Repeat for header/footer if you want visible bank branding on every page.

Tip: For a high-stakes demo, pre-author only the most visible segments:
- Cover Page
- Facility Details
- Interest & Fees
- Security & Collateral (conditional)
- Guarantee (conditional)
- Signature Page

## Phase 3 — Test readiness (before every demo)

1. Run all template test cases:
   - UI: Template → Test Cases → Run All
   - or API: `POST /api/templates/{templateId}/test-cases/run-all`
2. Confirm:
   - Derived values still compute as expected (`total_facility_amount`, `weighted_avg_rate`, `overall_ltv`)
   - Conditional flags behave (`has_security`, `has_guarantor`)
3. Optional: use scenario variants for quick toggles:
   - `demo-data-variants/01-full.json`
   - `demo-data-variants/02-no-security-no-guarantor.json`
   - `demo-data-variants/03-stress-many-facilities.json`

## Phase 4 — Live demo flow (recommended)

1. Show the **parameter table**:
   - Highlight validation rules (document ref format, min/max counts, required fields).
   - Highlight DERIVED parameters (JS + Excel formula examples).
2. Open a segment in **OnlyOffice** and edit a visible field (borrower name / date).
3. Preview/generate a document with watermark + barcode + qrcode:
   - Use the structure from `render-request-example.json`.
4. Toggle `has_security` / `has_guarantor` and regenerate:
   - Show segments appearing/disappearing deterministically.

## Known limitations (current platform behavior)

- Test-case comparisons for `VARIABLE_VALUE` only compare **top-level** keys (not deep nested paths).
  - Use derived/top-level fields for robust assertions.

## Quick links

- Talk track: `DEMO-SHOWCASE-SCRIPT.md`
- API quick calls: `demo-api.http`

