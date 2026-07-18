# P52 SPEC — 客户对账与差异处理

> **编号**：HUICAI-SPC-052
> **版本**：V1.0 | **日期**：2026-07-11
> **状态**：📝 草案（待审核）
> **关联需求**：REQ-2026-015（坏账计提）

## 1. 输入契约
→ 见本文 二、详细设计（2.1 核心数据表、2.2 对账单生成）、三、API 端点

## 2. 输出契约
→ 见本文 一、需求概述（1.3 验收标准）、三、API 端点（响应结构示例）

## 3. 状态流转
→ 见本文 2.3 对账状态流转（DRAFT→GENERATED→SENT→CONFIRMED/DISPUTED→CONFIRMED）

## 4. 异常处理
→ 见本文 2.6 与坏账计提的联动（差异未解决前不纳入计提范围）

> **关联文档**：[DESIGN.md](../DESIGN.md), [02-arap-design.md](../design/02-arap-design.md), [P43-bad-debt-provision.md](P43-bad-debt-provision.md), [P51-aging-analysis.md](P51-aging-analysis.md)
> **版本历史**：
> - V1.0 (2026-07-11): 初始版本

---

## 一、需求概述

### 1.1 业务背景

核销工作完成后，企业与客户之间需要进行账目核对，确保双方记录一致，这是年底结账前的必要步骤。对账差异未解决的客户，其应收数据不应纳入坏账计提范围。

```
核销完成
    ↓
生成客户对账单 ──→ 发送客户确认
    │                       ↓
    │                 客户反馈差异
    │                       ↓
    ├─ 未达账项：客户已付我方未到账 / 我方已扣客户未确认
    ├─ 争议款项：金额不一致、折扣争议、退货未处理
    └─ 差异处理：查明原因 → 调整 → 闭环
    ↓
对账确认 → 账龄分析 → 坏账计提
```

### 1.2 关键决策

| 决策项 | 结论 |
|--------|------|
| 触发方式 | 年底结账前人工触发，按客户/客户组批量生成 |
| 对账单内容 | 原始应收金额 + 已核销金额 + 未核销金额 + 账龄分布 + 核销明细 |
| 差异类型 | 金额不符、单据缺失、折扣争议、其他 |
| 与坏账关系 | 有未解决差异的客户/单据不纳入坏账计提范围 |

### 1.3 验收标准

| # | 标准 | 验证方式 |
|---|------|---------|
| 1 | 对账单能正确汇总各客户的应收/已收/未收及账龄 | 单元测试 + 数据验证 |
| 2 | 对账状态正确流转（生成→发送→确认/争议→解决） | 单元测试 |
| 3 | 未达账项支持创建和解决 | 单元测试 |
| 4 | 差异处理形成闭环（差异→原因→调整→关闭） | E2E 测试 |
| 5 | 坏账计提自动跳过有未解决差异的客户 | 集成测试 |

---

## 二、详细设计

### 2.1 核心数据表

#### 2.1.1 客户对账单

本模块**不创建独立的对账单存储表**。对账单数据通过实时查询生成：

- 应收数据 → `t_business_doc`（INVOICE_OUT / OTHER_RECEIVABLE / NOTE_RECEIVABLE）
- 预付数据 → `t_prepayment`
- 核销明细 → `t_arap_settlement` + `t_arap_settlement_entry`
- 账龄数据 → 复用 P51 账龄分析引擎

**对账单状态**通过新增表记录：

```sql
CREATE TABLE IF NOT EXISTS t_customer_statement (
    id                  BIGINT        PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    customer_id         BIGINT        NOT NULL,
    customer_name       VARCHAR(200),
    period              VARCHAR(6)    NOT NULL,
    statement_date      DATE          NOT NULL,
    total_original      NUMERIC(18,2) NOT NULL,  -- 原始应收总额
    total_settled       NUMERIC(18,2) NOT NULL,  -- 已核销总额
    total_unsettled     NUMERIC(18,2) NOT NULL,  -- 未核销总额
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    -- DRAFT / GENERATED / SENT / CONFIRMED / DISPUTED
    sent_at             TIMESTAMP,
    confirmed_at        TIMESTAMP,
    created_by          BIGINT,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT chk_stmt_status CHECK (status IN ('DRAFT','GENERATED','SENT','CONFIRMED','DISPUTED'))
);

CREATE INDEX idx_cust_stmt_customer ON t_customer_statement(customer_id);
CREATE INDEX idx_cust_stmt_period   ON t_customer_statement(period);
CREATE INDEX idx_cust_stmt_status   ON t_customer_statement(status);

COMMENT ON TABLE  t_customer_statement IS '客户对账单';
COMMENT ON COLUMN t_customer_statement.status IS 'DRAFT-草稿, GENERATED-已生成, SENT-已发送, CONFIRMED-已确认, DISPUTED-存在差异';
```

