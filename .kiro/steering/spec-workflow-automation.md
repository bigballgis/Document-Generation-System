---
inclusion: always
---

# Spec workflow automation

When developing in spec mode, follow this flow. Review standards:
- #[[file:.kiro/steering/spec-review-checklist.md]]
- #[[file:.kiro/steering/spec-quality.md]]

## Phases

| Phase | Deliverable | Review | Pause for user |
|-------|-------------|--------|----------------|
| 1 | `requirements.md` | Five passes (completeness → accuracy → deps → edge cases → contradictions) | Yes — confirm |
| 2 | `design.md` | Five passes (coverage → feasibility → repo fit → side effects → migrations) | Auto |
| 3 | `tasks.md` | Five passes (completeness → ordering → granularity → verifiability → risk) | Auto |
| 4 | Joint doc review | Five passes (traceability → consistency → repo walk → simulation → gaps) | Auto |
| 5 | Execute tasks | After each task: compile + tests | — |
| 6 | Build & deploy | Docker build → compose up → health checks | — |

## Review discipline

- Each pass must read real sources (`readCode` / grep / file search)
- Fix findings immediately — do not defer
- If clean, state explicitly: "Pass N: no issues found"

## Mandatory rules

- **Show all five passes in the chat** — do not delegate review-only work to subagents; the user must see each pass, findings, and fixes.
- **No skipping review** — even if a subagent drafted docs, the lead agent finishes five passes before claiming completion.
- **One dimension per pass** — never blend multiple dimensions in a single pass.
- **End with a summary table** — list each pass, dimension, issue count, and fixes.

## Build commands

```bash
docker build -f Dockerfile.local -t docgen-app:latest .        # cwd: backend/
docker build -f Dockerfile.local -t docgen-frontend:latest .     # cwd: frontend/
docker compose up -d
curl http://localhost:8080/actuator/health                       # expect UP
```

## Easy-to-miss checklist

`SecurityConfig` URLs, `OpenApiConfig` tags, `application.yml`, Docker/Compose files, nav menus, route guards, `AuditLog` action types

## Sub-spec splits

If a spec exceeds ~8 requirements or ~30 sub-tasks, split by functional boundary into `.kiro/specs/{parent}/{sub}/` and execute serially.
