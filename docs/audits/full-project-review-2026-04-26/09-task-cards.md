# Implementation Task Cards

These task cards are designed for lower-tier implementation models. GPT-5.5 should review each task before assignment and review the output before acceptance.

## Task Card Template

```text
Task ID:
Workstream:
Goal:
Files in scope:
Files out of scope:
Required behavior:
Security constraints:
Data or migration constraints:
Tests required:
Validation commands:
Stop conditions:
Expected final response:
```

## WS-01-T01: Characterize Main Template OnlyOffice Callback

Workstream: WS-01  
Priority: P0  
Task type: tests and characterization

Goal:

Document the current behavior of the main template OnlyOffice callback before changing it.

Files in scope:

- `backend/src/main/java/com/docgen/controller/OnlyOfficeController.java`
- `backend/src/main/java/com/docgen/service/OnlyOfficeService.java`
- `backend/src/test/java/**`

Files out of scope:

- `CompositeTemplateController`
- Segment callback behavior
- Frontend files
- Docxtemplater service files
- Flyway migrations

Required behavior:

- Add focused tests that show the current callback can reach the save path when status is `2` or `6`.
- Add a test that documents behavior when the callback has no URL.
- Add a test for an empty downloaded document response if practical with existing test patterns.

Security constraints:

- Do not add a new security mechanism in this task.
- Do not change anonymous access yet.
- The purpose is characterization only.

Data or migration constraints:

- Do not modify database migrations.
- Do not require a real MinIO instance unless existing test infrastructure already provides one.

Tests required:

- Unit tests using mocks are acceptable.
- Tests should make the current behavior explicit.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if the project lacks a test pattern for mocking `RestTemplate` or `MinioClient`.
- Stop if writing the test requires broad Spring context changes.

Expected final response:

- Summarize tests added.
- State whether the tests characterize an unsafe current behavior.
- List validation command results.

## WS-01-T02: Add RestTemplate Timeouts

Workstream: WS-01  
Priority: P0  
Task type: infrastructure safety

Goal:

Configure central connection and read timeouts for backend outbound HTTP calls.

Files in scope:

- `backend/src/main/java/com/docgen/config/RestTemplateConfig.java`
- `backend/src/test/java/**`
- `backend/src/main/resources/application.yml` only if configuration properties are needed

Files out of scope:

- OnlyOffice callback authentication
- Webhook URL validation
- Docxtemplater contract changes
- Frontend files

Required behavior:

- Replace the raw `RestTemplate` construction with a timeout-aware configuration.
- Use environment-configurable defaults if consistent with project style.
- Preserve the existing bean name and injection behavior.

Security constraints:

- Timeout values must prevent indefinite blocking.
- Do not disable TLS or hostname verification.

Data or migration constraints:

- No database changes.

Tests required:

- Add a test that verifies a `RestTemplate` bean is created with the intended request factory or configuration, if practical.
- If direct assertion is difficult, document the limitation in the final response.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if the project version does not support the selected Spring request factory without adding dependencies.
- Escalate before adding new dependencies.

Expected final response:

- Explain timeout defaults and how they are configured.
- List validation command results.

## WS-01-T03: Harden Main Template OnlyOffice Callback

Workstream: WS-01  
Priority: P0  
Task type: security fix

Goal:

Prevent forged main template OnlyOffice callbacks from overwriting template files.

Files in scope:

- `backend/src/main/java/com/docgen/controller/OnlyOfficeController.java`
- `backend/src/main/java/com/docgen/service/OnlyOfficeService.java`
- `backend/src/main/java/com/docgen/config/SecurityConfig.java`
- `backend/src/main/resources/application.yml`
- Focused backend tests

Files out of scope:

- Segment callback hardening
- Webhook SSRF hardening
- Frontend files
- Docxtemplater service files
- Flyway migrations

Required behavior:

- Require a verifiable callback proof before saving the edited document.
- Reject callbacks without valid proof.
- Reject callback download URLs that are not allowed by configured policy.
- Reject empty or oversized downloads before MinIO overwrite.
- Preserve valid OnlyOffice save behavior for legitimate callbacks.

Security constraints:

- Do not rely on obscurity of template IDs.
- Do not allow arbitrary callback download hosts.
- Do not treat security rejection as a successful save.
- Return a response compatible with OnlyOffice where possible, but do not hide server-side security failures in logs.

Data or migration constraints:

- No database migration changes.
- No MinIO object schema changes.

Tests required:

- Forged callback without proof is rejected.
- Callback with disallowed URL is rejected.
- Empty downloaded content is rejected.
- Valid callback path still saves content.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if the OnlyOffice callback token format is ambiguous.
- Stop if a product decision is needed between header JWT validation, body token validation, or one-time callback token.
- Escalate before changing public callback URL shape.

Expected final response:

- Describe the chosen validation mechanism.
- List tests added or changed.
- List validation command results.

## WS-01-T04: Harden Segment OnlyOffice Callback

Workstream: WS-01  
Priority: P0  
Task type: security fix

Goal:

Apply the callback hardening pattern to segment-level OnlyOffice callbacks.

Files in scope:

- `backend/src/main/java/com/docgen/controller/CompositeTemplateController.java`
- Shared helper or service files introduced by WS-01-T03
- Focused backend tests

Files out of scope:

- Main template callback behavior, except shared helper reuse.
- Frontend files.
- Docxtemplater service files.
- Database migrations.

Required behavior:

- Segment callback saves only when callback proof and download URL policy are valid.
- Existing content isolation validation remains in place.
- Invalid callbacks do not overwrite segment files.

Security constraints:

- Do not weaken `ContentIsolationValidator`.
- Do not duplicate security logic if a shared helper exists.
- Do not allow callback processing without tenant-safe template lookup.

Data or migration constraints:

- No database migration changes.

Tests required:

- Forged segment callback is rejected.
- Disallowed callback URL is rejected.
- Content isolation violations still reject saves.
- Valid segment callback still saves content.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if shared callback validation from WS-01-T03 is not available or not accepted.
- Stop if segment callback URL compatibility with OnlyOffice is unclear.

Expected final response:

- Summarize shared callback validation reuse.
- List tests added or changed.
- List validation command results.

## WS-02-T01: Document Java-to-Node Contract Mismatches

Workstream: WS-02  
Priority: P0  
Task type: contract documentation

Goal:

Create a precise contract mismatch inventory before implementation changes.

Files in scope:

- `docs/audits/full-project-review-2026-04-26/`
- Existing backend and Docxtemplater files may be read but not changed.

Files out of scope:

- Production code.
- Tests.
- Migrations.

Required behavior:

- Add a contract inventory document that lists each backend caller, intended Node route, actual Node route, request body, response body, and mismatch.
- Include `merge`, `watermark`, `evaluate`, and coverage scanning.

Security constraints:

- Keep all file content in English.

Data or migration constraints:

- None.

Tests required:

- Not required for this documentation task.

Validation commands:

```powershell
rg "[\\p{Han}]" docs/audits/full-project-review-2026-04-26
```

Stop conditions:

- Stop if additional runtime contracts are discovered that require code reading beyond the target modules; record them as follow-up items.

Expected final response:

- List the document created or updated.
- Confirm no Chinese text was introduced.

## WS-02-T02: Align Expression Evaluation Type Mapping

Workstream: WS-02  
Priority: P0  
Task type: contract fix

