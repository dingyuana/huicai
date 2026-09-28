# P97 实施 Plan — 报表 P2 专业化（REQ-099/097/098/100/102）

> **版本**：V1.0 | **日期**：2026-09-28 | **状态**：📋 待审核（三步闭环第 2 步产物，未开工）
> **上位 SPEC**：[P97-report-p2-professional.md](../specs/P97-report-p2-professional.md)（V1.1 已审核通过，含 3 项拍板：前端组树 / 重分类全局单开关 / 诊断首期 2 条规则）
> **关联需求**：REQ-2026-099（利润表法定行）、097（余额表树/辅助核算）、098（重分类）、100（异常诊断）、**102（期初连续性页条，P98 转入）**
> **前置门**：SPEC ✅ → 本 Plan → **审核门** → BUILD → VERIFY → REPORT
> **⚠️ 本 Plan 首要事项：开工前核验发现 REQ-099 的实际严重性高于其 P2 定级，见 §二 F3–F7 与 D1 拍板点**

---

## 一、范围与阶段划分（按严重度重排，不按 REQ 编号顺序）

| 阶段 | 内容 | 定级 | 理由 |
|---|---|---|---|
| **A** | 利润表取数口径修正 + 净利润行（REQ-099 上半） | **建议 P1** | 现取数把营业外收入/投资收益/其他收益算进营业收入，且**完全漏 6403 税金及附加与 6801 所得税** → 利润总额系统性虚高，属财务准确性缺陷 |
| **B** | 利润表法定行扩展与费用展开（REQ-099 下半） | P2 | 行结构与准则对齐 |
| **C** | 余额表树 + 辅助核算（REQ-097） | P2 | 含修复现存 level 空列缺陷 |
| **D** | 报表重分类开关（REQ-098） | P2 | 默认关，diff=0 |
| **E** | 诊断黄条：异常诊断（REQ-100）+ 期初连续性页条（REQ-102） | P2 | 共用诊断框架，一次做完 |

**不做**：不改任何 DB schema（无 migration）；不造业务数据；不重构 P94 已交付的报表口径代码（除阶段 A 必须触及的利润表）；不处理 D4 已知局限类问题。

---

## 二、开工前事实核验（2026-09-28 复核，全部 file:line / SQL 实证）

| 编号 | 事实 | 证据 |
|---|---|---|
| F1 | 利润表前端 7 行硬编码，全部 `fixed: true`（法定骨架不隐藏） | `IncomeStatementView.vue` L89-95 |
| F2 | 后端只返回 7 个金额字段，毛利/营业利润/利润总额由 Java 二次计算 | `ReportServiceImpl#incomeStatement` L265-300 |
| F3 | **营业收入口径过宽**：`direction='credit' AND code LIKE '6%'` | `ReportDataMapper.java` L37 |
| F4 | **6403 税金及附加不在任何取数段**（科目表存在，dev 零发生额故未暴露） | SQL L39-41 无 6403；`t_subject` 有 6403 |
| F5 | 期间费用 = `6601\|6602\|6603` 三科目捆绑，无销售/管理/财务分列 | SQL L40 |
| F6 | 研发费用（6604）被并入 `other_expense`「其他支出」 | SQL L41 |
| F7 | **无 6801 所得税费用取数 → 利润表无「净利润」行** | SQL 无 6801；F2 字段清单无 netProfit |
| F8 | 科目表 6xx 实有 **18 个四位段**（6001/6051/6101/6111/6115/6117/6301/6401/6402/6403/6601/6602/6603/6701/6711/6801/6901…） | `t_subject` distinct left(code,4) |
| F9 | `SubjectBalanceVO` **无 parentId/level/isLeaf**；`queryByPeriodWithSubject` 只回填 code/name/direction | `SubjectBalanceVO.java`；`SubjectBalanceServiceImpl` |
| F10 | 前端 `level` 列绑定 `row.level` —— VO 无该字段 → **该列当前恒为空**（现存缺陷） | `SubjectBalanceView.vue` L40 |
| F11 | `assist_json` 仅在业务单据生成凭证时写入；手工凭证不写 → dev 全库为空 | `BusinessDocServiceImpl` L468/480/600；`t_voucher_entry.assist_json` 全 NULL |
| F12 | 35 个末级科目配了 `aux_calc_type`（customer 14 / vendor 14 / employee 7），其中仅 1123、2203 有余额行 | `t_subject` |
| F13 | `balanceSheet` 现为 `buildBalanceSheet(period, balanceField)`（P94），**无重分类** | `ReportServiceImpl` |
| F14 | 诊断黄条范式已由 P94 建立（`el-alert type=warning` + `rowVisibility.ts` 纯函数单测） | `CashFlowView.vue`；`utils/report/rowVisibility.ts` |
| F15 | REQ-102 后端能力已具备（`checkOpeningContinuity`，P98），但**仅在 `/period-close/check` 暴露**，科目余额表响应未挂载 | `PeriodCloseController`；`SubjectBalanceService` |
| F16 | 608 个科目 `aux_calc_type` 为空 → 辅助核算明细只对少数科目适用 | `t_subject` |

