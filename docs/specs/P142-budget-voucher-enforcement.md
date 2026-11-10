# SPEC-P142：预算执行联动——凭证过账时的预算强制检查与使用额回写（立项）

| 项 | 内容 |
|---|---|
| 状态 | 🟡 **仅立项，待老丁审核（铁律 #10 三步闭环审核门；本批不改任何代码）** |
| REQ | **REQ-2026-156（新增，P1）** |
| 前序 | REQ-2026-126（预算编制侧已 DONE：V158 补列 + Entity 去幽灵 + checkBudget NPE 消除）；REQ-2026-121（原「功能未实现」判定）；铁律 #3（红冲对冲）、铁律 #5（审计追踪） |
| 关联既有缺口 | ①`addUsedAmount` **生产零调用**（全仓唯一调用方是 `BudgetFlowE2ETest:152` 自行模拟）；②凭证链路（制证/审批/过账）**零预算钩子**；③真实业务运行中 `used_amount` 永不回写 ⇒ `executionAnalysis` 统计恒 0；④REQ-126 遗留：`findBySubjectAndPeriod` 仍 `be.*` 返回 snake_case，`Service pick()` 治标，正解 SQL `AS` 别名 |

---

## §0 取证（全部实测，2026-10-28）

### 0.1 编制侧已落地（本轮不动）

| 能力 | 状态 | 证据 |
|---|---|---|
| `t_budget_entry` 维度列（dept/project/period_month/control_type/used_amount） | ✅ V158 已建 | 真库 `\d`：5 列 + `chk_budget_entry_control_type`(WARN/BLOCK/APPROVE) + 索引在位 |
| `BudgetEntryEntity` 字段映射 | ✅ 全部真列（含 `updatedAt` 反向修正） | `BudgetEntryEntity.java:30-58` |
| `checkBudget` 三态语义 | ✅ 可复用 | `BudgetServiceImpl.checkBudget:169-223`：NONE（无配置）/BLOCK（超额 `pass=false`）/APPROVE（超额 `pass=true, action=REQUIRE_APPROVE`）/WARN（>80% 提示，恒放行）|
| 前端 `check`/`execution` 端点 | ✅ 已接 | `frontend/src/api/modules/budget.ts:50-56` |

### 0.2 执行侧断链（本批要立项补的洞）

| # | 证据（file:line） | 含义 |
|---|---|---|
| 1 | `grep addUsedAmount src/main/java` ⇒ **0 调用** | 生产不存在任何「用掉预算」的通路，`used_amount` 只能被手工 SQL 改 |
| 2 | `grep checkBudget src/main/java` ⇒ 仅 Controller 透传端点 | `BLOCK` 语义**无处生效**——凭证点过账照样入账 |
| 3 | 凭证服务（`com.huicai.base.voucher.service`）grep `budget` ⇒ **0 命中** | 制证/审批/过账与预算零联动 |
| 4 | `executionAnalysis` 只读条目 `usedAmount` | 真实业务里该列恒 0 ⇒ 执行分析报表永远「零执行」 |

### 0.3 钩子必要条件（均已核实在位）

- **凭证期间**：`VoucherEntity.period`（L24）真列，与 `t_budget.period` 同为 VARCHAR(6)（`202607` 格式），`findBySubjectAndPeriod` 直接相等比对。
- **科目/金额取数**：`VoucherEntryEntity.subjectId`/`debit`/`credit`（L24/27/30）——借方发生额可直接聚合。
- **过账点**：`VoucherStatus.POSTED`（`t_voucher.status` 转换点，POSTED 后不可修改由 `canModify` 封锁）；红冲 = `reversed_from` 非空 + `status=POSTED`（`VoucherStatus.isReversed:52-54`）。

---

## §1 方案骨架（供拍板）

**核心链路（建议主案）**：凭证**过账（→POSTED）事务内**：
1. 按「科目 × period」聚合凭证借方发生额（贷方科目不控制——预算控制支出发生）；
2. 逐科目调 `checkBudget(subjectId, voucher.period, 借方发生额)`：
   - `BLOCK` → **抛 BusinessException**（凭证不落 POSTED，fail-loud；报文须含「科目、预算剩余额、超申请额」）；
   - `APPROVE→REQUIRE_APPROVE` → 见 D-3 拍板；
   - `WARN/NONE` → 放行（WARN 提示经返回体带给前端，不阻断）；
3. 全部通过后，事务内按条目回写 `addUsedAmount`（聚合后单次/每条目一次，杜绝多次累加）。

