# P42 SPEC — 核销能力补充：Timeline 视图 + 穿透点击 + FIFO 自动核销

> **编号**：HUICAI-SPC-042 | **优先级**：P1
> **依据**：核销参考设计比对评估，三项前端能力可补充
> **关联需求**：REQ-2026-055, REQ-2026-056, REQ-2026-057
> **版本**：V1.0 | **日期**：2026-07-08

---

## 1. 输入契约
→ 见本文各节 API 端点：GET /api/v1/reconciliation/{id}/trace / POST /api/v1/reconciliation/auto-fifo

## 2. 输出契约
→ 见本文各节验收标准：1.4 / 2.4 / 3.5

## 3. 状态流转
→ 见本文 [## 3.2 设计原则 — FIFO 自动核销的人审约束](#3-p42-3fifo-自动核销按钮req-2026-057)

## 4. 异常处理
→ 见本文各 BusinessException 抛出点（如 FIFO 匹配失败、参数校验不通过）

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

---

## 4. 施工顺序

| 批 | 内容 | 前置依赖 | 工作量估计 |
|----|------|---------|-----------|
| P42-1 | Timeline 视图 | 无（纯前端） | 小（1 个 Vue 组件） |
| P42-2 | 穿透点击 | 无（纯前端） | 中（修改 SettlementList + 抽屉组件） |
| P42-3 | FIFO 自动核销按钮 | 后端 `autoFifo` API | 中（后端 API + 前端按钮） |

**推荐顺序**：P42-1 → P42-2 → P42-3（逐步交付）

---

## 5. 不做事项

- ❌ 不改后端推荐算法（L1-L6 现有逻辑不动）
- ❌ 不改差额调整逻辑
- ❌ 不增加定时自动核销（违反 §1.1 铁律）
- ❌ 不增加多币种/信用期/账龄分析

---

## 6. 机器可读契约

```yaml
contract_version: "1.0"
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
    expected: "200 + 核销单信息 + 上游 + 下游 + 操作轨迹"

  - id: P42-C2
    description: "FIFO自动匹配返回分配结果"
    type: api
    endpoint: POST /api/v1/reconciliation/auto-fifo
    expected: "200 + allocations数组 + remainingAmount"

  - id: P42-C3
    description: "FIFO不自动生成核销单"
    type: assertion
    rule: "autoFifo返回草稿，不写入t_arap_settlement"

acceptance_tests:
  - id: AT-P42-1-1
    description: "Timeline展示4个节点"
    status: planned
  - id: AT-P42-2-1
    description: "核销单详情显示上下游标签"
    status: planned
  - id: AT-P42-3-1
    description: "工作台显示自动核销按钮"
    status: planned

constraints:
  - id: C-P42-1
    type: business
    rule: "自动核销结果必须人工确认后才能执行"
    enforcement: "前端弹窗展示结果，用户勾选后手动触发执行"

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