### 由 F3–F8 推出的严重性判断（提级依据）

按企业会计准则，营业利润 = 营业收入 − 营业成本 − 税金及附加 − 期间费用 + 其他收益 + 投资收益 + 公允价值变动 + 信用/资产减值 + 资产处置收益。现实现：

- **营业外收入（6301）、其他收益（6117）、投资收益（6111）、公允价值变动（6101）、资产处置（6115）只要方向为贷方就被计入「营业收入」**（F3）→ 营收虚高
- **税金及附加（6403）整段缺失**（F4）→ 利润总额虚高
- **所得税（6801）缺失**（F7）→ 无法出净利润，报表不完整
- 研发费用（6604）被错列进「其他支出」（F6）→ 费用结构误导

dev 数据恰好 6xx 几乎全零（仅 6603 有余额行且为 0.00），所以**页面上看不出问题**——这正是它一直没被发现的原因。真实企业上线即错。

---

## 三、待拍板口径（需老丁确认）

| 编号 | 议题 | 推荐默认 | 理由 |
|---|---|---|---|
| **D1** | 阶段 A 是否提级为 P1 并排在最前 | ⭐ **提级 P1，先做** | 这是财务准确性缺陷（利润总额系统性虚高），不是 P2 体验优化；且阶段 A 完成后阶段 B 才有意义（B 的行结构依赖 A 的字段） |
| **D2** | 营业收入口径如何收窄 | ⭐ 显式白名单 `6001 + 6051`，其余 6xx 各自成行（6301 营业外收入、6117 其他收益、6111 投资收益…） | 继续用 `6%` 前缀无法排除 63/68 段；白名单与科目表实证（F8）一一对应，不臆造 |
| **D3** | 辅助核算明细的数据来源与本期可验收性 | ⭐ 从 `t_voucher_entry.assist_json` 聚合（需 `JsonbTypeHandler`，陷阱 4.4-6），**接受 dev 无数据**，用真实 DB 造数测试验收 | 不新建表（F11 已有数据源）；dev 无数据是来源问题不是实现问题 |
| **D4** | 阶段 E 的诊断规则首期范围 | ⭐ 首期 2 条（零收入+高费用、期末现金环比骤降>50%）+ REQ-102 期初连续性页条，共用一套规则 id 与黄条组件 | 复用 P94 黄条范式（F14），一次做完避免两次改同一页面 |
| **D5** | REQ-102 挂载位置 | ⭐ 科目余额表响应新增 `openingContinuity` 字段（复用 P98 的 `checkOpeningContinuity`），对齐 P94 `yearStart` 挂载范式 | 一次请求、不新增端点；与 F15 现有能力一致，零重复实现 |

---

## 四、文件清单（预计 6 改 + 2 新增 + 1 新 util）

| 文件 | 动作 | 阶段 |
|---|---|---|
| `report/mapper/ReportDataMapper.java` | 改 | A/B：`incomeStatementData` + `cumulativeData` 段位重划（白名单化 + 补 6403/6801 + 6601/6602/6603/6604 分列） |
| `report/service/impl/ReportServiceImpl.java` | 改 | A/B：`incomeStatement` 返回新字段 + 净利润；D：重分类开关（默认关） |
| `balance/dto/SubjectBalanceVO.java` | 改 | C：补 `parentId` / `level` / `isLeaf` / `auxCalcType` |
| `balance/service/impl/SubjectBalanceServiceImpl.java` | 改 | C：回填新字段 + 辅助核算明细聚合；E：挂载 `openingContinuity` |
| `frontend/.../report/income-statement/IncomeStatementView.vue` | 改 | B：法定行结构 + 费用展开 |
| `frontend/.../report/subject-balance/SubjectBalanceView.vue` | 改 | C：树状（前端组树，拍板已定）+ level 列修复 + E 黄条 |
| `frontend/src/utils/report/reportDiagnostics.ts` | **新增** | E：诊断规则（纯函数，vitest 可测，仿 `rowVisibility.ts`） |
| `frontend/src/__tests__/report-diagnostics.test.ts` | **新增** | E：规则单测 |
| 测试 | 新增/改 | 各阶段对应单测 + 利润表取数真实 DB 测试（dev 无数据，必须真实 DB 造数） |

---

## 五、微循环（TDD Red→Green）

