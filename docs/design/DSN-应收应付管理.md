# 02-应收应付管理设计

> **关联PRD**：../prd/业务单据管理-PRD-V1.0.md, ../prd/应收应付核销-PRD-V1.0.md
> **关联SPEC**：P30-reconciliation-workbench-enhance.md, P36-invoice-reverse-chain.md, P42-reconciliation-frontend.md, P43-bad-debt-provision.md, P51-aging-analysis.md, P52-customer-reconciliation.md, P53-procurement-payment-finance.md, S-28-反核销制证凭证联动作废.md
> **编号**：HUICAI-DES-003
> **版本**：V1.5 | **修改日期**：2026-09-17 | **修改人**：Hermes | **修改内容**：新增 §8 预收预付余额汇总报表设计（口径对齐 P75，填补 G-3 缺口）
> 代码包：`com.huicai.module.arap`
> 设计文档：[项目说明](../CORE-项目说明.md) | [技术方案](../CORE-技术方案.md) | [需求分析](../CORE-需求分析.md)

---

## 1. 模块定位

传统定位：往来款项的明细账本。应收记录客户欠款核销，应付记录供应商欠款，传统依赖"三单匹配"（采购单/入库单/发票）。

**核心架构变更：**
- 传统：独立 t_receivable / t_payable 子账
- **当前：已删除独立表（V74），统一合并到 t_business_doc，通过 doc_type 区分 INVOICE_OUT/INVOICE_IN**
- 核销唯一入口：**核销工作台**（ReconciliationController），原 Receivable/Payable Controller 已 @Deprecated

## 2. 核心组件

| 组件 | 说明 |
|------|------|
| ReconciliationService | 核销工作台：推荐匹配 + 执行核销 |
| ArapSettlementService | 核销单管理 + 凭证生成 |
| PrepaymentService | 预收预付管理 |
| BadDebtService | 坏账计提 |
| ExpenseReimbursementService | 费用报销（见 05） |
| ReceivableService | 应收单查询（已迁移 t_business_doc，仅保留分页） |
| PayableService | 应付单查询（已迁移 t_business_doc，仅保留分页） |

## 3. 数据模型

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| t_business_doc | 业务单据（替代应收/应付） | doc_no, doc_type, doc_date, amount, customer_id, vendor_id, status, invoice_no, voucher_no, settled_amount, unsettled_amount |
| t_business_doc_entry | 业务单据分录 | doc_id, amount, summary, invoice_no |
| t_arap_settlement | 核销单 | settlement_no, amount, status, party_id, party_type, voucher_no, adjustment_amount, adjustment_reason |
| t_arap_settlement_entry | 核销单明细 | settlement_id, business_doc_id, amount, before_balance, after_balance |
| t_prepayment | 预收预付 | party_id, party_type, amount, doc_id |
| t_bad_debt_provision | 坏账计提 | period, amount, subject_id |
| t_reconciliation_tolerance | 核销容差配置 | id, party_id, party_type, tolerance_amount, tolerance_rate, effective_from, effective_to |
| t_reconciliation_log | 核销日志 | source_doc_type, source_doc_id, target_doc_type, target_doc_id, allocated_amount, match_score, match_method, status, operation_type, created_by, created_at |

### 3.1 新增余额快照字段（t_arap_settlement_entry）

| 字段 | 类型 | 说明 |
|------|------|------|
| before_balance | NUMERIC(18,2) | 核销前单据余额快照 |
| after_balance | NUMERIC(18,2) | 核销后单据余额快照 |

### 3.2 新增容差配置表（t_reconciliation_tolerance）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| tenant_id | BIGINT | 租户ID |
| party_id | BIGINT | 客户/供应商ID（NULL表示全局配置） |
| party_type | VARCHAR(20) | CUSTOMER/VENDOR |
| tolerance_amount | NUMERIC(18,2) | 容差金额阈值（默认5元） |
| tolerance_rate | NUMERIC(5,2) | 容差比例阈值（默认10%） |
| effective_from | DATE | 生效日期 |
| effective_to | DATE | 失效日期 |
| created_at | TIMESTAMP | 创建时间 |

