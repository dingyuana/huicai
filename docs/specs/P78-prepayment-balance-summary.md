# P78 SPEC — 预收预付余额汇总（贷方往来余额视图）

> **版本**：V1.0 | **最后修改**：2026-09-17 | **作者**：Hermes
> **状态**：📝 草案（待审核）
> **编号**：HUICAI-SPC-078 | 优先级：P1
> **依据**：竞品差距核查（DSN-竞品差距与管理类报表核查.md G-3）+ DSN-应收应付管理.md §8
> **目标**：提供预收/预付余额汇总查询（期初/本期新增/本期抵扣/本期冲销/期末，一行一往来单位），补齐全贷方往来余额管理视图（P75 管借方应收应付，P78 管贷方预收预付）
> **工期**：2 天（后端聚合 + 前端视图）

> **关联需求**：REQ-2026-088（预收预付余额汇总）
> **依赖 SPEC**：P75（口径对齐）、P53（预付款联动）

## 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| V1.0 | 2026-09-17 | 初版。基于代码实证（PrepaymentEntity/PrepaymentServiceImpl）设计贷方往来余额汇总，恒等式对齐 P75 |

---

## 0. 背景与问题

P75 补齐了应收应付（借方：客户欠我们 / 我们欠供应商）的余额汇总。但往来管理另一半——**预收（客户先付、我们欠客户的货/服务）与预付（我们预付供应商、供应商欠我们）**——仍是贷方敞口，只有单笔 CRUD（`PrepaymentController`：page/confirm/apply/reverse/open/available）+ 可用余额查询，缺"一行一往来单位"的汇总管理视图。

竞品基线（易代账/金蝶往来余额表）把应收应付预收预付四类余额统一成往来余额表；慧财因 `t_prepayment` 独立于 `t_business_doc`，需单独出贷方汇总。

**业务影响**：无法按客户/供应商快速定位预收/预付未结清敞口 → 退预收、追预付无数据底稿；月结时贷方往来与总账（2203 预收/1123 预付）无法核对。

---

## 1. 竞品对标

| 竞品 | 余额视图 | 关键设计 | 结论 |
|------|---------|---------|------|
| **金蝶云星空** | 往来余额表（含预收/预付贷方） | 一行一往来单位 × 期初/本期/期末；预收预付与应收应付对称但方向相反（贷方余额） | ✅ 本 SPEC 对标其贷方结构 |
| **易代账** | 客户/供应商往来汇总 | 预收预付独立台账，按单位汇总未结清 | ✅ 贷方敞口 |
| **SAP B1** | 业务伙伴余额表 + special G/L | 预收预付走特殊总账科目对账 | ✅ open balance |

**结论**：竞品一致把贷方往来余额与借方对称呈现；慧财 P75 管借方，P78 补贷方，口径对称但方向相反（预收/预付是"我们欠对方/对方欠我们"的未结清）。

---

## 2. 改动清单总览

| # | 优先级 | 改动 | 文件 | 风险 | 状态 |
|---|--------|------|------|------|------|
| 1 | P0 | 余额汇总端点 `GET /api/sme/arap/v1/prepayment/balance-summary`（期初/新增/抵扣/冲销/期末，一行一单位） | `PrepaymentController` + `PrepaymentBalanceSummaryVO` + Service 聚合（仿 `ArapBalanceReportServiceImpl` LambdaQueryWrapper + Java 分组） | ✅ 低（纯聚合，无 schema 变更） | 📝 待开发 |
| 2 | P0 | 前端预收预付余额视图（期间必填守卫 + 单位表格 + 汇总卡片） | `frontend/src/views/arap/balance/PrepaymentSummaryView.vue` | ✅ 低 | 📝 待开发 |
| 3 | P1 | 贷方往来与总账对账（预收 vs 2203、预付 vs 1123） | 后续迭代（与 P75 对账项合并） | ⚠️ 中 | 后续 |

---

## 3. 四段模板（输入/输出/状态/异常）

### 3.1 输入契约

