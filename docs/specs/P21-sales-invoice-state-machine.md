# P21-a SPEC — 销售发票状态机实现规格书

> 状态：**已实施（P31修正）** | 优先级：高（P21-a）
> 依据：`docs/需求分析书_发票与凭证状态机_V1.0.md` §3.1 发票状态机
> 目标：为 `OutputInvoiceEntity`（销售发票）建立完整 8 状态机，消除 magic string
> 工期：单批交付，3 个 commit
> 拆分说明：原 P21 拆分为 P21-a（销售发票，本文件）+ P21-b（采购发票，已废弃）
>
> **P31 修正（2026-06-26）**：`confirm()` 现在自动生成应收单 + 凭证。
> 流程变更为：发票导入 → 人工审核(confirm) → 自动生成应收单据和凭证 → 人工审核(凭证)。

---

## 0. 改动清单总览

| # | 改动 | 文件 | 风险 |
|---|------|------|------|
| 1 | 创建 `InvoiceStatus` 枚举常量类 | `backend/.../tax/constant/InvoiceStatus.java` | ✅ 低 |
| 2 | `OutputInvoiceEntity` 新增 status 字段约束（已存在 String 字段，补注释 + 常量）| Entity 文件 | ✅ 低 |
| 3 | **V46** 迁移: `t_output_invoice.status` 数据迁移 4→8 + 新 CHECK 约束 + 索引 | Flyway | ✅ 低 |
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

    // ====== 冲销（V46 迁移加入，承接旧 RED_INK 数据）======
    public static final String REVERSED = "REVERSED";

    // ====== 检查方法 ======
    public static boolean isPendingConfirm(String status) {
        return PENDING_CONFIRM.equals(status);
    }
    public static boolean isPendingReview(String status) {
        return PENDING_REVIEW.equals(status);
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
        // 任何非终态都可作废（VOIDED/REVERSED/FULLY_RECONCILED 不可作废）
        return !VOIDED.equals(status)
            && !REVERSED.equals(status)
            && !FULLY_RECONCILED.equals(status);
    }
    public static boolean isModifiable(String status) {
        // 仅 PENDING_CONFIRM / PENDING_REVIEW 可修改
        return PENDING_CONFIRM.equals(status) || PENDING_REVIEW.equals(status);
    }
    public static boolean isReversed(String status) {
        return REVERSED.equals(status);
    }
    public static boolean isTerminal(String status) {
        // 终态：VOIDED / REVERSED / FULLY_RECONCILED
        return VOIDED.equals(status)
            || REVERSED.equals(status)
            || FULLY_RECONCILED.equals(status);
    }
}
```

---

## 2. 实体变更

### 2.1 `OutputInvoiceEntity` 注释更新

**现状**：`status` 字段是 String，无注释。

**改动**：

```java
/**
 * 状态: PENDING_CONFIRM / PENDING_REVIEW / CONFIRMED / VOUCHERED /
 *       FULLY_RECONCILED / PARTIALLY_RECONCILED / VOIDED / REVERSED
 * 详见 com.huicai.module.tax.constant.InvoiceStatus
 */
private String status;
```

---

## 3. Flyway 迁移（V46）

> **重要变更（2026-06-22）**：起草本 SPEC 时未考虑 V8 已有 CHECK 约束
> `chk_output_invoice_status CHECK (status IN ('DRAFT', 'ISSUED', 'VOID', 'RED_INK'))`。
> V46 必须先 DROP 旧 CHECK 再 UPDATE 旧数据最后 ADD 新 CHECK（V45 初版因顺序错误导致 PG check_violation 回滚，详见 V46 注释）。
>
> **状态值映射**（2026-06-22 老丁拍板）：
> | 旧状态 (V8) | 新状态 (V46) | 语义说明 |
> |:---|:---|:---|
> | `DRAFT` | `PENDING_CONFIRM` | 草稿 = 待确认 |
> | `ISSUED` | `CONFIRMED` | **已开票 = 已确认**（注意：旧 ISSUED 不一定有 voucher_id，业务上 voucher_id 为空的 CONFIRMED 视为"待生成凭证"，详见 §3.2 注释） |
> | `VOID` | `VOIDED` | 已作废 |
> | `RED_INK` | `REVERSED` | 红字冲销（已生成凭证后被冲销） |
> | `NULL` | `PENDING_CONFIRM` | 未设置状态的回退到待确认 |
>
> **新增状态**：V46 引入的 `PENDING_REVIEW` / `VOUCHERED` / `FULLY_RECONCILED` / `PARTIALLY_RECONCILED` 4 个状态，
> V46 数据迁移时无旧值映射，所有现有记录 status 都不在这 4 个状态里。

### 3.1 完整 V46 SQL

```sql
-- V46__migrate_output_invoice_status_to_8_states.sql
-- 2026-06-22 P21-a-1 修复版
-- 修复历史: V45 顺序 bug（先 UPDATE 再 DROP 旧 CHECK → 23514 check_violation），V46 修正为先 DROP 再 UPDATE 再 ADD