Goal:

Ensure Java expression types are mapped to the Node evaluate API explicitly and safely.

Files in scope:

- `backend/src/main/java/com/docgen/service/ExpressionEngineImpl.java`
- `backend/src/main/java/com/docgen/entity/ExpressionType.java`
- `docxtemplater-service/src/routes/evaluate.js`
- Relevant backend and Node tests

Files out of scope:

- Formula engine redesign.
- Parameter service behavior beyond expression evaluation.
- Other Docxtemplater routes.

Required behavior:

- Java must send values the Node service recognizes.
- Node must reject unknown `type` values explicitly instead of treating them as JavaScript.
- Existing JavaScript expression behavior must remain compatible.
- Excel formula behavior must route to Formula.js.

Security constraints:

- Unknown or malformed type must not fall through to JavaScript evaluation.
- Do not widen sandbox capabilities.

Data or migration constraints:

- No database changes.

Tests required:

- Java mapping test for `JAVASCRIPT`.
- Java mapping test for Excel formula type.
- Node test for unknown type rejection.
- Node test for Excel path.

Validation commands:

```powershell
cd backend
mvn test
```

```powershell
cd docxtemplater-service
npm test
```

Stop conditions:

- Stop if `ExpressionType` contains values with unclear product semantics.
- Escalate before renaming enum values or changing stored data.

Expected final response:

- Summarize mapping behavior.
- List tests and validation results.

## WS-02-T03: Add Node Unknown Evaluate Type Tests

Workstream: WS-02  
Priority: P0  
Task type: Node test coverage

Goal:

Add focused Node tests that document how `/evaluate` should reject unknown expression types.

Files in scope:

- `docxtemplater-service/src/routes/evaluate.js`
- `docxtemplater-service/src/__tests__/**`

Files out of scope:

- Java backend files
- Formula implementation details
- Sandbox implementation

Required behavior:

- Unknown `type` values must return a client error.
- Missing `type` may keep the current default behavior if documented.

Security constraints:

- Unknown types must not fall through to JavaScript execution.

Data or migration constraints:

- None.

Tests required:

- Unknown `type` is rejected.
- Default JavaScript path remains valid.

Validation commands:

```powershell
cd docxtemplater-service
npm test
```

Stop conditions:

- Stop if changing behavior would break existing tests without a clear contract decision.

Expected final response:

- List tests added.
- List validation results.

## WS-02-T04: Align Document Merge Route

Workstream: WS-02  
Priority: P0  
Task type: contract fix

Goal:

Make Java document merge calls reach a real Node service route.

Files in scope:

- `backend/src/main/java/com/docgen/service/DocumentMergeService.java`
- `docxtemplater-service/server.js`
- `docxtemplater-service/src/routes/merge-segments.js`
- Related tests

Files out of scope:

- Segment assembly merge behavior unless it shares the same route.
- Watermark behavior.
- Expression behavior.
- Frontend files.

Required behavior:

- Choose one route contract and make both Java and Node use it.
- Preserve existing segment assembly behavior.
- Add or update tests for the chosen route.

Security constraints:

- Do not increase request size limits in this task.
- Do not add unauthenticated public exposure beyond the existing internal service model.

Data or migration constraints:

- No database changes.

Tests required:

- Java service calls the selected route.
- Node route accepts the documented payload.
- Existing merge-segments tests still pass.

Validation commands:

```powershell
cd backend
mvn test
```

```powershell
cd docxtemplater-service
npm test
```

Stop conditions:

- Stop if `DocumentMergeService` payload is semantically different from segment merge payload.
- Escalate if a new route is needed instead of reusing `/merge-segments`.

Expected final response:

- State the selected route contract.
- List tests and validation results.

## WS-02-T05: Align Watermark Contract

Workstream: WS-02  
Priority: P0  
Task type: contract fix

Goal:

Align backend watermark behavior with the Node service implementation.

Files in scope:

- `backend/src/main/java/com/docgen/service/WatermarkService.java`
- `docxtemplater-service/src/routes/render.js`
- New Node route only if selected by the task decision
- Related tests

Files out of scope:

- Document generation pipeline redesign.
- PDF conversion.
- Frontend files.

Required behavior:

- Decide whether watermarking is performed through `/render` or a dedicated `/watermark` route.
- Implement the smallest change to make backend calls succeed.
- Document the chosen contract in the audit package or API docs.

Security constraints:

- Do not trust arbitrary image paths or external URLs without validation.
- Do not increase request body limits.

Data or migration constraints:

- No database changes.

Tests required:

- Backend watermark service calls an existing route.
- Node route behavior is tested if a route is added or changed.

Validation commands:

```powershell
cd backend
mvn test
```

```powershell
cd docxtemplater-service
npm test
```

Stop conditions:

- Stop if product behavior for standalone watermarking is unclear.

Expected final response:

- State the chosen watermark contract.
- List docs updated.
- List validation results.

## WS-02-T06: Define Composite Coverage Variable Scan Contract

Workstream: WS-02  
Priority: P1  
Task type: contract documentation

Goal:

Define the correct contract for extracting variables from composite segment templates.

Files in scope:

- `docs/audits/full-project-review-2026-04-26/`
- `backend/src/main/java/com/docgen/service/CompositeCoverageService.java` for reading only unless implementation is explicitly assigned
- `docxtemplater-service/src/routes/*` for reading only unless implementation is explicitly assigned

Files out of scope:

- Production code changes.
- Tests.

Required behavior:

- Document current mismatch.
- Propose one target contract.
- Include request and response examples.
- Include test requirements for the later implementation task.

Security constraints:

- Keep file content English.

Data or migration constraints:

- None.

Tests required:

- Not required in this documentation task.

Validation commands:

```powershell
rg "[\\p{Han}]" docs/audits/full-project-review-2026-04-26
```

Stop conditions:

- Stop if the intended source of truth for variable scanning is unclear.

Expected final response:

- List document updated.
- Confirm no Chinese text was introduced.

## WS-02-T07: Implement Composite Coverage Variable Scan Contract

Workstream: WS-02  
Priority: P1  
Task type: contract implementation

Goal:

Implement the target variable scanning contract defined by WS-02-T06.

Files in scope:

- `backend/src/main/java/com/docgen/service/CompositeCoverageService.java`
- Target backend or Node route files from WS-02-T06
- Related tests

Files out of scope:

- General expression evaluation.
- Template rendering.
- Frontend coverage UI.

Required behavior:

- Stop using `/evaluate` as a variable extraction endpoint.
- Return variables from actual segment template content.
- Preserve existing coverage output shape unless explicitly changed by the contract.

Security constraints:

- Do not execute template expressions during variable scanning.
- Do not fetch external resources.

Data or migration constraints:

- No database changes.

Tests required:

- Segment template with variables returns expected variable names.
- Template without variables returns empty list.
- Missing or unreadable segment file has documented behavior.

Validation commands:

```powershell
cd backend
mvn test
```

```powershell
cd docxtemplater-service
npm test
```

Stop conditions:

- Stop if WS-02-T06 has not been completed.
- Stop if implementing the chosen contract requires a large parser redesign.

Expected final response:

- Summarize implementation.
- List tests and validation results.

## WS-04-T01: Add ContentDiffService Unit Tests

Workstream: WS-04  
Priority: P1  
Task type: test coverage

Goal:

