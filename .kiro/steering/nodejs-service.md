---
inclusion: auto
name: nodejs-service
description: Docxtemplater Node.js service — layout, REST API, sandbox, tests
---

# Docxtemplater service

## Layout

#[[file:docxtemplater-service/server.js]] — Express 4.x + docxtemplater + PizZip + isolated-vm + MinIO SDK + bwip-js + qrcode + LibreOffice headless

## API

| Method | Path | Purpose |
|--------|------|---------|
| POST | /render | Render `.docx` template |
| POST | /evaluate | Sandbox expression evaluation |
| POST | /convert-pdf | Word → PDF |
| GET | /health | Health check |

## Sandbox

- #[[file:docxtemplater-service/src/sandbox.js]] — isolated-vm V8 isolate
- Timeout 5000ms (`SANDBOX_TIMEOUT`), memory 64MB (`SANDBOX_MEMORY_LIMIT`)
- Blocked: fs, path, http, net, child_process, process, eval, `Function` constructor

## Tests

Jest + fast-check (PBT); run `npm test` with cwd `docxtemplater-service/`

## New routes

Follow patterns in #[[file:docxtemplater-service/src/routes/]] + validate inputs + uniform errors + Jest coverage
