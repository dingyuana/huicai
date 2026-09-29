# 慧财财务系统 — 需求登记册

> 版本：V1.26
> 日期：2026-09-28
> 关联文档：[项目说明](../../项目说明.md)、[技术方案](../../技术方案.md)、[需求分析](../../需求分析.md)、[P0-P3 路线图](../../development/plans/P0-P3-roadmap.md)、[文档注册表](../../文档注册表.md)
>
> 本文件是系统所有功能需求的唯一权威来源。每条需求有唯一编号 REQ-2026-XXX，关联到 SPEC 文档和实现模块。
> 新增/修改需求必须在此登记并更新版本号。
>
> **与文档编号的关系**：REQ 编号用于功能需求的可追溯性，HUICAI-xxx 编号用于文档索引管理，两者互补。

---

## 版本变更记录

| 版本 | 日期 | 变更人 | 变更内容 |
|------|------|--------|----------|
| V1.30 | 2026-09-29 | opencode | **银行流水批次修复（REQ-2026-118）**：5 类 9 项表层错误（`tx_type` NOT NULL、`account_id` 空值、`fk_statement_account`、`t_asset_card.category_id`/`useful_life`）修完后，暴露 4 层更深缺陷：①**约束用例「因错误的原因通过」** —— `BankStatementMapperTest` 5 个 `assertThrows` 用例被 FK 违约顶替，从未真正验证 `chk_stmt_type`/`chk_stmt_match_status` 等；②`insert_shouldEnforceChkDirection` 守护**不存在的约束**（`direction` 是 `exist=false`，DB 无该列故无 CHECK）→ 改为断言该字段确实不落库；③`version` 断言与 DB 默认值 1 矛盾，且 MyBatis-Plus 不回填 `@Version` 默认值 → 改用查库实体做乐观锁更新，并补「过期 version 命中 0 行」用例（须显式构造旧 version，同 SqlSession 内两次 `selectById` 返回**同一对象实例**）；④`BankStatementAuditIntegrationTest` 的 `audit()` 内部走 `autoGenerateInNewTx`（**REQUIRES_NEW**），看不到基类 `@Transactional` 未提交的夹具数据 → 显式 `@Transactional(NOT_SUPPORTED)` 关闭回滚，并补登录态与 1002/2203/1122 科目。慢测 **44 → 35 项**，快测 1733/0 无回归 |
| V1.29 | 2026-09-29 | opencode | **悬空外键批次修复（REQ-2026-117）**：基类补 `ensureCustomer()`（与 `ensureVendor()` 对称，`t_customer` 唯一约束 `UNIQUE(code, enterprise_id)`，同企业造一行并缓存）；重写 `SalesFlowE2ETest`/`InputFlowE2ETest`，修 3 类缺陷：硬编码 `customerId/vendorId=1|99` 悬空外键、`t_business_doc.doc_no` NOT NULL 只读不写、单据状态误用发票状态（`CONFIRMED`/`SETTLED` 均不在 `chk_doc_status` 允许集）。**顺带纠正一批不可能成立的断言**：原代码断言 `invoice.docNo`/`invoice.voucherNo`/`voucher.sourceDocNo` 等 `exist=false` 字段（实体注释明写「DB 无此列」），经 DB 往返必为 null，改为断言真实 id 列落库并补 `voucher.businessDocId` 溯源。慢测 **55 → 44 项 / 31 类**，两个 FK 根因彻底消失，快测 1733/0 无回归 |
| V1.28 | 2026-09-29 | opencode | **D5 实施完成（REQ-2026-116）**：`AbstractMapperTest` 统一设默认企业上下文 + `@AfterEach` 清理，慢测 **92 → 55 项 / 33 类**，`enterprise_id` 根因从 Top 榜彻底消失。**两处必须处理的副作用**：①`DataIsolationAuditTest` 6 个「漏洞确认」用例 + `BankStatementDataIsolationTest` 1 个「超级管理员」用例依赖「无上下文→拦截器放行全部」，改为方法体内显式 `clear()`（并留注释说明为何与基类默认值相反）；②`IncomeStatementCaliberRealDBTest`(9904)/`AuxiliaryDetailRealDBTest`(9905)/`CashSubjectBalanceRealDBTest`(9902)/`OpeningContinuityRealDBTest`(9903) 用独立 `ENT_ID` 造数，上下文须经新增的 `useEnterprise()` 切到同一企业，否则报表查询返回 0 行。**无回归**：全量慢测无新增红项，快测 1733/0 |
| V1.27 | 2026-09-29 | opencode | **慢测 A+D 类修复回写**：新增 REQ-2026-113（A 类 29 项，编号关联旧设计 → 改写断言新模型，不补 schema）、REQ-2026-114（D 类 7 项，IDENTITY 显式赋 id + `exist=false` 属性做 lambda 排序）、REQ-2026-115（生产缺陷：银行 CSV 真实表头「对方户名」未在 `ColumnMappingResolver` 别名中，导致 `counterAccount` 恒 null）。**分诊归因纠正**：`InvoiceConfirmAuditPathIntegrationTest` 的 2 项原归 D 类（IDENTITY），实为 C 类 —— 真因是 `AuditLogEntity.createdAt` 标注 `exist=false`，`orderByDesc(getCreatedAt)` 抛 `can not find lambda cache`，与 IDENTITY 无关。**全量慢测 1984 项 → 1 failure + 91 errors（原 128），本批修复 36 项** |
| V1.26 | 2026-09-28 | opencode | **P99 批次实施完成回写**：REQ-2026-103~110 状态由「SPEC 已写待审核」转「✅ 已开发完成」（commit 815cb7e6）；新增 REQ-2026-111（V157 扩展 chk_stmt_review_status 为 13 值超集，修复 manual_pending/DUPLICATE/classified 三个被 DB 拒绝的生产写入路径，其中 Excel 导入遇重复流水会整批失败）与 REQ-2026-112（**仅登记未修**：应用用户 huicai 为超级用户，超级用户绕过 RLS 且 FORCE 无效，导致现有 70 张表的行级安全实为摆设，租户隔离仅靠应用层拦截器）。同时据实登记 P99 V1.1 的 5 处偏差，其中 REQ-108 原判「fork 复用」被证伪（reuseForks=false 反使 ConnectException 131→194），真实根因为「扩展按类各建容器 + Spring 上下文只建 1 个」 |
| V1.25 | 2026-09-28 | opencode | 新增 REQ-2026-103~110 共 8 条（SPC-P99，开发环境可运行性修复与构建门禁治理）。**全部为本机从零搭建环境并跑通全链路时实测触发的缺陷，非静态审查推测**：前端 `vue-tsc` 门禁失败致无 `dist`；种子账号哈希与注释口令不符致新库无法登录（e2e 与人工验收全部受阻）；ai-service 因 `pydantic==2.6.1` 与 `langchain>=0.3.0` 冲突致镜像无法构建；compose 两服务同映射 8000 致全量 `up` 必失败（附带发现 `ai.service-url` 自引用后端自身端口）；V106 之后建的 4 张租户表漏掉 RLS；慢测全量运行因共享容器+`reuseForks` 失败但逐类单跑全绿；`VoucherList.test.ts` 5 项失败；`application.yml` 明文写入 NVIDIA API Key。**批次性质为缺陷修复，不含任何新功能、不改业务口径**。SPEC 见 `docs/specs/P99-runnability-and-build-fixes.md`（V1.0，待老丁审核，含 D1-D6 拍板项）。**登记时即附实测证据**（file:line + 命令输出），避免后续重跑复现 |
| V1.0 | 2026-07-07 | 初次建立 | 从 DESIGN.md V3.0 + 全部 SPEC 提取 54 条需求，按模块分类编号 |
| V1.1 | 2026-07-08 | Hermes | 新增 REQ-2026-055~057：核销 Timeline/穿透点击/FIFO 自动核销 |
| V1.2 | 2026-07-09 | Hermes | 新增 REQ-2026-058~062：AI 流水分类/审核建议/核销匹配/反馈闭环/AI 测试 |
| V1.3 | 2026-07-11 | Hermes | 新增 REQ-2026-063~064：账龄分析与逾期预警、客户对账与差异处理 |
| V1.4 | 2026-07-23 | Hermes | 新增 REQ-2026-066~075：Agency 分支（多租户+代账引擎）10 条需求；删除 §十二 重复登记的 REQ-055~057/063~065 |
| V1.5 | 2026-08-07 | Hermes | 新增 REQ-2026-076：销项发票批量操作（7 个状态机批量端点 + 前端选择+动态按钮+失败明细） |
| V1.6 | 2026-08-07 | Hermes | 新增 REQ-2026-077：企业级建账期间通用化（start_period 落库 + 默认期间接口 + 过账校验重写） |
| V1.7 | 2026-08-13 | Hermes | 新增 REQ-2026-078：期初建账审计增强（指定录入时间 + 建账日期/录入人员记录 + 审计日志操作人修复） |
| V1.8 | 2026-08-31 | Sisyphus | 新增 REQ-2026-079：账簿查询增强（辅助核算账/明细账日期范围/本年累计/过滤维度），依据账簿评估报告 D1-D7 |
| V1.9 | 2026-09-11 | Sisyphus | 新增 REQ-2026-080：反核销制证凭证联动作废（幽灵凭证修复），作废 DRAFT 凭证 + 双侧挂接清空 + 非 DRAFT 拦截 |
| V1.10 | 2026-09-12 | Sisyphus | REQ-2026-015（坏账计提 P43）状态回写 ✅：BadDebtController/ProvisionService/状态机/测试均已落地，文档状态滞后修正 |
| V1.11 | 2026-09-13 | Sisyphus | 新增 REQ-2026-081（批量操作交互统一 P67 + 前端设计规范 FDS）、REQ-2026-082（资产负债表平衡根治）、REQ-2026-083（期末结账按期间顺序约束） |
| V1.12 | 2026-09-14 | Sisyphus | 新增 REQ-2026-084（采购进项发票转凭证科目映射修正 P70） |
| V1.13 | 2026-09-14 | Sisyphus | REQ-2026-077/078（P71/P72）状态回写 ✅：代码已全部落地（commit b24c7c0），接口测试补齐（11 接口 + 审计落库 RealDB 断言） |
| V1.14 | 2026-09-17 | Sisyphus | 新增 REQ-2026-085（应收应付余额汇总 P75）：期初/本期应收/本期实收/期末余额汇总端点 + 前端视图，对标用友/金蝶/QuickBooks/Xero/SAP B1 汇总表结构 |
| V1.15 | 2026-09-24 | Sisyphus | 新增 REQ-2026-090~100 共 11 条（报表深度评估 P0/P1/P2 批次，关联 SPC-P94~P97）：年初口径统一、零值行规则、勾稽预警增强、全局下钻穿透、A4 打印、凭证级现金流项目与筹资识别、间接法补充资料、余额表树状/辅助核算、报表重分类、利润表法定行扩展、异常业务诊断；依据两轮四表深度技术评估对标用友/金蝶/SAP/QuickBooks |
| V1.16 | 2026-09-24 | Sisyphus | REQ-2026-090~100 状态回写：四批 SPEC（P94~P97）经老丁分批审核全部通过（各 SPEC V1.1 记录拍板项），状态由「草案待审核」→「已审核通过，待排期」 |
| V1.17 | 2026-09-28 | Sisyphus | REQ-2026-090/091/092 状态回写：P94 批次1 完成开发与部署核验（Plan 审核通过后按 M0-M5 微循环实施，年初 begin 口径/零值行规则+悬空保护/勾稽纳入 1012+页面级 Alert 均已落地，后端快测 1723 全绿、真实 DB 3/3、前端 vitest 14/14、typecheck 0 错），状态由「已审核通过，待排期」→「✅ 已开发完成，待业务验收」；REQ-097~100 仍待排期 |
| V1.24 | 2026-09-28 | Sisyphus | **REQ-2026-100 与 REQ-2026-102 阶段 E 代码完成，测试与部署验证待补**（commit `a59ade0`）。实现：`/reports/diagnostics` 返回规则命中列表，首期三条规则全部复用既有查询不新增 SQL——零收入高费用、期末现金骤降超半数、期初余额不连续（后者直接复用 P98 的 `checkOpeningContinuity`，即 REQ-102 页条的数据来源）；前端 `DiagnosticAlert.vue` 三表共用 + 纯函数整形（规则 id 前缀、过滤空项、空输入不渲染、接口失败静默降级）。诊断纯建议不改数（铁律 #2），只读已用交互白名单断言。**状态标注为「代码完成·待验证」而非「已验收」**：前端测试未执行确认、全量快测与真实 DB 测试与页面部署核验本轮按指示未跑 |
| V1.23 | 2026-09-28 | Sisyphus | **REQ-2026-097 阶段 C-2 与 REQ-2026-098 阶段 D 完成并部署验证**。C-2 辅助核算明细：核验发现 `assist_json` 的 schema 在代码库中**无任何定义**（全链路透传），故采用 schema 无关聚合（按 jsonb 值分组 + 前端通用渲染「键: 值」），避免按臆测键名写 SQL 匹配不到数据且上游改键名即静默返回空。D 报表重分类：新增 `balanceSheetWithReclassification`（资产类科目贷方余额重分类为负债列报 + `reclassified` 标记），**默认关闭时与现状 diff==0**，开关状态随响应返回并在报表抬头显示；纯列报层动作，不改账不出凭证。测试：真实 DB 5/5 + 服务层 3 例 + 快测 1732 全绿 + typecheck 0 错。**已知限制**：dev 数据无资产类贷方余额、`assist_json` 全库为空，两项在页面均为空操作，规则有效性由单测保证。**遗留**：REQ-2026-100 诊断黄条与 REQ-2026-102 期初连续性页条（阶段 E）待排期 |
| V1.22 | 2026-09-28 | Sisyphus | **REQ-2026-097 阶段 C-1（余额表树状与层级字段）完成并部署验证**，顺带修复一处现存缺陷：余额表「层级」列绑定 `row.level` 但后端 VO 从未回填该字段，**该列长期恒为空**，本批补 `parentId`/`level`/`isLeaf`/`auxCalcType` 回填后恢复真实值。同时按 D3 拍板以纯函数实现前端组树，含「父级不在结果集则提升为根」「自引用不死循环」两条不丢行规则。实施期修正严重缺陷：组树初版只认 camelCase，而页面报表端点由 SQL 直出 snake_case 键，导致整表只剩 1 行，已改双形态兼容并补回归用例。测试：后端 36/36 + vitest 9/9 + 快测 1729 全绿 + 页面实测零错误。**REQ-2026-099 阶段 B（法定行）随阶段 A 一并交付**（前端与导出 18 行法定结构、四费分列）。**遗留**：C-2 辅助核算明细、阶段 D 重分类、阶段 E 诊断黄条（含 REQ-102）待排期 |
| V1.21 | 2026-09-28 | Sisyphus | **REQ-2026-099 阶段 A（利润表取数口径）完成并部署验证**。开工前核验发现原 SPEC 描述之外的三处**财务准确性缺陷**，已一并修正：① 营业收入口径 `6%` 一锅端 → 营业外收入/其他收益/投资收益/公允价值变动/资产处置收益被算进营收；② 6403 税金及附加整段缺失 → 利润总额虚高；③ 6801 所得税缺失 → 无净利润行（报表不完整）。另将研发费用从「其他支出」独立、四费分列。dev 数据 6xx 几乎全零，故页面看不出问题，属长期潜伏缺陷。测试：真实 DB 4/4 + 服务层勾稽 2 例 + 快测 1727 全绿 + typecheck 0 错。**口径修正声明**：历史利润表数字与旧导出件不可比（纠错非改规则）。遗留：阶段 B-E（余额表树/辅助核算、重分类、诊断黄条含 REQ-102）待排期 |
| V1.20 | 2026-09-28 | Sisyphus | **REQ-2026-101 业务验收通过**（老丁 2026-09-28），状态「✅ 已开发完成，待业务验收」→「✅ 已验收」。验收依据：① 真实 DB 跨期测试 6/6（4001 期初 200000 vs 上期期末 300000 → diff −100000、回溯命中 202401；修正期初后不再检出）② 结账闸门单测 38/38（期初不连续 → passed=false 且 issues 含期初；连续 → 不新增 issue）③ 后端快测 1725/0 failures ④ 部署验证 202408 skip 且 passed=true（不误阻断）、202409 mismatchCount=0 且不新增 issue ⑤ 页面入口已可用（期末结账页「执行结账前检查」直接渲染 issues，无需改前端）。**遗留**：202407 因已结账无法现场演示拦截，需反结账后重结账方可验证（业务状态变更待授权）；D4 已知局限（断层跨度 >12 个月按 skip 处理） |
| V1.19 | 2026-09-28 | Sisyphus | REQ-2026-101 状态回写：P98 按 Plan（审核通过）实施完成并部署验证（commit `fc2c775`）——`checkOpeningContinuity` 逐末级科目比对本期期初 vs 上期期末（复用逐月回溯口径、批量取数），接入 `checkBeforeClose` 第三项并复用既有阻断路径；部署验证 202408 skip 不误阻断 / 202409 连续不新增 issue；真实 DB 6/6、快测 1725 全绿。状态「草案待审核」→「✅ 已开发完成，待业务验收」。REQ-2026-102 仍为草案（随 P97 实施） |
| V1.18 | 2026-09-28 | Sisyphus | 新增 REQ-2026-101/102 共 2 条（SPC-P98，期初余额跨期连续性）：**来源为 P94 业务验收现场发现**——实收资本 4001 由 202401 期末 300,000 变为 202407 期初 200,000，凭据表查无一笔触及 4001，两期各自试算平衡与资产负债恒等式均通过，断层未被任何现有校验拦下。核验结论：结账闸门 `checkBeforeClose` 已有「试算平衡 + 资产=负债+权益」两项并阻断结账，缺的是跨期期初连续性，故按最小侵入补第三项而非新建机制。对标用友 NC65「年初重算：期初余额数据与上年不一致即报错拦截」「期初建账：试算不平衡无法建账」、金蝶云星空「结账前系统自检不通过就结不了」「试算平衡按钮」「结账后余额表结转下期」 |
---

