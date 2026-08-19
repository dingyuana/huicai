# P42 SPEC — 核销能力补充：Timeline 视图 + 穿透点击 + FIFO 自动核销

> **编号**：HUICAI-SPC-042 | **优先级**：P1
> **依据**：核销参考设计比对评估，三项前端能力可补充
> **关联需求**：REQ-2026-055, REQ-2026-056, REQ-2026-057
> **版本**：V2.1 | **日期**：2026-08-18
> **关联PRD**：../prd/应收应付核销-PRD-V1.0.md
> **版本历史**：V1.0 (2026-07-08) 初始方案；V2.0 (2026-08-15) 现状盘点 + 缺口识别 + 契约修正方案；V2.1 (2026-08-18) G1/G2/G3 全部实施完成（commit 69e6fbc/6da15ea/9fd94a4）

> **test_ref**：ReconciliationServiceImplTest, ReconciliationRestContractTest
---

## 0. V2.0 现状盘点（2026-08-15 代码核对）

**结论**：V1.0 三项需求在 7d368f5（合并核销工作台与往来核销）时已落地大部分，登记册「🆕 规划中」状态过期。剩余为 **3 个缺口 + 1 个契约 bug**。

| 需求 | 实现现状 | 剩余缺口 |
|------|---------|---------|
| REQ-055 Timeline | ✅ 组件 `ReconciliationTimeline.vue` + 后端 `GET /sme/arap/v1/reconciliation/{id}/trace` 均已实现，挂载于 `SettlementPanel.vue` 详情对话框 | ~~G1~~ ✅ 已解决（9fd94a4：节点 jumpPath 生成）；~~G3~~ ✅ 已解决（69e6fbc：trace() 填充 downstream.invoices） |
| REQ-056 穿透点击 | ✅ `onTimelineJump(path) → router.push(path)` 已接线；上游/下游 el-drawer 已存在（`showUpstreamDrawer`/`showDownstreamDrawer`） | ~~G1~~ ✅ 已解决（9fd94a4：Timeline 节点 jumpPath 生效，点击可跳转） |
| REQ-057 FIFO 自动核销 | ✅ 后端 `autoReconcileFifo()` + `POST /sme/arap/v1/reconciliation/auto-fifo` 已实现（dueDate 升序 + 剩余转预收/预付）；前端 `WorkbenchPanel.vue` 已有「自动核销」按钮 + 结果弹窗 + 确认执行 | ~~G2（契约 bug）~~ ✅ 已解决（6da15ea：auto-fifo 改 dry-run 预览 + 前端确认后 batch-execute + execute() 防重复校验） |

### 跳转目标路由（已核实存在）

| 节点类型 | 跳转路径 | 参数 |
|---------|---------|------|
| 银行流水 | `/finance/bank-statement` | 列表页（无 query 定位，跳列表） |
| 收款单/下游单据 | `/finance/business-doc/detail?id={id}` | `BusinessDocDetail.vue` 支持 `route.query.id` |
| 凭证 | `/finance/voucher/detail?id={id}` | `VoucherDetail.vue` 支持 `route.query.id` |
| 核销单 | `/arap/reconciliation?tab=settlement` | SettlementPanel 所在页 |

---

## 1. 输入契约
→ 见本文各节 API 端点：GET /api/v1/reconciliation/{id}/trace / POST /api/v1/reconciliation/auto-fifo

## 2. 输出契约
→ 见本文各节验收标准：1.4 / 2.4 / 3.5 / V2.0 新增 AT-P42-V2-*

