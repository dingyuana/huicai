# P20 设计文档 — AR/AP 核心业务规则与状态机设计

> 版本：V1.0 | 日期：2026-06-17 | 状态：草稿 → 待评审
> 对应：项目说明书 §4.7 往来管理、P10 应收应付集成

---

## 1. 业务概述

### 1.1 范围

慧财财务系统的往来管理（AR/AP）覆盖以下核心流程：

| 流程 | 入口 | 终点 |
|------|-----|------|
| 销售 → 收款 | 销项发票导入 / 银行流水 business_receipt | 应收单核销 + 凭证记录 |
| 采购 → 付款 | 进项发票导入 / 银行流水 business_payment | 应付单核销 + 凭证记录 |
| 预付款 | 银行流水 business_payment（无未结清应付时） | 预付款核销 |
| 坏账准备 | 账龄分析 / 按比例计提 | 坏账确认 |

### 1.2 设计原则

1. **不可逆性**：已确认/已记账的记录不可直接修改，必须通过红冲/反核销更正
2. **人工审核铁律**：AI 和自动流程只到"推荐/待确认"，最终确认权在财务人员
3. **金额驱动**：应收/应付的"状态"由金额决定（unsettled_amount = 0 即结清）
4. **审计追溯**：所有核销操作记录 reconciliation_log，支持反核销

---

## 2. 实体状态机设计

### 2.1 应收单（Receivable）— `t_receivable`

#### 当前实现现状

**`ReceivableEntity` 当前无 status 字段**，状态通过 `unsettled_amount` 隐式推断：

| unsettled_amount | 语义 |
|-----------------|------|
| = amount（初始） | 未收款 |
| = 0 | 已结清 |
| > 0 且 < amount | 部分收款 |

**问题**：无法区分"草稿/已确认/已核销"等业务状态，审计追溯粒度不够。

#### 设计方案（新增状态字段）

```
[DRAFT] ──confirm──> [CONFIRMED] ──settle(核销)──> [SETTLED]
                       │
                       └──reverse──> [REVERSED]（仅从 CONFIRMED）
                             
[CONFIRMED] + unsettled_amount > 0 → 部分收款（语义状态，不改变 status 值）
```

| 状态 | 含义 | 说明 |
|------|------|------|
| `DRAFT` | 草稿 | 手工录入或导入后初始状态，允许修改 |
| `CONFIRMED` | 已确认 | 债权确认，参与账龄分析，不可修改金额 |
| `SETTLED` | 已结清 | unsettled_amount = 0 时自动或手动标记 |
| `REVERSED` | 已冲销 | 反核销/红冲后置为此状态 |

**状态转换表**：

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

### 2.2 应付单（Payable）— `t_payable`

**与应收单对称**，状态机设计完全一致，仅方向相反：

```
[DRAFT] ──confirm──> [CONFIRMED] ──settle(核销)──> [SETTLED]
                       │
                       └──reverse──> [REVERSED]
```

### 2.3 核销单（Settlement）— `t_arap_settlement`

#### 当前实现现状

现有状态流转：`DRAFT → CONFIRMED`，缺失后续状态。

#### 补全方案

```
[DRAFT] ──confirm──> [CONFIRMED] ──generateVoucher──> [VOUCHERED]
                       │
                       └──reverse──> [REVERSED]
                             
[CONFIRMED] ──reverse──> [REVERSED]（反核销）
```

| 状态 | 含义 | 说明 |
|------|------|------|
| `DRAFT` | 草稿 | 创建后初始状态，可修改可删除 |
| `CONFIRMED` | 已确认 | 更新应收/应付的 settled_amount，不可修改 |
| `VOUCHERED` | 已记账 | 已生成凭证，终态 |
| `REVERSED` | 已冲销 | 反核销后状态 |

### 2.4 预付款（Prepayment）— `t_prepayment`

#### 当前实现现状

`PrepaymentEntity` 声明了 `DRAFT/SUBMITTED/AUDITED/POSTED` 状态注释，但代码中仅在 `AutoGenerationService` 设置了 `DRAFT`，**无提交/审核/记账实现**。

#### 方案选择：简化设计

预付款的业务性质决定了它不需要完整的审批流：

```
[DRAFT] ──confirm──> [CONFIRMED] ──apply(核销抵扣)──> [APPLIED]
```

| 状态 | 含义 |
|------|------|
| `DRAFT` | 草稿 |
| `CONFIRMED` | 已确认（可参与付款抵扣） |
| `APPLIED` | 已核销抵扣（预付转应付） |

### 2.5 报销单（ExpenseReimbursement）— 现状 ✅

`t_expense_reimbursement` 已有完整状态机：
```
DRAFT ──submit──> SUBMITTED ──approve──> APPROVED ──generateVoucher──> VOUCHERED
                                        └─reject──> REJECTED
```
现有实现完整，无需修改。

---

## 3. 交互流程

### 3.1 发票导入 → 应收/应付

