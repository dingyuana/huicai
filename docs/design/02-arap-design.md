# 02-应收应付管理设计

> **编号**：HUICAI-DES-003
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes | **修改内容**：初始创建
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
| t_business_doc | 业务单据（替代应收/应付） | doc_no, doc_type, doc_date, amount, customer_id, vendor_id, status, invoice_no, voucher_no |
| t_business_doc_entry | 业务单据分录 | doc_id, amount, summary, invoice_no |
| t_arap_settlement | 核销单 | settlement_no, amount, status, party_id, party_type, voucher_no |
| t_arap_settlement_entry | 核销单明细 | settlement_id, receivable_id, payable_id, amount |
| t_prepayment | 预收预付 | party_id, party_type, amount, doc_id |
| t_bad_debt_provision | 坏账计提 | period, amount, subject_id |

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
| /api/v1/arap-settlements/** | CRUD | 核销单 |
| /api/v1/arap-settlements/{id}/generate-voucher | POST | 生成凭证 |
| /api/v1/prepayments/** | CRUD | 预收预付 |
| /api/v1/bad-debts/** | CRUD | 坏账 |

## 6. AI 叠加场景

| 场景 | 说明 | 优先级 |
|------|------|--------|
| 核销匹配推荐 | 基于金额+客户/供应商相似度推荐 | 🟡 P2 |
| 审核建议 | 费用报销 AI 初审 | 🟡 P2 |

## 7. 成熟度与待办

| 维度 | 状态 | 备注 |
|------|------|------|
| 后端 | ✅ 完整 | 核销工作台+坏账+预收预付 |
| 前端 | ✅ 完整 | 核销工作台+结算列表+坏账列表 |
| 测试 | ✅ 良好 | ReconciliationControllerTest + 42 个测试 |
| 对传统超越 | ✅ 统一 BusinessDoc、核销工作台统一入口 | |
| 与传统差距 | 账龄分析前端 | 后端有，前端待完善 |

> **文档结束**