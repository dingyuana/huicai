# P22 SPEC — 凭证状态机扩展规格书
> **版本**：V1.0 | **最后修改**：2026-07-19 | **作者**：Hermes
> **状态**：✅ 生效

> **编号**：HUICAI-SPC-022 | 优先级：高（P22）
> 依据：`docs/需求分析书_发票与凭证状态机_V1.0.md` §3.2 凭证状态机
> 目标：扩展 `VoucherEntity` 增加 rejected_reason/reverse_reason 字段，支持 5 状态数据
> 工期：单批交付，3 个 commit
> 核心决策：`VoucherEntity.status` 5 状态（DRAFT/SUBMITTED/AUDITED/POSTED/CLOSED），REJECTED/REVERSED 作为**附属字段**（详见需求文档 §3.2 决议）
>
> **2026-06-22 修订内容**：
> - V41 → V47（V41-V44 + V46 已被占用）
> - 删 `reversed_voucher_id`（用现 `reversedFrom` 字段）
> - 删 `reversal_pair_id`（业务无需求，代码无引用）
> - 加 `reverse_reason`（红冲原因，便于审计）
> - 保留 `rejected_reason`（驳回原因，便于审计）
>
> **2026-07-01 P22 偏差说明**（审计发现）：
> - 2026-07-09 代码新增 CLOSED 状态，SPEC 未同步更新
> **2026-07-18 路由修正**：SPEC §7 API 中红冲路由 `/reversal` 与代码实现不符。实际代码为 `POST /api/v1/vouchers/{id}/reverse`（Controller 定义 + 前端调用一致），已更新 SPEC 匹配代码。

---

> **关联需求**: REQ-2026-007

## SDD 四段结构索引

