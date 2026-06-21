# P21-b SPEC — 采购发票状态机实现规格书

> 状态：待实现 | 优先级：高（P21-b）
> 依据：`docs/需求分析书_发票与凭证状态机_V1.0.md` §4.2 采购发票 + §3.1 发票状态机
> 目标：为 `InputInvoiceEntity`（采购发票）建立完整 7 状态机
> 工期：单批交付，3 个 commit
> 拆分说明：与 P21-a（销售发票）对称实现，复用 `InvoiceStatus` 常量类
> Migration 编号：本 SPEC 使用 V40（P21-a 已占 V38；P21-a 之后若有 V39 留给其他工单）

---

## 0. 改动清单总览

| # | 改动 | 文件 | 风险 |
|---|------|------|------|
| 1 | 复用 `InvoiceStatus` 常量类 | （P21-a 已建）| ✅ 低 |
| 2 | `InputInvoiceEntity` 注释更新（同 P21-a §2.1）| Entity 文件 | ✅ 低 |
| 3 | **V40** 迁移: `t_input_invoice.status` 加 CHECK 约束 + 索引 | Flyway | 🟡 中 |
| 4 | 创建 `InputInvoiceStateMachineService` | Service 文件 | 🟡 中 |
| 5 | `InputInvoiceServiceImpl` 适配新状态机 | Service 文件 | 🟡 中 |
| 6 | `InputInvoiceImportService` / `AutoGenerationService` 调用方适配 | 调用方 | 🟡 中 |
| 7 | 单测覆盖（≥8 @Test，对称 P21-a）| Test 文件 | ✅ 低 |

> **复用声明**：P21-a 创建的 `com.huicai.module.tax.constant.InvoiceStatus` 本 SPEC **直接复用**，不重建。`OutputInvoiceStateMachineService` 与本 SPEC 的 `InputInvoiceStateMachineService` 接口对称，但分别实现（避免跨销售/采购耦合）。

---

## 1. 枚举常量

### 1.1 复用 `InvoiceStatus`

**路径**: `com.huicai.module.tax.constant.InvoiceStatus`（P21-a 已建）

本 SPEC **不创建新枚举**，直接复用 P21-a 的 7 状态常量：
- `PENDING_CONFIRM` / `PENDING_REVIEW` / `CONFIRMED`
- `VOUCHERED` / `FULLY_RECONCILED` / `PARTIALLY_RECONCILED`
- `VOIDED`

---

## 2. 实体变更

### 2.1 `InputInvoiceEntity` 注释更新

**现状**：`status` 字段是 String，无注释。

**改动**：与 P21-a §2.1 完全对称。

```java
/**
 * 状态: PENDING_CONFIRM / PENDING_REVIEW / CONFIRMED / VOUCHERED /
 *       FULLY_RECONCILED / PARTIALLY_RECONCILED / VOIDED
 * 详见 com.huicai.module.tax.constant.InvoiceStatus
 */
private String status;
```

---

## 3. Flyway 迁移（V40）

```sql
-- V40__add_input_invoice_status_constraint.sql

-- 1. t_input_invoice.status 加 CHECK 约束
ALTER TABLE t_input_invoice
    DROP CONSTRAINT IF EXISTS t_input_invoice_status_check;
ALTER TABLE t_input_invoice
    ADD CONSTRAINT t_input_invoice_status_check
    CHECK (status IN (
        'PENDING_CONFIRM', 'PENDING_REVIEW', 'CONFIRMED',
        'VOUCHERED', 'FULLY_RECONCILED', 'PARTIALLY_RECONCILED', 'VOIDED'
    ));

-- 2. status 字段索引
CREATE INDEX IF NOT EXISTS idx_t_input_invoice_status
    ON t_input_invoice(status);

-- 3. 已有数据校验
DO $$
DECLARE
    invalid_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_count
    FROM t_input_invoice
    WHERE status IS NOT NULL
      AND status NOT IN (
        'PENDING_CONFIRM', 'PENDING_REVIEW', 'CONFIRMED',
        'VOUCHERED', 'FULLY_RECONCILED', 'PARTIALLY_RECONCILED', 'VOIDED'
      );
    IF invalid_count > 0 THEN
        RAISE EXCEPTION 't_input_invoice.status 存在 % 条非法值，迁移前请人工修正', invalid_count;
    END IF;
END $$;

COMMENT ON COLUMN t_input_invoice.status IS
    '状态: PENDING_CONFIRM/PENDING_REVIEW/CONFIRMED/VOUCHERED/FULLY_RECONCILED/PARTIALLY_RECONCILED/VOIDED';
```

