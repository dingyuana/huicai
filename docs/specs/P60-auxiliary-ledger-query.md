# P60 SPEC — 辅助核算账查询

> **编号**：HUICAI-SPC-060
> **test_ref**：LedgerServiceImplTest / LedgerChainRealDBTest
> **版本**：V1.1 | **日期**：2026-08-31
> **状态**：✅ 已实现（2026-09-01 验证：5 个 BDD 场景 + 3 个 Mapper RealDB 场景全部通过）
> **关联需求**：REQ-2026-079（账簿查询增强）
> **关联 PRD**：[总账结账-PRD-V1.0](../prd/总账结账-PRD-V1.0.md)（B-011 辅助核算账查询、AT-10）
> **评估依据**：[账簿查询功能评估报告](../development/audit/2026-08-31-账簿查询功能评估报告.md)（缺陷 D1）

## 1. 输入契约

**API**：`GET /api/base/voucher/v1/ledgers/auxiliary`

| 参数 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `dimensionType` | String | 是 | 枚举：`customer` / `vendor` / `department` / `project` / `employee`（与科目 `aux_calc_type` 取值一致） |
| `period` | String | 是 | `YYYYMM`，6 位会计期间 |
| `dimensionValue` | Long | 否 | 具体核算维度 ID；为空则返回该维度类型下全部核算项（按维度值分组汇总） |

**前置条件**：
- 无（查询只读，不要求期间已结账）

**权限要求**：
- 需登录；受 `EnterpriseDataPermissionInterceptor` 组织级数据隔离约束（enterprise_id 自动注入）

**数据来源约定**：
- 维度字段与 assist_json 键映射（沿用 `VoucherServiceImpl.validateAssistJson` 既有约定，禁止另起键名）：

| auxCalcType（科目配置） | assist_json 键 |
|------------------------|---------------|
| customer | customerId |
| vendor | vendorId |
| department | deptId |
| project | projectId |
| employee | employeeId |

## 2. 输出契约

**成功响应**：`R<List<AuxiliaryLedgerRowVO>>`

| 字段 | 类型 | 说明 |
|------|------|------|
| `dimensionType` | String | 核算维度类型 |
| `dimensionValue` | Long | 核算维度 ID（dimensionValue 为空时按此分组） |
| `dimensionName` | String | 核算维度名称（解析自主数据；解析失败置 null） |
| `subjectId` | Long | 科目 ID |
| `subjectCode` | String | 科目编码 |
| `subjectName` | String | 科目名称 |
| `direction` | String | 科目方向 debit/credit |
| `beginBalance` | BigDecimal | 期初余额（按维度聚合推算，见 §3 设计决策） |
| `debitTotal` | BigDecimal | 本期借方发生额 |
| `creditTotal` | BigDecimal | 本期贷方发生额 |
| `endBalance` | BigDecimal | 期末余额 |

**失败响应**：
- `400 BAD_REQUEST`：`业务错误` — dimensionType 非法 / period 格式错误
- `401 UNAUTHORIZED`：未登录
- 查询错误不写任何数据（只读操作）

## 3. 状态流转

本功能为**纯查询**，不改变任何业务实体状态，无状态机。

**副作用声明（Side Effects）**：
- 无 DB 写操作、无缓存写、无 MQ 消息。
- 仅触发 `SELECT`：t_voucher_entry（JOIN t_voucher 过滤 period+deleted）、t_subject、维度主数据表。

**关键设计决策**：

| 决策项 | 结论 | 理由 |
|--------|------|------|
| 期初余额口径 | 从**历史各期维度分录聚合**推算（本期以前该科目该维度的 借贷差 累计），而非从 t_subject_balance 快照 | t_subject_balance 快照只按 subject_id+period，**不拆分辅助维度**；而真实库 assist_json 历史上无维度数据，无法建维度期初快照。聚合推算保证「期初 + 本期发生 = 期末」恒等式 |
| 期末余额口径 | `beginBalance + debitTotal - creditTotal`（debit 科目）/ `beginBalance + creditTotal - debitTotal`（credit 科目） | 与科目方向一致 |
| 维度名称解析 | 查主数据（customer→CustomerEntity、vendor→VendorEntity、department→DeptEntity、employee→EmployeeEntity），批量 `selectByIds`；**project 维度当前无 Project 实体/表，dimensionName 置 null**（仅支持按维度值查询/分组，名称待 P3 项目核算落地后补） | 避免 N+1；项目核算尚未落地 |
| 未过账凭证 | **包含**（沿用现有 `selectBySubjectIdAndPeriod` 行为，本 SPEC 不改变该策略；P1-T8 统一处理） | 与既有账簿查询一致 |
| 聚合层级 | SQL 层 `assist_json ->> dimensionField = ?` 过滤 + 按 subject_id 分组求和 | JSONB 算子，避免 Java 内存聚合 |

