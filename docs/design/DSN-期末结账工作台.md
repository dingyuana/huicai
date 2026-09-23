# 期末结账工作台设计

> **关联PRD**：../prd/期末结账工作台-PRD-V1.0.md
> **关联SPEC**：P84-period-close-workbench、P85-carryover-sequence-and-perf、P86-close-module-precheck、P87-period-lock-fullchain（均预留，下个 PR 开发）
> **编号**：HUICAI-DES-017
> **版本**：V1.0 | **修改日期**：2026-09-22 | **修改人**：Hermes | **修改内容**：初始创建
> 代码包：`com.huicai.base.voucher`（结账/期间/凭证链路）+ `com.huicai.sme.asset`（P85 折旧数据源）
> 设计文档：[项目说明](../CORE-项目说明.md) | [技术方案](../CORE-技术方案.md) | [需求分析](../CORE-需求分析.md)

---

## 1. 模块定位

结账从"点生成→跳列表找凭证"的任务流断裂，升级为**结账向导闭环**（4 步全在 `/finance/period-close` 内完成）；后端补 3 块：结转序列（折旧）+ 性能修复、前置检查扩展、期间锁全链路。

**设计原则**：
- **人工审核铁律**：`batch-review-post` 是"一键人工动作"——用户点按钮 = 审核 + 记账一次完成，但动作是人触发的，不建"自动审核"参数
- **专属聚合 API**：结账工作台不调通用凭证列表接口过滤 CLOSE-xxx，走 `/period-close/*` 专属端点
- **事实检查替代子模块状态机**：P86 检查"折旧未制证/发票未认证/申报未提交"等既有事实，不新增 ARAP/资产模块 close 状态（不引入新状态机）
- 既有 S-18 期间状态机（open/closed/locked）不动，P87 只扩拦截面

## 2. 核心组件

| 组件 | 说明 | 归属 |
|------|------|------|
| 结账向导（前端，P84） | 4 步 Checklist → Drawer 凭证卡片（generate 响应直接渲染）→ 迷你利润表核对 → 完成态；全程不路由跳转 | 前端 |
| generateSequence（P85） | 按 折旧→损益→利润分配 顺序调既有 Service 方法，返回 `List<ClosureVoucherVO{voucherId,voucherNo,type,totalDebit,totalCredit}>`；**不改既有方法签名**（拆私有方法复用，遵循 dy-backend-change-safety） | base.voucher |
| batchReviewAndPost（P84） | `@Transactional(rollbackFor=Exception.class)` 内逐张 审核→过账；任一失败整体回滚，错误定位到失败凭证序号 | base.voucher |
| 折旧自动制证（P85） | 按期间为"使用中"资产生成 DEPR DRAFT 凭证，金额口径 = P77 折旧统计（直线法，卡片月折旧额汇总）；幂等键 `DEPR-{period}` | sme.asset 复用 → base.voucher 生成 |
| t_close_log（P85） | 替代 `listCloseLog` 空实现：period/action/generator/duration_ms/result | base.voucher |
| 性能修复（P85） | `generateProfitCarryOver` 的 `voucherEntryMapper.selectList(null)` 全表扫描 → 改为按 period + 租户（MyBatis 拦截器自动过滤）的 SQL `GROUP BY subject_id` 聚合 | base.voucher |
| checkItems 结构（P86） | `checkBeforeClose` 返回值增加 `checkItems[{code,passed,detail,actionLink}]`；既有 `issues[]` 保留（向后兼容，S-17 契约不回退） | base.voucher |
| 期间锁全链路（P87） | 在 VoucherService 的 修改/过账/红冲 入口统一加 closed 拦截（现有仅制证 L645 一处）；反结账 reopen 前扫描未过账 CLOSE/DISTRIB/DEPR 凭证，人工确认后处理 | base.voucher |

## 3. 数据模型

| 表 | 状态 | 说明 |
|----|------|------|
| t_period（status open/closed/locked） | ✅ 已有 | S-18 状态机不变 |
| t_close_log | ❌ 待建（P85） | period, action（CHECK/GENERATE/REVIEW_POST/CLOSE/REOPEN）, operator, result, detail, created_at |
| 资产卡片折旧字段 | ✅ 已有 | P77 折旧统计报表已交付，月折旧额/累计折旧字段齐 |
| 应交税费科目 | ✅ 已有 | VAT 不进结转序列（S-17"增值税结转缺失"判定：SME 无需求，见 PRD §8） |

## 4. 端点设计

| 端点 | 方法 | 归属 | 说明 |
|------|------|------|------|
| /period-close/check | GET（增强） | P86 | 新增 checkItems 结构，保留 issues[] |
| /period-close/generate-sequence | POST（新增） | P85 | 替代前端对 3 个独立端点的串行调用；旧端点保留 |
| /period-close/batch-review-post | POST（新增） | P84 | body `[{voucherId}]`，同事务审核+过账 |
| /period-close/close | POST（既有） | — | 前置校验：sequence 凭证全部 POSTED |
| /voucher/{id} | GET（既有） | P84 | Drawer 凭证卡片取数 |

## 5. 依赖与边界

- **依赖**：S-18 期间状态机、P68 结账顺序、凭证审核/过账既有方法、P77 折旧口径、资产卡片
- **边界**：汇兑/摊销/存货结转不做（无数据基础，PRD §7）；子模块结账状态机不做（事实检查替代）；自动审核不做（铁律）
- **性能基线**：P85 修复前后各跑 1 次 10 万行基准（*RealDBTest*），SQL 聚合替代全表扫描内存聚合

## 6. 成熟度与待办

| 维度 | 状态 | 备注 |
|------|------|------|
| 后端 | ⚠️ 部分 | 结账前检查/损益/利润分配/期间锁制证拦截已有；序列/批量/锁扩展/日志待建 |
| 前端 | ❌ 待建 | 结账向导 4 步 + Drawer + 迷你利润表 |
| 测试 | ⚠️ 部分 | PeriodCloseServiceImplTest/RestContractTest 已有；P84~P87 按 Contract-First 补 |
| S-17 状态 | 需翻转 | "损益结转占位"已过时（实际完整实现），随 P85 交付时同步 SPEC |

> **文档结束**
