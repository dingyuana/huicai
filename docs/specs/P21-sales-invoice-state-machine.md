# P21-a SPEC — 销售发票状态机实现规格书

> 状态：待实现 | 优先级：高（P21-a）
> 依据：`docs/需求分析书_发票与凭证状态机_V1.0.md` §3.1 发票状态机
> 目标：为 `OutputInvoiceEntity`（销售发票）建立完整 7 状态机，消除 magic string
> 工期：单批交付，3 个 commit
> 拆分说明：原 P21 拆分为 P21-a（销售发票，本文件）+ P21-b（采购发票，InputInvoiceEntity 对称实现）

---

## 0. 改动清单总览

| # | 改动 | 文件 | 风险 |
|---|------|------|------|
| 1 | 创建 `InvoiceStatus` 枚举常量类 | `backend/.../tax/constant/InvoiceStatus.java` | ✅ 低 |
| 2 | `OutputInvoiceEntity` 新增 status 字段约束（已存在 String 字段，补注释 + 常量）| Entity 文件 | ✅ 低 |
| 3 | V38 迁移: `t_output_invoice.status` 加 CHECK 约束 + 索引 | Flyway | 🟡 中 |
| 4 | 创建 `OutputInvoiceStateMachineService`（状态机 Service）| Service 文件 | 🟡 中 |
| 5 | `OutputInvoiceServiceImpl` 适配新状态机（替换 magic string）| Service 文件 | 🟡 中 |
| 6 | `SalesInvoiceImportService` / `AutoGenerationService` 调用方适配 | 调用方 | 🟡 中 |
| 7 | 单测覆盖（≥8 @Test）| Test 文件 | ✅ 低 |

---

## 1. 枚举常量

### 1.1 `InvoiceStatus` 常量类

**路径**: `com.huicai.module.tax.constant.InvoiceStatus`

```java
package com.huicai.module.tax.constant;

/**
 * 发票模块统一状态常量.
 * 使用 String 常量而非 Enum，保持与数据库 VARCHAR 兼容.
 *
 * 状态机详见 docs/需求分析书_发票与凭证状态机_V1.0.md §3.1
 * 与 P20 (ArapStatus) 的边界详见 P20 SPEC §10.
 */
public final class InvoiceStatus {

    private InvoiceStatus() {}

    // ====== 导入与审核 ======
    public static final String PENDING_CONFIRM = "PENDING_CONFIRM";
    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String CONFIRMED = "CONFIRMED";

    // ====== 凭证与核销 ======
    public static final String VOUCHERED = "VOUCHERED";
    public static final String FULLY_RECONCILED = "FULLY_RECONCILED";
    public static final String PARTIALLY_RECONCILED = "PARTIALLY_RECONCILED";

    // ====== 终止 ======
    public static final String VOIDED = "VOIDED";

    // ====== 检查方法 ======
    public static boolean isPendingConfirm(String status) {
        return PENDING_CONFIRM.equals(status);
    }
    public static boolean isConfirmed(String status) {
        return CONFIRMED.equals(status);
    }
    public static boolean isVoucherable(String status) {
        return CONFIRMED.equals(status);
    }
    public static boolean isVouchered(String status) {
        return VOUCHERED.equals(status);
    }
    public static boolean isReconciled(String status) {
        return FULLY_RECONCILED.equals(status) || PARTIALLY_RECONCILED.equals(status);
    }
    public static boolean isVoidable(String status) {
        // 任何非终态都可作废
        return !VOIDED.equals(status) && !FULLY_RECONCILED.equals(status);
    }
    public static boolean isModifiable(String status) {
        // 仅 PENDING_CONFIRM / PENDING_REVIEW 可修改
        return PENDING_CONFIRM.equals(status) || PENDING_REVIEW.equals(status);
    }
}
```

---

## 2. 实体变更

### 2.1 `OutputInvoiceEntity` 注释更新

**现状**：`status` 字段是 String，无注释和约束。

**改动**：

