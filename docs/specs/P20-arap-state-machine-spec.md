# P20 SPEC — AR/AP 状态机实现规格书

> 状态：待实现 | 优先级：高（P20）
> 依据：docs/design/P20-arap-state-machine-design.md
> 目标：补齐应收/应付/核销单状态机，消除 magic string
> 工期：单批交付，3 个 commit

---

## 0. 改动清单总览

| # | 改动 | 文件 | 风险 |
|---|------|------|------|
| 1 | 创建 `ArapStatus` 枚举常量类 | `backend/.../arap/constant/ArapStatus.java` | ✅ 低 |
| 2 | `ReceivableEntity` + `PayableEntity` 新增 status 字段 | Entity 文件 | ✅ 低 |
| 3 | V37 迁移: 给 t_receivable / t_payable 加 status 列 | Flyway | ✅ 低 |
| 4 | 创建 `ReceivableService` / `PayableService` 状态机方法 | Service 文件 | 🟡 中 |
| 5 | 更新 `ArapSettlementServiceImpl` 补全状态机 | Service 文件 | 🟡 中 |
| 6 | 更新 `AutoGenerationService` / 各调用方适配新 status | 调用方 | 🟡 中 |

---

## 1. 枚举常量

### 1.1 `ArapStatus` 常量类

**路径**: `com.huicai.module.arap.constant.ArapStatus`

```java
package com.huicai.module.arap.constant;

/**
 * AR/AP 模块统一状态常量.
 * 使用 String 常量而非 Enum，保持与数据库 VARCHAR 兼容。
 */
public final class ArapStatus {

    private ArapStatus() {}

    // ====== 通用 ======
    public static final String DRAFT = "DRAFT";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String REVERSED = "REVERSED";

    // ====== Receivable / Payable ======
    public static final String SETTLED = "SETTLED";

    // ====== Settlement 特有 ======
    public static final String VOUCHERED = "VOUCHERED";

    // ====== ReconciliationLog ======
    public static final String EXECUTED = "EXECUTED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    // ====== Prepayment ======
    public static final String APPLIED = "APPLIED";

    // ====== 检查方法 ======
    public static boolean isDraft(String status) {
        return DRAFT.equals(status);
    }
    public static boolean isConfirmed(String status) {
        return CONFIRMED.equals(status);
    }
    public static boolean isSettled(String status) {
        return SETTLED.equals(status);
    }
    public static boolean isReversible(String status) {
        return CONFIRMED.equals(status) || SETTLED.equals(status);
    }
    public static boolean isModifiable(String status) {
        return DRAFT.equals(status);
    }
}
```

---

## 2. 实体变更

### 2.1 `ReceivableEntity` 新增字段

```java
/** 状态: DRAFT / CONFIRMED / SETTLED / REVERSED，默认 CONFIRMED */
private String status;
```

- 默认 `CONFIRMED`：现有导入/流水生成的应收单都是"已确认"状态
- 新建 `createReceivable()` 方法接收 status 参数：供后续手工录入用（初始 DRAFT）

### 2.2 `PayableEntity` 新增字段

```java
/** 状态: DRAFT / CONFIRMED / SETTLED / REVERSED，默认 CONFIRMED */
private String status;
```

- 与 Receivable 对称，默认值同为 CONFIRMED

---

## 3. Flyway 迁移（V37）

```sql
-- V37__add_status_to_receivable_payable.sql

ALTER TABLE t_receivable ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';
COMMENT ON COLUMN t_receivable.status IS '状态: DRAFT/CONFIRMED/SETTLED/REVERSED';

ALTER TABLE t_payable ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';
COMMENT ON COLUMN t_payable.status IS '状态: DRAFT/CONFIRMED/SETTLED/REVERSED';

-- 已有数据全部为已确认，无需数据迁移
```

---

## 4. Service 状态机实现

### 4.1 `ReceivableService` 新增方法

```java
// ReceivableService.java (接口)

/** 确认应收单（草稿→已确认） */
void confirm(Long id, Long userId);

/** 标记为已结清（unsettled_amount=0 时调用） */
void markSettled(Long id, Long userId);

/** 反核销/冲销（CONFIRMED/SETTLED→REVERSED） */
void reverse(Long id, Long userId);

/** 核销扣减金额时同步检查状态 */
void onSettlementUpdate(Long id, BigDecimal settledAmount, Long userId);
```

