---
inclusion: manual
name: spec-quality
description: Spec quality — FE/BE alignment checks, EARS patterns, correctness properties
---

# Spec quality

## Frontend task template

```
- [ ] N. Frontend — {module}
  - [ ] N.1 API module (frontend/src/api/xxx.ts)
  - [ ] N.2 Components (FormDialog/List/Validator)
  - [ ] N.3 Wire into views
  - [ ] N.4 i18n (all locale files)
```

## Checklist

1. API coverage: each Controller endpoint has a function in `frontend/src/api/`
2. Routes: each screen registered in `router/index.ts`
3. i18n: keys for all copy; locales stay in sync
4. Controller → client: `XxxController.java` maps to `frontend/src/api/xxx.ts`

## EARS patterns

| Kind | Template |
|------|------------|
| Ubiquitous | THE [system] SHALL [action] |
| Event-driven | WHEN [trigger], THE [system] SHALL [action] |
| State-driven | WHILE [state], THE [system] SHALL [action] |
| Unwanted | IF [condition], THEN THE [system] SHALL [action] |

## Correctness properties

Each spec should cite executable properties: jqwik on backend, fast-check on frontend, traced to requirement IDs.