```java
/**
 * 状态: PENDING_CONFIRM / PENDING_REVIEW / CONFIRMED / VOUCHERED /
 *       FULLY_RECONCILED / PARTIALLY_RECONCILED / VOIDED
 * 详见 com.huicai.module.tax.constant.InvoiceStatus
 */
private String status;
```

---

## 3. Flyway 迁移（V38）

```sql
-- V38__add_invoice_status_constraint.sql

-- 1. t_output_invoice.status 加 CHECK 约束
ALTER TABLE t_output_invoice
    DROP CONSTRAINT IF EXISTS t_output_invoice_status_check;
ALTER TABLE t_output_invoice
    ADD CONSTRAINT t_output_invoice_status_check
    CHECK (status IN (
        'PENDING_CONFIRM', 'PENDING_REVIEW', 'CONFIRMED',
        'VOUCHERED', 'FULLY_RECONCILED', 'PARTIALLY_RECONCILED', 'VOIDED'
    ));

-- 2. status 字段索引（前端按状态过滤）
CREATE INDEX IF NOT EXISTS idx_t_output_invoice_status
    ON t_output_invoice(status);

-- 3. 已有数据校验（确保没有非法状态）
DO $$
DECLARE
    invalid_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_count
    FROM t_output_invoice
    WHERE status IS NOT NULL
      AND status NOT IN (
        'PENDING_CONFIRM', 'PENDING_REVIEW', 'CONFIRMED',
        'VOUCHERED', 'FULLY_RECONCILED', 'PARTIALLY_RECONCILED', 'VOIDED'
    );
    IF invalid_count > 0 THEN
        RAISE EXCEPTION 't_output_invoice.status 存在 % 条非法值，迁移前请人工修正', invalid_count;
    END IF;
END $$;

COMMENT ON COLUMN t_output_invoice.status IS
    '状态: PENDING_CONFIRM/PENDING_REVIEW/CONFIRMED/VOUCHERED/FULLY_RECONCILED/PARTIALLY_RECONCILED/VOIDED';
```

**P21-b 采购发票迁移（V39）见独立 SPEC，本文件不重复**。

---

## 4. Service 状态机实现

### 4.1 `OutputInvoiceStateMachineService` 新增 Service

**接口** `OutputInvoiceStateMachineService.java`：

```java
package com.huicai.module.tax.service;

/**
 * 销售发票状态机服务.
 * 详见 docs/需求分析书_发票与凭证状态机_V1.0.md §3.1.
 */
public interface OutputInvoiceStateMachineService {

    /** 提交审核 (PENDING_CONFIRM → PENDING_REVIEW) */
    void submitForReview(Long invoiceId, Long userId);

    /** 审核通过 (PENDING_REVIEW → CONFIRMED) */
    void confirm(Long invoiceId, Long userId);

    /** 审核驳回 (PENDING_REVIEW → PENDING_CONFIRM, 记录驳回原因) */
    void reject(Long invoiceId, Long userId, String reason);

    /** 回退到待审核 (CONFIRMED → PENDING_REVIEW, 选错结算状态) */
    void revertToReview(Long invoiceId, Long userId);

    /** 标记已生成凭证 (CONFIRMED → VOUCHERED, 记录 voucherId) */
    void markVouchered(Long invoiceId, Long voucherId, Long userId);

    /** 核销扣减后更新状态 (VOUCHERED → FULLY_RECONCILED / PARTIALLY_RECONCILED) */
    void onReconciliationUpdate(Long invoiceId, BigDecimal unsettledAmount, Long userId);

    /** 作废 (任意非终态 → VOIDED, 记录作废原因) */
    void voidInvoice(Long invoiceId, Long userId, String reason);
}
```

**实现** `OutputInvoiceStateMachineServiceImpl.java`（关键骨架）：