## 一、基础数据管理

| 编号 | 需求名称 | 优先级 | 验收标准 | 关联 SPEC | 实现状态 |
|------|---------|--------|---------|-----------|---------|
| REQ-2026-001 | 会计科目管理 | P0 | 科目增删改查、层级管理、编码规则可配置 | — | ✅ |
| REQ-2026-002 | 会计期间管理 | P0 | 期间创建、状态控制（OPEN/CLOSED/LOCKED） | SPC-003 | ✅ |
| REQ-2026-003 | 凭证类型管理 | P0 | 凭证类型增删改查、默认属性配置 | — | ✅ |
| REQ-2026-004 | 用户与角色权限 | P0 | RBAC 模型、button-level 控制、JWT 认证 | SPC-001 | ✅ |
| REQ-2026-005 | 主数据管理 | P0 | 客户/供应商/部门/员工主数据 | — | ✅ |
| REQ-2026-006 | 系统参数与审计日志 | P0 | 参数配置、AOP + jsonb 审计日志 | — | ✅ |

---

## 二、总账管理

| 编号 | 需求名称 | 优先级 | 验收标准 | 关联 SPEC | 实现状态 |
|------|---------|--------|---------|-----------|---------|
| REQ-2026-007 | 凭证管理（6态状态机） | P0 | DRAFT→SUBMITTED→APPROVED→POSTED→CLOSED→REVERSED | SPC-004-P22 | ✅ |
| REQ-2026-008 | 科目余额计算与期初余额 | P0 | 自动计算、期初录入、试算平衡 | SPC-004 | ✅ |
| REQ-2026-009 | 期间结账 | P0 | 期末结账/反结账、损益结转 | SPC-003 | ✅ |
| REQ-2026-010 | 凭证模板管理 | P0 | 模板增删改查、自动匹配生成凭证 | SPC-004 | ✅ |
| REQ-2026-011 | 全链路编号追溯 | P0 | 按任意编号双向追溯，6 种实体类型 | — | ✅ |
| REQ-2026-079 | 账簿查询增强 | P1 | 辅助核算账查询（B-011/AT-10）；明细账日期范围；本年累计列；过滤维度；联查穿透 | SPC-060-P60 / SPC-062-P62 / SPC-063-P63 / SPC-064-P64 | ✅ T1-T10 全部实现（含 P2 质量：N+1 消除 + VO 化），全量 1555 测试 0 失败 |
| REQ-2026-083 | 期末结账按期间顺序约束 | P0 | 结账时紧邻上一会计期间必须已结账（建账起始期除外），否则拒绝并提示；反结账时后续期间必须全部未结账，防止跨期跳结 | SPC-P68 | ✅ 已实现待验收（结账/反结账硬校验+检查页软提示，26+5+4 测试） |
| REQ-2026-084 | 采购进项发票转凭证科目映射修正 | P0 | 进项发票转凭证借方使用 1405 库存商品（不含税）+ 2221.02 应交增值税-进项税额，贷方 2202 应付账款；目标科目缺失时抛 BusinessException（不得自动建科目）；缺 2221.02 企业由 Flyway 种子补齐 | SPC-P70 | ✅ 已实现（23 测试含 4 个新增 P70 场景；V146 种子幂等，dry-run 5 企业正确） |