#### 2.1.2 未达账项

```sql
CREATE TABLE IF NOT EXISTS t_reconciliation_outstanding (
    id                  BIGINT        PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    customer_id         BIGINT        NOT NULL,
    statement_id        BIGINT REFERENCES t_customer_statement(id),
    outstanding_type    VARCHAR(20)   NOT NULL,  -- CUSTOMER_PAID / COMPANY_DEDUCTED
    amount              NUMERIC(18,2) NOT NULL,
    description         VARCHAR(500),
    evidence            VARCHAR(200),            -- 凭证附件路径
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    -- PENDING / RESOLVED / CANCELLED
    resolved_at         TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             INTEGER       NOT NULL DEFAULT 0
);

COMMENT ON TABLE  t_reconciliation_outstanding IS '未达账项';
COMMENT ON COLUMN t_reconciliation_outstanding.outstanding_type IS 'CUSTOMER_PAID-客户已付我方未到账, COMPANY_DEDUCTED-我方已扣客户未确认';
COMMENT ON COLUMN t_reconciliation_outstanding.status IS 'PENDING-待处理, RESOLVED-已解决, CANCELLED-已取消';
```

#### 2.1.3 对账差异记录

```sql
CREATE TABLE IF NOT EXISTS t_reconciliation_dispute (
    id                  BIGINT        PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    statement_id        BIGINT        NOT NULL REFERENCES t_customer_statement(id),
    customer_id         BIGINT        NOT NULL,
    doc_no              VARCHAR(64),             -- 争议单据号
    dispute_type        VARCHAR(20)   NOT NULL,  -- AMOUNT_MISMATCH / MISSING_DOC / DISCOUNT / OTHER
    expected_amount     NUMERIC(18,2),
    actual_amount       NUMERIC(18,2),
    diff_amount         NUMERIC(18,2),
    reason              TEXT,
    resolution          TEXT,                    -- 处理方案
    resolved_by         BIGINT,
    resolved_at         TIMESTAMP,
    status              VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    -- OPEN / RESOLVED / CLOSED
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dispute_statement ON t_reconciliation_dispute(statement_id);
CREATE INDEX idx_dispute_customer  ON t_reconciliation_dispute(customer_id);
CREATE INDEX idx_dispute_status    ON t_reconciliation_dispute(status);

COMMENT ON TABLE  t_reconciliation_dispute IS '对账差异记录';
COMMENT ON COLUMN t_reconciliation_dispute.dispute_type IS 'AMOUNT_MISMATCH-金额不符, MISSING_DOC-单据缺失, DISCOUNT-折扣争议, OTHER-其他';
COMMENT ON COLUMN t_reconciliation_dispute.status IS 'OPEN-待处理, RESOLVED-已解决, CLOSED-已关闭';
```

### 2.2 对账单生成

**处理逻辑**：

```java
CustomerStatementVO generateStatement(Long customerId, String period) {
    // 1. 查询该客户所有业务单据（INVOICE_OUT / OTHER_RECEIVABLE / NOTE_RECEIVABLE）
    // 2. 查询该客户预付款（t_prepayment）
    // 3. 汇总原始金额、已核销金额、未核销金额
    // 4. 调用 P51 账龄分析引擎计算账龄分布
    // 5. 查询核销明细（含凭证号、核销日期）
    // 6. 查询未达账项
    // 7. 组装对账单结构返回
}
```

**对账单结构**：

```json
{
  "customerId": 123,
  "customerName": "XX科技有限公司",
  "period": "202612",
  "statementDate": "2026-12-31",
  "summary": {
    "totalOriginalAmount": 500000.00,
    "totalSettledAmount": 350000.00,
    "totalUnsettledAmount": 150000.00
  },
  "agingAnalysis": {
    "current": { "amount": 50000.00, "count": 3 },
    "days_1_30": { "amount": 40000.00, "count": 2 },
    "days_31_60": { "amount": 30000.00, "count": 1 },
    "days_61_90": { "amount": 20000.00, "count": 1 },
    "days_91_180": { "amount": 10000.00, "count": 1 }
  },
  "details": [
    {
      "docNo": "INV2026120001",
      "docType": "INVOICE_OUT",
      "docDate": "2026-12-01",
      "originalAmount": 100000.00,
      "settledAmount": 80000.00,
      "unsettledAmount": 20000.00,
      "dueDate": "2026-12-31",
      "agingDays": 0,
      "agingBucket": "current",
      "settlementRecords": [
        {
          "settlementNo": "ST2026120001",
          "settlementDate": "2026-12-20",
          "amount": 80000.00,
          "voucherNo": "PZ2026120001"
        }
      ]
    }
  ],
  "outstandingItems": [
    {
      "type": "CUSTOMER_PAID",
      "amount": 50000.00,
      "description": "客户已付12月货款，银行未到账",
      "status": "PENDING"
    }
  ]
}
```

