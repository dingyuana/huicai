# P44 SPEC — 核销单列表展示缺陷修复

> **编号**：HUICAI-SPC-044 | **优先级**：P0
> **关联需求**：REQ-2026-058
> **版本**：V1.0 | **日期**：2026-07-09

> **test_ref**：ArapSettlementServiceImplTest, ArapSettlementControllerTest
---

## 1. 输入契约
→ 见本文 [## 1. 修复方案 — ArapSettlementVO / Mapper 自定义查询 / 详情接口](#1-修复方案)

## 2. 输出契约
→ 见本文 [## 2. 验收标准 — AT-P44-1 至 AT-P44-4 验收清单](#2-验收标准)

## 3. 状态流转
→ 见本文 [## 1.5 P44-5：状态机说明 — DRAFT→CONFIRMED→VOUCHERED 流转](#15-p44-5状态机说明)

## 4. 异常处理
→ 见本文各 BusinessException 抛出点（如核销明细查询失败、数据校验异常）

## 0. 现状审计

访问 `/arap/settlement` 页面，核销单列表存在 5 个显示缺陷：

| # | 问题 | 用户描述 | 根因 |
|---|------|---------|------|
| 1 | 客户/供应商为空 | 不显示对方名称 | `ArapSettlementEntity` 只有 partyId/partyType，pageQuery 用 MyBatis-Plus `selectPage()` 没有 JOIN 客户/供应商表 |
| 2 | 类型不显示 | "应收核销"没出来 | 数据库存 `settlement_type='RECEIVE'`，前端比较的是 `'RECEIVABLE'`，不匹配 |
| 3 | 上游来源无数据 | 点击查看详情，上游为空 | `trace` 接口查 reconciliation_log 表，但该核销单无对应日志记录 |
| 4 | 核销依据未体现 | 列表看不到核销了哪些单据 | 列表只显示核销单头信息，不展示核销明细（t_arap_settlement_entry） |
| 5 | 状态机不清楚 | 不知道有哪些状态、是否需要审核 | 前端 `statusLabel` 缺少 CANCELLED/REJECTED 的显示 |

## 1. 修复方案

### 1.1 P44-1：客户/供应商显示（后端改动）

**根因**：`ArapSettlementServiceImpl.pageQuery()` 使用 `BaseMapper.selectPage()`，只查 `t_arap_settlement` 单表，没有 JOIN 客户/供应商名称。

**方案 A（推荐）**：创建 `ArapSettlementVO`，在 `pageQuery` 中用自定义 SQL JOIN：

```sql
SELECT s.*, c.name AS customer_name, v.name AS vendor_name
FROM t_arap_settlement s
LEFT JOIN t_customer c ON s.party_id = c.id AND s.party_type = 'CUSTOMER'
LEFT JOIN t_supplier v ON s.party_id = v.id AND s.party_type = 'VENDOR'
WHERE s.deleted = 0
```

**方案 B**：在 `ArapSettlementEntity` 中增加 `customerName`/`vendorName` 冗余字段，创建时从 partyId 回填。

> **选择方案 A**，避免数据冗余，保持一致性。

**改动点**：

| 文件 | 改动 |
|------|------|
| `ArapSettlementVO.java`（新建） | 继承 `ArapSettlementEntity`，加 `customerName`/`vendorName` |
| `ArapSettlementMapper.java` | 新增 `pageQueryWithPartyName()` 自定义查询 |
| `ArapSettlementServiceImpl.java` | `pageQuery()` 改为调用新查询，返回 VO |
| 对应 XML 或 `@Select` 注解 | 写 JOIN SQL |

### 1.2 P44-2：类型显示（前端改动）

**根因**：DB 使用 `settlement_type='RECEIVE'`，前端 `settlementTypeLabel` 已存在但值比较是 `'RECEIVABLE'`，未覆盖 `'RECEIVE'`。

**修复**：已在前端补了 `settlementTypeLabel()` 函数，兼容 `RECEIVE`/`RECEIVABLE`/`PAY`/`PAYABLE` 四种取值。

**改动点**：

| 文件 | 改动 |
|------|------|
| `SettlementList.vue` | 类型列改用 `settlementTypeLabel()` 替代直接比较 |
| 模板中类型 tag | 已改，见 43 行附近 |

### 1.3 P44-3：上游来源无数据（数据问题）

**根因**：核销单是直接从 ReconciliationServiceImpl.execute() 创建的，未经过 reconciliation_log 表。trace 接口先查 reconciliation_log，再查 settlement。当 log 不存在时只返回 settlement 基本信息，上游自然为空。

**修复**：当前 trace 接口已有 settlement 降级逻辑。上游数据需要 `t_reconciliation_log` 中记录 `source_doc_id` 和 `source_doc_type`。这需要核销执行时写入日志。

> **此问题非前端能修**，需要在 ReconciliationServiceImpl.execute() 中确保 reconciliation_log 记录完整。当前改动范围过大，建议另开工单处理。

**临时方案**：核销单详情中显示 settlement entries 作为"核销依据"。

### 1.4 P44-4：核销依据体现（前端+后端改动）

**根因**：核销单列表只有头信息，没有关联的核销明细（t_arap_settlement_entry）。

**修复**：在核销单详情弹窗中，增加"核销明细"表格，展示：

```
来源单据号 | 目标单据号 | 核销金额 | 核销前余额 | 核销后余额
```

**改动点**：

| 文件 | 改动 |
|------|------|
| `ArapSettlementServiceImpl.java` | `getDetail()` 返回时附带 entry 列表 |
| `ArapSettlementController.java` | 详情接口返回 entry 列表 |
| `SettlementList.vue` | 详情弹窗增加"核销明细"表格 |

### 1.5 P44-5：状态机说明

当前核销单状态及流转：

```
DRAFT ──→ CONFIRMED ──→ VOUCHERED ──→ (凭证过账)
  │           │
  └──→ CANCELLED    REJECTED
                    └──→ REVERSED（从 VOUCHERED 反核销）
```

**不需要独立审核**：核销单从 ReconciliationServiceImpl.execute() 创建时直接为 CONFIRMED，无需审核环节。当前流程：核销执行 → 生成 CONFIRMED 核销单 → 生成凭证 → VOUCHERED。

**修复**：前端 `statusLabel()` 已补全 CANCELLED/REJECTED 显示。

---

## 2. 验收标准

| ID | 描述 | 断言 |
|----|------|------|
| AT-P44-1 | 核销单列表显示客户/供应商名称 | 每行 `customer_name` 或 `vendor_name` 不为空 |
| AT-P44-2 | 类型列支持 RECEIVE/RECEIVABLE 显示为"应收核销" | `settlementTypeLabel('RECEIVE')` = "应收核销" |
| AT-P44-3 | 详情弹窗显示核销明细 | 明细表格行数 > 0 |
| AT-P44-4 | 状态标签支持 CANCELLED/REJECTED | `statusLabel('CANCELLED')` = "已取消" |

---

## 3. 不做事项

- ❌ 不改 reconciliation_log 写入逻辑（P44-3 另开工单）
- ❌ 不改 trace 接口（已足够健壮）
- ❌ 不新增数据库字段
- ❌ 不改动核销执行流程

---

## 4. 施工计划

| 步 | 内容 | 文件数 | 预估工时 |
|----|------|--------|---------|
| 1 | P44-1：新建 ArapSettlementVO + Mapper 自定义查询 | 3 | 1.5h |
| 2 | P44-4：详情弹窗增加核销明细表格 | 2 | 0.5h |
| 3 | P44-2/P44-5：前端标签修正 | 1 | 0.2h |
| 4 | 后端编译 + 前端构建 + 部署 | — | 0.3h |
| 5 | 验证 | — | 0.3h |

---

## 5. BDD 验收标准

### 场景 1：核销单列表正确显示客户/供应商名称
**Given** 核销单数据存在，t_arap_settlement 表记录有 partyId 和 partyType
**When** 用户访问核销单列表页，调用 pageQuery 接口
**Then** 返回的每行记录中包含 customer_name（partyType=CUSTOMER）或 vendor_name（partyType=VENDOR），且不为空

### 场景 2：类型列兼容 RECEIVE/RECEIVABLE 两种取值
**Given** 数据库中存在 settlement_type='RECEIVE' 的核销单记录
**When** 前端调用 settlementTypeLabel('RECEIVE')
**Then** 返回"应收核销"，与 settlementTypeLabel('RECEIVABLE') 返回结果一致

### 场景 3：详情弹窗展示核销明细表格
**Given** 一张核销单存在多条关联的核销明细（t_arap_settlement_entry）
**When** 用户点击该核销单查看详情
**Then** 弹窗中显示核销明细表格，包含来源单据号、目标单据号、核销金额等字段，且明细行数 > 0