---

## 三、应收应付管理

| 编号 | 需求名称 | 优先级 | 验收标准 | 关联 SPEC | 实现状态 |
|------|---------|--------|---------|-----------|---------|
| REQ-2026-012 | 核销工作台 | P0 | 推荐匹配、手工匹配、执行核销 | SPC-007-P30 | ✅ |
| REQ-2026-013 | 核销单管理 | P0 | 核销单 DRAFT→CONFIRMED→VOUCHERED→REVERSED | SPC-007 | ✅ |
| REQ-2026-014 | 预收预付管理 | P0 | 预收/预付创建、核销冲抵 | SPC-007 | ✅ |
| REQ-2026-015 | 坏账计提 | P1 | 账龄分析、坏账准备计提 | SPC-043-P43 | ✅ 已完成 |
| REQ-2026-016 | 费用报销管理 | P1 | DRAFT→SUBMITTED→APPROVED→REJECTED→VOUCHERED | SPC-008-P11 | ⚠️ 后端完整，前端基础 |
| REQ-2026-055 | 核销全链路 Timeline 视图 | P1 | 时间轴展示从银行流水→收款单→核销→凭证全链路 | SPC-042 | 🆕 规划中 |
| REQ-2026-056 | 核销穿透点击（Drill-down） | P1 | 核销单详情页中上游来源/下游去向标签，点击跳转 | SPC-042 | 🆕 规划中 |
| REQ-2026-057 | FIFO 自动核销（人工触发） | P1 | 核销工作台一键触发先进先出自动匹配，草稿展示待确认 | SPC-042 | 🆕 规划中 |
| REQ-2026-063 | 账龄分析与逾期预警 | P1 | 账龄分析表、到期债权表、逾期预警(4级) | SPC-051-P51 | ✅ 已完成 |
| REQ-2026-064 | 客户对账与差异处理 | P1 | 客户对账单、未达账项、差异处理闭环 | SPC-052-P52 | ✅ 已完成 |
| REQ-2026-065 | 采购付款财务流程 | P1 | 应付账龄、付款计划、采购退货、预付款联动 | SPC-053-P53 | 📝 SPEC已完成 |
| REQ-2026-080 | 反核销制证凭证联动作废（幽灵凭证修复） | P0 | 反核销时作废 DRAFT 制证凭证并清空核销单/业务单据双侧 voucher 挂接；非 DRAFT 凭证拦截提示先红冲；单据状态按剩余金额回落 | SPC-111 | ✅ 已完成（commit 4843440，28 单测 + 端到端 settlement_27 全链验证） |
| REQ-2026-085 | 应收应付余额汇总 | P1 | 期初/本期应收/本期实收/期末余额四栏汇总端点（一行一客商）+ 前端视图；期间必填；已结清单据不计期末余额但计本期发生；跨期核销归属当期；反核销不污染视图；数据权限隔离；恒等式 opening+current−settled==closing | SPC-075-P75 | 📝 SPEC 草案待审核 |

