# P62 SPEC — 明细账日期范围 + 试算平衡空数据提示

> **编号**：HUICAI-SPC-062
> **test_ref**：LedgerServiceImplTest / LedgerControllerTest / VoucherEntryMapperRealDBTest / SubjectBalanceServiceImplTest
> **版本**：V1.0 | **日期**：2026-09-01
> **状态**：✅ 已实现（2026-09-01 验证：T2 单测+RealDB 7/7 通过；T3 单测 34 通过）
> **关联需求**：REQ-2026-079（账簿查询增强）
> **关联 PRD**：[总账结账-PRD-V1.0](../prd/总账结账-PRD-V1.0.md)（B-010 明细账 §4.5、验收 AT-09）
> **评估依据**：[账簿查询功能评估报告](../development/audit/2026-08-31-账簿查询功能评估报告.md)（缺陷 D2 明细账缺日期范围、D4 试算平衡空数据假阳性）
> **关联计划**：[账簿查询完善计划](../development/plans/2026-08-31-账簿查询完善计划.md)（T2 明细账日期范围、T3 试算平衡空数据提示）

---

## 一、明细账日期范围（T2，缺陷 D2）

### 1. 输入契约

**API**：`GET /api/base/voucher/v1/ledgers/subsidiary`

| 参数 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `subjectId` | Long | 是 | 科目 ID |
| `period` | String | 是 | `YYYYMM`，6 位会计期间 |
| `startDate` | LocalDate | 否 | `yyyy-MM-dd`，起始日期（含） |
| `endDate` | LocalDate | 否 | `yyyy-MM-dd`，结束日期（含） |

**前置条件**：无（查询只读）

**数据来源约定（关键设计决策）**：
- `t_voucher` **当前无凭证日期列**（仅有 `period` YYYYMM + `created_at`/`posted_at` 时间戳）
- 因此日期范围过滤以 `DATE(v.created_at)` 为日期代理，**不新增 DDL**
- 两个日期均为 null 时退化为按期间过滤（与 `selectBySubjectIdAndPeriod` 行为一致，保证旧调用兼容）
- 该决策已记入遗留事项：若后续引入「凭证日期」业务列（手工改期场景），需同步迁移此过滤字段

### 2. 输出契约

**成功响应**：`R<List<Map<String, Object>>>`（与现有明细账行结构一致，不破坏前端）

| 字段 | 类型 | 说明 |
|------|------|------|
| voucherId | Long | 凭证 ID |
| subjectId | Long | 科目 ID |
| subjectCode / subjectName | String | 科目编码/名称 |
| summary | String | 分录摘要 |
| debit / credit | BigDecimal | 借方/贷方发生额 |
| assistJson | String | 辅助核算 JSON 原文 |

**行排序**：`v.created_at ASC, e.id ASC`（按日期+分录序排列）

**失败响应**：`400 BAD_REQUEST`（现有行为不变）

### 3. 状态流转

纯查询，无状态变更，无副作用（仅 SELECT t_voucher_entry JOIN t_voucher）。

### 4. 异常处理

| 异常码 | HTTP | 说明 |
|--------|------|------|
| `COMMON_PARAM_ERROR` | 400 | startDate/endDate 格式非法（Spring `@DateTimeFormat` 自动拦截） |
| `COMMON_NOT_FOUND` | — | subjectId 不存在时返回空列表（现有行为） |

---

## 二、试算平衡空数据提示（T3，缺陷 D4）

### 1. 输入契约

**API**：`GET /api/base/voucher/v1/ledgers/trial-balance`（不变）

| 参数 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `period` | String | 是 | `YYYYMM` |

### 2. 输出契约

**成功响应**：`R<Map<String, Object>>`，在既有字段基础上新增：

| 字段 | 类型 | 说明 |
|------|------|------|
| `empty` | Boolean | **新增**：true=该期间无余额快照数据（未过账/未建账），false=有数据 |
| `emptyMessage` | String | **新增**（empty=true 时）："该期间无余额数据，可能是尚未过账或未建账，无法判断借贷平衡" |

