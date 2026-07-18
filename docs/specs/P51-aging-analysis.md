# P51 SPEC — 账龄分析与逾期预警

> **编号**：HUICAI-SPC-051
> **版本**：V1.0 | **日期**：2026-07-11
> **状态**：📝 草案（待审核）
> **关联需求**：REQ-2026-015（坏账计提）

## 1. 输入契约
→ 见本文 二、详细设计（2.1 账龄分析引擎、2.2 账龄分析表 API 请求结构）、三、API 端点

## 2. 输出契约
→ 见本文 一、需求概述（1.3 验收标准）、三、API 端点（响应结构示例）

## 3. 状态流转
→ 见本文 2.4 逾期预警（MILD→MODERATE→SEVERE→CRITICAL 四级预警）、2.3 到期债权表

## 4. 异常处理
→ 见本文 2.4 预警去重逻辑、2.6 前端功能（人工忽略、标记已处理）

> **关联文档**：[DESIGN.md](../DESIGN.md), [02-arap-design.md](../design/02-arap-design.md), [P43-bad-debt-provision.md](P43-bad-debt-provision.md)
> **版本历史**：
> - V1.0 (2026-07-11): 初始版本

---

## 一、需求概述

### 1.1 业务背景

核销完成后，系统需要对未结清的应收款项进行账龄分析，为催收跟进和坏账计提提供数据基础。账龄分析位于"客户对账"和"坏账计提"之间，三者构成年底结账的完整闭环。

```
核销完成 → 客户对账 → 账龄分析 → 坏账计提
                          │
                          ├─ 账龄分析表：按区间展示各客户应收余额分布
                          ├─ 到期债权表：已到期未核销应收明细及过期天数
                          └─ 逾期预警：超信用期按等级自动通知
```

### 1.2 关键决策

| 决策项 | 结论 |
|--------|------|
| 数据范围 | 应收账款(INVOICE_OUT) + 其他应收款(OTHER_RECEIVABLE) + 预付账款(t_prepayment) + 应收票据(NOTE_RECEIVABLE) |
| 分析维度 | 按区间汇总 + 按客户明细 |
| 账龄区间 | 与坏账计提方案一致（信用期/1-30/31-60/61-90/91-180/181-365/365+） |
| 触发方式 | 年底结账前人工触发 |
| 逾期预警 | 系统内通知，4 级预警等级 |

### 1.3 验收标准

| # | 标准 | 验证方式 |
|---|------|---------|
| 1 | 账龄分析表能正确按区间统计未清应收金额和笔数 | 单元测试 + 数据验证 |
| 2 | 按客户维度的账龄分布数据准确 | 单元测试 |
| 3 | 到期债权表只包含已到期未核销单据，按逾期天数降序排列 | 单元测试 |
| 4 | 逾期预警按等级正确触发，不重复生成已有预警 | 单元测试 |
| 5 | 前端可查看账龄分布图、到期债权明细、预警列表 | 手工验证 |

---

## 二、详细设计

### 2.1 账龄分析引擎

**复用现有组件**：`BadDebtServiceImpl.computeAgingBucket()` 已实现基本账龄计算逻辑，本模块直接复用。

**数据来源**：

| 类型 | 数据表 | 过滤条件 |
|------|--------|---------|
| INVOICE_OUT | `t_business_doc` | `doc_type='INVOICE_OUT'`, `unsettled_amount > 0` |
| OTHER_RECEIVABLE | `t_business_doc` | `doc_type='OTHER_RECEIVABLE'`, `unsettled_amount > 0` |
| PREPAYMENT | `t_prepayment` | `unsettled_amount > 0` |
| NOTE_RECEIVABLE | `t_business_doc` | `doc_type='NOTE_RECEIVABLE'`, `unsettled_amount > 0` |

**账龄区间定义**（与计提方案 `t_bad_debt_provision_scheme_item` 一致）：

