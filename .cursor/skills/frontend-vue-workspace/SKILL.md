---
name: frontend-vue-workspace
description: Implement and test Vue 3 TypeScript frontend remediation tasks for this project. Use for template workspace state, OnlyOfficeEditor behavior, SegmentVersionDialog, VersionDiffPanel, Pinia store interactions, Vite, Vitest, and i18n tasks.
---

# Frontend Vue Workspace

## Required Context

Read the active task card in:

`docs/audits/full-project-review-2026-04-26/09-task-cards.md`

Also read:

- `AGENTS.md`
- `docs/audits/full-project-review-2026-04-26/10-implementation-review-checklist.md`
- Relevant component files only.

## Frontend Rules

- Use Vue 3 Composition API patterns already present in the project.
- Keep TypeScript types explicit where practical.
- Prefer behavior-focused tests over internal implementation assertions.
- Mock external systems such as OnlyOffice, Axios, and browser clipboard APIs.
- Do not call real OnlyOffice scripts in tests.
- Keep component changes narrow.
- Preserve existing i18n structure.

## Testing Guidance

- Use Vitest and existing project test setup.
- Prefer `mount` or the existing test utility pattern used in the repository.
- Arrange, act, and assert clearly.
- Restore mocks after tests.
- Test user-visible behavior for dialogs, buttons, warnings, and rendered diff rows.
- Avoid over-coupling tests to private refs unless the task is specifically about reactivity internals.

## i18n Guidance

- The project contains localized UI resource files.
- Localized strings are allowed only inside intentional i18n resource files.
- Other files should use English content and i18n keys.
- If this conflicts with the repository-wide English policy, stop and report the conflict.

## Stop Conditions

Stop and report if:

- A component cannot be mounted with the existing test setup.
- A fix requires broad router or store redesign.
- A task requires product decisions about UI wording.
- A change would affect unrelated workspace flows.

## Useful References

- Vitest component testing guide: `https://vitest.dev/guide/browser/component-testing`
- Vue Test Utils documentation should be checked against the installed project version.
- Vite and Vitest CI behavior should be validated with the repository scripts.