Add focused tests for line diff behavior before changing the diff contract.

Files in scope:

- `backend/src/main/java/com/docgen/service/ContentDiffService.java`
- `backend/src/test/java/**`

Files out of scope:

- `DocxTextExtractor`
- MinIO integration
- Frontend files
- API documentation

Required behavior:

- Test added lines.
- Test removed lines.
- Test modified lines.
- Test empty strings.
- Test identical text according to the currently implemented behavior.

Security constraints:

- None beyond existing test safety.

Data or migration constraints:

- No database changes.

Tests required:

- Unit tests only.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if `computeLineDiff` visibility prevents testing without broad refactor.
- Escalate before changing production behavior in this test-only task.

Expected final response:

- List tests added.
- State the current identical-text behavior documented by tests.
- List validation results.

## WS-04-T02: Add DocxTextExtractor Unit Tests

Workstream: WS-04  
Priority: P1  
Task type: test coverage

Goal:

Add focused tests for docx text extraction behavior.

Files in scope:

- `backend/src/main/java/com/docgen/service/DocxTextExtractor.java`
- `backend/src/test/java/**`

Files out of scope:

- MinIO integration unless existing test utilities make it simple.
- Content diff service.
- Frontend files.

Required behavior:

- Test valid document text extraction.
- Test multiple paragraphs.
- Test missing `word/document.xml`.
- Test invalid ZIP input.
- Test truncation if practical without large fixtures.

Security constraints:

- Generate test docx archives in memory.
- Do not add binary fixtures unless necessary.

Data or migration constraints:

- No database changes.

Tests required:

- Unit tests for extraction and failure paths.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if generating in-memory docx ZIPs becomes too broad.

Expected final response:

- List tests added.
- List validation results.

## WS-04-T03: Decide Identical Diff Contract

Workstream: WS-04  
Priority: P1  
Task type: contract documentation

Goal:

Choose the canonical behavior for identical text diffs.

Files in scope:

- `.kiro/specs/docx-content-diff/requirements.md`
- `docs/segment-version-api.md`
- `docs/audits/full-project-review-2026-04-26/`

Files out of scope:

- Production code.
- Tests.

Required behavior:

- Decide whether identical text returns an empty list or all `EQUAL` lines.
- Document the chosen behavior.
- Record compatibility impact.

Security constraints:

- Keep all file content English.

Data or migration constraints:

- None.

Tests required:

- Not required for this documentation task.

Validation commands:

```powershell
rg "[\\p{Han}]" docs/audits/full-project-review-2026-04-26 docs/segment-version-api.md .kiro/specs/docx-content-diff
```

Stop conditions:

- Stop if product decision is unavailable.

Expected final response:

- State chosen contract.
- List documents updated.
- Confirm no Chinese text was introduced.

## WS-04-T04: Implement Identical Diff Contract

Workstream: WS-04  
Priority: P1  
Task type: contract implementation

Goal:

Update `ContentDiffService` to match the identical-text contract selected in WS-04-T03.

Files in scope:

- `backend/src/main/java/com/docgen/service/ContentDiffService.java`
- Backend tests

Files out of scope:

- `DocxTextExtractor`
- Frontend files unless response shape changes require a separate task.
- API docs.

Required behavior:

- Match the chosen identical-text contract.
- Preserve changed-text behavior.
- Preserve truncation behavior.

Security constraints:

- None.

Data or migration constraints:

- No database changes.

Tests required:

- Identical text behavior.
- Changed text behavior still passes.
- Empty text behavior remains documented.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if WS-04-T03 is not complete.

Expected final response:

- Summarize behavior change.
- List tests and validation results.

## WS-04-T05: Fix Docx XML Parse Failure Semantics

Workstream: WS-04  
Priority: P1  
Task type: correctness fix

Goal:

Prevent main document XML parse failures from silently becoming empty extracted text.

Files in scope:

- `backend/src/main/java/com/docgen/service/DocxTextExtractor.java`
- Backend tests

Files out of scope:

- Content diff algorithm.
- Frontend files.
- Migrations.

Required behavior:

- Main `word/document.xml` parse failure should fail extraction.
- Header/footer parse failures may follow a documented downgrade policy.
- Error code should remain consistent with content extraction failure.

Security constraints:

- Do not enable XML external entity processing.
- Preserve XXE protections.

Data or migration constraints:

- No database changes.

Tests required:

- Corrupt main document XML fails.
- Valid document still extracts.
- XXE protection remains enabled if existing tests can cover it.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if failure policy for header/footer XML is unclear.

Expected final response:

- Summarize failure semantics.
- List tests and validation results.

## WS-04-T06: Handle Concurrent Segment Publish Conflicts

Workstream: WS-04  
Priority: P1  
Task type: consistency fix

Goal:

Make concurrent segment version publishing produce controlled behavior.

Files in scope:

- `backend/src/main/java/com/docgen/service/SegmentVersionService.java`
- `backend/src/main/java/com/docgen/repository/SegmentVersionRepository.java`
- Backend tests

Files out of scope:

- Flyway migrations unless explicitly required.
- Frontend version dialog.
- Content diff behavior.

Required behavior:

- Concurrent publish conflicts should retry or fail with a clear business error.
- Do not produce duplicate version numbers.
- Preserve sequential publish behavior.

Security constraints:

- Preserve tenant isolation.

Data or migration constraints:

- Avoid migration changes unless a separate migration task is created.

Tests required:

- Sequential publish increments versions.
- Simulated duplicate version conflict is handled predictably.
- Concurrent behavior test if feasible.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if robust fix requires database locking or schema changes.

Expected final response:

- Summarize conflict strategy.
- List tests and validation results.

## WS-04-T07: Update Segment Version API Documentation

Workstream: WS-04  
Priority: P1  
Task type: documentation

Goal:

Update segment version API documentation to include content diff fields and parameters.

Files in scope:

- `docs/segment-version-api.md`
- `docs/audits/full-project-review-2026-04-26/05-traceability-matrix.md`
- `docs/audits/full-project-review-2026-04-26/07-iteration-log.md`

Files out of scope:

- Production code.
- Tests.

Required behavior:

- Document `includeContentDiff`.
- Document `contentChanged`.
- Document `truncated`.
- Document `contentDiffs`.
- Document identical-text behavior after WS-04-T03.

Security constraints:

- File content must be English.

Data or migration constraints:

- None.

Tests required:

- Not required.

Validation commands:

```powershell
rg "[\\p{Han}]" docs/segment-version-api.md docs/audits/full-project-review-2026-04-26
```

Stop conditions:

- Stop if WS-04-T03 is not complete.

Expected final response:

- List docs updated.
- Confirm no Chinese text was introduced.

## WS-04-T08: Add SegmentVersionDialog Tests

Workstream: WS-04  
Priority: P1  
Task type: frontend test coverage

Goal:

Add focused tests for segment version comparison UI behavior.

Files in scope:

- `frontend/src/views/template-workspace/components/SegmentVersionDialog.vue`
- `frontend/src/__tests__/**`

Files out of scope:

- Backend files.
- API implementation.
- OnlyOffice components.

Required behavior:

- Compare action sends `includeContentDiff=true`.
- `contentChanged=false` shows no-change state.
- `truncated=true` shows truncation warning.
- Content diff rows render added, removed, and modified lines.

Security constraints:

- None.

Data or migration constraints:

- None.

Tests required:

- Component tests with mocked API functions.

Validation commands:

```powershell
cd frontend
npm test
```

Stop conditions:

- Stop if current test setup cannot mount Element Plus dialog components without broad changes.

Expected final response:

- List tests added.
- List validation results.

## WS-04-T09: Fix SegmentVersionDialog Collapsed Diff Reactivity

Workstream: WS-04  
Priority: P2  
Task type: frontend bug fix

Goal:

Ensure expanding collapsed unchanged diff sections updates the UI reliably.

Files in scope:

- `frontend/src/views/template-workspace/components/SegmentVersionDialog.vue`
- `frontend/src/__tests__/**`

Files out of scope:

- Backend files.
- API changes.

Required behavior:

- Updating expanded groups must trigger recomputation or rerender.
- Existing progressive rendering behavior must remain.

Security constraints:

- None.

Data or migration constraints:

- None.

Tests required:

- Test expanding a collapsed group changes visible rows.

Validation commands:

```powershell
cd frontend
npm test
```

Stop conditions:

- Stop if test requires large DOM refactor.

Expected final response:

- Summarize reactivity fix.
- List tests and validation results.

## WS-05-T01: Add Generation Eligibility Characterization Tests

Workstream: WS-05  
Priority: P1  
Task type: test coverage

Goal:

Document current generation eligibility behavior across synchronous, asynchronous, and batch generation paths.

Files in scope:

- `backend/src/main/java/com/docgen/service/DynamicApiService.java`
- `backend/src/main/java/com/docgen/service/DocumentGeneratorService.java`
- `backend/src/main/java/com/docgen/service/AsyncDocumentService.java`
- `backend/src/main/java/com/docgen/service/BatchDocumentService.java`
- `backend/src/test/java/**`

Files out of scope:

- Production behavior changes.
- Template state machine changes.
- Frontend files.

Required behavior:

- Add tests that show which paths allow or reject non-`ACTIVE` templates today.
- Do not change production behavior in this task.

Security constraints:

- Preserve tenant isolation in tests.

Data or migration constraints:

- No database changes.

Tests required:

- Synchronous path characterization.
- Asynchronous path characterization.
- Batch path characterization if practical.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if test setup requires large integration fixtures.

Expected final response:

- Summarize current behavior found.
- List tests and validation results.

## WS-05-T02: Centralize Generation Eligibility Rule

Workstream: WS-05  
Priority: P1  
Task type: business consistency fix

Goal:

Create one shared backend rule for whether a template can generate documents.

Files in scope:

- `backend/src/main/java/com/docgen/service/**`
- `backend/src/test/java/**`

Files out of scope:

- Frontend files.
- Migrations.
- Versioned rendering behavior.

Required behavior:

- Define one shared eligibility check.
- Apply it to synchronous, asynchronous, batch, and internal generation entry points.
- Preserve intended `ACTIVE` behavior.

Security constraints:

- Preserve tenant isolation.
- Do not allow generation for templates from another tenant.

Data or migration constraints:

- No database changes.

Tests required:

- Non-`ACTIVE` templates are consistently rejected or handled according to the chosen rule.
- `ACTIVE` templates still generate through supported paths.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if product rules for `DRAFT` generation are unclear.

Expected final response:

- State the shared rule.
- List paths updated.
- List tests and validation results.

## WS-05-T03: Characterize Version Parameter Behavior

Workstream: WS-05  
Priority: P1  
Task type: test coverage

Goal:

Document current behavior of the generate API `version` parameter before changing it.

Files in scope:

- `backend/src/main/java/com/docgen/service/DynamicApiService.java`
- `backend/src/main/java/com/docgen/service/DocumentGeneratorService.java`
- `backend/src/test/java/**`

Files out of scope:

- Production behavior changes.
- Frontend files.
- Migrations.

Required behavior:

- Add tests that show whether `version` changes the rendered file path.
- Add tests for invalid version handling if practical.

Security constraints:

- Preserve access checks.

Data or migration constraints:

- No database changes.

Tests required:

- Current version behavior characterization.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if existing test infrastructure cannot represent template versions.

Expected final response:

- Summarize current `version` behavior.
- List tests and validation results.

## WS-05-T04: Decide Versioned Rendering Contract

Workstream: WS-05  
Priority: P1  
Task type: contract documentation

Goal:

Decide whether document generation supports historical template versions.

Files in scope:

- API docs under `docs/`
- Audit package files
- Relevant `.kiro/specs` files if they describe versioned generation

Files out of scope:

- Production code.
- Tests.

Required behavior:

- Document whether `?version=n` is supported.
- If supported, define the expected file source and validation behavior.
- If not supported, define rejection behavior.

Security constraints:

- File content must be English.

Data or migration constraints:

- None.

Tests required:

- Not required.

Validation commands:

```powershell
rg "[\\p{Han}]" docs .kiro/specs
```

Stop conditions:

- Stop if product decision is unavailable.

Expected final response:

- State chosen contract.
- List docs updated.

## WS-05-T05: Implement Versioned Rendering Contract

Workstream: WS-05  
Priority: P1  
Task type: business implementation

Goal:

Implement the versioned rendering behavior selected in WS-05-T04.

Files in scope:

- `backend/src/main/java/com/docgen/service/DynamicApiService.java`
- `backend/src/main/java/com/docgen/service/DocumentGeneratorService.java`
- Template version related services or repositories as needed
- Backend tests

Files out of scope:

- Frontend UI.
- Migrations unless separately assigned.

Required behavior:

- If supported, render from the selected version's file path.
- If not supported, reject the parameter explicitly.
- Preserve current generation behavior when no version is requested.

Security constraints:

- Enforce tenant and template ownership checks.

Data or migration constraints:

- No migration changes unless explicitly assigned.

Tests required:

- No version uses current template.
- Requested version uses selected version or is rejected according to contract.
- Invalid version fails clearly.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if WS-05-T04 is not complete.
- Stop if required version file path data is missing.

Expected final response:

- Summarize implemented behavior.
- List tests and validation results.

## WS-05-T06: Align Composite Activation with State Machine

Workstream: WS-05  
Priority: P1  
Task type: business consistency fix

Goal:

Ensure composite template activation follows the intended lifecycle rule.

Files in scope:

- `backend/src/main/java/com/docgen/service/CompositeTemplateService.java`
- `backend/src/main/java/com/docgen/service/TemplateStateMachineService.java`
- Backend tests

Files out of scope:

- Frontend UI.
- Marketplace.
- Migrations.

Required behavior:

- Either route composite activation through the state machine or explicitly enforce a documented separate rule.
- Preserve validation that composite templates are complete before activation.

Security constraints:

- Preserve authorization and tenant checks.

Data or migration constraints:

- No database changes.

Tests required:

- Valid composite activation succeeds according to rule.
- Invalid transition is rejected.
- Incomplete composite template cannot activate.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if product lifecycle rules are unclear.

Expected final response:

- State lifecycle rule.
- List tests and validation results.

## WS-05-T07: Characterize Template Test Service Semantics

Workstream: WS-05  
Priority: P2  
Task type: test and documentation

Goal:

Document whether template test cases run the real generation pipeline or only compare stored JSON.

Files in scope:

- `backend/src/main/java/com/docgen/service/TemplateTestService.java`
- `backend/src/test/java/**`
- Audit package files

