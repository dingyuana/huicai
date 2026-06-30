# P23 SPEC — 银行流水对账单状态机规格书

> 状态：**已实现** | 优先级：高（P23）
> 依据：`docs/需求分析书_发票与凭证状态机_V1.0.md` §3.3 银行流水状态机
> 目标：消除 `reviewStatus` magic string，建立完整的银行流水状态机检查体系
> 工期：单批交付，3 个 commit

---

## 0. 改动清单总览

| # | 改动 | 文件 | 风险 |
|---|------|------|------|
| 1 | 创建 `StatementStatus` 常量类 | `backend/.../finance/constant/StatementStatus.java` | ✅ 低 |
| 2 | `BankStatementServiceImpl` 各 state machine 方法统一使用常量 | Service 文件 | ✅ 低 |
| 3 | `BankStatementEntity` 无字段变更 | Entity 文件 | ✅ 无 |
| 4 | 单测：完整正向+负向断言覆盖（≥20 @Test）| Test 文件 | ✅ 低 |
| 5 | `AGENTS.md §4 陷阱库` 登记此次修复教训 | AGENTS.md | ✅ 低 |

---

## 1. 状态枚举

### 1.1 `StatementStatus` 常量类

**路径**: `com.huicai.module.finance.constant.StatementStatus`

```java
package com.huicai.module.finance.constant;

/**
 * 银行流水对账单 reviewStatus 状态常量.
 * 使用 String 常量而非 Enum，保持与数据库 VARCHAR 兼容。
 */
public final class StatementStatus {

    private StatementStatus() {}

    // ====== 初始状态（导入时设置）======
    /** CSV 导入: 不设 reviewStatus（null） */
    /** Excel 导入: 自动设为 PENDING */

    // ====== 审核前 ======
    public static final String PENDING = "PENDING";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String UNCONFIRMED = "UNCONFIRMED";

    // ====== 审核通过（自动生成凭证+单据） ======
    public static final String CONFIRMED = "CONFIRMED";

    // ====== 生成结果 ======
    public static final String VOUCHER_GENERATED = "voucher_generated";
    public static final String PAYMENT_CREATED = "payment_created";

    // ====== 最终 ======
    public static final String APPROVED = "approved";

    // ====== C 类人工处理 ======
    public static final String MANUAL_PENDING = "manual_pending";

    // ====== 检查方法 ======

    /** 是否可以出纳确认（review） */
    public static boolean isReviewable(String status) {
        return status == null
                || PENDING.equals(status)
                || "classified".equals(status)   // 旧状态值，兼容
                || MANUAL_PENDING.equals(status)
                || "RECLASSIFIED".equals(status); // 旧状态值，兼容
    }

    /** 是否可以主管审核（audit） */
    public static boolean isAuditable(String status) {
        return CONFIRMED.equals(status);
    }

    /** 是否可以制证（generateVoucher，恢复/重试场景：接受 CONFIRMED 和新旧过渡期的 AUDITED） */
    public static boolean isGeneratable(String status) {
        return CONFIRMED.equals(status) || "AUDITED".equals(status);
    }

    /** 是否可以核准（approve） */
    public static boolean isApprovalable(String status) {
        return VOUCHER_GENERATED.equals(status) || PAYMENT_CREATED.equals(status);
    }

    /** 是否已锁定不可修改 */
    public static boolean isLocked(String status) {
        return CONFIRMED.equals(status)
                || VOUCHER_GENERATED.equals(status)
                || PAYMENT_CREATED.equals(status)
                || APPROVED.equals(status);
    }
}
```

---

## 2. 状态转换图

```
                    导入
                    │
                    ▼
              null / PENDING
                    │
                    │  classifySingle()
                    ▼
              classified (隐含)
                    │
                    │  review()   ← 出纳确认
                    ▼
               CONFIRMED
                    │
                    │  audit() + autoGenerate  ← 主管会计审核
                    │  （内部调用 autoGenerateInNewTx 生成凭证+单据草稿）
                    ▼
     ┌──────────────────────────────┐
     │  A 类           │  B 类       │
     │ voucher_generated │ payment_created │
     └────────┬─────────────────────┘
              │  approve()  ← 主管会计核准
              ▼
           approved (终态)


C 类人工分支:
manual_pending ──┬── processManual(A) → voucher_generated ──→ approved
                 └── processManual(B) → payment_created  ──→ approved

可逆转换（需撤回机制）:
approved / voucher_generated / payment_created → （暂无撤回，需 P24 补充）
```

---

## 3. 状态转换表