| 区间标识 | 天数范围 | 展示标签 | 说明 |
|---------|---------|---------|------|
| `current` | `days ≤ 0` | 信用期内 | 未到期 |
| `days_1_30` | `1 ≤ days ≤ 30` | 1-30天 | 轻度逾期 |
| `days_31_60` | `31 ≤ days ≤ 60` | 31-60天 | 中度逾期 |
| `days_61_90` | `61 ≤ days ≤ 90` | 61-90天 | 较重逾期 |
| `days_91_180` | `91 ≤ days ≤ 180` | 91-180天 | 严重逾期 |
| `days_181_365` | `181 ≤ days ≤ 365` | 181-365天 | 长期逾期 |
| `over_365` | `days > 365` | 365天以上 | 极严重逾期 |

### 2.2 账龄分析表

**API**：`GET /api/v1/aging-analysis/summary?period=YYYYMM&customerId=`

**处理逻辑**：

```java
List<AgingBucketResult> analyzeAging(String period, Long customerId) {
    // 1. 加载所有未清应收单据（4 数据源）
    // 2. 对每张单据计算 aging_days 和所属区间
    // 3. 按区间分组汇总金额和笔数
    // 4. 计算总逾期金额和逾期率
    // 5. 返回结构化结果
}
```

**响应结构**：

```json
{
  "reportDate": "2026-12-31",
  "period": "202612",
  "summary": {
    "totalUnsettled": 1500000.00,
    "totalOverdue": 800000.00,
    "overdueRate": "53.33%"
  },
  "agingBuckets": [
    { "label": "信用期内", "amount": 700000.00, "count": 20, "percentage": "46.67%" },
    { "label": "1-30天",    "amount": 300000.00, "count": 8,  "percentage": "20.00%" },
    { "label": "31-60天",   "amount": 200000.00, "count": 5,  "percentage": "13.33%" },
    { "label": "61-90天",   "amount": 150000.00, "count": 3,  "percentage": "10.00%" },
    { "label": "91-180天",  "amount": 100000.00, "count": 2,  "percentage": "6.67%" },
    { "label": "181-365天", "amount": 30000.00,  "count": 1,  "percentage": "2.00%" },
    { "label": "365天以上", "amount": 20000.00,  "count": 1,  "percentage": "1.33%" }
  ]
}
```

**按客户维度**：`GET /api/v1/aging-analysis/by-customer?period=YYYYMM`

```json
{
  "period": "202612",
  "customers": [
    {
      "customerId": 1,
      "customerName": "XX科技",
      "totalUnsettled": 500000.00,
      "buckets": {
        "current": 200000.00,
        "days_1_30": 150000.00,
        "days_31_60": 100000.00,
        "days_61_90": 50000.00
      }
    }
  ]
}
```

### 2.3 到期债权表

**API**：`GET /api/v1/aging-analysis/due-receivables?date=YYYY-MM-DD&customerId=`

**数据过滤**：
- `due_date < 报告日期`（已到期）
- `unsettled_amount > 0`（未结清）
- 按 `overdue_days` 降序排列

**响应结构**：

```json
{
  "reportDate": "2026-12-31",
  "totalDue": 800000.00,
  "totalDueCount": 20,
  "items": [
    {
      "customerName": "XX科技",
      "docNo": "INV2026100001",
      "docDate": "2026-10-01",
      "dueDate": "2026-10-31",
      "originalAmount": 100000.00,
      "unsettledAmount": 50000.00,
      "overdueDays": 61,
      "agingBucket": "days_31_60",
      "contactPerson": "张三",
      "contactPhone": "138xxxx"
    }
  ]
}
```

### 2.4 逾期预警

#### 预警规则

| 等级 | 触发条件 | 通知对象 | 颜色标记 |
|------|---------|---------|---------|
| MILD（轻度） | 超过信用期 1-30 天 | 业务员 | 🟡 |
| MODERATE（中度） | 超过信用期 31-60 天 | 业务主管 + 财务 | 🟠 |
| SEVERE（严重） | 超过信用期 61-90 天 | 部门经理 + 财务主管 | 🔴 |
| CRITICAL（极严重） | 超过信用期 90 天以上 | 总经理 + 法务 | ⛔ |

#### 预警数据表