- **触发条件**：`GET /api/sme/arap/v1/prepayment/balance-summary`
- **必填参数**：`period`（YYYYMM 会计期间，如 `202609`；6 位数字，对齐 P75/代码实证）
- **可选参数**：`partyType`（`PRE_RECEIPT` 预收=客户 / `PRE_PAYMENT` 预付=供应商；camelCase，对齐 P75 `@RequestParam` 约定）、`partyId`（按往来单位过滤，PRE_RECEIPT 对应 customerId，PRE_PAYMENT 对应 vendorId）
- **前置条件**：期间存在；数据权限拦截器自动注入 enterprise_id（铁律#6）
- **校验顺序（fail fast）**：`requirePeriod` → `normalizePartyType` → `requirePeriodExists`（参数合法性先于查库，避免传非法 partyType 时先被"期间不存在"挡住）

### 3.2 输出契约

```jsonc
{
  "period": "202609",
  "partyType": "PRE_RECEIPT",          // 显式指定单侧时回显；null = 两侧都出
  "consistent": true,
  "preReceiptTotal": 60000.00,         // 预收侧期末未结清合计（Σ preReceipts.closingUnsettled）
  "prePaymentTotal": 30000.00,         // 预付侧期末未结清合计（Σ prePayments.closingUnsettled）
  "preReceipts": [                      // 预收（按客户）；指定 partyType=PRE_PAYMENT 时为空
    {
      "partyId": 1001, "partyName": "华东商贸",
      "openingUnsettled": 50000.00,   // 期初未结清
      "currentCreated": 30000.00,     // 本期新增（预收/预付单 amount）
      "currentApplied": 20000.00,     // 本期抵扣（YS-/YF- 结算单前缀隔离）
      "currentReversed": 0.00,        // 本期冲销（reverse）
      "closingUnsettled": 60000.00    // 期末未结清
    }
  ],
  "prePayments": [ /* 预付（按供应商），结构同 preReceipts；指定 partyType=PRE_RECEIPT 时为空 */ ]
}
```

> 两侧分列对齐 P75 的 `receivables`/`payables` 结构；指定 `partyType` 时仅出对应侧，另一侧列表为空。

**口径定义（对齐代码实证）**：

- **数据源**：`t_prepayment`（`PrepaymentEntity`：`vendorId/customerId/amount/settledAmount/unsettledAmount/status/period/txDate`），预收按 `customerId` 分组（`PRE_RECEIPT`），预付按 `vendorId` 分组（`PRE_PAYMENT`）；两侧互不混入。
- **期间归属（关键）**：`PrepaymentEntity.period` 在 `create()` 未赋值，可能为 null。有效期间 = `period`（非空则用），否则回退 `txDate` 的 `yyyyMM`。所有"本期/期初"列以此有效期间归属。
- **本期新增 currentCreated**：预收/预付单 `status ∈ (CONFIRMED, APPLIED, REVERSED)`（排除 DRAFT）且 有效期间=查询期间，聚合 `amount`（含当期新建后已被抵扣/冲销的单据——"本期新增"按落库归属，不按当前状态）。
- **本期抵扣 currentApplied**：`t_arap_settlement` 按 **`settlementNo` 前缀隔离**——预收冲应收前缀 `YS-`（`partyType=CUSTOMER`），预付冲应付前缀 `YF-`（`partyType=VENDOR`）；`status ∈ (CONFIRMED, VOUCHERED)`、`period=查询期间`、`totalAmount>0`，聚合 `totalAmount`。前缀隔离是关键：P75 普通核销单前缀为 `JS/FS`（收款/付款），若不做前缀隔离，P78 会把 P75 已统计的普通核销单重复计入 currentApplied。
- **本期冲销 currentReversed**：`reverse()` 置 `status=REVERSED`，按单据有效期间（回退 `txDate` 月）归属，聚合 `amount`。
- **期末未结清 closingUnsettled**：`status ∈ (CONFIRMED, APPLIED)` 且 有效期间 ≤ 查询期间（累计口径，对齐 P75 的 `period ≤` 逻辑）求和 `unsettledAmount`；`REVERSED/DRAFT` 不计。
- **期初推导**：`openingUnsettled = closingUnsettled − currentCreated + currentApplied + currentReversed`（会计恒等式，对齐 P75；不建冗余快照表）。
- **恒等式校验**：`openingUnsettled + currentCreated − currentApplied − currentReversed == closingUnsettled`（服务内部断言，`BigDecimal.compareTo`；失败标 `consistent=false` 不阻断）。