Files out of scope:

- Production behavior changes unless explicitly assigned.
- Frontend files.

Required behavior:

- Add tests or documentation showing current behavior.
- Record whether current behavior matches product expectations.

Security constraints:

- None.

Data or migration constraints:

- No database changes.

Tests required:

- Characterize current test execution behavior.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if product expectation is needed before writing docs.

Expected final response:

- Summarize current semantics.
- List tests or docs updated.

## WS-06-T01: Add Workspace Route Reuse Test

Workstream: WS-06  
Priority: P2  
Task type: frontend test coverage

Goal:

Characterize workspace behavior when route `templateId` changes without remounting the component.

Files in scope:

- `frontend/src/views/template-workspace/Index.vue`
- `frontend/src/__tests__/**`

Files out of scope:

- Store redesign.
- Backend files.
- OnlyOffice components.

Required behavior:

- Add a test that simulates route parameter change.
- Document whether workspace data reloads today.

Security constraints:

- None.

Data or migration constraints:

- None.

Tests required:

- Route parameter change characterization.

Validation commands:

```powershell
cd frontend
npm test
```

Stop conditions:

- Stop if router test setup is not available.

Expected final response:

- Summarize current behavior.
- List tests and validation results.

## WS-06-T02: Fix Workspace Reload on Route Change

Workstream: WS-06  
Priority: P2  
Task type: frontend bug fix

Goal:

Reload workspace state when the route template ID changes.

Files in scope:

- `frontend/src/views/template-workspace/Index.vue`
- `frontend/src/stores/templateWorkspace.ts` only if needed
- `frontend/src/__tests__/**`

Files out of scope:

- Backend files.
- OnlyOffice components.

Required behavior:

- Route changes from one template ID to another trigger workspace reinitialization.
- Existing mount initialization still works.
- Store reset behavior remains safe.

Security constraints:

- None.

Data or migration constraints:

- None.

Tests required:

- Route ID change reloads data.
- Existing initialization test still passes.

Validation commands:

```powershell
cd frontend
npm test
```

Stop conditions:

- Stop if fixing requires router architecture changes.

Expected final response:

- Summarize reload mechanism.
- List tests and validation results.

## WS-06-T03: Add OnlyOfficeEditor Prop Change Tests

Workstream: WS-06  
Priority: P2  
Task type: frontend test coverage

Goal:

Characterize how `OnlyOfficeEditor` behaves when document props change.

Files in scope:

- `frontend/src/components/OnlyOfficeEditor.vue`
- `frontend/src/__tests__/**`

Files out of scope:

- Parent workspace components.
- Backend files.

Required behavior:

- Mock `DocsAPI`.
- Mount the component with one document.
- Change `documentUrl` or `documentKey`.
- Document whether the editor is recreated.

Security constraints:

- Do not call real OnlyOffice scripts.

Data or migration constraints:

- None.

Tests required:

- Prop change characterization test.

Validation commands:

```powershell
cd frontend
npm test
```

Stop conditions:

- Stop if test setup cannot mock dynamic script loading.

Expected final response:

- Summarize current behavior.
- List tests and validation results.

## WS-06-T04: Reinitialize OnlyOfficeEditor on Document Change

Workstream: WS-06  
Priority: P2  
Task type: frontend bug fix

Goal:

Ensure `OnlyOfficeEditor` loads the correct document after `documentUrl` or `documentKey` changes.

Files in scope:

- `frontend/src/components/OnlyOfficeEditor.vue`
- `frontend/src/__tests__/**`

Files out of scope:

- Parent workspace components unless a key-based remount is chosen and required.
- Backend files.

Required behavior:

- Recreate or remount the editor on document identity change.
- Destroy the old editor before creating the new one.
- Preserve locale-change behavior.

Security constraints:

- Do not proceed silently without signing if the configured security model requires a token.

Data or migration constraints:

- None.

Tests required:

- Changing document key or URL destroys old editor and creates a new one.
- Existing mount behavior still works.

Validation commands:

```powershell
cd frontend
npm test
```

Stop conditions:

- Stop if token signing failure behavior is unclear.

Expected final response:

- Summarize reinitialization behavior.
- List tests and validation results.

## WS-06-T05: Remove DesignStage Debug Logging

Workstream: WS-06  
Priority: P2  
Task type: cleanup

Goal:

Remove production debug logging from `DesignStage`.

Files in scope:

- `frontend/src/views/template-workspace/components/DesignStage.vue`

Files out of scope:

- Other frontend components.
- Backend files.

Required behavior:

- Remove debug `console.log` statements.
- Preserve user-facing behavior.
- Keep useful error handling if any exists.

Security constraints:

- Do not log sensitive editor data.

Data or migration constraints:

- None.

Tests required:

- Existing tests should pass.
- Add tests only if behavior changes.

Validation commands:

```powershell
cd frontend
npm test
```

Stop conditions:

- Stop if logs are required by an existing debug flag convention.

Expected final response:

- List removed logs.
- List validation results.

## WS-06-T06: Fix VersionDiffPanel Non-Text Diff Display

Workstream: WS-06  
Priority: P2  
Task type: frontend bug fix

Goal:

Display diff details when only non-text diff categories exist.

Files in scope:

- `frontend/src/views/template-workspace/components/VersionDiffPanel.vue`
- `frontend/src/__tests__/**`

Files out of scope:

- Backend version diff API.
- Segment version dialog.

Required behavior:

- The details table should render when `allDiffs.length > 0`.
- Text-only, variable-only, data-source-only, and expression-only diffs should display.

Security constraints:

- None.

Data or migration constraints:

- None.

Tests required:

- Existing text diff test.
- New non-text-only diff test.

Validation commands:

```powershell
cd frontend
npm test
```

Stop conditions:

- Stop if API type definitions do not support non-text diff categories.

Expected final response:

- Summarize display condition change.
- List tests and validation results.

## WS-06-T07: Complete Frontend i18n for Reviewed Components

Workstream: WS-06  
Priority: P2  
Task type: frontend localization

Goal:

Remove hardcoded user-facing strings from reviewed frontend components.

Files in scope:

- `frontend/src/components/OnlyOfficeEditor.vue`
- `frontend/src/views/template-workspace/components/VersionDiffPanel.vue`
- `frontend/src/views/template-workspace/components/SegmentVersionDialog.vue`
- `frontend/src/api/request.ts`
- `frontend/src/i18n/en-US.json`
- `frontend/src/i18n/zh-CN.json`
- `frontend/src/i18n/zh-TW.json`

Files out of scope:

- Unrelated components.
- Backend files.

Required behavior:

- Add missing keys.
- Replace hardcoded user-facing strings with i18n calls where practical.
- Keep JSON keys and values valid.
- File content may include localized strings in i18n resource files only if the project intentionally stores localized UI text there.

Security constraints:

- Do not include sensitive values in messages.

Data or migration constraints:

- None.

Tests required:

- Existing frontend tests should pass.
- Add focused tests only if components already have coverage.

Validation commands:

```powershell
cd frontend
npm test
```

Stop conditions:

- Stop if the language policy conflicts with existing i18n resource files.

Expected final response:

- List keys added.
- List validation results.

## WS-07-T01: Classify Dirty Working Tree Artifacts

Workstream: WS-07  
Priority: P1  
Task type: release governance documentation

Goal:

Classify current uncommitted and untracked files before implementation work continues.

Files in scope:

- `docs/audits/full-project-review-2026-04-26/`
- Git status output may be used as evidence.

Files out of scope:

- Production code.
- Tests.
- Deleting files.
- Git commits.

Required behavior:

- Create an artifact classification document.
- Classify files into feature code, documentation, sample assets, local debug files, generated artifacts, binaries, and ignore candidates.
- Do not modify or delete classified files.

Security constraints:

- Flag possible secrets or local credentials.
- Do not copy secret values into the document.

Data or migration constraints:

- None.

Tests required:

- Not required.

Validation commands:

```powershell
rg "[\\p{Han}]" docs/audits/full-project-review-2026-04-26
```

Stop conditions:

- Stop if files appear to contain secrets; record paths only and escalate.

Expected final response:

- List document created or updated.
- Confirm no files were deleted.
- Confirm no Chinese text was introduced.

## WS-07-T02: Add Backend CI Stage

Workstream: WS-07  
Priority: P2  
Task type: CI setup

Goal:

Add a CI stage for backend validation.

Files in scope:

- CI configuration files.
- `backend/pom.xml` only if a minimal test plugin configuration is required.
- Audit package status files.

Files out of scope:

- Production Java code.
- Frontend CI.
- Node service CI.
- Docker publishing.

Required behavior:

- Run backend tests in CI.
- Use a stable Java version compatible with the project.
- Cache dependencies if supported by the chosen CI system.

Security constraints:

- Do not include secrets in CI config.
- Do not disable tests.

Data or migration constraints:

- Do not run destructive migrations against shared databases.

Tests required:

- CI config should be syntactically valid.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if the repository CI provider is unknown.

Expected final response:

- State CI provider and backend command.
- List validation results.

## WS-07-T03: Add Frontend CI Stage

Workstream: WS-07  
Priority: P2  
Task type: CI setup

Goal:

Add a CI stage for frontend validation.

Files in scope:

- CI configuration files.
- `frontend/package.json` only if scripts need minimal adjustment.
- Audit package status files.

Files out of scope:

- Frontend feature code.
- Backend CI.
- Node service CI.

Required behavior:

- Run dependency install using lockfile.
- Run type checks.
- Run tests.
- Run build if feasible.

Security constraints:

- Do not expose secrets.
- Do not use lint auto-fix in CI.

Data or migration constraints:

- None.

Tests required:

- CI config should be syntactically valid.

Validation commands:

```powershell
cd frontend
npm ci
npm run type-check
npm test
npm run build
```

Stop conditions:

- Stop if the CI provider is unknown.
- Stop if dependency installation requires environment-specific registry credentials.

Expected final response:

- State CI provider and frontend commands.
- List validation results.

## WS-07-T04: Add Docxtemplater CI Stage

Workstream: WS-07  
Priority: P2  
Task type: CI setup

Goal:

Add a CI stage for Docxtemplater service validation.

Files in scope:

- CI configuration files.
- `docxtemplater-service/package.json` only if scripts need minimal adjustment.
- Audit package status files.

Files out of scope:

- Backend CI.
- Frontend CI.
- Production Node route behavior.

Required behavior:

- Install dependencies using lockfile.
- Run Jest tests.
- Do not require LibreOffice integration tests in the first CI stage unless the environment supports them.

Security constraints:

- Do not expose secrets.

Data or migration constraints:

- None.

Tests required:

- CI config should be syntactically valid.

Validation commands:

```powershell
cd docxtemplater-service
npm ci
npm test
```

Stop conditions:

- Stop if native dependencies fail without a documented workaround.

Expected final response:

- State CI provider and Node commands.
- List validation results.

## WS-07-T05: Create Migration Runbook

Workstream: WS-07  
Priority: P1  
Task type: release documentation

Goal:

Document database migration risks and upgrade steps around V30, V36, and V39.

Files in scope:

- `docs/audits/full-project-review-2026-04-26/`
- Existing migration files may be read but not changed.

Files out of scope:

- Flyway SQL changes.
- Production code.
- Tests.

Required behavior:

- Document new database install path.
- Document existing database upgrade risk.
- Document backup requirements.
- Document rollback limitations.
- Document unresolved decisions.

Security constraints:

- Do not include real production connection strings.

Data or migration constraints:

- Do not modify migrations.

Tests required:

- Not required.

Validation commands:

```powershell
rg "[\\p{Han}]" docs/audits/full-project-review-2026-04-26
```

Stop conditions:

- Stop if actual production schema history is required.

Expected final response:

- List document created or updated.
- Confirm no Chinese text was introduced.

## WS-07-T06: Create Release Runbook

Workstream: WS-07  
Priority: P2  
Task type: release documentation

Goal:

Create a release runbook for build, test, backup, deployment, and rollback.

Files in scope:

- `docs/audits/full-project-review-2026-04-26/`
- Existing Docker and config files may be read but not changed.

Files out of scope:

- CI implementation.
- Dockerfile changes.
- Production code.

Required behavior:

- Include pre-release checks.
- Include backup steps.
- Include deployment steps.
- Include post-release smoke tests.
- Include rollback limitations.

Security constraints:

- Do not include real secrets.

Data or migration constraints:

- Clearly state that database rollback requires backup restore or a planned migration strategy.

Tests required:

- Not required.

Validation commands:

```powershell
rg "[\\p{Han}]" docs/audits/full-project-review-2026-04-26
```

Stop conditions:

- Stop if production platform details are required.

Expected final response:

- List document created or updated.
- Confirm no Chinese text was introduced.

## WS-08-T01: Pin Floating Docker Image Tags

Workstream: WS-08  
Priority: P2  
Task type: infrastructure hardening

Goal:

Replace floating container image tags with pinned versions where practical.

Files in scope:

- `docker-compose.yml`
- Docker-related documentation in the audit package

Files out of scope:

- Dockerfile runtime user changes.
- Application code.
- CI.

Required behavior:

- Identify floating tags.
- Replace with explicit version tags if a safe target is known.
- Document any image that cannot be pinned yet.

Security constraints:

- Do not change secrets.

Data or migration constraints:

- Changing database or object storage image versions must be conservative and documented.

Tests required:

- Not required, but Docker config should remain valid.

Validation commands:

```powershell
docker compose config
```

Stop conditions:

- Stop if safe target versions are unknown.

Expected final response:

- List image tags changed.
- List validation results.

## WS-08-T02: Add Compose Resource Limits

Workstream: WS-08  
Priority: P2  
Task type: infrastructure hardening

Goal:

Add CPU and memory guidance or Compose resource limits for heavy services.

Files in scope:

- `docker-compose.yml`
- Audit package documentation

Files out of scope:

- Kubernetes manifests.
- Application code.
- Dockerfile changes.

Required behavior:

- Add or document limits for Docxtemplater, OnlyOffice, backend, PostgreSQL, Redis, and MinIO.
- Be conservative for local development compatibility.

Security constraints:

- None.

Data or migration constraints:

- Do not change volumes.

Tests required:

- Not required, but Docker config should remain valid.

Validation commands:

```powershell
docker compose config
```

Stop conditions:

- Stop if Compose version compatibility is unclear.

Expected final response:

- List limits added or documented.
- List validation results.

## WS-08-T03: Improve Frontend Docker Reproducibility

Workstream: WS-08  
Priority: P2  
Task type: Docker hardening