```
销项发票导入                 进项发票导入
     │                            │
     v                            v
 创建 BusinessDoc             创建 BusinessDoc
 (INVOICE_OUT, DRAFT)         (INVOICE_IN, DRAFT)
     │                            │
     v                            v
 生成凭证 (DRAFT)             生成凭证 (DRAFT)
     │                            │
     v                            v
 写入 OutputInvoice           写入 InputInvoice
     │                            │
     v                            v
 创建 Receivable (DRAFT)      创建 Payable (DRAFT)
     │                            │
     v                            v
  → 人工确认 → CONFIRMED      → 人工确认 → CONFIRMED
```

### 3.2 银行流水确认 → 核销

```
银行流水 B 类确认（business_receipt / business_payment）
     │
     ├─ P10-1: 创建 BusinessDoc (RECEIPT/PAYMENT, 初始 VOUCHERED)
     ├─ P10-2: 生成凭证 (DRAFT)
     ├─ P10-3: 创建 Receivable/Payable (CONFIRMED)
     │              │
     │              └─ 预付检测: 有未结清应付 → 走应付
     │                             无未结清应付 → 走预付款 (DRAFT)
     │
     └─ P10-4: 自动核销（停在 CONFIRMED）
                    │
                    v
               Settlement (CONFIRMED)
                    │
                    └─ 核销工作台 → 人工确认执行 → EXECUTED
```

### 3.3 核销工作台交互

```
核销工作台
  ├─ 展示所有 bank_txn 来源且未完全核销的流水
  ├─ 系统自动推荐匹配单据（按匹配度排序 ≥ 0.7）
  ├─ 出纳点击"核销推荐" → 展示匹配明细
  ├─ 出纳确认核销方案 → execute() → CONFIRMED
  │      └─ 更新 settlement.status = CONFIRMED
  │      └─ 更新 receivable/payable unsettled_amount
  │
  └─ 出纳可反核销 → reverse() → REVERSED
         └─ 恢复 unsettled_amount 原值
```

---

## 4. 数据库变更

### 4.1 `t_receivable` 新增字段

```sql
ALTER TABLE t_receivable ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';
COMMENT ON COLUMN t_receivable.status IS '状态: DRAFT/CONFIRMED/SETTLED/REVERSED';
```

### 4.2 `t_payable` 新增字段

```sql
ALTER TABLE t_payable ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';
COMMENT ON COLUMN t_payable.status IS '状态: DRAFT/CONFIRMED/SETTLED/REVERSED';
```

### 4.3 `t_prepayment` 状态修正

```sql
-- 保持 status 字段，注释更新为简化后的状态集
COMMENT ON COLUMN t_prepayment.status IS '状态: DRAFT/CONFIRMED/APPLIED';
```

---

## 5. 与现有代码的差距分析

| 设计文档要求 | 代码现状 | 差距 |
|------------|---------|------|
| 收款单(Receipt)独立实体+状态机 | ❌ 不存在 | 当前用 BusinessDoc + Receivable 替代，不加新实体 |
| 付款单(Payment)独立实体+状态机 | ❌ 不存在 | 同上，不加新实体 |
| 应收单状态 DRAFT/CONFIRMED/SETTLED/REVERSED | ❌ 无 status 字段 | 需要新增字段+迁移 |
| 应付单状态 DRAFT/CONFIRMED/SETTLED/REVERSED | ❌ 无 status 字段 | 需要新增字段+迁移 |
| 核销单完整状态机 | ⚠️ 只有 DRAFT→CONFIRMED | 补全 VOUCHERED/REVERSED |
| 预付款状态机 | ⚠️ 声明了但未实现 | 简化设计避免过度工程 |
| 三单匹配（PO-GRN-Invoice） | ❌ 未实现 | 后置迭代 |
| 付款审批流 | ❌ 未实现 | 后置迭代 |
| 反核销 API | ⚠️ ReconciliationController 有 reverse 端点 | 后端未完整实现 |

### 设计调整说明

相比原始设计文档的"理想方案"，本文档做了以下务实调整：

1. **不新增 Receipt/Payment 实体**：当前 BusinessDoc(RECEIPT/PAYMENT) + Receivable/Payable 的组合已覆盖业务需求，新增实体增加复杂度但价值有限
2. **Receivable/Payable 新增 status 字段**：最小改动解决"无法区分状态"的问题
3. **预付款简化**：预付款是过渡性科目，不需要完整的 DRAFT→SUBMITTED→AUDITED→POSTED 流程
4. **核销单补全**：最低成本对齐凭证状态机的 VOUCHERED 终态

---

## 6. 未覆盖场景（后置迭代）

| 场景 | 说明 | 优先级 |
|------|------|--------|
| 三单匹配 | PO→入库单→发票的金额/数量校验 | P30+ |
| 付款审批流 | 付款申请→主管审批→出纳付款 | P30+ |
| 自动催款 | 应收到期未收自动发送催款通知 | P40+ |
| 账龄分析报表 | 按期间/客户/账龄段统计应收 | P25（已有部分基础） |
