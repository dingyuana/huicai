# SPEC Contract YAML Schema — Machine-Readable Contract Format

> **编号**：HUICAI-SPC-099
> **版本**：1.0 | **修改日期**：2026-06-30 | **修改人**：Hermes | **修改内容**：初始创建
> Purpose: Define the machine-readable contract block that can be appended to any
> SPEC markdown file for automated validation.

> **关联需求**: REQ-2026-047, REQ-2026-048, REQ-2026-052
## 1. Design Principles

1. **Co-located**: YAML block lives in the SAME `.md` file as the natural-language SPEC,
   separated by `---`. No separate files needed.
2. **Human-ignorable**: The YAML block is after the `---` separator. Humans read above,
   machines parse below. Changes to YAML don't affect readability of the SPEC.
3. **Incremental adoption**: Not all SPECs need all sections. A minimal contract might
   only have `states` + `transitions`. A rich one adds `acceptance_tests`, `negative_assertions`,
   and `constraints`.
4. **Single source of truth**: The YAML is THE contract. Code and tests must conform to it.
   If code diverges, CI fails.

## 2. Schema Definition

```yaml
# === MACHINE-READABLE CONTRACT ===
# Parse from here down. Everything above is human-readable SPEC.

contract_version: "1.0"

# --- Entity metadata ---
entity: OutputInvoiceEntity          # Target entity class name
module: tax                          # Module/package namespace
table: t_output_invoice              # Database table name

# --- States (finite state machine definition) ---
states:
  PENDING_CONFIRM:                   # State name (matches InvoiceStatus constant)
    description: "导入后默认状态，待确认"
    initial: true                    # Is this an initial state?
    terminal: false                  # Can transitions leave this state?

  PENDING_REVIEW:
    description: "待审核"
    initial: false
    terminal: false

  CONFIRMED:
    description: "已确认（已开票）"
    initial: false
    terminal: false

  VOUCHERED:
    description: "已生成凭证"
    initial: false
    terminal: false

  FULLY_RECONCILED:
    description: "全额核销"
    initial: false
    terminal: true

  PARTIALLY_RECONCILED:
    description: "部分核销"
    initial: false
    terminal: false

  VOIDED:
    description: "已作废"
    initial: false
    terminal: true

  REVERSED:
    description: "已红冲"
    initial: false
    terminal: true

# --- Transitions (state machine edges) ---
transitions:
  - id: T-01                         # Unique transition ID
    from: PENDING_CONFIRM
    to: PENDING_REVIEW
    trigger: submitForReview         # Method name in state machine service
    precondition: "status == PENDING_CONFIRM"
    postcondition: "status == PENDING_REVIEW"
    side_effects: []                 # Other entities created/modified
    test_ref: test_submit_for_review_positive

  - id: T-02
    from: PENDING_REVIEW
    to: CONFIRMED
    trigger: confirm
    precondition: "status == PENDING_REVIEW"
    postcondition: "status == CONFIRMED; auditedBy = userId"
    side_effects:
      - entity: BusinessDocEntity
        action: create
        status: DRAFT
      - entity: ReceivableEntity
        action: create
        status: DRAFT
      - entity: VoucherEntity
        action: create
        status: DRAFT
    test_ref: test_confirm_auto_generates_voucher
    negative_assertions:
      - assertion: "confirm should not directly set invoice status to VOUCHERED"
        method: confirm_should_not_directly_set_vouchered

  - id: T-03
    from: PENDING_REVIEW
    to: PENDING_CONFIRM
    trigger: reject
    precondition: "status == PENDING_REVIEW"
    postcondition: "status == PENDING_CONFIRM; reason recorded"
    side_effects: []
    test_ref: test_reject_positive

  - id: T-04
    from: CONFIRMED
    to: PENDING_REVIEW
    trigger: revertToReview
    precondition: "status == CONFIRMED"
    postcondition: "status == PENDING_REVIEW"
    side_effects: []
    test_ref: test_revert_to_review_positive

  - id: T-05
    from: CONFIRMED
    to: VOUCHERED
    trigger: markVouchered
    precondition: "status == CONFIRMED"
    postcondition: "status == VOUCHERED; voucherId recorded"
    side_effects: []
    test_ref: test_mark_vouchered_positive

  - id: T-06
    from: VOUCHERED
    to: FULLY_RECONCILED
    trigger: onReconciliationUpdate
    precondition: "status == VOUCHERED && unsettledAmount == 0"
    postcondition: "status == FULLY_RECONCILED"
    side_effects: []
    test_ref: test_fully_reconciled

  - id: T-07
    from: VOUCHERED
    to: PARTIALLY_RECONCILED
    trigger: onReconciliationUpdate
    precondition: "status == VOUCHERED && unsettledAmount > 0"
    postcondition: "status == PARTIALLY_RECONCILED"
    side_effects: []
    test_ref: test_partially_reconciled

  - id: T-08
    from: ANY_NON_TERMINAL             # Special: matches any non-terminal state
    to: VOIDED
    trigger: voidInvoice
    precondition: "!InvoiceStatus.isTerminal(status)"
    postcondition: "status == VOIDED; reason recorded"
    side_effects: []
    test_ref: test_void_various_status

  - id: T-09
    from: CONFIRMED
    to: (new red invoice)
    trigger: reverseInvoice
    precondition: "status in (CONFIRMED, VOUCHERED, PARTIALLY_RECONCILED)"
    postcondition: "original invoice status = REVERSED; new red invoice created"
    side_effects:
      - entity: OutputInvoiceEntity
        action: create_red_invoice
        status: PENDING_CONFIRM
    test_ref: test_reverse_invoice_positive

# --- Constraints (non-functional rules) ---
constraints:
  - id: C-01
    type: database
    rule: "CHECK (status IN (...8 states...))"
    migration: V46

  - id: C-02
    type: business
    rule: "All state transitions must be audited (auditedBy + auditedAt)"
    enforcement: "State machine service sets these fields"

  - id: C-03
    type: immutability
    rule: "Terminal states (VOIDED, REVERSED, FULLY_RECONCILED) cannot transition out"
    enforcement: "State machine precondition check"

# --- Acceptance Tests (machine-verifiable) ---
acceptance_tests:
  - id: AT-001
    description: "导入后默认 PENDING_CONFIRM"
    method: test_import_default_status
    assertion: "invoice.status == PENDING_CONFIRM"
    status: covered                    # covered / partial / missing

  - id: AT-002
    description: "非终态不可作废"
    method: test_void_terminal_state_fails
    assertion: "voidInvoice throws BusinessException for VOIDED invoice"
    status: covered

  - id: AT-003
    description: "作废必须提供原因"
    method: test_void_without_reason_fails
    assertion: "voidInvoice throws BusinessException when reason is null/empty"
    status: covered

  - id: AT-004
    description: "审核通过不应直接设 VOUCHERED"
    method: test_confirm_should_not_directly_set_vouchered
    assertion: "after confirm(), invoice.status != VOUCHERED (P31: status = CONFIRMED, postProcess creates doc/receivable/voucher)"
    status: covered

# --- Out of Scope (explicit exclusions) ---
out_of_scope:
  - "InputInvoiceEntity state machine (handled by P21-b, deprecated)"
  - "VoucherEntity state machine (handled by P22)"
  - "AOP audit logging (handled by P24)"
  - "Multi-level approval rules (not in current scope)"

# --- Dependencies ---
dependencies:
  - spec: P20                        # AR/AP state machine
    relation: "ReceivableEntity created by confirm()"
  - spec: P22                        # Voucher state machine
    relation: "VoucherEntity created by confirm()"
  - spec: P24                        # Audit tracking
    relation: "Audit log replaces log.info statements"
```

