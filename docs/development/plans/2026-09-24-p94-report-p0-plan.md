# P94 实施 Plan — 报表 P0 一致性修复（REQ-090/091/092）

> **版本**：V1.0 | **日期**：2026-09-24 | **状态**：📋 待审核（三步闭环第 2 步产物，未开工）
> **上位 SPEC**：[P94-report-p0-consistency.md](../specs/P94-report-p0-consistency.md)（V1.1 已审核通过）
> **关联需求**：REQ-2026-090 / 091 / 092（REQUIREMENTS_REGISTRY §八 V1.16，状态=已审核通过待排期）
> **前置门**：SPEC ✅ 审核通过（批次1/4）→ 本 Plan → **审核门** → BUILD（微循环 TDD）→ VERIFY → REPORT

---

## 一、范围与不做清单

**做**（3 项，全只读报表链路）：
1. **REQ-090**：资产负债表年初列小计/合计改用 `begin_balance` 口径（当前用 1 月**期末**聚合）
2. **REQ-091**：零值行可见性规则 + 两个独立复选框 + 悬空保护（保留金额最大明细行）
3. **REQ-092**：现金勾稽口径纳入 1012 + 页面级黄色 Alert + 导出文案同步

**不做**（明确排除，防范围蔓延）：
- 不做 P95（下钻/打印）、P96（现金流专业化）、P97（P2 四合一）任何代码
- 不改利润表（`incomeStatement`）、不改 `t_voucher_cash_flow`（P96 范围）
- 不做 Entity 变更（→ 无 Flyway migration、无 `check-entity-schema` 风险）
- 不重构报表返回值为 VO（铁律 #13 的存量 Map 风格保持不变，避免范围蔓延；新增字段沿用 camelCase）
- 不修 P93 §5 测试数据断层（实收资本 202401=300000 → 202407=200000），仅由年初对账暴露为提示

---

## 二、事实核验结论（开工前复核，2026-09-24）

| 编号 | 事实 | 证据 | 对方案的影响 |
|---|---|---|---|
| F1 | `balanceSheet()` 全程聚合 `end_balance` | `ReportServiceImpl.java` L66/L76-116/L238 | 年初口径必须换聚合基数，不是改前端 |
| F2 | `subjectBalance(period)` 已返回 `begin_balance` | `ReportDataMapper.java` L20 | **无需改 SQL**：1 月行的 `begin_balance` 就是年初数 |
| F3 | `subtotal()` 硬编码取 `end_balance` | `ReportServiceImpl.java` L238 | 需给 `subtotal()` 增 balanceField 参数 |
| F4 | 三分小计为独立字段、勾稽有内联断言 | L181-190、L150-153 | 小计不折叠口径必须保住（BD2 回归） |
| F5 | 导出年初列取 `balanceSheet(yearStart)` | L466-475（注释 L467-468 自认"与前端同口径"，但口径本身错） | 导出同改 begin 口径 |
| F6 | 前端 BS 年初小计读 `yearStartData.*` | `BalanceSheetView.vue` L49/62/75/88/118/131/144/173，赋值 L235 | 前端改为读 `result.yearStart.*` |
| F7 | 前端 BS 年初明细读 `subjectBalance(ys).begin_balance` | L212-215、L232 | 明细口径本就正确，**保留** |
| F8 | `isZeroRow` 只看 `end_balance` | `BalanceSheetView.vue` L217 | REQ-091 主战场 |
| F9 | **科目余额表已按 4 列判定零值** | `SubjectBalanceView.vue` L78-79（begin/debit/credit/end 全 0 才隐藏） | **已合规**，不重复改判定，只加开关 |
| F10 | **现金流量表已按本期/累计双列判定 + fixed 骨架行常驻** | `CashFlowView.vue` L82-88 | **已合规**，不重复改判定，只加开关 |
| F11 | `cashSubjectBalance` 用 `LIKE '100%'` | `ReportDataMapper.java` L84 | 改白名单 `IN ('1001','1002','1009','1012')` |
| F12 | 勾稽差异已返回 `cashCheckDiff/cashCheckOk`（+Ytd） | L335-340、L345-352 | 后端仅需改 SQL 口径，前端加 Alert |
| F13 | 导出勾稽文案写死"1001+1002" | L554-556 | 文案同步改口径 |
| F14 | 前端有 vitest + 组件测试范式 | `frontend/package.json` L13；`__tests__/BalanceSummaryView.test.ts` | REQ-091 抽纯函数 + vitest 单测 |
| F15 | 后端真实 DB 测试范式存在 | `mapper/CashFlowPeriodRangeRealDBTest.java`（`AbstractMapperTest`） | REQ-092 SQL 变更走真实 DB 测试 |
| F16 | 勾稽行仅在 `cashCheckOk === false` 时插入 | `CashFlowView.vue` L73-75 | 异常必显的义务已内建，开关2 不得屏蔽 |