---

## 四、现金与资金管理

| 编号 | 需求名称 | 优先级 | 验收标准 | 关联 SPEC | 实现状态 |
|------|---------|--------|---------|-----------|---------|
| REQ-2026-017 | 银行流水导入 | P0 | Excel/CSV 导入、预览、去重、容错 | SPC-005-P1 | ✅ |
| REQ-2026-018 | 银行流水分类（8类） | P0 | 8 类分类、A/B/C 路由、规则引擎 | SPC-005 | ✅ |
| REQ-2026-019 | 自动生单 | P0 | A→凭证、B→单据、C→待人工 | SPC-005 | ✅ |
| REQ-2026-020 | 银行对账 | P0 | 自动/手工对账、余额调节表 | SPC-012-P14 | ✅ |
| REQ-2026-021 | 日记账管理 | P0 | 银行/现金日记账查询 | — | ✅ |

---

## 五、固定资产管理

| 编号 | 需求名称 | 优先级 | 验收标准 | 关联 SPEC | 实现状态 |
|------|---------|--------|---------|-----------|---------|
| REQ-2026-022 | 资产卡片管理 | P0 | 增删改查、IN_USE→IDLE→DISPOSED→SCRAPPED | SPC-006-P1 | ✅ |
| REQ-2026-023 | 资产类别管理 | P0 | 类别属性配置 | SPC-006 | ✅ |
| REQ-2026-024 | 折旧计提 | P0 | 按月计提、多折旧方法 | SPC-006 | ✅ |
| REQ-2026-025 | 资产处置与盘点 | P0 | 处置、盘点、盘盈/盘亏处理 | SPC-013-P2 | ⏳ 规划中 |

---

## 六、发票与税务管理

| 编号 | 需求名称 | 优先级 | 验收标准 | 关联 SPEC | 实现状态 |
|------|---------|--------|---------|-----------|---------|
| REQ-2026-026 | 进项发票管理 | P0 | Excel 导入、去重、价税分离 | SPC-010-P21 | ✅ |
| REQ-2026-027 | 销项发票管理 | P0 | Excel 导入、容错、匿名客户 | SPC-010 | ✅ |
| REQ-2026-028 | 发票状态机 | P0 | PENDING_CONFIRM→CONFIRMED→VOUCHERED→REVERSED | SPC-010 | ✅ |
| REQ-2026-029 | 以票定账（凭证生成） | P0 | 人工审核→业务单→凭证，票→证全链路追溯 | SPC-014-P41 | ✅ |
| REQ-2026-030 | 税务申报 | P1 | 增值税申报、税金计算 | SPC-011-P13 | ⏳ 规划中 |
| REQ-2026-076 | 销项发票批量操作 | P1 | 7 个状态机批量端点（submit/confirm/reject/revert/markVouchered/void/reverse）+ 前端选择列+动态按钮+失败明细弹窗；best-effort 模式单条失败不影响其他；单次≤100 条 | SPC-076-P56 | 🆕 规划中 |

---

## 七、预算管理

| 编号 | 需求名称 | 优先级 | 验收标准 | 关联 SPEC | 实现状态 |
|------|---------|--------|---------|-----------|---------|
| REQ-2026-031 | 预算编制与执行控制 | P1 | 事前控制、预算预占/释放 | SPC-015-P16 | ✅ |
| REQ-2026-032 | 预算状态机 | P1 | DRAFT→SUBMITTED→APPROVED/REJECTED→CLOSED | SPC-015 | ✅ |
| REQ-2026-033 | 预算调整 | P1 | 调整申请/审批、追加/追减 | SPC-015 | ✅ |

---

## 八、财务报表与分析

