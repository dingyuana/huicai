# P59 期初建账一次性规则（已锁定期间禁止解锁/清空/重录 + start_period 重置修复）

> **编号**：HUICAI-SPC-P59
> **版本**：1.0 | **修改日期**：2026-08-14 | **修改人**：Hermes
> **关联需求**：REQ-2026-078（P58 延伸）
> **前置**：P58（期初建账审计增强，已上线）
> **状态**：已实施（2026-08-14，测试通过）

---

## 1. 输入契约

### 1.1 问题背景
1. **期初建账可被反复重录**：P58 上线后，期初数据仍可通过 unlock→clear→init 链路反复清空重建。用户 2026-08-13 18:06-18:07 在界面上操作，把已修复到 202610 的期初数据又搬回 202311，导致报表数据与 start_period 再次错位。
2. **业务规则缺失**：期初建账本质是"企业开账时的一次性初始化"，一旦锁定应进入终态，不允许解锁/清空/重录。当前 `unlockOpeningBalances` 允许 locked→entered（仅校验无 POSTED 凭证），`clearOpeningBalances`/`initOpeningBalances` 已有 locked 拒绝逻辑但不够完整。
3. **start_period 重置 bug**：`resetStartPeriodIfAlone` 使用 `enterpriseMapper.updateById(update)` 且 `update.setStartPeriod(null)`，MyBatis-Plus 默认字段策略 `NOT_NULL` 会跳过 null 字段，导致清空唯一建账期间后 start_period 无法被重置为 null（实测日志显示方法执行了但列未更新，随后 init 新期间时 backfill 因 start_period 非 null 跳过回填，造成 start_period 与数据期间错位）。

### 1.2 新增强制约束
- **已锁定期间（opening_status=locked）禁止解锁**：`unlockOpeningBalances` 对 locked 期间直接拒绝（锁定 = 终态）
- **已锁定期间禁止清空/重录**：沿用 P58 已有约束（clear/init 对 locked 拒绝），本次补齐负向断言测试
- **entered 状态保留清空重录能力**：P58 场景 4（清空重录）仅适用于 entered（未锁定）期间，本次数据修复即走此链路
- **修复 resetStartPeriodIfAlone 的 null 更新 bug**：改用 `LambdaUpdateWrapper.set(EnterpriseEntity::getStartPeriod, null)` 显式置空

### 1.3 输入参数
无新增接口参数（纯约束收紧 + bug 修复 + 数据修复）

### 1.4 权限要求
- 沿用现有权限（含 @Auditable 审计）；解锁接口保留但仅对非 locked 期间调用（实际 locked 直接拒绝）

---

## 2. 输出契约

### 2.1 修改接口：POST /api/base/balance/v1/subject-balances/unlock?period=XXX
- 当 period.opening_status = locked 时：返回 `{"code":400,"message":"期间 {period} 期初已锁定, 锁定后不可解锁/清空/重录, 如需修正请通过红冲凭证方式处理"}`
- 成功场景：无（locked 一律拒绝；非 locked 调用返回「期初未处于锁定状态, 无需解锁」维持现状）

### 2.2 修改接口：clear / init（无签名变化，行为不变）
- clear 对 locked：409「期间 {period} 期初已锁定, 不可清空」（已有）
- init 对 locked：409「期间 {period} 期初已锁定, 不可重新录入」（已有）

### 2.3 数据修复（本次操作，非代码）
- 清空 202311 错误数据 → 在 202610 重新 init（openedAt=2026-10-01 00:00:00, opened_by=admin）→ 锁定 202610 → start_period 修正为 202610
- 最终态：t_subject_balance 仅 202610 有 3 行；202311 opening_status=none 无数据；t_enterprise(1).start_period=202610

---

## 3. 状态流转

```
period.opening_status:
none ──init(建账成功)──▶ entered ──lock──▶ locked(终态, 不可回退)
 │                          │               
 └──clear──▶ none      clear(仅entered)──▶ none
                       locked 不可 unlock/clear/init（本次强制）
```

### 负向断言（新增）
- **locked 期间不可 unlock**（本次新增：锁定后不可解锁，只能红冲凭证修正）
- **locked 期间不可 clear**（已有）
- **locked 期间不可 init**（已有）
- **清空唯一建账期间后 start_period 必须置空**（修复 resetStartPeriodIfAlone bug 的断言）

---

## 4. 异常处理

| 场景 | HTTP | message |
|------|------|---------|
| unlock 已锁定期间（新增） | 400 | 期间 {period} 期初已锁定, 锁定后不可解锁/清空/重录, 如需修正请通过红冲凭证方式处理 |
| unlock 非锁定期间 | 400 | 期间 {period} 期初未处于锁定状态, 无需解锁（维持现状） |
| clear 已锁定期间 | 409 | 期间 {period} 期初已锁定, 不可清空（已有） |
| init 已锁定期间 | 409 | 期间 {period} 期初已锁定, 不可重新录入（已有） |

---

## 验收标准（BDD）

### 场景 1：已锁定期间禁止解锁
- **Given** 期间 202610 opening_status=locked
- **When** 调用 unlock(period=202610)
- **Then** 抛出 400「期初已锁定, 锁定后不可解锁/清空/重录」
- **And** 负向：opening_status 仍为 locked，余额数据未变

### 场景 2：已锁定期间禁止清空/重录（补齐负向）
- **Given** 期间 202610 opening_status=locked
- **When** 调用 clear(period=202610) / init(period=202610)
- **Then** 抛出 409「期初已锁定」
- **And** 负向：余额数据未被删除/修改