**范围收窄结论（重要）**：REQ-091 实际只有**资产负债表**存在口径缺陷（F8）；科目余额表（F9）与现金流量表（F10）已满足"任一展示列非零即显示"。本批不做无谓改动，只在三个页面统一挂载两个开关。

---

## 三、待确认口径细化（SPEC 授权实现期定义，需老丁拍板）

| 编号 | 议题 | 推荐默认 | 理由 |
|---|---|---|---|
| **D1** | `yearStartCheckDiff`（SPEC §2 草案字段）对账什么 | 定义为**年初口径的资产恒等式差异**：年初 `totalAssets − (totalLiabilities + totalEquity)`，附 `yearStartCheckOk` | 与"科目余额表期初合计对账"等价——两者同源于 `begin_balance`，若 BS 年初不平衡，说明明细加总 ≠ 年初合计或数据断层（P93 §5 场景会真实触发），是可证伪的强校验；若按字面做"BS 年初合计 vs 余额表期初合计"逐项对账，恒等成立、永远为 0，无校验价值 |
| **D2** | 年初区块数据来源 | `balanceSheet(period)` 响应内新增 `yearStart` 块（服务端内部按 `YYYY01` + `begin_balance` 口径计算），前端**不再二次请求** `balanceSheet(ys)`；明细年初值仍走 `subjectBalance(ys)`（F7 保留） | 少一次 HTTP；口径唯一（避免前后端各自算）；"后端 begin 口径聚合"拍板的直接落地 |
| **D3** | 「隐藏报表标准空白行」开关作用范围 | 白名单三处：① 现金流量表 `fixed:true` 骨架行（期初/期末现金）在**本期与累计均 0** 时隐藏；② 资产负债表「其他资产」「其他负债」全 0 时隐藏；③ 勾稽提示行**不受此开关控制**（异常必显，F16）；④ 三分小计行**永不隐藏**（保 P92-B 不折叠 + BD2）；⑤ 科目余额表无标准空白骨架行 → 该开关置 disabled 并注明"本页无标准空白行" | 与开关1（明细行级过滤）职责不重叠；不破坏 P92-B 不折叠口径与提示义务 |

D1/D2/D3 若被否决，退路：D1 可退回"逐科目对账"（弱校验但符合字面）；D2 可退回新增独立端点 `/balance-sheet/begin`；D3 可退回开关2 不实现（仅保留开关1）——均不阻塞开工，只是范围缩小。

---

## 四、文件清单（改动面，预计 8 文件 + 3 新测试）