### 3.3 核销日志字段增强（t_reconciliation_log）

| 字段 | 类型 | 说明 |
|------|------|------|
| operation_type | VARCHAR(20) | CREATE/CONFIRM/REJECT/CANCEL |
| rule_id | VARCHAR(50) | 触发规则ID（自动核销时记录） |

## 4. 核销流程

```
银行流水 ─→ B类路由 ─→ 收款单/付款单(t_business_doc, DRAFT)
                              ↓
                       核销工作台 ← 唯一入口
                              ↓
                 推荐匹配 → 执行核销 → 核销单(DRAFT)
                              ↓
                 generateVoucher → 凭证(DRAFT)
                              ↓
                       reverse（反核销，S-28/SPC-111）
                              ├─ DRAFT 凭证 → 联动作废 + 清空核销单/单据双侧挂接
                              └─ 非 DRAFT 凭证 → 拦截，提示先红冲（铁律#3）
```

## 5. API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| /api/v1/reconciliation/suggest | POST | 推荐核销匹配 |
| /api/v1/reconciliation/execute | POST | 执行核销 |
| /api/v1/reconciliation/{id}/trace | GET | **核销全链路追溯**（新增） |
| /api/v1/reconciliation/tolerance/** | CRUD | **容差配置管理**（新增） |
| /api/v1/reconciliation/tolerance/default | GET | **获取默认容差配置**（新增） |
| /api/v1/arap-settlements/** | CRUD | 核销单 |
| /api/v1/arap-settlements/{id}/generate-voucher | POST | 生成凭证 |
| /api/v1/arap-settlements/{id}/reverse | POST | **反核销**（S-28：DRAFT 凭证联动作废并清空双侧挂接；非 DRAFT 拦截提示先红冲） |
| /api/v1/prepayments/** | CRUD | 预收预付 |
|| /api/v1/bad-debts/** | CRUD | 坏账 | P43 |
|| /api/v1/aging-analysis/** | 账龄分析 | 账龄分析表、到期债权表、逾期预警 | P51 |
|| /api/v1/aging-analysis/payable-* | 应付账龄 | 应付账龄汇总、按供应商、到期应付 | P53 |
|| /api/v1/customer-statements/** | 客户对账 | 对账单生成、发送、确认、差异处理 | P52 |
|| /api/v1/customer-statements/** | 客户对账 | 对账单生成、发送、确认、差异处理 | P52 |
|| /api/v1/outstanding-items/** | 未达账项 | 未达账项管理 | P52 |
|| /api/v1/disputes/** | 差异处理 | 对账差异记录 | P52 |
|| /api/v1/payment-plans | GET | 付款计划 | P53 |
|| /api/v1/purchase-returns | POST/GET | 采购退货（财务） | P53 |
|| /api/v1/prepayment/available | GET | 可用预付款查询 | P53 |

### 5.1 核销全链路追溯 API

**GET /api/v1/reconciliation/{id}/trace**

一次性返回核销单的完整业务链路，包含：
- 核销单主表信息
- 上游资金链路（银行流水 → 收款单/付款单）
- 下游业务链路（应收单/应付单 → 发票）
- 操作轨迹列表（按时间正序）

**响应结构：**
```json
{
  "settlement": { ... },
  "upstream": {
    "bankTransaction": { ... },
    "receipt": { ... }
  },
  "downstream": {
    "businessDocs": [...],
    "invoices": [...]
  },
  "operationTrail": [
    {"operationType": "CREATE", "operator": "...", "time": "...", "remark": "..."},
    {"operationType": "CONFIRM", "operator": "...", "time": "...", "remark": "..."}
  ]
}
```

## 6. AI 叠加场景

| 场景 | 说明 | 优先级 |
|------|------|--------|
| 核销匹配推荐 | 基于金额+客户/供应商相似度推荐 | 🟡 P2 |
| 审核建议 | 费用报销 AI 初审 | 🟡 P2 |

## 7. 成熟度与待办

| 维度 | 状态 | 备注 |
|------|------|------|
| 后端 | ✅ 完整 | 核销工作台+坏账(P43)+账龄分析(P51)+客户对账(P52)+应付账龄(P53) |
| 前端 | ✅ 完整 | 核销工作台+结算列表+坏账列表+Timeline+穿透点击+FIFO自动核销 |
| 测试 | ✅ 良好 | ReconciliationControllerTest + 42 个测试、ReconciliationWorkbenchE2ETest |
| 对传统超越 | ✅ 统一 BusinessDoc、核销工作台统一入口 | |
| 与传统差距 | 账龄分析前端 | 后端有，前端待完善 |
|| 待开发规范 | — | 所有模块已完成 |

---

## 8. 预收预付余额汇总报表（V1.5 新增，G-3）

**定位**：管理型聚合报表。竞品基线（易代账/金蝶往来余额表）覆盖应收应付（P75），
预收预付是"贷方余额"，口径对称但方向相反，目前只有单笔 CRUD + 可用余额查询，缺按往来单位的汇总管理视图。

### 8.1 报表口径

| 项 | 定义 |
|----|------|
| 数据源 | t_prepayment（含 customer_id / vendor_id / amount / settled_amount / unsettled_amount / status / period） |
| 统计范围 | 指定期间内的有效预收预付单，`status ∈ (CONFIRMED, VOUCHERED, APPLIED)`（已生效，不含 DRAFT/REVERSED） |
| 汇总维度 | 按往来单位（预收=客户、预付=供应商），一行一单位 |
| 金额口径 | 期初未结清 + 本期新增 + 本期抵扣（apply-to-* 流水）+ 期末未结清 |
| 恒等式 | 期初 + 本期新增 − 本期抵扣 − 本期冲销 = 期末（与 P75 会计恒等式对称） |
| 导出 | EasyExcel 导出（与 P75 报表中心导出规范一致） |

### 8.2 API 端点

| 端点 | 方法 | 说明 | SPEC |
|------|------|------|------|
| /api/sme/arap/v1/prepayment/balance-summary | GET | 预收预付余额汇总（参数：period, party_type=PRE_RECEIPT\|PRE_PAYMENT, party_id） | P78 |
| /api/sme/arap/v1/prepayment/balance-summary/export | GET | 导出 Excel | P78 |

**响应结构（balance-summary 示意）：**

```json
{
  "period": "202609",
  "partyType": "PRE_RECEIPT",
  "rows": [
    {"partyId": 1, "partyName": "华东商贸",
     "openingUnsettled": 50000.00,
     "currentCreated": 30000.00,
     "currentApplied": 20000.00,
     "currentReversed": 0.00,
     "closingUnsettled": 60000.00}
  ],
  "total": {"openingUnsettled": 120000.00, "currentCreated": 80000.00,
            "currentApplied": 55000.00, "closingUnsettled": 145000.00},
  "consistent": true
}
```

### 8.3 异常与边界

| 场景 | 处理 |
|------|------|
| 期间无有效单据 | 返回空 rows + total 全 0，不报错 |
| 期初推导 | 同 P75：`openingUnsettled = closingUnsettled − currentCreated + currentApplied + currentReversed` |
| 已冲销（REVERSED）单据 | 计入本期冲销列，不计入期末余额 |
| 数据权限 | EnterpriseDataPermissionInterceptor 注入 enterprise_id |
| 恒等式校验 | consistent=false 时前端高亮告警，不阻止展示 |

### 8.4 与 P75 的关系

P75 处理应收/应付（借方余额：客户欠我们 / 我们欠供应商），
P78 处理预收/预付（贷方余额：我们欠客户 / 客户欠我们），
两者共用期间校验工具类、Excel 导出组件、数据权限拦截器，
但数据源表不同（t_business_doc vs t_prepayment），互不依赖。

## 9. 成熟度与待办（更新）

| 维度 | 状态 | 备注 |
|------|------|------|
| 预收预付余额汇总 | ❌ 待开发 | §8 已设计，SPEC P78 待建；口径复用 P75 恒等式逻辑 |

> **文档结束**