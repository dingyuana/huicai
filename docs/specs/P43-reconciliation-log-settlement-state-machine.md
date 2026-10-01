# P43 SPEC — 核销日志修复 + 清空核销单据 + 核销单状态机

> **编号**：HUICAI-SPC-043 | **优先级**：P0
> **依据**：用户反馈核销日志无数据、数据维护缺少清空核销功能、核销单缺乏状态机管理
> **关联需求**：REQ-2026-058, REQ-2026-059, REQ-2026-060
> **版本**：V1.0 | **日期**：2026-07-09

> **test_ref**：ArapSettlementServiceImplTest, ArapSettlementRestContractTest
---

## 1. 输入契约
→ 见本文 [## 3. P43-3：核销单状态机管理 — 状态定义与转换规则](#3-p43-3核销单状态机管理)

## 2. 输出契约
→ 见本文 [## 4. 验收标准 — AT-P43-1 至 AT-P43-7 验收清单](#4-验收标准)

## 3. 状态流转
→ 见本文 [## 3.1 状态定义 — ArapSettlement 状态机流转图](#31-状态定义)

## 4. 异常处理
→ 见本文 [## 3.4 状态校验 — BusinessException 前置校验逻辑](#34-状态校验)

## 0. 现状审计

### 审计项 1：核销日志为什么没有数据

**后端**：`GET /api/v1/reconciliation/logs/page` 已存在，查询 `t_reconciliation_log` 表

| 检查项 | 结果 |
|--------|------|
| 数据库中是否有数据 | ✅ 有 1 条记录（id=9, source_doc_type=receipt, allocated_amount=1200.00） |
| 后端接口是否正常 | ✅ 正常返回 200 |
| 前端默认查询条件 | `logQuery = { sourceDocType: '', current: 1, size: 20 }` |
| 前端 `fetchReconLogs()` | 调 `pageReconLogs(logQuery.value)` |

**结论**：后端数据存在，接口正常。问题出在**前端** — `SettlementList.vue` 的"核销日志" tab 在页面加载时默认不显示数据（需切换到该 tab 后调用 `fetchReconLogs()`）。已确认 `onMounted` 只调了 `fetchSettlements()`（核销单 tab），没有调 `fetchReconLogs()`。当用户切换到"核销日志" tab 时，`@tab-change` 事件触发了 `fetchReconLogs()`，但**默认的 filter 条件为空字符串**应该返回全部。实际可能问题在于查询条件或 tab 切换逻辑。

**需要仔细确认的**：SettlementList.vue 有两个 tab，`activeTab` 默认值为 `'settlement'`。切换到 `'reconLog'` 时通过 `@tab-change` 回调触发 `fetchReconLogs()`。但 `el-tabs` 的 `@tab-change` 和 `@tab-click` 可能不是同一个事件。当前代码用 `@tab-change="onTabChange"`。

**修复方向**：确认 tab 切换事件名称正确，核销日志默认能加载数据。

### 审计项 2：清空核销单据功能缺失

**后端**：`ClearDataService` 已有 4 个方法，无清空核销单/核销日志的逻辑

| 方法 | 删除内容 | 是否涉及核销数据 |
|------|---------|----------------|
| `clearBankStatements()` | 银行流水 + 关联凭证 + 关联业务单据 | ✅ 会连带删除 |
| `clearInvoiceRecords()` | 发票 + 关联凭证 + 关联业务单据 | ✅ 会连带删除 |
| `clearVouchers()` | 所有凭证 | ⚠️ 核销单凭证 |
| `clearBusinessDocs()` | 所有业务单据 + 核销明细 + 核销日志 | ✅ 已包含 |

**需要新增**：`clearSettlements()` — 独立清空核销数据，不清除业务单据本身

### 审计项 3：核销单状态机缺失

**当前状态**：

| 状态 | 含义 | 存在位置 |
|------|------|---------|
| DRAFT | 草稿 | 代码中未显式定义，`create()` 默认 |
| CONFIRMED | 已确认 | `confirm()` 方法 |
| VOUCHERED | 已生成凭证 | `generateVoucher()` 后 |
| CANCELLED | 已取消 | `reverse()` 后 |

**缺失**：
- 无统一的 `StateMachineConfig` 定义（其他实体如 `t_voucher` 有）
- 无 `@StatusChangeable` 的完整状态转换规则
- 无审批流程（`APPROVED` 状态缺失）
- `confirm()` 和 `reverse()` 未校验状态是否合法

---

## 1. P43-1：修复核销日志无数据

### 1.1 修复方案

```javascript
// SettlementList.vue
// 当前: @tab-change 事件可能不匹配
// 改为: @tab-click="onTabClick"
// 或直接在 tab 切换时调用 fetchReconLogs()
```

### 1.2 改动点

| 文件 | 改动 |
|------|------|
| `SettlementList.vue` | 确认 `@tab-change` → `@tab-click`，确保切换 tab 时加载数据 |
| `SettlementList.vue` | `onMounted` 时也加载一次 `fetchReconLogs()`（可异步，避免阻塞） |

---

## 2. P43-2：清空核销单据功能

### 2.1 后端

**ClearDataService.java** 新增方法：

```java
public int clearSettlements() {
    int sl = 0, se = 0, tl = 0;
    // 1. 先清除核销单明细
    try { se = settlementEntryMapper.physicalDeleteAll(); } catch (Exception e) { log.warn("settlement_entry: {}", e.getMessage()); }
    // 2. 清除核销单
    try { sl = settlementMapper.physicalDeleteAll(); } catch (Exception e) { log.warn("settlement: {}", e.getMessage()); }
    // 3. 清除核销日志
    try { tl = reconciliationLogMapper.physicalDeleteAll(); } catch (Exception e) { log.warn("recon_log: {}", e.getMessage()); }
    // 4. 重置业务单据的核销金额
    try { 
        int updated = businessDocMapper.resetSettlementAmounts();
        log.info("业务单据核销金额已重置: {}", updated);
    } catch (Exception e) { log.warn("businessDoc reset: {}", e.getMessage()); }
    log.info("清空核销数据: settlements={}, entries={}, logs={}", sl, se, tl);
    return sl + se + tl;
}
```

**BusinessDocMapper.java** 新增：

```java
@Update("UPDATE t_business_doc SET settled_amount = 0, unsettled_amount = amount WHERE deleted = 0")
int resetSettlementAmounts();
```

### 2.2 前端

**ClearDataView.vue** 新增清空核销卡片：

```html
<el-card>
  <strong>清空核销数据</strong>
  <p>清空所有核销单、核销明细和核销日志，重置业务单据核销金额。</p>
  <el-popconfirm title="确定清空所有核销数据?" @confirm="onClear('settlements')">
    <el-button type="danger" plain>清空核销数据</el-button>
  </el-popconfirm>
</el-card>
```

**ClearDataView.vue** 的 `onClear` 方法增加 `'settlements'` 分支：

```javascript
case 'settlements':
  await clearSettlements()
  break
```

---

## 3. P43-3：核销单状态机管理

### 3.1 状态定义

```
        ┌──────────────────────────────────────┐
        │           ArapSettlement              │
        │     @StatusChangeable(ARAP_SETTLEMENT)│
        └──────────────────────────────────────┘

DRAFT ──→ CONFIRMED ──→ VOUCHERED ──→ POSTED
  │           │                            │
  │           ↓                            │
  │       REJECTED                         │
  │                                        │
  └──→ CANCELLED ←─────────────────────────┘
```

### 3.2 状态转换规则

| 当前状态 | 目标状态 | 操作 | 条件 |
|---------|---------|------|------|
| DRAFT | CONFIRMED | `confirm()` | 核销金额校验通过 |
| CONFIRMED | VOUCHERED | `generateVoucher()` | 凭证生成成功 |
| VOUCHERED | POSTED | `postVoucher()` | 凭证过账成功 |
| CONFIRMED | REJECTED | `reject()` | 需填写原因 |
| VOUCHERED | CANCELLED | `reverse()` | 反核销，需原因 |
| DRAFT | CANCELLED | `cancel()` | 取消草稿 |

### 3.3 新增/修改方法

**ArapSettlementServiceImpl.java**：

| 方法 | 说明 | 状态转换 | 是否已存在 |
|------|------|---------|----------|
| `confirm(id)` | 确认核销 | DRAFT→CONFIRMED | ✅ 已有，需加固校验 |
| `reject(id, reason)` | 驳回 | CONFIRMED→REJECTED | ❌ 新增 |
| `approve(id)` | 审批通过 | CONFIRMED→VOUCHERED | ❌ 新增（与 generateVoucher 区分）|
| `reverse(id, reason)` | 反核销 | VOUCHERED→CANCELLED | ✅ 已有，需加固 |
| `cancel(id)` | 取消 | DRAFT→CANCELLED | ❌ 新增 |

### 3.4 状态校验

在 `confirm()`、`reverse()`、`generateVoucher()` 方法前增加前置校验：

```java
// 校验当前状态是否允许转换
if (!ArapStatus.canTransition(from, to)) {
    throw BusinessException.badRequest("核销单状态不允许此操作: " + entity.getStatus());
}
```

---

## 4. 验收标准

| ID | 描述 | 断言 |
|----|------|------|
| AT-P43-1 | 核销日志 tab 加载后有数据 | `fetchReconLogs()` 返回 `records.length > 0` |
| AT-P43-2 | 清空核销后 `t_arap_settlement` 为空 | `SELECT COUNT(*) FROM t_arap_settlement WHERE deleted=0` = 0 |
| AT-P43-3 | 清空核销后业务单据核销金额重置 | `business_doc.settled_amount = 0 AND unsettled_amount = amount` |
| AT-P43-4 | DRAFT→CONFIRMED 状态转换成功 | `confirm(id)` → 状态变为 CONFIRMED |
| AT-P43-5 | DRAFT→CANCELLED 状态转换成功 | `cancel(id)` → 状态变为 CANCELLED |
| AT-P43-6 | 非法状态转换抛异常 | `reverse(DRAFT)` → BusinessException |
| AT-P43-7 | 清空核销数据前端按钮可见可用 | 按钮点击后调后端 API 成功 |

---

## 5. 不做事项

- ❌ 不改核销日志后端查询逻辑（接口本身正常）
- ❌ 不改 ArapSettlementEntity 的表结构
- ❌ 不新增独立审批页面（复用现有流程）
- ❌ 不修改现有核销推荐算法

---

# MACHINE-READABLE CONTRACT

> **P105 核对修正**：本节原为 `## 6. MACHINE-READABLE CONTRACT` + `contracts:` 列表，
> 校验器既认不出这个标题形态（只找 `# MACHINE-READABLE CONTRACT`），也读不懂 `contracts`
> 字段（只认 `states`/`transitions`/`rules`）⇒ **该节从未被校验过，属于恒绿假象**。
> 且原约束写的 `DRAFT→CONFIRMED→VOUCHERED→POSTED/CANCELLED` 与代码不符：
> ① `POSTED` 是凭证状态，`chk_settlement_status` 里**没有**；② `DRAFT→CONFIRMED` 不合法，
> 必须经 `SUBMITTED`。已按 `ArapStatus.canTransition()` 重写。

```yaml
contract_version: "1.0"
spec_file: "P43-reconciliation-log-settlement-state-machine.md"
spec_id: P43
entity: ArapSettlement
module: arap
table: t_arap_settlement
last_updated: "2026-10-01"
implementation_status: implemented

# 权威来源：ArapStatus.canTransition()（ArapStatus.java）与 chk_settlement_status
check_allowed_set:
  - DRAFT
  - SUBMITTED
  - CONFIRMED
  - REJECTED
  - VOUCHERED
  - REVERSED
  - CANCELLED

states:
  DRAFT:
    description: "草稿，仅草稿可修改（ArapStatus.isModifiable）"
    initial: true
    terminal: false
  SUBMITTED:
    description: "已提交待审批"
    terminal: false
  CONFIRMED:
    description: "已确认/已审批，可生成凭证"
    terminal: false
  REJECTED:
    description: "已驳回，终态"
    terminal: true
  VOUCHERED:
    description: "已生成凭证，可反核销"
    terminal: false
  REVERSED:
    description: "已反核销，终态"
    terminal: true
  CANCELLED:
    description: "已取消，终态"
    terminal: true

transitions:
  - id: submit
    from: DRAFT
    to: SUBMITTED
    trigger: submit
    precondition: "status == DRAFT（ArapStatus.isSubmitable）"
    implementation: ArapSettlementServiceImpl.submit()
    test_ref: submit_positive

  - id: cancel
    from: [DRAFT, SUBMITTED]
    to: CANCELLED
    trigger: cancel
    precondition: "status ∈ {DRAFT, SUBMITTED}（ArapStatus.isCancellable）"
    implementation: ArapSettlementServiceImpl.cancel()
    test_ref: cancel_positive

  - id: approve
    from: SUBMITTED
    to: CONFIRMED
    trigger: approve
    precondition: "status == SUBMITTED（ArapStatus.isApprovable）。**DRAFT 不可直接确认**"
    implementation: ArapSettlementServiceImpl.approve()
    test_ref: approve_positive
    note: "原 BDD 场景 1 写「DRAFT → confirm → CONFIRMED」，与 isApprovable 只认 SUBMITTED 矛盾，已修正"

  - id: reject
    from: SUBMITTED
    to: REJECTED
    trigger: reject
    precondition: "status == SUBMITTED（ArapStatus.isRejectable）"
    implementation: ArapSettlementServiceImpl.reject()
    test_ref: reject_positive

  - id: voucher
    from: CONFIRMED
    to: VOUCHERED
    trigger: generateVoucher
    precondition: "status == CONFIRMED（ArapStatus.canTransition(CONFIRMED, VOUCHERED)）"
    implementation: ArapSettlementServiceImpl.generateVoucher()
    test_ref: generateVoucher_positive

  - id: reverse
    from: [CONFIRMED, VOUCHERED]
    to: REVERSED
    trigger: reverse
    precondition: "status ∈ {CONFIRMED, VOUCHERED}（ArapStatus.isSettlementReversible）"
    implementation: ArapSettlementServiceImpl.reverse()
    test_ref: reverse_positive
    note: >-
      ✅ 已收敛（2026-10-01）。此前守卫 isSettlementReversible（CONFIRMED 或 VOUCHERED）
      与 canTransition 图分裂：图里只有 VOUCHERED→REVERSED，而 reverse() 从不调 canTransition，
      故生产中真的会发生 CONFIRMED→REVERSED，声明的图却不承认。
      判定为**图不完整**（非守卫过宽 —— 反核销「已确认未制证」的核销单本属合法业务，
      异常文案亦明写「仅已确认或已记账的核销单可反核销」）。
      已补 canTransition 的 CONFIRMED→REVERSED 边，并把 reverse() 的守卫改为
      **直接用状态机图**，消除第二个判定源。
      两者一致性由 ArapStatusCanTransitionTest.guardPredicateAgreesWithGraph 锁定。

deviations:
  - "P105: 原约束 DRAFT→CONFIRMED→VOUCHERED→POSTED/CANCELLED 有两处错误 —— POSTED 非本表状态；DRAFT→CONFIRMED 缺 SUBMITTED 中间态"
  - "P105: 原节标题形态导致校验器从未解析，属恒绿假象"

constraints:
  - id: C-P43-1
    type: database
    rule: "chk_settlement_status 允许集恰为 DRAFT/SUBMITTED/CONFIRMED/REJECTED/VOUCHERED/REVERSED/CANCELLED"
    enforcement: "PostgreSQL CHECK"

  - id: C-P43-2
    type: state_machine
    rule: "状态必须按 canTransition() 图转换： DRAFT→{SUBMITTED,CANCELLED}; SUBMITTED→{CONFIRMED,REJECTED,CANCELLED}; CONFIRMED→VOUCHERED; VOUCHERED→REVERSED"
    enforcement: "ArapStatus.canTransition() + 各 Service 前置校验"

acceptance_tests:
  - id: AT-S01
    description: "DRAFT → SUBMITTED"
    method: submit_positive
    assertion: "status == SUBMITTED"
    status: covered
  - id: AT-S02
    description: "SUBMITTED → CONFIRMED"
    method: approve_positive
    assertion: "status == CONFIRMED"
    status: covered
  - id: AT-S03
    description: "SUBMITTED → REJECTED（终态）"
    method: reject_positive
    assertion: "status == REJECTED"
    status: covered
  - id: AT-S04
    description: "DRAFT/SUBMITTED → CANCELLED（终态）"
    method: cancel_positive
    assertion: "status == CANCELLED"
    status: covered
  - id: AT-S05
    description: "CONFIRMED → VOUCHERED"
    method: generateVoucher_positive
    assertion: "status == VOUCHERED"
    status: covered
  - id: AT-S06
    description: "VOUCHERED → REVERSED（终态）"
    method: reverse_positive
    assertion: "status == REVERSED"
    status: covered
  - id: AT-S07
    description: "DRAFT 不可直接 approve（守卫只认 SUBMITTED）"
    method: approve_from_draft_throws
    assertion: "BusinessException"
    status: covered

notes:
  - "P43-C1 核销日志 tab 切换后加载数据 —— UI 契约，校验器不消费"
  - "P43-C2 清空核销数据 API POST /api/v1/clear-data/settlements —— 维护接口，注意其为无 WHERE 全表 DELETE，返回行数含种子数据，断言须取基线后比增量"
```

---

## 8. BDD 验收标准

### 场景 1：核销单 DRAFT→CONFIRMED 状态转换成功
**Given** 一张核销单处于 DRAFT 状态，核销金额校验通过
**When** 用户调用 confirm(id)
**Then** 核销单状态变为 CONFIRMED，且校验通过后不抛出 BusinessException

### 场景 2：非法状态转换抛出异常
**Given** 一张核销单处于 DRAFT 状态
**When** 用户调用 reverse(id) 尝试反核销
**Then** 系统抛出 BusinessException，提示"核销单状态不允许此操作"

### 场景 3：清空核销数据后业务单据核销金额重置
**Given** 存在多条核销单记录和对应的业务单据（已结算金额非零）
**When** 用户执行清空核销数据操作
**Then** t_arap_settlement 表数据被清除，t_business_doc 的 settled_amount 重置为 0，unsettled_amount 恢复为原金额
```