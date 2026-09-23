# 经营状况分析设计

> **关联PRD**：../prd/经营状况分析-PRD-V1.0.md
> **关联SPEC**：P80-budget-variance-report.md、P81-metric-alert-scan.md、P82-cashflow-forecast.md、P83-management-dashboard.md（均预留，下个 PR 开发）
> **编号**：HUICAI-DES-014
> **版本**：V1.0 | **修改日期**：2026-09-22 | **修改人**：Hermes | **修改内容**：初始创建（外部 PRD 评估裁剪后落地）
> 代码包：`com.huicai.base.report`（P80~P83 新增端点）+ `com.huicai.sme.budget`（P80 数据源）
> 设计文档：[项目说明](../CORE-项目说明.md) | [技术方案](../CORE-技术方案.md) | [需求分析](../CORE-需求分析.md)

---

## 1. 模块定位

在既有报表中心（三大表/杜邦/指标/趋势）之上叠加**经营层分析**：预算差异、指标预警、现金流静态预测、管理驾驶舱。

**设计原则**：
- 全部基于已落库结果做只读聚合（铁律#1：报表不触发业务动作）
- 只选**有数据基础**的分析模型；无数据基础的（阿米巴/成本中心/行业数据）坚决不建模型
- 预警状态的人工消除走既有账龄预警 4 态模式，系统不自动闭环

## 2. 核心组件

| 组件 | 说明 | 数据基础 |
|------|------|---------|
| BudgetVarianceService | 预算 vs 实际 差异聚合 + 导出 | t_budget + 凭证发生额（P80） |
| MetricAlertScanService | t_financial_metric 阈值扫描 → 预警记录（幂等） | 指标表已建，alert_enabled 已预留（P81） |
| CashflowForecastService | 应收账龄×回收率 + 应付到期 → 30/60/90 天曲线 | 账龄分析 + 应付到期日（P82） |
| 管理驾驶舱（纯前端） | KPI 卡 + 趋势 + 利润瀑布，聚合 ReportController 既有 10 端点 | 零新计算端点（P83） |

## 3. 数据模型

| 表 | 状态 | 说明 |
|----|------|------|
| t_financial_metric | ✅ 已有 | code/formula/threshold/alert_enabled，P81 直接消费 |
| t_budget / t_budget_entry | ✅ 已有（DES-009） | P80 预算数来源 |
| t_metric_alert（P81 新增） | ❌ 待建 | 预警记录：metric_code/period/value/threshold/status/ignored_by，UNIQUE(metric_code,period,status=UNREAD 语义) |
| 回收率计算 | 无需新表 | P82 用近 12 期"应收回收流水 ÷ 期初余额"按账龄段聚合 |

## 4. 端点设计

| 端点 | 方法 | 归属 |
|------|------|------|
| /api/v1/reports/budget-variance(+export) | GET | P80 |
| /api/v1/reports/metric-alerts/scan | POST | P81 |
| /api/v1/reports/metric-alerts（list/ignore/resolve） | GET/POST | P81（复用账龄预警端点模式） |
| /api/v1/reports/cashflow-forecast?days=90 | GET | P82 |
| 驾驶舱 | 无新端点 | P83（前端聚合既有 /reports/*） |

## 5. 依赖与边界

- **依赖**：报表中心（DU 邦/指标/三大表端点）、预算模块、账龄分析、P76/P78 汇总模式（聚合+导出的编码范式）
- **边界（不进本模块）**：摊分引擎、CVP、行业对标、压力测试、行级权限、NLG 月报——理由见 PRD §6
- **推送通道**：预警落库为 P81 全部；企微/钉钉 webhook 推送单列 P2 候选（项目当前无消息通道组件，不为本需求新建）

## 6. 成熟度与待办

| 维度 | 状态 | 备注 |
|------|------|------|
| 后端 | ❌ 待建 | P80~P82 待下个 PR；数据基础已齐 |
| 前端 | ❌ 待建 | P83 驾驶舱视图 |
| 测试 | ❌ 待建 | 按 Contract-First 每 SPEC 绑定 test_ref |

> **文档结束**
