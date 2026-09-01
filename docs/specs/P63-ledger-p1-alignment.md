# P63 SPEC — 账簿查询 P1 阶段（T4-T8 友商功能对齐）

> **编号**：HUICAI-SPC-063
> **test_ref**：LedgerServiceImplTest / LedgerControllerTest / VoucherEntryMapperRealDBTest / LedgerChainRealDBTest / SubjectBalanceServiceImplTest
> **版本**：V1.0 | **日期**：2026-09-01
> **状态**：📝 草案（2026-09-01 老丁已确认三决策点）
> **关联需求**：REQ-2026-079（账簿查询增强）
> **关联 PRD**：[总账结账-PRD-V1.0](../prd/总账结账-PRD-V1.0.md)（B-009/B-010/B-012）
> **评估依据**：[账簿查询功能评估报告](../development/audit/2026-08-31-账簿查询功能评估报告.md)（缺陷 D3 本年累计、D5 过滤维度、D6 联查穿透、D7 余额列，§5.2 未过账策略）
> **关联计划**：[账簿查询完善计划](../development/plans/2026-08-31-账簿查询完善计划.md)（T4-T8）

---

## 0. 已确认决策（老丁 2026-09-01）

| 决策 | 结论 |
|------|------|
| D-A（T8 默认值） | 账簿三表默认**只含 POSTED 凭证**，`includeUnposted=true` 可恢复包含未过账 |
| D-B（T7 期初口径） | 期初行取 `t_subject_balance.begin_balance` 快照 |
| D-C（前端联动） | 本阶段**只做后端 + 测试**，前端 LedgerView 后续接入（行结构兼容，加字段不破坏现有列） |

执行顺序：T7 → T4 → T6 → T5 → T8（依赖驱动：T7 依赖 T2 SQL 已就绪；T8 涉及默认行为变更放最后）。

---

## T7 明细账余额列（缺陷 D7）

### 1. 输入契约

**API**：`GET /api/base/voucher/v1/ledgers/subsidiary`（参数不变：subjectId/period/startDate/endDate）

### 2. 输出契约

**成功响应**：`R<List<Map<String, Object>>>`，行结构在既有字段上**新增**：

| 新增字段 | 类型 | 说明 |
|----------|------|------|
| `type` | String | `OPENING`-期初行 / `ENTRY`-分录行 |
| `voucherNo` | String | 凭证号（JOIN t_voucher） |
| `voucherDate` | LocalDate | 凭证日期（`DATE(v.created_at)` 代理） |
| `running` | BigDecimal | 滚动余额（期初开始逐笔累计，按科目方向） |

**行为**：
- 返回行 = **期初行（1 行，type=OPENING, running=begin_balance）** + 分录行（每行携带 voucherNo/voucherDate/running）
- 期初行摘要「期初余额」；科目不存在返回空列表（现行为，不分页）
- 滚动逻辑：debit 科目 `running += debit - credit`；credit 科目 `running += credit - debit`

### 3. 状态流转 / 4. 异常处理

纯查询；异常处理与 P62 一致（period 格式非法 → 400）。

### 验收（BDD）

- **场景 1**：借方科目 1001 期初 1000 + 分录借 500（voucherNo=V-001）→ 期初行 running=1000，分录行 running=1500、voucherNo=V-001、voucherDate 非空
- **场景 2**：科目无余额快照 → 期初行 running=0，仍输出期初行 + 分录行
- **场景 3**（RealDB）：过账后查明细账 → obtener 期初行 + 分录行滚动余额与总账一致

---

## T4 本年累计列（缺陷 D3）

### 1. 输入契约

- **余额表**：`GET /ledgers/subject-balance?period=YYYYMM`（参数不变）
- **总账**：`GET /ledgers/general?subjectId&period`（参数不变）

### 2. 输出契约

**余额表**每行新增：

| 新增字段 | 类型 | 说明 |
|----------|------|------|
| `yearBeginBalance` | BigDecimal | 年初余额：该科目本年度**最早期间**快照的 begin_balance |
| `yearDebitTotal` | BigDecimal | 本年累计借方：本年各期（period ≤ 当前期）credit/debit_total 汇总 |
| `yearCreditTotal` | BigDecimal | 本年累计贷方（同上口径） |

**总账**在「本期合计」(CLOSING) 行后新增「本年累计」(YEAR_TOTAL) 行：summary=本年累计、debit=yearDebitTotal、credit=yearCreditTotal。

**口径**：`period LIKE '<year>%' AND period <= #{period}`（按字符串比较，YYYYMM 前缀天然有序）。

### 验收（BDD）

- **场景 1**：给定 202601-202607 快照（1 月期初 100），查余额表 202607 → yearBeginBalance=100、yearDebitTotal=1-7 月 debit 合计
- **场景 2**：总账 CLOSING 后出现 YEAR_TOTAL 行，数值 = 本年累计
- **场景 3**：年初无快照（1 月未建账）→ yearBeginBalance=0（向前追溯循环，与 getPreviousEndBalance 同逻辑）

---

## T6 账证表一体化联查（缺陷 D6）

### 1. 输入契约

**API**：总账/明细账端点参数不变。

### 2. 输出契约

- 总账 ENTRY 行、明细账 ENTRY 行 **新增 `voucherNo` + `voucherDate`**（JOIN t_voucher 一次取回，避免逐行查询）
- 余额表行新增 `subjectId`（已有）+ subjectCode（已有）→ 前端可凭此触发明细账查询（`/subsidiary?subjectId&period`）

