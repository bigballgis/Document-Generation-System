# AI Context Noise Reduction Master Plan

This plan optimizes how AI agents work in this repository: reduce irrelevant context, prevent hallucinations, preserve critical constraints, and keep long-running work coherent without flooding the model with raw history.

## Source Principles

This plan is grounded in current LLM and software-agent guidance:

- Anthropic, "Effective context engineering for AI agents": context is a finite resource; the goal is the smallest high-signal token set that maximizes the desired outcome. Use just-in-time retrieval, compaction, structured note-taking, and sub-agent isolation for long-horizon work.
- Claude hallucination guardrails: allow the model to say "I do not know"; use direct quotes for factual grounding; verify claims with citations; restrict answers to provided context when appropriate.
- Liu et al., "Lost in the Middle" (TACL 2024): long-context models may perform worse when relevant information is placed in the middle of long inputs, so ordering and compression matter.
- Chroma, "Context Rot" (2025): longer input can degrade performance even on controlled tasks; distractors and semantically similar irrelevant content are especially harmful.
- Google Engineering Practices and Software Engineering at Google: review should optimize code health, comprehension, consistency, and maintainability; small focused changes keep review and reasoning tractable.

References:

- <https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents>
- <https://platform.claude.com/docs/en/test-and-evaluate/strengthen-guardrails/reduce-hallucinations>
- <https://aclanthology.org/2024.tacl-1.9/>
- <https://www.trychroma.com/research/context-rot>
- <https://google.github.io/eng-practices/review/reviewer/standard.html>
- <https://abseil.io/resources/swe-book/html/ch09.html>

## Goal

Make every AI session cheaper, safer, and more accurate by giving the model:

- Less raw text.
- More source-grounded evidence.
- Clearer scope boundaries.
- Explicit uncertainty handling.
- Better task-state summaries.
- Repeatable validation requirements.

## Problem Taxonomy

### Context Bloat

Symptoms:

- Long audit documents pasted or loaded wholesale.
- Multiple overlapping rules repeat the same constraints.
- Tool outputs remain in context after their information value has been extracted.
- Entire files are read when a symbol search or focused snippet would suffice.

Risk:

- Token waste, slower work, higher cost, lower recall, and more "lost in the middle" failures.

### Context Pollution

Symptoms:

- Old plans remain active after the user changes direction.
- Failed hypotheses, stale tool output, or unrelated user changes stay mixed with the current task.
- Examples are treated as requirements.
- Adjacent task-card rules leak across workstreams.

Risk:

- The agent follows obsolete instructions or makes changes outside scope.

### Hallucination Pressure

Symptoms:

- The agent answers without reading the relevant source file.
- The agent infers product behavior from naming instead of implementation.
- The agent summarizes tests or commands it did not run.
- The agent treats web snippets as source of truth without fetching primary references when precision matters.

Risk:

- False claims, unsafe implementation decisions, incorrect validation reports.

### Long-Horizon Drift

Symptoms:

- The agent loses the active task ID, stop conditions, or latest user instruction.
- Implementation details are buried in historical tool output.
- Final summaries answer an older task.

Risk:

- Incoherent execution, duplicate work, incorrect handoff.

## Context Architecture

Use a layered context model.

### Layer 0: Stable Constraints

Always available through project rules and `AGENTS.md`:

- Language policy.
- Git safety.
- Task-card discipline.
- Security and migration constraints.
- Validation reporting requirements.

Keep this layer short, canonical, and non-duplicative.

### Layer 1: Active Task Packet

Load only what is needed for the current task:

- User's latest request.
- Active task ID or "ad-hoc" label.
- Files in scope and out of scope.
- Stop conditions.
- Required validation commands.
- Known dirty-tree constraints.

This is the default working context for implementation.

### Layer 2: Just-in-Time Evidence

Retrieve evidence as needed:

- File paths and symbols first.
- Snippets before whole files.
- Targeted tests before full suites.
- Web primary sources before secondary summaries.

Discard raw tool output after extracting the relevant facts.

### Layer 3: External Memory

Persist only durable state:

