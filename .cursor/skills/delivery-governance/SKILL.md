---
name: delivery-governance
description: Manage CI/CD, Docker hardening, release governance, migration runbooks, dirty working tree classification, and production readiness for this project. Use for WS-07 and WS-08 task cards.
---

# Delivery Governance

## Required Context

Read the active task card in:

`docs/audits/full-project-review-2026-04-26/09-task-cards.md`

Also read:

- `AGENTS.md`
- `docs/audits/full-project-review-2026-04-26/11-execution-sequence.md`
- `docs/audits/full-project-review-2026-04-26/10-implementation-review-checklist.md`

## CI Rules

- Add one CI stage at a time.
- Do not introduce secrets into CI configuration.
- Do not disable tests to make CI pass.
- Use lockfile-based installs for Node projects.
- Keep CI commands aligned with `06-validation-commands.md`.
- Document commands that cannot run in the current environment.

## Docker Rules

- Avoid floating `latest` tags in production-oriented configuration.
- Prefer non-root runtime users where practical.
- Do not mount the Docker daemon socket into containers.
- Do not hardcode secrets in Dockerfiles or Compose files.
- Use multi-stage builds where already present.
- Validate Compose syntax after changes.

## Migration and Release Rules

- Do not edit Flyway migrations unless a task explicitly permits it.
- Migration runbooks must distinguish new installs from existing database upgrades.
- Release runbooks must include backup, deploy, smoke test, rollback, and known limitations.
- Do not include real connection strings or secrets in runbooks.

## Stop Conditions

Stop and report if:

- The CI provider is unknown.
- Docker image target versions are uncertain.
- A migration strategy requires production schema history.
- Resource limits would break local development without an agreed profile split.

## Useful References

- Docker build best practices: `https://docs.docker.com/build/building/best-practices/`
- OWASP Docker Security Cheat Sheet: `https://cheatsheetseries.owasp.org/cheatsheets/Docker_Security_Cheat_Sheet.html`
- Snyk Docker image security guidance: `https://snyk.io/blog/10-docker-image-security-best-practices/`