**顺手收口 REQ-126 遗留**：`findBySubjectAndPeriod` SQL 显式 `AS "usedAmount"/"controlType"` 别名，删 `pick()` 兼容层（治标转治本）。

---

## §2 BDD 验收场景（实施批的回归锁目标）

| # | Given | When | Then |
|---|---|---|---|
| B1 | ACTIVATE 预算，条目 6601 科目 WARN 80% | 凭证借 6601 70% 额度后过账 | 200，`used_amount` 回写，`action=WARN` |
| B2 | 同上超 80% | 过账 | 仍成功（WARN 语义），响应含提示 |
| B3 | 条目 BLOCK | 过账使 newUsed>budget | 拒绝（BusinessException），凭证保持原状态，`used_amount` 不变 |
| B4 | 条目 APPROVE 超额 | 过账 | 按 D-3 拍板行为 |
| B5 | 无预算配置科目 | 过账 | 正常入账，不受影响（NONE） |
| B6 | 已过账凭证红冲（reversed_from 置位的新凭证） | 过账 | 按 D-4 拍板（回减原发生额 ⇔ 铁律 #3 对冲语义） |
| B7 | 并发两笔凭证同时过账同一 BLOCK 条目 | 并行提交 | 恰一笔成功，`used_amount` 不超 `amount`（D-5 拍板的并发方案生效，真库并发压测） |

---

## §3 决策项（老丁拍板）

| # | 问题 | 选项与建议 |
|---|---|---|
| D-1 | **触发时点** | A(推荐)：**过账（→POSTED）时一次到位**——检查+回写同事务，BLOCK 语义可真正拦截入账；B：制证保存时（会拦草稿，体验差且没卡住入账点）。⚠️ 必须找准入账原子点（候选 `VoucherServiceImpl` 里置 POSTED 的更新路径，实施批第一步取证定位） |
| D-2 | **控制方向** | A(推荐)：只控**借方发生额**（预算=支出上限）；B：借贷双方都查（会把收入科目也卷入，需新增方向列）。条目无方向列，A 不需 DDL |
| D-3 | **APPROVE 态超额** | A(推荐)：放行入账 + `REQUIRE_APPROVE` 写凭证备注/审计，人工事后处置；B：拦截等审批（需新状态，超范围） |
| D-4 | **红冲对 `used_amount`** | A(推荐)：红冲凭证过账时**回减原凭证对应科目发生额**（对冲语义，负数回写）；B：不处理（赤字假象，`executionAnalysis` 失真）。⇠ 若选 A，红冲凭证本身的借方は原贷方科目，须特判 `reversedFrom != null` 走回减分支而非正常检查 |
| D-5 | **并发防护** | A(推荐)：事务内 `SELECT ... FOR UPDATE` 锁到本次涉及的预算条目行再算+回写（单行锁，简单可靠）；B：`UPDATE ... WHERE used_amount + #{x} <= amount` 条件原子更新（省一次 SELECT 但 BLOCK 提示文案难带明细）。⚠️ 二者必须存在一个，先检查后回写的非原子写法禁止 |
| D-6 | **REQ-126 遗留顺手收口** | A(推荐)：本批顺带 SQL `AS` 别名 + 删 `pick()`；B：单独小批后补 |
| D-7 | **警告可视化** | WARN/REQUIRE_APPROVE 结果如何到前端：A(推荐)：过账响应体携带 `budgetWarnings` 数组，前端凭证保存 toast；B：仅后端日志（用户无感，不推荐） |

---

## §4 实施批预置（拍板后动代码时）

- 迁移：**预计无 DDL**（列/约束已齐）；若 D-2 选 B 才需加方向列。
- 真库回归锁：新增 `BudgetEnforcementRealDBTest` 满 B1–B7（含 B7 并发）；`BudgetFlowE2ETest` Step5/6 改造为「走过账路径」而非直调 `addUsedAmount`（测试隔离同步升级，否决测试内造数的旧行为）。
- 审计（铁律 #5）：BLOCK 拒绝事件须落 trace/log 可追（拒绝非静默）。
- 雉尾：出参面铁律 #13 零容忍自检会自动覆盖新端点；入参面零容忍同样。
- 验证门槛：L1 1769+/0/0/5、L2 2366+/0/0/5、覆盖率门禁全过。

---

## §5 登记动作

- 登记册：新增 **REQ-2026-156** 行（状态 ⏳ 待拍板）+ V1.94 版本行。
- AGENTS §0：V1.94 行简记立项。
