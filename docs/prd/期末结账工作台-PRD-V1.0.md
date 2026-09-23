# 期末结账工作台 PRD

> **编号**：HUICAI-PRD-017
> **版本**：V1.0 | **日期**：2026-09-22 | **作者**：Hermes
> **关联总 PRD**：`../CORE-需求分析.md` R-136, R-137, R-138, R-139
> **关联设计**：`../design/DSN-期末结账工作台.md`
> **关联SPEC**：P84-period-close-workbench.md（预留）、P85-carryover-sequence-and-perf.md（预留）、P86-close-module-precheck.md（预留）、P87-period-lock-fullchain.md（预留）
> **对应包**：com.huicai.base.voucher（P84~P87）+ com.huicai.sme.asset / com.huicai.sme.tax（P85/P86 数据源）
> **前置铁律**：所有审核必须人工完成——"一键审核并记账"是零跳转的人工动作，**不做**系统自动审核

---

## 1. 模块定位

把现有"点击生成 → 跳凭证列表大海捞针 → 审核 → 回来继续"的**任务流断裂**，重构为结账向导（Wizard）闭环；同时补全结转序列与期间锁的链路缺口。

**现有基础（不重复建）**：
- 结账前检查 `checkBeforeClose`（4 项：期间状态/未记账凭证/试算平衡/红冲草稿）
- 损益结转 + 利润分配凭证生成（DRAFT，幂等保护，事务回滚）
- 期间锁定（closed 状态 + VoucherServiceImpl L645 制证拦截）+ 反结账 + P68 顺序校验
- P77 折旧统计报表（资产卡片数据可复用）

## 2. 功能清单

| 编号 | 功能点 | 优先级 | 状态 | 验收标准 | 关联SPEC |
|------|--------|--------|------|---------|---------|
| P84 | 结账工作台（4 步向导 + Drawer 凭证卡片 + 一键人工审核记账 + 专属聚合 API） | P1 | ✅ 已修复（见 P84 SPEC） | 全程不离开结账页：4 步向导（检查→生成→核对→结账），`generate-sequence` 返回三步凭证清单（DEPR/CLOSE/DISTRIB，含 id/no/类型/借贷合计/分录数）；`batch-review-post` 同事务完成 提交→审核→记账（前置预校验全部 DRAFT，任一步失败整体回滚）；记账后 Drawer 卡片状态即时变 ✅。架构：编排落 `sme.periodclose`（base 零依赖 sme 硬约束） | P84 |
| P85 | 结转序列补全（折旧自动制证 + 结账日志表 + 全表扫描性能修复） | P1 | ✅ 已修复（commit b40a9e5/7cf5620/1a044f5） | 三子项全部落地：①`generateProfitCarryOver` 改单条聚合 SQL（消除 `selectList(null)` + 两层 N+1），`HAVING ABS(debit-credit)<>0` 排除借贷对冲科目；②`t_close_log` 建表落地（V151），`listCloseLog` 空实现替换，`operator_id` 自定义列（`created_by` 是 exist=false 不落库）；③DEPR- 折旧制证（`sme.asset` 包，凭证号=幂等键，6602/1602，DRAFT 人工审核）。附带发现 V102_5 漏 seed 1602 累计折旧（V152 补）。`generate-sequence` 编排已随 P84 工作台交付 | P85 |
| P86 | 子模块前置检查扩展（固定资产计提/发票认证/申报 完成度进 Checklist） | P2 | ❌ 待建 | check 返回结构化 checkItems（code/passed/detail/actionLink），新增 3 项基于既有事实：当期折旧未制证、进项未认证张数、申报未提交——只检查事实，不新建"子模块结账"状态 | P86 |
| P87 | 期末锁全链路 + 反结账未过账结转凭证防护 | P1 | ✅ 已修复（commit e893232） | 现状 trace 后比立项预判好：`assertPeriodOpen` 已覆盖 7 个写入口（制证/修改/删除/提交/审核/反过账/过账），真实缺口仅 2 处已补——①`reverse`(红冲) 补拦截；②`reopenPeriod` 反结账前扫 CLOSE-/DISTRIB-/DEPR- DRAFT 凭证，存在则拦（只拦不自动删，守人工铁律） | P87 |

## 3. 状态流转

*结账向导步骤本身不是业务单据，无状态机；期间状态机（open→closed→reopen）沿用 S-18 既有定义，P87 只补拦截面，不改状态枚举。*

## 4. 交互设计（向导 4 步）

