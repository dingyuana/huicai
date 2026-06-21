# P22 SPEC — 凭证状态机扩展规格书

> 状态：待实现 | 优先级：高（P22）
> 依据：`docs/需求分析书_发票与凭证状态机_V1.0.md` §3.2 凭证状态机
> 目标：扩展 `VoucherEntity` 增加 REVERSED/REJECTED 字段，不破坏现有 4 状态数据
> 工期：单批交付，3 个 commit
> 核心决策：保持现有 `VoucherEntity.status` 4 状态（DRAFT/SUBMITTED/AUDITED/POSTED），REVERSED/REJECTED 作为**附属字段**（详见需求文档 §3.2 决议）

---

## 0. 改动清单总览

| # | 改动 | 文件 | 风险 |
|---|------|------|------|
| 1 | 创建 `VoucherStatus` 常量类（封装 4 状态 + 2 附属）| `backend/.../finance/constant/VoucherStatus.java` | ✅ 低 |
| 2 | `VoucherEntity` 新增 3 字段：`rejected_reason` / `reversed_voucher_id` / `reversal_pair_id` | Entity 文件 | 🟡 中 |
| 3 | V41 迁移: t_voucher 加 3 字段 + CHECK 约束 | Flyway | 🟡 中 |
| 4 | 创建 `VoucherStateMachineService`（含反向/驳回/红冲方法）| Service 文件 | 🟡 中 |
| 5 | `VoucherServiceImpl` 适配新字段（替换 magic string）| Service 文件 | 🟡 中 |
| 6 | 红字冲销调用方（BusinessDocServiceImpl 等）适配 | 调用方 | 🟡 中 |
| 7 | 单测覆盖（≥10 @Test）| Test 文件 | ✅ 低 |

---

## 1. 枚举常量

### 1.1 `VoucherStatus` 常量类

**路径**: `com.huicai.module.finance.constant.VoucherStatus`

```java
package com.huicai.module.finance.constant;

/**
 * 凭证模块状态常量.
 * 核心 4 状态（status 字段）+ REJECTED/REVERSED 作为附属字段.
 * 详见 docs/需求分析书_发票与凭证状态机_V1.0.md §3.2.
 */
public final class VoucherStatus {

    private VoucherStatus() {}

    // ====== status 字段 4 状态 ======
    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String AUDITED = "AUDITED";
    public static final String POSTED = "POSTED";

    // ====== 附属字段值（不进入 status 字段）======
    // REJECTED 用 status=DRAFT + rejected_reason 非空表达
    // REVERSED 用 status=POSTED + reversed_voucher_id 非空表达

    // ====== 检查方法 ======
    public static boolean isDraft(String status) {
        return DRAFT.equals(status);
    }
    public static boolean isSubmittable(String status) {
        return DRAFT.equals(status);
    }
    public static boolean isAuditable(String status) {
        return SUBMITTED.equals(status);
    }
    public static boolean isPostable(String status) {
        return AUDITED.equals(status);
    }
    public static boolean isPosted(String status) {
        return POSTED.equals(status);
    }
    public static boolean isModifiable(String status) {
        // POSTED 不可修改（铁律）
        return !POSTED.equals(status);
    }
    public static boolean isReversible(String status) {
        // 仅 POSTED 可冲销
        return POSTED.equals(status);
    }
}
```

---

## 2. 实体变更

### 2.1 `VoucherEntity` 新增字段

**位置**：现有 87 行后追加（不破坏现有字段）。

```java
// ========== P22 新增字段（2026-06-21 起）==========

/** 驳回原因（仅在 SUBMITTED → DRAFT 反向时记录） */
private String rejectedReason;

/** 红字凭证 ID（指向关联的红字凭证；POSTED 后唯一修正路径） */
private Long reversedVoucherId;

/** 冲销对 ID（双向绑定，原凭证与红字凭证共享同一 ID） */
private Long reversalPairId;

/** 驳回时间 */
private LocalDateTime rejectedAt;

/** 驳回操作人 */
private Long rejectedBy;
```

### 2.2 字段语义说明