```java
package com.huicai.module.tax.service.impl;

import com.huicai.module.tax.constant.InvoiceStatus;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutputInvoiceStateMachineServiceImpl
        implements OutputInvoiceStateMachineService {

    private final OutputInvoiceMapper invoiceMapper;

    @Override
    @Transactional
    public void confirm(Long invoiceId, Long userId) {
        OutputInvoiceEntity entity = invoiceMapper.selectById(invoiceId);
        if (entity == null) {
            throw new BusinessException("发票不存在: id=" + invoiceId);
        }
        if (!InvoiceStatus.PENDING_REVIEW.equals(entity.getStatus())) {
            throw new BusinessException(
                "仅待审核状态可确认，当前: " + entity.getStatus());
        }
        entity.setStatus(InvoiceStatus.CONFIRMED);
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("销售发票确认: id={}, userId={}", invoiceId, userId);
    }

    @Override
    @Transactional
    public void markVouchered(Long invoiceId, Long voucherId, Long userId) {
        OutputInvoiceEntity entity = invoiceMapper.selectById(invoiceId);
        if (!InvoiceStatus.isVoucherable(entity.getStatus())) {
            throw new BusinessException(
                "仅已确认状态可生成凭证，当前: " + entity.getStatus());
        }
        entity.setStatus(InvoiceStatus.VOUCHERED);
        entity.setVoucherId(voucherId);
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("销售发票已生成凭证: invoiceId={}, voucherId={}",
            invoiceId, voucherId);
    }

    @Override
    @Transactional
    public void onReconciliationUpdate(Long invoiceId,
            BigDecimal unsettledAmount, Long userId) {
        OutputInvoiceEntity entity = invoiceMapper.selectById(invoiceId);
        if (!InvoiceStatus.isVouchered(entity.getStatus())) {
            // 未生成凭证的发票不可能进入核销流程
            throw new BusinessException("仅已生成凭证的发票可核销");
        }
        String newStatus = unsettledAmount.compareTo(BigDecimal.ZERO) == 0
            ? InvoiceStatus.FULLY_RECONCILED
            : InvoiceStatus.PARTIALLY_RECONCILED;
        entity.setStatus(newStatus);
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("销售发票核销更新: id={}, status={}", invoiceId, newStatus);
    }

    @Override
    @Transactional
    public void voidInvoice(Long invoiceId, Long userId, String reason) {
        OutputInvoiceEntity entity = invoiceMapper.selectById(invoiceId);
        if (!InvoiceStatus.isVoidable(entity.getStatus())) {
            throw new BusinessException("当前状态不可作废: " + entity.getStatus());
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("作废必须填写原因");
        }
        entity.setStatus(InvoiceStatus.VOIDED);
        entity.setRemark(appendReason(entity.getRemark(), reason, userId));
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("销售发票作废: id={}, userId={}, reason={}",
            invoiceId, userId, reason);
    }

    // submitForReview / reject / revertToReview 实现类似，省略
}
```

---

## 5. 调用方适配

### 5.1 `SalesInvoiceImportService` 导入后置状态

```java
// 现状：导入后 status=null 或 magic string
OutputInvoiceEntity invoice = new OutputInvoiceEntity();
// ...
invoiceMapper.insert(invoice);

// 改为：导入成功后置 PENDING_CONFIRM
invoice.setStatus(InvoiceStatus.PENDING_CONFIRM);
invoiceMapper.insert(invoice);
```

### 5.2 `AutoGenerationService` 银行流水生成发票

```java
// 现状：status 字段未设置
OutputInvoiceEntity invoice = new OutputInvoiceEntity();
// ...

// 改为：流水生成的发票直接 PENDING_REVIEW（已匹配）
invoice.setStatus(InvoiceStatus.PENDING_REVIEW);
```

### 5.3 `OutputInvoiceServiceImpl` 替换 magic string

| 位置 | 现用字符串 | 替换为 |
|------|-----------|--------|
| line XXX | `"PENDING_CONFIRM"` | `InvoiceStatus.PENDING_CONFIRM` |
| line XXX | `"CONFIRMED"` | `InvoiceStatus.CONFIRMED` |
| line XXX | `"VOIDED"` | `InvoiceStatus.VOIDED` |

