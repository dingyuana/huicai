# 02-应收应付管理设计

> **编号**：HUICAI-DES-003
> **版本**：V1.2 | **修改日期**：2026-07-08 | **修改人**：Hermes | **修改内容**：新增 Timeline 视图、穿透点击、FIFO 自动核销按钮
> 代码包：`com.huicai.module.arap`
> 设计文档：[主文档](../DESIGN.md)

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
| /api/v1/prepayments/** | CRUD | 预收预付 |
| /api/v1/bad-debts/** | CRUD | 坏账 |

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
| 后端 | ✅ 完整 | 核销工作台+坏账+预收预付 |
| 前端 | ✅ 完整 | 核销工作台+结算列表+坏账列表+Timeline+穿透点击+FIFO自动核销 |
| 测试 | ✅ 良好 | ReconciliationControllerTest + 42 个测试、ReconciliationWorkbenchE2ETest |
| 对传统超越 | ✅ 统一 BusinessDoc、核销工作台统一入口 | |
| 与传统差距 | 账龄分析前端 | 后端有，前端待完善 |

> **文档结束**