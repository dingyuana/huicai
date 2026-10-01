# P44 SPEC — 销项发票（OutputInvoice）审核状态机

> **编号**：HUICAI-SPC-044 | **优先级**：P0
> **版本**：V1.0 | **日期**：2026-10-01 | **作者**：opencode
> **状态**：✅ 生效
> **关联需求**：REQ-2026-136（P105 契约治理衍生）
> **姊妹 SPEC**：P40（进项发票审核状态机）—— 两者**共用同一套 `InvoiceStatus` 状态值**

> **本 SPEC 为何新建**：P40 只定义了 `InputInvoice`（进项）；销项发票的审核状态机
> 与之同构（8 态、同常量类、同 Service 形态）却**无任何 SPEC 定义**。
> 且校验器对同一文件只解析**第一个** `# MACHINE-READABLE CONTRACT` marker，
> 故无法把销项契约并入 P40 —— 只能另立文件。
> 校验器扫描 `docs/specs/P*.md`，本文件纳入覆盖率统计。

---

## 1. 输入契约

| 项 | 约束 |
|----|------|
| 表 | `t_output_invoice` |
| 必填（NOT NULL 且无默认值） | `invoice_no` / `invoice_date` / `period` / `amount` / `tax_rate` / `tax_amount` / `total_amount` / **`invoice_type`** |
| `invoice_type` | `chk_output_invoice_type`：**`SPECIAL` / `PLAIN` / `CUSTOMS`** |
| `amount` 语义 | **不含税金额**。`createOutput()` 按 `amount × tax_rate / 100` 反算税额 |
| 逻辑删除 | `deleted`（`BaseEntity.@TableLogic`） |

> ⚠️ `invoice_type` 是 NOT NULL 且无默认值，漏传即 `23502`。库内实际取值仅 `SPECIAL`。

## 2. 输出契约

对外返回 VO，禁止直接返回 Entity（铁律 #13）。

## 3. 状态流转

见下方 MACHINE-READABLE CONTRACT。状态值以 `chk_output_invoice_status` 为准。

## 4. 异常处理

非法转换抛 `BusinessException`（铁律 #14）。

---

# MACHINE-READABLE CONTRACT

> 权威来源：`pg_get_constraintdef(oid)` + `OutputInvoiceStateMachineServiceImpl` 实际写入点。

