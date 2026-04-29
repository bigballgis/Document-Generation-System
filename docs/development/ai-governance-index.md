# AI Governance Index

This is the entry point for AI noise reduction, AI-readable code cleanup, and low-tier agent execution.

## Document Roles

Use one document layer at a time.

| Layer | File | Owner | Purpose | Low-tier agent use |
| --- | --- | --- | --- | --- |
| Entry point | `docs/development/ai-governance-index.md` | GPT-5.5 / senior agent | Explains how all AI governance documents fit together. | Read only if assigned orchestration or handoff work. |
| AI context strategy | `docs/development/ai-context-noise-reduction-master-plan.md` | GPT-5.5 / senior agent | Reduces prompt/context bloat, hallucination risk, stale context, and long-horizon drift. | Do not execute directly. |
| Code cleanup strategy | `docs/development/noise-reduction-master-plan.md` | GPT-5.5 / senior agent | General code/repository noise reduction principles. | Do not execute directly. |
| AI-readable code strategy | `docs/development/ai-readable-code-remediation-plan.md` | GPT-5.5 / senior agent | Defines comment necessity, AI-readable structure, deduplication, and abstraction control. | Reference only; do not infer extra scope. |
| Low-tier execution | `docs/development/ai-readable-code-task-cards.md` | Low-tier agents, one card at a time | Exact executable task cards with scope, allowed edits, forbidden edits, validation, and stop conditions. | Primary execution source. |
| Comment allowlist | `docs/development/comment-cleanup-config-allowlist.md` | All agents | Lists paths where configuration comments must be preserved. | Required for comment-cleanup cards. |

## Skill Roles

| Skill | File | Use when | Do not use for |
| --- | --- | --- | --- |
| AI context noise | `.cursor/skills/ai-context-noise-reduction/SKILL.md` | Prompt/context compression, hallucination prevention, evidence discipline, minimal task packets. | Code refactoring execution. |
| AI-readable code | `.cursor/skills/ai-readable-code-governance/SKILL.md` | Comment necessity, AI-readable structure, deduplication, abstraction/class/method explosion prevention. | Broad architecture changes without a task card. |
| General noise reduction | `.cursor/skills/noise-reduction-governance/SKILL.md` | General repository cleanup, code slimming, comment clutter, duplication, maintainability cleanup. | AI context governance; use `ai-context-noise-reduction` instead. |

## Execution Flow

### Senior / GPT-5.5 Flow

1. Read this index.
2. Choose the correct strategy document.
3. Decide whether a low-tier task card already exists.
4. If not, create a new narrow task card before assigning execution.
5. Review low-tier output against the strategy and stop conditions.
6. Decide whether to approve the next card.

### Low-Tier Agent Flow

1. Read only the assigned task card in `docs/development/ai-readable-code-task-cards.md`.
2. Read `.cursor/skills/ai-readable-code-governance/SKILL.md`.
3. Read only the strategy sections named by the card or skill.
4. Execute only the assigned card.
5. Stop on any stop condition.
6. Return the expected handoff.

Low-tier agents must not:

- Execute from master plans directly.
- Combine task cards.
- Broaden file scope.
- Perform architecture decisions not explicitly approved.
- Touch security, migrations, auth, tenant isolation, SSRF, or deployment configuration unless a task card explicitly permits it.

## Current Readiness Assessment

**AI-CODE-T01 through AI-CODE-T10** (see `docs/development/ai-readable-code-task-cards.md`) are **closed** for the 2026-04-28 snapshot. Summary: [ai-readable-code-completion-report.md](ai-readable-code-completion-report.md).

Future low-tier runs should **open a new narrow task card** (or a dated follow-up series) rather than reusing T01–T10 batch IDs for unrelated passes.

Not ready for low-tier execution without a new task card:

- Any broad "AI Navigation Pass" not decomposed into a task card.
- Any cross-backend/frontend/docxtemplater contract consolidation.
- Any class/interface merge without an approved inventory candidate.

## Review Checklist Before Starting Low-Tier Work

- Is there exactly one task card?
- Are files in scope and out of scope explicit?
- Are allowed edits and forbidden edits explicit?
- Are validation commands listed?
- Are stop conditions clear?
- Is senior approval required?
- Is the working tree dirty in files the card will touch?