-- ============================================================
-- Step 1: DROP V8 旧 CHECK 约束（chk_output_invoice_status 4 状态）
-- ============================================================

ALTER TABLE t_output_invoice
    DROP CONSTRAINT IF EXISTS chk_output_invoice_status;

-- ============================================================
-- Step 2: 数据迁移（4 旧状态 → 8 新状态）
-- ============================================================

-- 2.1 NULL → PENDING_CONFIRM（未设置状态视为待确认）
UPDATE t_output_invoice
SET status = 'PENDING_CONFIRM'
WHERE status IS NULL;

-- 2.2 DRAFT → PENDING_CONFIRM（草稿 = 待确认）
UPDATE t_output_invoice
SET status = 'PENDING_CONFIRM'
WHERE status = 'DRAFT';

-- 2.3 ISSUED → CONFIRMED（已开票 = 已确认）
-- 注意: 旧 ISSUED 记录中部分 voucher_id 为空,
--       业务上 voucher_id 为空的 CONFIRMED 视为"待生成凭证"
UPDATE t_output_invoice
SET status = 'CONFIRMED'
WHERE status = 'ISSUED';

-- 2.4 VOID → VOIDED（已作废）
UPDATE t_output_invoice
SET status = 'VOIDED'
WHERE status = 'VOID';

-- 2.5 RED_INK → REVERSED（红字冲销）
UPDATE t_output_invoice
SET status = 'REVERSED'
WHERE status = 'RED_INK';

-- ============================================================
-- Step 3: ADD 新 CHECK 约束（8 状态）
-- ============================================================

ALTER TABLE t_output_invoice
    ADD CONSTRAINT chk_output_invoice_status
    CHECK (status IN (
        'PENDING_CONFIRM', 'PENDING_REVIEW', 'CONFIRMED',
        'VOUCHERED', 'FULLY_RECONCILED', 'PARTIALLY_RECONCILED', 'VOIDED',
        'REVERSED'
    ));

-- ============================================================
-- Step 4: status 字段索引（前端按状态过滤，幂等）
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_t_output_invoice_status
    ON t_output_invoice(status);

-- ============================================================
-- Step 5: COMMENT 更新
-- ============================================================

COMMENT ON COLUMN t_output_invoice.status IS
    '状态: PENDING_CONFIRM/PENDING_REVIEW/CONFIRMED/VOUCHERED/FULLY_RECONCILED/PARTIALLY_RECONCILED/VOIDED/REVERSED (2026-06-22 由 V8 旧 4 状态经 V46 迁移)';

-- ============================================================
-- Step 6: 迁移结果审计（输出统计，供人工核对）
-- ============================================================

DO $$
DECLARE
    rec RECORD;
BEGIN
    FOR rec IN
        SELECT status, COUNT(*) AS cnt
        FROM t_output_invoice
        GROUP BY status
        ORDER BY status
    LOOP
        RAISE NOTICE 'V46 迁移结果: status=%, count=%', rec.status, rec.cnt;
    END LOOP;