| 文件 | 动作 | 要点 |
|---|---|---|
| `backend/.../report/service/impl/ReportServiceImpl.java` | 改 | 抽 `buildBalanceSheet(period, balanceField)`；`subtotal()` 增字段参数；新增 `beginBalanceSheet()`；`balanceSheet()` 增 `yearStart` 块 + `yearStartCheckDiff/Ok`；导出 `ysData` 改 begin 口径；L554-556 文案 |
| `backend/.../report/service/ReportService.java` | 改 | 接口加 `beginBalanceSheet`（若 D2 采纳则仅内部用，接口可不暴露 → 默认**不暴露**，保持接口最小） |
| `backend/.../report/mapper/ReportDataMapper.java` | 改 | L84 `LIKE '100%'` → `s.code IN ('1001','1002','1009','1012')` |
| `frontend/src/views/report/balance-sheet/BalanceSheetView.vue` | 改 | 删 `balanceSheet(ys)` 调用改读 `result.yearStart`；`isZeroRow` 改多列判定；两个开关；年初不平衡黄色 Alert |
| `frontend/src/views/report/subject-balance/SubjectBalanceView.vue` | 改 | 仅挂载两个开关（判定逻辑 F9 不动） |
| `frontend/src/views/report/cash-flow/CashFlowView.vue` | 改 | 仅挂载两个开关（判定逻辑 F10 不动）+ 页面级勾稽 Alert |
| `frontend/src/utils/report/rowVisibility.ts` | **新增** | 纯函数：`isRowVisible(row, opts)`、`resolveDanglingGuard(rows, group)`、`STANDARD_BLANK_RULES`（D3 白名单） |
| `frontend/src/utils/report/rowVisibility.test.ts` | **新增** | vitest 单测（纯函数，勿在 .vue 内写不可测逻辑） |

> 前端测试统一放 `frontend/src/__tests__/report-row-visibility.test.ts`（对齐现有 `__tests__/` 约定，`utils/` 下不放测试）。

---

## 五、微循环（TDD Red→Green，每环 5-15 分钟，独立可回滚）

### M0 前置干净化（非 TDD，独立 commit）
- 现状：`ReportServiceImplTest.java` 有 **69 行未提交 P93 测试**（P93 impl 已随 `e17d6a6` 上线，测试被漏下）
- 动作：`cd backend && mvn test -Dtest=ReportServiceImplTest` → 绿则单独 commit（`test(report): 补交 P93 权益区测试`）；红则**先修 P93 测试**再进 M1
- 目的：P94 的 diff 不与 P93 混在一起（审计可追溯）

### M1 REQ-090 后端 begin 口径（核心环）
1. **Red**：`ReportServiceImplTest#p94_beginBalanceSheet_yearStartSubtotalEqualsDetailSum`
   - 造数：`subjectBalance("202601")` 中 1002 begin=80,000 / end=120,000（1 月有发生额）；`subjectBalance("202606")` 中 1002 begin=120,000 / end=150,000
   - 断言：`service.balanceSheet("202606").get("yearStart").currentAssets == 80,000.00`（年初=1 月**期初**）
   - 负向断言：`yearStart.currentAssets != 120,000.00`（旧口径=1 月期末必须被证伪）、`totalAssets` 期末仍 150,000（不受影响）
2. **Green**：实现 `buildBalanceSheet(period, balanceField)` + `subtotal(rows, target, fallback, balanceField)` + `beginBalanceSheet()` + `yearStart` 块（`yearStartCheckDiff/Ok` 按 D1）
3. **回归**：`mvn test -Dtest=ReportServiceImplTest` 全绿（17+ 存量用例含 P92B-BD2 勾稽、P93 权益）

### M2 REQ-090 导出 + 前端切换
1. **Red**：`ReportExportTest#p94_exportBalanceSheet_yearStartUsesBeginCaliber`（年初列小计单元格 == 80,000 而非 120,000）
2. **Green**：导出 `ysData` 改 begin 口径；`BalanceSheetView` 改读 `result.yearStart`（D2）
3. 验证：`npx vue-tsc --noEmit`（仅存量错误）+ `npx vite build --mode development` exit 0

### M3 REQ-091 可见性规则 + 两开关 + 悬空保护
1. **Red**：`report-row-visibility.test.ts` 6 例
   - ① 年初≠0、期末=0 的 BS 明细行 → 可见
   - ② 小计≠0 但其明细按规则全隐藏 → 强制保留**金额最大**明细行（负向：不得出现"有合计无明细"）
   - ③ 开关1 与开关2 互不影响（切开关2 不改开关1 结果）
   - ④ 开关2 隐藏现金流量表全 0 骨架行，但**勾稽异常行永显**
   - ⑤ 开关2 永不隐藏三分小计行（P92-B 回归）
   - ⑥ 科目余额表 4 列判定回归（F9 不被改坏）
2. **Green**：实现 `rowVisibility.ts` 纯函数 → 三视图接入两个开关

