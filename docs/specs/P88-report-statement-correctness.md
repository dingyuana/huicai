# P88 SPEC — 报表三表信任级缺陷修复（资产表丢行 / 利润表累计口径 / 现金流不闭环）

> **版本**：V1.0 | **最后修改**：2026-09-22 | **作者**：Hermes
> **状态**：✅ 已实现（三缺陷修复，测试通过，commit d74c7c6）
> **编号**：HUICAI-SPC-088 | 优先级：P0（信任级——报表数据可信度）
> **依据**：四表截图综合评估（PRD-018 §1 前置事实）+ `ReportServiceImpl` 代码 trace
> **关联需求**：R-140（需求矩阵）
> **test_ref**：ReportServiceImplTest#p88_balanceSheet_lossYearProfitRow_appearsInEquity、#p88_incomeStatement_revenueIsGrossNotNet、#p88_cashFlowStatement_closingLoopAndCheck、#p88_cashFlowStatement_checkOkWhenConsistent

---

## 0. 背景与问题

用户用真实账套四表截图暴露"数据底层对、表层呈现失信"的三个信任级缺陷：

| # | 现象 | 根因（代码 trace） |
|---|------|------|
| ① | 资产负债表权益区缺"本年利润 -27"行，肉眼加总 ≠ 合计 212773.07，用户不信任"平衡✓" | `ReportServiceImpl.balanceSheet()` 把本年利润（4103 余额 + 当期未结转 6xx 净额）算进 `totalEquity` 与平衡校验，但只输出 `currentYearProfit` 单字段，未作为权益区**行**；前端 `BalanceSheetView` 只渲染 `equity` 数组（实收资本），本年利润行丢失 |
| ② | 利润表本期"营业收入 -27"、"营业利润 -54"，本年累计毛利率突变为 0，自相矛盾 | 后端 `incomeStatementData` SQL 的 `revenue` 本意是"credit 方向 6xx"，但 Java `incomeStatement()` L152 `revenue - revenue_offset` 把**全部费用侧发生额从营收里减掉** → 营收行实际=净利润；`cumulativeProfit = cumRevenue - cumCost` 漏 66xx 费用与 other_expense；**前端 `IncomeStatementView` L54-58 把本年累计列除首尾外硬编码 0** |
| ③ | 现金流量表只有"净增加额 -75684.30"，无期初/期末现金余额，报表不闭环 | `cashFlowStatement()` 仅返 In/Out/Net 六组，无 `openingCash`/`closingCash`；用户无法判断 75684.30 流出后还剩多少 |

## 1. 契约（四段）

### 1.1 输入
- `balanceSheet(period)` / `incomeStatement(period)` / `cashFlowStatement(period)`：入参不变（period，YYYYMM）
- 取数 SQL：`incomeStatementData`（本期分列）、`cumulativeData`（年初到本期分列，本次修口径）、`cashSubjectBalance`（**新增**，1001+1002 现金科目期初/期末余额）

### 1.2 输出（新增字段，向后兼容——既有消费方 P83 驾驶舱/导出 均不受影响）

`balanceSheet`：
- `equity` 数组**新增合成行**（仅 `currentYearProfit ≠ 0` 时）：`{code:4103, name:"本年利润(含未结转)", direction, begin_balance:0, end_balance:currentYearProfit, rowType:"currentYearProfit"}`
- 不变量：`Σ(equity.end_balance 逐行) == totalEquity`

`incomeStatement`：
- `revenue` 语义修正 = credit 方向 6xx 毛收入（**不再减费用侧**）
- 新增：`cumulativeGrossProfit` / `cumulativeExpense` / `cumulativeOperatingProfit` / `cumulativeOtherExpense`；`cumulativeProfit` 修正为 `cumRevenue - cumCost - cumExpense - cumOtherExpense`

`cashFlowStatement`：
- 新增：`openingCash`（1001+1002 期初）/ `closingCash`（opening + totalNet）/ `endBalanceFromSubject` / `cashCheckDiff`（科目期末 - 计算期末）/ `cashCheckOk`（|diff|<0.01）

### 1.3 状态
报表只读聚合，无状态机。

### 1.4 副作用
| 操作 | 副作用 | 回滚 |
|------|--------|------|
| balanceSheet/incomeStatement/cashFlowStatement | 只读（SQL 聚合） | N/A |
| cashSubjectBalance（新增 SQL） | 只读 | N/A |

### 1.5 异常码
| 码 | 场景 | 处理 |
|----|------|------|
| — | `cashCheckOk=false` | 非阻断：`cashCheckDiff` 暴露差异，前端红字提示；导出加勾稽提示行 |
| — | 本年利润=0 | 不追加合成行（报表惯例 0 值不占行） |

## 2. 副作用声明
纯只读聚合，无写库、无状态变更、无外部调用。新增 1 条 `@Select`（`cashSubjectBalance`），不改 schema。

## 3. 物理路径编码约定
- 后端：`backend/src/main/java/com/huicai/base/report/{service/impl/ReportServiceImpl.java, mapper/ReportDataMapper.java}`
- 前端：`frontend/src/views/report/{balance-sheet/BalanceSheetView.vue, income-statement/IncomeStatementView.vue, cash-flow/CashFlowView.vue}`
  - 资产表本年利润行：后端并入 `equity` 数组后**前端无需改**（渲染既有数组自动出现）
  - 利润表：前端删 L54-58 硬编码 0，逐行接后端新 `cumulative*` 字段
  - 现金流：前端补期初/期末行 + `cashCheckOk===false` 时红字勾稽提示行（`fixed` 标记，零值不隐藏）

## 4. BDD 验收场景

- **P88-BD1** Given 实收资本 200000、财务费用未结转 27 When 查 202607 资产负债表 Then 权益区含"本年利润(含未结转) -27.00"行，`Σ权益行 == totalEquity`，`balanced=true`
- **P88-BD2** Given 营业收入 10000、成本 3000、费用 2000、其他 500 When 查利润表 Then 营业收入=10000（非 4500），利润总额=4500，累计利润总额=4500（不漏费用）
- **P88-BD3** Given 期初现金 200000、本期无流量、科目期末 124315.70 When 查现金流 Then `closingCash=200000`，`cashCheckDiff=-75684.30`，`cashCheckOk=false` 且前端红字提示
- **P88-BD4** Given 期初现金 200000、无流量、科目期末 200000 When 查现金流 Then `cashCheckDiff=0`，`cashCheckOk=true`，无提示行

## 5. 防回归
新增 4 个 *Test（ReportServiceImplTest）锁定：①本年利润行进权益区且逐行加总恒等、②营收=毛收入且累计扣全部费用、③现金流闭环 + 差异暴露、④现金流一致时通过。

> **文档结束**
