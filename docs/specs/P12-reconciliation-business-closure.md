# P12 SPEC — 核销业务闭环 (审批/驳回/差额/预收预付)
> **版本**：V1.0 | **最后修改**：2026-07-19 | **作者**：Hermes
> **状态**：✅ 生效
> **关联PRD**：../prd/应收应付核销-PRD-V1.0.md

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

## 5. BDD 验收场景 (Given-When-Then)

### 5.1 P12-1: 核销审批/驳回

```gherkin
Feature: 核销审批与驳回 (P12-1)
  As 财务审核员
  I want 对已确认的核销单进行审批或驳回
  So that 核销流程可以闭环

  Background:
    Given 存在一笔核销单，状态为 CONFIRMED

  Scenario: 审批通过 — CONFIRMED → EXECUTED
    Given 核销单状态为 CONFIRMED
    When 用户调用 POST /api/v1/reconciliations/{id}/approve
    Then 核销单状态变为 EXECUTED
    And 记录 approvedBy 为当前操作人
    And 记录 approvedAt 为当前时间戳
    And 应收/应付的 unsettled_amount 已扣减

  Scenario: 审批通过 — 状态校验失败 (非 CONFIRMED)
    Given 核销单状态为 PENDING
    When 用户调用 POST /api/v1/reconciliations/{id}/approve
    Then 系统抛出 BusinessException
    And 错误码为 RECONCILIATION_STATUS_INVALID
    And 核销单状态不变

  Scenario: 驳回 — CONFIRMED → REJECTED
    Given 核销单状态为 CONFIRMED
    When 用户调用 POST /api/v1/reconciliations/{id}/reject?reason=金额有误
    Then 核销单状态变为 REJECTED
    And 应收/应付的 unsettled_amount 已回滚至核销前金额

  Scenario: 驳回 — reason 为空
    Given 核销单状态为 CONFIRMED
    When 用户调用 POST /api/v1/reconciliations/{id}/reject?reason=
    Then 系统抛出 BusinessException
    And 错误码为 REJECT_REASON_REQUIRED

  Scenario: 反核销 — EXECUTED → CANCELLED
    Given 核销单状态为 EXECUTED
    When 用户调用 reverse 操作
    Then 核销单状态变为 CANCELLED
    And 应收/应付的 unsettled_amount 已回滚
```

### 5.2 P12-2: 差额调整

```gherkin
Feature: 核销差额调整 (P12-2)
  As 财务审核员
  I want 在收款金额与应收金额不一致时自动生成差额调整分录
  So that 核销差异可计入相应科目

  Background:
    Given 存在一笔应收 10,000.00
    And 存在一笔银行收款 9,800.00

  Scenario: 手续费差额 (FEE)
    Given 差额类型为 FEE，差额 200.00
    When 调用 executeWithAdjustment(request, 200.00, "FEE", 6601)
    Then 主核销日志金额为 10,000.00 (source → target)
    And 生成第二条核销日志 (RECON_TYPE=ADJUSTMENT, source=bank_txn, target=6601)
    And 调整金额 200.00 计入财务费用科目

  Scenario: 折扣让零差额 (DISCOUNT)
    Given 差额类型为 DISCOUNT，差额 50.00
    When 调用 executeWithAdjustment(request, 50.00, "DISCOUNT", 6603)
    Then 主核销日志金额为 10,000.00
    And 调整金额 50.00 计入销售折扣科目

  Scenario: 尾差差额 (TAIL)
    Given 差额类型为 TAIL，差额 100.00
    When 调用 executeWithAdjustment(request, 100.00, "TAIL", 5711)
    Then 主核销日志金额为 10,000.00
    And 调整金额 100.00 计入营业外收入/支出科目
```

### 5.3 P12-3: 预收/预付

```gherkin
Feature: 预收预付完整实现 (P12-3)
  As 财务系统
  I want 在供应商无未结清应付时自动走预付账款路径
  So that 符合会计实务

  Scenario: 供应商无未结清应付 — 走预付路径
    Given 供应商"广州建材公司"无未结清应付
    When 向该供应商付款 50,000.00
    Then 系统检测到 hasOpenPayables = false
    And 创建 t_prepayment 记录
    And prepayment.vendor_id = 供应商ID
    And prepayment.amount = 50,000.00
    And prepayment.status = 'DRAFT'

  Scenario: 供应商有未结清应付 — 走应付路径
    Given 供应商"广州建材公司"存在未结清应付 30,000.00
    When 向该供应商付款 50,000.00
    Then 系统检测到 hasOpenPayables = true
    And 按 P10-3/4 老逻辑生成 t_payable
    And 不创建 t_prepayment

  Scenario: 预付款数据模型
    Given t_prepayment 表已创建
    Then 表包含字段: id, tenant_id, vendor_id, doc_id, voucher_id, period, tx_date, amount, settled_amount, unsettled_amount, summary, status, source_doc_type, source_doc_id, remark, created_by, created_at, updated_at, deleted
    And 表包含索引: idx_t_prepayment_vendor, idx_t_prepayment_status
```

