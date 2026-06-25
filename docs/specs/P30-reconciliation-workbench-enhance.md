# P30 SPEC — 核销工作台修复与增强

> 状态：**待启动** | 优先级：高
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
- 通过 `t_receivable`/`t_payable` 的 `unsettled_amount` 判断是否已核销

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

### 3.2 核销异常池页面

后端已有 `GET /reconciliation/exceptions/page` + `POST /exceptions/{id}/resolve`：
- 列表显示异常记录
- 可标记已解决

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
