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

## 6. MACHINE-READABLE CONTRACT

```yaml
contract_version: "1.0"
module: arap
entity: ArapSettlement
acronym: P43
contracts:
  - id: P43-C1
    description: "核销日志 tab 切换后加载数据"
    type: ui
    expected: "tab-click → fetchReconLogs() → records.length > 0"
  - id: P43-C2
    description: "清空核销数据 API"
    type: api
    endpoint: POST /api/v1/clear-data/settlements
    expected: "200"
  - id: P43-C3
    description: "核销单状态机校验"
    type: unit_test
    target: ArapSettlementServiceImplTest
    assertion: "每个状态转换方法前置校验当前状态合法性"
  - id: P43-C4
    description: "核销单状态转换 DRAFT→CONFIRMED"
    type: api
    endpoint: POST /api/v1/arap-settlements/{id}/confirm
    expected: "200 + status == CONFIRMED"
constraints:
  - id: C-P43-1
    type: state_machine
    rule: "核销单状态必须按 DRAFT→CONFIRMED→VOUCHERED→POSTED/CANCELLED 顺序转换"
    enforcement: "ArapStatus.canTransition() 前置校验"

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