### 阶段 A（P1，利润表取数口径）— 优先
- **M0 Red**：真实 DB 测试造 6xx 各段发生额（6001 收入 / 6301 营业外收入 / 6117 其他收益 / 6403 税金及附加 / 6602 管理费用 / 6801 所得税），断言：营业收入 **只含 6001+6051**、营业外收入独立、税金及附加被扣、净利润 = 利润总额 − 所得税
- **M1 Green**：SQL 段位重划（D2 白名单）+ `incomeStatement` 新字段
- **M2**：导出与页面对齐（`exportIncomeStatement` 行次 + 前端行），回归断言既有勾稽（营收−成本=毛利 等）

### 阶段 B（P2，法定行 + 费用展开）
- **M3 Red**：销售/管理/财务/研发四费分列断言；**负向**：四费之和 == 原「期间费用」+ 研发费用（不重不漏）
- **M4 Green**：前端行扩展 + 费用明细行

### 阶段 C（P2，余额表树 + 辅助核算）
- **M5 Red**：VO 补字段单测 + 真实 DB 树守恒（父级合计 == 子级之和，容差 0.01）；辅助核算明细 == 科目合计
- **M6 Green**：后端回填 + 前端组树（拍板：前端组树）+ level 列修复；父科目被删 → 子节点提升为根，不丢行（P92B-BD3 精神）

### 阶段 D（P2，重分类开关）
- **M7 Red**：开关关 → 三表与现状 **diff == 0**；开关开 → 列报变化而**凭证数/科目余额/损益完全不变**、无新凭证
- **M8 Green**：`buildBalanceSheet` 增可选重分类参数（默认关），状态显示在报表抬头

### 阶段 E（P2，诊断黄条）
- **M9 Red**：`reportDiagnostics.ts` 规则单测（零收入+高费用、现金骤降>50%、期初不连续），含**负向**：不满足条件不产出诊断、不自动改数
- **M10 Green**：科目余额表与利润表/现金流量表接入黄条；REQ-102 挂载 `openingContinuity`

### M11 VERIFY + REPORT
后端快测 0 failures；真实 DB 测试全绿；`vue-tsc` 新增错误 0；`vite build` exit 0；Playwright 截图 4 张（利润表新行、余额表树、诊断黄条、重分类开关）；REGISTRY/SPEC/AGENTS 回写

---

## 六、验证门

| 门 | 标准 |
|---|---|
| 后端快测 | `mvn test`：Failures 0 / Errors 0 |
| 真实 DB（利润表取数、余额表树、辅助核算） | `mvn test -DexcludedGroups= -Dtest=...` 全绿；**注意本机 3 GB 内存，Testcontainers 类须单独跑**（P98 已实证并跑失败） |
| 前端单测 | `npx vitest run` 新增规则用例全绿 |
| 类型/构建 | `vue-tsc` 新增错误 0；`vite build` exit 0 |
| 负向红线 | 全流程零写入：凭证数、科目余额、损益、余额行数均不变（铁律 #1/#2） |

---

## 七、风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| **阶段 A 改动历史利润表数字** | 已出报表不可比 | 属**口径修正**（纠错非改规则），REPORT 与 SPEC 版本历史明示；建议先跑阶段 A 并单独 commit，便于回溯 |
| 阶段 A 段位重划漏段 | 利润仍算错 | 用 F8 的 18 个四位段逐一对照测试；负向断言覆盖 6901 以前年度损益调整等边界段 |
| 辅助核算 dev 无数据 | 无法页面验收 | D3 已定：真实 DB 造数验收，页面在无数据时不显示该区块 |
| 树状改造破坏合计 | 数字错乱 | 树守恒断言（父 == 子之和）+ 三分小计常驻回归 |
| 重分类开关误开 | 列报漂移 | 默认关 + diff==0 断言 + 抬头显式状态 |

**回滚**：无 migration，逐阶段独立 commit，可单独 `revert`。

---

## 八、待办

- [ ] 老丁审核本 Plan（**D1–D5 拍板，D1 涉及定级变更**）
- [ ] 阶段 A（M0-M2）→ 阶段 B（M3-M4）→ 阶段 C（M5-M6）→ 阶段 D（M7-M8）→ 阶段 E（M9-M10）→ M11 VERIFY

---

## 版本历史

| 版本 | 日期 | 作者 | 变更 |
|---|---|---|---|
| V1.0 | 2026-09-28 | Sisyphus | 初稿：16 条事实核验（F1-F16），据此**将 REQ-099 取数口径部分提级建议为 P1 并前置**（F3-F7 证实营收口径过宽、6403/6801 整段缺失，属财务准确性缺陷）；按严重度重排为 A-E 五阶段；D1-D5 待拍板；11 个微循环；无 DB 变更 |
