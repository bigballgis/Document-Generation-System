# Comment cleanup: configuration paths (do not strip)

This document lists paths where **comments are preserved** during code hygiene passes (see ad-hoc comment cleanup / code slimming).

## Build and tooling (including root)

- `**/pom.xml` (including `backend/pom.xml`)
- `**/package.json`, `package-lock.json` (no line comments; unchanged)
- `frontend/vite.config.ts`, `frontend/eslint.config.js`, `frontend/tsconfig.json`, `frontend/tsconfig.node.json`
- `docxtemplater-service/jest.config.js`
- `.vscode/**`, `.github/workflows/**` (YAML with operational comments)

## Runtime and deployment configuration

- `**/*.yml`, `**/*.yaml` (e.g. `application.yml`, `application-*.yml`, `docker-compose.yml`, `k8s/**`)
- `**/*.properties`
- `.env.example` and local env samples (if present)

## Excluded from “source comment stripping”

- `docs/**` (unless a dedicated doc task)
- `node_modules/**`, `**/target/**`, `**/dist/**`
- Product i18n and static JSON data where comments are not used (`frontend/src/i18n/*.json`, `templates/**` sample JSON)

## Convention

- **Vite, ESLint, and Jest config files** at repository paths above are treated as **configuration**; inline comments in those files are **kept**.