| 字段 | 何时写入 | 与 status 关系 |
|:---|:---|:---|
| `rejectedReason` | SUBMITTED 驳回时 | status 从 SUBMITTED 回退到 DRAFT |
| `rejectedAt` / `rejectedBy` | 同上 | 同上 |
| `reversedVoucherId` | 生成红字凭证时（双向）| 双方 status 都保持 POSTED |
| `reversalPairId` | 同上 | 双方共享同一 UUID/ID |

---

## 3. Flyway 迁移（V41）

```sql
-- V41__add_voucher_state_machine_fields.sql

-- 1. 加 5 个新字段
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS rejected_reason VARCHAR(500);
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP;
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS rejected_by BIGINT;
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS reversed_voucher_id BIGINT;
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS reversal_pair_id BIGINT;

-- 2. CHECK 约束：status 字段仅允许 4 状态值
ALTER TABLE t_voucher
    DROP CONSTRAINT IF EXISTS t_voucher_status_check;
ALTER TABLE t_voucher
    ADD CONSTRAINT t_voucher_status_check
    CHECK (status IN ('DRAFT', 'SUBMITTED', 'AUDITED', 'POSTED'));

-- 3. 索引
CREATE INDEX IF NOT EXISTS idx_t_voucher_reversal_pair_id
    ON t_voucher(reversal_pair_id) WHERE reversal_pair_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_t_voucher_reversed_voucher_id
    ON t_voucher(reversed_voucher_id) WHERE reversed_voucher_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_t_voucher_status
    ON t_voucher(status);

-- 4. 已有数据校验
DO $$
DECLARE
    invalid_status_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_status_count
    FROM t_voucher
    WHERE status IS NOT NULL
      AND status NOT IN ('DRAFT', 'SUBMITTED', 'AUDITED', 'POSTED');
    IF invalid_status_count > 0 THEN
        RAISE EXCEPTION 't_voucher.status 存在 % 条非 4 状态值，迁移前请人工修正',
            invalid_status_count;
    END IF;
END $$;

COMMENT ON COLUMN t_voucher.rejected_reason IS '驳回原因';
COMMENT ON COLUMN t_voucher.reversed_voucher_id IS '红字凭证 ID（双向绑定）';
COMMENT ON COLUMN t_voucher.reversal_pair_id IS '冲销对 ID（双方共享）';
```

---

## 4. Service 状态机实现

### 4.1 `VoucherStateMachineService` 接口

**接口** `VoucherStateMachineService.java`：

```java
package com.huicai.module.finance.service;

/**
 * 凭证状态机服务.
 * 详见 docs/需求分析书_发票与凭证状态机_V1.0.md §3.2.
 */
public interface VoucherStateMachineService {

    /** 提交 (DRAFT → SUBMITTED) */
    void submit(Long voucherId, Long userId);

    /** 审核通过 (SUBMITTED → AUDITED) */
    void audit(Long voucherId, Long userId);

    /** 审核驳回 (SUBMITTED → DRAFT, 记录原因) */
    void reject(Long voucherId, Long userId, String reason);

    /** 过账 (AUDITED → POSTED) */
    void post(Long voucherId, Long userId);

    /** 反过账 (POSTED → AUDITED, 仅纠错用，业务上应避免) */
    void unpost(Long voucherId, Long userId);

    /** 生成红字凭证 (POSTED → 红字凭证，status 双方都 POSTED) */
    Long generateReversalVoucher(Long originalVoucherId, Long userId);
}
```

### 4.2 关键实现骨架

