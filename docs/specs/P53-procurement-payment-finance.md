# P53 SPEC — 采购付款财务流程

> **编号**：HUICAI-SPC-053
> **版本**：V1.0 | **日期**：2026-07-11
> **状态**：✅ 已实现（V1.0）
> **关联需求**：REQ-2026-015（坏账计提，对称扩展）

## 1. 输入契约
→ 见本文 三、详细设计（3.1 应付账龄分析 API 请求、3.2 付款计划、3.3 采购退货）、3.6 API 端点总表

## 2. 输出契约
→ 见本文 一、需求概述（1.3 验收标准）、三、详细设计（各 API 响应结构示例）

## 3. 状态流转
→ 见本文 四、与销售收款循环的对称关系（采购付款与销售收款镜像对称：INVOICE_IN→PAYMENT→核销）

## 4. 异常处理
→ 见本文 3.4 重复付款拦截（BusinessException 抛出）、3.5 预付冲应付自动化

> **关联文档**：[项目说明](../项目说明.md), [技术方案](../技术方案.md), [需求分析](../需求分析.md), [02-arap-design.md](../design/02-应收应付管理.md), [P51-aging-analysis.md](P51-aging-analysis.md)
> **版本历史**：
> - V1.1 (2026-07-11): M2付款计划+M3采购退货+M4预付款提示+重复付款拦截 已实现
> - V1.0 (2026-07-11): 初始版本

---

## 一、需求概述

### 1.1 业务定位

采购付款循环与销售收款循环形成**完全对称的镜像结构**：

| 维度 | 销售收款（已有） | 采购付款（本 SPEC） |
|------|----------------|-------------------|
| 方向 | 资金流入 | 资金流出 |
| 核心对象 | 客户 / 应收账款 | 供应商 / 应付账款 |
| 核心单据 | INVOICE_OUT → RECEIPT | INVOICE_IN → PAYMENT |
| 核销 | 收款核销应收 | 付款核销应付 |
| 账龄分析 | P51 应收账龄 | **本 SPEC 扩展** |
| 预收/预付 | 预收账款 | 预付账款（已有） |

**范围确认**：只做财务侧的发票、付款、预付款流程，不做采购申请/采购订单/入库单等业务单据。

### 1.2 关键决策

| 决策项 | 结论 |
|--------|------|
| 供应商管理 | 已有 `VendorEntity`，维持现状 |
| 进项发票 | 已有，维持现状 |
| 应付单 | 已有 `t_business_doc(INVOICE_IN)`，维持现状 |
| 应付账龄分析 | **扩展 P51 引擎**，增加供应商维度 |
| 付款计划 | 新增：按到期日排序生成付款建议 |
| 采购退货（财务） | 新增：红字应付单 + 进项税转出凭证 |
| 重复付款拦截 | 新增：按发票号校验 |
| 付款审批流 | 暂不做（二期） |
| 预算控制 | 已有预算模块，可联动（二期） |

### 1.3 验收标准

| # | 标准 | 验证方式 |
|---|------|---------|
| 1 | 应付账龄分析按供应商维度展示各区间余额 | 单元测试 + 手工验证 |
| 2 | 付款计划按到期日排序，自动生成建议付款列表 | 手工验证 |
| 3 | 采购退货生成红字应付单 + 进项税转出凭证 | E2E 测试 |
| 4 | 重复付款拦截：同一发票号不得重复付款 | 单元测试 |
| 5 | 付款单生成后自动核销对应应付单 | 集成测试 |

---

## 二、当前状态 vs 目标状态

### 已存在（无需重建）

| 组件 | 位置 | 说明 |
|------|------|------|
| 供应商档案 | `VendorEntity` | code, name, contact, bank, creditLimit, creditDays |
| 进项发票 | `InputInvoiceEntity` | 导入 + 管理 |
| 应付单 | `t_business_doc(INVOICE_IN)` | 通过 P34 统一业务单据体系 |
| 预付款 | `PrepaymentEntity` + `PrepaymentService` | 创建/确认/冲应付/红冲 |
| 付款核销 | `ReconciliationServiceImpl` | INVOICE_IN ↔ PAYMENT 匹配 |
| 凭证生成 | 模板引擎 | 自动制证 |
| 付款单 | `t_business_doc(PAYMENT)` | 银行流水B类路由生成 |

### 待新增

| 组件 | 差距 | 优先级 |
|------|------|--------|
| 应付账龄分析 | ❌ 端点是空壳，需扩展 P51 引擎 | P0 |
| 付款计划 | ❌ 不存在 | P1 |
| 采购退货（财务处理） | ❌ 不存在 | P1 |
| 重复付款拦截 | ❌ 不存在 | P1 |
| 预付冲应付自动化 | ⚠️ 接口有，缺少自动提醒 | P1 |

