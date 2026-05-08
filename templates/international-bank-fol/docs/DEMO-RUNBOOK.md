# International Bank FOL — Full Demo Runbook (Design + Test)

This runbook turns the `international-bank-fol` folder into a **repeatable, customer-ready demo**.

## Goals

- A composite template that visibly demonstrates platform features (assembly, conditions, data scope, parameters, derived fields, watermark/barcodes, OnlyOffice editing, and automated test cases).
- A stable “happy path” scenario with curated data that looks realistic for enterprise stakeholders.
- A pre-flight test suite that catches broken derived parameters and common regressions before the live demo.
- A clear story for stakeholders: **authors work segment-by-segment**, while **tests and document generation render one merged DOCX** that represents the full letter.

## Design workspace vs merged output (important)

- **Template workspace (OnlyOffice)**: each segment is a **separate DOCX** in storage. Users open one segment at a time to edit tags and layout. This is intentional for governance and parallel authoring.
- **Template tests (`run-all` / `run`) and composite generation**: the backend **assembles enabled segments in `position` order** (respecting conditions and `pageBreakBefore`) into **one in-memory merged DOCX** before comparisons or storage. That merged file is what exercises “the whole document looks right” — including cross-segment flow, pagination, and optional Word fields that only make sense at document scope.
- **Word Table of Contents (TOC) fields**: a native `TOC` field only evaluates headings **inside the same Word file**. The Table of Contents segment therefore includes (a) a **static dot-leader list** for a polished demo when viewing that segment alone, and (b) an optional **TOC field** that becomes meaningful when you open the **merged** output (for example after download from generation, or when inspecting the merged bytes from a test run) and **Update Fields** in Word / OnlyOffice.

## Phase 1 — Bootstrap (one-time per environment)

1. Ensure local stack is running:
   - Frontend: `http://localhost/`
   - Backend: `http://localhost:8080/actuator/health/ping`
2. Run the bootstrap:

```powershell
cd "D:\working\Document Generation System\templates\international-bank-fol"
.\scripts\demo-bootstrap.ps1
```

Environment variables (optional):

- `DOCGEN_BACKEND_URL` (default `http://localhost:8080`)
- `DOCGEN_USERNAME` (default `sunsun`)
- `DOCGEN_PASSWORD` (if unset, the script prompts)

Outputs:
- A newly created COMPOSITE template
- Blank segment/header/footer DOCX objects in MinIO
- Parameter table imported from `spec/parameters.json`
- Test cases imported from `scripts/demo-test-cases.json`

## Phase 2 — Design / Authoring (OnlyOffice)

The bootstrap creates **blank** DOCX objects in MinIO. You can either author manually or use the bulk filler script.

### Option A — Automated segment bodies (recommended for demos)

After bootstrap, run (replace `31` with your template id):

```powershell
cd "D:\working\Document Generation System\templates\international-bank-fol"
$env:DOCGEN_BACKEND_URL = "http://localhost:8080"
$env:DOCGEN_USERNAME   = "sunsun"
$env:DOCGEN_PASSWORD   = "<your-password>"
"31" | pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\fill-all-segments.ps1
```

This script reads `docs/template-content.md`, generates **styled** per-segment DOCX (tables, typography, optional header/footer; cover segment has **no** header/footer), uploads each file, and updates `assembly-config` `filePath` values.

### Option B — Manual authoring in OnlyOffice

1. Open the template workspace in the UI.
2. For each segment:
   - Open the segment in OnlyOffice.
   - Copy/paste the corresponding fenced block from `docs/template-content.md`.
   - Save (callback persists the DOCX to MinIO).
3. Repeat for header/footer if you want shared branding across segments that include headers.

Tip: For a time-boxed rehearsal, you can still prioritize the most visible segments first; the script above is for **full 18-segment** fidelity.

## Phase 3 — Test readiness (before every demo)

1. Run all template test cases:
   - UI: Template → Test Cases → Run All
   - or API: `POST /api/templates/{templateId}/test-cases/run-all`
2. Understand what the runner does for **COMPOSITE** templates:
   - The service builds **one merged DOCX in memory** from all enabled segments (same assembly path as real generation), then runs the configured comparison (`VARIABLE_VALUE`, `TEXT_CONTENT`, or `FILE_SNAPSHOT`).
   - That is why stakeholders still see a **“whole letter”** outcome during the test phase, even though day-to-day editing is segment-scoped.
3. Confirm:
   - Derived values still compute as expected (`total_facility_amount`, `weighted_avg_rate`, `overall_ltv`)
   - Conditional flags behave (`has_security`, `has_guarantor`)
4. Optional: use scenario variants for quick toggles:
   - `demo-data-variants/01-full.json`
   - `demo-data-variants/02-no-security-no-guarantor.json`
   - `demo-data-variants/03-stress-many-facilities.json`
5. Optional — **TOC field** in merged output: if you export or open the merged DOCX in Word-class tooling, select the TOC area and **Update Field** so page numbers and entries refresh against headings that use outline levels (the filler applies outline level to `DocGenHeading1`-style headings inside each segment body).

## Phase 4 — Live demo flow (recommended)

1. Show the **parameter table**:
   - Highlight validation rules (document ref format, min/max counts, required fields).
   - Highlight DERIVED parameters (JS + Excel formula examples).
2. Open a segment in **OnlyOffice** and edit a visible field (borrower name / date).
3. Preview/generate a document with watermark + barcode + qrcode:
   - Use the structure from `spec/render-request-example.json`.
4. Toggle `has_security` / `has_guarantor` and regenerate:
   - Show segments appearing/disappearing deterministically.

## Known limitations (current platform behavior)

- Test-case comparisons for `VARIABLE_VALUE` only compare **top-level** keys (not deep nested paths).
  - Use derived/top-level fields for robust assertions.
- Native Word **fields** (TOC, PAGE) may require a manual **Update Fields** step in the client after opening a merged DOCX; automated tests do not emulate Word’s field refresh UI.

## Quick links

- Talk track: `docs/DEMO-SHOWCASE-SCRIPT.md`
- API quick calls: `scripts/demo-api.http`

## Clean tenant templates + import one golden ZIP (optional)

Use when you want **only** the final composite template left for inspection:

```powershell
cd "D:\working\Document Generation System\templates\international-bank-fol"
$env:DOCGEN_BACKEND_URL = "http://localhost:8080"
$env:DOCGEN_USERNAME   = "sunsun"
$env:DOCGEN_PASSWORD   = "<your-password>"
pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\reset-all-templates-and-import.ps1 -ExportGoldFromTemplateId 31 -Force
```

Replace `31` with the template id you trust as the “final” export source. The script writes the new id to `out/last-template-id.txt`.

