---
name: validation-test-runner
description: Run scoped validation commands for backend, frontend, Docxtemplater, Docker, and audit documentation. Use after implementing task cards or when verifying readiness before handoff.
---

# Validation Test Runner

## Required Context

Read:

- `AGENTS.md`
- Active task card in `docs/audits/full-project-review-2026-04-26/09-task-cards.md`
- `docs/audits/full-project-review-2026-04-26/06-validation-commands.md`

## Validation Rules

- Run only commands relevant to the active task card.
- Prefer targeted tests first, then broader tests.
- Do not run destructive commands.
- Do not start long-running services unless required by the task.
- Document commands that were not run and why.

## Backend Validation

Working directory: `backend`

```powershell
mvn test
```

Use broader validation only when needed:

```powershell
mvn verify
```

## Frontend Validation

Working directory: `frontend`

```powershell
npm run type-check
```

```powershell
npm test
```

```powershell
npm run build
```

If dependencies are not installed or lockfile state is uncertain:

```powershell
npm ci
```

## Docxtemplater Validation

Working directory: `docxtemplater-service`

```powershell
npm test
```

If dependencies are not installed or lockfile state is uncertain:

```powershell
npm ci
```

## Documentation Validation

Check for unintended Chinese text in generated English-only docs:

```powershell
rg "[\p{Han}]" docs/audits/full-project-review-2026-04-26 AGENTS.md CLAUDE.md .cursor
```

## Docker Validation

Use only when Docker is available and the task requires it:

```powershell
docker compose config
```

```powershell
docker compose build
```

## Result Reporting

Report:

- Command.
- Working directory.
- Result.
- Failure summary.
- Whether failures are pre-existing or introduced by the task, if known.

## Stop Conditions

Stop and report if:

- A command requires unavailable credentials.
- A command would connect to production.
- A command would delete data.
- A long-running process is already active and duplicate startup would conflict.
- Test failures indicate broad unrelated breakage.
