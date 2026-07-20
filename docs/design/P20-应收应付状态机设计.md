# P20 设计文档 — AR/AP 核心业务规则与状态机设计

> 版本：V2.0 | 日期：2026-07-09 | 状态：已生效
> 对应：REQ-2026-012~015、P10 应收应付集成、P34 架构变更
> **重要变更**：V74 架构重构后，统一使用 `t_business_doc` 替代原有的 `t_receivable`/`t_payable` 独立表

---

## 1. 业务概述

### 1.1 范围

慧财财务系统的往来管理（AR/AP）覆盖以下核心流程：

| 流程 | 入口 | 终点 |
|------|-----|------|
| 销售 → 收款 | 销项发票导入 / 银行流水 business_receipt（B类路由） | 应收单核销 + 凭证记录 |
| 采购 → 付款 | 进项发票导入 / 银行流水 business_payment（B类路由） | 应付单核销 + 凭证记录 |
| 预付款 | 银行流水 business_payment（无未结清应付时） | 预付款核销 |
| 坏账准备 | 账龄分析 / 按比例计提 | 坏账确认 |

### 1.2 设计原则

1. **不可逆性**：已确认/已记账的记录不可直接修改，必须通过红冲/反核销更正
2. **人工审核铁律**：AI 和自动流程只到"推荐/待确认"，最终确认权在财务人员
3. **金额驱动**：应收/应付的"状态"由金额决定（unsettled_amount = 0 即结清）
4. **审计追溯**：所有核销操作记录 reconciliation_log，支持反核销
5. **统一单据模型**：V74 架构变更后，应收/应付统一使用 `t_business_doc` 表，通过 `docType` 区分（INVOICE_OUT / INVOICE_IN / RECEIPT / PAYMENT / OTHER_RECEIVABLE / OTHER_PAYABLE）

---

## 2. 实体状态机设计

### 2.1 业务单据（BusinessDoc）— `t_business_doc`

应收/应付方向的单据统一使用 `t_business_doc` 表，通过 `docType` 区分方向。

**状态机：**
```
[DRAFT] ──confirm──→ [CONFIRMED] ──settle(核销)──→ [SETTLED]
                       │
                       └──reverse──→ [REVERSED]（仅从 CONFIRMED）
                            
[CONFIRMED] + unsettled_amount > 0 → 部分收款（语义状态，不改变 status 值）
```

| 状态 | 含义 | 说明 |
|------|------|------|
| `DRAFT` | 草稿 | 手工录入或导入后初始状态，允许修改 |
| `CONFIRMED` | 已确认 | 债权确认，参与账龄分析，不可修改金额 |
| `SETTLED` | 已结清 | unsettled_amount = 0 时自动或手动标记 |
| `REVERSED` | 已冲销 | 反核销/红冲后置为此状态 |

**状态转换表：**

| 当前状态 | 操作 | 下个状态 | 条件 |
|---------|------|---------|------|
| (新建) | create | DRAFT | — |
| DRAFT | confirm | CONFIRMED | — |
| DRAFT | delete | (删除) | 物理删除 |
| CONFIRMED | 核销 | CONFIRMED | unsettled_amount 减少，>0 保留状态 |
| CONFIRMED | settle() | SETTLED | unsettled_amount = 0 |
| CONFIRMED | reverse | REVERSED | 反核销，冲回金额 |
| SETTLED | reverse | CONFIRMED | 反核销恢复未结清 |
| REVERSED | — | — | 终态 |

### 2.2 核销单（Settlement）— `t_arap_settlement`

**状态机：**
```
[DRAFT] ──submit──→ [SUBMITTED] ──approve──→ [CONFIRMED] ──generateVoucher──→ [VOUCHERED]
   │                       │                           │
   │                       ↓                           ↓
   │                   CANCELLED                   REJECTED
   │
   └──cancel──→ [CANCELLED]

[VOUCHERED] ──reverse(红冲)──→ [REVERSED]（创建对冲核销单，金额为负）
```

| 状态 | 含义 | 说明 |
|------|------|------|
| `DRAFT` | 草稿 | 创建后初始状态，可修改可删除 |
| `SUBMITTED` | 已提交 | 等待审批，不可修改 |
| `CONFIRMED` | 已确认 | 审批通过，更新应收/应付的 settled_amount，不可修改 |
| `REJECTED` | 已驳回 | 审批拒绝，退回 DRAFT |
| `VOUCHERED` | 已记账 | 已生成凭证，终态 |
| `REVERSED` | 已冲销 | 反核销后状态 |
| `CANCELLED` | 已取消 | 取消草稿或驳回后取消 |