### 2.3 对账状态流转

```
DRAFT ──generate──→ GENERATED（已生成，数据已计算）
                       │
                       ↓
                    SENT（已发送给客户确认）
                       │
                  ┌────┴────┐
                  ↓         ↓
             CONFIRMED   DISPUTED（存在差异）
              (一致)        │
                           ↓
                      ADJUST/RESOLVE
                           │
                           ↓
                       CONFIRMED
```

| 操作 | 前置状态 | 目标状态 | 说明 |
|------|---------|---------|------|
| `generate` | DRAFT | GENERATED | 生成对账单数据 |
| `send` | GENERATED | SENT | 标记已发送（实际通过邮件/系统通知发出） |
| `confirm` | SENT / DISPUTED | CONFIRMED | 客户确认一致 / 差异解决后确认 |
| `dispute` | SENT | DISPUTED | 客户反馈存在差异 |
| `resolve` | DISPUTED | CONFIRMED | 差异解决后直接确认（跳过 adjust 中间态） |

### 2.4 未达账项管理

**两类未达账项**：

| 类型 | 说明 | 典型场景 | 处理方式 |
|------|------|---------|---------|
| CUSTOMER_PAID | 客户已付，我方未到账 | 客户汇款后银行尚未入账 | 标记"在途"，待银行流水导入后自动匹配核销 |
| COMPANY_DEDUCTED | 我方已扣，客户未确认 | 手续费、折扣、退货扣款 | 生成对账单时标注，等待客户确认 |

**处理流程**：

1. 对账单生成时自动检测：核销金额 vs 银行流水金额 的差异
2. 差异金额 → 创建 `t_reconciliation_outstanding` 记录
3. 财务人员核实后：确认（RESOLVED）或取消（CANCELLED）
4. 已解决的未达账项：状态标记为 RESOLVED

### 2.5 差异处理闭环

**完整流程**：

```
客户反馈差异
    ↓
创建差异记录（dispute_type / expected_amount / actual_amount / diff_amount）
    ↓
财务人员查明原因（录入 reason）
    ↓
制定调整方案（录入 resolution）
    ├─ 调账凭证 → 调用凭证接口生成调整凭证
    ├─ 冲销单据 → 调用核销接口调整核销数据
    └─ 人工确认 → 直接标记差异已解决
    ↓
差异状态 → RESOLVED
    ↓
重新确认对账 → CONFIRMED
```

### 2.6 与坏账计提的联动

**铁律**：对账差异未解决前，对应客户的应收**不纳入**坏账计提范围。

```java
// P43 BadDebtServiceImpl 中查询数据源时过滤
List<BusinessDocEntity> docs = businessDocMapper.selectList(
    wrapper -> wrapper
        .in(BusinessDocEntity::getDocType, List.of("INVOICE_OUT", "OTHER_RECEIVABLE", "NOTE_RECEIVABLE"))
        .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO)
        .notIn(BusinessDocEntity::getCustomerId, 
            // 子查询：有未解决差异的客户
            getCustomersWithOpenDisputes()
        )
);
```

**差异联动检查规则**：

| 场景 | 处理 |
|------|------|
| 客户 A 有未解决差异 | 客户 A 的全部应收不列入坏账计提基数 |
| 客户 A 差异已解决 | 重新计提时纳入计算 |
| 某笔单据有差异 | 仅该笔不列入，客户其他正常单据纳入 |
| 对账单确认一致后出现新差异 | 不影响已计提的坏账（已生成凭证），下期调整 |

### 2.7 前端功能

| 功能 | 说明 | 优先级 |
|------|------|--------|
| 对账单生成 | 选择客户/期间，点击生成 | P0 |
| 对账单查看 | 详情页展示完整对账单结构 | P0 |
| 对账单导出 | PDF/Excel 导出，用于发送客户 | P0 |
| 对账操作 | 发送/确认/提交差异按钮 | P0 |
| 未达账项列表 | 展示待处理的未达账项 | P1 |
| 差异处理弹窗 | 录入原因、方案、标记解决 | P1 |
| 差异客户标记 | 坏账计提页面显示被跳过的客户 | P1 |

---

