---
inclusion: auto
name: nodejs-service
description: Docxtemplater Node.js 服务规范，包括项目结构、REST API、安全沙箱和测试规范
---

# Docxtemplater 服务规范

## 结构

#[[file:docxtemplater-service/server.js]] — Express 4.x + docxtemplater + PizZip + isolated-vm + minio SDK + bwip-js + qrcode + LibreOffice Headless

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /render | 渲染 .docx 模板 |
| POST | /evaluate | 沙箱执行表达式 |
| POST | /convert-pdf | Word 转 PDF |
| GET | /health | 健康检查 |

## 沙箱

- #[[file:docxtemplater-service/src/sandbox.js]] — isolated-vm V8 Isolate
- 超时 5000ms (`SANDBOX_TIMEOUT`)，内存 64MB (`SANDBOX_MEMORY_LIMIT`)
- 禁止: fs, path, http, net, child_process, process, eval, Function 构造函数

## 测试

Jest + fast-check (PBT)，运行: `npm test` (cwd: `docxtemplater-service/`)

## 新端点

遵循 #[[file:docxtemplater-service/src/routes/]] 现有模式 + 输入验证 + 统一错误格式 + Jest 测试
