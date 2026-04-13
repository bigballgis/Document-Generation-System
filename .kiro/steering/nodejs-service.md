---
description: Docxtemplater Node.js 服务规范，包括项目结构、REST API、安全沙箱和测试规范
inclusion: auto
fileMatchPattern: 'docxtemplater-service/**'
---

# Docxtemplater Node.js 服务规范

## 项目结构

```
docxtemplater-service/
├── src/              # 源码
├── scripts/          # 工具脚本
├── server.js         # 入口文件 (Express)
├── package.json
├── jest.config.js    # 测试配置
└── Dockerfile
```

## 技术栈

- **运行时**: Node.js
- **框架**: Express 4.x
- **文档引擎**: docxtemplater 3.x + PizZip
- **图片模块**: docxtemplater-image-module-free
- **Excel 公式**: @formulajs/formulajs
- **安全沙箱**: isolated-vm (可选依赖)
- **对象存储**: minio SDK
- **条码/二维码**: bwip-js + qrcode
- **PDF 转换**: LibreOffice Headless (系统命令调用)

## REST API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/render` | 渲染 .docx 模板 |
| POST | `/evaluate` | 沙箱执行表达式 |
| POST | `/convert-pdf` | Word 转 PDF |
| GET | `/health` | 健康检查 |

## 安全沙箱规则

- 使用 `isolated-vm` 基于 V8 Isolate 隔离
- 默认超时: 5000ms (`SANDBOX_TIMEOUT` 环境变量)
- 默认内存限制: 64MB (`SANDBOX_MEMORY_LIMIT` 环境变量)
- 禁止访问: `fs`, `path`, `http`, `https`, `net`, `child_process`, `process`, `eval`, `Function` 构造函数

## 测试规范

- 使用 Jest 框架
- PBT 使用 fast-check 库
- 运行命令: `npm test` (jest --run-in-band --forceExit)

## 新增端点规范

为模板分段功能，可能需要新增:
- `POST /render-segment` — 渲染单个段落
- `POST /merge-segments` — 合并多个已渲染段落为完整文档

新端点必须:
- 遵循现有 Express 路由模式
- 包含输入验证
- 返回统一错误格式
- 添加对应的 Jest 测试
