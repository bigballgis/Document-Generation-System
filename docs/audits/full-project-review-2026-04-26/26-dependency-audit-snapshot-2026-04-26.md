# Dependency audit snapshot (2026-04-26)

**Type:** read-only tooling report (no dependency upgrades applied)  
**Scope:** `docxtemplater-service/`, `frontend/` (`npm audit`). Java backend transitive CVE review is **out of scope** for this snapshot (use OWASP Dependency-Check or vendor advisories on a schedule).

## docxtemplater-service (`npm audit`)

| Severity | Count | Packages / notes |
| --- | --- | --- |
| Critical | 1 | `xmldom` (transitive via `docxtemplater-image-module-free`) — **no upstream fix** in the reported chain at audit time. |
| High | 1 | `@xmldom/xmldom` — `npm audit fix` may bump compatible range; verify Docxtemplater stack before merging. |
| Moderate | 2 | `fast-xml-parser` &lt; 5.7.0 — fix suggested via `npm audit fix`. |

**Exit code:** `npm audit` returns **non-zero** when vulnerabilities are present (expected).

**Recommended follow-up (task-card sized):**

1. Run `npm audit fix` (non-`--force`) in `docxtemplater-service/`, then full **`npm test`** and a smoke render with image-heavy templates.
2. If `xmldom` / `docxtemplater-image-module-free` remains blocked, document **risk acceptance** (trusted template sources only) or replace the image module with a maintained alternative in a dedicated change request.

## frontend (`npm audit`)

| Severity | Count | Packages / notes |
| --- | --- | --- |
| Moderate | 6 | `dompurify` (via `monaco-editor`), `esbuild` (via `vite`), `follow-redirects`, `postcss`. |

**Tooling hints:**

- `npm audit fix` may resolve **follow-redirects** and **postcss** without major bumps — still run **`npm run type-check`**, **`npm test`**, and **`npm run build`** after.
- `npm audit fix --force` was reported to pull **breaking** majors (`monaco-editor`, `vite`); **do not** run in this snapshot without a planned Vue/Vite upgrade task.

**Recommended follow-up:**

1. Schedule a **minor/patch** dependency batch (Vite ecosystem) with CI green on `frontend-ci.yml`.
2. Track `monaco-editor` / `dompurify` advisories separately; upgrading Monaco often requires UI regression passes.

## Backend (Maven)

No `npm audit` equivalent was executed here. Recommended commands for a future pass:

```powershell
cd backend
mvn -B -ntp dependency:tree
```

Optionally integrate **OWASP Dependency-Check** or GitHub **Dependabot** alerts for `backend/pom.xml` and report into this audit directory.

## Evidence commands (reproducibility)

```powershell
cd docxtemplater-service
npm audit
```

```powershell
cd frontend
npm audit
```
