# P76 SPEC — 费用汇总报表（部门/费用类型/员工 × 期间区间）

> **版本**：V1.1 | **最后修改**：2026-09-18 | **作者**：Hermes
> **状态**：✅ 已实现（P1 批次交付，测试通过，commit a284c09）
> **编号**：HUICAI-SPC-076 | 优先级：P1
> **依据**：竞品差距核查（DSN-竞品差距与管理类报表核查.md G-1）+ DSN-费用报销管理.md §8
> **目标**：提供按部门/费用类型/员工 × 期间区间的报销费用聚合视图（金额/单据数/人均/同比/环比 + Excel 导出），补齐"按什么维度汇总花了多少钱"的管理报表
> **工期**：2 天（后端聚合 + 前端视图）

> **关联需求**：REQ-2026-086（费用汇总报表）
> **依赖 SPEC**：P11（报销状态机）

## 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| V1.0 | 2026-09-17 | 初版。基于代码实证（ExpenseReimbursementEntity：deptId/expenseType/amount/status/approvedAt）设计费用汇总端点 |

---

## 0. 背景与问题

现状盘点（代码实证）：

1. **数据载体已就绪**：`t_expense_reimbursement`（`ExpenseReimbursementEntity`：`reimbNo/applicantId/employeeId/deptId/expenseType/amount/status/approvedAt/createdAt`），报销→审批→凭证全流程已上线（P11）。
2. **缺口**：端点只有 `/page`、`/list`、`/{id}` + 状态流转，**无任何聚合端点**。无法回答"研发部 1-6 月差旅费花了多少、比上月多了还是少了"。
3. **竞品基线**：金蝶云报销多维度费用报表、易代账费用统计，均标配"维度 × 期间"聚合 + 同比环比。

**业务影响**：费用管控无数据底稿，预算执行分析（P16）缺费用侧明细支撑。

---

## 1. 竞品对标

| 竞品 | 费用报表 | 关键设计 | 结论 |
|------|---------|---------|------|
| **金蝶云星辰（云报销）** | 多维度费用报表 | 按部门/费用类型/人员 × 期间，同比环比，图形化展示 | ✅ 本 SPEC 直接对标维度结构 |
| **易代账** | 费用统计 | 代账场景按客户 × 费用类型汇总 | ✅ 维度可切换 |
| **QuickBooks** | P&L by category | 费用类别 × 期间 + 对比列 | ✅ 对比列标配 |

**结论**：三家一致"维度 × 期间区间 + 对比列"。慧财 `expenseType` 为字符串字段（无独立字典表），本期按字符串值分组，字典化另立项。

---

## 2. 改动清单总览

| # | 优先级 | 改动 | 文件 | 风险 | 状态 |
|---|--------|------|------|------|------|
| 1 | P0 | 费用汇总端点 `GET /api/sme/arap/v1/expense-reimbursements/summary`（期间区间 + group_by + 同比环比） | `ExpenseReimbursementController` + `ExpenseSummaryVO` + Service 聚合（仿 `ArapBalanceReportServiceImpl` LambdaQueryWrapper + Java 分组） | ✅ 低（纯聚合，无 schema 变更） | 📝 待开发 |
| 2 | P0 | 导出端点 `GET .../summary/export`（hutool ExcelUtil，对齐既有报表中心 `ReportServiceImpl`） | 同上 | ✅ 低 | 📝 待开发 |
| 3 | P0 | 前端费用汇总视图（维度切换 + 期间选择 + 表格 + 导出按钮） | `frontend/src/views/arap/expense/ExpenseSummaryView.vue` | ✅ 低 | 📝 待开发 |
| 4 | P2 | 费用类型字典表（替代 expenseType 字符串） | 后续迭代 | ⚠️ 中 | 后续 |

---

## 3. 四段模板（输入/输出/状态/异常）

### 3.1 输入契约

- **触发条件**：`GET /api/sme/arap/v1/expense-reimbursements/summary`
- **必填参数**：`periodFrom`、`periodTo`（YYYYMM 6 位数字区间，如 `202601`~`202606`；`periodFrom ≤ periodTo`，camelCase 对齐 P75 `@RequestParam` 约定）
- **可选参数**：
  - `groupBy`：`DEPT`（默认）/ `EXPENSE_TYPE` / `EMPLOYEE`
  - `includeYoy`（默认 true）：同比列（去年同期同区间）
  - `includeMom`（默认 true）：环比列（上一等长区间）
- **前置条件**：数据权限拦截器自动注入 enterprise_id（铁律#6）

### 3.2 输出契约

