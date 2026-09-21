# P75 SPEC — 应收应付余额汇总（账款余额视图）

> **版本**：V1.1 | **最后修改**：2026-09-17 | **作者**：Sisyphus
> **状态**：📝 草案（待审核）
> **编号**：HUICAI-SPC-075 | 优先级：P1
> **依据**：用户需求「缺乏账款的管理，应收付，未收付等数据如何得到」+ 竞品对标分析（铁律#15）
> **目标**：提供应收/应付余额汇总查询（期初/本期应收/本期实收/期末余额，一行一客商），支撑账款管理与月结对账
> **工期**：2-3 天（后端聚合 + 前端视图）

> **关联需求**: REQ-2026-085（应收应付余额汇总，本次登记）

## 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| V1.1 | 2026-09-17 | **口径修正（对齐代码实证）**：① 单据侧状态原写 `status∈(CONFIRMED, SETTLED)` 系 ArapStatus 误植，修正为 BusinessDocStatus 有效敞口四值 `APPROVED/VOUCHERED/PARTIALLY_RECONCILED/FULLY_RECONCILED`（详见 §3.2）；② period 格式 `yyyy-MM` → `YYYYMM`（代码实证：`AgingAnalysisServiceImpl`/`ReconciliationServiceImpl` 用 `DateTimeFormatter.ofPattern("yyyyMM")`，前端样例 `202607`）；③ 本期实收归属维度 `sourceDocType` → `settlement.partyType`（`CUSTOMER`/`VENDOR`，实证 `ReconciliationServiceImpl` L379），并补充 `totalAmount > 0` 排除红字 `-H` 负数对冲单（`ArapSettlementServiceImpl.reverse()` 新建负额对冲单，防双计）；④ 恒等式推导说明补充跨期语义 |
| V1.0 | 2026-09-17 | 初版。依据竞品对标（用友 U8/金蝶云星空/QuickBooks/Xero/SAP B1）设计余额汇总端点 + 前端视图，对齐「往来单位 × 期初/本期/期末」行业标准结构 |

---

## 0. 背景与问题

### 0.1 问题发现过程

用户反馈「缺乏账款的管理，应收付，未收付等数据如何得到」。现状盘点：

1. **数据载体已就绪**：`t_business_doc` 统一承载 AR/AP（`amount/settledAmount/unsettledAmount/dueDate/docDate/period/customerId/supplierId/docType/status`）；发票确认即建单（`OutputInvoiceStateMachineServiceImpl:277-278`、`InputInvoiceStateMachineServiceImpl:275-276`、`SalesInvoiceImportService:478-479`）；核销审批扣减余额（`ArapSettlementServiceImpl.approve()` L211-214/L248-251），反核销/驳回回滚（L508-512/L569-586）。
2. **缺口 1 — 无余额汇总视图**：现有出口只有未核销明细（`unmatched-receivables/unmatched-payables`）、核销日志/`summary`、账龄分析（P51）。缺少「一行一客商」的余额汇总表（金蝶应收款汇总表同构物）。
3. **缺口 2 — 无期间损益还原**：无法回答「本月新增应收多少、本月实收多少、期初还有多少未收」。
4. **缺口 3 — 无与总账对账**：业务余额 vs 总账科目（1122 应收/2202 应付）无对账报告（五家竞品全部标配）。

### 0.2 业务影响

- 无法按客户/供应商快速定位未收付敞口 → 催收/付款计划无数据底稿
- 月结时业务口径（未核销）与总账口径（科目余额）无法核对 → 差异排查靠人工

---

## 1. 竞品对标

| 竞品 | 余额视图 | 关键设计 | 结论 |
|------|---------|---------|------|
| **用友 U8** | 业务余额表/业务总账表/业务明细账/对账单 | 详细核算（逐笔追踪）与简单核算（仅汇总）两种模型；期初余额录入；账龄分析/收款账龄/收款预测/欠款分析；与总账对账 | ✅ 汇总+明细+对账三层标配 |
| **金蝶云星空** | **应收款汇总表**=期初余额/本期应收/本期实收/期末余额，按往来单位联查明细 | 应收单=债权成立唯一标志；账龄分析表=核销未完毕应收单+自定义账龄区间+按业务日期/到期日双口径；到期债权表；客户对账单可短信/电邮；核销=自动/匹配/手工/特殊+反核销；应收应付互转；总账与应收应付对账（8 项差异分析） | ✅ 行业最全：本 SPEC 直接对标其汇总表结构 |
| **QuickBooks** | AR Aging Summary（客户×账龄列）+ Detail（逐发票） | open balance 模型；aging method=Current（基于今天）/Report date；区间 1-30/31-60/61-90/91+，支持 Custom ranges；按 due date 计算逾期；**aging 总额须匹配资产负债表 AR 科目** | ✅ open balance + 与总账对账 |
| **Xero** | Aged Receivables Summary/Detail | 按 Due Date 或 Invoice Date 双口径；期数/时长可配置（默认 12 期月度）；Current 列规则；可加信用额度/可用额度列；总额=GL AR 控制科目 | ✅ 汇总+明细+对账 |
| **SAP B1** | 业务伙伴余额表、dunning 账龄、对账单 | 业务伙伴主数据+未清项（open items）追踪；统驭科目（special G/L）对账 | ✅ 未清项模型一致 |

