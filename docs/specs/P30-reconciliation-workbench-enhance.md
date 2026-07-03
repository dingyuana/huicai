# P30 SPEC — 核销工作台修复与增强

> 状态：**已完成**（P30-1/P30-2/P30-3 全部落地） | 优先级：高
> 依据：P12 核销业务闭环已落地，但前端工作台只支持银行流水触发，缺少收款单/付款单直接触发入口
> 目标：补全核销工作台的触发源+审批UI+日志查询，使后端完整能力在前端可用
> 工期：3 批工单（P30-1 / P30-2 / P30-3）

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

out_of_scope:
  - "核销推荐算法变更（L1-L6 现有逻辑不动）"
  - "差额调整逻辑变更（后端已完整）"
  - "银行对账页面变更（独立模块）"
  - "多币种/信用期/账龄分析"