Goal:

Make frontend Docker builds use lockfile-based dependency installation.

Files in scope:

- `frontend/Dockerfile`
- `frontend/Dockerfile.local` if applicable

Files out of scope:

- Frontend application code.
- CI configuration.

Required behavior:

- Prefer `npm ci` over `npm install` for production build images.
- Preserve build arguments.
- Do not silently skip required type checks unless documented.

Security constraints:

- Do not add registry credentials.

Data or migration constraints:

- None.

Tests required:

- Not required.

Validation commands:

```powershell
docker compose build frontend
```

Stop conditions:

- Stop if lockfile and package file are inconsistent.

Expected final response:

- Summarize Dockerfile change.
- List validation results.

## WS-08-T04: Add Non-Root Runtime Users

Workstream: WS-08  
Priority: P2  
Task type: Docker hardening

Goal:

Run application containers as non-root users where practical.

Files in scope:

- `backend/Dockerfile`
- `backend/Dockerfile.local`
- `frontend/Dockerfile`
- `docxtemplater-service/Dockerfile`

Files out of scope:

- Compose networking.
- Application code.
- CI.

Required behavior:

- Add non-root users where compatible.
- Ensure writable directories remain writable.
- Preserve service startup commands.

Security constraints:

- Do not grant broad filesystem permissions.

Data or migration constraints:

- Do not change named volumes.

Tests required:

- Not required.

Validation commands:

```powershell
docker compose build
```

Stop conditions:

- Stop if LibreOffice or Nginx requires broader runtime changes.

Expected final response:

- List containers changed.
- List validation results.

## WS-03-T01: Add ZIP Import Boundary Tests

Workstream: WS-03  
Priority: P0  
Task type: test coverage

Goal:

Characterize ZIP import behavior around large entries, unexpected paths, and invalid archives before hardening.

Files in scope:

- `backend/src/main/java/com/docgen/service/CompositeImportExportService.java`
- `backend/src/test/java/**`

Files out of scope:

- Production hardening changes.
- Frontend import UI.
- Docxtemplater service files.
- Flyway migrations.

Required behavior:

- Add tests for invalid ZIP.
- Add tests for unexpected entry paths.
- Add tests for very large or simulated oversized entries if practical.

Security constraints:

- Do not introduce real large binary test fixtures.
- Generate test archives in memory.

Data or migration constraints:

- No database migration changes.

Tests required:

- Invalid archive test.
- Unexpected path test.
- Size or entry count characterization test if practical.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if test setup requires real MinIO and no existing pattern is available.

Expected final response:

- List tests added.
- Explain current unsafe behavior documented by tests.
- List validation results.

## WS-03-T02: Add ZIP Import Limits

Workstream: WS-03  
Priority: P0  
Task type: security hardening

Goal:

Add ZIP import limits for total size, entry count, per-entry size, and allowed entry paths.

Files in scope:

- `backend/src/main/java/com/docgen/service/CompositeImportExportService.java`
- `backend/src/main/resources/application.yml` if configuration is needed
- `backend/src/test/java/**`

Files out of scope:

- `render-config.json` feature work.
- Frontend UI.
- Migrations.

Required behavior:

- Reject archives exceeding configured limits.
- Reject unsupported paths.
- Reject unsafe names.
- Preserve valid existing ZIP import behavior.

Security constraints:

- Avoid `readAllBytes()` for unbounded entries.
- Do not save partial unsafe content.

Data or migration constraints:

- No database changes.

Tests required:

- Oversized entry rejected.
- Too many entries rejected.
- Unsafe path rejected.
- Valid archive still imports.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if changing import streaming requires broad service redesign.

Expected final response:

- List configured limits.
- List tests and validation results.

## WS-03-T03: Normalize Imported Object Names

Workstream: WS-03  
Priority: P0  
Task type: storage safety

Goal:

Ensure imported segment, header, and footer names cannot create unsafe or unexpected object keys.

Files in scope:

- `backend/src/main/java/com/docgen/service/CompositeImportExportService.java`
- `backend/src/test/java/**`

Files out of scope:

- ZIP size limits.
- Frontend files.
- Migrations.

Required behavior:

- Sanitize object key name components consistently.
- Preserve user-visible segment names where product behavior requires it.
- Prevent path separators or traversal-like names from affecting object key structure.

Security constraints:

- Do not use raw ZIP entry names in object keys.
- Do not log full object paths if they may contain unsafe input.

Data or migration constraints:

- No migration changes.
- Existing imported objects are not renamed by this task.

Tests required:

- Name with slashes is sanitized in object path.
- Name with traversal-like content is sanitized.
- Normal names remain readable.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if product requires preserving exact file names in storage keys.

Expected final response:

- Describe sanitization rule.
- List tests and validation results.

## WS-03-T04: Decide Composite R7 Render Config Scope

Workstream: WS-03  
Priority: P1  
Task type: product and contract documentation

Goal:

Decide whether `render-config.json` is implemented now or deferred.

Files in scope:

- `.kiro/specs/composite-template-full-import/requirements.md`
- `.kiro/specs/composite-template-full-import/tasks.md`
- `docs/audits/full-project-review-2026-04-26/`

Files out of scope:

- Production code.
- Tests.

Required behavior:

- Document one decision: implement now or defer.
- If implementing, define exact JSON shape.
- If deferring, record reason, impact, and target version.
- Keep all file content English.

Security constraints:

- Do not add ambiguous render config that can fetch external resources without validation.

Data or migration constraints:

- None.

Tests required:

- Not required for this documentation decision.

Validation commands:

```powershell
rg "[\\p{Han}]" docs/audits/full-project-review-2026-04-26 .kiro/specs/composite-template-full-import
```

Stop conditions:

- Stop if product decision is unavailable.

Expected final response:

- State implement or defer.
- List documents updated.
- Confirm no Chinese text was introduced.

## WS-03-T05: Implement Composite R7 Export

Workstream: WS-03  
Priority: P1  
Task type: feature implementation

Goal:

Export `render-config.json` for composite templates if WS-03-T04 selects implementation.

Files in scope:

- `backend/src/main/java/com/docgen/service/CompositeImportExportService.java`
- Related DTOs or entities if needed
- Backend tests

Files out of scope:

- Import behavior.
- Frontend UI.
- Migrations unless explicitly required by WS-03-T04.

Required behavior:

- Add `render-config.json` to ZIP when render config exists.
- Omit it or write an empty documented structure when render config does not exist, according to WS-03-T04.
- Preserve existing ZIP files.

Security constraints:

- Do not export secrets.
- Do not include environment-specific URLs unless explicitly required.

Data or migration constraints:

- Follow WS-03-T04.

Tests required:

- Export includes render config when present.
- Export behavior without render config is documented and tested.
- Existing required ZIP files are still present.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if WS-03-T04 is not complete.
- Stop if render config storage location is unclear.

Expected final response:

- Summarize export behavior.
- List tests and validation results.

## WS-03-T06: Implement Composite R7 Import

Workstream: WS-03  
Priority: P1  
Task type: feature implementation

Goal:

Import `render-config.json` for composite template packages if WS-03-T04 selects implementation.

Files in scope:

- `backend/src/main/java/com/docgen/service/CompositeImportExportService.java`
- Related DTOs or entities if needed
- Backend tests

Files out of scope:

- Export behavior.
- Frontend UI.
- Migrations unless explicitly required by WS-03-T04.

Required behavior:

- Parse optional `render-config.json`.
- Store imported render config according to WS-03-T04.
- Preserve backward compatibility for ZIP files without render config.

Security constraints:

- Validate imported render config.
- Do not allow external resource references unless explicitly allowed by policy.

Data or migration constraints:

- Follow WS-03-T04.

Tests required:

- Import with render config.
- Import without render config.
- Import with invalid render config is rejected or ignored according to contract.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if WS-03-T04 is not complete.
- Stop if storage location requires a migration not covered by the task.

Expected final response:

- Summarize import behavior.
- List tests and validation results.

## WS-01-T05: Add SSRF URL Policy Helper

Workstream: WS-01  
Priority: P0  
Task type: security helper

Goal:

Create a reusable backend helper for validating outbound URLs before server-side downloads or callbacks use them.

Files in scope:

- `backend/src/main/java/com/docgen/**`
- `backend/src/test/java/**`
- `backend/src/main/resources/application.yml` if configuration keys are needed

Files out of scope:

- OnlyOffice callback save behavior
- Webhook delivery behavior
- Frontend files
- Docxtemplater service files
- Flyway migrations

Required behavior:

- Allow only configured schemes, hosts, and ports.
- Reject loopback, link-local, private, multicast, and metadata IP ranges unless explicitly configured for development.
- Resolve hostnames safely and validate resolved addresses.
- Return clear validation errors.

Security constraints:

- Default policy must be deny-by-default for unknown hosts.
- Do not rely on string prefix checks alone.
- Do not add DNS caching with unsafe stale behavior.

Data or migration constraints:

- No database changes.

Tests required:

- Reject `localhost`.
- Reject `127.0.0.1`.
- Reject private IPv4 ranges.
- Reject metadata address examples.
- Allow a configured host.
- Reject unsupported schemes.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if configuration requirements are unclear.
- Escalate before allowing private IPs by default.

Expected final response:

- Summarize policy defaults.
- List tests added.
- List validation results.

## WS-01-T06: Apply URL Policy to Main OnlyOffice Callback

Workstream: WS-01  
Priority: P0  
Task type: security integration

Goal:

Use the SSRF URL policy helper in the main template OnlyOffice callback download path.

Files in scope:

- `backend/src/main/java/com/docgen/service/OnlyOfficeService.java`
- Shared URL policy helper from WS-01-T05
- Focused backend tests

Files out of scope:

- Segment callback path
- Webhook path
- Callback authentication mechanism
- Frontend files
- Migrations

Required behavior:

- Validate the callback download URL before any HTTP request.
- Reject invalid URLs without calling `RestTemplate`.
- Preserve valid URL behavior.

Security constraints:

- Do not bypass the helper for fallback behavior.
- Do not log full sensitive URLs if they may include tokens.

Data or migration constraints:

- No database changes.

Tests required:

- Disallowed URL does not invoke `RestTemplate`.
- Allowed URL invokes the existing download/save path.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if WS-01-T05 is not implemented.

Expected final response:

- Summarize integration points.
- List tests added or changed.
- List validation results.

## WS-01-T07: Apply URL Policy to Segment OnlyOffice Callback

Workstream: WS-01  
Priority: P0  
Task type: security integration

Goal:

Use the SSRF URL policy helper in the segment-level OnlyOffice callback path.

Files in scope:

- `backend/src/main/java/com/docgen/controller/CompositeTemplateController.java`
- Shared URL policy helper from WS-01-T05
- Focused backend tests

Files out of scope:

- Main template callback path
- Webhook path
- Callback authentication mechanism
- Frontend files
- Migrations

Required behavior:

- Validate the callback download URL before any HTTP request.
- Preserve content isolation validation after a valid download.
- Reject invalid URLs without overwriting segment files.

Security constraints:

- Do not weaken content isolation validation.
- Do not duplicate policy logic.

Data or migration constraints:

- No database changes.

Tests required:

- Disallowed URL does not invoke `RestTemplate`.
- Allowed URL reaches content isolation validation.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if WS-01-T05 is not implemented.

Expected final response:

- Summarize integration points.
- List tests added or changed.
- List validation results.

## WS-01-T08: Apply URL Policy to Webhook Delivery

Workstream: WS-01  
Priority: P0  
Task type: security integration

Goal:

Prevent tenant-configured webhook URLs from targeting internal or disallowed network locations.

Files in scope:

- `backend/src/main/java/com/docgen/service/WebhookService.java`
- Shared URL policy helper from WS-01-T05
- Focused backend tests

Files out of scope:

- OnlyOffice callback behavior
- Webhook UI
- Database migrations

Required behavior:

- Validate webhook target URL before delivery.
- Reject disallowed URLs with a clear error or failed delivery status.
- Preserve existing HMAC behavior for allowed URLs.

Security constraints:

- Do not send requests to disallowed URLs.
- Do not log webhook secrets or full signed payloads.

Data or migration constraints:

- No database changes.

Tests required:

- Private URL is rejected.
- Allowed URL preserves existing request signing behavior.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if product behavior for invalid webhook configuration is unclear.

Expected final response:

- Summarize rejection behavior.
- List tests added or changed.
- List validation results.

## WS-01-T09: Enforce Callback Download Size Limit

Workstream: WS-01  
Priority: P0  
Task type: security hardening

Goal:

Prevent callback downloads from reading unbounded response bodies into memory.

Files in scope:

- `backend/src/main/java/com/docgen/service/OnlyOfficeService.java`
- `backend/src/main/java/com/docgen/controller/CompositeTemplateController.java`
- Shared download helper if introduced
- Focused backend tests

Files out of scope:

- RestTemplate timeout configuration
- URL policy helper
- Frontend files
- Migrations

Required behavior:

- Enforce a maximum download size before saving to MinIO.
- Reject empty downloads.
- Preserve valid docx downloads.

Security constraints:

- Do not call `getForObject(..., byte[].class)` for unbounded downloads if a streaming alternative is available.
- Do not save partial oversized content.

Data or migration constraints:

- No database changes.

Tests required:

- Empty response is rejected.
- Oversized response is rejected.
- Valid response is saved.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if existing RestTemplate patterns make streaming changes too broad.

Expected final response:

- State the configured size limit.
- List tests and validation results.

## WS-01-T10: Enforce Production Secret Startup Checks

Workstream: WS-01  
Priority: P0  
Task type: configuration safety

Goal:

Prevent production startup with placeholder or weak default secrets.

Files in scope:

- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/docgen/**`
- `backend/src/test/java/**`

Files out of scope:

- Docker secret management
- Frontend files
- Node service files
- Migrations

Required behavior:

- Detect placeholder JWT, encryption, MinIO, and OnlyOffice secrets when production profile is active.
- Fail startup with a clear message.
- Allow test and local development profiles to use explicit test values.

Security constraints:

- Do not print secret values in error messages.
- Do not weaken existing token validation.

Data or migration constraints:

- No database changes.

Tests required:

- Production profile with placeholder secret fails.
- Non-production test profile does not fail because of test placeholders.
- Error message identifies the property name but not the secret value.

Validation commands:

```powershell
cd backend
mvn test
```

Stop conditions:

- Stop if the project has no profile convention for production.

Expected final response:

- List protected properties.
- List tests and validation results.
