---
inclusion: manual
name: spec-review-checklist
description: Spec review methodology — fix-then-reread loops and five review passes
---

# Spec review methodology

## Fix-then-reread loop

1. Reread the full document each pass (no memory-only review)
2. Check every item for the current dimension
3. Fix issues one by one (no batch deferral)
4. Reread after fixes to catch regressions
5. Proceed to the next pass only after sign-off

Forbidden: batching fixes across unrelated issues, skipping rereads, mixing dimensions in one pass.

## Five passes

| Pass | Dimension | Focus |
|------|-----------|-------|
| 1 | Structure | Sections complete, numbering contiguous, no stray optionals |
| 2 | Cross-doc | AC ↔ design ↔ tasks references; paths/names aligned |
| 3 | Code truth | Signatures, `ErrorCode`, DTO fields, TS types |
| 4 | Edge cases | Empty data, API failures, concurrency, null, large payloads |
| 5 | Feasibility | Races, lifecycles, compatibility, N+1 |

## Severity

P0 blocks implementation; P1 functional gaps; P2 UX; P3 cleanliness

## Stop criteria

Two consecutive clean passes, or final pass only has P2/P3, or all five dimensions done.
