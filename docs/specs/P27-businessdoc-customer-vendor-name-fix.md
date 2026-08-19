# P27 SPEC — BusinessDoc 客户/供应商名称字段补齐
> **版本**：V1.0 | **最后修改**：2026-07-19 | **作者**：Hermes
> **状态**：✅ 生效

> **编号**：HUICAI-SPC-027 | 优先级：P0（编译失败，mvn test 红）
> 修复目标：`BusinessDocServiceImpl.generateVoucher()` 第 317 行调用 `entity.getCustomerName()/getSupplierName()` 时 `BusinessDocEntity` 上不存在这 2 个字段，导致 4 个测试 fail、整体 BUILD FAILURE。
> 依据：P26 凭证模板引擎 SPEC §1.1（变量定义：{客户名称} / {供应商名称}）、影响分析 §第 15 章（实施回归）

> **test_ref**：BusinessDocServiceImplTest, BusinessDocRestContractTest
---

> **关联需求**: REQ-2026-005

## SDD 四段结构索引

### 1. 输入契约
→ 见本文 [## 1. 实体变更（BusinessDocEntity 新增字段/字段定义）](#1-实体变更76-五步实测已确认) 及 [## 2. 写入点（4 个写入位置）](#2-写入点4-个)

### 2. 输出契约
→ 见本文 [## 4. 验证清单（编译/测试/端到端验证条件）](#4-验证清单)

### 3. 状态流转
→ 本文不涉及独立状态机，BusinessDoc 状态机详见 P34 SPEC

### 4. 异常处理
→ 本文不涉及新增 BusinessException

---

## 0. 改动清单总览

| # | 改动 | 文件 | 风险 | 批次 |
|---|------|------|------|------|
| 1 | V50 migration: t_business_doc 加 customer_name / supplier_name 字段 | Flyway | 🟢 低 | P0-1 |
| 2 | `BusinessDocEntity` 加 customerName/supplierName 字段 + getter/setter | Entity 文件 | 🟢 低 | P0-1 |
| 3 | `BusinessDocVO` 加同名字段（VO 已有但来源 customerMap，要确认一致性） | DTO 文件 | 🟢 低 | P0-2 |
| 4 | `BusinessDocServiceImpl.create()` / `update()` 写入时通过 customerId/vendorId 反查 Customer/Vendor 表填名称 | Service 文件 | 🟡 中 | P0-2 |
| 5 | `SalesInvoiceImportService` / `InputInvoiceImportService` 写入 t_business_doc 时填名称 | Service 文件 | 🟡 中 | P0-2 |
| 6 | 修复 `BusinessDocServiceImpl.generateVoucher()` 第 317-318 行的 getXxxName 调用（编译失败根因） | Service 文件 | 🟢 低 | P0-2 |
| 7 | `BusinessDocServiceImplTest` 4 个 fail 测试加 customerName/supplierName 设置 + 新增测试覆盖字段持久化 | 测试文件 | 🟡 中 | P0-3 |
| 8 | mvn test 验证 390/0/0 + 全链路银行流水→单据→凭证 走通 | CI | 🟢 低 | P0-3 |

---

## 1. 实体变更（§76 五步实测已确认）

### 1.1 现状

| 维度 | 实测 |
|---|---|
| Entity 字段 | `customerId` / `supplierId`（已有，Long 类型）|
| 缺失字段 | `customerName` / `supplierName`（String，VARCHAR(200)）|
| VO 字段 | `BusinessDocVO.customerName` 已存在（ServiceImpl:601 拼装）|
| TemplateContext | `customerName` / `vendorName` 已存在（P26 已建）|
| 业务代码使用 | `BusinessDocServiceImpl.java:317-318` 调 `entity.getCustomerName()/getSupplierName()`（编译失败）|

### 1.2 字段定义

```sql
ALTER TABLE t_business_doc
  ADD COLUMN customer_name VARCHAR(200),
  ADD COLUMN supplier_name VARCHAR(200);
```

### 1.3 Entity 新增字段

```java
@TableField("customer_name")
private String customerName;

@TableField("supplier_name")
private String supplierName;
```

---

## 2. 写入点（4 个）

| # | 位置 | 来源 | 时机 |
|---|------|------|------|
| 1 | `BusinessDocServiceImpl.create()` | 已有 customerId → 反查 Customer 表 | 创建时 |
| 2 | `BusinessDocServiceImpl.update()` | 同上 | 更新时（customerId 变化时）|
| 3 | `BusinessDocServiceImpl.convertToVO()` | 已存在（line 601），无需改 | VO 拼装 |
| 4 | `SalesInvoiceImportService` / `InputInvoiceImportService` | 导入时直接用 `row.buyerName` / `row.sellerName` | 银行流水导入时 |

---

## 3. Flyway Migration

**V50__add_business_doc_customer_supplier_name.sql**：
- 仅 ALTER TABLE 加 2 列
- 默认 NULL（兼容存量数据，无需回填）
- 现有 0 行数据，**不**需要 UPDATE 填充

---

## 4. 验证清单

| 检查项 | 期望 |
|---|---|
| `mvn compile` | 通过（消除"getCustomerName undefined"错误）|
| `mvn test -Dtest=BusinessDocServiceImplTest` | 19 个 @Test 全绿 |
| `mvn test` 全量 | **390/0/0** BUILD SUCCESS |
| 端到端：银行流水→单据→凭证 | 模板上下文 `customerName` 有值 |

---

## 5. 不做的事（避免范围蔓延）

- ❌ 不改 `TemplateContext`（P26 已稳定）
- ❌ 不改 `BusinessDocVO`（VO 已有 customerName 字段）
- ❌ 不补 4 个老 P0 缺口（AssetCard / reconciliation_suggestion / OutputInvoiceStateMachine / Ticket endorse）— 独立工单
- ❌ 不动 P26 P2 批（V50 已用，要改名 V51）

---

## 6. 工期估算

| 批次 | 内容 | 预计 commit |
|:---:|:---|:---:|
| P0-1 | V50 migration + Entity 字段 | 1 |
| P0-2 | 4 个写入点 + generateVoucher 编译修复 | 2 |
| P0-3 | 单测补齐 + mvn test 验证 | 1 |
| **合计** | | **4 commit** |

---

## 7. 风险与回退

- **风险**：写入点改 4 处，可能漏改某个路径 → 用 `grep -rn setCustomerName` 全量验证
- **回退**：4 commit 串行，任意一步 mvn test 红即可 `git reset --hard HEAD~1` 回退
- **数据兼容**：ALTER TABLE 加列（默认 NULL）不影响存量数据

---

## BDD 验收标准

### 场景 1：创建业务单据时自动回填客户/供应商名称
**Given** 用户创建一张业务单据，`customerId = 1` 且 `supplierId = null`
**When** `BusinessDocServiceImpl.create()` 执行
**Then** 该单据的 `customer_name` 字段被自动填充为 Customer 表对应 ID 的名称，`supplier_name = null`

### 场景 2：编译通过且 getCustomerName/getSupplierName 可用
**Given** `BusinessDocServiceImpl.generateVoucher()` 第 317 行调用了 `entity.getCustomerName()`
**When** 执行 `mvn compile`
**Then** 编译成功，无 `getCustomerName undefined` 错误

### 场景 3：模板上下文中 customerName 有值
**Given** 一张业务单据关联了客户且 `customer_name` 已填充
**When** 凭证模板引擎渲染时引用 `{客户名称}` 变量
**Then** 模板上下文中 `customerName` 字段非空且等于客户名称