---

## 三、详细设计

### 3.1 应付账龄分析

**直接复用 P51 账龄分析引擎**，增加供应商维度。

**API 扩展**（在 `AgingAnalysisController` 追加）：

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/v1/aging-analysis/payable-summary?period=YYYYMM&vendorId=` | 应付账龄汇总（按区间分布） |
| GET | `/api/v1/aging-analysis/payable-by-vendor?period=YYYYMM` | 按供应商维度的应付账龄 |
| GET | `/api/v1/aging-analysis/payable-due?date=YYYY-MM-DD` | 到期应付表（已到期未付明细） |

**数据源**：`t_business_doc` where `doc_type IN ('INVOICE_IN', 'PAYMENT', 'OTHER_PAYABLE')` and `unsettled_amount > 0`

**关键区别**：与应收账龄对称，但方向相反——应收是"客户欠我"，应付是"我欠供应商"。

### 3.2 付款计划

**用途**：根据应付单到期日自动生成未来付款安排，便于资金规划。

**API**：`GET /api/v1/payment-plans?period=YYYYMM&vendorId=`

**处理逻辑**：

```java
List<PaymentPlanVO> generatePaymentPlan(String period) {
    // 1. 查询所有未清应付单（INVOICE_IN/OTHER_PAYABLE, unsettled_amount > 0）
    // 2. 按到期日排序（升序，最早到期优先）
    // 3. 按供应商汇总（同一供应商多笔应付合并）
    // 4. 计算建议付款日期（到期日前 3 个工作日）
    // 5. 返回付款建议列表
}
```

**响应结构**：

```json
[
  {
    "vendorId": 1,
    "vendorName": "XX供应商",
    "totalDue": 100000.00,
    "items": [
      {
        "docNo": "PO2026120001",
        "dueDate": "2026-12-31",
        "unsettledAmount": 50000.00,
        "overdueDays": 0,
        "suggestedPayDate": "2026-12-28",
        "priority": "NORMAL"
      }
    ]
  }
]
```

**优先级规则**：

| 条件 | 优先级 |
|------|--------|
| 已逾期 90 天以上 | CRITICAL |
| 已逾期 31-90 天 | HIGH |
| 已逾期 1-30 天 | MEDIUM |
| 7 天内到期 | NORMAL |
| 超过 7 天到期 | LOW |

### 3.3 采购退货（财务处理）

**用途**：已入库的货物退货后，财务侧需冲减应付并处理进项税转出。

**API**：`POST /api/v1/purchase-returns`

**请求体**：

```json
{
  "originalDocNo": "PO2026120001",
  "vendorId": 1,
  "returnAmount": 10000.00,
  "taxAmount": 1300.00,
  "reason": "质量不合格",
  "sourceDocType": "INVOICE_IN"
}
```

**处理逻辑**：

```java
@Transactional
public PurchaseReturnVO createReturn(PurchaseReturnRequest request) {
    // 1. 查找原始应付单
    // 2. 校验退货金额 ≤ 应付单未清金额
    // 3. 创建红字业务单据（RED_RETURN 类型）
    // 4. 生成退货凭证：
    //    借：应付账款——供应商    XXX
    //      贷：原材料/库存商品          YYY
    //          应交税费——进项税额转出    ZZZ
    // 5. 更新原始应付单：unsettled_amount -= returnAmount
}
```

**凭证模板**：

| 业务事件 | 借方 | 贷方 |
|---------|------|------|
| 退货冲减应付 | 应付账款——供应商（退货金额） | 原材料/库存商品（不含税） |
| 退货进项税转出 | — | 应交税费——进项税额转出（税额） |

### 3.4 重复付款拦截

**拦截点**：在付款单创建/确认时校验。

**校验逻辑**：

```java
void validateNoDuplicatePayment(String invoiceNo, Long vendorId) {
    // 1. 查询 t_business_doc（PAYMENT 类型）中是否有已关联该 invoiceNo 的记录
    // 2. 如果有且已核销（settled_amount > 0），抛出 BusinessException
    // 3. 如果有但未核销，发出警告（允许用户确认继续）
}
```

### 3.5 预付冲应付自动化

**当前流程**：`PrepaymentService.applyToPayable()` 已存在，但需要手动触发。

**改进**：付款单核销时，自动检测该供应商是否有未冲销的预付款，如有则提示用户"是否优先使用预付款冲抵"。

**API**：`GET /api/v1/prepayments/available?vendorId=&amount=`

**响应**：

```json
{
  "hasAvailablePrepayment": true,
  "totalPrepayment": 50000.00,
  "suggestedOffset": 30000.00
}
```

### 3.6 API 端点总表

| 方法 | 端点 | 说明 | 模块 |
|------|------|------|------|
| GET | `/api/v1/aging-analysis/payable-summary` | 应付账龄汇总 | P53 |
| GET | `/api/v1/aging-analysis/payable-by-vendor` | 按供应商应付账龄 | P53 |
| GET | `/api/v1/aging-analysis/payable-due` | 到期应付表 | P53 |
| GET | `/api/v1/payment-plans` | 付款计划 | P53 |
| POST | `/api/v1/purchase-returns` | 采购退货（财务） | P53 |
| GET | `/api/v1/prepayments/available` | 可用预付款查询 | P53 |

### 3.7 新增数据表

```sql
-- 采购退货记录
CREATE TABLE IF NOT EXISTS t_purchase_return (
    id                  BIGINT        PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    return_no           VARCHAR(64)   NOT NULL,
    vendor_id           BIGINT        NOT NULL,
    original_doc_no     VARCHAR(64),
    original_doc_id     BIGINT,
    return_amount       NUMERIC(18,2) NOT NULL,
    tax_amount          NUMERIC(18,2) NOT NULL DEFAULT 0,
    reason              TEXT,
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    -- DRAFT / CONFIRMED / VOUCHERED / REVERSED
    voucher_id          BIGINT,
    voucher_no          VARCHAR(64),
    created_by          BIGINT,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             INTEGER       NOT NULL DEFAULT 0
);

