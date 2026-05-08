---
name: ai-context-noise-reduction
description: Manages AI context noise, hallucination risk, context budget, source grounding, compaction, and minimal task packets. Use when the user asks about AI noise reduction, hallucination prevention, reducing context length, context engineering, prompt/context cleanup, agent reliability, or making future AI work more accurate and cheaper.
---

# AI Context Noise Reduction

## Required Context

Read first:

- `AGENTS.md`
- `docs/development/ai-context-noise-reduction-master-plan.md`

Read only if relevant:

- `docs/development/noise-reduction-master-plan.md` for code/repository cleanup.
- `docs/development/comment-cleanup-config-allowlist.md` for comment-cleanup tasks.
- Active audit task card only when doing remediation work.

## Core Objective

Give the agent the smallest high-signal context that is sufficient to act correctly.

Optimize for:

- Source-grounded answers.
- Explicit uncertainty.
- Short context.
- No stale-plan drift.
- No claims about files, tests, or behavior without evidence.

## Before Work Starts

Classify the request:

- Direct answer.
- Repository-grounded answer.
- Implementation.
- Research synthesis.
- Validation/reporting.

Then build a minimal context packet:

```text
Task:
Scope:
Out of scope:
Primary sources needed:
Assumptions:
Stop conditions:
Validation:
Next action:
```

Do not read broad documents or whole directories unless the task truly requires them.

## Evidence Rules

Use this evidence ladder:

1. Repository source/config.
2. Command output from this session.
3. Project docs/task cards.
4. Primary external docs or papers.
5. Secondary summaries/search snippets.
6. Model prior knowledge.

Implementation decisions require tiers 1-3 whenever possible. If only weaker evidence exists, say so.

## Context Budget

Default limits:

- Initial read: 5 files or fewer.
- Large files: search first, then read focused snippets.
- Broad exploration: use read-only explore subagents and request concise findings.
- Tool output: summarize useful facts, then stop carrying raw output mentally.

Avoid loading:

- `node_modules`, `target`, `dist`, generated artifacts.
- Entire audit packages when one task card or checklist section is enough.
- Prior chat transcripts unless the user asks for history.
- Raw logs unless debugging a specific failure.

## Hallucination Guards

- Do not state repository behavior without reading or searching the relevant source.
- Do not claim tests passed unless the command was run and succeeded.
- Do not infer product requirements from names alone.
- Do not treat examples as requirements.
- If evidence is missing, ask 1-2 focused questions or state the gap.
- For research, prefer primary sources and record references in generated docs.

## Compaction Rules

When summarizing long work, keep:

- Latest user request.
- Active task and scope.
- Decisions made.
- Files changed.
- Commands run and results.
- Known failures or uncertainty.
- Next action.

Drop:

- Superseded plans.
- Raw tool output already summarized.
- Failed hypotheses no longer relevant.
- Repeated status updates.
- Unrelated listings.

## Output Expectations

For plans:

- Name the source files or sources to read.
- State what will not be read by default.
- Include stop conditions.
- Include validation.

For final answers:

- Cite relevant repository paths or primary sources.
- Separate facts from assumptions.
- Include commands run and failures when validation was requested.
- Keep summaries short and high signal.

## Stop Conditions

Stop and ask or report when:

- The requested action needs security, auth, tenant isolation, migration, or data-loss decisions not covered by the current scope.
- Evidence conflicts.
- Required files cannot be found.
- The dirty tree contains unrelated changes in the same files.
- The user changes the goal mid-task and old context may mislead the work.

