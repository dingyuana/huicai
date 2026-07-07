# P20 SPEC — AR/AP 状态机实现规格书（待废弃）

> **编号**：HUICAI-SPC-020 | 优先级：低
> 依据：docs/design/P20-arap-state-machine-design.md
> **2026-07-01 标注**：P34 架构已决定将应收/应付合并到业务单据体系，
> ReceivableEntity/PayableEntity 将在 P34 M4（V72）阶段删除。
> 本 SPEC 仅在 P34 完全实施前作为历史参考，不再维护。
> 核心流程已迁移到 P34 SPEC 和 BusinessDocEntity。

---

> **关联需求**: REQ-2026-015
## 0. P34 架构变更说明

P34 决定将应收/应付合并到业务单据体系：
- 销售发票审核 → 创建 `BusinessDocEntity(INVOICE_OUT)` + 凭证，**不再创建独立 `ReceivableEntity`**
- 采购发票导入 → 创建 `BusinessDocEntity(INVOICE_IN)` + `PayableEntity`（**双写，待清理**）
- 核销结算 → 同时更新 `ReceivableEntity`、`PayableEntity`、`BusinessDocEntity` 三处（**三写，待统一**）
- V68 migration 已为 `t_business_doc` 增加 `settled_amount/unsettled_amount/due_date` 字段

**当前代码实际状态**：
- `ReceivableStateMachineService` / `PayableStateMachineService` 存在但功能有限（仅 confirm/onReconciliationUpdate/reverse）
- `ReceivableEntity` / `PayableEntity` 仍有 16 个生产文件引用，未被移除
- `t_receivable` / `t_payable` 表仍存在（P34 尚未执行 V71 删除）

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

### 1.2 ⚠️ 常量类与实际代码偏差

| 问题 | 说明 | 修复建议 |
|------|------|----------|
| `PARTIALLY_SETTLED` 未定义 | SPEC 定义了这个状态但常量类中没有 | P34 后应收/应付不再独立管理核销状态，由 BusinessDocEntity 的 `PARTIALLY_RECONCILED` 替代 |
| `isReversible()` 不包含 `VOUCHERED` | SPEC T-07 说 CONFIRMED/VOUCHERED/PARTIALLY_SETTLED 可冲销 | 当前代码 `isReversible()` 只检查 CONFIRMED/SETTLED。P34 后冲销通过 BusinessDocService 处理 |
| `CANCELLED` 常量存在但无 `cancel()` 方法 | ArapStatus 定义了 CANCELLED 但 ReceivableServiceImpl/PayableServiceImpl 无此方法 | P34 后取消操作合并到 BusinessDocService.cancel() |
| `EXECUTED`/`REJECTED`/`APPLIED` 未使用 | 这些是为 ReconciliationLog/Prepayment 预留的状态 | 当前未被任何代码引用 |

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

## 4. Service 状态机实现 — 当前实际 vs SPEC 定义

### 4.1 偏差总览

| SPEC 定义的方法 | 代码实际 | 偏差 |
|----------------|----------|------|
| `submit()` (DRAFT→SUBMITTED) | **不存在** | 🔴 |
| `confirm()` (DRAFT→CONFIRMED) | ✅ `ReceivableStateMachineServiceImpl.confirm()` | ✅ 一致（但跳过了 SUBMITTED） |
| `reject()` (SUBMITTED→DRAFT) | **不存在** | 🔴 |
| `generateVoucher()` (CONFIRMED→VOUCHERED) | **不存在** | 🔴 |
| `markSettled()` (VOUCHERED→SETTLED) | ✅ `ReceivableServiceImpl.markSettled()` | ⚠️  Preconditions 不同：SPEC 要求 VOUCHERED，代码要求 CONFIRMED |
| `onReconciliationUpdate()` | ✅ `ReceivableStateMachineServiceImpl.onReconciliationUpdate()` | ✅ 一致 |
| `reverse()` | ✅ `ReceivableStateMachineServiceImpl.reverse()` | ✅ 一致 |
| `cancel()` (DRAFT→CANCELLED) | **不存在** | 🔴 |

### 4.2 当前实际实现

**`ReceivableStateMachineService`**（3 个方法）：
- `confirm()` — DRAFT → CONFIRMED（一步到位，跳过 SUBMITTED）
- `onReconciliationUpdate()` — CONFIRMED/SETTLED → SETTLED（unsettledAmount=0 时）
- `reverse()` — CONFIRMED/SETTLED → REVERSED

