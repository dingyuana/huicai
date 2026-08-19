# 总账与结账 PRD

> **编号**：HUICAI-PRD-003
> **版本**：V1.0 | **日期**：2026-08-19
> **关联总 PRD：`(../CORE-需求分析.md)` 
> **关联设计**：DSN-总账管理.md
> **关联SPEC**：S-17-期末自动化结转.md、S-18-结账控制与反结账.md
> **对应包**：com.huicai.base.voucher（SubjectBalanceService / PeriodCloseService / LedgerService）

---

## 1. 模块定位

管理科目余额的实时更新、期初余额录入、期末结账/反结账、账簿查询。

**做什么**：科目余额计算、期初余额管理、期末结账流程、总账/明细账/辅助核算账查询。

**不做什么**：
- 不做凭证 CRUD 和状态机（属于凭证管理模块，V-001~V-015）
- 不做凭证模板（属于凭证管理模块）
- 不做三大报表生成（属于报表中心模块，R-008）

---

## 2. 功能清单

| 编号 | 功能点 | 优先级 | 状态 | 验收标准 |
|------|--------|--------|------|---------|
| B-001 | 期初余额录入 | P0 | ✅ 已完成 | 首期间可录入；借贷平衡校验；余额方向（借/贷）选择 |
| B-002 | 期初余额审核 | P0 | ✅ 已完成 | 审核通过后才可过账；审核前允许修改 |
| B-003 | 科目余额实时更新 | P0 | ✅ 已完成 | 凭证过账时自动更新 begin/occur/end_debit/credit |
| B-004 | 科目余额查询 | P0 | ✅ 已完成 | 按期间/科目树/辅助核算维度查询 |
| B-005 | 科目余额快照 | P0 | ✅ 已完成 | 期末结账时固化余额快照，防止追溯修改 |
| B-006 | 期末结账 | P0 | ✅ 已完成 | 校验：上期已结、本期所有凭证已审核、损益结转完成 |
| B-007 | 反结账 | P0 | ✅ 已完成 | 需反结账权限；解锁期间；可重新过账 |
| B-008 | 损益结转 | P0 | ✅ 已完成 | 损益类科目余额自动转入本年利润（1001） |
| B-009 | 总账查询 | P0 | ✅ 已完成 | 按科目+期间查询，显示期初/本月借方/贷方/期末 |
| B-010 | 明细账查询 | P0 | ✅ 已完成 | 按科目+期间+日期查询，逐笔显示 |
| B-011 | 辅助核算账查询 | P1 | ✅ 已完成 | 按客户/供应商/部门/项目维度查询 |
| B-012 | 科目余额表 | P0 | ✅ 已完成 | 展示全部科目余额汇总（借方/贷方/余额方向） |

---

## 3. 数据模型

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| t_subject_balance | 科目余额 | subject_id, period, begin_debit/credit, occur_debit/credit, end_debit/credit |
| t_opening_balance | 期初余额 | subject_id, period, begin_debit/credit, status |
| t_account_closure | 期间结账记录 | period, closed_by, closed_at, reverse_by, reverse_at |
| t_profit_transfer | 损益结转凭证 | period, transfer_voucher_id |

---

## 4. 核心业务规则

### 4.1 期初余额规则

| 规则 | 说明 |
|------|------|
| 首期间 | 只允许录入首个期间 |
| 借贷平衡 | 借方合计 = 贷方合计，否则拒绝审核 |
| 审核前置 | 未审核前可自由修改；审核后锁定，期间不可修改 |
| 余额方向 | 借方余额=debit-credit（正数）；贷方余额=credit-debit（正数） |
| 累计借贷 | 非首期间自动从上期结转，不重复录入 |

### 4.2 期末结账规则（顺序强制）

结账前必须满足全部前置条件，否则拒绝：

