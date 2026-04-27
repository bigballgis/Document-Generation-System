# International Bank FOL — Enterprise Demo Script (10–15 minutes)

Use this as a **talk track** (what to say) + **click path** (what to do). It is optimized for an enterprise buyer demo: credibility, control, and risk containment.

## Audience assumptions

- Bank / large enterprise stakeholders care about governance, correctness, auditability, and speed-to-change.
- They want “contract-like” outputs, not toy templates.
- They will probe on “what happens when data changes” and “how we prevent mistakes”.

## Pre-demo checklist (2 minutes)

1. Open `http://localhost/` and login.
2. Confirm backend health: `http://localhost:8080/actuator/health/ping` returns 200.
3. Run all tests on the template (UI → Template → Test Cases → Run All).
4. Have the following ready to paste:
   - A borrower legal name change
   - Toggle `has_security` / `has_guarantor`
   - A new facility row (optional)

## Demo narrative structure

### 1) “This is not a toy template” (1–2 minutes)

Show:
- The composite template and segment structure (18 segments).
- Point out conditional segments (Security / Guarantee).
- Mention data scope isolation per segment (only relevant fields are available inside that segment).

Say:
- “We model this like a real bank facility offer letter, but split into governed segments so teams can evolve parts independently.”

### 2) Parameter table as governance (3 minutes)

Show:
- Parameter table (tree view).
- Validation rules on critical fields (document ref pattern, required fields, min/max lists).
- Derived parameters:
  - `total_facility_amount`
  - `weighted_avg_rate`
  - `overall_ltv`

Say:
- “We don’t just accept arbitrary JSON — we define a schema with validation and derived computations so the output is deterministic.”
- “Derived fields reduce manual error and act like a built-in reconciliation layer.”

### 3) OnlyOffice editing with controlled persistence (3 minutes)

Show:
- Open a visible segment in OnlyOffice (Cover Page / Facility Details).
- Change a visible value (borrower legal name or deadline).
- Save and return to the system.

Say:
- “Business users can edit in a familiar document UI, but saves are still governed by our callback controls and storage versioning.”

### 4) Generation features buyers care about (2 minutes)

Show:
- Generate with watermark + barcode + QR code (from `render-request-example.json` structure).

Say:
- “In regulated environments, every document should carry traceability primitives: watermark, barcode, and verification QR.”

### 5) “Change one input, the contract changes predictably” (3–4 minutes)

Show:
- Toggle `has_security` from true → false, regenerate.
- Toggle `has_guarantor` from true → false, regenerate.

Say:
- “We can safely produce variants without duplicating templates — conditional segments let legal/compliance approve a single governed template with clear rules.”

### 6) Proof of reliability: automated test runner (1–2 minutes)

Show:
- Test cases (variable-value comparisons).
- Run all tests; show pass/fail counts.

Say:
- “We treat templates as code: every change can be checked. This is how you scale to hundreds of templates without fear.”

## Q&A traps and crisp answers

- “How do you stop broken changes?”
  - “Schema + validation + derived computations + test runner + controlled state transitions.”
- “Can business users bypass controls?”
  - “Edits persist via governed callbacks; the backend applies policy checks.”
- “What about auditability?”
  - “We keep versions and can compare; tests act as living acceptance criteria.”

