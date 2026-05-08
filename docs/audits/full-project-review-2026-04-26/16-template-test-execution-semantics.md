# Template Test Execution Semantics (WS-05-T07)

This note records **observed** behavior of `TemplateTestService` as of the remediation pass. It answers whether template tests run the real generation pipeline or only compare stored JSON.

## Summary

- **Single test (`runTestCase`)** and **batch (`runAllTests`)** execute the **real render path** by calling `DocumentGeneratorService.renderForTemplateTest(templateId, parameters)` where `parameters` is parsed from the case’s `testDataJson`. That entry point **does not** enforce `Template` `ACTIVE` status (see `TemplateGenerationEligibilityService` for persisted document generation only).
- Assertions are **not** “compare two JSON blobs from the database without rendering.” They compare **render outputs** (variable map, extracted DOCX text, or DOCX byte hash) against `expectedResultJson` according to `comparisonType`.
- **CRUD and listing** (`createTestCase`, `updateTestCase`, `deleteTestCase`, `listTestCases`) do **not** invoke the document generator or `DocxTextExtractor`.

## Comparison modes (after render)

| `comparisonType` | Render output used | Assertion |
| --- | --- | --- |
| `VARIABLE_VALUE` | `TemplateTestRenderOutcome.dataContext()` | Key-by-key comparison against expected map |
| `TEXT_CONTENT` | DOCX bytes → `DocxTextExtractor.extractText` (invoked twice per run: comparison and persisted actual summary) | Plain text vs `_textContent` in expected JSON |
| `FILE_SNAPSHOT` | DOCX bytes | SHA-256 vs `_snapshotHash` in expected JSON |

## `runAllTests`

Loads all cases for the template (`findByTemplateIdOrderByCreatedAtDesc`) and calls `runTestCase` once per case, so each case triggers a full render + comparison + persisted `TestResult`.

## Evidence

- Implementation: `backend/src/main/java/com/docgen/service/TemplateTestService.java` (`runTestCase`, `runComparison`, `runAllTests`).
- Characterization tests (method names prefixed with `characterization_`): `backend/src/test/java/com/docgen/service/TemplateTestServiceTest.java`.

## Product expectation

Whether “real pipeline in tests” is desired long-term is a **product** decision. This document only characterizes current behavior; changing to a lighter or mocked pipeline would be a separate task.