| # | 方法 | 前置状态 | 后置状态 | 副作用 |
|---|------|----------|----------|--------|
| 1 | `review()` | null/PENDING/classified/manual_pending/RECLASSIFIED | CONFIRMED | 设置 reviewedBy/reviewedAt |
| 2 | `audit()` | CONFIRMED | voucher_generated / payment_created | 调 autoGenerationService.autoGenerateInNewTx()，生成凭证+单据（均为草稿） |
| 3 | `generateVoucher()` | CONFIRMED / AUDITED（旧数据） | voucher_generated / payment_created | 调 autoGenerationService.autoGenerateInNewTx()，恢复/重试场景 |
| 4 | `approve()` | voucher_generated / payment_created | approved | 无 |
| 5 | `processManual(A)` | manual_pending | voucher_generated | 调 autoGenerationService.autoGenerateInNewTx() |
| 6 | `processManual(B)` | manual_pending | payment_created | 调 autoGenerationService.autoGenerateInNewTx() |
| 7 | `updateClassification()` | 任意 | 不变（仅改 classification） | 无 |
| 8 | `deleteStatement()` | 任意 | 删除 | 无 |

---

## 4. Service 状态机方法契约

### 4.1 `review(statementId, userId)`

```
前置: stmt != null
前置: classification 非空
前置: reviewStatus ∈ {null, PENDING, classified, manual_pending, RECLASSIFIED}
后置: reviewStatus = CONFIRMED
后置: reviewedBy = userId, reviewedAt = now()
负向: 不调用 autoGenerationService.autoGenerateInNewTx()
负向: 不创建任何凭证/业务单据
```

### 4.2 `audit(statementId, userId)`

```
前置: stmt != null
前置: reviewStatus = CONFIRMED
前置: classification 已设置（用于确定 A/B 路由）
后置: reviewStatus = voucher_generated (A类) / payment_created (B类)
后置: autoGenerationService.autoGenerateInNewTx() 被调用，生成凭证+业务单据（均为草稿状态）
角色: 财务会计操作，出纳确认后触发
```

### 4.3 `generateVoucher(statementId, userId)` [恢复/重试专用]

```
前置: stmt != null
前置: reviewStatus = CONFIRMED（新流程重试）或 AUDITED（旧数据过渡）
后置: reviewStatus = voucher_generated (A类) / payment_created (B类)
后置: autoGenerationService.autoGenerateInNewTx() 被调用
说明: 主流程已合并至 audit()，此方法仅用于生成失败后的恢复重试场景
```

### 4.4 `approve(statementId)`

```
前置: stmt != null
前置: reviewStatus ∈ {voucher_generated, payment_created}
后置: reviewStatus = approved
负向: 不调用 autoGenerationService.autoGenerateInNewTx()
负向: 不创建任何凭证/业务单据
```

### 4.5 `processManual(statementId, targetType, paymentType, userId)`

```
前置: stmt != null
前置: reviewStatus = manual_pending
前置: targetType ∈ {A, B}
后置: targetType=A → voucher_generated, targetType=B → payment_created
后置: autoGenerationService.autoGenerateInNewTx() 被调用
负向: processManual 不改其他字段
```

---

## 5. 测试契约（每方法必须覆盖）

### 5.1 正向断言（Positive）

每个状态方法至少有一个 happy-path test，验证：
- 后置状态正确
- 写入字段正确（reviewedBy, reviewedAt）
- side-effect 方法被正确调用

### 5.2 负向断言（Negative）

每个状态方法必须验证：

| 维度 | 断言方式 | 示例 |
|------|----------|------|
| 不存在 | `assertThrows` + message 含"不存在" | `review(99L)` → BusinessException |
| 前置状态不符 | `assertThrows` + message 含状态提示 | `audit()` 在 PENDING 状态时 |
| 不应有的副作用 | `verify(mapper, never()).insert(...)` | review 不应生成凭证 |
| 不应 update 错误数据 | `verify(statementMapper).updateById(captor.capture())` | 验证写入字段 |

### 5.3 全流程测试

一个端到端测试覆盖完整正向链路：
```
null → review() → CONFIRMED → audit() → AUDITED → generateVoucher() → voucher_generated → approve() → approved
```

---

## 6. 陷阱与经验（登记到 AGENTS.md §4）

### 6.1 已修复缺陷：review() 不应自动制证

**问题**：`review()` 曾耦合 `autoGenerationService.autoGenerateInNewTx()`，导致出纳确认时意外生成凭证，违反"凭证不可自动生成"铁律。

**修复**：
- `review()` 只改状态为 CONFIRMED，不触发生单
- `generateVoucher()` 独立端点，由主管审核后触发
- `audit()` 新增状态 AUDITED 作为制证前置守卫