COMMENT ON TABLE  t_purchase_return IS '采购退货记录';
COMMENT ON COLUMN t_purchase_return.return_amount IS '退货金额（含税）';
COMMENT ON COLUMN t_purchase_return.tax_amount IS '进项税额转出金额';
```

---

## 四、与销售收款循环的对称关系

| 销售功能 | 采购对应 | 状态 |
|---------|---------|------|
| P51 应收账龄分析 | ⬅ **应付账龄分析**（扩展 P51） | ⏳ 本 SPEC |
| P52 客户对账 | 供应商对账（对称） | ⏳ 后续 |
| P43 坏账计提 | 预付减值（对称） | ⏳ 后续 |
| 收款核销 | 付款核销 | ✅ 已有 |
| 预收账款 | 预付账款 | ✅ 已有 |
| 销售退货 | 采购退货 | ⏳ 本 SPEC |

---

## 五、实施计划

### M1: 扩展 P51 引擎（应付账龄）
| 任务 | 工时 |
|------|------|
| AgingAnalysisService 增加供应商数据源查询 | 1h |
| 新增 3 个端点（payable-summary/by-vendor/due） | 1h |
| 前端应付账龄标签页 | 1.5h |

### M2: 付款计划
| 任务 | 工时 |
|------|------|
| PaymentPlanService 接口 + 实现 | 2h |
| Controller + 前端 | 2h |

### M3: 采购退货
| 任务 | 工时 |
|------|------|
| V88 migration: t_purchase_return 表 | 0.5h |
| PurchaseReturnService（创建/确认/凭证） | 2h |
| 前端列表 + 退货弹窗 | 2h |

### M4: 预付款提示 + 重复付款拦截
| 任务 | 工时 |
|------|------|
| 可用预付款查询 API | 0.5h |
| 付款时重复校验 + 预付款提示 | 1.5h |
| 测试 | 1.5h |

### 合计
| 阶段 | 工时 |
|------|------|
| M1 应付账龄 | 3.5h |
| M2 付款计划 | 4h |
| M3 采购退货 | 4.5h |
| M4 预付提示+拦截 | 3.5h |
| **总计** | **15.5h** |

---

## 六、BDD 验收标准

### 场景 1：应付账龄分析按供应商维度展示
**Given** 存在未清应付单据（INVOICE_IN）  
**When** 调用 GET /api/v1/aging-analysis/payable-summary  
**Then** 返回按账龄区间分布的应付金额汇总  
**And** 支持按供应商维度切换查看

### 场景 2：重复付款拦截生效
**Given** 发票号 PO2026120001 已有关联付款记录  
**When** 尝试对该发票创建新的付款单  
**Then** 抛出 BusinessException  
**And** 提示"该发票已付款，请勿重复操作"

### 场景 3：采购退货生成红字应付单
**Given** 原始应付单 PO2026120001 未清金额 50000  
**When** 发起退货 10000 元  
**Then** 创建 RED_RETURN 类型红字单据  
**And** 原始应付单 unsettled_amount 减少 10000