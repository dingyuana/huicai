# P40 - 进项发票状态机与下游流程衔接

> **版本**：V1.0 | **日期**：2026-07-17 | **作者**：Hermes
> **状态**：**✅ 已实现**

---

## 1. 问题背景

### 1.1 现状
进项发票（t_input_invoice）当前存在三大缺陷：

1. **无 status 字段**：数据库没有 status 列，Entity 没有 status 属性。仅有 `certification_status`（认证状态），与审核状态机是两个不同维度。
2. **无状态机**：销项发票有完整的 `OutputInvoiceStateMachineService`（8态状态机 + 7个API），进项完全没有对应实现。
3. **无下游衔接**：手动创建的进项发票 `certify()` 只改认证状态，不创建 BusinessDoc、不生成凭证、不关联应付。导入路径虽一步到位创建 BusinessDoc+Voucher，但绕过了审核流程。

### 1.2 设计文档已有的定义
`docs/design/06-tax-design.md` §4 定义了进项应走6步流程：
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
