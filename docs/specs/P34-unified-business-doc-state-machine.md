# P34 SPEC — 统一业务单据（t_business_doc）状态机

> **版本**：V1.0 | **日期**：2026-10-01 | **作者**：opencode
> **状态**：✅ 生效（契约于 P105 补齐）
> **编号**：HUICAI-SPC-034
> **关联需求**：REQ-2026-136（核销模板科目修正，P105 契约治理衍生）

> **本 SPEC 为何新建**：P10 SPEC 头部写明「V74 已删除 t_receivable/t_payable，
> 当前架构统一使用 `t_business_doc`，**详见 P34 SPEC**」—— 但 `docs/specs/` 下
> **从来不存在 P34 文件**，该引用一直是断链。结果是 `t_business_doc` 这个
> 被 11 种单据类型共用的核心实体，**全项目没有任何 SPEC 定义其状态机**。
> P12 的「状态机」章节描述的是 `t_reconciliation_log`（PENDING→CONFIRMED→EXECUTED），
> 与本实体无关。本 SPEC 补上这一空缺。

---

## 1. 输入契约

| 项 | 约束 |
|----|------|
| 表 | `t_business_doc` |
| 主键 | `id BIGINT GENERATED ALWAYS AS IDENTITY` |
| 必填（无默认值） | `doc_no` / `doc_type` / `doc_date` / `period` / `status`(默认 DRAFT) / `amount` / `enterprise_id` |
| 租户 | `enterprise_id` NOT NULL，由 `MyMetaObjectHandler` 强制覆盖入参值 |
| 删除 | 逻辑删除 `deleted`（`BaseEntity.@TableLogic`） |

`doc_type` 合法值（`chk_doc_type`，**仅 8 个**）：
`RECEIPT / PAYMENT / EXPENSE / INVOICE_IN / INVOICE_OUT / OTHER_RECEIVABLE / OTHER_PAYABLE / PREPAYMENT`

## 2. 输出契约

状态查询一律经 `BusinessDocMapper`；对外返回 VO，**禁止**直接把 Entity 交给前端（铁律 #13）。
状态相关查询的过滤条件必须含 `deleted = 0`。

## 3. 状态流转

见下方 MACHINE-READABLE CONTRACT（权威版本）。
`status` 合法值以 `chk_doc_status` 为准，**禁止**照抄其它常量类
（`ArapStatus.SETTLED` 曾串台到本表，见下「已知缺陷」）。

## 4. 异常处理

- 非法状态转换统一抛 `BusinessException`（铁律 #14），不得静默吞掉
- 乐观锁冲突抛 `OptimisticLockingFailureException`（`ArapSettlementServiceImpl` 两处反核销/确认均显式判 `updateById == 0`）
- 金额参数为 null / ≤0 一律 `BusinessException`

## 5. 已知缺陷（待修）

- **`ArapStatus.SETTLED` 不是缺陷，但对本表是陷阱**。核实结论（2026-10-01）：
  该常量是 **`t_prepayment`（预付款）的合法状态值** —— 该表**没有** status CHECK 约束
  （`BadDebtServiceImpl:457`、`PrepaymentServiceImpl` 均在用），故那些写入点合法。
  真正的问题只是：`ArapStatus.isSettled()` / `isReversible()` 语义上会被误用来判断
  `t_business_doc.status` —— 本表的「已结清」是 `FULLY_RECONCILED`，用 SETTLED 判会**恒为 false**。
  已修本表的全部写入点（`04932ece`）。写本表状态请一律用 `BusinessDocStatus`。

---

# MACHINE-READABLE CONTRACT

> 权威来源：`pg_get_constraintdef(oid)`（`chk_doc_status`）+ 各 Service 实际 `setStatus` 写入点。

