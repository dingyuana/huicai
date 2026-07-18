# SPEC: Phase 4 — 对方名称按业务方向识别

> **编号**：HUICAI-SPC-004
> **版本**：v1.0 | **修改日期**：2026-06-14 | **修改人**：— | **修改内容**：添加编号头部
> 关联：FR-BANK-01.3（列名映射）、FR-BANK-03（智能分类）
> 上游：`docs/需求分析/01-银行流水智能处理.md`

---

> **关联需求**: REQ-2026-005

## 1. 输入契约
→ 见本文 二、功能规格（FR-COUNTERPARTY-01 至 FR-COUNTERPARTY-03）、四、接口设计

## 2. 输出契约
→ 见本文 五、验收标准（8 条验收条件）

## 3. 状态流转
→ 见本文 二、功能规格（INCOME/EXPENSE 方向判断规则、回退链）

## 4. 异常处理
→ 见本文 三、影响范围（BusinessException 抛出点不在本 SPEC 范围内，改动均为内部逻辑修正）

## 一、现状与问题

### 1.1 当前行为

`BankStatementExcelImportService.parseRow()` 中对方名称的提取逻辑：

```java
if ("INCOME".equals(txType) && payeeIdx != null) {
    counterparty = PAYEE_NAME;   // 收款时取"收款人名称"
} else if ("EXPENSE".equals(txType) && payerIdx != null) {
    counterparty = PAYER_NAME;   // 付款时取"付款人名称"
}
```

### 1.2 问题

此逻辑**方向颠倒**。从银行流水视角：

| 流水方向 | 我方角色 | 对方是谁 | 当前代码选取 | 应选取 |
|----------|---------|---------|-------------|-------|
| INCOME（收款） | 收款人（收钱方） | **付款人**（付钱给我方的人） | PAYEE_NAME（我方）❌ | PAYER_NAME（对方）✅ |
| EXPENSE（付款） | 付款人（付钱方） | **收款人**（我方付钱给的人） | PAYER_NAME（我方）❌ | PAYEE_NAME（对方）✅ |

举例：
- 客户 A 向我方支付 10,000 元货款 → `txType=INCOME`。对方是**客户 A（付款人）**，当前代码却取了「收款人」（即我方自己）
- 我方支付 5,000 元给供应商 B → `txType=EXPENSE`。对方是**供应商 B（收款人）**，当前代码却取了「付款人」（即我方自己）

**影响**：导入后的`counter_account`字段填充了己方信息而非真正对手方，后续对账、往来核销均基于错误数据。

### 1.3 根因

原始需求（Go 版移植）对 `payer_name` / `payee_name` 的语义理解与银行对账单实际列名含义不一致。银行对账单的「付款人名称」列通常指**往账（我方付款）的对手方**，而非我方的开户名。

---

## 二、功能规格

### FR-COUNTERPARTY-01: 按方向选取对方名称

**目标**：根据流水方向（txType/direction）正确选取对手方名称。

**规则**：

```
IF txType == 'INCOME' (收款，资金流入)
  → counterparty = PAYER_NAME 列（付款人 = 付钱给我方的人）
  → 若 PAYER_NAME 为空 → 回退 PAYEE_NAME → 回退 COUNTER_ACCOUNT

IF txType == 'EXPENSE' (付款，资金流出)
  → counterparty = PAYEE_NAME 列（收款人 = 我方付钱给的人）
  → 若 PAYEE_NAME 为空 → 回退 PAYER_NAME → 回退 COUNTER_ACCOUNT

IF txType 为空（无法判断方向）
  → 按 direction 字段: direction=in → 按收款规则; direction=out → 按付款规则
  → direction 也为空 → PAYER_NAME → PAYEE_NAME → COUNTER_ACCOUNT
```

### FR-COUNTERPARTY-02: 对方账户按方向联动

**目标**：当 Excel 中有独立的「对方账号」列（`COUNTER_ACCOUNT`）时，与名称同步正确选取。

**现状**：`COUNTER_ACCOUNT` 在所有方向下都直接使用同一个列值。
**变更**：新增 `PAYER_ACCOUNT` / `PAYEE_ACCOUNT` 概念（可选，如有独立列）。

对应关系：

```
INCOME  → 对方账号 = PAYER_ACCOUNT（付款人账号）
EXPENSE → 对方账号 = PAYEE_ACCOUNT（收款人账号）
兜底    → 对方账号 = COUNTER_ACCOUNT（对方账户）
```

> 注：大部分银行对账单只有一列「对方账号」，此时所有方向共用 COUNTER_ACCOUNT，无需变更。此规则仅当 Excel 同时存在「付款人账号」和「收款人账号」两列时生效。