**状态转换表：**

| 当前状态 | 目标状态 | 操作 | 条件 |
|---------|---------|------|------|
| (新建) | DRAFT | create | — |
| DRAFT | SUBMITTED | `submit(id)` | 核销金额校验通过 |
| DRAFT | CANCELLED | `cancel(id)` | 取消草稿 |
| SUBMITTED | CONFIRMED | `approve(id)` | 审批通过 |
| SUBMITTED | REJECTED | `reject(id, reason)` | 审批驳回，需填写原因 |
| SUBMITTED | CANCELLED | `cancel(id)` | 取消提交中的核销单 |
| CONFIRMED | VOUCHERED | `generateVoucher(id)` | 凭证生成成功 |
| VOUCHERED | REVERSED | `reverse(id, reason)` | 红冲反核销，**必须创建对冲核销单** |

**红冲规范（对齐 Voucher 模式）：**
- `reverse()` **不直接改 status**，而是创建一条新的对冲核销单（`settlementType=REVERSAL`，金额取负，`reversedFromSettlementId` 指向原单）
- 原核销单 status 改为 `REVERSED`
- 对冲核销单初始 status = `DRAFT`，需走完整的 submit→approve 流程
- 对冲核销单审批通过后回滚业务单据的 settled_amount/unsettled_amount

### 2.3 预付款（Prepayment）— `t_business_doc`（docType=PREPAYMENT）

**简化状态机：**
```
[DRAFT] ──confirm──→ [CONFIRMED] ──apply(核销抵扣)──→ [APPLIED]
```

| 状态 | 含义 |
|------|------|
| `DRAFT` | 草稿 |
| `CONFIRMED` | 已确认（可参与付款抵扣） |
| `APPLIED` | 已核销抵扣（预付转应付） |

### 2.4 报销单（ExpenseReimbursement）— `t_expense_reimbursement`

已有完整状态机，无需修改：
```
DRAFT ──submit──→ SUBMITTED ──approve──→ APPROVED ──generateVoucher──→ VOUCHERED
                                        └─reject──→ REJECTED
```

---

## 3. 交互流程

### 3.1 发票导入 → 应收/应付

```
销项发票导入                  进项发票导入
     │                            │
     v                            v
 创建 BusinessDoc             创建 BusinessDoc
 (INVOICE_OUT, DRAFT)         (INVOICE_IN, DRAFT)
     │                            │
     │ 人工审核 (confirm)         │ 人工审核 (confirm)
     v                            v
 创建 BusinessDoc             创建 BusinessDoc
 (INVOICE_OUT, CONFIRMED)     (INVOICE_IN, CONFIRMED)
     │                            │
     ├── 生成凭证 (DRAFT)         ├── 生成凭证 (DRAFT)
     │                            │
     └── 核销工作台 ←─────────────┘
          │
          ▼
      核销结算 → 核销单 → 凭证
```

### 3.2 银行流水 → 应收/应付

```
银行流水导入
     │
     ▼
  A类（银行对账）→ 直接生成凭证
  B类（业务确认）→ 创建 BusinessDoc 草稿（RECEIPT/PAYMENT）
  C类（无法识别）→ 待人工分类
     │
     ▼
  人工确认 → 核销工作台匹配 → 核销 → 凭证
```

**铁律：** 银行流水不直接参与核销。正确路径：银行流水 → B类路由 → 生成收款单/付款单（BusinessDoc）→ 核销工作台匹配应收/应付。

---

## 4. 历史变更记录

| 版本 | 日期 | 变更内容 |
|------|------|---------|
| V1.0 | 2026-06-17 | 初始创建，基于 t_receivable/t_payable 独立表 |
| V2.0 | 2026-07-09 | 架构变更：统一使用 t_business_doc 替代 t_receivable/t_payable；更新核销流程、状态机、交互图；增加架构变更说明 |
| V3.0 | 2026-07-10 | 核销单增加 submit→approve 审核流程、红冲创建对冲单据、所有操作记录 reconciliation_log |

---

## 5. 关联文档

| 文档 | 说明 |
|------|------|
| 02-arap-design.md | AR/AP 模块设计主文档 |
| P30-reconciliation-workbench-enhance.md | 核销工作台增强 SPEC |
| P34-receivable-payable-to-businessdoc.md | V74 架构变更 SPEC |
| P43-reconciliation-log-settlement-state-machine.md | 核销日志与状态机 SPEC |
| 01-gl-design.md | 总账凭证状态机（6态含 CLOSED） |