| 检查项 | 说明 |
|--------|------|
| 上期已结账 | 本期前一期间必须已关闭 |
| 本期全部凭证已审核 | 无 AUDITED 以下状态的凭证 |
| 损益结转已完成 | 损益类科目已转入本年利润（1001） |
| 期初余额已录入 | 首期间需先录期初 |
| 期间未过期 | 结账时期间不能是"已过期" |
| 结账锁定 | 结账后所有凭证自动转为 CLOSED，禁止修改 |

### 4.3 反结账规则

| 规则 | 说明 |
|------|------|
| 权限 | 仅具有"反结账"权限的用户可操作 |
| 追溯性 | 反结账后，期间内凭证恢复为 POSTED，允许修改/反过账 |
| 审计 | 反结账记录 `reverse_by` / `reverse_at` 到 `t_account_closure` |

### 4.4 损益结转规则

| 规则 | 说明 |
|------|------|
| 触发 | 期末结账时自动执行 |
| 范围 | 损益类科目（收入/成本/费用） |
| 目标 | 本年利润科目（1001） |
| 方向 | 收入类→本年利润借方；费用类→本年利润贷方 |

### 4.5 账簿查询规则

| 账簿 | 查询维度 | 说明 |
|------|---------|------|
| 总账 | 科目 + 期间 | 汇总显示期初/本期借方/本期贷方/期末 |
| 明细账 | 科目 + 期间 + 日期范围 | 逐笔显示，按凭证号排列 |
| 辅助核算账 | 核算维度 + 期间 | 按客户/供应商/部门/项目 |
| 余额表 | 科目树 | 全部科目余额汇总，可筛选零余额 |

---

## 5. 验收标准

| ID | BDD 场景 | 对应测试 |
|----|---------|---------|
| AT-01 | Given 首期间 When 期初余额借贷不平衡 Then 拒绝审核 | opening_balance_check |
| AT-02 | Given 首期间期初已审核 When 过账凭证 Then 科目余额正确更新 | balance_update_on_post |
| AT-03 | Given 期末 When 结账(损益未结转) Then 拒绝 | close_profitNotTransferred_throws |
| AT-04 | Given 期末 When 结账(有未审核凭证) Then 拒绝 | close_pendingVoucher_throws |
| AT-05 | Given 上期未结 When 结账本期 Then 拒绝 | close_previousNotClosed_throws |
| AT-06 | Given 期末 When 结账(全部满足) Then 成功 + 凭证转CLOSED + 损益结转 | close_positive |
| AT-07 | Given 已结账期间 When 反结账 Then 成功 + 凭证恢复POSTED | close_reverse_positive |
| AT-08 | Given 已结账期间 When 修改凭证 Then 抛异常 | closed_immutable |
| AT-09 | Given 多期间 When 查询明细账 Then 返回指定期间内的全部分录 | ledger_detail_query |
| AT-10 | Given 有辅助核算 When 查询辅助核算账 Then 按客户/供应商维度汇总 | auxiliary_account_query |

---

## 6. 不做的事

| 不做 | 理由 |
|------|------|
| 合并报表 | 集团场景，非当前需求 |
| 多币种余额 | 单币种 |
| 外币折算 | 当前需求不含外币业务 |
| 现金流量表编制 | 属于报表中心模块 |
| 预算执行对比 | 属于预算管理模块 |

---

## 7. API 清单

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/opening-balances` | 期初余额录入 |
| POST | `/api/v1/opening-balances/audit` | 期初余额审核 |
| GET | `/api/v1/subject-balances/page` | 科目余额查询 |
| GET | `/api/v1/subject-balances/by-subject/{id}` | 指定科目余额 |
| POST | `/api/v1/periods/close` | 期末结账 |
| POST | `/api/v1/periods/unlock` | 反结账 |
| GET | `/api/v1/ledger/summary` | 总账 |
| GET | `/api/v1/ledger/detail` | 明细账 |
| GET | `/api/v1/ledger/auxiliary` | 辅助核算账 |
| GET | `/api/v1/subject-balances/report` | 科目余额表 |

---

> **文档结束。** 下篇：`报表中心-PRD-V1.0.md`（三大报表 + 杜邦分析 + 趋势分析）