## 6. YAML 契约

```yaml
openapi: 3.0.3
info:
  title: 核销业务闭环 API (P12)
  version: "1.0.0"
  description: 审批/驳回/差额调整/预收预付

paths:
  /api/v1/reconciliations/{id}/approve:
    post:
      summary: 核销审批 — CONFIRMED → EXECUTED
      tags: [核销审批]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      requestBody:
        content: {}
      responses:
        "200":
          description: 审批成功
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ReconciliationApproveResponse"
        "400":
          description: 状态校验失败
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"

  /api/v1/reconciliations/{id}/reject:
    post:
      summary: 核销驳回 — CONFIRMED → REJECTED
      tags: [核销审批]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
        - name: reason
          in: query
          required: true
          schema:
            type: string
            minLength: 1
      responses:
        "200":
          description: 驳回成功
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ReconciliationRejectResponse"
        "400":
          description: reason 为空或状态校验失败
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"

  /api/v1/reconciliations/{id}/executeWithAdjustment:
    post:
      summary: 差额调整核销
      tags: [差额调整]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/ExecuteWithAdjustmentRequest"
      responses:
        "200":
          description: 差额核销成功
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ReconciliationLogEntity"
        "400":
          description: 参数校验失败
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"

  /api/v1/reconciliations/{id}/reverse:
    post:
      summary: 反核销 — EXECUTED → CANCELLED
      tags: [核销审批]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        "200":
          description: 反核销成功
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ReconciliationReverseResponse"

components:
  schemas:
    ReconciliationApproveResponse:
      type: object
      properties:
        id:
          type: integer
          format: int64
          description: 核销单 ID
        status:
          type: string
          enum: [EXECUTED]
          description: 审批后状态
        approvedBy:
          type: string
          description: 审批人
        approvedAt:
          type: string
          format: date-time
          description: 审批时间
      required: [id, status, approvedBy, approvedAt]

    ReconciliationRejectResponse:
      type: object
      properties:
        id:
          type: integer
          format: int64
        status:
          type: string
          enum: [REJECTED]
        reason:
          type: string
      required: [id, status, reason]

    ReconciliationReverseResponse:
      type: object
      properties:
        id:
          type: integer
          format: int64
        status:
          type: string
          enum: [CANCELLED]
      required: [id, status]

    ExecuteWithAdjustmentRequest:
      type: object
      properties:
        executeRequest:
          $ref: "#/components/schemas/ExecuteRequest"
        adjustAmount:
          type: number
          format: bigdecimal
          description: 差额
        adjustType:
          type: string
          enum: [FEE, DISCOUNT, TAIL]
          description: 差额类型: 手续费/折扣让零/尾差
        adjustSubjectId:
          type: integer
          format: int64
          description: 差额科目 ID
      required: [executeRequest, adjustAmount, adjustType, adjustSubjectId]

    ExecuteRequest:
      type: object
      description: 标准核销请求
      properties:
        sourceId:
          type: integer
          format: int64
        targetId:
          type: integer
          format: int64
        amount:
          type: number
          format: bigdecimal

    ReconciliationLogEntity:
      type: object
      properties:
        id:
          type: integer
          format: int64
        reconType:
          type: string
          enum: [NORMAL, ADJUSTMENT]
        sourceId:
          type: integer
          format: int64
        targetId:
          type: integer
          format: int64
        amount:
          type: number
          format: bigdecimal
        status:
          type: string
          enum: [CONFIRMED, EXECUTED, REJECTED, CANCELLED]

    ErrorResponse:
      type: object
      properties:
        code:
          type: string
          description: 错误码 (e.g. RECONCILIATION_STATUS_INVALID, REJECT_REASON_REQUIRED)
        message:
          type: string
          description: 错误描述
      required: [code, message]
```

## 7. 测试验收

| 批 | 新增测试 | 关键桩 |
|---|---|---|
| P12-1 | +6 | approve/reject 状态机, 金额回滚 |
| P12-2 | +3 | executeWithAdjustment 三种类型 |
| P12-3 | +2 | hasOpenInvoices=true/false 两条路径 |

**验收**: 235 → 250 (+15 测试, 0 fail, 0 error)

---

## 8. 不在 P12 范围

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
