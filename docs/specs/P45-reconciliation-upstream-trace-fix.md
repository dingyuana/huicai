# P45 SPEC — 核销上游来源追溯修复

> **编号**：HUICAI-SPC-045 | **优先级**：P0
> **关联需求**：REQ-2026-059
> **版本**：V1.0 | **日期**：2026-07-09

---

## 0. 现状与根因分析

### 数据流

核销执行时（`ReconciliationServiceImpl.execute()`）会同时创建两样东西：

```
execute()
  ├── (1) ReconciliationLog ← sourceDocType, sourceDocId, targetDocType, targetDocId
  └── (2) ArapSettlement ← partyId, partyType, totalAmount
          └── ArapSettlementEntry ← businessDocId, settledAmount
```

**但两者之间没有关联字段**：`t_reconciliation_log` 没有 `settlement_id` 列，`t_arap_settlement` 没有 `log_id` 列。核销单详情页点击"上游来源"时：

```
前端: trace(settlement.id)  →  后端 trace(logId)   →  查 reconciliation_log
                                                           ↓
                                                     log 不存在 (因为传的是 settlement.id)
                                                           ↓
                                                     fallback → 查 settlement → 返回空上游
```

**上游为空的原因**：trace 接口先查 `reconciliation_log`（需要 logId），再查 settlement 做降级。降级只返回 settlement 基本信息，不包含上游单据。

### 关联字段问题

`ReconciliationLogEntity` 有 `targetDocId`（映射 `target_doc_id`，指向发票ID），但缺少 `targetBusinessDocId`（映射 `target_business_doc_id`，指向业务单据ID）。表结构中有这个列（V81 已加），但代码没写字段。

---

## 1. 修复方案

### 思路

让 settlement 和 reconciliation_log 之间建立关联，使 trace 接口能通过 settlement ID 找到对应的 log。

**方案 A（推荐）⭐：trace 接口中通过金额匹配找到 log**

当前 trace 方法已有 settlement 降级逻辑：

```
logId → 查 log → 没找到 → 查 settlement → 找到 →
  用 settlement.totalAmount 匹配 log.allocatedAmount → 找到 log → 完整 trace
```

但 `ReconciliationLogEntity` 缺少 `targetBusinessDocId` 字段，导致 `getTargetBusinessDocId()` 不存在，编译失败（之前已遇到）。修复：

1. `ReconciliationLogEntity` 加 `targetBusinessDocId` 字段
2. `ReconciliationServiceImpl.trace()` 中的降级逻辑修复（移除不可用的 `getSettlementId` 引用）

**方案 B**：在 `t_reconciliation_log` 加 `settlement_id` 列，创建时回填
**方案 C**：在 `t_arap_settlement` 加 `log_id` 列，创建时回填

> **选择方案 A**，改动最小，无需 migration。

### 1.1 ReconciliationLogEntity 补充字段

```java
/** 目标业务单据 ID（V81 已添加列） */
private Long targetBusinessDocId;
```

### 1.2 ReconciliationServiceImpl.trace() 修复

当前降级逻辑：
```java
// 用 settlement.totalAmount 匹配 log.allocatedAmount
log = logMapper.selectOne(new LambdaQueryWrapper<ReconciliationLogEntity>()
    .eq(ReconciliationLogEntity::getAllocatedAmount, settlement.getTotalAmount())
    .last("LIMIT 1"));
```

已有，但需移除不可用的 `ReconciliationLogEntity::getSettlementId` 引用（之前已修）。

### 1.3 核销执行时回填 targetBusinessDocId

```java
// ReconciliationServiceImpl.execute() 中创建 log 时
reconLog.setTargetBusinessDocId(request.targetDocId());
```

当前 `targetDocId` 已传入业务单据ID（`request.targetDocId()`），只需增加一行赋值。

---

## 2. 改动点

| 文件 | 改动 |
|------|------|
| `ReconciliationLogEntity.java` | 新增 `targetBusinessDocId` 字段 |
| `ReconciliationServiceImpl.java` | execute() 中设置 `reconLog.setTargetBusinessDocId()` |

---

## 3. 验收标准

| ID | 描述 | 断言 |
|----|------|------|
| AT-P45-1 | 核销执行后 reconciliation_log.target_business_doc_id 不为空 | `SELECT target_business_doc_id FROM t_reconciliation_log WHERE id = newLogId` != null |
| AT-P45-2 | trace 接口传入 settlement ID 能返回上游数据 | `trace(settlementId).upstream.receipt != null` |
| AT-P45-3 | 前端上游来源抽屉有数据 | 点击"上游来源"按钮后抽屉中显示来源单据信息 |

---

## 4. 不做事项

- ❌ 不新增 migration（字段已存在 V81）
- ❌ 不改前端 SettlementList.vue 的 trace 调用逻辑
- ❌ 不修改 settlement 创建流程

---

## 5. MACHINE-READABLE CONTRACT

```yaml
contract_version: "1.0"
module: arap
entity: ReconciliationLog
acronym: P45
contracts:
  - id: P45-C1
    description: "ReconciliationLog 补充 targetBusinessDocId 字段"
    type: entity_field
    expected: "ReconciliationLogEntity.targetBusinessDocId 映射 target_business_doc_id"
  - id: P45-C2
    description: "核销执行时回填 targetBusinessDocId"
    type: code_audit
    expected: "execute() 中 reconLog.setTargetBusinessDocId(request.targetDocId())"
  - id: P45-C3
    description: "trace 接口通过 settlement ID 查到上游数据"
    type: api
    endpoint: GET /api/v1/reconciliation/{settlementId}/trace
    expected: "response.upstream.receipt != null"
```