### M4 REQ-092 勾稽口径 + 页面级 Alert
1. **Red**：
   - `CashSubjectBalanceRealDBTest#p94_cashSubjectBalance_includes1012`（真实 DB：`AbstractMapperTest` 范式，造 1012 余额，断言合计含 1012）
   - `ReportServiceImplTest#p94_cashCheck_covers1012`（mock 口径回归：`cashCheckOk` 逻辑不变）
2. **Green**：SQL 白名单 + `CashFlowView` 黄色 Alert（`cashCheckOk === false` 显示差异额，`cashCheckOkYtd` 同）+ 导出文案
3. 负向：差异 < 0.01 时**无** Alert（回归 F12 现状）

### M5 VERIFY（部署核验，P94 场景 7）
- 重启后端（`java -jar ... --spring.profiles.active=dev`，`/tmp/backend.log`）
- Playwright 截图 4 张：① 年初列小计 == 明细加总 ② 零值行规则生效无悬空 ③ 现金流勾稽黄色 Alert ④ 权益区显示"未分配利润"（P93 回归）
- `mvn test` 全量：**Failures 0 / Errors 0**

---

## 六、验证门（全部必须通过，缺一不放行）

| 门 | 命令/动作 | 通过标准 |
|---|---|---|
| 后端单测 | `cd backend && mvn test` | Failures: 0, Errors: 0 |
| 后端真实 DB | 同上（`AbstractMapperTest` 组） | 1012 用例绿；docker 不可用则记录 skip 证据，不静默跳过 |
| 前端单测 | `cd frontend && npx vitest run src/__tests__/report-row-visibility.test.ts` | 6 例全绿 |
| 类型检查 | `npx vue-tsc --noEmit` | 仅存量错误（BusinessDocEdit ×4、CashFlowView 76/117、SubjectList 232），**新增错误 = 0** |
| 构建 | `npx vite build --mode development` | exit 0 |
| 部署核验 | 重启后端 + Playwright 截图 4 张 | 与 M5 断言一致 |

---

## 七、风险与回滚

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| 年初口径修正后历史导出数字变化 | 高（必然） | 老导出件不可比 | REPORT 中明示"口径修正"；不改 DB、不改历史数据；SPEC §版本历史留痕 |
| `subtotal()` 加参数影响 P92-B 勾稽断言 | 中 | 三分小计回归失败 | M1 强制跑存量 17+ 用例；断言不变量保持（L150-153） |
| 三视图开关语义漂移 | 中 | 页面行为不一致 | 纯函数单一实现 + 6 例单测锁死语义 |
| 前端二次请求删除引发加载竞态 | 低 | 年初列空白 | `yearStartAvailable` 改为由 `result.yearStart != null` 判定；`.catch()` 兜底保留 |
| 委派模型路由故障 | 高 | 无法并行 | 按 AGENTS §4.5-16 直写；或试一次 `deep-low` 委派，失败即直写并留任务书 |
| 真实 DB 不可用 | 中 | M4 SQL 验证不足 | 显式记录 skip；SQL 白名单改动为单行 IN 列表，人工对证 `t_subject` |

**回滚**：全部为源码改动，无 migration、无数据变更 → `git revert <commit>` 即可；每个微循环独立 commit，可逐环回退。

---

## 八、待办与停机点

- [ ] 老丁审核本 Plan（D1/D2/D3 拍板）
- [ ] M0 P93 测试补交（独立 commit）
- [ ] M1 REQ-090 后端 begin 口径
- [ ] M2 REQ-090 导出 + 前端
- [ ] M3 REQ-091 可见性 + 开关 + 悬空保护
- [ ] M4 REQ-092 勾稽口径 + Alert
- [ ] M5 VERIFY + REPORT（含口径修正说明、截图证据、REGISTRY 状态回写 V1.17）

---

## 版本历史

| 版本 | 日期 | 作者 | 变更 |
|---|---|---|---|
| V1.0 | 2026-09-24 | Sisyphus | 初稿：16 条开工前事实核验（F1-F16），据此把 REQ-091 范围收窄至仅资产负债表；D1/D2/D3 三项口径细化待老丁拍板；5 个微循环（M0-M5）TDD 计划 + 6 道验证门；全程只读，无 DB 变更 |