**`ReceivableService`**（额外方法）：
- `markSettled()` — CONFIRMED → SETTLED（要求 unsettledAmount=0）
- `confirm()` — 与 StateMachineService 重复实现（DRAFT → CONFIRMED）

**`PayableStateMachineService`** — 与 Receivable 对称，完全相同的方法集合。

### 4.3 核销扣减时同步状态（已实现）

```java
// ReceivableStateMachineServiceImpl.onReconciliationUpdate():
String newStatus = unsettledAmount.compareTo(BigDecimal.ZERO) == 0
        ? ArapStatus.SETTLED
        : ArapStatus.CONFIRMED;
entity.setStatus(newStatus);
```

### 4.4 待 P34 完成的事项

- [ ] 应收/应付状态机逻辑迁移到 `BusinessDocService`
- [ ] `ArapSettlementServiceImpl` 统一操作 `BusinessDocEntity`（当前三写：Receivable+Payable+BusinessDoc）
- [ ] 删除 `ReceivableEntity`/`PayableEntity` 及所有引用
- [ ] 删除 `t_receivable`/`t_payable` 表（V71 migration）
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

---

# MACHINE-READABLE CONTRACT

```yaml
contract_version: "1.0"
spec_file: "P20-arap-state-machine-spec.md"
spec_id: P20
entity: ReceivableEntity,PayableEntity,BusinessDocEntity
module: arap,finance
table: t_receivable,t_payable,t_business_doc
last_updated: "2026-07-01"
implementation_status: partial
deviation_score: 65%

# P34 架构变更后，应收/应付状态机已部分迁移到 BusinessDocEntity
# 当前代码中 ReceivableEntity/PayableEntity 仍被 16 个生产文件引用
# 待 P34 完成后，应收/应付状态机将被 BusinessDoc 状态机取代
# V68 migration 已为 t_business_doc 增加 settled/unsettled/due_date 字段

deviations:
  - "T-01/T-03 submit/reject: 未实现，confirm() 一步到位 DRAFT→CONFIRMED"
  - "T-04 generateVoucher: 未实现，应收单不直接生成凭证"
  - "T-05 markSettled precondition: 代码为 CONFIRMED，SPEC 为 VOUCHERED"
  - "T-06 PARTIALLY_SETTLED: 未实现，代码保持 CONFIRMED"
  - "T-08 cancel: 未实现，取消合并到 BusinessDocService"
  - "reverse() 不创建新的反向应收单，仅修改自身状态为 REVERSED"

business_doc_integration:
  - "doc_type: INVOICE_OUT → 销售发票审核后创建，status=APPROVED"
  - "doc_type: INVOICE_IN → 采购发票导入后创建，status=APPROVED"
  - "BusinessDocEntity 有独立状态机: DRAFT/SUBMITTED/APPROVED/VOUCHERED/PARTIALLY_RECONCILED/FULLY_RECONCILED/CLOSED/REJECTED/REVERSED"
  - "核销结算同时更新 ReceivableEntity/PayableEntity/BusinessDocEntity（三写）" 

states:
  DRAFT:
    description: "草稿，待提交"
    initial: true
    terminal: false
    note: "SPEC 定义，但当前代码中 Receivable/Payable 创建时直接 CONFIRMED，不走 DRAFT"

  SUBMITTED:
    description: "已提交，待审核"
    initial: false
    terminal: false
    note: "⚠️ SPEC 定义但未实现。代码中 confirm() 直接从 DRAFT→CONFIRMED（一步）"

  CONFIRMED:
    description: "已审核通过"
    initial: false
    terminal: false
    note: "✅ 当前实际初始状态。发票导入/流水生成的应收单直接 CONFIRMED"

  VOUCHERED:
    description: "已生成凭证"
    initial: false
    terminal: false
    note: "⚠️ SPEC 定义但未实现。应收/应付不直接生成凭证，由凭证模块独立处理"

  SETTLED:
    description: "已核销"
    initial: false
    terminal: true
    note: "✅ 通过 onReconciliationUpdate(markSettled) 实现，unsettledAmount=0 时自动标记"

  PARTIALLY_SETTLED:
    description: "部分核销"
    initial: false
    terminal: false
    note: "⚠️ SPEC 定义但未实现。代码中 unsettledAmount>0 时保持 CONFIRMED，不切到 PARTIALLY_SETTLED"

  REJECTED:
    description: "已驳回"
    initial: false
    terminal: false
    note: "⚠️ 常量存在但无 reject() 方法。应收单无驳回流程"

  CANCELLED:
    description: "已取消"
    initial: false
    terminal: true
    note: "⚠️ 常量存在但无 cancel() 方法。取消操作合并到 BusinessDocService"

  REVERSED:
    description: "已红冲"
    initial: false
    terminal: true
    note: "✅ 通过 reverse() 实现，需传入 reason 参数"

# P34 架构变更后，应收/应付的状态机已部分迁移到 BusinessDocEntity：
# BusinessDocEntity 状态: DRAFT/SUBMITTED/APPROVED/VOUCHERED/PARTIALLY_RECONCILED/FULLY_RECONCILED/CLOSED/REJECTED/REVERSED
# INVOICE_OUT/INVOICE_IN 类型走 BusinessDoc 状态机，不走独立的 Receivable/Payable 状态机

transitions:
  - id: T-01
    from: DRAFT
    to: SUBMITTED
    trigger: submit
    precondition: "status == DRAFT"
    postcondition: "status == SUBMITTED"
    side_effects: []
    test_ref: submit_positive
    deviation: "❌ SPEC 定义但未实现。代码无 submit() 方法。"

  - id: T-02
    from: SUBMITTED
    to: CONFIRMED
    trigger: confirm
    precondition: "status == SUBMITTED"
    postcondition: "status == CONFIRMED; auditedBy = userId"
    side_effects: []
    test_ref: confirm_positive
    deviation: "❌ 代码中 confirm() 从 DRAFT 直接到 CONFIRMED，跳过 SUBMITTED"

  - id: T-03
    from: SUBMITTED
    to: DRAFT
    trigger: reject
    precondition: "status == SUBMITTED"
    postcondition: "status == DRAFT; reason recorded"
    side_effects: []
    test_ref: reject_positive
    deviation: "❌ SPEC 定义但未实现。应收单无驳回流程。"

  - id: T-04
    from: CONFIRMED
    to: VOUCHERED
    trigger: generateVoucher
    precondition: "status == CONFIRMED"
    postcondition: "status == VOUCHERED; voucherId recorded"
    side_effects:
      - entity: VoucherEntity
        action: create
        status: DRAFT
    test_ref: generateVoucher_positive
    deviation: "❌ SPEC 定义但未实现。应收单不直接生成凭证，由凭证模块独立处理。"

  - id: T-05
    from: VOUCHERED
    to: SETTLED
    trigger: markSettled
    precondition: "status == VOUCHERED && unsettledAmount == 0"
    postcondition: "status == SETTLED"
    side_effects: []
    test_ref: markSettled_fully
    deviation: "⚠️ 代码实现为 CONFIRMED→SETTLED，非 VOUCHERED→SETTLED"

  - id: T-06
    from: VOUCHERED
    to: PARTIALLY_SETTLED
    trigger: onSettlementUpdate
    precondition: "status == VOUCHERED && unsettledAmount > 0"
    postcondition: "status == PARTIALLY_SETTLED"
    side_effects: []
    test_ref: onSettlementUpdate_partial
    deviation: "❌ 代码中 unsettledAmount>0 时保持 CONFIRMED，不切到 PARTIALLY_SETTLED"

  - id: T-07
    from: CONFIRMED
    to: REVERSED
    trigger: reverse
    precondition: "status == CONFIRMED || status == VOUCHERED || status == PARTIALLY_SETTLED"
    postcondition: "status = REVERSED; new reversed AR/AP created"
    side_effects:
      - entity: ReceivableEntity/PayableEntity
        action: create_reversed
        status: DRAFT
    test_ref: reverse_positive
    deviation: "⚠️ 代码实现 CONFIRMED/SETTLED→REVERSED，不创建新的反向应收单"

  - id: T-08
    from: DRAFT
    to: CANCELLED
    trigger: cancel
    precondition: "status == DRAFT"
    postcondition: "status == CANCELLED"
    side_effects: []
    test_ref: cancel_positive
    deviation: "❌ SPEC 定义但未实现。取消操作合并到 BusinessDocService"

  - id: T-09 (新)
    from: DRAFT
    to: CONFIRMED
    trigger: confirm
    precondition: "status == DRAFT"
    postcondition: "status == CONFIRMED; auditedBy = userId; auditedAt = now()"
    side_effects: []
    test_ref: confirm_positive
    deviation: "✅ 当前代码实际实现（一步到位）"

  - id: T-10 (新)
    from: CONFIRMED
    to: SETTLED
    trigger: markSettled/onReconciliationUpdate
    precondition: "status == CONFIRMED && unsettledAmount == 0"
    postcondition: "status == SETTLED"
    side_effects: []
    test_ref: markSettled_fully
    deviation: "✅ 当前代码实际实现"

  - id: T-11 (新)
    from: CONFIRMED
    to: REVERSED
    trigger: reverse
    precondition: "status == CONFIRMED || status == SETTLED"
    postcondition: "status == REVERSED"
    side_effects: []
    test_ref: reverse_positive
    deviation: "✅ 当前代码实际实现"

  - id: T-12 (新)
    from: CONFIRMED
    to: CONFIRMED
    trigger: onReconciliationUpdate
    precondition: "status == CONFIRMED && unsettledAmount > 0"
    postcondition: "status == CONFIRMED; settledAmount += delta; unsettledAmount -= delta"
    side_effects: []
    test_ref: reconciliation_update_partial
    deviation: "✅ 当前代码实际实现（保持 CONFIRMED，不切 PARTIALLY_SETTLED）" 

constraints:
  - id: C-01
    type: database
    rule: "CHECK constraint on t_receivable/t_payable.status"
    migration: V37

  - id: C-02
    type: business
    rule: "审核必须由人确定"
    enforcement: "StateMachineService 方法"

  - id: C-03
    type: immutability
    rule: "终态不可再转换"
    enforcement: "InvoiceStatus.isTerminal() 前置检查"

acceptance_tests:
  - id: AT-001
    description: "DRAFT → SUBMITTED"
    method: submit_positive
    assertion: "status == SUBMITTED"
    status: not_implemented
    deviation: "SPEC 定义但未实现"

  - id: AT-002
    description: "SUBMITTED → CONFIRMED"
    method: confirm_positive
    assertion: "status == CONFIRMED"
    status: not_implemented
    deviation: "代码从 DRAFT 直接到 CONFIRMED"

  - id: AT-003
    description: "SUBMITTED → DRAFT (驳回)"
    method: reject_positive
    assertion: "status == DRAFT; reason recorded"
    status: not_implemented
    deviation: "应收单无驳回流程"

  - id: AT-004
    description: "CONFIRMED → VOUCHERED"
    method: generateVoucher_positive
    assertion: "status == VOUCHERED; voucherId recorded"
    status: not_implemented
    deviation: "应收单不直接生成凭证"

  - id: AT-005
    description: "CONFIRMED → SETTLED (全额)"
    method: markSettled_fully
    assertion: "status == SETTLED; unsettledAmount == 0"
    status: covered
    deviation: "代码实现为 CONFIRMED→SETTLED，非 VOUCHERED→SETTLED"

  - id: AT-006
    description: "CONFIRMED → CONFIRMED (部分核销)"
    method: onReconciliationUpdate_partial
    assertion: "status == CONFIRMED; settledAmount += delta; unsettledAmount -= delta"
    status: covered
    deviation: "代码保持 CONFIRMED，不切到 PARTIALLY_SETTLED"

  - id: AT-007
    description: "终态不可再转换"
    method: transition_from_terminal_state_throws
    assertion: "BusinessException"
    status: covered
    deviation: "REVERSED 为终态，不可再转换"

  - id: AT-008 (新)
    description: "CONFIRMED → REVERSED"
    method: reverse_positive
    assertion: "status == REVERSED"
    status: covered
    deviation: "✅ 当前代码实现"

  - id: AT-009 (新)
    description: "SETTLED → REVERSED"
    method: reverse_from_settled
    assertion: "status == REVERSED"
    status: covered
    deviation: "✅ 当前代码实现"

  - id: AT-010 (新)
    description: "REVERSED 不可再转换"
    method: reverse_from_reversed_throws
    assertion: "BusinessException"
    status: covered
    deviation: "✅ 当前代码实现" 

out_of_scope:
  - "InputInvoiceEntity (采购发票，P21-b 已废弃)"
  - "VoucherEntity 状态机 (P22 范围)"

dependencies:
  - spec: P21
    entity: OutputInvoiceEntity
    relation: "销售发票审核创建应收单 (DRAFT)"
  - spec: P22
    entity: VoucherEntity
    relation: "生成凭证"
  - spec: P36
    entity: OutputInvoiceEntity
    relation: "红冲级联"
```
