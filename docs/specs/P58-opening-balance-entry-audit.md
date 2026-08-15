# P58 期初建账审计增强（指定录入时间 + 建账日期/录入人员 + 审计日志修复）

> **编号**：HUICAI-SPC-P58
> **版本**：1.0 | **修改日期**：2026-08-13 | **修改人**：Hermes
> **关联需求**：REQ-2026-078
> **前置**：P57（企业级建账期间通用化，start_period 落库）
> **状态**：草案待审核

---

## 1. 输入契约

### 1.1 问题背景
1. **期初余额误录期间**：期初录入界面只有期间下拉框（默认选最早未建账期间），无日期字段。企业1 期初余额误录到 202311（2023年11月），而企业 start_period=202610，导致资产负债表按 202610 查询全零。
2. **审计日志操作人丢失**：`AuditLogEntity` 的 `username`/`method`/`requestParams`/`responseResult`/`oldSnapshot`/`newSnapshot`/`status` 全部标注 `@TableField(exist=false)`，但 `t_audit_log` 表有 `operator_name`/`operator_id`/`before_data`/`after_data` 列未映射。AOP 写入的操作人永不落库，审计追踪（铁律5）形同虚设。

### 1.2 新增强制约束
- 期初建账必须允许指定**录入时间（建账日期）**，不再隐式取当前时间
- `t_period` 增加 `opened_at`（期初建账日期）+ `opened_by`（录入人员ID）+ `opened_by_name`（录入人员名）冗余记录
- 审计日志必须落库 `operator_name`/`operator_id`（修复 Entity 映射）
- 已锁定期间（opening_status=locked）不允许重录/清空（沿用现有约束）

### 1.3 输入参数
| 参数 | 类型 | 约束 |
|------|------|------|
| period | String(6) | YYYYMM，必须存在于 t_period 且 enterprise_id = 当前企业，opening_status != locked |
| openedAt | LocalDateTime | 期初建账日期，允许任意指定；必填（不填则取当前时间，向前兼容） |
| balances | Map<Long, BigDecimal> | 科目ID → 期初余额，试算必须平衡 |

### 1.4 权限要求
- 期初建账/锁定/清空：沿用现有权限（含 @Auditable 审计）

---

## 2. 输出契约

### 2.1 修改接口：POST /api/base/balance/v1/subject-balances/init
```json
// 请求体
{
  "period": "202610",
  "openedAt": "2026-10-01T00:00:00",
  "balances": { "2": 200000, "16": 100000, "61": 300000 }
}
```
- 成功：`{"code":200,"data":null}`
- 失败：`{"code":4xx,"message":"..."}`（错误码见 §4）

### 2.2 新增返回字段：GET /api/base/balance/v1/subject-balances?period=XXX
`t_period` 新增字段透出（前端展示录入人/日期）：
```json
{
  "code": 200,
  "data": {
    "openedAt": "2026-10-01T00:00:00",
    "openedBy": 1,
    "openedByName": "admin",
    "items": [ { "subjectId": 2, "subjectCode": "1002", "beginBalance": 200000 } ]
  }
}
```

### 2.3 审计日志落库修复
- `AuditLogEntity` 映射 `operator_name`/`operator_id`（现有 DB 列），AOP 写入 `getCurrentUsername()`/`SecurityUtils.getCurrentUserId()`
- 期初建账/锁定/清空审计记录可查操作人

---

## 3. 状态流转

```
period.opening_status:
none ──init(建账成功)──▶ entered ──lock──▶ locked
 │                          │                │
 └──clear──▶ none      unlock◀──┘                │
                      clear(仅entered)──▶ none   │
                      locked 不可 clear/init     │
```

### 负向断言
- **locked 期间不可 init/clear**（已锁定期初不可重录/清空）
- **entered 期间不可重复 init**（已存在余额数据，须先 clear）
- **有 POSTED 凭证的期间不可 clear**
- **试算不平衡不可 init**

---

## 4. 异常处理

| 场景 | HTTP | message |
|------|------|---------|
| 期间不存在 | 400 | 会计期间不存在: {period} |
| 期间 status != open | 400 | 会计期间已{status}, 不可录入期初: {period} |
| 期初已锁定 | 409 | 期间 {period} 期初已锁定, 不可重新录入 |
| 已存在余额数据 | 409 | 期间 {period} 已存在余额数据, 请先清空重录 |
| 试算不平衡 | 400 | 期初试算不平衡: 借方合计=X, 贷方合计=Y |
| openedAt 格式非法 | 400 | 建账日期格式错误, 应为 YYYY-MM-DD HH:mm:ss |

---

## 验收标准（BDD）

### 场景 1：期初建账允许指定录入时间
- **Given** 期间 202610 存在且 opening_status=none，登录用户 admin
- **When** 调用 init(period=202610, openedAt=2026-10-01T00:00:00, balances 借贷平衡)
- **Then** 返回成功，t_subject_balance 写入余额，t_period.opened_at=2026-10-01T00:00:00、opened_by=1、opened_by_name=admin
- **And** 负向：未发生锁定期初（opening_status 仍为 entered）

