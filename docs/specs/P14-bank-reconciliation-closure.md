# P14 SPEC — 银企对账 E2E 闭环 (match + confirmMatch + 状态机)
> **版本**：V1.0 | **最后修改**：2026-07-19 | **作者**：Hermes
> **状态**：✅ 生效

> **编号**：HUICAI-SPC-014（接口已 9 个方法, 缺 `confirmMatch` 人工确认）
> 目标：自动匹配 → 人工确认 → 锁定 → 完成对账
> 工期：1 批（补 1 方法 + 1 端点 + 2 单测）

> **test_ref**：BankReconciliationServiceImplTest
---

> **关联需求**: REQ-2026-020

## 1. 输入契约
→ 见本文 [现状摸底 / 银企对账参数定义] 章节

## 2. 输出契约
→ 见本文 [验收标准 / 测试用例 / 响应结构] 章节

## 3. 状态流转
→ 见本文 [对账状态机图 / 状态常量 / 状态转换方法] 章节

## 4. 异常处理
→ 见本文 [BusinessException 抛出点 / 错误码定义] 章节

## 1. 现状摸底 (2026-06-15)

| 文件 | 状态 |
|---|---|
| `BankReconciliationService` | ✅ 接口 9 方法 (P4 阶段) |
| `BankReconciliationServiceImpl` | ✅ 4 方法实现, **缺 `confirmMatch` 人工确认** |
| `BankReconciliationServiceImplTest` | ✅ 18 测试 (P7 阶段) |
| `Controller` | ⚠️ 需补 `confirmMatch` 端点 |

**已实现**:
- `calculateScore(...)` — 5 维评分 (amount/date/name/desc/ref)
- `runMatching(...)` — 批量自动匹配 (≥85 MATCHED, 60-84 PENDING_CONFIRM, <60 UNMATCHED)
- `lockReconciliation` / `unlockReconciliation` — 分布式锁

**缺**:
- `confirmMatch(...)` — 人工确认 PENDING_CONFIRM → MATCHED
- `rejectMatch(...)` — 人工驳回 PENDING_CONFIRM → UNMATCHED
- Controller 端点暴露

---

## 2. P14-1 任务 (本批)

### 2.1 补方法 + 端点

```java
// BankReconciliationService.java
record ConfirmResult(Long statementId, Long journalId, String newStatus) {}

ConfirmResult confirmMatch(Long statementId, Long journalId, String operator);
ConfirmResult rejectMatch(Long statementId, Long journalId, String operator);
```

```java
// Controller
POST /api/v1/bank-reconciliation/{statementId}/confirm
POST /api/v1/bank-reconciliation/{statementId}/reject
```

### 2.2 单测 (2 个)

| # | 测试 | 覆盖方法 |
|---|---|---|
| 1 | `confirmMatch_PENDING_CONFIRM_变MATCHED` | `confirmMatch` |
| 2 | `rejectMatch_PENDING_CONFIRM_变UNMATCHED` | `rejectMatch` |

### 2.3 状态机

```
UNMATCHED ──→ runMatching (≥85) ──→ MATCHED (auto)
UNMATCHED ──→ runMatching (60-84) ──→ PENDING_CONFIRM
UNMATCHED ──→ runMatching (<60) ──→ UNMATCHED (留待下期)

PENDING_CONFIRM ──→ confirmMatch ──→ MATCHED
PENDING_CONFIRM ──→ rejectMatch ──→ UNMATCHED

MATCHED ──→ lockReconciliation ──→ LOCKED
```

---

## 3. 不在 P14 范围

- 银行对账单导入 (依赖 P1 Excel 导入能力, 已完成)
- 银行流水与日记账双向同步 (P15 候选)
- 智能提示 (哪笔该优先处理)
- 跨期调节 (期末调账)

---

## 4. 测试验收

**目标**: 290 → 292 (+2 测试, 0 fail, 0 error)

---
## 验收标准

| ID | 描述 | 断言 |
|----|------|------|
| AT-P14-1 | 自动匹配生成匹配记录 | `match(period) → reconciliation_log count > 0` |
| AT-P14-2 | 人工确认后匹配锁定 | `confirmMatch(id) → log.status == 'CONFIRMED'` |
| AT-P14-3 | 余额调节表生成 | `execute(period) → reconciliation_report exists` |
## BDD 验收标准

| ID | Given-When-Then |
|----|----------------|
| RCL-01 | Given 有银行流水+银行对账单 When 执行自动匹配 Then 相同日期金额配对，状态=待确认 |
| RCL-02 | Given 有匹配记录 When 人工 confirmMatch Then 状态=已确认，双方锁定 |
| RCL-03 | Given 已确认匹配 When 再次匹配 Then 排除已确认记录，避免重复 |
| RCL-04 | Given 无匹配银行对账单 When 执行匹配 Then 返回空列表，不抛异常 |