END $$;
```

### 3.2 ISSUED→CONFIRMED 的业务语义说明

旧 `ISSUED`（"已开票"）迁移到新 `CONFIRMED`（"已确认"），但**部分记录的 voucher_id 为空**：

- 旧业务流：销售发票开出去 → 可能立即生成凭证 → 也可能延后（赊销未收货款）
- 新状态机：`CONFIRMED → VOUCHERED`（生成凭证后转 VOUCHERED）

**业务规则**（前端 / 报表实现）：
- `status = CONFIRMED` 且 `voucher_id IS NOT NULL` → "已确认且已生成凭证"（实际显示为已生凭证）
- `status = CONFIRMED` 且 `voucher_id IS NULL` → "已确认但待生成凭证"（需要补凭证）
- 前端列表页应**额外显示 voucher_id 是否为空**，辅助业务判断

**数据迁移后预期**：
- 所有旧 `ISSUED` 记录 → `CONFIRMED` + 业务上分两类（有/无 voucher_id）
- 需要人工/批量任务补凭证（**不在本 SPEC 范围**）

### 3.3 REVERSED 状态的特殊处理

旧 `RED_INK`（红字）迁移到新 `REVERSED`，**继续保留在 7+1 状态枚举里**（CHECK 约束已包含 REVERSED）。

原因：
- 需求文档 §3.1 设计的 7 状态不含 REVERSED
- 但实际生产数据有 RED_INK 记录，必须有状态承接
- 妥协方案：在枚举里加 REVERSED 作 8 状态（与文档略有差异，详见 §3.4）

### 3.4 需求文档 §3.1 与 V46 实施的差异说明

| 项 | 需求文档 §3.1 设计 | V46 实施 | 差异原因 |
|:---|:---|:---|:---|
| 发票状态数 | 7 个 | 8 个（+ REVERSED）| 旧数据 RED_INK 需承接 |
| REVERSED 语义 | 未列 | 已生成凭证后被冲销 | 与 V8 旧 RED_INK 等价 |
| 未来清理 | — | 待所有 RED_INK 记录归档后，移除 REVERSED 状态 | 长期收敛 |

**P21-b 采购发票迁移见独立 SPEC（已废弃），不再同步更新。**

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

    /**
     * 审核通过 (PENDING_REVIEW → CONFIRMED).
     *
     * P31: 审核通过后自动触发 postProcessAfterInvoiceConfirm,
     * 直接生成业务单(DRAFT) + 应收单(DRAFT) + 凭证(DRAFT).
     * 发票状态变为 VOUCHERED；无需中间的业务单审批环节。
     */
    void confirm(Long invoiceId, Long userId);

    /** 审核驳回 (PENDING_REVIEW → PENDING_CONFIRM, 记录驳回原因) */
    void reject(Long invoiceId, Long userId, String reason);

    /** 回退到待审核 (CONFIRMED → PENDING_REVIEW, 选错结算状态) */
    void revertToReview(Long invoiceId, Long userId);

    /**
     * 标记已生成凭证 (CONFIRMED → VOUCHERED, 记录 voucherId).
     * P31: 该方法仅由 TaxService.generateVoucherFromInvoice 内部调用，
     * 前端不再直接调用此接口。
     */
    void markVouchered(Long invoiceId, Long voucherId, Long userId);

    /** 核销扣减后更新状态 (VOUCHERED → FULLY_RECONCILED / PARTIALLY_RECONCILED) */
    void onReconciliationUpdate(Long invoiceId, BigDecimal unsettledAmount, Long userId);

    /** 作废 (任意非终态 → VOIDED, 记录作废原因) */
    void voidInvoice(Long invoiceId, Long userId, String reason);
}
```

**实现** `OutputInvoiceStateMachineServiceImpl.java`（关键骨架，P31修正后）：