> **注**：具体行号需在实现时通过 `grep -n "status.*="` 定位。

---

## 6. 与 P20/P22/P24 的边界

| 边界 | 本 SPEC 范围 | P20/P22/P24 范围 |
|:---|:---|:---|
| **销售发票**状态 | ✅ 本 SPEC 定义 | 不动 |
| **应收单**(ReceivableEntity)状态 | ❌ 不涉及 | P20 定义 |
| **凭证**(VoucherEntity)状态 | ❌ 不涉及 | P22 定义 |
| 状态变更的审计日志 | ❌ 本 SPEC 仅 log.info | **P24 AOP 自动捕获** |
| 与客户档案联动 | ❌ 不涉及 | 现有 P13/P10 |

**关键调用关系**：
- 本 SPEC `markVouchered` 调用后，触发 VoucherService 生成凭证（由 P22 范围）
- 本 SPEC `onReconciliationUpdate` 由 `ReconciliationServiceImpl.execute()` 调用（已存在）

---

## 7. 测试要点

| 测试场景 | 方法 | 期望 |
|---------|------|------|
| 导入后默认 PENDING_CONFIRM | `testImportDefaultStatus()` | status=PENDING_CONFIRM |
| PENDING_CONFIRM → PENDING_REVIEW | `testSubmitForReview()` | submitForReview 成功 |
| 非 PENDING_CONFIRM 提交失败 | `testSubmitForReviewInvalid()` | 抛 BusinessException |
| PENDING_REVIEW → CONFIRMED | `testConfirm()` | confirm 成功 |
| CONFIRMED → VOUCHERED | `testMarkVouchered()` | markVouchered 成功 |
| VOUCHERED + unsettled=0 → FULLY_RECONCILED | `testFullyReconciled()` | status=FULLY_RECONCILED |
| VOUCHERED + unsettled>0 → PARTIALLY_RECONCILED | `testPartiallyReconciled()` | status=PARTIALLY_RECONCILED |
| 任意非终态可 VOIDED | `testVoidVariousStatus()` | voidInvoice 成功 |
| VOIDED 不能再 void | `testVoidVoided()` | 抛 BusinessException |
| 缺 reason 不能 void | `testVoidWithoutReason()` | 抛 BusinessException |

**Mockito 单测要求**：参考 `huicai-java-backend` skill §9 R5 + §12 P9 模式：
- ≥8 @Test 用例
- BaseMapper 全部 stub（`selectById` / `updateById`）
- 测试方法名全 ASCII + 中文注释（详见 §40 教训）

---

## 8. API 变更

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/output-invoices/{id}/submit-review` | 提交审核 |
| POST | `/api/v1/output-invoices/{id}/confirm` | 审核通过 |
| POST | `/api/v1/output-invoices/{id}/reject` | 审核驳回 |
| POST | `/api/v1/output-invoices/{id}/revert` | 回退到待审核 |
| POST | `/api/v1/output-invoices/{id}/void` | 作废 |
| GET | `/api/v1/output-invoices?status=VOUCHERED` | 按状态过滤（V38 索引）|

**前端**：在销售发票列表页加状态筛选器 + 操作按钮（按状态显示可用操作）。

---

## 9. 不做事项

- ❌ 不修改 InputInvoiceEntity（由 P21-b 处理）
- ❌ 不修改 VoucherEntity（由 P22 处理）
- ❌ 不实现 AOP 审计（由 P24 处理）
- ❌ 不实现审批人指派规则（按金额阈值，多级审批不在本期）
- ❌ 不实现"已作废"凭证的反向同步（业务上作废不联动凭证）
- ❌ 不实现发票打印/导出（不在状态机范围）

---

## 10. 后续依赖

- **依赖 P22 凭证状态机**：`markVouchered` 调用需要 VoucherService 提供"草稿凭证创建"接口
- **依赖 P24 审计追踪**：上线时把本 SPEC 的 log.info 替换为 audit_log 写入
- **依赖 P21-b**：采购发票同步实现，业务规则保持一致