## 4. 异常处理

| 异常码 | HTTP | 日志 | 提示文案 |
|--------|------|------|---------|
| `COMMON_BAD_REQUEST`（dimensionType 非法） | 400 | WARN | "不支持的辅助核算维度类型: {type}" |
| `COMMON_BAD_REQUEST`（period 格式错误） | 400 | WARN | "会计期间格式错误, 应为 YYYYMM" |

**事务**：只读查询，无事务回滚条件。

---

## 验收标准（BDD）

### 场景 1：按客户维度 + 指定客户查询
- **Given** 科目"应收账款"(aux_calc_type=customer) 有 2 条本期分录 assist_json 含 customerId=1001，借方 500/200，科目为 debit 方向
- **When** 调用 `auxiliaryLedger("customer", "202608", 1001L)`
- **Then** 返回 1 行，subjectId 匹配、debitTotal=700、creditTotal=0、direction=debit
- **And** 该科目下 customerId=2001 的分录不出现（维度过滤生效）

### 场景 2：维度类型合法但该维度无数据
- **Given** 期间无任何含 customerId=9999 的分录
- **When** 调用 `auxiliaryLedger("customer", "202608", 9999L)`
- **Then** 返回空列表（不抛异常）

### 场景 3：dimensionValue 为空时按维度值分组
- **Given** 有 customerId=1001(借方500) 和 customerId=2001(借方300) 分录
- **When** 调用 `auxiliaryLedger("customer", "202608", null)`
- **Then** 返回 2 行，分别对应 1001 和 2001，各自 debitTotal 正确

### 场景 4：非法维度类型
- **Given** dimensionType="unknown"
- **When** 调用 `auxiliaryLedger("unknown", "202608", null)`
- **Then** 抛出 BusinessException(badRequest)，message 含"不支持的辅助核算维度类型"

### 场景 5：期初+发生=期末恒等式（debit 科目）
- **Given** 历史期该科目 customerId=1001 累计贷方 300（期初推算 300），本期借方 700
- **When** 调用 `auxiliaryLedger("customer", "202608", 1001L)`
- **Then** beginBalance=300、debitTotal=700、endBalance=1000（300+700-0）

---

## 版本历史

- V1.1 (2026-09-01): 实现完成，5 个 BDD 验收 + 3 个 Mapper RealDB 场景全部通过
- V1.0 (2026-08-31): 初始版本，覆盖 D1（辅助核算账未实现）

---

<!-- === MACHINE-READABLE CONTRACT ===
contract_version: "1.0"
entity: VoucherEntryEntity
module: base.voucher
table: t_voucher_entry
states: {}                     # 纯查询，无状态机
transitions: []
constraints:
  - id: C-01
    type: database
    rule: "dimension field key follows validateAssistJson convention (customerId/vendorId/deptId/projectId/employeeId)"
  - id: C-02
    type: business
    rule: "begin + debit - credit = end (debit subject); begin + credit - debit = end (credit subject)"
  - id: C-03
    type: database
    rule: "all queries must filter voucher.deleted=0 and entry.deleted=0 and period=#{period}"
acceptance_tests:
  - id: AT-001
    description: "按客户维度+指定客户查询，维度过滤生效"
    method: auxiliaryLedger_byCustomer_dimensionFiltered
    assertion: "returns 1 row, debitTotal=700, customerId=2001 excluded"
    status: implemented
  - id: AT-002
    description: "维度无数据返回空列表"
    method: auxiliaryLedger_noData_returnsEmpty
    assertion: "returns empty list without exception"
    status: implemented
  - id: AT-003
    description: "dimensionValue为空按维度值分组"
    method: auxiliaryLedger_groupByDimensionValue
    assertion: "returns 2 rows for customerId 1001 and 2001"
    status: implemented
  - id: AT-004
    description: "非法维度类型抛业务异常"
    method: auxiliaryLedger_invalidDimensionType_throws
    assertion: "BusinessException with message 不支持的辅助核算维度类型"
    status: implemented
  - id: AT-005
    description: "期初+发生=期末恒等式"
    method: auxiliaryLedger_balanceIdentity
    assertion: "beginBalance=300, debitTotal=700, endBalance=1000"
    status: implemented
out_of_scope:
  - "辅助核算维度期初快照表（本期不建表，期初聚合推算）"
  - "未过账凭证入账策略（P1-T8 统一处理）"
  - "多栏账/数量金额账（远期）"
dependencies:
  - spec: P4
    relation: "assist_json 维度字段键约定源自 VoucherServiceImpl.validateAssistJson"
  - spec: P33
    relation: "记账凭证→账簿链路复用"
-->