### 场景 2：不传 openedAt 向前兼容
- **Given** 期间 202401 opening_status=none
- **When** 调用 init(period=202401, balances={...})（不带 openedAt）
- **Then** t_period.opened_at ≈ 当前时间（不报错）

### 场景 3：审计日志落库操作人
- **Given** 登录用户 admin 执行期初建账
- **When** 查询 t_audit_log 该记录
- **Then** operator_name=admin、operator_id=1 非空
- **And** 负向：旧记录（修复前）operator_name 为空不受影响

### 场景 4：清空重录（本次数据修复操作）
- **Given** 企业1 期初余额误录在 202311（3 条），start_period=202610
- **When** 解锁 202311 → 清空 202311 → 在 202610 重新 init → 锁定期初
- **Then** 202311 无余额数据，202610 有余额数据，报表按 202610 查询非零

### 场景 5：锁定期间禁止重录
- **Given** 期间 202311 opening_status=locked
- **When** 调用 init/clear
- **Then** 抛出 409「期初已锁定」
- **And** 负向：余额数据未被修改

---

## 风险与决策点

1. **openedAt 默认值**：不填取当前时间（向前兼容旧调用），填了严格校验格式
2. **opened_by_name 冗余**：与 t_audit_log.operator_name 冗余，便于前端直接展示，避免 join
3. **审计修复范围**：仅修复 operator_name/operator_id 映射（现有 DB 列），不新增审计列；method/requestParams 等 exist=false 字段维持现状（表无对应列），如需完整快照另立 SPEC
4. **清空重录操作**：属数据修复，通过现有 unlock→clear→init→lock 接口执行，不走 DDL

---

# === MACHINE-READABLE CONTRACT ===

contract_version: "1.0"

entity: SubjectBalanceEntity
module: balance
table: t_subject_balance

states:
  NONE:
    description: "未建账（期间存在但尚未录入期初余额）"
    initial: true
    terminal: false
  ENTERED:
    description: "已录入未锁定（允许清空重录、允许编辑）"
    initial: false
    terminal: false
  LOCKED:
    description: "已锁定（不可修改、不可清空）"
    initial: false
    terminal: false

transitions:
  - id: T-01
    from: NONE
    to: ENTERED
    trigger: initOpeningBalances
    precondition: "opening_status == NONE && status == open && trial balance == 0"
    postcondition: "opening_status == ENTERED; t_period.opened_at/opened_by/opened_by_name set"
    side_effects:
      - entity: SubjectBalanceEntity
        action: insert_many
      - entity: AuditLogEntity
        action: insert
        note: "operator_name/operator_id 落库"
    test_ref: test_init_with_opened_at_positive

  - id: T-02
    from: ENTERED
    to: LOCKED
    trigger: lockOpeningBalances
    precondition: "opening_status == ENTERED && trial balance balanced"
    postcondition: "opening_status == LOCKED"
    test_ref: test_lock_opening_positive

  - id: T-03
    from: LOCKED
    to: ENTERED
    trigger: unlockOpeningBalances
    precondition: "opening_status == LOCKED && no POSTED voucher constraint"
    postcondition: "opening_status == ENTERED"
    test_ref: test_unlock_opening_positive

  - id: T-04
    from: ENTERED
    to: NONE
    trigger: clearOpeningBalances
    precondition: "opening_status == ENTERED && no POSTED voucher"
    postcondition: "opening_status == NONE; balance rows deleted"
    test_ref: test_clear_opening_positive

constraints:
  - id: C-01
    type: business
    rule: "locked 期间不可 init/clear（负向断言）"
    enforcement: "initOpeningBalances/clearOpeningBalances 前置检查"
  - id: C-02
    type: database
    rule: "t_period 新增 opened_at/opened_by/opened_by_name 列（V135）"
    migration: V135

acceptance_tests:
  - id: AT-001
    description: "期初建账允许指定录入时间并落库"
    method: test_init_with_opened_at_positive
    assertion: "t_period.opened_at == 指定值 && opened_by == 当前用户ID"
    status: missing
  - id: AT-002
    description: "不传 openedAt 向前兼容"
    method: test_init_without_opened_at_positive
    assertion: "t_period.opened_at ≈ now()"
    status: missing
  - id: AT-003
    description: "审计日志操作人落库"
    method: test_audit_log_operator_persisted
    assertion: "t_audit_log.operator_name == 'admin'"
    status: missing
  - id: AT-004
    description: "锁定期间禁止重录"
    method: test_init_locked_period_fails
    assertion: "init throws conflict(409) 期初已锁定"
    status: missing
  - id: AT-005
    description: "清空重录流程（数据修复）"
    method: test_clear_and_reinit_flow
    assertion: "clear 后 NONE，可重新 init 到目标期间"
    status: missing

out_of_scope:
  - "method/requestParams/responseResult 等审计列（表无对应列，另立 SPEC）"
  - "start_period 编辑接口（P57 已限制）"
  - "期初余额批量导入"

dependencies:
  - spec: P57
    relation: "start_period 落库 + 默认期间接口"
  - spec: P24
    relation: "审计追踪 AOP（操作人落库修复）"