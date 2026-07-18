# P30 SPEC — 核销工作台修复与增强

> **编号**：HUICAI-SPC-030 | 优先级：高
> 依据：P12 核销业务闭环已落地，但前端工作台只支持银行流水触发，缺少收款单/付款单直接触发入口
> 目标：补全核销工作台的触发源+审批UI+日志查询，使后端完整能力在前端可用
> **V2.0 更新**：新增 P30-4 批次 — 全链路追溯API、余额快照、容差配置化
> 工期：4 批工单（P30-1 / P30-2 / P30-3 / P30-4）

---

> **关联需求**: REQ-2026-012

## SDD 四段结构索引

### 1. 输入契约
→ 见本文 [## 0. 现状摸底（现有 API 端点/数据结构）](#0-现状摸底) 及 [## 7. P30-4（全链路追溯 API/容差配置 API 定义）](#7-p30-4核销全链路追溯--余额快照--容差配置化)

### 2. 输出契约
→ 见本文 [## 7.5 P30-4 验收标准（AT-P30-4-1 至 AT-P30-4-4）](#75-p30-4-验收标准) 及 MACHINE-READABLE CONTRACT 中的 acceptance_tests

### 3. 状态流转
→ 见本文各批次中的核销状态流转（CONFIRMED→EXECUTED/REJECTED/CANCELLED）

### 4. 异常处理
→ 见本文 [MACHINE-READABLE CONTRACT constraints（乐观锁/状态校验/数据完整性约束）](#machine-readable-contract)

---

## 0. 现状摸底

### 现有能力（后端 ✅）

| 能力 | API 端点 | 前端是否有 |
|------|----------|-----------|
| 收款单→核销推荐 | `POST /reconciliation/receipt/{receiptId}/recommend` | ❌ 无入口 |
| 付款单→核销推荐 | `POST /reconciliation/payment/{paymentId}/recommend` | ❌ 无入口 |
| 执行单笔核销 | `POST /reconciliation/execute` | ❌ 工作台只有银行流水入口 |
| 批量核销 | `POST /reconciliation/batch-execute` | ✅ 工作台弹窗 |
| 带差额调整核销 | `POST /reconciliation/execute-with-adjustment` | ❌ 无入口 |
| 核销审批 (CONFIRMED→EXECUTED) | `POST /reconciliation/{id}/approve` | ❌ 无页面 |
| 核销驳回 (CONFIRMED→REJECTED) | `POST /reconciliation/{id}/reject` | ❌ 无页面 |
| 反核销 | `POST /reconciliation/{id}/reverse` | ❌ 无页面 |
| 核销日志分页查询 | `GET /reconciliation/logs/page` | ❌ 无页面 |
| 核销异常池 | `GET /reconciliation/exceptions/page` | ❌ 无页面 |

### 前端工作台现状

`frontend/src/views/arap/reconciliation-workbench/ReconciliationWorkbench.vue`
- 当前只支持银行流水触发核销（有设计缺陷）
- **应改为**：仅支持收款单/付款单 tab，银行流水不直接参与核销
- 无审批/驳回流程
- 无日志查询

---

## 1. P30-1：核销工作台增加收款单/付款单触发入口

### 1.1 方案

现有工作台改为 **2 个 tab**，**去掉银行流水 tab**：

| Tab | 触发源 | 数据来源 | 核销推荐 API |
|-----|--------|----------|-------------|
| **收款单** | `t_business_doc` 中 `docType=RECEIPT` 且未完全核销 | `pageReceipts()` 调业务单据分页接口 | `getReceiptRecommend(receiptId, ...)` |
| **付款单** | `t_business_doc` 中 `docType=PAYMENT` 且未完全核销 | 同上 | `getPaymentRecommend(paymentId, ...)` |

> 设计原则：核销是**业务单据 vs 应收/应付**的匹配，银行流水不应直接参与核销。
> 正确链路：银行流水 → B类路由 → 生成收款单/付款单(业务单据) → 核销工作台匹配应收/应付。

### 1.2 后端改动

**新增 BusinessDoc 查询接口**（或复用现有 `/api/v1/business-docs/page`）：
- 支持 `docType=RECEIPT|PAYMENT` 过滤
- 支持 `status=VOUCHERED`（已生成凭证但未核销）
- 返回字段：单据号、日期、对方名称、金额、已核销金额、未核销金额

**可选：核销状态字段**（若需精准判断）：
- `t_business_doc` 现有 `voucher_id` 和 `status` 字段
- 通过 `t_business_doc` 的 `unsettled_amount` 判断是否已核销（P34 后不再使用被删除的 t_receivable/t_payable）

### 1.3 前端改动

| 改动 | 文件 | 说明 |
|------|------|------|
| 去掉银行流水 tab | `ReconciliationWorkbench.vue` | 删除银行账户筛选、流水列表、`getBankStatementPage` 调用 |
| 加收款单/付款单 tab | 同上 | el-radio-group: 收款单/付款单 |
| 收款单列表 | 同上 | 分页显示未核销收款单据 |
| 付款单列表 | 同上 | 分页显示未核销付款单据 |
| 点击行→核销推荐 | 复用现有弹窗 | 调 `getReceiptRecommend` / `getPaymentRecommend` |
| 核销执行 | 弹窗按钮 | sourceDocType='receipt'/'payment' 传入后端 |

### 1.4 测试

- 收款单 tab：验证显示正确的待核销单据
- 点击执行核销：验证 `sourceDocType='receipt'` 传入后端
- 付款单 tab：同上 `sourceDocType='payment'`

---

## 2. P30-2：核销审批/驳回 UI

### 2.1 方案

核销执行后状态为 `CONFIRMED`，需要有人审批才能到 `EXECUTED`。当前缺少审批页面。

**新增页面**：`reconciliation-approval/ReconciliationApproval.vue`

| 功能 | API | 说明 |
|------|-----|------|
| 列表显示待审批核销 | `GET /reconciliation/logs/page?status=CONFIRMED` | 分页 |
| 审批通过 | `POST /reconciliation/{id}/approve` | CONFIRMED → EXECUTED |
| 驳回（需原因） | `POST /reconciliation/{id}/reject?reason=...` | CONFIRMED → REJECTED，回滚金额 |
| 反核销（需原因） | `POST /reconciliation/{id}/reverse?reason=...` | EXECUTED → CANCELLED |

### 2.2 列表字段

| 字段 | 说明 |
|------|------|
| 来源类型 | bank_txn / receipt / payment |
| 来源单据号 | 对应业务单据号 |
| 对方名称 | 客户/供应商名 |
| 目标单据号 | 被核销的发票/应收单号 |
| 核销金额 | 实际核销金额 |
| 匹配级别 | L1-L6 |
| 核销时间 | created_at |
| 状态 | CONFIRMED / EXECUTED / REJECTED / CANCELLED |

---

## 3. P30-3：核销日志与异常池页面

### 3.1 核销日志页面

**新增页面**：`reconciliation-logs/ReconciliationLogs.vue`

后端已有 `GET /reconciliation/logs/page`，前端需要一个查看页面：
- 筛选：来源类型、日期范围、状态
- 列表：展示所有核销记录
- 详情弹窗：查看核销匹配详情、关联凭证

> ✅ **已实现**：核销日志功能已集成在 `SettlementList.vue` 的"核销日志"tab 中（P30-2 时已做），无需单独页面。

### 3.2 核销异常池页面

后端已有 `GET /reconciliation/exceptions/page` + `POST /exceptions/{id}/resolve`：
- 列表显示异常记录
- 可标记已解决

> ✅ **已实现（2026-07-02）**：`ReconciliationExceptionList.vue` — 完整 CRUD 页面，支持按状态/异常类型筛选，支持解决/忽略/重试操作。

### 3.3 核销审批页面

> ✅ **已实现（2026-07-02）**：`ReconciliationApproval.vue` — 显示 CONFIRMED 状态的核销日志列表，支持审批通过和驳回操作。

### 3.4 实现文件清单

| 文件 | 说明 |
|------|------|
| `frontend/src/api/modules/arapSettlement.ts` | 新增 approve/reject/exception CRUD API 函数 |
| `frontend/src/views/arap/reconciliation-approval/ReconciliationApproval.vue` | 核销审批页面 |
| `frontend/src/views/arap/reconciliation-exception/ReconciliationExceptionList.vue` | 核销异常池页面 |
| `frontend/src/router/routes/base.ts` | 新增 2 条路由 |

---

## 4. 施工顺序

| 批 | 内容 | 前置依赖 | 风险 |
|---|------|---------|------|
| **P30-1** | 工作台增加收款/付款单 tab | BusinessDoc 分页接口 | 🟡 低 |
| **P30-2** | 核销审批/驳回 UI | P30-1（建议但非必须） | 🟡 低 |
| **P30-3** | 核销日志与异常池 | 无 | ✅ 低 |

**推荐开工顺序**：P30-1 → P30-2 → P30-3

---

## 5. 痛点对应关系

| 用户反馈的痛点 | 对应工单 | 说明 |
|---------------|---------|------|
| 收款/付款单不能触发核销 | **P30-1** | 工作台只支持银行流水，改为收款单/付款单 tab |
| 没有可视化操作界面 | P30-1 + P30-2 | 补齐所有触发源+审批页面 |

---

## 6. 不做事项

- ❌ 不改后端推荐算法（L1-L6 现有逻辑不动）
- ❌ 不改差额调整逻辑（后端已完整，前端暂时不暴露）
- ❌ 不改银行对账页面（`BankReconciliationView` 与核销是独立模块）
- ❌ 不在本批增加多币种/信用期/账龄分析

---

# === MACHINE-READABLE CONTRACT ===

contract_version: "1.0"

entity: ReconciliationRecord (business)
module: arap
table: t_reconciliation / t_reconciliation_log / t_arap_settlement

acronym: P30

contracts:
  - id: P30-C1
    description: "收款单→核销推荐入口可用"
    type: api
    endpoint: POST /api/v1/reconciliation/receipt/{receiptId}/recommend
    expected: "200 + 推荐列表（业务单据金额匹配）"

  - id: P30-C2
    description: "付款单→核销推荐入口可用"
    type: api
    endpoint: POST /api/v1/reconciliation/payment/{paymentId}/recommend
    expected: "200 + 推荐列表（业务单据金额匹配）"

  - id: P30-C3
    description: "核销执行后业务单据 settledAmount 正确更新"
    type: db_query
    assertion: |
      SELECT settled_amount, unsettled_amount FROM t_business_doc
      WHERE id = :docId
      → settled_amount == :execAmount AND unsettled_amount == :remaining

  - id: P30-C4
    description: "核销日志记录完整"
    type: db_query
    assertion: |
      SELECT operation_type, source_type, target_doc_type, target_business_doc_id
      FROM t_reconciliation_log
      WHERE settlement_id = :settlementId
      → operation_type IS NOT NULL AND target_doc_type IN ('INVOICE_OUT','INVOICE_IN')

  - id: P30-C5
    description: "核销执行时校验状态机：仅已审批单可核销"
    type: unit_test
    target: ReconciliationServiceImplTest.testExecuteRejectsNonApprovedDoc
    assertion: "execSettlement(docId) for doc.status != APPROVED → BusinessException"

acceptance_tests:
  - id: AT-P30-1
    description: "收款单 tab 显示核销推荐"
    method: testReceiptReconciliationRecommend
    status: planned
  - id: AT-P30-2
    description: "核销确认后更新业务单据金额"
    method: testSettlementUpdatesDocAmount
    status: covered
  - id: AT-P30-3
    description: "核销操作记录审计日志"
    method: testSettlementCreatesLog
    status: covered

constraints:
  - id: C-P30-1
    type: data_integrity
    rule: "核销后 settledAmount + unsettledAmount = amount 恒成立"
    enforcement: "业务层 SUM 校验"
  - id: C-P30-2
    type: business
    rule: "仅 APPROVED 状态的业务单据可参与核销"
    enforcement: "状态机前置检查"
  - id: C-P30-3
    type: concurrency
    rule: "同一业务单据不可被两个互斥的核销操作同时更新"
    enforcement: "乐观锁（BusinessDocEntity.version）"

dependencies:
  - spec: P34
    relation: "核销目标为 INVOICE_OUT/INVOICE_IN 业务单据（P34 架构）"
  - spec: P36
    relation: "红冲过的单据核销金额需重新计算"
  - spec: P22
    relation: "核销凭证的摘要需包含业务单据号"

---

## 7. P30-4：核销全链路追溯 + 余额快照 + 容差配置化

### 7.1 目标

参考外部核销设计方案，补全本项目缺失的三项核心能力：
1. **全链路追溯API**：一次性返回核销单的完整业务链路（流水→收款单→核销单→应收单→发票→凭证）
2. **余额快照**：核销时记录单据前后余额，便于审计追溯
3. **容差配置化**：将硬编码的5元容差阈值改为可配置，支持按客户/供应商设置不同阈值

### 7.2 P30-4-1：全链路追溯 API

**API 定义**：

| 方法 | 路由 | 说明 |
|------|------|------|
| GET | `/api/v1/reconciliation/{id}/trace` | 核销全链路追溯 |

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 核销日志ID或核销单ID |

**响应结构**：

```json
{
  "traceId": "TRC-202607-001",
  "settlement": {
    "id": 1,
    "settlementNo": "JS-202607-0001",
    "amount": 10000.00,
    "status": "EXECUTED",
    "createdAt": "2026-07-08 10:00:00"
  },
  "upstream": {
    "bankTransaction": {
      "id": 100,
      "transactionNo": "BK-202607-0001",
      "amount": 10000.00,
      "counterAccount": "客户A"
    },
    "receipt": {
      "id": 50,
      "docNo": "SK-202607-0001",
      "amount": 10000.00,
      "status": "VOUCHERED"
    }
  },
  "downstream": {
    "businessDocs": [
      {
        "id": 200,
        "docNo": "YD-202607-0001",
        "docType": "INVOICE_OUT",
        "amount": 10000.00,
        "settledAmount": 10000.00,
        "unsettledAmount": 0.00
      }
    ],
    "invoices": [
      {
        "id": 300,
        "invoiceNo": "FP-202607-0001",
        "amount": 10000.00,
        "status": "FULLY_RECONCILED"
      }
    ]
  },
  "operationTrail": [
    {
      "operationType": "CREATE",
      "operator": "张三",
      "time": "2026-07-08 10:00:00",
      "remark": "自动匹配核销"
    },
    {
      "operationType": "CONFIRM",
      "operator": "李四",
      "time": "2026-07-08 10:30:00",
      "remark": "审核通过"
    }
  ],
  "voucher": {
    "id": 400,
    "voucherNo": "PZ-202607-0001",
    "status": "POSTED"
  }
}
```

### 7.3 P30-4-2：余额快照

**数据库变更**（Flyway V81）：

```sql
ALTER TABLE t_arap_settlement_entry 
ADD COLUMN before_balance NUMERIC(18,2) DEFAULT 0,
ADD COLUMN after_balance NUMERIC(18,2) DEFAULT 0;

ALTER TABLE t_reconciliation_log 
ADD COLUMN operation_type VARCHAR(20) DEFAULT 'CREATE',
ADD COLUMN rule_id VARCHAR(50);
```

**业务逻辑**：

1. 核销执行前，查询业务单据当前 `unsettled_amount` 作为 `before_balance`
2. 核销执行后，计算 `after_balance = before_balance - settled_amount`
3. 将快照写入 `t_arap_settlement_entry`
4. 同时记录操作类型到 `t_reconciliation_log`

### 7.4 P30-4-3：容差配置化

**数据库变更**（Flyway V82）：

```sql
CREATE TABLE t_reconciliation_tolerance (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    party_id BIGINT,
    party_type VARCHAR(20),
    tolerance_amount NUMERIC(18,2) DEFAULT 5.00,
    tolerance_rate NUMERIC(5,2) DEFAULT 10.00,
    effective_from DATE DEFAULT NOW(),
    effective_to DATE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    deleted INTEGER DEFAULT 0
);

CREATE INDEX idx_tolerance_party ON t_reconciliation_tolerance(party_id, party_type);
CREATE INDEX idx_tolerance_tenant ON t_reconciliation_tolerance(tenant_id);
```

**API 定义**：

| 方法 | 路由 | 说明 |
|------|------|------|
| GET | `/api/v1/reconciliation/tolerance/default` | 获取默认容差配置 |
| GET | `/api/v1/reconciliation/tolerance/{partyId}/{partyType}` | 获取指定客户/供应商容差配置 |
| POST | `/api/v1/reconciliation/tolerance` | 创建容差配置 |
| PUT | `/api/v1/reconciliation/tolerance/{id}` | 更新容差配置 |
| DELETE | `/api/v1/reconciliation/tolerance/{id}` | 删除容差配置 |

**容差获取优先级**：

```
1. 客户/供应商专属配置（party_id + party_type）
2. 全局配置（party_id = NULL）
3. 系统默认值（5元，10%）
```

### 7.5 P30-4 验收标准

| ID | 描述 | 断言 |
|----|------|------|
| AT-P30-4-1 | 全链路追溯API返回完整数据 | `trace().settlement != null && trace().upstream != null && trace().downstream != null && trace().operationTrail.length >= 1` |
| AT-P30-4-2 | 核销明细记录余额快照 | `settlementEntry.beforeBalance > 0 && settlementEntry.afterBalance == settlementEntry.beforeBalance - settlementEntry.amount` |
| AT-P30-4-3 | 容差配置优先使用客户专属 | `getTolerance(customerId).toleranceAmount == customerConfig.toleranceAmount` |
| AT-P30-4-4 | 容差配置回退全局 | `getTolerance(unknownId).toleranceAmount == globalConfig.toleranceAmount` |

---

## 8. 施工顺序（更新）

| 批 | 内容 | 前置依赖 | 风险 |
|---|------|---------|------|
| **P30-1** | 工作台增加收款/付款单 tab | BusinessDoc 分页接口 | 🟡 低 |
| **P30-2** | 核销审批/驳回 UI | P30-1（建议但非必须） | 🟡 低 |
| **P30-3** | 核销日志与异常池 | 无 | ✅ 低 |
| **P30-4** | 全链路追溯 + 余额快照 + 容差配置化 | P30-1/P30-2 | 🟡 中 |

**推荐开工顺序**：P30-1 → P30-2 → P30-3 → P30-4

---

out_of_scope:
  - "核销推荐算法变更（L1-L6 现有逻辑不动）"
  - "差额调整逻辑变更（后端已完整）"
  - "银行对账页面变更（独立模块）"
  - "多币种/信用期/账龄分析"

---

## BDD 验收标准

### 场景 1：收款单 Tab 点击后显示核销推荐
**Given** 用户进入核销工作台，切换到"收款单"Tab
**When** 点击一条未核销收款单的"核销推荐"按钮
**Then** 系统调用 `POST /reconciliation/receipt/{receiptId}/recommend`，返回推荐匹配列表

### 场景 2：核销审批通过后状态变为 EXECUTED
**Given** 一条核销记录 `status = CONFIRMED`
**When** 调用 `POST /reconciliation/{id}/approve`
**Then** 核销记录 `status = EXECUTED`，业务单据的 `unsettled_amount` 相应扣减

### 场景 3：全链路追溯 API 返回完整上下游数据
**Given** 一条已执行核销的记录
**When** 调用 `GET /api/v1/reconciliation/{id}/trace`
**Then** 响应中包含 `upstream`（银行流水/收款单）、`downstream`（业务单据/发票）、`operationTrail`（操作日志）和 `voucher`（凭证）四段完整链路