> **来源**：
> - 用友 U8（U872 使用指南 PDF）：http://download.cucdc.com/wenku/04ced3ea42464d73bd20a0ebc04d981e.html
> - 金蝶云星空（应收应付培训笔记）：https://vip.kingdee.com/article/96184913031690496
> - QuickBooks A/R Ageing：https://quickbooks.intuit.com/learn-support/en-au/help-article/accounts-receivable-reports/run-accounts-receivable-ageing-report/L4N7PC2hg_AU_en_AU ；对账指引：https://quickbooks.intuit.com/learn-support/en-us/help-article/financial-reports/get-aging-reports-match/L9T7gcIJw_US_en_US
> - Xero Aged Receivables：https://central.xero.com/0/article/Aged-Receivables-Summary-report-New-US ；指南：https://www.xero.com/us/guides/accounts-receivable-aging-report/

**结论**：五家竞品一致采用「往来单位 × 期初/本期/期末 汇总表 + 账龄明细 + 与总账对账」三层结构；余额=未核销单据实时聚合（open balance），无冗余台账。慧财数据模型方向正确（`t_business_doc` open balance 与竞品一致），**缺的是聚合表达层**：余额汇总表（本期立项）、账龄区间配置化、与总账对账（均在遗留事项）。

---

## 2. 改动清单总览

| # | 优先级 | 改动 | 文件 | 风险 | 状态 |
|---|--------|------|------|------|------|
| 1 | P0 | 余额汇总端点 `GET /api/sme/arap/v1/report/balances`（期初/本期应收/本期实收/期末，一行一客商） | `ArapBalanceReportController` + `ArapBalanceSummaryVO` + Service 聚合（LambdaQueryWrapper 查询 + Java 分组，仿 AgingAnalysisServiceImpl） | ✅ 低（纯聚合查询，无 schema 变更） | 📝 待开发 |
| 2 | P0 | 前端余额汇总视图（期间必填守卫 + 客商表格 + 汇总卡片） | `frontend/src/views/arap/balance/BalanceSummaryView.vue` | ✅ 低 | 📝 待开发 |
| 3 | P1 | 与总账对账报告（业务余额 vs 1122/2202 科目） | 后续迭代 | ⚠️ 中 | 后续 |
| 4 | P1 | 账龄区间配置化（替代 P51 硬编码区间） | 后续迭代 | ⚠️ 中 | 后续 |

---

## 3. 四段模板（输入/输出/状态/异常）

### 3.1 输入契约

- **触发条件**：`GET /api/sme/arap/v1/report/balances`
- **必填参数**：`period`（YYYYMM 会计期间，如 `202609`；period 格式为 6 位数字，代码实证 `AgingAnalysisServiceImpl`/`ReconciliationServiceImpl` 使用 `yyyyMM`）
- **可选参数**：`customerId`、`vendorId`（按客商过滤）
- **前置条件**：期间存在；数据权限拦截器自动注入 enterprise_id（铁律#6）

### 3.2 输出契约

```jsonc
{
  "period": "202609",
  "receivableTotal": 128000.00,        // 应收期末余额合计
  "payableTotal": 86000.00,            // 应付期末余额合计
  "receivables": [                      // 一行一客商
    {
      "partyId": 1001, "partyName": "某客户",
      "openingUnsettled": 50000.00,    // 期初未核销
      "currentAmount": 30000.00,       // 本期应收
      "currentSettled": 20000.00,      // 本期实收
      "closingUnsettled": 60000.00     // 期末余额
    }
  ],
  "payables": [ /* 同构：openingUnsettled/currentAmount(应付)/currentSettled(实付)/closingUnsettled */ ]
}
```

