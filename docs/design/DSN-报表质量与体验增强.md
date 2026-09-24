# 报表质量与体验增强设计

> **关联PRD**：../prd/报表质量与体验增强-PRD-V1.0.md、../prd/报表法定结构增强-PRD-V0.1草案.md
> **关联SPEC**：P88-report-statement-correctness、P89-report-display-drilldown、P90-report-advanced（均预留）、P92-report-statutory-structure（待建）
> **编号**：HUICAI-DES-018
> **版本**：V1.1 | **修改日期**：2026-09-24 | **修改人**：Hermes | **修改内容**：补 P92 章节（现金流本年累计 / 资产分类小计）的数据模型与端点设计，登记 R-145 立项
> **历史**：V1.0 | 2026-09-22 | 初始创建（三缺陷代码级定位 + 三层增强设计）
> 代码包：`com.huicai.base.report`（后端）+ `frontend/src/views/report/*`（前端）
> 设计文档：[项目说明](../CORE-项目说明.md) | [技术方案](../CORE-技术方案.md) | [需求分析](../CORE-需求分析.md)

---

## 1. 模块定位

报表中心"数据对、呈现失信"的三层治理：P88 止血（三缺陷，信任级）→ P89 体验（显示/穿透/导出/对比列）→ P90 进阶（重分类/辅助核算展开/异常高亮）。

**铁律**：报表只读聚合，修复不回溯改账；重分类只做展示层。

## 2. 核心组件与根因定位

| 缺陷 | 根因（已 trace） | 修复组件 |
|------|-----------------|---------|
| ① 资产负债丢行 | `ReportServiceImpl.balanceSheet()` 本年利润（4103+当期 6xx）单列 `currentYearProfit` 计入合计与平衡校验，但**前端只渲染 `equity` 数组** | 后端：把本年利润作为虚拟行追加进 `equity` 行列表（新字段，既有字段语义不变）；前端：按行数组渲染（现即自动出现该行） |
| ② 利润表累计数 | 后端 L176 `cumulativeProfit = cumRevenue - cumCost`，漏 `cumulative_cost_expense`（SQL 已查未消费）与 other_expense 累计；**前端 L54-58 本年累计列除收入外硬编码 0** | 后端：累计数全量取 SQL（补查 other_expense 累计）；前端：删除硬编码 0，全走 result 字段 |
| ③ 现金流不闭环 | `cashFlowStatement()` 仅 In/Out/Net | 后端补 `openingCash`（1001+1002 期初余额）/`closingCash`（=opening+totalNet）；一致性校验 closingCash vs 1002 期末余额（差异提示，不阻断） |

## 3. 数据模型

| 项 | 状态 | 说明 |
|----|------|------|
| 三表取数 SQL | ✅ 已有 | `ReportDataMapper` 注解 SQL；P88 需补 2 列（other_expense 累计、期初现金） |
| 对比列数据源 | 🔶 需补 | 资产负债"年初数"= 年初期间科目余额快照；利润表"上期金额"= 上期 incomeStatementData |
| 异常规则（P90） | ❌ 待建 | 3 条起步（现金贷方余额/收入倒挂为借方/4103 连续借方亏损），独立小表 t_report_alert_rule（rule_code/formula/level），或并入 t_financial_metric（P81）统一规则源——**SPEC 评审时定** |
| 重分类视图（P90） | 纯计算 | 无新表：预付贷方余额行 + 调整后视图 = 行级 if-else，展示层 |
| 现金流本年累计（P92-A） | 🔶 需补 | **无新表**：`cashFlowDataYtd(period)` 变体查询，照抄 `trendData` 的 `v.period >= 年初 AND <= period` 范式（ReportDataMapper.java:140） |
| 资产分类小计（P92-B） | ❌ 待建 | 现状 `balanceSheet()` 仅按科目首位分大类（`case '1'/'2'/'4'`），**无流动/非流动维度**；`t_subject` **无分类字段**。需新增 `account_type` 列 + 迁移 + 存量回填——**B1 科目段规则 vs B2 表加列，待老丁拍板** |

## 4. 端点设计（全部既有端点增强，无新端点）

| 端点 | 变更 | 归属 |
|------|------|------|
| GET /reports/balance-sheet | `equity` 含本年利润虚拟行（字段 `rowType: profit`）；新增 `openingAssets`/`openingLiabEquity` 对比列 | P88① + P89 |
| GET /reports/income-statement | 累计数字段全量修复；新增 `previous`（上期）各字段 | P88② + P89 |
| GET /reports/cash-flow | 新增 `openingCash`/`closingCash`/`cashCheckDiff` | P88③ |
| GET /reports/subject-balance/{code}/entries | **新增**：穿透第 2 跳——科目行 → 凭证分录列表（复用既有凭证查询端点按 subjectId 过滤，端点已存在则不加） | P89 |
| POST /reports/* /export | `writeExcel` 扩模板（标题/期间/制表人/审核人） | P89 |
| GET /reports/cash-flow | items 行新增 `ytdAmount`（本年累计）；导出同步加第 4 列 | P92-A |
| GET /reports/balance-sheet | 资产/负债区新增分类小计行（`subTotal: currentAssets` 等） | P92-B（待口径） |

> **向后兼容**：所有响应为**新增字段**，既有消费方（P83 驾驶舱、导出）不受影响；前端逐视图切换新字段。

## 5. 依赖与边界

- **依赖**：P60 辅助核算明细（P90 展开）、P75/P78 余额口径（对比列年初数）、P92-B 依赖科目分类配置（`t_subject.account_type`，需先建迁移与回填脚本）
- **边界**：间接法/拖拽模板/外币/行级权限不做（PRD §6）；异常高亮规则表归属在 P90 与 P81 预警（PRD-016）间**二选一**，避免两套规则源
- **测试基线**：P88 修复需 3 期基准数据（含 27 元财务费用场景）进 *RealDBTest*，防回归

## 6. 成熟度与待办

| 维度 | 状态 | 备注 |
|------|------|------|
| 后端 | ⚠️ | 三缺陷点全部定位到行号（见 §2） |
| 前端 | ⚠️ | 利润表硬编码 0 已定位（IncomeStatementView L54-58） |
| 测试 | ⚠️ | 需补 3 期基准 *RealDBTest* |

> **文档结束**