- Task notes.
- Iteration log entries.
- Decision records.
- Skill and rule files.
- Compact handoff summaries.

Do not keep raw exploration transcripts in working context unless they remain directly relevant.

## Evidence Ladder

Use the highest available evidence tier before making claims.

1. Direct source code or configuration in the repository.
2. Executed command output from this session.
3. Project docs or task cards.
4. Primary external documentation or research papers.
5. Secondary summaries or web snippets.
6. Model prior knowledge.

Claims based on tiers 4-6 must be labeled as such when they affect implementation decisions. Claims based only on model prior knowledge should not drive code changes.

## Anti-Hallucination Protocol

Before answering or editing, the agent must classify the request:

- Direct answer from known facts.
- Repository-grounded answer.
- Implementation.
- Research synthesis.
- Validation/reporting.

Then apply the relevant gate.

### Repository-Grounded Gate

- Read or search the relevant files before explaining behavior.
- Cite file paths for important claims.
- If the relevant file cannot be found, say so and ask or search narrower.

### Implementation Gate

- Identify files in scope before editing.
- Check dirty-tree risk when broad edits are involved.
- Keep changes behavior-preserving unless asked otherwise.
- Run validation appropriate to risk.
- Report commands not run and why.

### Research Gate

- Prefer primary sources.
- Distinguish source facts from synthesis.
- Do not treat search-result snippets as final authority when precision matters.
- Record references in the resulting document.

### Validation Gate

- Never say a command passed unless it was run and succeeded.
- Preserve exact failure summaries for failed commands.
- Separate environmental failures from code failures only when evidence supports the distinction.

### Uncertainty Gate

Use explicit uncertainty when:

- Evidence is missing.
- Sources conflict.
- The requested scope is too broad.
- The task touches security, migrations, auth, tenant isolation, or data-loss risk without a task card.

Ask 1-2 focused questions instead of guessing.

## Context Budget Policy

Default budget:

- Prefer fewer than 5 files in the initial read.
- Prefer `rg`/glob/symbol search before reading large files.
- Prefer snippets of 80-200 lines over whole files unless the file is small.
- For files over 1,000 lines, search within the file first.
- For broad exploration, use read-only explore subagents and require concise findings.

Do not load:

- Generated directories (`target`, `dist`, `node_modules`).
- Raw logs unless diagnosing a specific failure.
- Entire audit packages when a task card or checklist section is enough.
- Prior chat transcripts unless the user explicitly asks for history.

## Compaction Policy

When context grows, retain:

- Latest user instruction.
- Active task and scope.
- Decisions made.
- Files changed.
- Validation run and results.
- Open blockers.
- Next concrete action.

Discard:

- Raw tool output already summarized.
- Superseded plans.
- Failed hypotheses no longer under consideration.
- Repeated status updates.
- Unrelated file listings.

Compaction summaries must preserve uncertainty and failed validations, not just successful outcomes.

## Minimal Context Packet Template

Use this template before starting substantial AI work:

```text
Task:
Mode:
Scope:
Out of scope:
Primary sources read:
Current evidence:
Assumptions:
Stop conditions:
Validation:
Next action:
```

For handoff:

```text
Latest user request:
What changed:
Files touched:
Commands run:
Results:
Known failures:
Open decisions:
Next step:
```

## Repository Application

Apply these repository-specific defaults:

- Use `AGENTS.md` as the top-level operating guide.
- Use domain skills only when their trigger matches the task.
- Use task cards only for remediation work; do not read all cards by default.
- Use `docs/audits/full-project-review-2026-04-26/10-implementation-review-checklist.md` selectively, not wholesale.
- Use `docs/development/comment-cleanup-config-allowlist.md` only for comment-cleanup tasks.
- Use `docs/development/noise-reduction-master-plan.md` only for code/repository cleanup tasks.

## Success Metrics

Track:

- Fewer files read before first useful plan.
- Fewer irrelevant tool outputs in final context.
- Fewer user corrections caused by stale instructions.
- More answers backed by file paths, command output, or primary sources.
- More explicit "not enough evidence" stops.
- Shorter handoff summaries with all necessary state preserved.

