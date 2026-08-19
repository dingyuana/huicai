# P17 SPEC — 报表中心 (科目余额表/资产负债表/利润表/现金流量表/趋势)
> **版本**：V1.0 | **最后修改**：2026-07-19 | **作者**：Hermes
> **状态**：✅ 生效

> **编号**：HUICAI-SPC-017（9 方法全已实现, 0 测试覆盖）
> 目标：补 5 个单测 + 文档化 4 大报表
> 工期：1 批

> **test_ref**：ReportServiceImplTest, ReportServiceTest
---

> **关联需求**: REQ-2026-034, REQ-2026-035

## 1. 输入契约
→ 见本文 [现状摸底 / 报表参数定义] 章节

## 2. 输出契约
→ 见本文 [验收标准 / 测试用例 / 响应结构] 章节

## 3. 状态流转
→ 见本文 [报表生成流程 / 状态常量] 章节

## 4. 异常处理
→ 见本文 [BusinessException 抛出点 / 错误码定义] 章节

## 1. 现状摸底 (2026-06-15)

| 文件 | 状态 |
|---|---|
| `t_report_template` (报表模板) | ✅ 实体 (P3) |
| `t_financial_metric` (财务指标) | ✅ 实体 (P3) |
| `t_cash_flow_rule` (现金流分类规则) | ✅ 实体 (P3) |
| `ReportDataMapper` | ✅ 5 mapper 方法 (subjectBalance/balanceSheetAggregate/incomeStatementData/cumulativeData/cashFlowData/trendData) |
| `ReportServiceImpl` | ✅ **9/9 方法全实现** |
| `ReportServiceImplTest` | ❌ **零测试** — 本批补 |

**已实现方法**:
- `subjectBalanceTable(period)` — 科目余额表
- `balanceSheet(period)` — 资产负债表 (按 1xxx/2xxx/3xxx 前缀分组)
- `incomeStatement(period)` — 利润表 (收入/成本/费用)
- `cashFlowStatement(period)` — 现金流量表 (按 t_cash_flow_rule 分类)
- `trend(startPeriod, endPeriod)` — 趋势分析
- `export*` × 4 — Excel 导出 (Hutool ExcelWriter)

---

## 2. P17-1 任务 (本批)

### 2.1 补 5 个单测

| # | 测试 | 覆盖 |
|---|---|---|
| 1 | `subjectBalanceTable_返回list` | subjectBalanceTable |
| 2 | `balanceSheet_有数据_按科目前缀分组` | balanceSheet |
| 3 | `incomeStatement_有数据_返回收入费用` | incomeStatement |
| 4 | `cashFlowStatement_有数据_按规则分类` | cashFlowStatement |
| 5 | `trend_两期间_返回list` | trend |

### 2.2 关键设计

**资产负债表** — 按科目编码前缀分组:
- `1xxx` → 资产 (assets)
- `2xxx` → 负债 (liabilities)
- `3xxx` → 所有者权益 (equity)
- 验证恒等式: 资产 = 负债 + 所有者权益

**利润表** — 期间 + 累计:
- 当期: incomeStatementData(period)
- 累计: cumulativeData(period)

**现金流量表** — 按 t_cash_flow_rule 分类:
- 经营/投资/筹资 三大类

---

## 3. 不在 P17 范围

- 报表模板可视化编辑器 (前端)
- 报表订阅/定时推送 (P18 候选)
- 报表钻取 (drill-down)
- 多币种/合并报表

---

## 4. 测试验收

**目标**: 300 → 305 (+5 测试)

---
## 验收标准

| ID | 描述 | 断言 |
|----|------|------|
| AT-P17-1 | 资产负债表数据完整 | `balanceSheet(period) → assets == liabilities + equity` |
| AT-P17-2 | 利润表数据完整 | `profitStatement(period) → revenue - expense == profit` |
| AT-P17-3 | 科目余额表按层级展示 | `subjectBalance(period) → tree structure` |
| AT-P17-4 | 杜邦分析返回指标树 | `dupont(period) → ROE decomposed` |