```jsonc
{
  "periodFrom": "202601", "periodTo": "202606", "groupBy": "DEPT",
  "rows": [
    { "dimId": 1, "dimName": "研发部", "count": 42, "amount": 152000.00,
      "perCapita": 15200.00, "amountYoy": 138000.00, "amountMom": 28000.00 }
  ],
  "totalCount": 180, "totalAmount": 620000.00
}
```

> `perCapita` 仅 DEPT 维度输出，其余维度为 null；`amountYoy/amountMom` 缺数据或未启用时 null。合计列为顶层扁平 `totalCount`/`totalAmount`（对齐 `ExpenseSummaryVO` record，非嵌套 `total` 对象）。

**口径定义（对齐代码实证）**：

- **数据源**：`t_expense_reimbursement`，`status ∈ (APPROVED, VOUCHERED)`（已生效单据；DRAFT/SUBMITTED/REJECTED 不计入）。
- **期间归属**：按 `approvedAt` 所在期间（审批通过=费用生效期，与凭证归属一致）；无 `approvedAt` 的已生效单归 `createdAt` 期间。
- **维度**：
  - `DEPT`：按 `deptId` 分组（名称取 `t_dept`）
  - `EXPENSE_TYPE`：按 `expenseType` 字符串值分组（本期不建字典表）
  - `EMPLOYEE`：按 `employeeId` 分组（名称取 `t_employee`）
- **指标**：`count`（单据数）、`amount`（金额合计，BigDecimal）、`perCapita`（仅 DEPT 维度：amount / 期末在职人数，`t_employee` 按 `deptId` 且在职状态计数；其余维度不输出 perCapita，返回 null）
- **同比**：去年同期同区间（`periodFrom-12m` ~ `periodTo-12m`）同维度金额合计；无上年数据返回 null（前端 "—"）
- **环比**：上一等长区间（`periodFrom - 等长` ~ `periodTo - 1m`）；同环比缺数据返回 null
- **导出**：hutool `ExcelUtil`（对齐既有报表中心 `ReportServiceImpl.writeExcel` 的 `writeCellValue` 模式，非 EasyExcel），列 = 维度 + 单数 + 金额 + 人均 + 同比 + 环比 + 合计行；null 单元格写空串。

### 3.3 状态流转（数据口径，非状态机）

```
t_expense_reimbursement（status）
  APPROVED / VOUCHERED → 计入汇总（按 approvedAt 期间归属）
  DRAFT / SUBMITTED / REJECTED → 排除
```

### 3.4 异常处理

| 场景 | 处理 | 错误码 |
|------|------|--------|
| `periodFrom`/`periodTo` 缺失或格式非法（非 6 位数字） | `BusinessException`（400） | P76_001 |
| `periodFrom > periodTo` | `BusinessException`（400） | P76_002 |
| `groupBy` 非法（非 DEPT/EXPENSE_TYPE/EMPLOYEE） | `BusinessException`（400） | P76_003 |
| 区间内无有效单据 | 返回空 rows + totalCount/totalAmount 全 0，不报错 | — |
| 同比/环比区间无数据 | 对应列返回 null，不影响主列 | — |
| 数据隔离 | 拦截器注入 enterprise_id，越权查空 | — |

**事务**：纯聚合只读查询，无写操作，不加 `@Transactional`。

---

## 4. BDD 验收场景

### 场景 1：部门维度聚合正确（L2 集成，Testcontainers PG16）

```gherkin
Given 202601-202606 研发部 3 张 APPROVED/VOUCHERED 报销单（金额 100/200/300），市场部 2 张（500/600）
When 查询 summary?periodFrom=202601&periodTo=202606&groupBy=DEPT
Then 研发部行 count=3, amount=600.00；市场部行 count=2, amount=1100.00
And totalAmount == 1700.00
```

### 场景 2：已生效状态过滤（L2 集成，🟡 数据口径）

```gherkin
Given 一张 DRAFT、一张 SUBMITTED、一张 REJECTED、一张 APPROVED 报销单
When 查询汇总
Then 仅 APPROVED 计入（负向断言：DRAFT/SUBMITTED/REJECTED 金额不出现在任何行）
```

### 场景 3：同比无上年数据返回 null（L1，Service 纯逻辑）

```gherkin
Given 区间 202601-202606，去年同期区间无任何报销单
When include_yoy=true 查询
Then 各行 amountYoy == null（前端显示 "—"），amountMom 正常计算
```

### 场景 4：期间参数守卫（L2 Controller）

```gherkin
Given period_from=202606&period_to=202601（from > to），或 group_by=FOO
When 调用 summary
Then 分别返回 400 P76_002 / P76_003
```

### 场景 5：人均仅 DEPT 维度输出（L1）

```gherkin
Given group_by=DEPT，研发部期末在职 10 人、金额合计 152000
When 查询
Then perCapita = 15200.00
And group_by=EXPENSE_TYPE 时 perCapita 字段为 null
```

