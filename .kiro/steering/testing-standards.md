---
inclusion: auto
name: testing-standards
description: Testing — PBT (jqwik/fast-check), unit, integration, how to run
---

# Testing

## When to use PBT

Round-trips, invariants, idempotency, ordering/merge correctness, security properties, state-machine transitions.

## jqwik (backend)

- Class name: `XxxPropertyTest.java`
- Package (typical): `backend/src/test/java/com/docgen/property/` (also `.../service/` for service-focused suites)
- Example: #[[file:backend/src/test/java/com/docgen/property/TemplateStateMachinePropertyTest.java]]

```java
@Property(tries = 100)
void propertyName(@ForAll @From("providerName") Type input) {
    assertThat(service.method(input)).satisfies(r -> ...);
}
@Provide Arbitrary<Type> providerName() { return Arbitraries.of(...); }
```

Use ASCII `@Tag` slugs (JUnit/jqwik tag rules).

## fast-check (frontend)

- Files: `*.property.test.ts`
- Location: `frontend/src/__tests__/`
- Example: #[[file:frontend/src/__tests__/parameterPath.property.test.ts]]

## Unit tests

- Backend: JUnit 5 + Mockito; mirror package layout; cover happy path, errors, edges per service
- Frontend: Vitest + `@vue/test-utils`; `frontend/src/__tests__/`

## Integration tests

- Backend: Testcontainers (PostgreSQL) under `backend/src/test/java/com/docgen/integration/`
- Frontend: `frontend/src/__tests__/integration/`

## Commands

| Scope | Command | cwd |
|-------|---------|-----|
| Backend all | `./mvnw test` | `backend/` |
| Backend one | `./mvnw test -Dtest=XxxTest` | `backend/` |
| Frontend all | `npx vitest --run` | `frontend/` |
| Frontend one | `npx vitest --run src/__tests__/Xxx.test.ts` | `frontend/` |
| Node service | `npm test` | `docxtemplater-service/` |