| 编号 | 需求名称 | 优先级 | 验收标准 | 关联 SPEC | 实现状态 |
|------|---------|--------|---------|-----------|---------|
| REQ-2026-034 | 三大报表自动生成 | P0 | 资产负债表/利润表/现金流量表 | SPC-016-P17 | ⚠️ 基础 |
| REQ-2026-035 | 自定义报表 | P1 | 自定义模板、Excel/PDF 导出 | SPC-016 | ⚠️ 基础 |
| REQ-2026-036 | 财务分析 | P2 | 杜邦分析、趋势分析、指标计算 | SPC-017-P0 | ⏳ 规划中 |
| REQ-2026-037 | 异常指标告警 | P2 | 指标异常自动检测、告警推送 | SPC-018-P3 | ⏳ 规划中 |
| REQ-2026-082 | 资产负债表平衡根治 | P0 | 资产总计恒等于负债+所有者权益；含未分配利润（本年利润）取数、科目方向口径统一、不平衡强校验拦截与差异诊断；报表取数不依赖未结转的脏数据 | SPC-P69 | ✅ 算法层已实现待验收（全科目归类+本年利润行+diff/unbalancedItems+结账拦截，17+28 测试）；部署与存量脏数据为运营项 |
| REQ-2026-090 | 资产负债表年初数口径统一与期初对账 | P0 | 年初列明细行与小计/合计同口径：明细=subjectBalance(YYYY01).begin_balance，小计/合计必须聚合同一 begin 口径（禁止用 balanceSheet(YYYY01) 的期末聚合冒充年初）；年初列逐行加总==年初小计（容差 0.01）；年初合计与科目余额表同期期初合计自动对账，差异≥0.01 即提示；仅提示不改数（人是唯一审核主体） | SPC-P94 | ✅ 已开发完成（P94 V1.2），待业务验收 |
| REQ-2026-091 | 报表零值行可见性规则与选项细化 | P0 | 行可见性=任一展示列（年初/期末/本期借方/本期贷方）非零即显示，禁止只看期末；汇总行有值而明细全被过滤时不得出现"有合计无明细"（强制保留金额最大明细行或隐藏该汇总行，二选一配置）；提供「隐藏无发生额且无余额科目」「隐藏报表标准空白行」两个独立复选框，作用于资产负债表/科目余额表/现金流量表；默认不破坏现有勾选习惯 | SPC-P94 | ✅ 已开发完成（P94 V1.2），待业务验收 |
| REQ-2026-092 | 现金流量表与货币资金勾稽预警增强 | P0 | 页面级黄色 Alert：期末现金及现金等价物（1001+1002+1012）与现金流量表期末现金行不一致（容差 0.01）时弹出；勾稽口径覆盖其他货币资金 1012（现 P88③ 仅 100% 前缀不含 1012）；仅提示不阻断、不自动调整数据；导出 Excel 同步输出预警行 | SPC-P94 | ✅ 已开发完成（P94 V1.2），待业务验收 |
| REQ-2026-093 | 财务报表全局下钻穿透 | P1 | 资产负债表/利润表/现金流量表/科目余额表的科目级行与金额可点击 → 明细账（finance/ledger，带期间+科目+方向过滤）→ 凭证列表/凭证详情；复用 P89-C 模式扩展；不穿透的小计/合计行不可点；数据权限与登录态复用，无新鉴权面 | SPC-P95 | 📝 SPEC 已审核通过（P95 V1.1），待排期 |
| REQ-2026-094 | A4 打印预览与标准签章排版 | P1 | 报表打印预览页：企业抬头+报表名称+所属期间+金额单位，A4 纵向分页不截断行，表头每页重复；底部制表人/审核人/法定代表人签章栏；@media print 隐藏导航与操作按钮；区别于现有 Excel 导出（导出功能保持不变） | SPC-P95 | 📝 SPEC 已审核通过（P95 V1.1），待排期 |
| REQ-2026-095 | 凭证级现金流项目绑定与筹资活动识别 | P1 | 启用已建未用的 t_voucher_cash_flow（V111）；按借贷科目组合规则生成/人工指定流量类型；补全 FINANCING 分支（现行 cashFlowData 永不产出筹资活动，而 ReportServiceImpl 已预留 FINANCING_IN/OUT 展示分支）：借款/还款（2001/2501）、资本注入（4001）、股利分配（4104）→ FINANCING；预付设备款（1123 对 16xx）→ INVESTING（现行规则错归经营）；三类净额之和==期末−期初现金（容差 0.01） | SPC-P96 | 📝 SPEC 已审核通过（P96 V1.1），待排期 |
| REQ-2026-096 | 现金流量表补充资料（间接法） | P1 | 附表：净利润 + 非付现项目（折旧/摊销/减值）+ 财务费用 + 经营性应收应付变动（资产负债表期初期末差剔除投资筹资影响）调节为经营活动现金流量净额；与直接法经营净额勾稽容差 0.01；数据只读聚合不改账 | SPC-P96 | 📝 SPEC 已审核通过（P96 V1.1），待排期 |
| REQ-2026-097 | 科目余额表树状层级与辅助核算展开 | P2 | 按 t_subject.parent_id/level 树状折叠展开（沿用 level 列数据）；辅助核算维度（客户/供应商/项目/部门）展开明细行（assist_json）；合计恒等于明细加总 | SPC-P97 | 🟡 阶段C-1/C-2已完成待验收（P97 V1.4），E待排期 |
| REQ-2026-098 | 报表重分类设置 | P2 | 预付账款贷方余额→应付账款借方、应收账款贷方→预收账款等准则重分类开关；重分类仅影响列报不改账、不出凭证；开关状态在报表抬头展示 | SPC-P97 | ✅ 已开发完成待业务验收（P97 V1.4） |
| REQ-2026-099 | 利润表法定行扩展与期间费用展开 | P2 | 增加研发费用/其他收益/营业外收支/所得税费用法定行（口径按账套准则拍板：小企业准则缺省行置零隐藏，企业准则全量展示）；「减:期间费用」死数行支持点击展开管理费用/销售费用/财务费用明细（现 6601+6602+6603 捆绑求和不可展开）；本年累计列与本期列同构 | SPC-P97 | 🟡 阶段A+B已完成待验收（P97 V1.3），C-2/D/E待排期 |
| REQ-2026-100 | 报表异常业务风险诊断预警 | P2 | 零收入+低费用、存量客户长期无收入、期末现金骤降等场景输出黄色诊断提示；AI/规则输出=建议仅展示，不自动调整任何财务数据（铁律 #1/#2）；关联 REQ-2026-037 异常指标告警 | SPC-P97 | 🟡 代码完成待验证（P97 V1.5） |
| REQ-2026-101 | 期初余额跨期连续性校验（结账闸门） | P1 | 结账前检查项新增「期初连续性」：逐科目比对本期期初与上期期末（同 `getPreviousEndBalance` 回溯口径），差异 ≥0.01 汇总为 issue 并阻断结账；上期无余额数据（新账套首期/年中启用）判为 skip 而非不平；期初一致时不得误报；纯只读校验，不自动修数（铁律 #1/#2） | SPC-P98 | ✅ 已验收（P98 V1.2） |
| REQ-2026-102 | 期初连续性诊断页面提示 | P2 | 科目余额表页展示期初连续性黄色提示条（列出不平科目 + 上期期末/本期期初/差额），使用户在录凭证阶段即发现断层而非结账才被拦；复用 P97 REQ-100 的诊断规则 id 体系与黄条样式，可并入 P97 实施 | SPC-P98 | 🟡 代码完成待验证（P97 V1.5 阶段E） |
| REQ-2026-103 | 前端构建门禁与编译版本配置修复 | P0 | ① `PeriodList.vue` 补 `ElMessageBox` 导入（现仅导入 `ElMessage`，却在反结账处调用 `ElMessageBox.confirm`）→ 解除 `vue-tsc` TS2552 门禁，使 `npm run build` 能产出 `dist`；② 移除 `pom.xml` 中被 `maven.compiler.release=21` 覆盖的死配置 `<source>17</source><target>17</target>`，防日后误删属性导致静默退回 Java 17（AGENTS §4.4-19） | SPC-P99 | ✅ 已开发完成（P99 V1.1，commit 815cb7e6；场景 103-1/103-2） |
| REQ-2026-104 | 种子账号密码迁移修正 | P0 | 新增 Flyway migration 校正 `admin`/`accountant01`/`reviewer01`/`assistant01` 四个种子账号的密码哈希（现哈希与 `V114` 注释声明的 `admin123` **不匹配**，实测三者皆错：登录 400 + bcrypt 独立校验不通过 + 日志 `BadCredentialsException` 而非 `UsernameNotFoundException`）；新迁移仅 `UPDATE t_user.password`，不改表结构；**已知局限**：本系统无首次登录强制改密，生产部署前须另行处理 | SPC-P99 | ✅ 已开发完成（P99 V1.1，commit 815cb7e6；场景 104-1/2/3） |
| REQ-2026-105 | ai-service 依赖冲突解除 | P0 | `requirements.txt` 的 `pydantic==2.6.1` 放宽为 `>=2.7.4,<3.0.0`——langchain 0.3.0~0.3.17 全部要求 `pydantic>=2.7.4`，现 pin 导致 `docker compose build ai-service` 在第 5/6 步 `ResolutionImpossible` 失败（确定性冲突，与网络无关）；**仅放宽 pydantic 一项**，其余 17 个 pin 不动避免连带回归 | SPC-P99 | ✅ 已开发完成（P99 V1.1，commit 815cb7e6；场景 105-1/105-2） |
| REQ-2026-106 | compose 端口冲突与 AI 服务地址自引用修正 | P1 | `docker-compose.yml` 的 `ai-service` 宿主端口 8000→8001（现 `:88` 与 `:115` 同映射 8000，全量 `up` 必失败），容器内端口保持 8000 不动；同步修正 `application.yml` 的 `ai.service-url`（现为 `http://localhost:8000`，**指向后端自身端口**属自引用地雷）。已核验 `com.huicai.base.ai` 包内无任何 HTTP 客户端、当前**零调用方**，改端口无影响面 | SPC-P99 | ✅ 已开发完成（P99 V1.1，commit 815cb7e6；场景 106-1/106-2） |
| REQ-2026-107 | 租户表 RLS 补齐 | P1 | 为 V106 之后创建因而漏掉 `enterprise_policy` 的 4 张表补齐行级安全：`t_contract`(V107)、`t_agency_user_enterprise`(V112)、`t_service_progress`(V148)、`t_close_log`(V151)，四者均含 `enterprise_id`。须**同时** `ENABLE` + `FORCE ROW LEVEL SECURITY`（只 ENABLE 则表 owner 即应用用户 `huicai` 可绕过），策略谓词逐字复用 V106 原文。**V106 故意排除的 11 张表不动** | SPC-P99 | ✅ 已开发完成（P99 V1.1，commit 815cb7e6；场景 107-1/2/3） |
| REQ-2026-108 | 慢测共享容器基建修复 | P1 | `mvn test -DexcludedGroups=` 全量运行时 56 个 `extends AbstractMapperTest` 类共用静态容器 + `forkCount=1 reuseForks=true` 单 JVM 顺序执行，导致累计 131 次 `ConnectException` 与 `HikariPool - Connection is not available, request timed out`；**但逐类单跑全部通过**（P97 三个新 RealDB 测试 4/5/6 项均 BUILD SUCCESS）。优先改 surefire 配置而非重写 56 个子类；单类耗时须 ≤ 60s | SPC-P99 | ✅ 已开发完成（P99 V1.1，commit 815cb7e6；场景 108-2 通过，108-1 的全量 BUILD SUCCESS 未达成（暴露 128 个既有测试数据缺陷，见 P99 V1.1 偏差 4）） |
| REQ-2026-109 | VoucherList 前端单测 5 项失败修复 | P1 | `vitest run` 中 `src/__tests__/VoucherList.test.ts` 19 项里 5 项失败（失败行 `:102`/`:114`/`:130`），致 `Test Files 1 failed \| 25 passed`、`Tests 5 failed \| 260 passed`。**根因未定位**，需先做失败剖析；**禁止**用断言弱化（`toBeTruthy` 取代具体值断言/删 await/注掉 skip）掩盖问题 | SPC-P99 | ✅ 已开发完成（P99 V1.1，commit 815cb7e6；场景 109-1/109-2） |
| REQ-2026-110 | 硬编码 API Key 清除 | P1 | `backend/src/main/resources/application.yml` 明文写入 `nvidia.api-key`（`nvapi-` 前缀），违反 AGENTS §7-3「禁止硬编码敏感信息」。改为环境变量占位并由 compose 从宿主注入。**已知局限**：该值已进入 git 历史（`main @ 33f36422`），代码改动无法消除历史暴露，**须同步吊销该 key**（对标 Supabase/RLS 官方文档与主流 SaaS「密钥按已泄露假设」原则） | SPC-P99 | ✅ 已开发完成（P99 V1.1，commit 815cb7e6；源码无明文密钥，场景 110-1 通过。历史残留待吊销） |
| REQ-2026-111 | 银行流水审核状态 CHECK 约束与代码枚举对齐 | P0 | `chk_stmt_review_status` 允许集缺 `manual_pending` / `classified` / `DUPLICATE` 三个值，而 `StatementStatus` 枚举与前端映射正在写入它们：①`BankStatementServiceImpl:669/679` 写 `manual_pending` → 手工处理兜底分支写不进库、状态卡死；②`BankStatementExcelImportService:178/315` 写 `DUPLICATE` → **Excel 导入遇重复流水整批失败**；③前端 `PendingPool.vue:136` 按 `manual_pending` 查询恒无数据。**V120 曾修同一约束但未覆盖这三个值，属二次复发**。修法（老丁拍板）：扩展 DB 约束为 13 值超集并归一化存量大写值，**不动 Java 枚举与前端**（二者本就一致，DB 约束是唯一异类） | SPC-P99 | ✅ 已开发完成（P99 V1.1，V157；13 值逐个实测可插入，归一化段验证 PASS，关联 2 个测试类 24/24 全绿） |
| REQ-2026-112 | 应用改用非超级用户数据库角色以恢复 RLS 兜底 | P1 | 应用连接用户 `huicai` 的 `rolsuper=true` 且 `rolbypassrls=true`（`docker-compose.yml` 的 `POSTGRES_USER` 被官方 postgres 镜像默认建为 superuser），**超级用户始终绕过 RLS，`FORCE ROW LEVEL SECURITY` 亦无效**。实测：superuser 身份下设 `app.enterprise_id='999'` 仍可查到数据；换非超级用户角色则策略完全正常（无 GUC→0 行，GUC=1→命中）。含义：**现有 70 张表的 RLS 实为摆设**，租户隔离 100% 依赖应用层 `EnterpriseDataPermissionInterceptor`，与 CORE-技术方案 §4.8「MyBatis 拦截器 + PG RLS」三层防线的表述不符。修法方向：应用改用 `NOSUPERUSER` 独立角色 + 最小必要授权，使 RLS 真正成为兜底层 | SPC-P99 | 📋 **仅登记未修**（实施中由 P99 REQ-107 的 FORCE RLS 验证过程发现；属安全架构决策，需老丁单独拍板） |
| REQ-2026-113 | 编号关联旧设计测试守护已废弃数据模型（慢测 A 类） | P0 | `NumberingAssociationIndexesTest`/`FieldsTest`/`E2ETest` 三个类共 **29 项**守护的是**已废弃且当前 schema 从不存在**的编号结构：①发票侧 `doc_no`/`voucher_no`（编号冗余已收敛到 `t_business_doc` 一侧）与凭证侧 `source_doc_no`/`source_doc_id`/`source_doc_type`（`VoucherEntity` 自身标注 `@TableField(exist = false)`、注释明写「DB 无此列」）；②所引 V64 migration 不存在（V60~V91 无任何 migration）；③`t_receivable`/`t_payable` 已并入 `t_business_doc`。**若为转绿而补建索引/列，将复活已废弃模型并与 `t_business_doc` 单一模型冲突**。修法（老丁拍板）：**不补 schema**，改写测试断言新模型真实关联列（`t_voucher.business_doc_id` + `t_business_doc.invoice_id/invoice_no` + `voucher_id/voucher_no`），并加负向断言锁死废弃列不得复活 | SPC-P99 | ✅ 已开发完成（P99 A 类；3 个类 33/33 全绿，废弃列/表负向断言 4 项） |
| REQ-2026-114 | 测试显式赋 IDENTITY 主键 + 悬空外键/非法枚举/未设企业上下文（慢测 D 类） | P0 | 7 项报 `cannot insert a non-DEFAULT value into column "id"`：`t_input_invoice`/`t_bank_account` 等均为 `GENERATED ALWAYS AS IDENTITY`。**根因分两类**：①测试显式 `setId()` 违反 IDENTITY（4 项）；②`AuditLogEntity.createdAt` 是 `exist=false`，`orderByDesc(AuditLogEntity::getCreatedAt)` 报 `can not find lambda cache`（2 项，**分诊曾误归为 D 类，实为 C 类**）。**禁止**把 `GENERATED ALWAYS` 降级为 `BY DEFAULT` 迁就测试。连带修正：勾稽 SQL 按 `vendor_id` 过滤但 `t_vendor` 为空表→补 `ensureVendor` 助手；分类引擎走 `SecurityUtils.getCurrentEnterpriseId()` 但测试无登录态→`importFromCsv` 的 catch 吞掉异常致 `classification` 为 null；`LoginUser` 构造器第 3 参才是 `enterpriseId`（传错则规则全落空退化为方向兜底） | SPC-P99 | ✅ 已开发完成（P99 D 类；3 个类 7/7 全绿） |
| REQ-2026-115 | 银行流水 CSV 真实表头「对方户名」无法识别 | P1 | `ColumnMappingResolver.Field.COUNTER_ACCOUNT` 别名仅含 `对方账户/对方名称/对手方/对方账号`，而真实银行对账单普遍使用 **`对方户名`**（工行/建行/招行导出格式），导致 `counterAccount` 恒为 null、流水分类与自动生单全部失准。属真实生产缺陷，非测试问题。修法：补齐真实表头别名（`对方户名`/`对方单位`/`对方姓名`/`交易对方`/`对方方名`） | SPC-P99 | ✅ 已开发完成（P99 A/D 实施中由慢测暴露；`BankStatementRealDataImportTest` 1/1 绿） |
| REQ-2026-116 | 慢测基类统一设置企业上下文，消解 enterprise_id 空值大面积失败 | P0 | 剩余慢测中 **90+ 项同根因**：`enterprise_id` 自 V102~V105 起为 `NOT NULL` 且**无 DB 默认值**，而 `MyMetaObjectHandler.insertFill` 仅在 `EnterpriseContextHolder.get() != null` 时回填；测试无登录态 → 上下文 null → 不回填 → 报 `null value in column "enterprise_id" ... violates not-null constraint`。逐类补 `setEnterpriseId()` 既重复又易漏。修法：在 `AbstractMapperTest` 加 `@BeforeEach` 设默认企业 + `@AfterEach` 清理（ThreadLocal 不随 `@Transactional` 回滚）。**副作用需处理**：①数据权限拦截器会给所有慢测 SELECT 注入 `enterprise_id = 1`，故依赖「无上下文→放行全部」语义的用例必须显式 `clear()`；②使用独立 `ENT_ID`（9902~9905）造数的报表/余额类必须把上下文切到同一企业 | SPC-P99 | ✅ 已开发完成（P99 C 类；慢测 92 → **55 项**、33 类，`enterprise_id` 根因彻底消失；快测 1733/0 回归通过） |
| REQ-2026-117 | 进销项 E2E 悬空外键 + 单据状态误用发票状态 | P0 | `SalesFlowE2ETest`(6) / `InputFlowE2ETest`(5) 报 `fk_output_invoice_customer` / `fk_input_invoice_vendor`：硬编码 `customerId=1|99`、`vendorId=1|99`，而迁移后 `t_customer`/`t_vendor` 均为空表（seed 0 行）。**连带 2 处同源缺陷**：①`t_business_doc.doc_no` NOT NULL 无默认值，原代码只读不写；②单据状态写 `"CONFIRMED"`/`"SETTLED"`，而 `chk_doc_status` 允许集为 DRAFT/SUBMITTED/APPROVED/VOUCHERED/PARTIALLY_RECONCILED/FULLY_RECONCILED/CLOSED/REJECTED/REVERSED —— `CONFIRMED` 属**发票侧**状态、`SETTLED` 根本不存在。另原代码断言 `invoice.docNo`/`invoice.voucherNo`/`voucher.sourceDocNo` 等 `exist=false` 字段（注释明写「DB 无此列」），经 DB 往返必为 null，断言无法成立。修法：基类补 `ensureCustomer()`（与 `ensureVendor()` 对称）；显式赋 `docNo`；单据状态改 `APPROVED`/`FULLY_RECONCILED`；废弃字段改为断言真实 id 列落库，并补 `voucher.businessDocId` 建立凭证→单据溯源 | SPC-P99 | ✅ 已开发完成（P99 C 类；两类 **11/11 全绿**，慢测 55 → **44 项**/31 类，两个 FK 根因彻底消失） |
| REQ-2026-118 | 银行流水类 NOT NULL/FK/乐观锁/REQUIRES_NEW 四重缺陷 | P0 | 5 个类共 9 项（`BankStatementAuditIntegrationTest`3、`BankStatementMapperTest`2、`PerformanceBaselineTest`2、`EntityDbSchemaIntegrationTest`1、`BankFlowE2ETest`1）报 `tx_type` 空值 / `fk_statement_account` 悬空 / `account_id` 空值 / `category_id` 空值。**修完表层后暴露 4 层更深缺陷**：①**约束用例「因错误的原因通过」** —— `BankStatementMapperTest` 的 5 个 `assertThrows` 用例被 FK 违约顶替，从未真正验证目标约束；②`insert_shouldEnforceChkDirection` 守护**不存在的约束** —— `direction` 是 `@TableField(exist = false)`，DB 无该列故无 CHECK，永远无法通过；③`assertEquals(0, version)` 与 DB 默认值 **1** 矛盾，且 MyBatis-Plus 不把 `@Version` 的 DB 默认值回填内存对象；④`BankStatementAuditIntegrationTest` 的 `audit()` 内部走 `autoGenerateInNewTx`（**REQUIRES_NEW**），看不到基类 `@Transactional` 未提交的夹具数据 → 必抛「银行流水不存在」，须显式 `NOT_SUPPORTED` 关闭回滚 | SPC-P99 | ✅ 已开发完成（P99 C 类；5 类 **32/32 全绿**，慢测 44 → **35 项**；新增基类 `ensureSubject()` 助手 + 乐观锁过期版本用例） |

