---
inclusion: auto
name: i18n-standards
description: 国际化规范，包括支持语言、i18n key 命名、前端翻译使用和后端错误消息映射
---

# 国际化规范

## 语言文件

- en-US (默认): `frontend/src/i18n/en-US.json`
- zh-CN: `frontend/src/i18n/zh-CN.json`
- zh-TW: `frontend/src/i18n/zh-TW.json`

## 规则

- 所有用户可见文本必须用 i18n key，禁止硬编码
- 使用 `$t('key')` 或 `t('key')` (Composition API)
- key 格式: `{module}.{page}.{element}`，如 `template.list.createButton`
- 新增功能必须同时更新三个语言文件
- 后端错误通过 ErrorCode 返回，前端按 code 映射本地化文本
- 后端日志用英文
