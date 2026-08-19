# 09-财务报表与分析设计

> **关联PRD**：../prd/报表中心-PRD-V1.0.md
> **编号**：HUICAI-DES-010
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：初始创建
> 代码包：`com.huicai.module.report`
> 设计文档：[项目说明](../CORE-项目说明.md) | [技术方案](../CORE-技术方案.md) | [需求分析](../CORE-需求分析.md)

---

## 1. 模块定位

传统定位：法定报告出具与基础经营分析。内置三大报表（资产负债表/利润表/现金流量表）模板。

**对比传统：**
- 传统：Excel 导出，当前：**在线查看 + EasyExcel 导出**
- 传统：固定维度查询，当前：**杜邦分析 + 趋势分析 + 财务指标**
- 传统：无预警，当前：**预警规则引擎（可配置阈值）**

**与传统差距：** 自定义报表（拖拽设计器）功能较弱。

## 2. 核心组件

| 组件 | 说明 |
|------|------|
| ReportService | 三大报表生成（科目余额公式驱动） |
| AnalysisService | 杜邦分析、趋势分析、同比环比 |
| FinancialMetricService | 财务指标计算（可配置公式） |

## 3. 数据模型

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| t_report_template | 报表模板 | name, type(balance/profit/cashflow), formula_def(jsonb) |
| t_cash_flow_rule | 现金流量规则 | subject_id, direction, cash_flow_item |
| t_financial_metric | 财务指标 | code, name, formula, threshold, alert_enabled |

## 4. 报表生成逻辑

```
科目余额(t_subject_balance)
  │
  ├── 资产负债表 ← 余额类科目（期末余额）
  ├── 利润表 ← 发生额类科目（本期发生额）
  └── 现金流量表 ← 凭证分录标签 + CashFlowRule
```

## 5. API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| /api/v1/reports/balance-sheet | GET | 资产负债表 |
| /api/v1/reports/income-statement | GET | 利润表 |
| /api/v1/reports/cash-flow | GET | 现金流量表 |
| /api/v1/reports/subject-balance | GET | 科目余额表 |
| /api/v1/reports/trend | GET | 趋势数据(多期) |
| /api/v1/reports/analysis/dupont | GET | 杜邦分析 |
| /api/v1/reports/analysis/key-metrics | GET | 关键指标 |
| /api/v1/reports/analysis/metrics | GET | 指标定义 |
| /api/v1/reports/analysis/yoy-mom | GET | 同比环比 |
| /api/v1/reports/subject-balance/export | GET | 科目余额表导出 |
| /api/v1/reports/balance-sheet/export | GET | 资产负债表导出 |
| /api/v1/reports/income-statement/export | GET | 利润表导出 |
| /api/v1/reports/cash-flow/export | GET | 现金流量表导出 |

## 6. AI 叠加场景

| 场景 | 说明 | 优先级 |
|------|------|--------|
| 自然语言查数 | "上个月收入多少"→SQL→图表 | 🔵 P3 |
| 异常指标告警 | 超阈值自动通知 | 🟡 P2 |
| 智能分析报告 | AI 生成月度经营分析 | 🔵 P3 |

## 7. 成熟度与待办

| 维度 | 状态 | 备注 |
|------|------|------|
| 后端 | ⚠️ 基础 | 三大报表+杜邦分析可实现 |
| 前端 | ⚠️ 基础 | 报表查看页面存在 |
| 测试 | ❌ 缺 | ReportServiceImplTest 仅有 Mock 测试 |
| 对传统覆盖 | ✅ 主要功能 | 三大报表+指标分析 |
| 与传统差距 | 自定义报表 | 拖拽设计器未实现 |

> **文档结束**