### 3.3 状态流转（数据口径，非状态机）

```
t_prepayment（status，ArpStatus 常量）
  CONFIRMED  → 计入期末未结清（敞口）
  APPLIED    → 已抵扣，计入期末（若 unsettledAmount>0）
  REVERSED   → 已冲销，不计期末（计入 currentReversed）
  DRAFT      → 草稿，排除
```

### 3.4 异常处理

| 场景 | 处理 | 错误码 |
|------|------|--------|
| `period` 缺失或格式非法（非 6 位数字） | `BusinessException`（400，提示期间必填 YYYYMM） | P78_001 |
| 期间不存在 | `BusinessException`（400，提示创建期间） | P78_002 |
| `partyType` 非法（非 PRE_RECEIPT/PRE_PAYMENT，null 允许） | `BusinessException`（400） | P78_003 |
| 恒等式断言失败 | 日志告警 + 返回数据标 `consistent=false`，不阻断 | P78_004（WARN） |
| 数据隔离 | 拦截器注入 enterprise_id，越权查空 | — |

**事务**：纯聚合只读查询，无写操作，不加 `@Transactional`。

---

## 4. BDD 验收场景

### 场景 1：会计恒等式成立（L2 集成，Testcontainers PG16）

```gherkin
Given 某期间存在预收单（CONFIRMED, amount=30000）与抵扣结算单（CONFIRMED, period=当期, 20000）
When 查询 GET /prepayment/balance-summary?period=该期间&party_type=PRE_RECEIPT
Then 该行 openingUnsettled + currentCreated − currentApplied − currentReversed == closingUnsettled
And total.closingUnsettled == Σ 各行 closingUnsettled
```

### 场景 2：已冲销不计入期末（L2 集成，🟡 数据口径）

```gherkin
Given 一张预收单 status=REVERSED（amount=5000）
When 查询余额
Then 该单不计入 closingUnsettled
And 计入 currentReversed
```

### 场景 3：期初推导（L2 集成）

```gherkin
Given 期末未结清 closingUnsettled=60000，本期新增 30000，本期抵扣 20000，本期冲销 0
When 查询
Then openingUnsettled = 60000 − 30000 + 20000 + 0 = 50000
```

### 场景 4：期间必填守卫（L2 Controller）

```gherkin
Given 未传 period，或传 "2026-09"（非 6 位数字）
When 调用 balance-summary
Then 返回 400 P78_001，提示期间必填 YYYYMM
```

### 场景 5：数据权限隔离（L2 集成，🟡 跨租户）

```gherkin
Given 企业 A 的预收单
When 企业 B 用户查询 balance-summary
Then 结果不含企业 A 任何预收预付单（拦截器注入 enterprise_id）
```

### 场景 6：对称性负向断言（L1）

```gherkin
Given party_type=PRE_RECEIPT（客户侧）
When 查询
Then 预付（供应商侧）单据不出现在结果中（按 customerId 分组，不含 vendorId 数据）
```

---

## 5. 影响范围

| 维度 | 影响 |
|------|------|
| 数据库 | 无 schema 变更（纯聚合查询，不建新表） |
| 后端 | `PrepaymentController` +1 端点 + `PrepaymentBalanceSummaryVO` + Service 聚合 |
| 前端 | +1 视图（PrepaymentSummaryView.vue）+ 路由注册 |
| 测试 | +6 测试（6 场景，含对称负向断言） |
| API | 新增 1 端点 |

---

## 6. 遗留事项

- **贷方往来与总账对账**（P1 后续）：预收 vs 2203、预付 vs 1123 科目期未余额 + 差异定位（与 P75 借方对账项合并）
- **预收预付对冲**（竞品标配）：同一单位既是预收客户又是预付供应商，另立项
- **预收预付转应收应付**（金蝶"应收应付互转"）：预付转应付已有 `apply-to-payable`，转预收需补充，另立项

---