### FR-COUNTERPARTY-03: 列名映射扩展

在 `ColumnMappingResolver.Field` 枚举中确认或补充以下别名，确保常见银行对账单列名可被正确识别：

| 系统字段 | 需覆盖的别名（含中英文变体） |
|---------|--------------------------|
| PAYER_NAME | 付款人名称、付款人、付款方名称、payer name、remitter |
| PAYEE_NAME | 收款人名称、收款人、收款方名称、payee name、beneficiary |
| PAYER_ACCOUNT | 付款人账号、付款方账号、payer account、remitter account |
| PAYEE_ACCOUNT | 收款人账号、收款方账号、payee account、beneficiary account |
| COUNTER_ACCOUNT | 对方账号、对方账户、对方户名、counterparty account、counter account |

---

## 三、影响范围

### 3.1 需要修改的文件

| 文件 | 改动 |
|------|------|
| `BankStatementExcelImportService.java` | `parseRow()` 方法中 counterparty 选取逻辑（~10 行核心变更） |
| `ColumnMappingResolver.java` | 可选：补充 PAYER_ACCOUNT / PAYEE_ACCOUNT 别名（如 Excel 有此列） |
| `FallbackHeuristicServiceTest.java` | 可选：新增方向对应的对方名称测试用例 |
| `ColumnMappingResolverTest.java` | 检查现有对方列名映射是否受影响 |

### 3.2 不修改的文件

- 前端 `BankStatementView.vue` — 展示逻辑不变，只改后端数据正确性
- 数据库表结构 — 不改 schema，`counter_account` 字段复用
- 已导入的历史数据 — 不回溯修正（仅修复新导入）

---

## 四、接口设计

无新增 API 端点。此 SPEC 仅修改内部解析逻辑，对外接口不变：

| 方法 | 路径 | 说明 | 是否变更 |
|------|------|------|---------|
| POST | `/api/v1/bank-statements/preview-excel` | Excel 预览 | 内部逻辑修正，响应不变 |
| POST | `/api/v1/bank-statements/confirm-import` | 确认导入 | 不受影响 |
| POST | `/api/v1/bank-statements/import-excel` | 一步式导入 | 内部逻辑修正 |

---

## 五、验收标准

1. ✅ 导入 INCOME 流水：`counter_account` = PAYER_NAME 列的值（非己方名称）
2. ✅ 导入 EXPENSE 流水：`counter_account` = PAYEE_NAME 列的值（非己方名称）
3. ✅ PAYER_NAME 为空时：INCOME 自动回退 PAYEE_NAME → COUNTER_ACCOUNT
4. ✅ PAYEE_NAME 为空时：EXPENSE 自动回退 PAYER_NAME → COUNTER_ACCOUNT
5. ✅ txType 为空但有 direction：按 direction 推断（in→收款规则, out→付款规则）
6. ✅ txType 和 direction 均为空：走原有兜底逻辑（PAYER_NAME → PAYEE_NAME → COUNTER_ACCOUNT）
7. ✅ 现有列映射不受影响（PAYER_NAME/PAYEE_NAME/COUNTER_ACCOUNT 别名不变）
8. ✅ 预览数据中的对方名称等于确认导入后的实际写入值

---

## 六、实施计划

| 步骤 | 内容 | 预估 |
|------|------|------|
| 1 | 修改 `parseRow()` 方向判断逻辑 | 15 min |
| 2 | 补充 ColumnMappingResolver 别名（如需） | 5 min |
| 3 | 运行现有测试确认不破坏已有功能 | 5 min |
| 4 | 用真实银行对账单 Excel 验证导入 | 10 min |
| 5 | commit + push | 5 min |
| **合计** | | **~40 min** |

---

## 七、BDD 验收标准

### 场景 1：INCOME 流水正确识别对方
**Given** 导入 INCOME 类型银行流水，PAYER_NAME 列有值  
**When** parseRow() 执行对手方提取  
**Then** counter_account = PAYER_NAME 列的值  
**And** 不等于己方名称

### 场景 2：PAYER_NAME 为空时自动回退
**Given** 导入 INCOME 流水，PAYER_NAME 列为空  
**When** parseRow() 执行  
**Then** 回退到 PAYEE_NAME 列  
**And** 若 PAYEE_NAME 也为空则继续回退到 COUNTER_ACCOUNT

### 场景 3：txType 为空时按 direction 推断
**Given** txType 为空但 direction = "in"  
**When** parseRow() 执行  
**Then** 按收款规则处理（counterparty = PAYER_NAME）