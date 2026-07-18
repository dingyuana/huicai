# P18 SPEC — 申报状态机扩展 (SUBMITTED → APPROVED/REJECTED)

> **编号**：HUICAI-SPC-018（P13 已实现 submitDeclaration, 缺 approve/reject）
> 目标：完整 5 态状态机 (DRAFT/SUBMITTED/APPROVED/REJECTED)
> 工期：1 批

---

> **关联需求**: REQ-2026-036, REQ-2026-037

## SDD 四段结构索引

### 1. 输入契约
→ 见本文 [## 2. P18-1 任务（接口定义/Controller 端点）](#2-p18-1-任务-本批)

### 2. 输出契约
→ 见本文 [## 4. 测试验收（测试目标/验收条件）](#4-测试验收)

### 3. 状态流转
→ 见本文 [## 1. 现状摸底（DRAFT/SUBMITTED/APPROVED/REJECTED 状态机）](#1-现状摸底-2026-06-15) 及 MACHINE-READABLE CONTRACT 中的 states/transitions

### 4. 异常处理
→ 见本文各状态机前置校验中的 BusinessException 抛出点

---

## 1. 现状摸底 (2026-06-15)

| 文件 | 状态 |
|---|---|
| `t_tax_declaration` 实体 | ✅ (P3) |
| `TaxServiceImpl` 申报方法 | **2/4** (createDeclaration, submitDeclaration) |
| 缺 | `approveDeclaration / rejectDeclaration` |

**当前状态机**:
```
DRAFT ──→ submitDeclaration ──→ SUBMITTED ──→ (卡死, 缺审批)
```

**目标完整状态机**:
```
DRAFT ──→ submitDeclaration ──→ SUBMITTED
                                     ├─→ approveDeclaration ──→ APPROVED
                                     └─→ rejectDeclaration  ──→ REJECTED
```

---

## 2. P18-1 任务 (本批)

### 2.1 接口 + Impl

```java
// TaxService.java
TaxDeclarationEntity approveDeclaration(Long id, String approver);
TaxDeclarationEntity rejectDeclaration(Long id, String approver, String reason);
```

行为:
- 校验 status=SUBMITTED
- approveDeclaration → 状态=APPROVED, 记录 approver/approvedAt
- rejectDeclaration → 状态=REJECTED, 记录 approver/reason/rejectedAt
- 业务单据关联 (voucherId 联动, 当前用 placeholder)

### 2.2 Controller 端点

```java
POST /api/v1/tax/declarations/{id}/approve
POST /api/v1/tax/declarations/{id}/reject?reason=...
```

### 2.3 单测 (2 个)

| # | 测试 | 覆盖 |
|---|---|---|
| 1 | `approveDeclaration_SUBMITTED_变APPROVED_记录approver` | approveDeclaration |
| 2 | `rejectDeclaration_SUBMITTED_变REJECTED_理由必填` | rejectDeclaration |

---

## 3. 不在 P18 范围

- 申报撤销/作废
- 与电子税务局对接 (P14 候选)
- 自动生成申报凭证

---

## 4. 测试验收

**目标**: 305 → 307 (+2 测试)

---

# MACHINE-READABLE CONTRACT

```yaml
contract_version: "1.0"
spec_file: "P18-declaration-state-machine.md"
spec_id: P18
entity: TaxDeclarationEntity
module: tax
table: t_tax_declaration
last_updated: "2026-06-30"
implementation_status: implemented

states:
  DRAFT:
    description: "草稿"
    initial: true
    terminal: false

  SUBMITTED:
    description: "已提交"
    initial: false
    terminal: false

  APPROVED:
    description: "已批准"
    initial: false
    terminal: false

  REJECTED:
    description: "已驳回"
    initial: false
    terminal: false

  VOUCHERED:
    description: "已生成凭证"
    initial: false
    terminal: false

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
    to: APPROVED
    trigger: approve
    precondition: "status == SUBMITTED"
    postcondition: "status == APPROVED"
    side_effects: []
    test_ref: approve_positive

  - id: T-03
    from: SUBMITTED
    to: DRAFT
    trigger: reject
    precondition: "status == SUBMITTED"
    postcondition: "status == DRAFT"
    side_effects: []
    test_ref: reject_positive

  - id: T-04
    from: APPROVED
    to: VOUCHERED
    trigger: generateVoucher
    precondition: "status == APPROVED"
    postcondition: "status == VOUCHERED"
    side_effects:
      - entity: VoucherEntity
        action: create
        status: DRAFT
    test_ref: generate_voucher_positive

constraints:
  - id: C-01
    type: database
    rule: "CHECK constraint on t_tax_declaration.status"

acceptance_tests:
  - id: AT-001
    description: "DRAFT → SUBMITTED"
    method: submit_positive
    assertion: "status == SUBMITTED"
    status: covered

  - id: AT-002
    description: "SUBMITTED → APPROVED"
    method: approve_positive
    assertion: "status == APPROVED"
    status: covered

  - id: AT-003
    description: "APPROVED → VOUCHERED"
    method: generate_voucher_positive
    assertion: "status == VOUCHERED"
    status: covered

out_of_scope:
  - "税务申报计算逻辑"
  - "多税种合并申报"

dependencies:
  - spec: P22
    entity: VoucherEntity
    relation: "生成凭证"
```

---

## BDD 验收标准

### 场景 1：申报提交后审批通过流转
**Given** 一条状态为 `SUBMITTED` 的申报记录
**When** 调用 `approveDeclaration(id, approver)`
**Then** 记录状态变为 `APPROVED`，`approver` 和 `approvedAt` 字段被正确写入

### 场景 2：申报提交后审批驳回回退
**Given** 一条状态为 `SUBMITTED` 的申报记录
**When** 调用 `rejectDeclaration(id, approver, reason)`，且 `reason` 不为空
**Then** 记录状态变为 `REJECTED`，`approver`、`reason` 和 `rejectedAt` 字段被正确写入

### 场景 3：非法状态转移被拒绝
**Given** 一条状态为 `DRAFT` 的申报记录
**When** 尝试调用 `approveDeclaration(id, approver)`
**Then** 系统抛出 `BusinessException`，提示"仅已提交状态可审批"