```yaml
# === MACHINE-READABLE CONTRACT ===
contract_version: "1.0"
entity: PrepaymentEntity
module: sme-arap
table: t_prepayment

states:
  CONFIRMED:
    description: "已确认（预收/预付单落库生效，计入期末敞口）"
    initial: true
    terminal: false
  APPLIED:
    description: "已抵扣（apply-to-payable/receivable，仍可能有剩余未结清）"
    initial: false
    terminal: false
  REVERSED:
    description: "已冲销（reverse，不计期末，计入 currentReversed）"
    initial: false
    terminal: true
  DRAFT:
    description: "草稿（不计入任何汇总列）"
    initial: false
    terminal: false

transitions:
  - id: T-01
    from: DRAFT
    to: CONFIRMED
    trigger: confirm
    precondition: "status == DRAFT"
    postcondition: "status == CONFIRMED; amount 计入 currentCreated（当期）"
    side_effects: []
    test_ref: test_confirm_creates_open_balance
  - id: T-02
    from: CONFIRMED
    to: APPLIED
    trigger: applyToPayable / applyToReceivable
    precondition: "status == CONFIRMED && unsettledAmount > 0"
    postcondition: "status = APPLIED（若全抵扣）/ 部分抵扣仍 CONFIRMED；生成 t_arap_settlement(CONFIRMED)，金额计入 currentApplied"
    side_effects:
      - entity: ArapSettlementEntity
        action: create
        status: CONFIRMED
    test_ref: test_apply_reduces_unsettled_and_settles
  - id: T-03
    from: ANY_NON_REVERSED
    to: REVERSED
    trigger: reverse
    precondition: "status in (CONFIRMED, APPLIED)"
    postcondition: "status = REVERSED; 金额计入 currentReversed，不计期末"
    side_effects: []
    test_ref: test_reverse_excluded_from_closing

constraints:
  - id: C-01
    type: business
    rule: "closingUnsettled 仅统计 status in (CONFIRMED, APPLIED) 的 unsettledAmount 求和"
    enforcement: "Service LambdaQueryWrapper status in + Java 分组求和"
  - id: C-02
    type: immutability
    rule: "恒等式 opening + created − applied − reversed == closing，失败标 consistent=false 不阻断"
    enforcement: "BigDecimal.compareTo 内部断言"
  - id: C-03
    type: business
    rule: "预收按 customerId 分组（PRE_RECEIPT），预付按 vendorId 分组（PRE_PAYMENT），两侧互不混入"
    enforcement: "Service 按 partyType 选择分组字段"

acceptance_tests:
  - id: AT-001
    description: "会计恒等式成立"
    method: test_identity_holds
    assertion: "openingUnsettled + currentCreated − currentApplied − currentReversed == closingUnsettled"
    status: missing
  - id: AT-002
    description: "已冲销不计期末"
    method: test_reversed_excluded_from_closing
    assertion: "REVERSED 单 closingUnsettled 不含其金额，计入 currentReversed"
    status: missing
  - id: AT-003
    description: "期初推导正确"
    method: test_opening_derivation
    assertion: "openingUnsettled = closing − created + applied + reversed"
    status: missing
  - id: AT-004
    description: "期间必填守卫"
    method: test_period_required
    assertion: "无 period 或格式非法返回 400 P78_001"
    status: missing
  - id: AT-005
    description: "数据权限隔离"
    method: test_tenant_isolation
    assertion: "跨企业查询返回空（enterprise_id 拦截）"
    status: missing
  - id: AT-006
    description: "预收/预付对称不混入"
    method: test_party_type_symmetry
    assertion: "PRE_RECEIPT 结果不含供应商预付数据"
    status: missing

out_of_scope:
  - "借方应收应付余额（P75 负责）"
  - "贷方往来与总账对账（P75 遗留项合并）"
  - "预收预付转应收应付（另行立项）"
  - "schema 变更（无）"

dependencies:
  - spec: P75
    relation: "口径/恒等式/期间格式/数据权限完全对齐 P75（ArapBalanceReportServiceImpl 同源）"
  - spec: P53
    relation: "apply-to-payable 预付款联动已有，本 SPEC 复用其结算单"
```

> **文档结束**。关联：[P75-arap-balance-summary](./P75-arap-balance-summary.md) | [DSN-应收应付管理](../design/DSN-应收应付管理.md) §8