**行为语义**：
- 无快照数据时：`empty=true` + 提示文案，其余汇总字段全零（保留既有结构，仅新增标记）
- 有数据时：`empty=false`，行为与之前完全一致（期间过滤已由 `queryByPeriod` 保证）

**设计动机**（修复 D4 假阳性）：原实现无快照时返回全零 + `balanced=true`，无法区分「真平衡」与「无数据」；前端拿不到数据时展示「平衡」属误导。新增 empty 标记区分两种情形，不改变 balanced 计算。

### 3. 状态流转

纯查询，无状态变更，无副作用。

---

## 验收标准（BDD）

### 场景 1：明细账按日期范围过滤（T2）
- **Given** 科目 1001 在期间 202608 有 2 条已过账分录：8/1（借方100）、8/20（借方200）
- **When** 调用 `subsidiaryLedger(1001, "202608", 2026-08-05, 2026-08-31)`
- **Then** 返回 1 行，即 8/20 的分录（借方 200），8/1 分录被日期范围排除

### 场景 2：日期范围为 null 退化为期间过滤（T2）
- **Given** 同上数据
- **When** 调用 `subsidiaryLedger(1001, "202608", null, null)`
- **Then** 返回 2 行全部期间分录

### 场景 3：试算平衡无快照返回 empty=true（T3）
- **Given** 期间 202608 无任何 t_subject_balance 快照行
- **When** 调用 `checkTrialBalance("202608")`
- **Then** 返回 `empty=true`，`emptyMessage` 含「无余额数据」，汇总字段全零

### 场景 4：试算平衡有数据时 empty=false（T3）
- **Given** 期间有 1 行余额快照（debit 科目 1001 期初 1000）
- **When** 调用 `checkTrialBalance("202608")`
- **Then** 返回 `empty=false`，汇总计算与既有行为一致

---

## 版本历史

- V1.0 (2026-09-01): 初始版本，覆盖 D2（明细账日期范围）+ D4（试算平衡空数据假阳性）

---

## 遗留事项

1. **凭证日期列**：`t_voucher` 无 `voucher_date` 业务列，日期范围暂以 `created_at` 为代理；若业务引入手工凭证日期，需新增列并迁移过滤逻辑（评估报告 §5.2）
2. **未过账凭证策略**：明细账默认包含未过账凭证（沿用现有行为），由计划 T8 统一处理
3. **前端联动**：LedgerView.vue 目前只传 subjectId+period，日期范围参数为可选新增，前端可后续接入，不破坏现有调用

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
    rule: "timestamp filter uses DATE(v.created_at) as date proxy; no DDL added"
  - id: C-02
    type: business
    rule: "startDate/endDate both null degenerates to period-only filter (backward compatible)"
  - id: C-03
    type: database
    rule: "all queries must filter voucher.deleted=0, entry.deleted=0, period=#{period}"
  - id: C-04
    type: business
    rule: "trial balance: empty=true when no snapshot rows; balanced computation unchanged"
acceptance_tests:
  - id: AT-001
    description: "明细账按日期范围过滤"
    method: selectSubsidiaryByDates_日期范围过滤生效
    assertion: "returns only entry within date range"
    status: implemented
  - id: AT-002
    description: "日期范围null退化为期间过滤"
    method: subsidiaryLedger_passesDateRangeToMapper
    assertion: "dates null -> period-only query"
    status: implemented
  - id: AT-003
    description: "试算平衡无快照返回empty=true"
    method: checkTrialBalance_emptySnapshot_returnsEmptyFlag
    assertion: "empty=true, emptyMessage contains 无余额数据"
    status: implemented
  - id: AT-004
    description: "有数据时empty=false"
    method: checkTrialBalance_aggregatesByDirection
    assertion: "empty flag=false with data present"
    status: implemented
out_of_scope:
  - "凭证日期业务列（t_voucher.voucher_date）—— 本期以 created_at 为代理"
  - "未过账凭证入账策略（P1-T8 统一处理）"
dependencies:
  - spec: P60
    relation: "同属 REQ-2026-079 账簿查询增强；T1 辅助核算账已实现"
-->