---

## 九、AI 智能体层

| 编号 | 需求名称 | 优先级 | 验收标准 | 关联 SPEC | 实现状态 |
|------|---------|--------|---------|-----------|---------|
| REQ-2026-038 | AI 科目映射 | P1 | 商品名→科目映射；pgvector+LLM；top-3候选 | SPC-019-P3 | ⏳ P2 规划 |
| REQ-2026-039 | AI 异常检测 | P1 | 品名背离、金额异常、时间异常检测 | SPC-018 | ⏳ P2 规划 |
| REQ-2026-040 | AI 流水分类（语义兜底） | P2 | 规则引擎失败时语义分类 | — | ⏳ 规划中 |
| REQ-2026-041 | AI 审核建议 | P2 | OCR 校验合规性、发票合规检查 | — | ⏳ 规划中 |
| REQ-2026-042 | AI 核销匹配 | P2 | 金额+客户匹配推荐核销对 | — | ⏳ 规划中 |
| REQ-2026-043 | AI 自然语言查数 | P3 | NL→SQL→图表 | SPC-020-P3 | ⏳ 远期 |
| REQ-2026-044 | AI 预算预测 | P3 | 基于历史数据预测预算 | — | ⏳ 远期 |
| REQ-2026-045 | AI 风控规则 | P3 | 跨模块风控规则 | — | ⏳ 远期 |