```sql
CREATE TABLE IF NOT EXISTS t_aging_alert (
    id                  BIGINT        PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    customer_id         BIGINT        NOT NULL,
    doc_id              BIGINT        NOT NULL,           -- 关联业务单据
    doc_no              VARCHAR(64),
    unsettled_amount    NUMERIC(18,2) NOT NULL,
    due_date            DATE          NOT NULL,
    overdue_days        INTEGER       NOT NULL,
    alert_level         VARCHAR(20)   NOT NULL,           -- MILD / MODERATE / SEVERE / CRITICAL
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / DISMISSED / RESOLVED
    notified_at         TIMESTAMP,
    dismissed_at        TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_aging_alert_customer   ON t_aging_alert(customer_id);
CREATE INDEX idx_aging_alert_status     ON t_aging_alert(status);
CREATE INDEX idx_aging_alert_level      ON t_aging_alert(alert_level);

COMMENT ON TABLE  t_aging_alert IS '账龄预警记录';
COMMENT ON COLUMN t_aging_alert.alert_level IS 'MILD-轻度, MODERATE-中度, SEVERE-严重, CRITICAL-极严重';
COMMENT ON COLUMN t_aging_alert.status IS 'ACTIVE-生效中, DISMISSED-已忽略, RESOLVED-已解决';
```

#### 预警生成逻辑

```java
List<AgingAlertEntity> generateAlerts(String period) {
    // 1. 查到期债权表（已到期未核销）
    // 2. 对每笔按 overdue_days 确定 alert_level
    // 3. 检查 t_aging_alert 是否已有该 doc_id 的 ACTIVE 记录（去重）
    // 4. 插入新预警记录
    // 5. 返回新增的预警列表
}
```

> **去重逻辑**：同一笔应收在未解决前不重复生成预警。如果已存在的 ACTIVE 预警的 alert_level 发生变化（如从 MILD 升到 MODERATE），则更新等级。

### 2.5 与其他模块的关系

| 模块 | 关系 | 说明 |
|------|------|------|
| P43 坏账计提 | **共享引擎** | 账龄分析引擎 `computeAgingBucket()` 复用 |
| P43 坏账计提 | **数据输入** | 坏账计提使用的应有余额直接来自账龄分析结果 |
| P52 客户对账 | **数据输入** | 对账单中的账龄段数据来自本模块 |
| 应付侧（采购） | **可扩展** | 一期仅应收侧，预留应付侧接口 |

### 2.6 前端功能

| 功能 | 说明 | 优先级 |
|------|------|--------|
| 账龄分布图 | ECharts 饼图/柱状图展示各区间金额占比 | P0 |
| 按客户分布 | 可切换查看各客户账龄明细 | P0 |
| 到期债权列表 | 表格展示到期未核销明细，支持导出 | P0 |
| 预警列表 | 按等级筛选，标记已处理 | P0 |
| 预警忽略 | 手动忽略无需处理的预警 | P1 |
| 催收工单 | 后续可扩展催收跟进记录 | P2 |

---

## 三、API 端点

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/v1/aging-analysis/summary?period=YYYYMM&customerId=` | 账龄分析汇总（按区间分布） |
| GET | `/api/v1/aging-analysis/by-customer?period=YYYYMM` | 按客户维度的账龄分析 |
| GET | `/api/v1/aging-analysis/due-receivables?date=YYYY-MM-DD` | 到期债权表（已到期未核销明细） |
| GET | `/api/v1/aging-analysis/alerts` | 查询逾期预警列表 |
| POST | `/api/v1/aging-analysis/alerts/generate` | 手动触发逾期预警扫描 |
| POST | `/api/v1/aging-analysis/alerts/{id}/dismiss` | 忽略指定预警 |
| POST | `/api/v1/aging-analysis/alerts/{id}/resolve` | 标记预警为已解决 |
| GET | `/api/v1/aging-analysis/report?period=YYYYMM` | 获取完整账龄分析报告 |

---

## 四、YAML 契约

```yaml
# ===== 账龄分析 API 契约 =====