---

## 4. Service 状态机实现

### 4.1 与 P21-a 对称

**接口** `InputInvoiceStateMachineService.java`：

```java
package com.huicai.module.tax.service;

/**
 * 采购发票状态机服务.
 * 与 OutputInvoiceStateMachineService 对称，详见 P21-a SPEC.
 */
public interface InputInvoiceStateMachineService {

    void submitForReview(Long invoiceId, Long userId);
    void confirm(Long invoiceId, Long userId);
    void reject(Long invoiceId, Long userId, String reason);
    void revertToReview(Long invoiceId, Long userId);
    void markVouchered(Long invoiceId, Long voucherId, Long userId);
    void onReconciliationUpdate(Long invoiceId, BigDecimal unsettledAmount, Long userId);
    void voidInvoice(Long invoiceId, Long userId, String reason);
}
```

**实现** `InputInvoiceStateMachineServiceImpl.java`：与 P21-a 的 `OutputInvoiceStateMachineServiceImpl` 完全对称，仅 Entity 类型替换为 `InputInvoiceEntity`，Mapper 替换为 `InputInvoiceMapper`。此处不重复代码。

---

## 5. 采购发票业务差异

### 5.1 采购发票可能跳过 PENDING_REVIEW

依据需求文档 §207：
> 采购发票可不经过 `PENDING_REVIEW`，由供应商匹配度决定

**实现差异**：

```java
// InputInvoiceImportService 导入后置状态逻辑
InputInvoiceEntity invoice = new InputInvoiceEntity();
// ...
if (supplierMatchedFully && productMatchedFully) {
    // 供应商 + 商品完全匹配 → 直接 CONFIRMED（跳过待审核）
    invoice.setStatus(InvoiceStatus.CONFIRMED);
} else {
    // 匹配失败 → 待人工确认
    invoice.setStatus(InvoiceStatus.PENDING_CONFIRM);
}
invoiceMapper.insert(invoice);
```

> **设计权衡**：跳过 PENDING_REVIEW 是"供应商匹配度足够高"的优化路径，但合规上需要保留审计日志（由 P24 处理）。本 SPEC 不实现该逻辑的强制审计，由调用方按需记录。

### 5.2 现金折扣处理

采购发票核销时可能涉及现金折扣（详见需求文档 §4.2）：

```java
// onReconciliationUpdate 扩展（与 P21-a 差异点）
@Override
@Transactional
public void onReconciliationUpdate(Long invoiceId,
        BigDecimal unsettledAmount, Long userId, BigDecimal cashDiscount) {
    InputInvoiceEntity entity = invoiceMapper.selectById(invoiceId);
    if (!InvoiceStatus.isVouchered(entity.getStatus())) {
        throw new BusinessException("仅已生成凭证的发票可核销");
    }

    // 现金折扣独立凭证（不冲减发票金额）
    if (cashDiscount != null && cashDiscount.compareTo(BigDecimal.ZERO) != 0) {
        // 调用 VoucherService 生成折扣凭证
        // 借：应付账款—供应商  贷：财务费用—现金折扣
        voucherService.generateCashDiscountVoucher(
            invoiceId, entity.getVendorId(), cashDiscount, userId);
    }

    // 更新发票状态
    String newStatus = unsettledAmount.compareTo(BigDecimal.ZERO) == 0
        ? InvoiceStatus.FULLY_RECONCILED
        : InvoiceStatus.PARTIALLY_RECONCILED;
    entity.setStatus(newStatus);
    entity.setUpdatedBy(userId);
    invoiceMapper.updateById(entity);
}
```

