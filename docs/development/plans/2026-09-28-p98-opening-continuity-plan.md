# P98 实施 Plan — 期初余额跨期连续性校验（REQ-101）

> **版本**：V1.1 | **日期**：2026-09-28 | **状态**：✅ 已执行完毕（D1–D4 取推荐默认，2026-09-28 完成部署核验，见 SPEC V1.1）
> **上位 SPEC**：[P98-period-opening-continuity.md](../specs/P98-period-opening-continuity.md)（V1.0 审核通过）
> **关联需求**：REQ-2026-101（P1 结账闸门）｜REQ-2026-102（P2 页面黄条）**本轮不做**，随 P97 实施
> **前置门**：SPEC ✅ 审核通过 → 本 Plan → **审核门** → BUILD（微循环 TDD）→ VERIFY → REPORT

---

## 一、范围与不做清单

**做**（1 项，纯只读校验）：
- `SubjectBalanceService` 新增 `checkOpeningContinuity(period)`：逐末级科目比对「本期期初 vs 上期期末」
- `PeriodCloseServiceImpl#checkBeforeClose` 增加第三项检查，复用既有 `issues` 阻断机制

**不做**（明确排除，防范围蔓延）：
- **不做 REQ-102**（科目余额表页面黄条）—— 随 P97 诊断体系一并实施，避免两批次交叉改同一页面
- 不自动修数、不自动滚动期初、不生成调整凭证、不新增阻断型错误码（铁律 #1/#2）
- 校验路径**严禁**调用 `findOrCreate`（否则校验本身产生建行副作用）
- 不动 P94 已交付的报表口径代码；不改 dev 库现有数据（数据修复是独立运营决策）
- 不抽公共容差常量类（两处各留私有常量 + 交叉注释，避免为 0.01 动已交付代码）

---

## 二、开工前事实核验（2026-09-28 复核，全部 file:line）

| 编号 | 事实 | 证据 |
|---|---|---|
| F1 | `checkBeforeClose` 已有 **5 项**检查，累加到 `List<String> issues`，`passed = issues.isEmpty()` | `PeriodCloseServiceImpl.java` L60-113 |
| F2 | 结账阻断复用既有机制：`close()` L318-320 `throw BusinessException.badRequest("结账检查未通过: " + issues)` | 同上 |
| F3 | 现有两项与本例无关：试算平衡 L88（只查期间内借贷合计）、BS 恒等式 L94 | `SubjectBalanceServiceImpl` L440-495；`ReportServiceImpl` |
| F4 | **顺序闸门已有**：强制「上一会计期间必须已结账」，上一期不存在且晚于启用期则报错 | `validateCloseOrder`（L74 调用，实现见方法体） |
| F5 | dev 库 **202402–202407 全部 closed** → 断层在结账前已存在，**6 次结账全部放行** | `t_period` 查询 |
| F6 | `getPreviousEndBalance` 逐月回溯，**上限为上一年的 12 月**，超出返回 `ZERO` | `SubjectBalanceServiceImpl` L522-546 |
| F7 | 滚动逻辑**只在 `findOrCreate` 建行时**调用；行已存在则完全绕过，建行后无校验 | 同上 L500-517 |
| F8 | 现场数据：4001 期初 200,000 / 借贷 0 / 期末 200,000；202401 期末 300,000；查无触及 4001 的凭证 | `t_subject_balance` + `t_voucher_entry` 查询 |
| F9 | `t_period` 存在**同 period_code 多行**（多账套，如 202401 出现 open/closed 两行） | `t_period` 查询 → 测试数据必须用独立 `enterpriseId` |
| F10 | `SubjectBalanceService` 已有 `checkTrialBalance` 等 10 个方法，加方法不破坏接口兼容性 | `SubjectBalanceService.java` |
| F11 | 既有测试基座齐备：`SubjectBalanceServiceImplTest`、`PeriodCloseServiceImplTest`、`PeriodCloseRestContractTest` | `backend/src/test` |
| F12 | `SubjectBalanceMapper` 支持 `queryByPeriod(period)` 批量查（`SubjectBalanceService` L34），可避免逐科目查询 | `SubjectBalanceService.java` L34 |

### 由 F6/F7 推出的两个硬性实现约束

1. **必须复用逐月回溯口径**，不能只看紧邻上月：本例 202402–202406 无余额数据，只看紧邻上期会判「无上期数据 → skip」→ **恰恰漏检本例**。
2. **必须批量查询**：`getPreviousEndBalance` 是「逐科目 × 逐月」单行查询（N 科目 × M 月）。结账检查须改为「按回溯月批量取该期全部余额」构建 `Map<subjectId, endBalance>`，查询数 = 回溯月数（≤12）+ 1，与科目数无关。

---

## 三、待确认口径细化（需老丁拍板）

| 编号 | 议题 | 推荐默认 | 理由 |
|---|---|---|---|
| **D1** | 「上期」如何认定 | ⭐ 复用 `getPreviousEndBalance` 的逐月回溯（最多回溯至上一年的 12 月） | 唯一能检出本例的口径；与建账逻辑同源，避免两处口径漂移 |
| **D2** | 不连续时阻断结账还是仅提示 | ⭐ 阻断（并入既有 `issues`，零新增阻断路径） | 金蝶「不通过就结不了」、用友「期初建账报错」均为阻断；仅提示等于没闸门 |
| **D3** | REQ-102 页面黄条 | ⭐ 本轮不做，随 P97 实施 | 与 P97 REQ-100 共用诊断框架与黄条样式，分开做会两批次交叉改同一页面 |
| **D4** | 回溯上限 12 个月之外的断层 | ⭐ 本轮不处理，写入 SPEC「已知局限」并留 TODO | 超上限时 `getPreviousEndBalance` 返回 ZERO，语义等同「上期无数据」→ skip。跨 12 个月以上的断层需另设年度结转校验，属独立需求 |