```java
package com.huicai.module.tax.service.impl;

import com.huicai.module.tax.constant.InvoiceStatus;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import com.huicai.module.tax.service.TaxService;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.BusinessDocEntryMapper;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.arap.entity.ReceivableEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class OutputInvoiceStateMachineServiceImpl
        implements OutputInvoiceStateMachineService {

    private final OutputInvoiceMapper invoiceMapper;
    private final BusinessDocMapper docMapper;
    private final BusinessDocEntryMapper docEntryMapper;
    private final ReceivableMapper receivableMapper;
    private final StringRedisTemplate redisTemplate;

    @Lazy
    @Autowired
    private TaxService taxService;

    /**
     * 审核通过 (PENDING_REVIEW → CONFIRMED).
     *
     * P31: 审核通过后自动触发 postProcessAfterInvoiceConfirm,
     * 直接创建业务单 + 应收单 + 凭证，发票状态变为 VOUCHERED。
     */
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

        // P31: 审核后自动生成业务单 + 应收单 + 凭证
        postProcessAfterInvoiceConfirm(invoiceId, userId);
    }

    /**
     * 发票审核通过后自动生成：业务单(DRAFT) + 应收单(DRAFT) + 凭证(DRAFT)。
     *
     * 流程：发票审核 → 生成应收单据和凭证（无需中间的业务单审批环节）。
     * 业务单仅作为追溯记录，无需手动审批。
     */
    @Transactional
    public void postProcessAfterInvoiceConfirm(Long invoiceId, Long userId) {
        OutputInvoiceEntity invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            log.warn("发票不存在: invoiceId={}", invoiceId);
            return;
        }

        // 1) 创建业务单（DRAFT）
        BusinessDocEntity doc = createBusinessDocFromInvoice(invoice, userId);

        // 2) 创建应收单（DRAFT）
        createReceivableFromInvoice(doc, invoice, userId);

        // 3) 直接生成凭证（模板匹配 or 硬编码科目）
        taxService.generateVoucherFromInvoice(invoiceId, userId);

        // 4) 同步 voucherId 到业务单和应收单
        OutputInvoiceEntity updated = invoiceMapper.selectById(invoiceId);
        if (updated != null && updated.getVoucherId() != null) {
            doc.setVoucherId(updated.getVoucherId());
            docMapper.updateById(doc);

            ReceivableEntity recv = receivableMapper.selectOne(
                new LambdaQueryWrapper<ReceivableEntity>()
                    .eq(ReceivableEntity::getDocId, doc.getId()));
            if (recv != null) {
                recv.setVoucherId(updated.getVoucherId());
                receivableMapper.updateById(recv);
            }
        }
        log.info("发票审核后自动生成凭证完成: invoiceId={}", invoiceId);
    }

    private BusinessDocEntity createBusinessDocFromInvoice(
            OutputInvoiceEntity invoice, Long userId) {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo(generateDocNo(invoice.getPeriod()));
        doc.setDocType("INVOICE_OUT");
        doc.setDocDate(invoice.getInvoiceDate());
        doc.setPeriod(invoice.getPeriod());
        doc.setAmount(invoice.getTotalAmount());
        doc.setCustomerId(invoice.getCustomerId());
        doc.setSummary(invoice.getCustomerName());
        doc.setInvoiceNo(invoice.getInvoiceNo());
        doc.setStatus("DRAFT");
        doc.setSource("INVOICE_IMPORT");
        doc.setCreatedBy(userId != null ? userId : 1L);
        docMapper.insert(doc);

        BusinessDocEntryEntity entry = new BusinessDocEntryEntity();
        entry.setDocId(doc.getId());
        entry.setAmount(invoice.getTotalAmount());
        entry.setInvoiceNo(invoice.getInvoiceNo());
        entry.setSummary(invoice.getCustomerName());
        entry.setSortOrder(1);
        docEntryMapper.insert(entry);

        invoice.setDocId(doc.getId());
        invoiceMapper.updateById(invoice);
        return doc;
    }

    private void createReceivableFromInvoice(
            BusinessDocEntity doc, OutputInvoiceEntity invoice, Long userId) {
        ReceivableEntity recv = new ReceivableEntity();
        recv.setCustomerId(invoice.getCustomerId());
        recv.setDocId(doc.getId());
        recv.setVoucherId(doc.getVoucherId());
        recv.setPeriod(invoice.getPeriod());
        recv.setTxDate(invoice.getInvoiceDate());
        recv.setAmount(invoice.getTotalAmount());
        recv.setSettledAmount(BigDecimal.ZERO);
        recv.setUnsettledAmount(invoice.getTotalAmount());
        recv.setSummary(invoice.getCustomerName());
        recv.setStatus(ArapStatus.DRAFT);
        receivableMapper.insert(recv);
    }

    // markVouchered / onReconciliationUpdate / voidInvoice / ... 同前，略
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

**关键调用关系（P31修正）**：
- 本 SPEC `confirm` 审核通过后，自动调用 `postProcessAfterInvoiceConfirm`
  → 创建 `BusinessDocEntity`（DRAFT，业务单）
  → 创建 `ReceivableEntity`（DRAFT，应收单，P20 范围）
  → 调用 `TaxService.generateVoucherFromInvoice` 创建 `VoucherEntity`（DRAFT，凭证，P22 范围）
  → 内部调用 `markVouchered` 将发票状态变为 VOUCHERED
- 本 SPEC `onReconciliationUpdate` 由 `ReconciliationServiceImpl.execute()` 调用（已存在）

---

## 7. 测试要点

| 测试场景 | 方法 | 期望 |
|---------|------|------|
| 导入后默认 PENDING_CONFIRM | `testImportDefaultStatus()` | status=PENDING_CONFIRM |
| PENDING_CONFIRM → PENDING_REVIEW | `testSubmitForReview()` | submitForReview 成功 |
| 非 PENDING_CONFIRM 提交失败 | `testSubmitForReviewInvalid()` | 抛 BusinessException |
| PENDING_REVIEW → CONFIRMED | `testConfirm()` | confirm 成功 |
| **P31: confirm 后自动生业务单+应收单+凭证** | `testConfirmAutoGeneratesVoucher()` | doc/receivable/voucher 各 insert 1 次，发票状态=VOUCHERED |
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
| POST | `/api/v1/output-invoices/{id}/confirm` | **审核通过（P31：自动触发生成业务单+应收单+凭证）** |
| POST | `/api/v1/output-invoices/{id}/reject` | 审核驳回 |
| POST | `/api/v1/output-invoices/{id}/revert` | 回退到待审核 |
| POST | `/api/v1/output-invoices/{id}/void` | 作废 |
| GET | `/api/v1/output-invoices?status=VOUCHERED` | 按状态过滤（V38 索引）|

**P31 变更**：
- `mark-vouchered`（生成凭证）不再是独立前端按钮，凭证由 `confirm` 审核通过后自动生成
- `POST /api/v1/output-invoices/{id}/mark-vouchered` 仍保留（可用于异常重试），但不对外暴露
- 凭证生成后状态会变为 VOUCHERED，前端不会再显示"生成凭证"按钮

**前端**：在销售发票列表页加状态筛选器 + 操作按钮（按状态显示可用操作）。
- confirm 成功后，前端应刷新列表显示 VOUCHERED 状态
- 凭证的"人工审核"在凭证管理页面完成（P22）

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