---

## 十、存储管理

| 编号 | 需求名称 | 优先级 | 验收标准 | 关联 SPEC | 实现状态 |
|------|---------|--------|---------|-----------|---------|
| REQ-2026-046 | 文件存储与管理 | P0 | MinIO 附件上传/下载/关联业务实体 | — | ✅ |

---

## 十一、架构设计约束

| 编号 | 约束名称 | 优先级 | 说明 | 受影响模块 |
|------|---------|--------|------|-----------|
| REQ-2026-047 | 人是唯一审核主体 | P0 | 系统不允许自动调整业务状态 | 全部 |
| REQ-2026-048 | 凭证不可变性 | P0 | 已审核凭证只能红冲 | 总账 |
| REQ-2026-049 | 金额精度保证 | P0 | BigDecimal + NUMERIC(18,2) | 全部 |
| REQ-2026-050 | 编号关联溯源 | P0 | 全链路双向追溯 | 全部 |
| REQ-2026-051 | 审计追踪 | P0 | AOP + jsonb 快照 | 全部 |
| REQ-2026-052 | 数据权限隔离 | P1 | 组织级数据隔离 | 全部 |
| REQ-2026-053 | 核销架构约束 | P0 | 银行流水不直接参与核销 | 应收应付/资金 |
| REQ-2026-054 | AI 输出=建议 | P0 | 不自动应用，人工确认后落库 | AI 层/全部 |
| REQ-2026-081 | 前端批量操作与列表交互统一 | P1 | BatchActionBar/useBatchOperation/BatchResultDialog 三页面统一（every 启用语义、100 上限、危险二次确认、统一结果弹窗）；列表交互按 FDS 规范 A/B 类去操作列、点击行进入；新增列表强制复用，禁止页内自建 | SPC-P67 / FDS | 📝 SPEC 草案待审核（三页面代码已落地未提交，FDS-v0.1 草案） |

