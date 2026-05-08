---
inclusion: auto
name: i18n-standards
description: i18n — supported locales, key naming, frontend usage, backend error mapping
---

# Internationalization

## Locale files

- #[[file:frontend/src/i18n/en-US.json]] (default)
- #[[file:frontend/src/i18n/zh-CN.json]]
- #[[file:frontend/src/i18n/zh-TW.json]]

## Rules

- All user-visible copy must use i18n keys — no hard-coded UI strings
- Composition API: `const { t } = useI18n()` → `t('key')`
- Key shape: `{module}.{page}.{element}`, e.g. `template.list.createButton`
- New features: update all three locale files together
- Backend returns `ErrorCode`; frontend maps codes to localized messages
- Backend logs in English
- Locale switching: `useI18n()` `locale`, persist under `localStorage` key `locale` (see `MainLayout.vue` `changeLocale`)