**防护**：
- `audit()` 前置守卫不为 CONFIRMED 则拒
- `generateVoucher()` 前置守卫不为 AUDITED 则拒
- 测试中 `verify(autoGenerationService, never()).autoGenerateInNewTx()`

---

# MACHINE-READABLE CONTRACT

```yaml
contract_version: "1.0"
spec_file: "P23-bank-statement-state-machine.md"
spec_id: P23
entity: BankStatementEntity
module: bank
table: t_bank_statement
last_updated: "2026-06-30"
implementation_status: implemented

states:
  PENDING:
    description: "待确认"
    initial: true
    terminal: false

  CONFIRMED:
    description: "已确认"
    initial: false
    terminal: false

  UNCONFIRMED:
    description: "未确认（需人工处理）"
    initial: false
    terminal: false

  DUPLICATE:
    description: "重复流水"
    initial: false
    terminal: true

  MANUAL_PENDING:
    description: "待人工分类"
    initial: false
    terminal: false

  APPROVED:
    description: "已审批"
    initial: false
    terminal: false

  PAYMENT_CREATED:
    description: "已生成收付款单"
    initial: false
    terminal: false

  VOUCHER_GENERATED:
    description: "已生成凭证"
    initial: false
    terminal: false

transitions:
  - id: T-01
    from: PENDING
    to: CONFIRMED
    trigger: confirm
    precondition: "status == PENDING"
    postcondition: "status == CONFIRMED"
    side_effects: []
    test_ref: confirm_positive

  - id: T-02
    from: PENDING
    to: UNCONFIRMED
    trigger: unconfirm
    precondition: "status == PENDING"
    postcondition: "status == UNCONFIRMED"
    side_effects: []
    test_ref: unconfirm_positive

  - id: T-03
    from: PENDING
    to: DUPLICATE
    trigger: markDuplicate
    precondition: "status == PENDING"
    postcondition: "status == DUPLICATE"
    side_effects: []
    test_ref: mark_duplicate_positive

  - id: T-04
    from: MANUAL_PENDING
    to: APPROVED
    trigger: approve
    precondition: "status == MANUAL_PENDING"
    postcondition: "status == APPROVED"
    side_effects: []
    test_ref: approve_positive

  - id: T-05
    from: APPROVED
    to: PAYMENT_CREATED
    trigger: generatePayment
    precondition: "status == APPROVED"
    postcondition: "status == PAYMENT_CREATED"
    side_effects:
      - entity: ArapSettlementEntity
        action: create
        status: DRAFT
    test_ref: generate_payment_positive

  - id: T-06
    from: PAYMENT_CREATED
    to: VOUCHER_GENERATED
    trigger: generateVoucher
    precondition: "status == PAYMENT_CREATED"
    postcondition: "status == VOUCHER_GENERATED"
    side_effects:
      - entity: VoucherEntity
        action: create
        status: DRAFT
    test_ref: generate_voucher_positive

  - id: T-07
    from: PENDING
    to: MANUAL_PENDING
    trigger: review
    precondition: "status in (PENDING, CONFIRMED, UNCONFIRMED)"
    postcondition: "status == MANUAL_PENDING"
    side_effects: []
    test_ref: review_positive

constraints:
  - id: C-01
    type: database
    rule: "CHECK constraint on t_bank_statement.status"
    migration: V48

  - id: C-02
    type: business
    rule: "银行流水不应直接参与核销，正确流程：流水→B类路由→收款/付款单→核销"
    enforcement: "StateMachineService 不直接调用核销"

acceptance_tests:
  - id: AT-001
    description: "PENDING → CONFIRMED"
    method: confirm_positive
    assertion: "status == CONFIRMED"
    status: covered

  - id: AT-002
    description: "PENDING → DUPLICATE"
    method: mark_duplicate_positive
    assertion: "status == DUPLICATE"
    status: covered

  - id: AT-003
    description: "MANUAL_PENDING → APPROVED"
    method: approve_positive
    assertion: "status == APPROVED"
    status: covered

  - id: AT-004
    description: "APPROVED → PAYMENT_CREATED"
    method: generate_payment_positive
    assertion: "status == PAYMENT_CREATED"
    status: covered

  - id: AT-005
    description: "PAYMENT_CREATED → VOUCHER_GENERATED"
    method: generate_voucher_positive
    assertion: "status == VOUCHER_GENERATED"
    status: covered

out_of_scope:
  - "自动分类逻辑 (P1 范围)"
  - "AI 异常检测 (AI service 范围)"

dependencies:
  - spec: P1
    entity: BankStatementEntity
    relation: "导入和分类"
  - spec: P22
    entity: VoucherEntity
    relation: "生成凭证"
  - spec: P5
    entity: ArapSettlementEntity
    relation: "核销"
```