| Step | 界面 | 交互 |
|------|------|------|
| 1 结账环境检查 | Checklist（既有 4 项 + P86 的 3 项，✅/❌ + 数字明细如"未记账 0 张"） | 全绿后"下一步"高亮；红项附"去处理"跳转链接 |
| 2 生成与预览 | 点击生成 → 当前页下方/右侧滑出 **Drawer 凭证卡片**（凭证号/类型/借贷合计/分录摘要），**不跳转** | 卡片内"一键审核并记账"（调 P84 batch 端点，记账成功卡片状态即时变 ✅）；"编辑"开 Modal 编辑分录，保存后返回看板刷新 |
| 3 核对 | 记账后自动渲染迷你利润表 + 提示"损益类科目余额已为 0，本期净利 XXX" | 确认无误显示"执行期末关账"按钮 |
| 4 完成 | 成功提示"YYYY-MM 已关账"（下期存在性检查：若下期未建，提示先补建期间，不自动创建） | 撒花/完成态 |

**异常处理**：生成/审核/记账任一步失败 → 事务回滚（已有 `rollbackFor=Exception.class`）+ 页面弹错误详情（异常消息原文），不产生半成品凭证（幂等键 `CLOSE-{period}` 保证可重试）。

## 5. API 设计（结账专属聚合接口，不复用通用凭证列表）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /period-close/check（增强，P86） | 返回 `{passed, issues[], checkItems[{code,passed,detail,actionLink}], trialBalance}` |
| POST | /period-close/generate-sequence（P85） | 按 折旧→损益→利润分配 顺序生成 DRAFT 凭证序列，返回 `[{voucherId, voucherNo, type, totalDebit, totalCredit}]` |
| POST | /period-close/batch-review-post（P84 新增） | body 为 voucherId 数组；单事务逐张 审核→过账；任一失败整体回滚并返回失败明细 |
| POST | /period-close/close（既有） | 前置：generate-sequence 返回的凭证全部 POSTED |
| 凭证卡片数据 | GET /voucher/{id}（既有） | Drawer 直接取数，不新增 |

> P85 的 `generate-sequence` 与既有 `profit-carryover`/`profit-distribution` 端点关系：保留旧端点（S-17 契约不回退），新端点内部复用同一 Service 方法（拆分方法，不改签名，遵循 dy-backend-change-safety）。

## 6. 验收标准（BDD）

- **P84-BD1**：Given Step2 已生成 3 张结转凭证 When 点"一键审核并记账" Then 同一事务内 3 张全 POSTED、Drawer 卡片全 ✅，全程未离开结账页
- **P84-BD2**：Given 3 张中第 2 张过账失败（如期间已锁） When 执行 batch Then 全部回滚（前 1 张也退回 DRAFT）、错误定位到第 2 张
- **P85-BD1**：Given 当期 10 张"使用中"资产、P77 折旧合计 1.2 万 When 执行 generate-sequence Then DEPR 凭证借管理费用-折旧 1.2 万 / 贷累计折旧 1.2 万，状态 DRAFT
- **P85-BD2**：Given 10 万行凭证分录 When 生成结转 Then SQL 聚合耗时 <2s（修复前全表扫描基准对比）
- **P86-BD1**：Given 当期存在 2 张未认证进项发票 When 结账检查 Then checkItems 含 `INV_CERT` 未过项（detail："2 张未认证"），不阻断（黄灯项），仅 红灯项（未记账凭证/试算不平）阻断关账
- **P87-BD1**：Given 期间 202407 已 closed When 对该期间凭证执行 修改/过账/红冲 Then 全部拦截（现有仅制证拦截，扩展 3 处）
- **P87-BD2**：Given 已 closed 期间存在未过账 CLOSE 凭证（异常数据） When 反结账 Then 弹窗列出该凭证，人工确认删除/红冲后才完成 reopen

## 7. 不做的事（裁剪记录，防复发）

| 项 | 理由 |
|----|------|
| 汇兑损益结转 | 慧财**无外币/汇率**数据基础（全局搜索 0 命中） |
| 摊销结转 | 无长期待摊/摊销模块 |
| 存货跌价结转 | 无存货模块（SME 口径，采购/销售直接凭证） |
| 子模块"结账"状态机（金蝶式 ARAP/资产模块 close 状态） | 不新建状态——用 P86 **事实检查**替代（折旧未制证/发票未认证/申报未提交），成本低、不引入新状态机 |
| 系统自动审核结转凭证 | **铁律冲突**（审核必须人工）；P84 一键人工审核已消除跳转痛点 |
| 自动创建下一会计期间 | 期间初始化属建账动作，保持人工；向导 Step4 只做提示 |

## 8. 与 S-17 遗留对齐

| S-17 标注缺口 | 本 PRD 处置 |
|---------------|------------|
| 损益结转"占位实现" | 实际已完整实现（P79 批次后已区分收入/费用、结转入 4103，S-17 状态需翻转 ✅） |
| 折旧结转缺失 | P85 补（数据源=资产卡片，P77 报表口径） |
| 增值税结转缺失 | **不补**：SME 税额通过申报模块核算，发票税额直接计入应交税费，无"应交增值税"科目期末结转需求；如未来出现差额结转诉求再单独立项 |
| 结转模板配置 | 不做（固定 3 类序列，SME 不需要可配置模板） |
| 结账日志占位 | P85 落地 t_close_log |

> **文档结束**