### 场景 6：数据权限隔离（L2 集成，🟡 跨租户）

```gherkin
Given 企业 A 的报销单
When 企业 B 用户查询 summary
Then 结果不含企业 A 单据（拦截器注入 enterprise_id）
```

---

## 5. 影响范围

| 维度 | 影响 |
|------|------|
| 数据库 | 无 schema 变更（纯聚合查询） |
| 后端 | `ExpenseReimbursementController` +2 端点 + VO + Service 聚合 |
| 前端 | +1 视图（ExpenseSummaryView.vue）+ 路由注册 |
| 测试 | +6 测试（6 场景） |
| API | 新增 2 端点 |

---

## 6. 遗留事项

- **费用类型字典表**（P2 后续）：`expenseType` 字符串值分组 → 字典化后支持字典名展示与停用管理
- **费用预算联动**（远期）：汇总 vs 预算（P16）执行率列，另立项

---

```yaml
# === MACHINE-READABLE CONTRACT ===
contract_version: "1.0"
entity: ExpenseReimbursementEntity
module: sme-arap
table: t_expense_reimbursement

states:
  APPROVED:
    description: "审批通过（计入汇总，按 approvedAt 期间归属）"
    initial: false
    terminal: false
  VOUCHERED:
    description: "已生成凭证（计入汇总）"
    initial: false
    terminal: false
  DRAFT:
    description: "草稿（排除）"
    initial: true
    terminal: false
  SUBMITTED:
    description: "已提交待审批（排除）"
    initial: false
    terminal: false
  REJECTED:
    description: "已驳回（排除）"
    initial: false
    terminal: false

transitions:
  - id: T-01
    from: SUBMITTED
    to: APPROVED
    trigger: approve
    precondition: "status == SUBMITTED"
    postcondition: "status = APPROVED; approvedAt 落库（汇总期间归属依据）"
    side_effects: []
    test_ref: test_approved_counts_into_summary
  - id: T-02
    from: APPROVED
    to: VOUCHERED
    trigger: generateVoucher / autoVoucher
    precondition: "status == APPROVED"
    postcondition: "status = VOUCHERED; 仍计入汇总（口径 status ∈ (APPROVED, VOUCHERED)）"
    side_effects:
      - entity: VoucherEntity
        action: create
        status: DRAFT
    test_ref: test_vouchered_still_counted

constraints:
  - id: C-01
    type: business
    rule: "汇总仅统计 status in (APPROVED, VOUCHERED)，期间按 approvedAt（无则 createdAt）所在 YYYYMM"
    enforcement: "Service LambdaQueryWrapper status in + Java 期间分组"
  - id: C-02
    type: business
    rule: "全部金额 BigDecimal NUMERIC(18,2)；同比/环比缺数据返回 null 而非 0"
    enforcement: "Service 显式 null 语义"
  - id: C-03
    type: business
    rule: "perCapita 仅 DEPT 维度输出（分母=期末在职人数）"
    enforcement: "Service 按 groupBy 分支"

acceptance_tests:
  - id: AT-001
    description: "部门维度聚合正确"
    method: test_dept_grouping
    assertion: "研发部 count=3 amount=600.00，total.amount=1700.00"
    status: missing
  - id: AT-002
    description: "仅生效状态计入"
    method: test_only_approved_vouchered_counted
    assertion: "DRAFT/SUBMITTED/REJECTED 金额不出现"
    status: missing
  - id: AT-003
    description: "同比缺数据返回 null"
    method: test_yoy_null_when_no_data
    assertion: "amountYoy == null 且 amountMom 正常"
    status: missing
  - id: AT-004
    description: "期间参数守卫"
    method: test_period_params_guard
    assertion: "from>to 返回 400 P76_002；group_by 非法返回 400 P76_003"
    status: missing
  - id: AT-005
    description: "人均仅 DEPT 输出"
    method: test_per_capita_dept_only
    assertion: "DEPT 维度 perCapita=15200.00；EXPENSE_TYPE 维度 perCapita=null"
    status: missing
  - id: AT-006
    description: "数据权限隔离"
    method: test_tenant_isolation
    assertion: "跨企业查询返回空（enterprise_id 拦截）"
    status: missing

out_of_scope:
  - "费用类型字典表（后续迭代）"
  - "费用预算执行率联动（远期）"
  - "图形化趋势图（报表中心已有趋势框架，另立项）"
  - "schema 变更（无）"

dependencies:
  - spec: P11
    relation: "报销状态机与 approvedAt 字段来源（本 SPEC 读侧依赖）"
```

> **文档结束**。关联：[P11-员工费用报销](../design/DSN-费用报销管理.md) §8 | [DSN-费用报销管理](../design/DSN-费用报销管理.md)