```java
@Override
@Transactional
public void reject(Long voucherId, Long userId, String reason) {
    VoucherEntity entity = voucherMapper.selectById(voucherId);
    if (!VoucherStatus.isAuditable(entity.getStatus())) {
        throw new BusinessException(
            "仅已提交状态可驳回，当前: " + entity.getStatus());
    }
    if (reason == null || reason.trim().isEmpty()) {
        throw new BusinessException("驳回必须填写原因");
    }
    entity.setStatus(VoucherStatus.DRAFT);
    entity.setRejectedReason(reason);
    entity.setRejectedAt(LocalDateTime.now());
    entity.setRejectedBy(userId);
    entity.setUpdatedBy(userId);
    voucherMapper.updateById(entity);
    log.info("凭证驳回: id={}, userId={}, reason={}", voucherId, userId, reason);
}

@Override
@Transactional
public Long generateReversalVoucher(Long originalVoucherId, Long userId) {
    VoucherEntity original = voucherMapper.selectById(originalVoucherId);
    if (!VoucherStatus.isReversible(original.getStatus())) {
        throw new BusinessException("仅已过账凭证可冲销");
    }
    if (original.getReversedVoucherId() != null) {
        throw new BusinessException("该凭证已被冲销，请勿重复操作");
    }

    // 1. 创建红字凭证（拷贝借贷分录，金额变负）
    VoucherEntity reversal = copyAsReversal(original, userId);
    reversal.setStatus(VoucherStatus.POSTED);  // 红字凭证直接 POSTED
    voucherMapper.insert(reversal);

    // 2. 双向绑定 reversalPairId
    Long pairId = generatePairId();  // UUID or sequence
    original.setReversedVoucherId(reversal.getId());
    original.setReversalPairId(pairId);
    voucherMapper.updateById(original);

    reversal.setReversedVoucherId(original.getId());
    reversal.setReversalPairId(pairId);
    voucherMapper.updateById(reversal);

    log.info("红字冲销完成: originalId={}, reversalId={}",
        original.getId(), reversal.getId());
    return reversal.getId();
}
```

---

## 5. 与 P21-a/P21-b/P24 的边界

| 边界 | 本 SPEC 范围 | 其他 SPEC |
|:---|:---|:---|
| **凭证**核心 4 状态 | ✅ 本 SPEC 定义 | — |
| **凭证** REVERSED/REJECTED 字段 | ✅ 本 SPEC 定义 | — |
| **销售/采购发票**状态 | ❌ 不涉及 | P21-a / P21-b |
| 凭证生成的现金折扣凭证 | ⚠️ 本 SPEC 提供 `generateReversalVoucher` 模式 | P21-b 调用 |
| 状态变更的审计 | ❌ 本 SPEC 仅 log.info | **P24** AOP |

---

## 6. 测试要点

| 测试场景 | 期望 |
|---------|------|
| DRAFT → SUBMITTED | submit 成功 |
| SUBMITTED 二次 submit | 抛 BusinessException |
| SUBMITTED → AUDITED | audit 成功 |
| AUDITED 二次 audit | 抛 BusinessException |
| SUBMITTED 驳回 + 原因 | status=DRAFT，rejectedReason 写入 |
| SUBMITTED 驳回无原因 | 抛 BusinessException |
| AUDITED → POSTED | post 成功 |
| POSTED 直接修改金额 | 数据库约束抛异常（CHECK + 已过账禁 UPDATE）|
| POSTED → 生成红字 | reversalId 返回，original.reversed_voucher_id 写入 |
| 已冲销凭证再冲销 | 抛 BusinessException |
| POSTED → 反过账 → AUDITED | unpost 成功 |

**Mockito 单测要求**：≥10 @Test，参考 `huicai-java-backend` skill §9 R5 + §12 P9 模式。

---

## 7. API 变更

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/vouchers/{id}/submit` | 提交 |
| POST | `/api/v1/vouchers/{id}/audit` | 审核通过 |
| POST | `/api/v1/vouchers/{id}/reject` | 审核驳回 |
| POST | `/api/v1/vouchers/{id}/post` | 过账 |
| POST | `/api/v1/vouchers/{id}/unpost` | 反过账 |
| POST | `/api/v1/vouchers/{id}/reversal` | 生成红字 |

---

## 8. 不做事项

- ❌ 不修改 VoucherEntity.status 的 4 状态值（沿用现有）
- ❌ 不实现"POSTED 后允许修改"的反向（业务铁律）
- ❌ 不实现凭证模板的批量提交
- ❌ 不实现跨期凭证的强制阻断（账期控制由后续 SPEC 处理）
- ❌ 不实现凭证打印/导出

---

## 9. 后续依赖

- **依赖 P21-b**：`InputInvoiceStateMachineService.onReconciliationUpdate` 调用本 SPEC 的 `generateReversalVoucher` 或 `generateCashDiscountVoucher`
- **依赖 P24**：上线时把 log.info 替换为 audit_log 自动写入
- **未来**：账期控制 SPEC 需要校验 `voucher.period` 字段（VoucherEntity 已存在）