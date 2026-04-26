# International Bank Commercial Loan - Facility Offer Letter (FOL)

## 模板概述

这是一个国际银行商业贷款 Facility Offer Letter (FOL) 模板，设计为 **组合模板 (COMPOSITE)**，
包含多个 Segment，覆盖系统所有模板设计功能。

## 覆盖的模板功能清单

| 功能 | 使用位置 | 语法示例 |
|------|----------|----------|
| 简单变量替换 | 全文 | `{borrower_name}` |
| 嵌套对象访问 | 借款人信息 | `{borrower.legal_name}` |
| 条件块 `#if` | 担保条款、费用 | `{#if has_guarantor}...{/if}` |
| 否定条件 `#if !` | 豁免条款 | `{#if !is_secured}...{/if}` |
| 循环 `#each` | 贷款设施、费用表 | `{#facilities}...{/facilities}` |
| 嵌套循环 | 设施内的还款计划 | `{#facilities}{#repayment_schedule}...{/}{/}` |
| 字符串过滤器 | 名称大写 | `{borrower_name \| upper}` |
| 数字过滤器 | 金额格式化 | `{amount \| currency:'USD':2}` |
| 百分比过滤器 | 利率 | `{interest_rate \| percent:2}` |
| 日期过滤器 | 日期格式化 | `{effective_date \| dateFormat:'YYYY-MM-DD'}` |
| 默认值过滤器 | 可选字段 | `{middle_name \| default:'N/A'}` |
| 数组聚合 $sum | 费用合计 | `{fees.$sum_amount}` |
| 数组聚合 $avg | 平均利率 | `{facilities.$avg_interest_rate}` |
| 数组聚合 $count | 设施数量 | `{facilities.$count}` |
| 数组聚合 $min/$max | 利率范围 | `{facilities.$min_interest_rate}` |
| 数组聚合 $join | 名称拼接 | `{facilities.$join_facility_name}` |
| 数组 sortBy | 排序 | `{#fees \| sortBy:'amount'}` |
| 数组 where | 过滤 | `{#covenants \| where:'type === "financial"'}` |
| 数组 groupBy | 分组 | `{#fees \| groupBy:'category'}` |
| 数组 slice | 截取 | `{#facilities \| slice:0:3}` |
| 数组 unique | 去重 | `{#currencies \| unique:'code'}` |
| 数组 reverse | 反转 | `{#events \| reverse}` |
| sumBy 过滤器 | 行内求和 | `{line_items \| sumBy:'amount'}` |
| joinBy 过滤器 | 行内拼接 | `{guarantors \| joinBy:'name':','}` |
| count 过滤器 | 行内计数 | `{facilities \| count}` |
| 表达式计算 | 计算字段 | `{total_amount * 0.01}` |
| 图片插入 | 银行 Logo | `{%bank_logo}` |
| 条码 | 文档追踪码 | barcode 配置 |
| 二维码 | 电子签名链接 | qrcode 配置 |
| 水印 | CONFIDENTIAL | watermark 配置 |
| 组合模板 Segment | 多段落组装 | assembly_config |
| 条件 Segment | 按贷款类型显示 | conditionExpression |
| 分页控制 | 段落间分页 | pageBreakBefore |
| DataScope 映射 | 段落数据隔离 | dataScope |
| 页眉页脚 | 银行信息 | headerFilePath/footerFilePath |
| DERIVED 参数 | 计算字段 | expressionType: JAVASCRIPT |
| EXCEL_FORMULA | 公式计算 | expressionType: EXCEL_FORMULA |
| 验证规则 | 输入校验 | validationRules |
| 嵌套对象 5 层 | 深层数据 | `{borrower.address.city}` |

## Segment 结构 (组合模板)

| # | Segment 名称 | 页数 | 条件 |
|---|-------------|------|------|
| 1 | Cover Page | 1 | 始终 |
| 2 | Table of Contents | 1 | 始终 |
| 3 | Part A - Definitions & Interpretation | 8 | 始终 |
| 4 | Part B - Facility Details | 10 | 始终 |
| 5 | Part C - Interest & Fees | 8 | 始终 |
| 6 | Part D - Repayment Schedule | 6 | 始终 |
| 7 | Part E - Conditions Precedent | 5 | 始终 |
| 8 | Part F - Representations & Warranties | 6 | 始终 |
| 9 | Part G - Covenants | 8 | 始终 |
| 10 | Part H - Security & Collateral | 6 | `has_security === true` |
| 11 | Part I - Guarantee | 4 | `has_guarantor === true` |
| 12 | Part J - Events of Default | 5 | 始终 |
| 13 | Part K - Governing Law & Jurisdiction | 3 | 始终 |
| 14 | Part L - Miscellaneous | 3 | 始终 |
| 15 | Appendix A - Compliance Certificate | 2 | 始终 |
| 16 | Appendix B - Drawdown Notice | 2 | 始终 |
| 17 | Appendix C - Fee Schedule | 2 | 始终 |
| 18 | Signature Page | 2 | 始终 |
| **合计** | | **~82** | |
