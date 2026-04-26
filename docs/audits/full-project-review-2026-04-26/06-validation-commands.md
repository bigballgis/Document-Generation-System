# Validation Commands

This file records validation commands for later remediation phases. These commands have not been executed during the planning pass.

## Backend

Working directory: `backend`

```powershell
mvn test
```

```powershell
mvn verify
```

Future additions:

- Separate unit and integration test phases.
- Flyway migration validation.
- Testcontainers PostgreSQL migration execution.
- JaCoCo coverage reports.

## Frontend

Working directory: `frontend`

```powershell
npm ci
```

```powershell
npm run type-check
```

```powershell
npm test
```

```powershell
npm run build
```

Future additions:

- CI lint should not use auto-fix mode.
- Add component or E2E tests for OnlyOffice and critical workspace flows.

## Docxtemplater Service

Working directory: `docxtemplater-service`

```powershell
npm ci
```

```powershell
npm test
```

Future additions:

- Validate the `isolated-vm` path inside the production Docker image.
- Validate LibreOffice and UNO PDF conversion inside the Docker image.
- Add contract tests for `/render`, `/evaluate`, `/merge-segments`, and `/convert-pdf`.

## Docker and Integration

Working directory: repository root

```powershell
docker compose build
```

```powershell
docker compose up
```

Future additions:

- Run only in a local or isolated network.
- Never use production secrets.
- Add a health-check wait script.
- Add cross-service smoke tests.

## Security and Dependencies

Recommended future checks:

```powershell
npm audit
```

```powershell
mvn dependency:tree
```

```powershell
docker scout cves
```

Final tool selection should match the team environment.

## Validation Record Template

```text
Date:
Branch or commit:
Command:
Result:
Failure summary:
Related tracking item:
Decision:
```