## 三、API 端点

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/v1/customer-statements/generate` | 生成客户对账单 |
| GET | `/api/v1/customer-statements/{id}` | 获取对账单详情 |
| GET | `/api/v1/customer-statements/page` | 分页查询对账单列表 |
| POST | `/api/v1/customer-statements/{id}/send` | 发送对账单 |
| POST | `/api/v1/customer-statements/{id}/confirm` | 确认对账一致 |
| POST | `/api/v1/customer-statements/{id}/dispute` | 提交差异 |
| GET | `/api/v1/outstanding-items` | 查询未达账项 |
| POST | `/api/v1/outstanding-items/{id}/resolve` | 解决未达账项 |
| POST | `/api/v1/outstanding-items/{id}/cancel` | 取消未达账项 |
| GET | `/api/v1/disputes` | 查询差异记录 |
| POST | `/api/v1/disputes/{id}/resolve` | 解决差异 |

---

## 四、YAML 契约

```yaml
# ===== 客户对账 API 契约 =====

generate-statement:
  method: POST
  path: /api/v1/customer-statements/generate
  body:
    customerIds: { type: array, required: true, desc: "客户ID列表，空=全部" }
    period: { type: string, required: true, desc: "期间 YYYYMM" }
  response:
    generatedCount: 10
    statements:
      - id: 1
        customerId: 123
        customerName: "XX科技"
        period: "202612"
        totalOriginal: 500000.00
        totalSettled: 350000.00
        totalUnsettled: 150000.00
        status: "GENERATED"

get-statement:
  method: GET
  path: /api/v1/customer-statements/{id}
  response:
    id: 1
    customerId: 123
    customerName: "XX科技"
    period: "202612"
    summary:
      totalOriginalAmount: 500000.00
      totalSettledAmount: 350000.00
      totalUnsettledAmount: 150000.00
    details: [ ... ]
    outstandingItems: [ ... ]
    status: "GENERATED"

dispute:
  method: POST
  path: /api/v1/customer-statements/{id}/dispute
  body:
    docNo: "INV2026120001"
    disputeType: "AMOUNT_MISMATCH"
    expectedAmount: 100000.00
    actualAmount: 95000.00
    reason: "客户确认实际应收为95000"
  response:
    id: 1
    statementId: 1
    disputeType: "AMOUNT_MISMATCH"
    diffAmount: -5000.00
    status: "OPEN"
```

---

## 五、实施计划

### M1: 数据库
| 任务 | 工时 |
|------|------|
| t_customer_statement 表 | 0.5h |
| t_reconciliation_outstanding 表 | 0.5h |
| t_reconciliation_dispute 表 | 0.5h |

### M2: 后端
| 任务 | 工时 |
|------|------|
| CustomerStatementService（生成 + 状态流转） | 2h |
| 对账单查询和详情 API | 1h |
| OutstandingItemService（CRUD + 解决/取消） | 1h |
| DisputeService（差异创建/解决） | 1.5h |
| 与 P43 联动：坏账计提自动跳过差异客户 | 1h |
| PDF/Excel 导出 | 1.5h |

### M3: 前端
| 任务 | 工时 |
|------|------|
| 对账单列表页 | 2h |
| 对账单详情页（含账龄/核销明细） | 2.5h |
| 对账操作按钮组（生成/发送/确认/争议） | 1.5h |
| 未达账项管理 | 1.5h |
| 差异处理弹窗 | 1.5h |

### M4: 测试
| 任务 | 工时 |
|------|------|
| 对账单生成 + 数据准确性测试 | 1.5h |
| 状态流转测试（DRAFT→CONFIRMED 完整路径） | 1h |
| 差异处理闭环 E2E 测试 | 1.5h |
| 坏账计提跳过差异客户集成测试 | 1h |
| Controller 测试 | 1h |

---

## 六、未纳入范围

| 功能 | 原因 |
|------|------|
| 自动邮件发送对账单 | 一期仅标记已发送，后续对接邮件模块 |
| 客户自助平台查看对账单 | 需独立门户，后续 |
| 催收工单自动生成 | 逾期催收可后续结合预警扩展 |
|| 批量对账差异自动处理 | 需规则引擎，非核心场景 |

---

## 七、BDD 验收标准

### 场景 1：对账单生成与状态流转
**Given** 选择客户 A 和期间 202612  
**When** 调用 POST /api/v1/customer-statements/generate  
**Then** 生成 DRAFT 状态的对账单  
**And** 对账单包含应收/已收/未收汇总及账龄分布

### 场景 2：客户确认差异后状态变为 DISPUTED
**Given** 对账单状态为 SENT，客户反馈金额不符  
**When** 调用 POST /api/v1/customer-statements/{id}/dispute  
**Then** 对账单状态变为 DISPUTED  
**And** 创建差异记录（dispute_type/amount/reason）

### 场景 3：坏账计提自动跳过有差异客户
**Given** 客户 A 有未解决差异记录  
**When** P43 坏账计提执行  
**Then** 客户 A 的全部应收不纳入计提基数  
**And** 差异解决后重新计提时纳入计算