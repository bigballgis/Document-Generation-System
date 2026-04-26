# Project Skill Registry

This registry maps remediation work to project skills in `.cursor/skills/`.

## Core Skill

### `project-remediation-runner`

Path:

`.cursor/skills/project-remediation-runner/SKILL.md`

Use for:

- Any task from `09-task-cards.md`.
- Enforcing one-task-at-a-time execution.
- Applying task scope, validation commands, and stop conditions.

## Domain Skills

### `backend-security-hardening`

Path:

`.cursor/skills/backend-security-hardening/SKILL.md`

Use for:

- WS-01 tasks.
- OnlyOffice callbacks.
- SSRF prevention.
- RestTemplate safety.
- Webhook URL validation.
- Actuator exposure.
- Production secret checks.
- Tenant isolation security review.

### `docxtemplater-service-hardening`

Path:

`.cursor/skills/docxtemplater-service-hardening/SKILL.md`

Use for:

- WS-02 Node contract tasks.
- Express route safety.
- `/evaluate` behavior.
- Sandbox behavior.
- `/merge-segments`.
- PDF conversion.
- Request body limits.
- Java-to-Node contract repair.

### `frontend-vue-workspace`

Path:

`.cursor/skills/frontend-vue-workspace/SKILL.md`

Use for:

- WS-06 tasks.
- Vue 3 components.
- Pinia workspace state.
- OnlyOfficeEditor.
- SegmentVersionDialog.
- VersionDiffPanel.
- Vitest and component tests.
- Frontend i18n changes.

### `delivery-governance`

Path:

`.cursor/skills/delivery-governance/SKILL.md`

Use for:

- WS-07 tasks.
- WS-08 Docker and CI tasks.
- Dirty working tree classification.
- CI/CD.
- Release runbooks.
- Dockerfile and Compose hardening.
- Dependency audit planning.

### `migration-safety`

Path:

`.cursor/skills/migration-safety/SKILL.md`

Use for:

- Migration runbooks.
- Flyway migration review.
- V30/V36/V39 issues.
- `segment_versions` upgrade planning.
- Existing database upgrade risk.

### `git-change-management`

Path:

`.cursor/skills/git-change-management/SKILL.md`

Use for:

- Git status review.
- Commit preparation.
- Change grouping by task card.
- Staged diff review.
- Handoff summaries when no commit is requested.
- Preventing unrelated files, secrets, generated binaries, and temporary files from entering commits.

### `local-deployment-operations`

Path:

`.cursor/skills/local-deployment-operations/SKILL.md`

Use for:

- Local Docker Compose validation.
- Local service startup and smoke checks.
- Local health check troubleshooting.
- `.env` readiness checks for local development.
- Local deployment verification after remediation.

### `validation-test-runner`

Path:

`.cursor/skills/validation-test-runner/SKILL.md`

Use for:

- Running scoped backend, frontend, Docxtemplater, Docker, and documentation validation commands.
- Reporting command results.
- Recording commands not run and reasons.
- Avoiding destructive or production-connected validation commands.

## Skill Selection Rules

- Always use `project-remediation-runner` first for implementation tasks.
- Add one domain skill when the task clearly belongs to that domain.
- Use `migration-safety` together with another skill if a task touches database migrations.
- Use `validation-test-runner` after implementation when validation is required.
- Use `git-change-management` only when reviewing changes for commit or handoff.
- Use `local-deployment-operations` only for local deployment and smoke testing tasks.
- Do not use multiple domain skills unless the task card explicitly spans multiple domains.

## Consistency Rules

- All skills must point back to `AGENTS.md`.
- All skills must respect the English-file-content policy.
- All skills must respect task card scope and stop conditions.
- Skills do not override task cards; they provide domain guidance for executing them.