```java
// ReceivableServiceImpl.java

@Transactional
public void confirm(Long id, Long userId) {
    ReceivableEntity entity = getById(id);
    if (!ArapStatus.isDraft(entity.getStatus())) {
        throw new BusinessException("仅草稿状态的应收单可确认");
    }
    entity.setStatus(ArapStatus.CONFIRMED);
    entity.setUpdatedBy(userId);
    receivableMapper.updateById(entity);
    log.info("应收单确认: id={}, userId={}", id, userId);
}

@Transactional
public void markSettled(Long id, Long userId) {
    ReceivableEntity entity = getById(id);
    if (!ArapStatus.isConfirmed(entity.getStatus())) {
        throw new BusinessException("仅已确认状态的应收单可标记结清");
    }
    if (entity.getUnsettledAmount().compareTo(BigDecimal.ZERO) != 0) {
        throw new BusinessException("应收单未结清余额不为零, 不可标记结清");
    }
    entity.setStatus(ArapStatus.SETTLED);
    entity.setUpdatedBy(userId);
    receivableMapper.updateById(entity);
    log.info("应收单结清: id={}, userId={}", id, userId);
}

@Transactional
public void reverse(Long id, Long userId) {
    ReceivableEntity entity = getById(id);
    if (!ArapStatus.isReversible(entity.getStatus())) {
        throw new BusinessException("仅已确认或已结清的应收单可冲销");
    }
    entity.setStatus(ArapStatus.REVERSED);
    entity.setUpdatedBy(userId);
    receivableMapper.updateById(entity);
    log.info("应收单冲销: id={}, userId={}", id, userId);
}
```

`PayableService` 实现完全对称，此处省略。

### 4.2 核销扣减时同步状态

在 `ReconciliationServiceImpl.execute()` 和 `reverse()` 中，更新 `ReceivableEntity` / `PayableEntity` 的 `settledAmount` / `unsettledAmount` **之后**，检查 `unsettledAmount`：

```java
// 在 receivableMapper.updateById(r) 之后:
if (r.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0
    && ArapStatus.isConfirmed(r.getStatus())) {
    // unsettled 归零 → 自动标记 SETTLED
    r.setStatus(ArapStatus.SETTLED);
    receivableMapper.updateById(r);
}
```

### 4.3 `ArapSettlementServiceImpl` 补全

```java
@Transactional
public void generateVoucher(Long id, Long userId) {
    ArapSettlementEntity entity = getById(id);
    if (!ArapStatus.isConfirmed(entity.getStatus())) {
        throw new BusinessException("仅已确认的核销单可生成凭证");
    }
    // 创建凭证逻辑（可复用 BusinessDocServiceImpl.generateVoucher 风格）
    // ...
    entity.setStatus(ArapStatus.VOUCHERED);
    mapper.updateById(entity);
}

@Transactional
public void reverse(Long id, Long userId) {
    ArapSettlementEntity entity = getById(id);
    if (!ArapStatus.isConfirmed(entity.getStatus())) {
        throw new BusinessException("仅已确认的核销单可冲销");
    }
    // 恢复应收/应付的 unsettled_amount
    List<ArapSettlementEntryEntity> entries = entryMapper.selectList(
        new LambdaQueryWrapper<ArapSettlementEntryEntity>()
            .eq(ArapSettlementEntryEntity::getSettlementId, id));
    for (ArapSettlementEntryEntity entry : entries) {
        restoreUnsettledAmount(entry);
    }
    entity.setStatus(ArapStatus.REVERSED);
    mapper.updateById(entity);
}
```

---

## 5. 调用方适配

### 5.1 `SalesInvoiceImportService.createReceivableFromInvoice()`

```java
// 现有：无 status
ReceivableEntity recv = new ReceivableEntity();
// ...
receivableMapper.insert(recv);

// 改为：
recv.setStatus(ArapStatus.CONFIRMED);  // 发票导入生成的应收单直接已确认
receivableMapper.insert(recv);
```

### 5.2 `AutoGenerationService.createReceivableOrPayableFromBankDoc()`

```java
// Receivable:
recv.setStatus(ArapStatus.CONFIRMED);  // 流水生成的应收单直接已确认

// Payable:
pay.setStatus(ArapStatus.CONFIRMED);   // 流水生成的应付单直接已确认

// Prepayment:
prepay.setStatus(ArapStatus.DRAFT);    // 预付款保留草稿（现有行为不变）
```

### 5.3 `InputInvoiceImportService.createPayableFromInvoice()`

```java
pay.setStatus(ArapStatus.CONFIRMED);  // 发票导入生成的应付单直接已确认
```

### 5.4 `ArapSettlementServiceImpl.create()`

```java
// 现有：
if (entity.getStatus() == null) entity.setStatus("DRAFT");

// 改为：
if (entity.getStatus() == null) entity.setStatus(ArapStatus.DRAFT);
```

---

## 6. ReconciliationLog 状态机补全

`ReconciliationServiceImpl` 已有 reverse/approve/reject 逻辑，只需将 magic string 替换为 `ArapStatus` 常量：

| 位置 | 现用字符串 | 替换为 |
|------|-----------|--------|
| line 301 | `"CONFIRMED"` | `ArapStatus.CONFIRMED` |
| line 427 | `"CONFIRMED"` | `ArapStatus.CONFIRMED` |
| line 459 | `"CONFIRMED"` | `ArapStatus.CONFIRMED` |
| line 473 | `"CONFIRMED"` | `ArapStatus.CONFIRMED` |
| line 316 | `"CONFIRMED"` | `ArapStatus.CONFIRMED` |

---

## 7. 测试要点

