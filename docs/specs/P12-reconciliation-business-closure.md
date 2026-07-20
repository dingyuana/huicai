# P12 SPEC — 核销业务闭环 (审批/驳回/差额/预收预付)
> **版本**：V1.0 | **最后修改**：2026-07-19 | **作者**：Hermes
> **状态**：✅ 生效

> **编号**：HUICAI-SPC-012（main 分支 commit ff59d3d + 66fbea1）
> 工期：3 批工单 (P12-1 审批/驳回 / P12-2 差额调整 / P12-3 预收/预付)
> mvn test: 250/0/0

---

> **关联需求**: REQ-2026-020

## 1. 输入契约
→ 见本文 [业务背景 / 核销参数定义] 章节

## 2. 输出契约
→ 见本文 [验收标准 / 测试用例 / 响应结构] 章节

## 3. 状态流转
→ 见本文 [核销状态机图 / 状态常量 / 状态转换方法] 章节

## 4. 异常处理
→ 见本文 [BusinessException 抛出点 / 错误码定义] 章节

## 1. 业务背景

P5 + P10 已建核销基础 (推荐 L1-L5 + execute/reverse)，但**状态机不完整**：

- `execute()` 只能把日志设为 CONFIRMED，**没人能点"批准"让它走向 EXECUTED**
- 应收/应付金额与收款/付款金额有差异时（手续费/折扣/尾差），**没有自动调整机制**
- 供应商无未结清应付时收到付款，**仍生成 t_payable 而不是预付款**——不符合会计实务

P12 补齐这三块。

---

## 2. P12-1: 核销审批/驳回

### 2.1 状态机

```
              approve                reject
PENDING ──→ CONFIRMED ──────────→ EXECUTED
              │                      
              └──→ REJECTED (驳回)
              
              reverse
EXECUTED ──→ CANCELLED (反核销)
```

**修正**：`pending_review` 在 P12-1 已统一改为 `CONFIRMED`。

### 2.2 API

| 方法 | 路由 | 说明 |
|------|------|------|
| POST | `/api/v1/reconciliations/{id}/approve` | CONFIRMED → EXECUTED |
| POST | `/api/v1/reconciliations/{id}/reject?reason=...` | CONFIRMED → REJECTED |

### 2.3 关键设计

- `approve()` 校验状态=CONFIRMED，置 EXECUTED，记录 approvedBy/approvedAt
- `reject()` 校验状态=CONFIRMED 且 reason 非空，置 REJECTED
- 驳回/反核销都**回滚应收/应付的未结金额**（unsettledAmount）
- 业务单据 (t_business_doc) 状态联动

---

## 3. P12-2: 差额调整

### 3.1 场景

收款 9,800，但应收 10,000 — 差 200 块：

- **手续费**: 银行扣的 50 → 财务费用
- **折扣让零**: 客户砍价让的 50 → 销售折扣/主营业务收入
- **尾差**: 系统精度差 100 → 营业外收入/支出

### 3.2 API

```java
ReconciliationLogEntity executeWithAdjustment(
    ExecuteRequest request,             // 标准核销请求
    BigDecimal adjustAmount,            // 差额
    String adjustType,                  // FEE / DISCOUNT / TAIL
    Long adjustSubjectId                // 差额科目
)
```

### 3.3 行为

1. 主核销: source → target，金额 = 应收金额
2. 差额: 生成第二条核销日志 (RECON_TYPE=ADJUSTMENT, source=bank_txn, target=adjust_subject)
3. 调整金额计入相应科目 (FEE→6601 财务费用, DISCOUNT→5602.05 业务招待费或 6603 销售折扣)

---

## 4. P12-3: 预收/预付完整实现

### 4.1 场景

付款给供应商"广州建材公司" 50,000，但目前**没有该供应商的未结清应付**（还没收到发票或已全部结清）。这时：

- **传统做法**: 仍生成 t_payable（应付账款），等发票到时核销
- **会计实务**: 应走 **预付账款 (t_prepayment)**，因为这是先付款后收货的预付行为

### 4.2 检测逻辑

```java
boolean hasOpenPayables = reconciliationService.hasOpenInvoices("INVOICE_IN", vendorId);
if (!hasOpenPayables) {
    // 走预付款路径
    PrepaymentEntity prepay = new PrepaymentEntity();
    prepay.setVendorId(vendorId);
    prepay.setAmount(amount);
    prepaymentMapper.insert(prepay);
} else {
    // 走应付路径 (P10-3/4 老逻辑)
}
```

### 4.3 数据模型 (Flyway 待补 V35)

```sql
CREATE TABLE IF NOT EXISTS t_prepayment (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    vendor_id       BIGINT NOT NULL,
    doc_id          BIGINT,                   -- 关联 t_business_doc
    voucher_id      BIGINT,                   -- 关联 t_voucher
    period          VARCHAR(6),
    tx_date         DATE,
    amount          NUMERIC(18,2) NOT NULL,
    settled_amount  NUMERIC(18,2) DEFAULT 0,
    unsettled_amount NUMERIC(18,2),
    summary         VARCHAR(500),
    status          VARCHAR(20) DEFAULT 'DRAFT',
    source_doc_type VARCHAR(20),              -- bank_txn
    source_doc_id   BIGINT,
    remark          VARCHAR(500),
    created_by      VARCHAR(50),
    created_at      TIMESTAMP DEFAULT now(),
    updated_at      TIMESTAMP DEFAULT now(),
    deleted         INTEGER DEFAULT 0
);
CREATE INDEX idx_t_prepayment_vendor ON t_prepayment(vendor_id);
CREATE INDEX idx_t_prepayment_status ON t_prepayment(status);
```

### 4.4 后续 (P12-4+ 范围外)

- 预付冲应付 (P11 客户侧预收账款同步)
- 预付账款定期核销 (按发票到货周期)

---

## 5. 测试验收

| 批 | 新增测试 | 关键桩 |
|---|---|---|
| P12-1 | +6 | approve/reject 状态机, 金额回滚 |
| P12-2 | +3 | executeWithAdjustment 三种类型 |
| P12-3 | +2 | hasOpenInvoices=true/false 两条路径 |

**验收**: 235 → 250 (+15 测试, 0 fail, 0 error)

---

## 6. 不在 P12 范围

- 退/红字对冲 (P13 候选)
- 多笔合并核销 (一笔收款核销多张发票) — 当前已是 N:1 模型
- 信用期/账龄驱动的智能核销

---
## 验收标准

| ID | 描述 | 断言 |
|----|------|------|
| AT-P12-1 | 核销执行后应收单unsettled_amount减少 | `after execute: receivable.unsettled_amount < before` |
| AT-P12-2 | 核销单确认后状态为CONFIRMED | `confirm() → settlement.status == 'CONFIRMED'` |
| AT-P12-3 | 核销单生成凭证后状态为VOUCHERED | `generateVoucher() → settlement.status == 'VOUCHERED'` |
| AT-P12-4 | 预收预付创建后状态为DRAFT | `prepayment.status == 'DRAFT'` |
