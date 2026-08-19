# SPEC Contract YAML Schema - Machine-Readable Contract Format

> **编号**：HUICAI-SPC-099
> **版本**：2.0 | **修改日期**：2026-07-18 | **修改人**：Hermes | **修改内容**：加入 SDD 四段模板 + BDD Given-When-Then 格式规范
> **历史版本**：1.0 (2026-06-30) - 初始创建
> Purpose: Define the machine-readable contract block that can be appended to any
> SPEC markdown file for automated validation.

> **关联需求**: REQ-2026-047, REQ-2026-048, REQ-2026-052

## 0. SDD 四段模板（SPEC 正文强制结构）

每个 SPEC 文档正文必须包含以下四个段落。这是 SDD（规范驱动开发）的核心约束--Spec 定义边界，代码实现边界。

```markdown
## 1. 输入契约
- 接受什么参数、什么类型、什么约束
- 前置条件（如：实体必须处于 X 状态）
- 权限要求

## 2. 输出契约
- 返回什么结构、什么状态
- 成功响应结构（VO 字段列表）
- 失败响应结构（错误码 + message，参考全局错误码字典）

## 3. 状态流转
- 状态机图（合法转换 + 非法转换）
- 负向断言（禁止的跳转路径，如：DRAFT 不能直接跳到 AUDITED）
- **副作用声明（Side Effects）**：该操作对数据库/缓存/消息队列产生的所有写操作
  - 格式：`操作类型（INSERT/UPDATE/DELETE）→ 目标表/队列 → 说明`
  - 示例：`DB_INSERT → t_user_login_log → 记录每次登录`
  - 示例：`CACHE_SET → Redis:USER_TOKEN:{userId} → 缓存登录令牌`
  - 副作用声明用于变更影响评估：改一处代码，能精确知道影响了哪些表

## 4. 异常处理
- **异常码穷举（Exhaustive Error Codes）**：列出该接口可能抛出的所有业务异常码
  - 每个异常码标注：`错误码`、`HTTP状态码`、`日志级别`、`对客提示文案`
  - 格式：`ERROR_CODE → HTTP 状态 → 日志级别 → 提示文案`
  - 示例：`AUTH_001 → 401 → WARN → "用户账号或密码错误"`
  - 示例：`AUTH_002 → 403 → ERROR → "账户已被冻结或锁定"`
  - 代码实现必须穷举所有异常码，不得遗漏
- 异常场景列表（每个场景对应一个错误码）
- 每个场景的降级策略
- 事务回滚条件
```

## 0.1 BDD 行为契约格式（验收标准强制格式）

SPEC 中的验收标准必须使用 Given-When-Then 格式（BDD 行为驱动开发）：

```markdown
## 验收标准（BDD）

### 场景 1：{场景名}
- **Given** {前置条件/初始状态}
- **When** {触发动作}
- **Then** {期望结果}
- **And** {负向断言：不该发生的}

### 场景 2：{场景名}
- **Given** ...
- **When** ...
- **Then** ...
```

**规则**：
1. 每个 BDD 场景必须对应一个 `@Test` 方法，方法名用 `test_{场景描述}` 命名
2. Given-When-Then 块的 Then 必须包含至少一个正向断言
3. 状态机类场景必须包含负向断言（不该做的没做）
4. BDD 场景数量 = YAML 契约块中 acceptance_tests 数量

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
5. **AT status check**: `acceptance_tests` with `status: missing` -> CI warning
6. **ID uniqueness**: All IDs (`T-NN`, `AT-NNN`, `C-NN`) must be unique within their type
7. **SDD 四段检查**: SPEC 正文必须包含「输入契约」「输出契约」「状态流转」「异常处理」四段
8. **BDD 格式检查**: 验收标准必须使用 Given-When-Then 格式，每个场景对应一个 `@Test` 方法
9. **BDD 场景覆盖**: BDD 场景数量 = `acceptance_tests` 数量，两者必须 1:1 对应
10. **负向断言检查**: 状态机类 SPEC 必须包含 `negative_assertions`

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

## 6. 物理路径编码约定（Contract-First 追溯）

为实现 PRD→DSN→SPEC→代码→@Test 全链路可追溯，强制以下路径编码约定：

| 要素 | 约定 | 示例 |
|------|------|------|
| SPEC 文件名 | `PXX-功能名称.md` 或 `S-XX-模块名称.md` | `P22-voucher-state-machine.md` |
| 包路径 | `com.huicai.{base\|sme\|agency}.{模块名}` | `com.huicai.base.voucher` |
| 测试类名 | `{ServiceName}ImplTest.java` | `VoucherStateMachineServiceImplTest.java` |
| API 路径 | `/api/{version}/{模块}/{功能}` | `/api/v1/vouchers/{id}/submit` |
| test_ref 绑定 | SPEC 头部 `test_ref` 字段指向具体测试类 | `test_ref: VoucherStateMachineServiceImplTest` |
| PRD 关联 | PRD 头部 `关联SPEC` 字段列出所有关联 SPEC 文件名 | `关联SPEC: P22, P37, S-17` |

**追溯验证方法：**
1. 输入一个 PRD 编号 → 查 PRD 文件头部 `关联SPEC` → 找到所有 SPEC 文件
2. 输入一个 SPEC 编号 → 查 SPEC 文件头部 `test_ref` → 找到对应 @Test 类
3. 输入一个 @Test 类名 → 反查 SPEC 文件头部 `test_ref` → 找到来源 SPEC → 查关联 PRD

零数据漂移，纯文件头部字段，不依赖外部图谱数据库。