---

## 十二、AI 智能体（新增 2026-07-09）

| 编号 | 需求名称 | 优先级 | 验收标准 | 关联 SPEC | 实现状态 |
|------|---------|--------|---------|-----------|---------|
| REQ-2026-058 | 银行流水 AI 分类建议 | P2 | 规则引擎无法匹配时，AI 基于语义推荐分类 | SPC-046 | 🆕 规划中 |
| REQ-2026-059 | 发票 AI 审核建议 | P2 | AI 扫描发票特征，标记异常供财务复核 | SPC-047 | 🆕 规划中 |
| REQ-2026-060 | AI 核销匹配推荐 | P2 | 金额+客户/供应商智能匹配，推荐核销对 | SPC-048 | 🆕 规划中 |
| REQ-2026-061 | AI 反馈闭环 | P2 | 用户确认/修正 AI 结果后，反馈回 AI 改进模型 | SPC-049 | 🆕 规划中 |
| REQ-2026-062 | AI 服务测试 | P2 | AI 服务单元测试、集成测试、回归测试 | SPC-050 | 🆕 规划中 |

> **注**：REQ-2026-055~057、063~065 已在 §三 应收应付管理中登记，此处不再重复。

---

## 十三、Agency 分支（代账引擎）

| 编号 | 需求名称 | 优先级 | 验收标准 | 关联 SPEC | 实现状态 |
|------|---------|--------|---------|-----------|---------|
| REQ-2026-066 | 多租户数据隔离 | P0 | enterprise_id 列、拦截器、RLS 三层防线 | SPC-126-S-26 | 🆕 规划中 |
| REQ-2026-067 | 用户类型扩展 | P0 | SUPER_ADMIN/AGENCY/ENTERPRISE 三态 + JWT claims | SPC-126-S-26 | 🆕 规划中 |
| REQ-2026-068 | 代理公司管理 | P0 | 代理公司 CRUD、状态机 | SPC-126-S-26 | 🆕 规划中 |
| REQ-2026-069 | 客户企业管理 | P0 | 企业 CRUD、绑定关系、种子数据初始化 | SPC-126-S-26 | 🆕 规划中 |
| REQ-2026-070 | 客户切换 | P0 | 切换接口、RLS context 同步 | SPC-126-S-26 | 🆕 规划中 |
| REQ-2026-071 | 批量发票导入 | P1 | 多租户批量导入、enterprise_id 隔离 | SPC-126-S-26 | 🆕 规划中 |
| REQ-2026-072 | 批量凭证审核 | P1 | 批量审核只影响当前企业 | SPC-126-S-26 | 🆕 规划中 |
| REQ-2026-073 | 批量结账 | P1 | 多企业批量结账 | SPC-126-S-26 | 🆕 规划中 |
| REQ-2026-074 | 客户合同管理 | P2 | 合同 CRUD、续费提醒 | SPC-126-S-26 | 🆕 规划中 |
| REQ-2026-075 | 代理工作台前端 | P1 | 客户列表、切换、批量操作 UI | SPC-126-S-26 | 🆕 规划中 |
| REQ-2026-077 | 企业级建账期间通用化 | P1 | start_period 落库自动回填、默认期间接口、过账校验基于建账期间、前端 11 处默认期间改造 | SPC-P71 | ✅ 已实现（代码 commit b24c7c0；current-period 接口 11 tests + SubjectBalanceServiceImpl 35 tests 全绿；V134 migration） |
| REQ-2026-078 | 期初建账审计增强 | P1 | 期初建账允许任意指定录入时间（建账日期）；记录期初建账日期、录入人员；审计日志修复操作人落库 | SPC-P72 | ✅ 已实现（代码 commit b24c7c0；opened_at/opened_by 落库测试 + AuditLog operator_id/operator_name RealDB 断言；V135 migration） |
| REQ-2026-079 | 银行流水核销体验优化 | P1 | 状态命名去歧义（payment_created→待核销）、核销工作台批量制证、小额直制证阈值配置、仪表盘待核销提醒 | SPC-P73 | 🆕 规划中（SPEC 待审核） |
| REQ-2026-080 | 核销模板科目修正 | P0 | 修正核销单凭证模板双重记账：应收核销借1002→借2203、应付核销贷1002→贷1123 | SPC-P74 | ✅ 已实现（V130 源文件修正 + V140 生产迁移 + 端到端测试，commit 31ce3f4） |
| REQ-2026-086 | 费用汇总报表 | P1 | 按部门/费用类型/员工 × 期间区间的报销费用汇总（金额/单据数/人均/同比/环比）+ Excel 导出；status∈(APPROVED,VOUCHERED)；数据权限拦截器注入 | SPC-P76 | 📝 设计已完成（DSN-费用报销管理.md §8），SPEC 待建 |
| REQ-2026-087 | 折旧与资产统计报表 | P1 | 资产分类汇总（数量/原值/累计折旧/净值/本期应提/净值率）+ 折旧计提汇总（部门×类别）+ Excel 导出；处置资产排除；只读不触发计提 | SPC-P77 | 📝 设计已完成（DSN-固定资产管理.md §8），SPEC 待建 |
| REQ-2026-088 | 预收预付余额汇总 | P1 | 按往来单位的预收/预付余额汇总（期初/本期新增/本期抵扣/本期冲销/期末），恒等式校验 opening+created−applied−reversed==closing；口径对齐 P75 | SPC-P78 | 📝 设计已完成（DSN-应收应付管理.md §8），SPEC 待建 |
| REQ-2026-089 | 代理服务进度与工作量统计 | P2 | t_service_progress 节点跟踪（取票→记账→审核→报税）+ 超期预警 + 人工强制标记（留审计）+ 工作量统计（负责客户数/完成率/在办/超期）；只读聚合不改业务状态 | SPC-P79 | 📝 设计已完成（DSN-代理公司场景设计.md），SPEC 待建 |

---

*本文件应与 DESIGN.md 保持同步。模块变更时同步更新需求编号和状态。*