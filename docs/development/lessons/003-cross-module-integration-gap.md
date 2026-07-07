# 复盘：核销工作台 INVOICE_OUT 查询遗漏 — 跨模块集成断层

> **编号**：HUICAI-DEV-034
> **版本**：V1.0 | **修改日期**：2026-07-07 | **修改人**：Hermes
> **分类**：跨模块集成测试 | **严重度**：🔴 业务阻断

---

## 一、事件

核销工作台只查 `docType='RECEIPT'` 的单据，但销项发票导入生成的是 `INVOICE_OUT` 类型单据，导致已生成的应收单据在核销工作台不可见，核销流程断裂。

## 二、根因

### 数据层面
销项发票导入生成 `INVOICE_OUT` 类型的业务单据（`t_business_doc`），`unsettledAmount=1950.0`。但核销工作台查询条件只写了 `docType='RECEIPT'`。

### 业务逻辑断层
工作台 tab 值是 `RECEIPT`，概念上"收款单"应包含所有应收方向单据。但 `INVOICE_OUT` 这个 `docType` 是后期 P34 新增的，新增时没人同步更新工作台的查询逻辑。

### 根本原因

```
P34 新增 INVOICE_OUT 类型 (2026-07-01)
  → 代码: BusinessDocEntity 支持了 INVOICE_OUT
  → 前端: 核销工作台 ReconciliationWorkbench.vue 查询条件未更新
  → 测试: 没有任何测试覆盖这个跨模块链路
  → 结果: 断裂无人发现
```

## 三、测试为什么没发现（3 个空白）

### ① 后端 pageQuery() 零测试

`BusinessDocServiceImplTest.java` 有 575 行测试，覆盖了 `update()`、`getDetail()`、`generateVoucher()`，但 `pageQuery()` **一行测试都没有**。没有任何断言验证不同 `docType` 参数下返回的数据是否正确。

### ② 前端零组件级测试

`ReconciliationWorkbench.vue` **没有任何测试**。前端只有：
- `auth.store.test.ts`（状态管理）
- `system.api.test.ts`（API 封装）
- E2E 只覆盖了登录、银行对账单导入、销售发票导入，**没有核销工作台的测试**

### ③ 整个链路没有端到端覆盖

```
数据流:
导入销项发票 → 创建 INVOICE_OUT 单据 → 在核销工作台可见 → 核销匹配
     ✅ 有测试      ❌ 无测试             ❌ 无测试           ❌ 无测试
```

E2E 只停在第 1 步（销售发票导入），从未验证第 2-3 步。`INVOICE_OUT` 类型是在发票导入功能里新增的，但工作台查询条件没有同步更新，这个断层没有任何测试能发现。

---

## 四、防止再次犯错的预防机制

### 4.1 新增 docType 时的强制检查清单

当新增一个 `docType` 枚举值时，必须执行以下检查：

```bash
# 检查所有涉及 docType 的查询条件
grep -rn "docType\|doc_type\|RECEIPT\|INVOICE_OUT" frontend/src/ --include="*.vue" --include="*.ts"
grep -rn "eq.*docType\|eq.*doc_type" backend/src/main/java/ --include="*.java"

# 检查工作台查询逻辑
grep -rn "RECEIPT\|business_receipt\|INVOICE_OUT" frontend/src/views/arap/ --include="*.vue"
```

**新增 docType 六步检查**：

| 步骤 | 检查项 | 验证方法 |
|------|--------|---------|
| 1 | Entity 支持新类型 | `grep -n "docType\|doc_type" entity/` |
| 2 | 后端 Service 查询条件 | `grep -n "eq.*docType" service/impl/*.java` |
| 3 | 后端 Controller 参数 | `grep -n "docType" controller/*.java` |
| 4 | 前端 API 查询参数 | `grep -rn "docType\|doc_type" frontend/src/api/` |
| 5 | 前端页面/组件查询条件 | `grep -rn "RECEIPT\|INVOICE_OUT\|RECEIVABLE" frontend/src/views/` |
| 6 | 测试覆盖 | 确认 `pageQuery()` 测试包含新类型参数 |

### 4.2 测试强制要求

| 测试类型 | 要求 | 优先级 |
|---------|------|--------|
| **pageQuery() 测试** | 每个 Service 的 `pageQuery()` 方法必须有测试，覆盖不同过滤参数 | P0 |
| **前端组件测试** | 每个核心业务页面至少 1 个组件测试 | P0 |
| **E2E 跨模块链路** | 核心业务流程必须有端到端测试（从起点到终点） | P0 |

### 4.3 跨模块变更的"涟漪效应"检查

**规则**：任何模块新增枚举值/状态/类型时，必须检查所有引用了该枚举的模块。

```java
// 新增 INVOICE_OUT 时，必须 grep 以下位置：
// 1. 工作台查询条件
// 2. 前端 tab 过滤
// 3. 核销匹配逻辑
// 4. 报表/统计查询
// 5. 状态机转换守卫
```

---

## 五、修复动作

| 动作 | 文件 | 状态 |
|------|------|------|
| 修复核销工作台查询条件 | `ReconciliationWorkbench.vue` | ⏳ 待修复 |
| 修复后端 pageQuery 过滤 | `BusinessDocServiceImpl.java` | ⏳ 待修复 |
| 新增 pageQuery 测试 | `BusinessDocServiceImplTest.java` | ⏳ 待修复 |
| 新增前端组件测试 | `ReconciliationWorkbench.test.ts` | ⏳ 待修复 |
| 补充 E2E 链路 | `BankFlowE2ETest.java` | ✅ 已新建 |

---

> **文档结束。**