| 测试场景 | 方法 |
|---------|------|
| 新建应收单默认 CONFIRMED | 验证 insert 后 status=CONFIRMED |
| DRAFT 可 confirm 成 CONFIRMED | 调用 confirm() 成功 |
| CONFIRMED 再调用 confirm 拒绝 | 抛出 BusinessException |
| 核销后 unsettled=0 自动 SETTLED | 模拟 execute 扣减，验证 status 变更 |
| SETTLED 可 reverse 为 REVERSED | 调用 reverse() 成功 |
| REVERSED 不能再 reverse | 抛出 BusinessException |
| 核销单 confirm → voucher → reverse | 完整链路 |
| 预付款保持 DRAFT | 验证 insert 后 status=DRAFT |

---

## 8. API 变更

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/receivables/{id}/confirm` | 确认应收单 |
| POST | `/api/v1/receivables/{id}/reverse` | 冲销应收单 |
| POST | `/api/v1/payables/{id}/confirm` | 确认应付单 |
| POST | `/api/v1/payables/{id}/reverse` | 冲销应付单 |
| POST | `/api/v1/arap/settlements/{id}/generate-voucher` | 核销单生成凭证 |
| POST | `/api/v1/arap/settlements/{id}/reverse` | 反核销 |

（前端菜单页不需新增，在现有往来核销页面加操作按钮即可。）

---

## 9. 不做事项

- ❌ 不新增 ReceiptEntity / PaymentEntity
- ❌ 不修改 BusinessDoc 的状态机（已有 DRAFT→SUBMITTED→APPROVED→VOUCHERED，完整可用）
- ❌ 不做三单匹配（PO-GRN-Invoice）
- ❌ 不做付款审批流
- ❌ 不创建 ReceivableController / PayableController（仅加 Service 方法）

---

## 10. 与发票/凭证状态机的对齐说明（2026-06-21）

> **背景**：`docs/需求分析书_发票与凭证状态机_V1.0.md` 发布后，本 SPEC（P20）的状态枚举与之交叉引用，需明确边界，避免后续 SPEC 出现命名/语义冲突。

### 10.1 命名一致性

| 本 SPEC 状态 | 发票文档对应状态 | 关系 |
|:---|:---|:---|
| `ArapStatus.VOUCHERED`（核销单）| 发票 `VOUCHERED`（已生成凭证）| **同名同语义**，命名风格统一 |
| `ArapStatus.REVERSED`（应收/应付/核销单）| 凭证 `REVERSED`（被红字冲销）| **同名同语义**，命名风格统一 |
| `ArapStatus.SETTLED`（应收/应付单）| 发票 `FULLY_RECONCILED` | **语义等价但命名不同**——保留 SETTLED，P20 已落地 |
| Receivable/Payable `CONFIRMED` | 发票 `CONFIRMED` | **同名但语义不同**：应收"已确认"=等待核销；发票"已确认"=等待生成凭证。是上下游关系 |
| — | 发票 `VOIDED` | P20 不涉及，作废在发票侧处理 |
| — | 发票 `PARTIALLY_RECONCILED` | P20 通过 `unsettled_amount > 0` 隐式表达；发票文档新增显式状态（前端可按 status 过滤）|

### 10.2 P20 立场

- **本 SPEC 状态枚举保持不变**（已落地，避免破坏现有数据）
- 未来 SPEC（P21 发票状态机、P22 凭证状态机、P24 审计追踪）需引用本 SPEC 时，统一使用：
  - `ArapStatus.VOUCHERED` / `ArapStatus.REVERSED`（同名同语义）
  - `ArapStatus.SETTLED`（语义等价，但命名不统一，需在引用处注明映射）
- 新模块（发票、凭证）状态机由 P21/P22 独立定义，不复用 `ArapStatus` 类（不同领域）

### 10.3 与 P21/P22/P24 的边界

| 边界 | P20 范围 | P21/P22/P24 范围 |
|:---|:---|:---|
| 应收/应付单状态 | ✅ 本 SPEC 定义 | 不动 |
| 核销单状态 | ✅ 本 SPEC 定义 | 不动 |
| 预付款状态 | ✅ 本 SPEC 定义 | 不动 |
| **发票**状态 | ❌ 不涉及 | **P21 发票状态机 SPEC** |
| **凭证**状态 | ❌ 不涉及 | **P22 凭证状态机 SPEC** |
| **审计日志** | ❌ 不涉及 | **P24 审计追踪 SPEC** |
| 状态变更的审计 | ❌ 不涉及 | **P24 审计追踪 SPEC 统一实现** |

### 10.4 上下游数据流（参考）

```
发票 P21: PENDING_CONFIRM → PENDING_REVIEW → CONFIRMED → VOUCHERED
                                                    ↓
                                          生成 ReceivableEntity (status=CONFIRMED, P20)
                                                    ↓
                                          核销扣减 unsettled_amount
                                                    ↓
                                          unsettled=0 → ReceivableEntity.status=SETTLED (P20)
                                          unsettled>0 → 发票.status=PARTIALLY_RECONCILED (P21)

凭证 P22: DRAFT → PENDING_REVIEW → APPROVED → POSTED → REVERSED
                                            ↓
                                      audit_log 记录每次变更 (P24)
```
