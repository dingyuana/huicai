# P40 - 进项发票状态机与下游流程衔接

> **版本**：V1.0 | **日期**：2026-07-17 | **作者**：Hermes
> **编号**：HUICAI-SPC-040B
> **状态**：**✅ 已实现**

> **test_ref**：InputInvoiceStateMachineServiceImplTest, InputInvoiceImportServiceTest
---

## 0. SDD 四段结构索引

### 1. 输入契约
→ 见本文 [## 3. 详细设计 — 数据库 Migration / 状态机接口 / API 端点](#3-详细设计)

### 2. 输出契约
→ 见本文 [## 4. 测试计划 — 单元测试与集成测试断言](#4-测试计划)

### 3. 状态流转
→ 见本文 [## 3.2 状态机定义 — InvoiceStatus 8态流转图](#32-状态机定义)

### 4. 异常处理
→ 见本文各 BusinessException 抛出点 / ## 6. 待确认问题

## 1. 问题背景

### 1.1 现状
进项发票（t_input_invoice）当前存在三大缺陷：

1. **无 status 字段**：数据库没有 status 列，Entity 没有 status 属性。仅有 `certification_status`（认证状态），与审核状态机是两个不同维度。
2. **无状态机**：销项发票有完整的 `OutputInvoiceStateMachineService`（8态状态机 + 7个API），进项完全没有对应实现。
3. **无下游衔接**：手动创建的进项发票 `certify()` 只改认证状态，不创建 BusinessDoc、不生成凭证、不关联应付。导入路径虽一步到位创建 BusinessDoc+Voucher，但绕过了审核流程。

### 1.2 设计文档已有的定义
`docs/design/DSN-发票税务管理.md` §4 定义了进项应走6步流程：
```
① 导入 -> PENDING_CONFIRM（仅创建发票，不自动生单）
② 人工提交审核 -> PENDING_REVIEW
③ 人工审核通过 -> CONFIRMED
④ 人工点击"生成业务单据" -> BusinessDoc DRAFT
⑤ 人工审核业务单据 -> APPROVED
⑥ 人工点击"生成凭证" -> Voucher DRAFT
```
但仅销项实现了此流程，进项未实现。

### 1.3 与销项的差异
进项与销项状态机逻辑对称，但科目方向相反：

| 维度 | 销项(OUT) | 进项(IN) |
|------|----------|---------|
| 客商 | Customer | Vendor |
| BusinessDoc 类型 | INVOICE_OUT | INVOICE_IN |
| 借方科目 | 1122 应收账款 | 5001/1601(存货/费用) + 2221.01 进项税 |
| 贷方科目 | 5001 主营收入 + 2221.01 销项税 | 2202 应付账款 |
| 核销方向 | 收款核销应收 | 付款核销应付 |

---

## 2. 范围

### 2.1 本SPEC覆盖
- [ ] t_input_invoice 新增 status 列（Migration）
- [ ] InputInvoiceEntity 新增 status 字段
- [ ] InputInvoiceStateMachineService 接口 + 实现
- [ ] TaxController 新增进项发票状态机 API
- [ ] 进项发票审核通过后自动创建 BusinessDoc(INVOICE_IN) + Voucher
- [ ] 前端 InputInvoiceList.vue 增加审核操作按钮
- [ ] 单元测试

### 2.2 不覆盖（超出范围）
- 认证状态(certification_status)与审核状态(status)的联动逻辑（认证是税务概念，审核是业务概念，两者独立）
- 进项发票红冲（后续SPEC）
- AI 科目映射推荐（已有实现，不在本SPEC范围）

---

## 3. 详细设计

### 3.1 数据库 Migration

```sql
-- V80__add_status_to_input_invoice.sql
ALTER TABLE t_input_invoice ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'PENDING_CONFIRM';
COMMENT ON COLUMN t_input_invoice.status IS '审核状态: PENDING_CONFIRM/PENDING_REVIEW/CONFIRMED/VOUCHERED/FULLY_RECONCILED/PARTIALLY_RECONCILED/VOIDED/REVERSED';

-- 补充审计字段
ALTER TABLE t_input_invoice ADD COLUMN IF NOT EXISTS audited_by BIGINT;
ALTER TABLE t_input_invoice ADD COLUMN IF NOT EXISTS audited_at TIMESTAMP;
ALTER TABLE t_input_invoice ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(500);

-- CHECK 约束（与销项一致）
ALTER TABLE t_input_invoice ADD CONSTRAINT chk_input_invoice_status
    CHECK (status IN ('PENDING_CONFIRM','PENDING_REVIEW','CONFIRMED','VOUCHERED','FULLY_RECONCILED','PARTIALLY_RECONCILED','VOIDED','REVERSED'));

-- 历史数据迁移：已认证的进项发票标记为 CONFIRMED
UPDATE t_input_invoice SET status = 'CONFIRMED'
    WHERE status IS NULL AND certification_status = 'CERTIFIED';
UPDATE t_input_invoice SET status = 'CONFIRMED'
    WHERE status IS NULL AND doc_id IS NOT NULL;
UPDATE t_input_invoice SET status = 'PENDING_CONFIRM'
    WHERE status IS NULL;

CREATE INDEX IF NOT EXISTS idx_input_invoice_status ON t_input_invoice(status);
```

### 3.2 状态机定义

复用 `InvoiceStatus` 常量类（已有8态），与销项共用同一套状态值。

```
PENDING_CONFIRM ──submitReview──> PENDING_REVIEW ──confirm──> CONFIRMED ──markVouchered──> VOUCHERED
      ↕                              ↕                           ↕                          ↕
   void(->VOIDED)            reject(->PENDING_CONFIRM)     revert(->PENDING_REVIEW)    reconcile(->FULLY/PARTIALLY)
                                                                                       ↕
                                                                                    reverse(->REVERSED)
```

### 3.3 InputInvoiceStateMachineService

```java
public interface InputInvoiceStateMachineService {
    /** 提交审核 (PENDING_CONFIRM -> PENDING_REVIEW) */
    void submitForReview(Long invoiceId, Long userId);

    /** 审核通过 (PENDING_REVIEW -> CONFIRMED -> 自动创建 INVOICE_IN 业务单据 + 凭证) */
    void confirm(Long invoiceId, Long userId);

    /** 审核驳回 (PENDING_REVIEW -> PENDING_CONFIRM) */
    void reject(Long invoiceId, Long userId, String reason);

    /** 回退到待审核 (CONFIRMED -> PENDING_REVIEW) */
    void revertToReview(Long invoiceId, Long userId);

    /** 标记已生成凭证 (CONFIRMED -> VOUCHERED) */
    void markVouchered(Long invoiceId, Long voucherId, String voucherNo, Long userId);

    /** 核销扣减后更新状态 (VOUCHERED -> FULLY/PARTIALLY_RECONCILED) */
    void onReconciliationUpdate(Long invoiceId, BigDecimal unsettledAmount, Long userId);

    /** 作废 (任意非终态 -> VOIDED) */
    void voidInvoice(Long invoiceId, Long userId, String reason);
}
```

### 3.4 confirm() 流程（核心）

进项发票审核通过后，对称于销项的 confirm()：

```
1. 状态 PENDING_REVIEW -> CONFIRMED
2. 创建 BusinessDoc(INVOICE_IN, DRAFT)
   - docNo: FPR + period + 序号
   - amount: invoice.totalAmount
   - vendorId: invoice.vendorId
   - invoiceNo: invoice.invoiceNo
   - settledAmount: 0
   - unsettledAmount: invoice.totalAmount
3. 回写发票: invoice.docId / invoice.docNo
4. 创建 Voucher(DRAFT)
   - 借: 5001/1601(存货或费用) = amount(不含税)
   - 借: 2221.01(进项税) = taxAmount
   - 贷: 2202(应付账款) = totalAmount(价税合计)
5. 回写: invoice.voucherId / invoice.voucherNo
6. 回写: BusinessDoc.voucherId / voucherNo
7. 状态 -> VOUCHERED（自动跳过 markVouchered，与销项 confirm 一致）
```

### 3.5 API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| /api/v1/tax/input-invoices/{id}/submit-review | POST | 提交审核 |
| /api/v1/tax/input-invoices/{id}/confirm | POST | 审核通过(自动创建单据+凭证) |
| /api/v1/tax/input-invoices/{id}/reject | POST | 审核驳回 |
| /api/v1/tax/input-invoices/{id}/revert | POST | 回退到待审核 |
| /api/v1/tax/input-invoices/{id}/void | POST | 作废 |

### 3.6 前端改动

InputInvoiceList.vue 新增：
- 状态列（显示中文标签+颜色Tag）
- 操作列按状态显示不同按钮（提交审核/通过/驳回/回退/作废）
- 新增发票时默认 status = PENDING_CONFIRM

### 3.7 导入服务调整

InputInvoiceImportService 现有逻辑：导入时一步到位创建 BusinessDoc + Voucher + InputInvoice。

调整：导入时只创建 InputInvoice(status=PENDING_CONFIRM)，不再自动创建 BusinessDoc 和 Voucher。改为审核通过后再创建。

**风险**：这会改变导入行为，已有导入数据的历史兼容需要处理。

---

## 4. 测试计划

### 4.1 单元测试
- InputInvoiceStateMachineServiceImplTest：8个状态转换测试
- TaxServiceImplTest：进项凭证生成测试（科目方向验证）

### 4.2 集成测试
- 进项发票完整链路：创建 -> 提交 -> 审核 -> 验证 BusinessDoc + Voucher -> 核销
- 导入进项发票 -> 验证 status=PENDING_CONFIRM

---

## 5. 实施顺序

1. Migration: V80 添加 status 列 + 历史数据迁移
2. Entity: InputInvoiceEntity 添加 status + auditedBy/auditedAt 字段
3. Service: InputInvoiceStateMachineService 接口 + 实现
4. Controller: 5个新 API 端点
5. 导入服务: 调整为只创建发票不自动创建单据
6. 前端: 增加状态列和操作按钮
7. 测试: 单元测试 + 编译验证

---

## 6. 待确认问题

1. **导入服务是否改为不自动创建单据？** 当前导入一步到位创建 BusinessDoc+Voucher，如果改为审核后创建，现有导入流程会变。建议改，但需要确认。
2. **历史数据如何处理？** 已有导入的进项发票已有 doc_id/voucher_id，status 应初始化为 CONFIRMED 还是 VOUCHERED？建议 VOUCHERED。
3. **凭证科目方向确认**：进项发票凭证应该是 借:存货/费用+进项税 / 贷:应付账款。当前导入服务用的科目是 5001(销售收入)而不是存货科目，这是否正确？5001 是收入科目，进项应该用 1601(原材料)或 6601(销售费用)等。需要确认。

---

## 7. BDD 验收标准

> 规则：BDD 场景数量 = acceptance_tests 数量（SPEC-CONTRACT-SCHEMA §0.1 规则 4）。
> 以下 10 个场景与 AT-01 ~ AT-10 一一对应，每个场景均含负向断言。

### 场景 1 (AT-01)：进项发票审核通过后自动创建业务单据和凭证
**Given** 一张进项发票处于 PENDING_REVIEW 状态，尚未创建 BusinessDoc 和 Voucher
**When** 用户调用 confirm(invoiceId, userId)
**Then** 发票状态变为 VOUCHERED，自动创建 BusinessDoc(INVOICE_IN, DRAFT) 和 Voucher(DRAFT)，凭证科目方向为 借:存货/费用+进项税 / 贷:应付账款
**And** 不应跳过 BusinessDoc 创建步骤而直接进入 VOUCHERED
**And** 若发票状态不是 PENDING_REVIEW，调用 confirm() 应抛出 BusinessException，状态不变

### 场景 2 (AT-02)：进项发票导入时不自动创建单据
**Given** 进项发票成功导入系统
**When** 导入流程完成
**Then** 发票状态为 PENDING_CONFIRM，不自动创建 BusinessDoc 和 Voucher（等待人工审核通过后再创建）
**And** 不应存在 BusinessDoc 或 Voucher 关联到此发票（docId/voucherId 为空）

### 场景 3 (AT-03)：进项发票作废后不可继续流转
**Given** 一张进项发票处于 PENDING_CONFIRM 状态
**When** 用户调用 voidInvoice(invoiceId, userId, reason)
**Then** 发票状态变为 VOIDED
**And** VOIDED 状态下调用 submitForReview() / confirm() / reject() / revertToReview() / markVouchered() 均应抛出 BusinessException，状态保持 VOIDED

### 场景 4 (AT-04)：审核驳回后发票退回待确认状态
**Given** 一张进项发票处于 PENDING_REVIEW 状态
**When** 用户调用 reject(invoiceId, userId, reason)
**Then** 发票状态变为 PENDING_CONFIRM，reject_reason 记录驳回原因
**And** 不应在此步骤生成 BusinessDoc 或 Voucher
**And** 若发票状态不是 PENDING_REVIEW，调用 reject() 应抛出 BusinessException，状态不变

### 场景 5 (AT-05)：已确认发票可回退至待审核
**Given** 一张进项发票处于 CONFIRMED 状态
**When** 用户调用 revertToReview(invoiceId, userId)
**Then** 发票状态变为 PENDING_REVIEW
**And** 不应清除已有的 docId 关联（仅状态回退，业务单据保留）
**And** 若发票状态不是 CONFIRMED，调用 revertToReview() 应抛出 BusinessException，状态不变

### 场景 6 (AT-06)：已凭证发票全额核销后变为已全额核销
**Given** 一张进项发票处于 VOUCHERED 状态，未核销余额 unsettledAmount = totalAmount
**When** 核销系统调用 onReconciliationUpdate(invoiceId, unsettledAmount=0, userId)
**Then** 发票状态变为 FULLY_RECONCILED
**And** 若 unsettledAmount > 0，状态不应变为 FULLY_RECONCILED（应进入 PARTIALLY_RECONCILED）

### 场景 7 (AT-07)：已凭证发票部分核销后变为部分核销
**Given** 一张进项发票处于 VOUCHERED 状态，未核销余额 unsettledAmount = totalAmount
**When** 核销系统调用 onReconciliationUpdate(invoiceId, unsettledAmount>0, userId)
**Then** 发票状态变为 PARTIALLY_RECONCILED
**And** 若 unsettledAmount = 0，状态不应变为 PARTIALLY_RECONCILED（应变为 FULLY_RECONCILED）

### 场景 8 (AT-08)：已凭证发票红冲后变为已红冲
**Given** 一张进项发票处于 VOUCHERED 状态
**When** 用户调用 reverse(invoiceId, userId, reason)
**Then** 发票状态变为 REVERSED（终态）
**And** REVERSED 状态下任何状态转换操作均应抛出 BusinessException
**And** 若发票状态不是 VOUCHERED，调用 reverse() 应抛出 BusinessException，状态不变

### 场景 9 (AT-09)：待确认发票提交审核后进入待审核
**Given** 一张进项发票处于 PENDING_CONFIRM 状态
**When** 用户调用 submitForReview(invoiceId, userId)
**Then** 发票状态变为 PENDING_REVIEW
**And** 不应在提交审核阶段创建 BusinessDoc 或 Voucher（仅状态流转）
**And** 若发票状态不是 PENDING_CONFIRM，调用 submitForReview() 应抛出 BusinessException，状态不变

### 场景 10 (AT-10)：已确认发票手动标记凭证后变为已凭证
**Given** 一张进项发票处于 CONFIRMED 状态（如历史数据迁移）
**When** 用户调用 markVouchered(invoiceId, voucherId, voucherNo, userId)
**Then** 发票状态变为 VOUCHERED，回写 voucherId / voucherNo
**And** 不应在 markVouchered 中创建新的 BusinessDoc（仅回写凭证关联）
**And** 若发票状态不是 CONFIRMED，调用 markVouchered() 应抛出 BusinessException，状态不变

---

# MACHINE-READABLE CONTRACT

```yaml
contract_version: "1.0"
entity: InputInvoice
description: "进项发票审核状态机 — 8态流转，复用 InvoiceStatus 常量类，与销项共用同一套状态值"

states:
  - name: PENDING_CONFIRM
    label: "待确认"
    initial: true
    terminal: false
    description: "初始状态。发票导入或手动创建后进入此状态，尚未提交审核。"

  - name: PENDING_REVIEW
    label: "待审核"
    initial: false
    terminal: false
    description: "已提交审核，等待人工审核通过或驳回。"

  - name: CONFIRMED
    label: "已确认"
    initial: false
    terminal: false
    description: "审核通过，可手动标记已生成凭证，或通过 confirm() 自动跳过此状态直达 VOUCHERED。历史数据迁移中已认证的发票也会标记为此状态。"

  - name: VOUCHERED
    label: "已凭证"
    initial: false
    terminal: false
    description: "已生成凭证，可进行核销扣减或红冲反冲。"

  - name: FULLY_RECONCILED
    label: "已全额核销"
    initial: false
    terminal: false
    description: "全额核销完成，应付账款已结清。"

  - name: PARTIALLY_RECONCILED
    label: "部分核销"
    initial: false
    terminal: false
    description: "部分核销完成，仍有未核销余额。"

  - name: VOIDED
    label: "已作废"
    initial: false
    terminal: true
    description: "终态。发票作废，不可进行任何后续流转。"

  - name: REVERSED
    label: "已红冲"
    initial: false
    terminal: true
    description: "终态。发票已红冲反冲。"

transitions:
  - id: submit_review
    from: PENDING_CONFIRM
    to: PENDING_REVIEW
    trigger: submitForReview(invoiceId, userId)
    description: "提交审核。用户手动触发，将待确认发票提交至审核队列。"
    api: "POST /api/v1/tax/input-invoices/{id}/submit-review"

  - id: confirm
    from: PENDING_REVIEW
    to: VOUCHERED
    trigger: confirm(invoiceId, userId)
    description: "审核通过并自动创建业务单据和凭证。状态 PENDING_REVIEW → CONFIRMED → VOUCHERED（原子操作，自动跳过 markVouchered）。同时创建 BusinessDoc(INVOICE_IN, DRAFT) 和 Voucher(DRAFT)，凭证科目：借:存货/费用+进项税 / 贷:应付账款。"
    auto_create: [BusinessDoc, Voucher]
    api: "POST /api/v1/tax/input-invoices/{id}/confirm"

  - id: reject
    from: PENDING_REVIEW
    to: PENDING_CONFIRM
    trigger: reject(invoiceId, userId, reason)
    description: "审核驳回。审核不通过，发票退回至待确认状态，需重新提交。"
    api: "POST /api/v1/tax/input-invoices/{id}/reject"

  - id: revert_to_review
    from: CONFIRMED
    to: PENDING_REVIEW
    trigger: revertToReview(invoiceId, userId)
    description: "回退到待审核。从已确认状态回退至待审核，允许重新审核。"
    api: "POST /api/v1/tax/input-invoices/{id}/revert"

  - id: mark_vouchered
    from: CONFIRMED
    to: VOUCHERED
    trigger: markVouchered(invoiceId, voucherId, voucherNo, userId)
    description: "手动标记已生成凭证。用于已确认但未自动创建凭证的发票（如历史数据）。"

  - id: reconcile_full
    from: VOUCHERED
    to: FULLY_RECONCILED
    trigger: onReconciliationUpdate(invoiceId, unsettledAmount=0, userId)
    description: "全额核销。当 unsettledAmount 降至 0 时，状态变为已全额核销。"

  - id: reconcile_partial
    from: VOUCHERED
    to: PARTIALLY_RECONCILED
    trigger: onReconciliationUpdate(invoiceId, unsettledAmount>0, userId)
    description: "部分核销。当 unsettledAmount 大于 0 时，状态变为部分核销。"

  - id: void_pending_confirm
    from: PENDING_CONFIRM
    to: VOIDED
    trigger: voidInvoice(invoiceId, userId, reason)
    description: "作废待确认发票。"
    api: "POST /api/v1/tax/input-invoices/{id}/void"

  - id: void_pending_review
    from: PENDING_REVIEW
    to: VOIDED
    trigger: voidInvoice(invoiceId, userId, reason)
    description: "作废待审核发票。"
    api: "POST /api/v1/tax/input-invoices/{id}/void"

  - id: void_confirmed
    from: CONFIRMED
    to: VOIDED
    trigger: voidInvoice(invoiceId, userId, reason)
    description: "作废已确认发票。"
    api: "POST /api/v1/tax/input-invoices/{id}/void"

  - id: reverse
    from: VOUCHERED
    to: REVERSED
    trigger: reverse(invoiceId, userId, reason)
    description: "红冲反冲。从已凭证状态直接红冲至终态。"

acceptance_tests:
  - id: AT-01
    scenario: "进项发票审核通过后自动创建业务单据和凭证"
    given: "一张进项发票处于 PENDING_REVIEW 状态，尚未创建 BusinessDoc 和 Voucher"
    when: "用户调用 confirm(invoiceId, userId)"
    then: "发票状态变为 VOUCHERED，自动创建 BusinessDoc(INVOICE_IN, DRAFT) 和 Voucher(DRAFT)，凭证科目方向为 借:存货/费用+进项税 / 贷:应付账款"
    negative_assertion: "若发票状态不是 PENDING_REVIEW，调用 confirm() 应抛出 BusinessException"

  - id: AT-02
    scenario: "进项发票导入时不自动创建单据"
    given: "进项发票成功导入系统"
    when: "导入流程完成"
    then: "发票状态为 PENDING_CONFIRM，不自动创建 BusinessDoc 和 Voucher（等待人工审核通过后再创建）"
    negative_assertion: "导入后不应存在 BusinessDoc 或 Voucher 关联到此发票"

  - id: AT-03
    scenario: "进项发票作废后不可继续流转"
    given: "一张进项发票处于 PENDING_CONFIRM 状态"
    when: "用户调用 voidInvoice(invoiceId, userId, reason)"
    then: "发票状态变为 VOIDED"
    negative_assertion: "VOIDED 状态下调用 submitReview() / confirm() / reject() / revertToReview() / markVouchered() 均抛出 BusinessException"

  - id: AT-04
    scenario: "审核驳回后发票退回待确认状态"
    given: "一张进项发票处于 PENDING_REVIEW 状态"
    when: "用户调用 reject(invoiceId, userId, reason)"
    then: "发票状态变为 PENDING_CONFIRM，reject_reason 记录驳回原因"
    negative_assertion: "若发票状态不是 PENDING_REVIEW，调用 reject() 应抛出 BusinessException"

  - id: AT-05
    scenario: "已确认发票可回退至待审核"
    given: "一张进项发票处于 CONFIRMED 状态"
    when: "用户调用 revertToReview(invoiceId, userId)"
    then: "发票状态变为 PENDING_REVIEW"
    negative_assertion: "若发票状态不是 CONFIRMED，调用 revertToReview() 应抛出 BusinessException"

  - id: AT-06
    scenario: "已凭证发票全额核销后变为已全额核销"
    given: "一张进项发票处于 VOUCHERED 状态，未核销余额 unsettledAmount = totalAmount"
    when: "核销系统调用 onReconciliationUpdate(invoiceId, unsettledAmount=0, userId)"
    then: "发票状态变为 FULLY_RECONCILED"
    negative_assertion: "若 unsettledAmount > 0，状态不应变为 FULLY_RECONCILED"

  - id: AT-07
    scenario: "已凭证发票部分核销后变为部分核销"
    given: "一张进项发票处于 VOUCHERED 状态，未核销余额 unsettledAmount = totalAmount"
    when: "核销系统调用 onReconciliationUpdate(invoiceId, unsettledAmount>0, userId)"
    then: "发票状态变为 PARTIALLY_RECONCILED"
    negative_assertion: "若 unsettledAmount = 0，状态不应变为 PARTIALLY_RECONCILED（应变为 FULLY_RECONCILED）"

  - id: AT-08
    scenario: "已凭证发票红冲后变为已红冲"
    given: "一张进项发票处于 VOUCHERED 状态"
    when: "用户调用 reverse(invoiceId, userId, reason)"
    then: "发票状态变为 REVERSED"
    negative_assertion: "若发票状态不是 VOUCHERED，调用 reverse() 应抛出 BusinessException"

  - id: AT-09
    scenario: "待确认发票提交审核后进入待审核"
    given: "一张进项发票处于 PENDING_CONFIRM 状态"
    when: "用户调用 submitForReview(invoiceId, userId)"
    then: "发票状态变为 PENDING_REVIEW"
    negative_assertion: "若发票状态不是 PENDING_CONFIRM，调用 submitForReview() 应抛出 BusinessException"

  - id: AT-10
    scenario: "已确认发票手动标记凭证后变为已凭证"
    given: "一张进项发票处于 CONFIRMED 状态（如历史数据迁移）"
    when: "用户调用 markVouchered(invoiceId, voucherId, voucherNo, userId)"
    then: "发票状态变为 VOUCHERED，回写 voucherId / voucherNo"
    negative_assertion: "若发票状态不是 CONFIRMED，调用 markVouchered() 应抛出 BusinessException"
```