```yaml
contract_version: "1.0"
spec_file: "P34-unified-business-doc-state-machine.md"
spec_id: P34
entity: BusinessDoc
module: arap
table: t_business_doc
last_updated: "2026-10-01"
implementation_status: implemented

status_constant_class: com.huicai.base.voucher.constant.BusinessDocStatus
# ⚠️ 常量类在 base.voucher 包，不在 base.business —— 写错 import 会引到 ArapStatus

check_allowed_set:
  - DRAFT
  - SUBMITTED
  - APPROVED
  - VOUCHERED
  - PARTIALLY_RECONCILED
  - FULLY_RECONCILED
  - CLOSED
  - REJECTED
  - REVERSED

states:
  DRAFT:
    description: "草稿，仅草稿可编辑"
    initial: true
    terminal: false
  SUBMITTED:
    description: "已提交待审批"
    terminal: false
  APPROVED:
    description: "已审批，可制证"
    terminal: false
  VOUCHERED:
    description: "已生成凭证，可核销"
    terminal: false
  PARTIALLY_RECONCILED:
    description: "部分核销，仍有未清余额。**非终态** —— 可继续核销，也可被反核销"
    terminal: false
  FULLY_RECONCILED:
    description: "全额核销。**非终态** —— 本系统仍允许反核销它（ArapSettlementServiceImpl:584）"
    terminal: false
  REJECTED:
    description: "已驳回，终态"
    terminal: true
  REVERSED:
    description: "已红冲（凭证红冲级联回写），终态"
    terminal: true
  CLOSED:
    description: "已关闭。chk_doc_status 允许，但全仓无写入点（允许未用值）"
    terminal: true

transitions:
  - id: create
    from: ANY
    to: DRAFT
    trigger: create
    precondition: "无"
    implementation: BusinessDocServiceImpl.create()
    test_ref: create_positive

  - id: submit
    from: DRAFT
    to: SUBMITTED
    trigger: submit
    implementation: BusinessDocServiceImpl.submit()
    test_ref: submit_positive

  - id: approve
    from: SUBMITTED
    to: APPROVED
    trigger: approve
    implementation: BusinessDocServiceImpl.approve()
    test_ref: approve_positive

  - id: reject
    from: SUBMITTED
    to: REJECTED
    trigger: reject
    implementation: BusinessDocServiceImpl.reject()
    test_ref: reject_positive

  - id: mark_vouchered
    from: APPROVED
    to: VOUCHERED
    trigger: markDocVouchered
    precondition: "来源凭证已生成；同时联动来源流水 review_status: payment_created→voucher_generated（条件更新幂等）"
    implementation: BusinessDocServiceImpl.markDocVouchered()
    test_ref: markDocVouchered_positive
    note: "P73 引入。方法为 private，由制证链路内部调用，无独立端点。"

  - id: settle_partial
    from: [APPROVED, VOUCHERED, PARTIALLY_RECONCILED]
    to: PARTIALLY_RECONCILED
    trigger: settle
    precondition: "unsettled_amount > 0"
    implementation: ArapSettlementServiceImpl.approve()
    test_ref: settle_partial_positive

  - id: settle_full
    from: [APPROVED, VOUCHERED, PARTIALLY_RECONCILED]
    to: FULLY_RECONCILED
    trigger: settle
    precondition: "unsettled_amount == 0"
    implementation: ArapSettlementServiceImpl.approve()
    test_ref: settle_full_positive
    note: >-
      P0 修复记录：原写 ArapStatus.SETTLED（chk_doc_status 不允许 ⇒ 23514 必崩），
      已改 BusinessDocStatus.FULLY_RECONCILED。不得反向给 CHECK 补 SETTLED。

  - id: bad_debt_writeoff
    from: [APPROVED, VOUCHERED, PARTIALLY_RECONCILED]
    to: FULLY_RECONCILED
    trigger: writeOff
    precondition: "unsettled_amount == 0（坏账核销把剩余一次性核销掉）"
    implementation: BadDebtServiceImpl.writeOff()
    test_ref: badDebtWriteOff_full
    note: >-
      P0 修复记录（04932ece）：原无守卫 + 写 ArapStatus.SETTLED ⇒
      「把剩余应收一次性全额核销」这条最常见终局路径必然 500。
      复现见 BadDebtWriteOffStatusRealDBTest。

  - id: reverse_settle
    from: [APPROVED, VOUCHERED, PARTIALLY_RECONCILED, FULLY_RECONCILED]
    to: [APPROVED, VOUCHERED]
    trigger: reverse
    precondition: "已制证且凭证未作废 → 保持 VOUCHERED；否则回落 APPROVED"
    implementation: ArapSettlementServiceImpl.reverse()
    test_ref: reverse_settle_positive
    note: "**不得盲目回落 APPROVED**：已制证单据必须保持 VOUCHERED（ArapSettlementServiceImpl:583 注释）"

  - id: cascade_reversed
    from: [APPROVED, VOUCHERED, PARTIALLY_RECONCILED, FULLY_RECONCILED]
    to: REVERSED
    trigger: reverse
    precondition: "源凭证被红冲，级联回写本单据"
    implementation: VoucherServiceImpl.cascadeReverseToSourceDocs()
    test_ref: cascade_reversed_positive

deviations:
  - "ArapStatus.isSettled()/isReversible() 仍引用本表非法的 SETTLED，待清理"
  - "chk_doc_status 允许 CLOSED，但全仓无写入点"

constraints:
  - id: C-34-1
    type: database
    rule: "chk_doc_status 允许集恰为 DRAFT/SUBMITTED/APPROVED/VOUCHERED/PARTIALLY_RECONCILED/FULLY_RECONCILED/CLOSED/REJECTED/REVERSED"
    enforcement: "PostgreSQL CHECK"
  - id: C-34-2
    type: database
    rule: "chk_doc_type 允许集恰为 RECEIPT/PAYMENT/EXPENSE/INVOICE_IN/INVOICE_OUT/OTHER_RECEIVABLE/OTHER_PAYABLE/PREPAYMENT（注意：无 OTHER）"
    enforcement: "PostgreSQL CHECK"
  - id: C-34-3
    type: immutability
    rule: "已红冲单据（REVERSED）与已驳回单据（REJECTED）不得再流转"
    enforcement: "状态机终态 + Service 前置校验"

acceptance_tests:
  - id: AT-B01
    description: "创建后为 DRAFT"
    method: create_positive
    assertion: "status == DRAFT"
    status: covered
  - id: AT-B02
    description: "DRAFT → SUBMITTED → APPROVED"
    method: submit_approve_positive
    assertion: "status == APPROVED"
    status: covered
  - id: AT-B03
    description: "SUBMITTED → REJECTED（终态）"
    method: reject_positive
    assertion: "status == REJECTED"
    status: covered
  - id: AT-B04
    description: "APPROVED → VOUCHERED"
    method: markDocVouchered_positive
    assertion: "status == VOUCHERED"
    status: covered
  - id: AT-B05
    description: "全额核销 → FULLY_RECONCILED（不得写 SETTLED）"
    method: settle_full_positive
    assertion: "status == FULLY_RECONCILED 且不抛 DataIntegrityViolation"
    status: covered
  - id: AT-B06
    description: "坏账全额核销 → FULLY_RECONCILED（原必 500）"
    method: badDebtWriteOff_full
    assertion: "status == FULLY_RECONCILED 且 unsettled == 0"
    status: covered
  - id: AT-B07
    description: "反核销时已制证单据保持 VOUCHERED，不盲目回落 APPROVED"
    method: reverse_settle_keeps_vouchered
    assertion: "status == VOUCHERED"
    status: missing
  - id: AT-B08
    description: "凭证红冲级联回写 REVERSED"
    method: cascade_reversed_positive
    assertion: "status == REVERSED"
    status: missing
```

---

## 6. BDD 验收标准

### 场景 1：正常审批到制证
**Given** 一张 RECEIPT 单据处于 DRAFT
**When** 依次 `submit()` → `approve()` → `markDocVouchered()`
**Then** status 依次为 SUBMITTED → APPROVED → VOUCHERED

### 场景 2：全额核销写入合法状态
**Given** 一张已制证单据，`unsettled_amount = 1000`
**When** 核销 1000
**Then** status 为 `FULLY_RECONCILED`，且**不抛** `DataIntegrityViolation`（P0 回归）

### 场景 3：坏账一次性核销剩余全部
**Given** 一张应收单据 `unsettled_amount = 1000`，status = APPROVED
**When** `badDebtService.writeOff(id, "NOTE_RECEIVABLE", 1000, ...)`
**Then** status 为 `FULLY_RECONCILED`（P0 回归：原必然 500）

### 场景 4：反核销不得让已制证单据倒退
**Given** 一张 status = VOUCHERED 且凭证完好的单据
**When** 反核销使其全额清零
**Then** status **仍为 VOUCHERED**，不得回落 APPROVED