**数据来源**：SQL 层 JOIN `t_voucher` 取 `v.voucher_no`、`DATE(v.created_at)`，一次查询无 N+1。

后端只保证字段输出；前端跳转凭证详情/联查明细账为后续前端任务（决策 D-C）。

### 验收（BDD）

- **场景 1**：总账 ENTRY 行 voucherNo/voucherDate 正确（与凭证一致）
- **场景 2**：明细账 ENTRY 行 voucherNo/voucherDate 正确
- **场景 3**：凭证被删除（deleted=1）→ JOIN 已排除，行不出现

---

## T5 余额表过滤选项（缺陷 D5）

### 1. 输入契约

**API**：`GET /ledgers/subject-balance`，新增可选参数：

| 参数 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `includeZero` | Boolean | 否 | 默认 false：排除期末余额=0 的科目；true 时包含 |
| `includeNoMovement` | Boolean | 否 | 默认 false：排除本期无发生额（debit=0 且 credit=0）科目；true 时包含 |
| `subjectCodePrefix` | String | 否 | 科目编码前缀过滤（如 `1002` 只返回 1002 开头科目） |

### 2. 输出契约

行结构不变；过滤在 Service 层完成（读取已查询科目列表，条件过滤），科目仍只显示末级（既有逻辑）。

### 验收（BDD）

- **场景 1**：零余额科目，includeZero=false 排除 / =true 包含
- **场景 2**：无发生额科目（debit=0, credit=0），includeNoMovement=false 排除 / =true 包含
- **场景 3**：subjectCodePrefix=1002 → 只返回 1002 开头科目行

---

## T8 未过账凭证策略（评估报告 §5.2）

### 1. 输入契约

**API**：`GET /ledgers/subject-balance`、`/general`、`/subsidiary`，新增可选参数：

| 参数 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `includeUnposted` | Boolean | 否 | 默认 false：只含 POSTED 凭证；true：含 DRAFT/SUBMITTED/AUDITED/POSTED |

### 2. 输出契约

**默认行为变更**（决策 D-A）：账簿三表默认只返回已过账（POSTED）凭证的分录/余额影响。
- 余额表基于 t_subject_balance 快照（只在过账时写入），本就不含未过账 → 无需改 SQL，仅确认语义
- 总账/明细账基于分录 JOIN，需在 SQL 加 `v.status = 'POSTED'` 条件（includeUnposted=false 默认）
- `includeUnposted=true` → 移除 status 条件（等价现行为）

**兼容性**：现前端不传 includeUnposted → 默认只含 POSTED；当前库 58 张凭证全 DRAFT，账簿将查不到数据（符合会计语义：未过账不入账）。评估报告 §5.2 已提示这是友商默认，属预期行为修正。

### 验收（BDD）

- **场景 1**：DRAFT + POSTED 凭证各有分录，默认查总账 → 只返回 POSTED 分录
- **场景 2**：`includeUnposted=true` → 返回全部状态分录
- **场景 3**（RealDB）：POSTED 后过账 → 默认查询返回该分录；新 DRAFT 凭证 → 默认不返回

---

## 范围外（Out of Scope）

- 前端 LedgerView.vue 联查跳转/新列展示（决策 D-C，后续前端任务）
- T9 N+1 消除、T10 全量 VO 化（P2 阶段）
- 凭证日期业务列（t_voucher.voucher_date，P62 遗留）
- 跨年跨期查询（单期间口径保持）

## 依赖

- T7 ← P62 已就绪的 selectSubsidiaryByDates SQL 结构
- T4 ← t_subject_balance.year 字段（已确认存在）
- T6 ← 凭证详情端点已存在（`GET /vouchers/{id}`），本次仅输出 voucherNo/voucherDate
- T5/T8 ← Service 层过滤，无新 SQL 依赖

---

## 版本历史

- V1.0 (2026-09-01): 初始版本，覆盖 T4-T8；三决策点已确认

<!-- === MACHINE-READABLE CONTRACT ===
contract_version: "1.0"
module: base.voucher
table: t_voucher_entry / t_subject_balance
states: {}                     # 纯查询，无状态机
transitions: []
constraints:
  - id: C-01
    type: business
    rule: "ledger queries default to POSTED-only vouchers; includeUnposted=true includes all (D-A)"
  - id: C-02
    type: business
    rule: "subsidiary opening row uses t_subject_balance.begin_balance snapshot (D-B)"
  - id: C-03
    type: database
    rule: "year totals aggregate period LIKE '<year>%' AND period <= current"
  - id: C-04
    type: database
    rule: "all queries filter voucher.deleted=0, entry.deleted=0"
  - id: C-05
    type: business
    rule: "frontend changes out of scope (D-C); backend adds fields only"
acceptance_tests:
  - id: AT-001 (T7): subsidiary rows include OPENING row + running balance + voucherNo/Date
  - id: AT-002 (T4): subject-statement & general rows include yearBeginBalance + year totals
  - id: AT-003 (T6): general/subsidiary ENTRY rows include voucherNo + voucherDate
  - id: AT-004 (T5): subject-statement filters includeZero/includeNoMovement/subjectCodePrefix
  - id: AT-005 (T8): default POSTED-only; includeUnposted=true includes all
out_of_scope:
  - "前端 LedgerView 联动（D-C）"
  - "T9 N+1 / T10 VO 化（P2）"
  - "凭证日期业务列（P62 遗留）"
dependencies:
  - spec: P62
    relation: "T7 复用 selectSubsidiaryByDates SQL 结构"
-->