---

## 四、文件清单（预计 3 改 + 2 增测试）

| 文件 | 动作 | 要点 |
|---|---|---|
| `balance/service/SubjectBalanceService.java` | 改 | 接口新增 `Map<String,Object> checkOpeningContinuity(String period)` |
| `balance/service/impl/SubjectBalanceServiceImpl.java` | 改 | 实现：只读；批量按回溯月取上期期末；仅末级科目；容差 0.01；返回 `checked/skipped/skipReason/mismatches[]/mismatchCount/maxAbsDiff/passed` |
| `voucher/service/impl/PeriodCloseServiceImpl.java` | 改 | `checkBeforeClose` 增第三项（约 8 行）：`passed=false` 时把科目数与最大差额写入 `issues`；`result` 附 `openingContinuity` 明细 |
| `test/.../balance/service/impl/SubjectBalanceServiceImplTest.java` | 改 | mock 单测：边界/容差/非末级/skip/幂等 |
| `test/.../balance/mapper/OpeningContinuityRealDBTest.java` | **新增** | 真实 DB 跨期测试（`AbstractMapperTest` 范式，增量断言，独立 enterpriseId） |

---

## 五、微循环（TDD Red→Green，每环 5-15 分钟）

### M0 真实 DB 测试（Red）
- `OpeningContinuityRealDBTest`：造两期（本期期初 200,000 / 上期期末 300,000）→ 断言检出 4001 差额 100,000.00
- 负向：把本期期初改为与上期期末一致 → 不再检出
- 预期 Red：方法尚不存在

### M1 服务方法实现（Green）
- 实现 `checkOpeningContinuity`；批量查询版本，不复用逐科目单行查询
- 跑 `SubjectBalanceServiceImplTest` + `OpeningContinuityRealDBTest`

### M2 接入结账闸门（Red→Green）
- **Red**：`PeriodCloseServiceImplTest` 断言 `checkBeforeClose("202407")` 的 `issues` 含期初不连续原因、且 `close()` 抛 `BusinessException`
- **Green**：`checkBeforeClose` 增第三项；`passed` 与 `issues` 沿用既有结构
- 回归：既有 5 项检查结果不受影响

### M3 边界与铁律
- 上期无数据 → `checked=false` + `skipReason`，**不得**阻断（否则新账套首期永远结不了账）
- 差异 0.005 不列入；非末级科目不参与；已删除科目跳过
- 只读铁律：校验前后 `t_subject_balance` 行数与内容零变化；连跑两次结果幂等

### M4 VERIFY + REPORT
- `mvn test`（快测）Failures 0 / Errors 0
- `mvn test -DexcludedGroups= -Dtest=OpeningContinuityRealDBTest` 真实 DB 绿
- 现场数据验证：调用 `checkBeforeClose("202407")` 应**首次**报出 4001 断层
- REPORT：回写 SPEC V1.1 / REGISTRY 状态 / AGENTS 基线

---

## 六、验证门

| 门 | 命令 | 通过标准 |
|---|---|---|
| 后端快测 | `cd backend && mvn test` | Failures 0, Errors 0 |
| 真实 DB | `mvn test -DexcludedGroups= -Dtest=OpeningContinuityRealDBTest` | 全绿（注：属性名是 `excludedGroups`，见 AGENTS §6） |
| 现场验证 | 调用 `checkBeforeClose("202407")` | 能报出 4001 差额 100,000.00（现为放行） |
| 回归 | `PeriodCloseServiceImplTest` 全量 | 既有 5 项检查行为不变 |
| 前端 | 不涉及 | 本轮无前端改动 |

---

## 七、风险与回滚

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| 误阻断正常业务（新账套/年中启用） | 中 | 无法结账 | D1 的 skip 分支 + 场景 3 专项测试；skip 时 `passed=true` |
| 跨 12 个月断层漏检 | 低 | 漏检 | D4 记为已知局限 + TODO，不在本轮承诺 |
| 性能（结账变慢） | 中 | 结账超时 | 批量查询，查询数与科目数无关（见 F12） |
| 校验产生数据副作用 | 低 | 违反铁律 #1 | 严禁调 `findOrCreate`；场景 6 断言余额表零变化 |
| dev 数据断层导致现有结账测试失败 | 中 | 测试红 | 现有测试用 mock 不依赖 dev 数据；真实 DB 测试用独立 enterpriseId 构造 |

**回滚**：纯源码改动，无 migration、无数据变更 → `git revert` 即可；每个微循环独立 commit。

---

## 八、待办与停机点

- [ ] 老丁审核本 Plan（D1–D4 拍板）
- [ ] M0 真实 DB 测试（Red）
- [ ] M1 `checkOpeningContinuity` 实现
- [ ] M2 接入 `checkBeforeClose`
- [ ] M3 边界与只读铁律
- [ ] M4 VERIFY + REPORT

---

## 版本历史

| 版本 | 日期 | 作者 | 变更 |
|---|---|---|---|
| V1.0 | 2026-09-28 | Sisyphus | 初稿：12 条开工前事实核验（F1-F12），据此确定两项硬约束——必须复用逐月回溯口径（F6）、必须批量查询（F12）；D1-D4 四项口径细化待拍板；4 个微循环 + 5 道验证门；全程只读，无 DB 变更 |