**口径定义**（对齐代码实证）：
- 应收类单据：`docType ∈ (INVOICE_OUT, OTHER_RECEIVABLE)`，`deleted=0`，`status ∈ (APPROVED, VOUCHERED, PARTIALLY_RECONCILED, FULLY_RECONCILED)`（**BusinessDocStatus** 有效敞口四值；DRAFT/SUBMITTED 未审批、REJECTED/REVERSED/CLOSED 均不计入）
- 应付类单据：`docType ∈ (INVOICE_IN, OTHER_PAYABLE)`，同上
- **单据归属维度**：应收侧按 `customerId` 分组、应付侧按 `supplierId` 分组（业务单据一侧只挂一个客商）
- **本期实收/实付**：`t_arap_settlement` 按 `period=查询期间`、`status ∈ (CONFIRMED, VOUCHERED)`（ArapStatus）、`partyType='CUSTOMER'`（应收侧）/`'VENDOR'`（应付侧）聚合 `totalAmount`；**`totalAmount > 0`** 排除反核销红字负额对冲单（`ArapSettlementServiceImpl.reverse()` 会新建 `settlementNo+"-H"`、`totalAmount.negate()` 的负数核销单，其被审批后可达 CONFIRMED，若不排除会被误计入本期实收造成双计）
- **期初推导**：`openingUnsettled = closingUnsettled − currentAmount + currentSettled`（会计恒等式，不建冗余快照表，与竞品 open balance 模型一致）
- **恒等式校验**：`openingUnsettled + currentAmount − currentSettled == closingUnsettled`（服务内部断言，使用 `compareTo` 精度比较）

### 3.3 状态流转（数据口径，非状态机）

```
单据侧（t_business_doc）         核销侧（t_arap_settlement）
BusinessDocStatus                ArapStatus
APPROVED/VOUCHERED/
PARTIALLY_RECONCILED/
FULLY_RECONCILED → 计入余额      CONFIRMED/VOUCHERED → 计入本期实收
                                 （且 totalAmount > 0，排除 -H 红字对冲单）
REVERSED → 排除                  REVERSED → 排除（反核销不污染视图）
DRAFT/SUBMITTED/REJECTED/
CLOSED → 排除
```

### 3.4 异常处理

- `period` 缺失或格式非法（非 6 位数字）→ `BusinessException`（400，错误码提示期间必填 YYYYMM）
- 期间不存在 → `BusinessException`（400，提示创建期间）
- 金额聚合：全部使用 `BigDecimal`（铁律#7）；恒等式断言失败 → 日志告警 + 返回数据（标记 `consistent=false`），不阻断查询

---

## 4. BDD 验收场景

### 场景 1：会计恒等式成立

```gherkin
Given 某期间存在应收单据与核销记录
When 查询 GET /report/balances?period=该期间
Then 每行 openingUnsettled + currentAmount − currentSettled == closingUnsettled
And 合计 receivableTotal == Σ closingUnsettled
```

### 场景 2：已结清单据不计入期末余额

```gherkin
Given 一张 INVOICE_OUT 单据已 FULLY_RECONCILED（unsettledAmount=0）
When 查询期末余额
Then 该单据不计入 closingUnsettled（但计入本期应收 currentAmount）
```

### 场景 3：跨期核销归属正确

```gherkin
Given 一张 202608 应收单，202609 核销 20000（settlement.period=202609, CONFIRMED, partyType=CUSTOMER, totalAmount>0）
When 查询 202609 余额
Then currentSettled 中计入 20000
And 该行期初 openingUnsettled 相应减少 20000
```

### 场景 4：反核销不污染视图

```gherkin
Given 原核销单状态为 REVERSED，且存在对应 settlementNo 以 "-H" 结尾、totalAmount 为负的红字对冲单（状态可达 CONFIRMED）
When 查询余额
Then 原核销单不计入 currentSettled（状态 REVERSED 排除）
And 红字对冲单不计入 currentSettled（totalAmount <= 0 排除，防双计）
```

### 场景 5：期间必填守卫

```gherkin
Given 未传 period 参数，或传入非 6 位数字格式（如 "2026-09"）
When 调用 GET /report/balances
Then 返回 BusinessException 400，提示期间必填（YYYYMM）
```

### 场景 6：数据权限隔离

```gherkin
Given 企业 A 的数据
When 企业 B 用户查询 balances
Then 结果不包含企业 A 任何单据/核销记录（拦截器注入 enterprise_id）
```

---

## 5. 影响范围

| 维度 | 影响 |
|------|------|
| 数据库 | 无 schema 变更（纯聚合查询，不建新表） |
| 后端 | +1 Controller（ArapBalanceReportController）+ VO + Service SQL 聚合 |
| 前端 | +1 视图（BalanceSummaryView.vue）+ 路由注册 |
| 测试 | +6~10 测试（上表 6 场景 + 负向） |
| API | 新增 1 端点（510+ → 511+） |

---

## 6. 遗留事项

- **与总账对账报告**（P1 后续）：业务余额 vs 1122/2202 科目期未余额 + 差异明细定位（对标金蝶 8 项差异分析 / 用友与总账对账 / QB aging=资产负债表演示）
- **账龄区间配置化**（P1 后续）：`aging_bucket` 配置表替代 P51 硬编码区间
- **期初余额导入**（竞品标配）：存量企业上线门槛，另立项
- **应收应付对冲**（金蝶/用友标配）：同一客商既是客户又是供应商场景，另立项