```yaml
contract_version: "1.0"
spec_file: "P44-output-invoice-state-machine.md"
spec_id: P44
entity: OutputInvoice
module: tax
table: t_output_invoice
last_updated: "2026-10-01"
implementation_status: implemented

status_constant_class: com.huicai.sme.tax.constant.InvoiceStatus

check_allowed_set:
  - PENDING_CONFIRM
  - PENDING_REVIEW
  - CONFIRMED
  - VOUCHERED
  - PARTIALLY_RECONCILED
  - FULLY_RECONCILED
  - VOIDED
  - REVERSED

states:
  PENDING_CONFIRM:
    description: "待确认。导入或手动创建后的初始态，尚未提交审核"
    initial: true
    terminal: false
  PENDING_REVIEW:
    description: "已提交审核，等待人工审核（铁律 #1：人是唯一审核主体）"
    terminal: false
  CONFIRMED:
    description: "审核通过，可标记已制证"
    terminal: false
  VOUCHERED:
    description: "已生成凭证，可核销扣减或红冲反冲"
    terminal: false
  PARTIALLY_RECONCILED:
    description: "部分核销，仍有未核销余额。**非终态**"
    terminal: false
  FULLY_RECONCILED:
    description: "全额核销。**非终态**"
    terminal: false
  VOIDED:
    description: "已作废，终态"
    terminal: true
  REVERSED:
    description: "已被红冲反冲（原单据），终态"
    terminal: true

transitions:
  - id: create
    from: ANY
    to: PENDING_CONFIRM
    trigger: createOutput
    implementation: TaxServiceImpl.createOutput()
    test_ref: createOutput_defaultStatus
    note: >-
      P0 修复记录（56b545f3）：原在 status==null 时写 "DRAFT"，
      而 chk_output_invoice_status **不含 DRAFT** ⇒ 未传 status 的调用方必然 23514/500。
      已对齐 createInput 的约定改用 InvoiceStatus.PENDING_CONFIRM。
      回归见 OutputInvoiceCreateStatusRealDBTest。

  - id: submit_for_review
    from: PENDING_CONFIRM
    to: PENDING_REVIEW
    trigger: submitForReview
    implementation: OutputInvoiceStateMachineServiceImpl.submitForReview()
    test_ref: submitForReview_positive

  - id: confirm
    from: PENDING_REVIEW
    to: CONFIRMED
    trigger: confirm
    implementation: OutputInvoiceStateMachineServiceImpl.confirm()
    test_ref: confirm_positive

  - id: reject
    from: PENDING_REVIEW
    to: PENDING_CONFIRM
    trigger: reject
    implementation: OutputInvoiceStateMachineServiceImpl.reject()
    test_ref: reject_positive

  - id: revert_to_review
    from: CONFIRMED
    to: PENDING_REVIEW
    trigger: revertToReview
    implementation: OutputInvoiceStateMachineServiceImpl.revertToReview()
    test_ref: revertToReview_positive

  - id: mark_vouchered
    from: CONFIRMED
    to: VOUCHERED
    trigger: markVouchered
    implementation: OutputInvoiceStateMachineServiceImpl.markVouchered()
    test_ref: markVouchered_positive

  - id: reconcile_partial
    from: [VOUCHERED, PARTIALLY_RECONCILED]
    to: PARTIALLY_RECONCILED
    trigger: onReconciliationUpdate
    precondition: "unsettled > 0"
    implementation: OutputInvoiceStateMachineServiceImpl.onReconciliationUpdate()
    test_ref: onReconciliationUpdate_partial

  - id: reconcile_full
    from: [VOUCHERED, PARTIALLY_RECONCILED]
    to: FULLY_RECONCILED
    trigger: onReconciliationUpdate
    precondition: "unsettled == 0"
    implementation: OutputInvoiceStateMachineServiceImpl.onReconciliationUpdate()
    test_ref: onReconciliationUpdate_full

  - id: void
    from: [PENDING_CONFIRM, PENDING_REVIEW, CONFIRMED, VOUCHERED]
    to: VOIDED
    trigger: voidInvoice
    precondition: "填写作废原因（铁律：人审）"
    implementation: OutputInvoiceStateMachineServiceImpl.voidInvoice()
    test_ref: voidInvoice_positive

  - id: reverse_original
    from: [CONFIRMED, VOUCHERED, PARTIALLY_RECONCILED, FULLY_RECONCILED]
    to: REVERSED
    trigger: reverseInvoice
    precondition: "原单据被红冲"
    implementation: OutputInvoiceStateMachineServiceImpl.reverseInvoice()
    test_ref: reverseInvoice_original
    note: "同一次调用还会创建红字发票，状态 PENDING_CONFIRM（需重新走审核）"

deviations:
  - "前端 tax.ts 未暴露创建端点，故 create 的 23514 缺陷长期潜伏至 P105 才被发现"

constraints:
  - id: C-44-1
    type: database
    rule: "chk_output_invoice_status 允许集为 PENDING_CONFIRM/PENDING_REVIEW/CONFIRMED/VOUCHERED/PARTIALLY_RECONCILED/FULLY_RECONCILED/VOIDED/REVERSED（**无 DRAFT**）"
    enforcement: "PostgreSQL CHECK"
  - id: C-44-2
    type: database
    rule: "chk_output_invoice_type 允许集为 SPECIAL/PLAIN/CUSTOMS"
    enforcement: "PostgreSQL CHECK"
  - id: C-44-3
    type: immutability
    rule: "VOIDED / REVERSED 为终态，不得再流转"
    enforcement: "状态机终态 + Service 前置校验"

acceptance_tests:
  - id: AT-O01
    description: "不传 status 创建 → PENDING_CONFIRM（原必 500）"
    method: createOutput_defaultStatus
    assertion: "status == PENDING_CONFIRM 且不抛 DataIntegrityViolation"
    status: covered
  - id: AT-O02
    description: "PENDING_CONFIRM → PENDING_REVIEW"
    method: submitForReview_positive
    assertion: "status == PENDING_REVIEW"
    status: covered
  - id: AT-O03
    description: "PENDING_REVIEW → CONFIRMED"
    method: confirm_positive
    assertion: "status == CONFIRMED"
    status: covered
  - id: AT-O04
    description: "PENDING_REVIEW → PENDING_CONFIRM（驳回）"
    method: reject_positive
    assertion: "status == PENDING_CONFIRM"
    status: covered
  - id: AT-O05
    description: "CONFIRMED → PENDING_REVIEW（撤回复审）"
    method: revertToReview_positive
    assertion: "status == PENDING_REVIEW"
    status: covered
  - id: AT-O06
    description: "CONFIRMED → VOUCHERED"
    method: markVouchered_positive
    assertion: "status == VOUCHERED"
    status: covered
  - id: AT-O07
    description: "作废 → VOIDED（终态）"
    method: voidInvoice_positive
    assertion: "status == VOIDED"
    status: covered
  - id: AT-O08
    description: "红冲：原单 REVERSED 且红字单 PENDING_CONFIRM"
    method: reverseInvoice_original
    assertion: "original == REVERSED 且新发票 == PENDING_CONFIRM"
    status: missing
```

---

## 5. BDD 验收标准

### 场景 1：创建不带状态不得崩溃
**Given** 一个未传 `status` 的销项发票请求
**When** 调用创建接口
**Then** 落库成功且 `status = PENDING_CONFIRM`（P0 回归：原 500）

### 场景 2：人工审核闭环
**Given** 发票处于 `PENDING_CONFIRM`
**When** `submitForReview()` → `confirm()`
**Then** 依次为 `PENDING_REVIEW` → `CONFIRMED`

### 场景 3：驳回回到待确认
**Given** 发票处于 `PENDING_REVIEW`
**When** `reject(reason)`
**Then** `status = PENDING_CONFIRM`

### 场景 4：红冲产生红字发票需重新审核
**Given** 已制证发票 `status = VOUCHERED`
**When** `reverseInvoice(reason)`
**Then** 原单 `REVERSED`，新建红字发票 `PENDING_CONFIRM`（**不得自动确认**，铁律 #1）