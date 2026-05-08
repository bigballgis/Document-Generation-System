---
name: backend-security-hardening
description: Harden Spring Boot backend security for this project. Use for OnlyOffice callbacks, SSRF prevention, JWT/API key behavior, Actuator exposure, production secret checks, tenant isolation, webhook URL validation, and outbound HTTP safety tasks.
---

# Backend Security Hardening

## Required Context

Read the active task card in:

`docs/audits/full-project-review-2026-04-26/09-task-cards.md`

Also read:

- `AGENTS.md`
- `docs/audits/full-project-review-2026-04-26/10-implementation-review-checklist.md`
- `docs/audits/full-project-review-2026-04-26/04-evidence-index.md`

## Security Rules

- Deny unsafe input by default.
- Use allowlists for outbound URLs.
- Reject loopback, link-local, private, multicast, and metadata IP ranges unless a task explicitly allows development exceptions.
- Validate callback proof before writing to MinIO.
- Do not trust template IDs as authorization.
- Do not log secrets, tokens, or full token-bearing URLs.
- Do not expose Actuator details or metrics publicly in production.
- Do not introduce placeholder production secrets.
- Preserve tenant isolation.

## SSRF Guidance

Use the OWASP SSRF Prevention Cheat Sheet principles:

- Prefer strict allowlists over blocklists.
- Validate the parsed URL, scheme, host, port, and resolved addresses.
- Validate DNS resolution results, not only raw host strings.
- Apply network-layer restrictions where possible.

## Spring Boot Guidance

- Centralize outbound HTTP timeouts.
- Prefer explicit configuration properties with safe defaults.
- Protect management endpoints with authentication or network isolation.
- Keep public endpoints minimal and intentional.
- Add tests for rejection paths, not only success paths.

## Stop Conditions

Stop and report if:

- The task requires a product decision about callback token format.
- A migration appears necessary.
- A new dependency appears necessary.
- A change would broaden anonymous access.
- A test requires real secrets or production services.

## Useful References

- OWASP SSRF Prevention Cheat Sheet: `https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html`
- OWASP Web Security Testing Guide SSRF: `https://github.com/OWASP/wstg/blob/master/document/4-Web_Application_Security_Testing/07-Input_Validation_Testing/19-Testing_for_Server-Side_Request_Forgery.md`
- Spring Boot Actuator security documentation should be checked against the project Spring Boot version before implementation.