### 场景 3：entered 期间仍可清空重录（P58 场景 4 保留）
- **Given** 期间 202311 opening_status=entered（未锁定）
- **When** 调用 clear → init
- **Then** 清空成功后再录入成功

### 场景 4：清空唯一建账期间后 start_period 被置空（bug 修复）
- **Given** 企业 start_period=202610，期间 202610 entered 且为唯一有余额期间
- **When** 调用 clear(period=202610)
- **Then** t_enterprise.start_period IS NULL
- **And** 负向：若还有其他期间有余额，start_period 不被重置

### 场景 5：数据修复后报表与 start_period 一致
- **Given** 数据修复完成（202610 locked 有 3 行，202311 none 无数据）
- **When** 查询资产负债表 period=202610
- **Then** 非零（银行存款 200000），t_enterprise.start_period=202610

---

## 风险与决策点

1. **解锁能力移除的影响**：锁定后只能通过红冲凭证方式修正（铁律 #3 凭证不可变性 + 红蓝对冲精神）。误锁定的唯一补救是数据库人工干预（需老丁确认），故前端锁定时给出强提示文案
2. **前端适配**：移除/禁用「解锁期初」按钮（当前 `BeginningBalanceView.vue` 在 locked 时显示解锁按钮），改为展示"期初已锁定（终态）"提示
3. **start_period 修复方式**：`resetStartPeriodIfAlone` 改用 LambdaUpdateWrapper 显式 set null；`backfillEnterpriseStartPeriod` 维持现状（start_period 为 null 时才回填）
4. **本次数据修复**：202311 错误数据已在 2026-08-14 07:41 CST 通过 API 清空并重录至 202610，start_period 已 SQL 修正为 202610（reset bug 临时绕过）；代码修复后该 bug 不再需要人工 SQL

---

# === MACHINE-READABLE CONTRACT ===

contract_version: "1.0"

entity: PeriodEntity (opening_status) + EnterpriseEntity (start_period)
module: balance/system
table: t_period + t_enterprise

states:
  NONE:
    description: "未建账"
    initial: true
    terminal: false
  ENTERED:
    description: "已录入未锁定（允许清空重录）"
    initial: false
    terminal: false
  LOCKED:
    description: "已锁定（终态：禁止 unlock/clear/init）"
    initial: false
    terminal: true

transitions:
  - id: T-01
    from: NONE
    to: ENTERED
    trigger: initOpeningBalances
    precondition: "opening_status == NONE && status == open && trial balance == 0"
    postcondition: "opening_status == ENTERED; opened_at/opened_by set"
    test_ref: test_init_opening_positive

  - id: T-02
    from: ENTERED
    to: LOCKED
    trigger: lockOpeningBalances
    precondition: "opening_status == ENTERED && trial balance balanced"
    postcondition: "opening_status == LOCKED"
    test_ref: test_lock_opening_positive

  - id: T-03
    from: LOCKED
    to: NONE/ENTERED
    trigger: unlock/clear/init
    precondition: "FORBIDDEN — locked 为终态"
    postcondition: "opening_status 不变; BusinessException(400/409)"
    test_ref: test_unlock_locked_period_fails

  - id: T-04
    from: ENTERED
    to: NONE
    trigger: clearOpeningBalances
    precondition: "opening_status == ENTERED && no POSTED voucher"
    postcondition: "opening_status == NONE; balance rows deleted; start_period reset if alone"
    test_ref: test_clear_opening_positive + test_clear_resets_start_period

constraints:
  - id: C-01
    type: business
    rule: "locked 期间禁止 unlock/clear/init（终态）"
    enforcement: "unlockOpeningBalances 前置检查（新增）+ clear/init 已有检查"
  - id: C-02
    type: business
    rule: "清空唯一建账期间后 start_period 置空（修复 reset bug）"
    enforcement: "resetStartPeriodIfAlone 改用 LambdaUpdateWrapper.set(null)"
  - id: C-03
    type: frontend
    rule: "locked 期间不展示解锁按钮，展示终态提示"
    enforcement: "BeginningBalanceView.vue 移除解锁入口"

acceptance_tests:
  - id: AT-001
    description: "locked 期间 unlock 拒绝"
    method: test_unlock_locked_period_fails
    assertion: "unlock throws 400 期初已锁定; opening_status 保持 locked"
    status: passed
  - id: AT-002
    description: "locked 期间 clear/init 拒绝（负向补齐）"
    method: test_clear_and_init_locked_period_fails
    assertion: "clear/init throws 409 期初已锁定"
    status: passed
  - id: AT-003
    description: "entered 期间清空重录保留"
    method: test_clear_and_reinit_entered_flow
    assertion: "clear 后 NONE，可重新 init"
    status: passed
  - id: AT-004
    description: "清空唯一期间后 start_period 置空"
    method: test_clear_resets_start_period_when_alone
    assertion: "t_enterprise.start_period IS NULL"
    status: passed
  - id: AT-005
    description: "多期间有余额时 start_period 不重置"
    method: test_clear_keeps_start_period_when_others_exist
    assertion: "start_period 保持不变"
    status: passed

out_of_scope:
  - "start_period 编辑接口（P57 已限制）"
  - "红冲凭证修正流程（既有功能）"
  - "历史数据迁移（本次数据修复仅针对企业1）"

dependencies:
  - spec: P58
    relation: "opened_at/opened_by 落库 + 审计日志修复（已上线）"
  - spec: P57
    relation: "start_period 落库 + 默认期间接口"