aging-summary:
  method: GET
  path: /api/v1/aging-analysis/summary
  params:
    period: { type: string, required: true, desc: "期间 YYYYMM" }
    customerId: { type: integer, required: false }
  response:
    reportDate: "2026-12-31"
    period: "202612"
    summary:
      totalUnsettled: 1500000.00
      totalOverdue: 800000.00
      overdueRate: "53.33%"
    agingBuckets:
      - label: "信用期内"
        amount: 700000.00
        count: 20
        percentage: "46.67%"
      - label: "1-30天"
        amount: 300000.00
        count: 8
        percentage: "20.00%"

due-receivables:
  method: GET
  path: /api/v1/aging-analysis/due-receivables
  params:
    date: { type: string, required: true, desc: "报告日期 YYYY-MM-DD" }
    customerId: { type: integer, required: false }
  response:
    reportDate: "2026-12-31"
    totalDue: 800000.00
    totalDueCount: 20
    items:
      - customerName: "XX科技"
        docNo: "INV2026100001"
        dueDate: "2026-10-31"
        unsettledAmount: 50000.00
        overdueDays: 61
        agingBucket: "days_31_60"

generate-alerts:
  method: POST
  path: /api/v1/aging-analysis/alerts/generate
  body:
    period: { type: string, required: true }
  response:
    generatedCount: 15
    alerts:
      - id: 1
        customerId: 1
        docNo: "INV2026100001"
        unsettledAmount: 50000.00
        overdueDays: 61
        alertLevel: "MODERATE"
        status: "ACTIVE"
```

---

## 五、数据模型

### 新增表

**t_aging_alert** — 见 §2.4

### 无新增表（复用已有数据源）

账龄分析不存储中间结果，每次请求实时计算。如需历史快照（按年存档），后续可在实施时扩展。

---

## 六、实施计划

### M1: 数据库
| 任务 | 工时 |
|------|------|
| t_aging_alert 表创建 | 0.5h |
| 索引 + 注释 | 0.5h |

### M2: 后端
| 任务 | 工时 |
|------|------|
| AgingAnalysisService 接口 + 实现（复用 computeAgingBucket） | 2h |
| 账龄汇总分析 API | 1.5h |
| 按客户维度分析 API | 1h |
| 到期债权表 API | 1h |
| 逾期预警生成 + 去重逻辑 | 1.5h |
| 预警查询/忽略/解决 API | 1h |

### M3: 前端
| 任务 | 工时 |
|------|------|
| 账龄分布图（ECharts） | 2h |
| 按客户切换 | 1h |
| 到期债权列表 | 1.5h |
| 预警列表 + 操作按钮 | 2h |

### M4: 测试
| 任务 | 工时 |
|------|------|
| 账龄汇总计算测试 | 1h |
| 到期债权过滤测试 | 0.5h |
| 预警生成 + 去重测试 | 1h |
| Controller 测试 | 1h |

---

## 七、未纳入范围

| 功能 | 原因 |
|------|------|
| 催收工单系统 | 需独立跟进记录，可后续扩展 |
| 邮件/SMS 预警推送 | 一期仅系统内通知 |
| 应付侧（采购）账龄分析 | 一期聚焦应收，预留接口 |
| 账龄历史趋势图 | 需历史快照存储，二期 |

---

## 八、BDD 验收标准

### 场景 1：账龄分析表按区间正确统计
**Given** 存在未清应收单据属于不同账龄区间  
**When** 调用 GET /api/v1/aging-analysis/summary  
**Then** 返回按 7 个区间分组的金额和笔数汇总  
**And** 各区间百分比之和为 100%

### 场景 2：到期债权表只含已到期未核销单据
**Given** 存在已到期和未到期的应收单据  
**When** 调用 GET /api/v1/aging-analysis/due-receivables  
**Then** 结果只包含 due_date < 报告日期 且 unsettled_amount > 0 的记录  
**And** 按逾期天数降序排列

### 场景 3：逾期预警不重复生成
**Given** 同一笔应收已有 ACTIVE 状态的预警记录  
**When** 再次触发预警扫描  
**Then** 不新增重复预警  
**And** 若预警等级发生变化则更新现有记录