## 3. 状态流转
→ 见本文 [## 3.2 设计原则 — FIFO 自动核销的人审约束](#3-p42-3fifo-自动核销按钮req-2026-057) 及 V2.0 G2 的 dry-run→确认→batch-execute 流转

## 4. 异常处理
→ 见本文各 BusinessException 抛出点（如 FIFO 匹配失败、参数校验不通过、重复核销拦截）

## 0. 现状

后端已有但前端未使用的能力：

| 能力 | 后端状态 | 前端状态 |
|------|---------|---------|
| 全链路追溯 API | ✅ `GET /api/v1/reconciliation/{id}/trace` | 🔴 无调用入口 |
| 编号追溯 API | ✅ `GET /api/v1/trace/by-doc-no` | 🔴 无调用入口 |
| FIFO 自动核销 | ✅ `autoReconcileFifo()` 已实现 | 🔴 无触发按钮 |

---

## 1. P42-1：核销全链路 Timeline 视图（REQ-2026-055）

### 1.1 目标

在核销单详情页（或核销日志页）增加 Timeline 组件，以时间轴形式展示从`银行流水→收款单→核销单→凭证`的完整生命周期。

### 1.2 后端改动

**无**。复用现有 `GET /api/v1/reconciliation/{id}/trace` API。

### 1.3 前端改动

**新增组件**：`frontend/src/views/arap/reconciliation-workbench/ReconciliationTimeline.vue`

| 功能 | 说明 |
|------|------|
| 时间轴展示 | 按时间顺序从上到下展示 4 个节点：银行流水→收款单→核销单→凭证 |
| 节点状态 | 每个节点显示：操作类型、操作时间、操作人、金额 |
| 节点颜色 | 已完成 = 绿色，进行中 = 橙色，未开始 = 灰色 |
| 点击跳转 | 每个节点可点击跳转到对应单据详情页 |

**数据流**：

```
ReconciliationTimeline.vue
  └── onMounted → fetch(`/api/v1/reconciliation/${id}/trace`)
       └── 解析 upstream.bankTransaction / upstream.receipt
       └── 解析 settlement
       └── 解析 downstream.businessDocs / downstream.invoices
       └── 解析 voucher
       └── 解析 operationTrail → 按时间排序 → 渲染时间轴
```

### 1.4 验收标准

| ID | 描述 | 断言 |
|----|------|------|
| AT-P42-1-1 | Timeline 展示 4 个节点 | `timeline.nodes.length >= 4` |
| AT-P42-1-2 | 每个节点有操作时间、操作人、金额 | `node.time != null && node.operator != null` |
| AT-P42-1-3 | 单击节点可跳转对应单据 | `click(node) → router.push(detailUrl)` |

### 1.5 V2.0 缺口 G1：节点 jumpPath 生成（REQ-055 + REQ-056）✅ 已解决（9fd94a4）

**现状**：`ReconciliationTimeline.vue` `buildNodes()` 中所有节点 `jumpPath: null`，`onNodeClick` 从不 emit jump。`SettlementPanel.vue` 的 `onTimelineJump(path)` 已就绪。

**改动**（仅前端 `ReconciliationTimeline.vue`）：

| 节点类型 | jumpPath 生成规则 |
|---------|------------------|
| 银行流水 | `/finance/bank-statement`（跳列表） |
| 收款单 | `/finance/business-doc/detail?id=${receipt.id}` |
| 下游单据 | `/finance/business-doc/detail?id=${doc.id}` |
| 凭证 | `/finance/voucher/detail?id=${voucher.id}` |
| 核销单 | `/arap/reconciliation?tab=settlement` |
| 操作轨迹 | null（纯展示，不可跳转） |

**实施记录（2026-08-18）**：`ReconciliationTimeline.vue` `buildNodes()` 按上表生成 jumpPath；发票节点按 `invoiceType` 跳 `/tax/output-invoice` 或 `/tax/input-invoice`（后端 `ReconciliationTraceVO.InvoiceInfo` 补 `invoiceType` 字段 + `trace()` 双向填充）。验证：vue-tsc EXIT=0、vitest 14 passed、mvn compile EXIT=0。

### 1.6 V2.0 缺口 G3：trace() 填充 downstream.invoices（REQ-055）✅ 已解决（69e6fbc）

**现状**：`ReconciliationTraceVO.DownstreamInfo` 有 `invoices` 字段，但 `ReconciliationServiceImpl.trace()` 只 set `businessDocs`，未填充 `invoices` → Timeline 不展示发票节点。

**改动**（后端 `ReconciliationServiceImpl.trace()`）：当 `log.getTargetDocId()` 对应 BusinessDoc 存在且 `doc.getInvoiceId() != null` 时，查询发票并填充 `downstream.invoices`。前端 `ReconciliationTimeline.vue` 增加发票节点渲染（`INVOICE_OUT/INVOICE_IN` 发票，jumpPath 指向发票详情或业务单详情）。

**实施记录（2026-08-18）**：`trace()` 在 INVOICE_OUT/INVOICE_IN 分支分别查 `outputInvoiceMapper`/`inputInvoiceMapper` 填充 `downstream.invoices`（amount 取 totalAmount fallback amount）；前端 `buildNodes()` 增加 4.1 发票节点。验证：mvn compile EXIT=0、vue-tsc EXIT=0。

---

## 2. P42-2：核销穿透点击（Drill-down）（REQ-2026-056）

### 2.1 目标

在核销单详情页增加"上游来源"和"下游去向"标签，点击后侧边抽屉或新窗口展示对应单据明细。

### 2.2 后端改动

**无**。复用现有 trace API + NumberingTraceController。

### 2.3 前端改动

**修改组件**：`frontend/src/views/arap/settlement/SettlementList.vue`（或独立详情页）

| 功能 | 说明 |
|------|------|
| 上游来源标签 | 显示"银行流水 → 收款单"链路，点击跳转银行流水或收款单详情 |
| 下游去向标签 | 显示"应收单 → 发票 → 凭证"链路，点击跳转对应单据 |
| 侧边抽屉 | 点击后弹出 `el-drawer` 展示单据明细（无需跳转页面） |
| 操作轨迹 | 列表显示该核销单的所有操作记录（创建/审核/驳回/反核销） |

**API 调用**：
```
GET /api/v1/reconciliation/{id}/trace
  → upstream / downstream / operationTrail
```

### 2.4 验收标准

| ID | 描述 | 断言 |
|----|------|------|
| AT-P42-2-1 | 核销单详情页显示上游来源标签 | `drill-down.upstream.length >= 1` |
| AT-P42-2-2 | 上游标签点击后弹出抽屉 | `click(upstream) → drawer.visible == true` |
| AT-P42-2-3 | 下游去向标签可点击 | `click(downstream) → drawer.visible == true` |
| AT-P42-2-4 | 操作轨迹列表展示 | `operationTrail.length >= 1` |

---

## 3. P42-3：FIFO 自动核销按钮（REQ-2026-057）

### 3.1 目标

在核销工作台收款单/付款单 tab 增加"自动核销"按钮，触发先进先出（FIFO）自动匹配，结果以草稿形式展示供人工确认。

### 3.2 设计原则

- **人是唯一审核主体**（§1.1）：自动匹配结果以草稿展示，不自动执行
- 用户确认后才能生成核销单
- 匹配过程使用已存在的 `autoReconcileFifo()` 方法

### 3.3 后端改动

**新增 API 端点**（或开放现有接口）：

| 方法 | 路由 | 说明 |
|------|------|------|
| POST | `/api/v1/reconciliation/auto-fifo` | 对指定客户/供应商执行 FIFO 自动匹配 |

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| partyId | Long | 是 | 客户/供应商 ID |
| partyType | String | 是 | CUSTOMER / VENDOR |
| targetDocType | String | 是 | INVOICE_OUT / INVOICE_IN |

**响应**：

```json
{
  "code": 200,
  "data": {
    "totalAmount": 1950.00,
    "allocations": [
      {
        "sourceDocId": 1,
        "sourceDocNo": "SH2026070001",
        "targetDocId": 2,
        "targetDocNo": "YS2026070001",
        "amount": 1000.00
      }
    ],
    "remainingAmount": 950.00
  }
}
```

### 3.4 前端改动

**修改组件**：`ReconciliationWorkbench.vue`

| 功能 | 说明 |
|------|------|
| 自动核销按钮 | 收款单/付款单 tab 新增"自动核销"按钮 |
| 匹配结果展示 | 弹窗展示 FIFO 匹配结果，每条可勾选 |
| 确认执行 | 选择后调用 `executeReconciliation` 执行核销 |
| 容差提示 | 如果有差额（remainingAmount > 0），提示用户是否继续 |

### 3.5 验收标准

| ID | 描述 | 断言 |
|----|------|------|
| AT-P42-3-1 | 工作台显示"自动核销"按钮 | `button.text == '自动核销'` |
| AT-P42-3-2 | 点击后展示匹配结果弹窗 | `dialog.visible == true && allocations.length > 0` |
| AT-P42-3-3 | 选择后执行核销成功 | `executeReconciliation(selected) → settlement.created == true` |
| AT-P42-3-4 | 不自动执行，需人工确认 | `autoReconcileFifo() 不自动生成 settlement` |

### 3.6 V2.0 契约 bug G2：auto-fifo 双重核销风险（REQ-057）✅ 已解决（6da15ea）

**根因**：`ReconciliationServiceImpl.autoReconcileFifo()` 内部循环调用 `execute(req)` —— **立即更新 BusinessDoc settledAmount/unsettledAmount 并写 reconciliation_log**。但：
- 前端 `WorkbenchPanel.vue` 注释与 SPEC AT-P42-3-4 要求「仅匹配不执行、草稿展示待确认」
- 用户点击弹窗「执行核销」→ `onExecuteFromFifo` 再调 `executeReconciliation`（POST /execute）→ 同一 targetDoc **二次核销**
- `execute()` 无 `request.amount() <= unsettledAmount` 防重复校验 → 重复累加 settledAmount，资金风险

**修复方案**（人审铁律 #1/#2 回归）：

| 改动 | 文件 | 说明 |
|------|------|------|
| ① auto-fifo 改 dry-run 预览 | `ReconciliationServiceImpl.autoReconcileFifo()` | 循环内**不调 execute()**，只计算分配（读取 unsettled 排序 + 金额分配），返回 `List<ReconciliationFifoPreview>`（sourceDocId/sourceDocNo/targetDocId/targetDocNo/amount），**不落库** |
| ② 前端确认后批量执行 | `WorkbenchPanel.vue` | 弹窗确认后调用已有 `POST /reconciliation/batch-execute`（`batchExecute(List<ExecuteRequest>)`），不再逐条 executeReconciliation |
| ③ execute() 防重复校验 | `ReconciliationServiceImpl.execute()` | 增加 `request.amount() <= targetDoc.getUnsettledAmount()` 校验，超量抛 BusinessException（防御双保险，防接口直调） |
| ④ 前端契约对齐 | `reconciliation.ts` | `autoFifoReconciliation` 返回类型从 `Promise<any>` 改为 `Promise<ReconciliationFifoPreview[]>`，删除未使用的 `AutoFifoResult` 接口或对齐 |

**数据流（修复后）**：

```
用户点击「自动核销」 → POST /auto-fifo（dry-run，不落库）
  → 返回预览分配列表 → 弹窗展示待确认
用户确认 → POST /batch-execute（一次性落库） → settlement + log
```

**实施记录（2026-08-18）**：① `autoReconcileFifo()` 移除 `@Transactional`、循环内不调 `execute()`、返回 `List<ReconciliationFifoPreview>`（sourceDocNo 置 null）；② `WorkbenchPanel.vue` 弹窗删逐条「执行核销」按钮、footer 加「确认执行」→ `onConfirmFifoAll()`（map previews → ExecuteRequest[] → `batchExecuteReconciliation`，matchScore=100, matchMethod='AUTO'）；③ `execute()` 在 targetDoc 加载后加 `targetUnsettled` 守卫（null 回退 amount-settled），超量抛 BusinessException「核销金额超过未核销余额」；④ `reconciliation.ts` 新增 `ReconciliationFifoPreview` 接口 + `batchExecuteReconciliation`，删除 `AutoFifoResult`。验证：mvn compile EXIT=0、ReconciliationServiceImplTest EXIT=0（dry-run 日志 previews=2）、AutoGenerationServiceTest+ReconciliationRestContractTest EXIT=0、vue-tsc EXIT=0、vitest 14 passed。

---

## 4. 施工顺序

| 批 | 内容 | 前置依赖 | 工作量估计 |
|----|------|---------|-----------|
| P42-1 | Timeline 视图 | 无（纯前端） | 小（1 个 Vue 组件） |
| P42-2 | 穿透点击 | 无（纯前端） | 中（修改 SettlementList + 抽屉组件） |
| P42-3 | FIFO 自动核销按钮 | 后端 `autoFifo` API | 中（后端 API + 前端按钮） |
| P42-V2-G2 | auto-fifo dry-run + batch-execute + execute 防重复 | P42-3 已完成 | 中（后端契约修正 + 前端确认流） |
| P42-V2-G1 | Timeline 节点 jumpPath + 发票节点 | P42-1/2 已完成 | 小（前端 Timeline 增强） |
| P42-V2-G3 | trace() 填充 downstream.invoices | 无 | 小（后端 1 处填充） |

**推荐顺序**：G3（后端最小）→ G2（资金安全优先）→ G1（前端收尾）

---

## 5. 不做事项

- ❌ 不改后端推荐算法（L1-L6 现有逻辑不动）
- ❌ 不改差额调整逻辑
- ❌ 不增加定时自动核销（违反 §1.1 铁律）
- ❌ 不增加多币种/信用期/账龄分析
- ❌ 不改 autoReconcileFifo 的 FIFO 排序规则（dueDate 升序沿用）

---

## 6. 机器可读契约

```yaml
contract_version: "2.0"
spec: P42
module: arap
requirements:
  - REQ-2026-055
  - REQ-2026-056
  - REQ-2026-057

contracts:
  - id: P42-C1
    description: "全链路追溯API返回完整数据"
    type: api
    endpoint: GET /api/v1/reconciliation/{id}/trace
    expected: "200 + 核销单信息 + 上游 + 下游(businessDocs + invoices) + 操作轨迹"

  - id: P42-C2
    description: "FIFO自动匹配返回分配结果（dry-run，不落库）"
    type: api
    endpoint: POST /api/v1/reconciliation/auto-fifo
    expected: "200 + 预览分配数组(sourceDocId/sourceDocNo/targetDocId/targetDocNo/amount)，不写 t_arap_settlement / t_reconciliation_log"

  - id: P42-C3
    description: "FIFO不自动生成核销单"
    type: assertion
    rule: "autoFifo返回预览，不写入t_arap_settlement"

  - id: P42-C4
    description: "execute防重复核销"
    type: assertion
    rule: "execute校验 amount <= unsettledAmount，超量抛BusinessException"

  - id: P42-C5
    description: "Timeline节点可跳转"
    type: assertion
    rule: "除操作轨迹外，所有节点 jumpPath 非空"

acceptance_tests:
  - id: AT-P42-1-1
    description: "Timeline展示4个节点"
    status: implemented
  - id: AT-P42-1-2
    description: "每个节点有操作时间、操作人、金额"
    status: implemented
  - id: AT-P42-1-3
    description: "单击节点可跳转对应单据"
    status: implemented
  - id: AT-P42-2-1
    description: "核销单详情显示上下游标签"
    status: implemented
  - id: AT-P42-3-1
    description: "工作台显示自动核销按钮"
    status: implemented
  - id: AT-P42-V2-1
    description: "auto-fifo返回预览不落库"
    status: implemented
  - id: AT-P42-V2-2
    description: "确认后batch-execute一次性落库"
    status: implemented
  - id: AT-P42-V2-3
    description: "execute防重复校验拦截超量"
    status: implemented
  - id: AT-P42-V2-4
    description: "trace返回downstream.invoices"
    status: implemented
  - id: AT-P42-V2-5
    description: "Timeline节点jumpPath非空(操作轨迹除外)"
    status: implemented

constraints:
  - id: C-P42-1
    type: business
    rule: "自动核销结果必须人工确认后才能执行"
    enforcement: "auto-fifo仅返回预览，batch-execute由用户确认后触发"
```

---

## 7. BDD 验收标准

### 场景 1：核销详情页 Timeline 展示完整生命周期
**Given** 一张已完成的核销单，有关联的银行流水、收款单、凭证
**When** 用户打开核销单详情页的 Timeline 视图
**Then** 时间轴展示 4 个节点（银行流水→收款单→核销单→凭证），每个节点显示操作时间、操作人和金额，已完成的节点为绿色

### 场景 2：FIFO 自动核销返回匹配结果但不会自动执行
**Given** 某客户有未核销的应收单和收款记录
**When** 用户点击"自动核销"按钮，传入客户 ID 和方向参数
**Then** 后端返回 allocations 数组（sourceDoc→targetDoc 匹配结果）和 remainingAmount，但不会自动写入 t_arap_settlement 表

### 场景 3：核销单据的穿透点击可展示上下游详情
**Given** 核销单详情页已加载，trace API 返回了完整的上游、下游和操作轨迹数据
**When** 用户点击"上游来源"标签
**Then** 弹出侧边抽屉显示上游单据明细（银行流水 / 收款单），操作轨迹列表展示该核销单的所有操作记录

### 场景 4（V2.0）：FIFO 预览不落库 + 确认后批量执行
**Given** 某客户有 3 张未核销应收单（按到期日升序）和一笔收款单
**When** 用户点击"自动核销"，后端执行 dry-run 匹配
**Then** 返回 3 条预览分配（最早到期优先），`t_arap_settlement` 与 `t_reconciliation_log` 均无新增记录
**When** 用户确认预览，前端调用 batch-execute
**Then** 一次性生成 settlement + log，目标单据 unsettledAmount 正确扣减

### 场景 5（V2.0）：execute 防重复核销
**Given** 某应收单已全额核销（unsettledAmount = 0）
**When** 再次对该单据调用 execute（amount > 0）
**Then** 抛出 BusinessException「核销金额超过未核销余额」，settledAmount 不重复累加

### 场景 6（V2.0）：Timeline 节点穿透跳转
**Given** 核销单详情 Timeline 已加载，trace 返回 bankTransaction、receipt、businessDocs、voucher
**When** 用户点击"收款单"节点
**Then** 跳转至 `/finance/business-doc/detail?id={receipt.id}`；点击"凭证"节点跳转至 `/finance/voucher/detail?id={voucher.id}`；操作轨迹节点不可点击