### 1. 输入契约
→ 见本文 [## 1. 枚举常量（VoucherStatus 常量类）](#1-枚举常量) 及 [## 2. 实体变更（VoucherEntity 新增字段）](#2-实体变更)

### 2. 输出契约
→ 见本文 [## 6. 测试要点（测试场景/验收条件）](#6-测试要点) 及 [## 7. API 变更](#7-api-变更)

### 3. 状态流转
→ 见本文 MACHINE-READABLE CONTRACT 中的 states/transitions（DRAFT/SUBMITTED/AUDITED/POSTED/CLOSED 5 态）

### 4. 异常处理
→ 见本文 [§4.2 关键实现骨架（BusinessException 抛出点）](#42-关键实现骨架)

---

## 0. 改动清单总览

| # | 改动 | 文件 | 风险 |
|---|------|------|------|
| 1 | 更新 `VoucherStatus` 常量类（封装 5 状态 + CLOSED 结账方法）| `backend/.../finance/constant/VoucherStatus.java` | ✅ 低 |
| 2 | `VoucherEntity` 新增 2 字段：`rejected_reason` / `reverse_reason`（`reversedFrom` 已存在不新增）| Entity 文件 | 🟡 中 |
| 3 | V47 迁移: t_voucher 加 2 字段（无 CHECK 约束，2 字段都是 nullable text）| Flyway | 🟡 中 |
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
 * 核心 5 状态（status 字段）+ REJECTED/REVERSED 作为附属字段.
 * 详见 docs/specs/P22-voucher-state-machine.md
 * 2026-06-22 P22 创建 | 2026-07-09 新增 CLOSED 状态
 */
public final class VoucherStatus {

    private VoucherStatus() {}

    // ====== status 字段 5 状态 ======
    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String AUDITED = "AUDITED";
    public static final String POSTED = "POSTED";
    public static final String CLOSED = "CLOSED";

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
    /** 仅 POSTED 可结账 */
    public static boolean isClosable(String status) {
        return POSTED.equals(status);
    }
    public static boolean isPosted(String status) {
        return POSTED.equals(status) || CLOSED.equals(status);
    }
    public static boolean isModifiable(String status) {
        // POSTED 和 CLOSED 不可修改（铁律）
        return !POSTED.equals(status) && !CLOSED.equals(status);
    }
    public static boolean isReversible(String status) {
        // POSTED 和 CLOSED 均可冲销
        return POSTED.equals(status) || CLOSED.equals(status);
    }
}
```

---

## 2. 实体变更

### 2.1 `VoucherEntity` 新增字段

**位置**：现有 87 行后追加（不破坏现有字段）。

```java
// ========== P22 新增字段（2026-06-22 修订：只加 2 字段）==========

/** 驳回原因（仅在 SUBMITTED → DRAFT 反向时记录） */
private String rejectedReason;

/** 红冲原因（生成红字凭证时记录，便于审计） */
private String reverseReason;

// 注：reversedFrom 字段已存在 (V41 之前已加), 不再新增
// 注：rejectedAt / rejectedBy / reversedVoucherId / reversalPairId 在 2026-06-22 修订中删除（业务无需求）
```

### 2.2 字段语义说明

| 字段 | 何时写入 | 与 status 关系 |
|:---|:---|:---|
| `rejectedReason` | SUBMITTED 驳回时 | status 从 SUBMITTED 回退到 DRAFT |
| `reverseReason` | 生成红字凭证时 | 红字凭证 status=DRAFT, source=REVERSAL |
| `reversedFrom` | 生成红字凭证时（已有字段）| 红字凭证 reversedFrom 指向原凭证 |

---

## 3. Flyway 迁移（V47）

```sql
-- V47__add_voucher_rejected_reverse_reason.sql
-- 2026-06-22 P22 实施 (修订版: 只加 2 字段, 不动 status CHECK 约束)
-- 依据: docs/specs/P22-voucher-state-machine.md (2026-06-22 修订)

-- 1. 加 2 个新字段 (text 类型, nullable)
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS rejected_reason VARCHAR(500);
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS reverse_reason VARCHAR(500);

-- 注: status 字段 CHECK 约束不变 (4 状态 DRAFT/SUBMITTED/AUDITED/POSTED, V8 已建)
-- 注: reversedFrom 字段已存在 (V41 之前已加), 不动

-- 2. 索引 (前端按驳回原因/红冲原因查询场景罕见, 不加索引, 避免冗余)
-- 如未来查询压力大再加: CREATE INDEX idx_t_voucher_rejected_reason ON t_voucher(rejected_reason) WHERE rejected_reason IS NOT NULL;

-- 3. COMMENT 更新
COMMENT ON COLUMN t_voucher.rejected_reason IS
    '驳回原因: SUBMITTED 驳回时记录, status 回退到 DRAFT (2026-06-22 P22)';
COMMENT ON COLUMN t_voucher.reverse_reason IS
    '红冲原因: 生成红字凭证时记录, 红字凭证 source=REVERSAL (2026-06-22 P22)';

-- 4. 校验已有数据
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
| **凭证**核心 5 状态 | ✅ 本 SPEC 定义 | — |
| **凭证** CLOSED 结账 | ✅ 本 SPEC 定义（POSTED → CLOSED） | — |
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
| POSTED → CLOSED | close 成功，status=CLOSED |
| CLOSED 修改 | 抛 BusinessException |
| CLOSED 红冲 | 可正常生成红字凭证 |

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
| POST | `/api/v1/vouchers/{id}/close` | 结账 |
| POST | `/api/v1/vouchers/{id}/reverse` | 生成红字 |

---

## 8. 不做事项

- ❌ 不修改 VoucherEntity.status 的 5 状态值（沿用现有）
- ❌ 不实现"POSTED 后允许修改"的反向（业务铁律）
- ❌ 不实现凭证模板的批量提交
- ❌ 不实现跨期凭证的强制阻断（账期控制由后续 SPEC 处理）
- ❌ 不实现凭证打印/导出

---

## 9. 后续依赖

- **依赖 P21-b**：`InputInvoiceStateMachineService.onReconciliationUpdate` 调用本 SPEC 的 `generateReversalVoucher` 或 `generateCashDiscountVoucher`
- **依赖 P24**：上线时把 log.info 替换为 audit_log 自动写入
- **未来**：账期控制 SPEC 需要校验 `voucher.period` 字段（VoucherEntity 已存在）
---

# MACHINE-READABLE CONTRACT

```yaml
contract_version: "1.0"
spec_file: "P22-voucher-state-machine.md"
spec_id: P22
entity: VoucherEntity
module: finance
table: t_voucher
last_updated: "2026-07-18"
implementation_status: implemented
deviation_score: 10%

# P22 偏差说明：
# - VoucherStateMachineService 仅为状态检查（assert 方法），不含状态变更
# - 状态变更方法在 VoucherServiceImpl 中实现
# - 红冲方法名从 generateReversalVoucher 改为 reverse
# - 红冲路由从 /reversal 改为 /reverse（SPEC §7 已同步修正）
# - 红字凭证状态为 POSTED（非 DRAFT）
# - rejectedReason/reverseReason 字段存在（V47 migration 已落地）
# - CLOSED 状态及 close 方法已在代码中完整实现（2026-07-09），SPEC 已同步更新

deviations:
  - "§4.1: VoucherStateMachineService 接口仅有 assert 方法，无 submit/audit/post/unpost/reverse"
  - "§4.2: 实现骨架位置错误，方法在 VoucherServiceImpl 而非 VoucherStateMachineService"
  - "reverse() 不返回 Long，红字凭证直接 POSTED（非 DRAFT）"

states:
  DRAFT:
    description: "草稿，未提交"
    initial: true
    terminal: false

  SUBMITTED:
    description: "已提交，待审核"
    initial: false
    terminal: false

  AUDITED:
    description: "已审核"
    initial: false
    terminal: false

  POSTED:
    description: "已过账"
    initial: false
    terminal: false

  CLOSED:
    description: "已结账"
    initial: false
    terminal: true

transitions:
  - id: T-01
    from: DRAFT
    to: SUBMITTED
    trigger: submit
    precondition: "status == DRAFT"
    postcondition: "status == SUBMITTED"
    side_effects: []
    test_ref: submit_positive

  - id: T-02
    from: SUBMITTED
    to: AUDITED
    trigger: audit
    precondition: "status == SUBMITTED"
    postcondition: "status == AUDITED; auditedBy = userId; auditedAt = now"
    side_effects: []
    test_ref: audit_positive

  - id: T-03
    from: SUBMITTED
    to: DRAFT
    trigger: reject
    precondition: "status == SUBMITTED"
    postcondition: "status == DRAFT; reason recorded"
    side_effects: []
    test_ref: reject_positive

  - id: T-04
    from: AUDITED
    to: POSTED
    trigger: post
    precondition: "status == AUDITED"
    postcondition: "status == POSTED"
    side_effects: []
    test_ref: post_positive

  - id: T-05
    from: POSTED
    to: POSTED
    trigger: generateReversalVoucher
    precondition: "status == POSTED"
    postcondition: "new reversal voucher created (POSTED); original voucher stays POSTED"
    side_effects:
      - entity: VoucherEntity
        action: create_reversal
        status: POSTED
    test_ref: generateReversalVoucher_positive
    note: "This is a creation transition, not a state change. The original voucher remains POSTED."

  - id: T-06
    from: AUDITED
    to: SUBMITTED
    trigger: unpost
    precondition: "status == AUDITED"
    postcondition: "status == SUBMITTED"
    side_effects: []
    test_ref: unpost_positive

  - id: T-07
    from: POSTED
    to: CLOSED
    trigger: close
    precondition: "status == POSTED"
    postcondition: "status == CLOSED"
    side_effects: []
    test_ref: close_positive

constraints:
  - id: C-01
    type: database
    rule: "CHECK constraint on t_voucher.status"
    migration: V47

  - id: C-02
    type: immutability
    rule: "已过账/已结账凭证不可修改，只能红冲"
    enforcement: "StateMachineService 前置检查"

  - id: C-03
    type: audit
    rule: "审核/过账必须记录 auditedBy/auditedAt"
    enforcement: "State machine service 设置审计字段"

acceptance_tests:
  - id: AT-001
    description: "DRAFT → SUBMITTED"
    method: submit_positive
    assertion: "status == SUBMITTED"
    status: covered

  - id: AT-002
    description: "SUBMITTED → AUDITED"
    method: audit_positive
    assertion: "status == AUDITED; auditedBy set"
    status: covered

  - id: AT-003
    description: "SUBMITTED → DRAFT (驳回)"
    method: reject_positive
    assertion: "status == DRAFT"
    status: covered

  - id: AT-004
    description: "AUDITED → POSTED"
    method: post_positive
    assertion: "status == POSTED"
    status: covered

  - id: AT-005
    description: "POSTED 不可修改，只能红冲"
    method: modify_posted_voucher_throws
    assertion: "BusinessException"
    status: covered

  - id: AT-006
    description: "红冲生成新凭证 (POSTED)"
    method: reverse_positive
    assertion: "original status stays POSTED + reversedFrom set; new voucher created with POSTED status"
    status: covered
    deviation: "SPEC 原写 DRAFT，实际红字凭证直接 POSTED。方法名从 generateReversalVoucher 改为 reverse。" 

  - id: AT-007
    description: "POSTED → CLOSED (结账)"
    method: close_positive
    assertion: "status == CLOSED"
    status: covered 

out_of_scope:
  - "OutputInvoiceEntity 状态机 (P21 范围)"
  - "ReceivableEntity/PayableEntity 状态机 (P20 范围)"

dependencies:
  - spec: P21
    entity: OutputInvoiceEntity
    relation: "销售发票审核触发凭证创建 (DRAFT)"
  - spec: P24
    entity: AuditLog
    relation: "审计日志"
```

---

## BDD 验收标准

### 场景 1：凭证提交审核后状态正常流转
**Given** 一张状态为 `DRAFT` 的凭证
**When** 调用 `submit(voucherId, userId)`
**Then** 凭证状态变为 `SUBMITTED`

### 场景 2：已过账凭证红冲生成红字凭证
**Given** 一张状态为 `POSTED` 的凭证
**When** 调用 `generateReversalVoucher(originalVoucherId, userId)`
**Then** 原凭证 `status` 保持 `POSTED`，`reversedFrom` 指向新生成的凭证，新凭证 `status = POSTED`

### 场景 3：非法状态流转被 BusinessException 拦截
**Given** 一张状态为 `AUDITED` 的凭证
**When** 尝试调用 `submit(voucherId, userId)`
**Then** 系统抛出 `BusinessException`，提示"仅 DRAFT 状态可提交"

### 场景 4：已过账凭证结账
**Given** 一张状态为 `POSTED` 的凭证
**When** 调用 `close(voucherId, userId)`
**Then** 凭证状态变为 `CLOSED`

### 场景 5：已结账凭证不可修改
**Given** 一张状态为 `CLOSED` 的凭证
**When** 尝试修改凭证内容
**Then** 系统抛出 `BusinessException`，提示不可修改

### 场景 6：已结账凭证可红冲
**Given** 一张状态为 `CLOSED` 的凭证
**When** 调用 `reverse(voucherId, userId)`
**Then** 红冲凭证创建成功，原凭证保持 `CLOSED` 状态