## 3. Field Reference

### Top-level keys (all optional except `contract_version` and `states`)

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `contract_version` | string | Yes | Schema version, always `"1.0"` |
| `entity` | string | No | Target entity class name |
| `module` | string | No | Module/package namespace |
| `table` | string | No | Database table name |
| `states` | object | Yes | Finite state machine definition |
| `transitions` | array | Recommended | State transition definitions |
| `constraints` | array | Optional | Non-functional rules |
| `acceptance_tests` | array | Optional | Verifiable acceptance criteria |
| `out_of_scope` | array | Optional | Explicit exclusions |
| `dependencies` | array | Optional | Cross-SPEC dependencies |

### `states` object

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `{state_name}` | object | — | State key (must match enum constant) |
| → `description` | string | — | Human-readable description |
| → `initial` | boolean | `false` | Is this an entry state? |
| → `terminal` | boolean | `false` | Can transitions leave this state? |

### `transitions` array items

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `id` | string | Yes | Unique transition ID (format: `T-NN`) |
| `from` | string | Yes | Source state name |
| `to` | string | Yes | Target state name (or special `(new X)` for creation) |
| `trigger` | string | Yes | Method name that triggers this transition |
| `precondition` | string | Yes | Condition that must hold before transition |
| `postcondition` | string | Yes | State of system after transition |
| `side_effects` | array | No | Entities created/modified by this transition |
| `test_ref` | string | No | Corresponding test method name |
| `negative_assertions` | array | No | Assertions about what should NOT happen |

### `acceptance_tests` array items

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `id` | string | Yes | Unique test ID (format: `AT-NNN`) |
| `description` | string | Yes | Human-readable description |
| `method` | string | Yes | Test method name in code |
| `assertion` | string | Yes | What the test verifies |
| `status` | string | No | `covered` / `partial` / `missing` |

## 4. Validation Rules

The validator enforces:

1. **State consistency**: Every `from`/`to` in `transitions` must exist in `states`
2. **Terminal protection**: No transition FROM a `terminal: true` state
3. **Trigger uniqueness**: Each `trigger` method name appears at most once per `from` state
4. **Test coverage**: Every `transition` with a `test_ref` must have a corresponding `@Test` method
5. **AT status check**: `acceptance_tests` with `status: missing` → CI warning
6. **ID uniqueness**: All IDs (`T-NN`, `AT-NNN`, `C-NN`) must be unique within their type

## 5. Integration Points

### CI Pipeline
```bash
# Step 1: Validate YAML syntax
python scripts/validate_spec_contract.py --path docs/specs/P21-sales-invoice-state-machine.md

# Step 2: Cross-reference with code
python scripts/validate_spec_contract.py --path docs/specs/P21-sales-invoice-state-machine.md --check-implementation

# Step 3: Report coverage
python scripts/validate_spec_contract.py --path docs/specs/P21-sales-invoice-state-machine.md --check-tests
```

### Git Hook (pre-commit)
```bash
# All SPEC files in the commit get validated
for spec in $(git diff --cached --name-only | grep 'docs/specs/.*\.md'); do
    python scripts/validate_spec_contract.py --path "$spec" --strict
done
```