### 5.3 调用方适配

| 位置 | 现用字符串 | 替换为 |
|------|-----------|--------|
| `InputInvoiceImportService.insert()` | `status=null` 或 magic string | `InvoiceStatus.PENDING_CONFIRM` / `CONFIRMED`（按匹配度）|
| `AutoGenerationService.createPayableFromBankDoc()` | `status` 未设置 | `InvoiceStatus.PENDING_REVIEW`（流水生成的发票已匹配）|
| `InputInvoiceServiceImpl` 各处 | 散落 magic string | `InvoiceStatus.*` 常量 |

---

## 6. 与 P20/P21-a/P22/P24 的边界

| 边界 | 本 SPEC 范围 | 其他 SPEC 范围 |
|:---|:---|:---|
| **采购发票**状态 | ✅ 本 SPEC 定义 | — |
| 销售发票状态 | ❌ 不涉及 | **P21-a** 定义 |
| 应付单(PayableEntity)状态 | ❌ 不涉及 | **P20** 定义 |
| 凭证(VoucherEntity)状态 | ❌ 不涉及 | **P22** 定义 |
| 状态变更的审计 | ❌ 本 SPEC 仅 log.info | **P24** AOP |
| 现金折扣凭证生成 | ⚠️ 调用 VoucherService | **P22** 提供 generateCashDiscountVoucher 接口 |

---

## 7. 测试要点

| 测试场景 | 期望 | 与 P21-a 差异 |
|---------|------|--------------|
| 导入后默认 PENDING_CONFIRM（匹配失败）| status=PENDING_CONFIRM | 相同 |
| 导入后直接 CONFIRMED（匹配成功）| status=CONFIRMED | **差异**：跳过 PENDING_REVIEW |
| PENDING_CONFIRM → PENDING_REVIEW | 成功 | 相同 |
| PENDING_REVIEW → CONFIRMED | 成功 | 相同 |
| CONFIRMED → VOUCHERED | 成功 | 相同 |
| 核销扣减 unsettled=0 → FULLY_RECONCILED | 成功 | 相同 |
| 核销扣减 unsettled>0 → PARTIALLY_RECONCILED | 成功 | 相同 |
| 现金折扣生成独立凭证 | 折扣凭证生成，发票金额不变 | **差异** |
| 作废含原因 | 成功 | 相同 |

---

## 8. API 变更

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/input-invoices/{id}/submit-review` | 提交审核 |
| POST | `/api/v1/input-invoices/{id}/confirm` | 审核通过 |
| POST | `/api/v1/input-invoices/{id}/reject` | 审核驳回 |
| POST | `/api/v1/input-invoices/{id}/revert` | 回退到待审核 |
| POST | `/api/v1/input-invoices/{id}/void` | 作废 |
| GET | `/api/v1/input-invoices?status=VOUCHERED` | 按状态过滤（V39 索引）|

**前端**：在采购发票列表页加状态筛选器（与销售发票对称）。

---

## 9. 不做事项

- ❌ 不重建 `InvoiceStatus` 常量类（复用 P21-a）
- ❌ 不修改 OutputInvoiceEntity（P21-a 范围）
- ❌ 不修改 VoucherEntity（P22 范围）
- ❌ 不实现三单匹配（PO-GRN-Invoice）的强制校验（不在本期）
- ❌ 不实现多级审批流（采购大额审批由财务主管手动复核，不在本 SPEC）

---

## 10. 后续依赖

- **依赖 P21-a**：`InvoiceStatus` 常量类
- **依赖 P22**：现金折扣凭证生成接口 `voucherService.generateCashDiscountVoucher()`
- **依赖 P24**：上线时把 log.info 替换为 audit_log 自动写入